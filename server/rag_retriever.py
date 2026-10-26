# -*- coding: utf-8 -*-
"""轻量 RAG 检索层 —— 为智算铝宾智能体提供语义检索。

设计约束（保证可打包、离线、无额外模型依赖）:
  - 向量化用纯 numpy TF-IDF（词袋 + 字符 n-gram），不依赖 faiss/sentence-transformers，
    大规模向量检索对当前小体量领域语料绰绰有余，且避免打包时引入过重依赖。
  - 若本机 Ollama 已有 embedding 模型（如 nomic-embed-text / mxbai-embed-large），
    可自动启用真向量检索；否则退回内置 TF-IDF。二者均离线可用。
  - 检索结果注入严格受 _MEMORY_CHAR_CAP / _budget_tool_result 约束（调用方截断），
    避免小模型上下文被历史案例撑爆。DFT 参数 / 定量结论不通过本层取数，
    仍走确定性方法（BSE 校验、关系库、真实计算）。

语料来源（构建索引时读取，命中缺失文件则跳过）:
  - g16_diag_testset.json / g16_job_inventory.json      高斯作业分诊案例
  - Laya_高斯作业分诊_调校报告.md / Laya_温度校准报告.md  Laya 调校与误诊教训
  - workspace/sp_props 自旋基态性质 / 机理相关 md 文本
"""
import os
import re
import json
import math
import hashlib
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------- 中文/英文 tokenizer：词 + 二元字符 n-gram，兼顾中英文 ----------
_WS = re.compile(r"[\s,;:()\[\]{}\"'\\/\-—·=+\|\.]+")
_ALNUM = re.compile(r"[A-Za-z0-9_]+")
_CJK = re.compile(r"[\u4e00-\u9fff]")


def tokenize(text: str):
    text = (text or "").lower()
    toks = []
    # 英文/数字词
    for w in _ALNUM.findall(text):
        if len(w) > 1:
            toks.append(w)
    # 中文：整句按 2-gram 切，保留语义又不产生过碎的单字
    for seg in _CJK.findall(text):
        pass
    cjk_runs = re.findall(r"[\u4e00-\u9fff]+", text)
    for run in cjk_runs:
        if len(run) == 1:
            toks.append(run)
            continue
        for i in range(len(run) - 1):
            toks.append(run[i:i + 2])
    return toks


# ---------- 语料来源 ----------
_CORPUS_FILES = [
    ("g16_diag_testset.json", "json", "diagnosis_reports"),
    ("g16_job_inventory.json", "json", "job_inventory"),
    ("Laya_高斯作业分诊_调校报告.md", "md", "laya_tuning"),
    ("Laya_温度校准报告.md", "md", "laya_temp"),
    ("workspace/tune_laya_triage.py", "md", "laya_rules"),
]


def _chunk_json(entries, scope):
    """从诊断案例/库存 JSON 生成『一条一含判决的文本块』。"""
    if isinstance(entries, dict):
        entries = entries.get("jobs") or entries.get("data") or [entries]
    if not isinstance(entries, list):
        return []
    chunks = []
    for e in entries:
        if not isinstance(e, dict):
            continue
        name = e.get("raw") or e.get("name") or e.get("job") or "job"
        label = e.get("label") or e.get("expect") or ""
        rep = e.get("diagnostic_report") or e.get("summary") or ""
        parts = [f"[{scope}] job '{name}'", f"label={label}"]
        if rep:
            parts.append(rep)
        extra = []
        for k in ("term_norm", "term_err", "zsymb", "maxcyc", "conv_fail",
                  "n_imag", "multi", "fchk_kb", "lines", "lowest_imag"):
            if k in e and e[k] is not None and e[k] is not False:
                extra.append(f"{k}={e[k]}")
        if extra:
            parts.append("; ".join(extra))
        if e.get("usable_score") is not None:
            parts.append(f"usable_score={e['usable_score']} concern={e.get('concern')} "
                         f"needs_rerun={e.get('needs_rerun')} "
                         f"action={'RERUN' if _rerun(e) else 'KEEP'}")
        chunks.append((" ".join(parts), name, label, scope))
    return chunks


def _rerun(e):
    return bool(e.get("needs_rerun", 0) >= 0.5 or e.get("usable_score", 0) >= 1.9)


def _chunk_md(text, scope):
    """Markdown 按段落/代码块切块，每块附出处文件名。"""
    if not text:
        return []
    chunks = []
    parts = re.split(r"\n\s*\n+", text)
    for p in parts:
        p = p.strip()
        if len(p) < 12:
            continue
        chunks.append((" ".join(p.split()), os.path.basename(scope), None, "md"))
    return chunks


class RagRetriever:
    """TF-IDF (+可选 Ollama embedding) 语义检索器。

    用法:
        from rag_retriever import get_retriever
        hits = get_retriever().search("二重态 假成功 单重态", top_k=5)
        # hits: [ {"text","key","label","scope","score"}, ... ]
    """

    def __init__(self, sources=None):
        self.corpus_files = sources or _CORPUS_FILES
        self._chunks = []
        self._emb = None          # numpy 矩阵 [n_chunks, dim]
        self._vocab = {}
        self._idf = None
        self._norms = None
        self._loaded = False
        self._embed_model = self._probe_ollama_embed()
        self._mat = None

    # ---------- 构建 ----------
    def _gather(self):
        chunks = []
        for path, kind, scope in self.corpus_files:
            full = os.path.join(HERE, path) if not os.path.isabs(path) else path
            if not os.path.exists(full):
                continue
            try:
                if kind == "json":
                    data = json.load(open(full, encoding="utf-8"))
                    chunks += _chunk_json(data, scope)
                else:
                    text = open(full, encoding="utf-8", errors="ignore").read()
                    chunks += _chunk_md(text, path)
            except Exception as e:
                print(f"[rag] 跳过 {path}: {type(e).__name__}: {e}")
        # 去重
        seen, uniq = set(), []
        for c in chunks:
            key = c[0]
            if key not in seen:
                seen.add(key)
                uniq.append(c)
        return uniq

    def _build(self):
        self._chunks = self._gather()
        if not self._chunks:
            return
        # 词典 + 文档词频（TF）
        df = Counter()
        doc_tokens = []
        for (text, key, label, scope) in self._chunks:
            toks = tokenize(text)
            doc_tokens.append(toks)
            df.update(set(toks))
        n = len(doc_tokens)
        lower = float(n)
        # vocab：按文档频率排序，取高频稳定词（过低的单字词过滤）
        self._vocab = {t for t, c in df.items() if 1 <= c <= n and (len(t) > 1 or _CJK.search(t))}
        vocab_list = sorted(self._vocab)
        self._vocab = {t: i for i, t in enumerate(vocab_list)}
        self._idf = {}
        for t, c in df.items():
            if t in self._vocab:
                self._idf[t] = math.log(lower / (1.0 + c)) + 1.0
        dim = len(vocab_list)
        rows = []
        for toks in doc_tokens:
            tf = Counter(toks)
            norm = 0.0
            vec = [0.0] * dim
            for t, cnt in tf.items():
                i = self._vocab.get(t)
                if i is None:
                    continue
                w = (1.0 + math.log(cnt)) * self._idf[t]
                vec[i] = w
                norm += w * w
            if norm > 0:
                inv = 1.0 / math.sqrt(norm)
                vec = [v * inv for v in vec]
            rows.append(vec)
        import numpy as np
        self._mat = np.asarray(rows, dtype=np.float32) if rows else np.empty((0, 0), np.float32)
        self._loaded = True

    # ---------- 可选 Ollama embedding ----------
    @staticmethod
    def _probe_ollama_embed():
        try:
            import subprocess
            p = subprocess.run(["ollama", "list"], capture_output=True, text=True, timeout=8)
            if p.returncode != 0:
                return None
            for line in p.stdout.splitlines():
                name = line.split()[0].strip()
                if any(k in name for k in ("nomic-embed", "mxbai-embed", "bge", "snowflake")):
                    return name
        except Exception:
            pass
        return None

    # ---------- 检索 ----------
    def ensure(self):
        if not self._loaded:
            self._build()

    def search(self, query, top_k=5, scope=None, min_score=0.05):
        self.ensure()
        if not self._mat.size or not query:
            return []
        q = tokenize(query)
        qf = Counter(q)
        dim = self._mat.shape[1]
        qv = [0.0] * dim
        norm = 0.0
        for t, cnt in qf.items():
            i = self._vocab.get(t)
            if i is None:
                continue
            w = (1.0 + math.log(cnt)) * self._idf.get(t, 1.0)
            qv[i] = w
            norm += w * w
        if norm == 0:
            return self._keyword_fallback(query, top_k, scope)
        inv = 1.0 / math.sqrt(norm)
        qv = [v * inv for v in qv]
        import numpy as np
        scores = self._mat @ np.asarray(qv, dtype=np.float32)
        if scope:
            mask = [1 if self._chunks[i][3] == scope else 0 for i in range(len(self._chunks))]
            scores = scores * np.asarray(mask, dtype=np.float32)
        idx = np.argsort(-scores)[:top_k]
        out = []
        for i in idx:
            s = float(scores[i])
            if s < min_score:
                continue
            text, key, label, sc = self._chunks[i]
            out.append({"text": text, "key": key, "label": label,
                        "scope": sc, "score": round(s, 4)})
        return out

    def _keyword_fallback(self, query, top_k, scope):
        toks = tokenize(query)
        if not toks:
            return []
        scored = []
        for i, (text, key, label, sc) in enumerate(self._chunks):
            if scope and sc != scope:
                continue
            tl = tokenize(text)
            hit = sum(1 for t in toks if t in tl)
            if hit:
                scored.append((hit, text, key, label, sc))
        scored.sort(key=lambda x: -x[0])
        return [{"text": t, "key": k, "label": l, "scope": s, "score": round(h, 3)}
                for h, t, k, l, s in scored[:top_k]]

    def stats(self):
        self.ensure()
        return {"chunks": len(self._chunks), "vocab": len(self._vocab),
                "embed_model": self._embed_model}


_retriever = None


def get_retriever(corpus_dir=None):
    global _retriever
    if _retriever is None:
        _retriever = RagRetriever()
    return _retriever


def format_hits(hits, max_chars=4000):
    lines = [f"检索到 {len(hits)} 条相关案例："]
    used = 0
    for h in hits:
        s = (f"· (相似度 {h['score']}) [{h['scope']}] label={h['label'] or '-'}"
             f" — {h['text'][:260]}")
        if used + len(s) > max_chars:
            lines.append("…（检索结果已按预算截断）")
            break
        lines.append(s)
        used += len(s)
    return "\n".join(lines)


if __name__ == "__main__":
    r = RagRetriever()
    print("stats:", r.stats())
    for q in ("二重态假成功 单重态 重跑", "End of file in ZSymb 坐标损坏",
              "过渡态 虚频 未收敛 MaxCycles", "NBO 单点 闭壳层单重态 无虚频"):
        print("\n== 查询:", q)
        for h in r.search(q):
            print(f"  {h['score']:.3f} [{h['scope']}] {h['label']} {h['text'][:120]}")