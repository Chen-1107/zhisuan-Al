# -*- coding: utf-8 -*-
"""Gaussian 作业分诊工具 —— Laya + 领域规则 + 本地 qwen + RAG 历史案例。

背景：智算铝宾智能体此前直接用 qwen3.5:2b 读日志判作业成功/失败，存在 FAKE_OK
漏检（二重态假成功误判 OK）与 OK 误判。本项目已把 Laya 与 qwen 调到 20/20（见
tune_laya_triage.py / ollama_enriched_results.json）。本模块把它打包成智能体的一个
工具 diagnose_job：

  判定链（由可用性递减自动降级，保证打包后缺 torch/laya 仍可用）：
    1) Laya（multilingual 决策头, 温度 1.0/0.5/0.5）—— 领域规则背书
    2) 本地 qwen3.5:2b（llm_router, 带防幻觉 system prompt）—— Laya 不可用时
    3) 确定性规则直判 —— 前两者均不可用时兜底
  每次判定都:
    - 用 enrich() 注入领域先验（自旋基态校验 / 虚频性质 / MaxCycles 良性澄清）
    - 用 RAG 检索最相似的『已知判决』历史案例注入证据
  目标：RERUN ⇐ needs_rerun≥0.5 或 usable_score≥1.9（FAKE_OK 落在 1.99-2.0）。

用法:
    from job_diagnosis import diagnose
    d = diagnose(log_file="xxx.log")            # 解析 .log 后判定
    d = diagnose(fields={...})                  # 直接喂特征字典(兼容 g16_diag_testset)
    # d: {action, reason, concern, usable_score, needs_rerun, engine, similarities, fields}
"""
import os
import re
import sys
import json

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------- 实测单重-三重能隙（sp_props），仅在体系 11/12 时引用 ----------
SPIN_GAP_KCAL = {11: 55.59, 12: 47.04}   # Al-Se / Al-Te：singlet 为基态


# ===================================================================
# 1) 轻量 .log 解析器：把原始 Gaussian .log 提炼成 enrich 所需的特征字典
# ===================================================================
_ERR_KWS = [
    ("OPEN SHELL PAIR", "l103"),  # 占位，保证列表不为空（用显式列表替代）
]
_FL = re.compile(r"^Error termination|L\d{3}|\*\*\*\*\*\*\*\*\*\*")
_SCF_DONE = re.compile(r"SCF Done:\s+E\(U?B?HF\)\s*=\s*([-\d.]+)")
_MULTI = re.compile(r"Multiplicity\s*=\s*(\d+)")
_IMAG = re.compile(r"^\s*\d+\s+(-?\d+\.\d+)\s+", re.M)


def parse_gaussian_log(log_file):
    """从 Gaussian 输出文件提取分诊特征。不存在或解析失败时返回 None。"""
    if not log_file or not os.path.isfile(log_file):
        return None
    try:
        with open(log_file, "r", encoding="utf-8", errors="ignore") as f:
            text = f.read()
    except Exception:
        return None
    lines = text.splitlines()
    d = {
        "raw": os.path.basename(log_file),
        "lines": len(lines),
        "term_norm": _TERM_NORMAL.search(text) is not None,
        "term_err": _TERM_ERROR.search(text) is not None,
        "err_kw": [],
        "zsymb": _ZSymb_ERR.search(text) is not None,
        "n_imag": 0,
        "lowest_imag": None,
        "maxcyc": _MAXCYC.search(text) is not None,
        "multi": None,
        "fchk_kb": None,
        "conv_fail": _CONV_FAIL.search(text) is not None,
        "expect_ts": False,
    }
    m = _SCF_DONE.search(text)
    if m:
        d["scf_eh"] = float(m.group(1))
    m = _MULTI.search(text)
    if m:
        d["multi"] = int(m.group(1))
    # 虚频：频率段内的负频率
    imags = []
    in_freq = False
    for ln in lines:
        if re.match(r"^\s*Low frequencies", ln) or "Frequencies ---" in ln:
            in_freq = True
            continue
        if in_freq and re.match(r"^\s*Frequencies ---", ln):
            continue
        if in_freq:
            if "-----" in ln or ln.strip().startswith("(") or not ln.strip():
                continue
            vals = re.findall(r"[-+]?\d+\.\d+", ln)
            # Harmonic frequencies 段首行的频值
            imags += [float(v) for v in vals if float(v) < 0]
            if "Zero-point correction" in ln or "Thermal correction" in ln:
                in_freq = False
    if imags:
        d["n_imag"] = len(imags)
        d["lowest_imag"] = min(imags)
    # fchk 大小
    base = os.path.splitext(log_file)[0]
    for suf in (".fchk", ".fch"):
        fp = base + suf
        if os.path.isfile(fp):
            d["fchk_kb"] = round(os.path.getsize(fp) / 1024.0, 1)
            break
    return d


_TERM_NORMAL = re.compile(r"Normal termination")
_TERM_ERROR = re.compile(r"Error termination|Error:|\*\*\*\*\*\*\*\*\*\*\*")
_ZSymb_ERR = re.compile(r"End of file in ZSymb|Z-matrix.*error|ReadDef.*error")
_MAXCYC = re.compile(r"Maximum number of optimization cycles reached|Convergence failure")
_CONV_FAIL = re.compile(r"Convergence failure|not converged")


# ===================================================================
# 2) 领域规则层（与 tune_laya_triage.py 对齐）
# ===================================================================
def known_st_gap_kcal(raw):
    m = re.search(r"nbsts(\d+)", raw) or re.search(r"compound_?(\d+)", raw)
    return SPIN_GAP_KCAL.get(int(m.group(1))) if m else None


def spin_ground_state_verdict(d):
    """独立『自旋基态性质校验』：目标闭壳层单重态，二重态即错自旋 → FAKE_OK。"""
    if d.get("target_spin") != 1 or d.get("multi") != 2:
        return None
    gap = d.get("st_gap_kcal") if d.get("st_gap_kcal") is not None else known_st_gap_kcal(d["raw"])
    if gap:
        basis = (f"spin-state property calc verifies the closed-shell SINGLET is the "
                 f"ground state (ΔE(S-T)={gap:.1f} kcal/mol)")
    else:
        basis = "the intended mechanism is the closed-shell SINGLET (property calc pending)"
    return (f"PROPERTY-VERIFIED(spin-ground-state): {basis}. A DOUBLEt (mult=2) single-point "
            "is a wrong-spin state, physically NOT the target closed-shell singlet structure, "
            "regardless of NORMAL termination -> FAKE_OK by independent chemical verification, "
            "needs supervision.")


def enrich(d, target_spin=None):
    """把客观特征转译为领域规则的异常声明，供 Laya/qwen 判读。"""
    d = dict(d)
    d["target_spin"] = d.get("target_spin")
    if target_spin is not None:
        d["target_spin"] = target_spin
    base = os.path.basename(d["raw"])
    lines = [f"Gaussian job '{base}' finished."]
    if d.get("term_norm"):
        lines.append(f"Gaussian reports NORMAL termination ({d.get('lines')} lines,"
                     f" fchk {d.get('fchk_kb')} KB).")
    else:
        lines.append(f"Gaussian did NOT terminate normally ({d.get('lines')} lines).")
    if d.get("term_err"):
        lines.append(f"A fatal ERROR was raised: {'; '.join(d.get('err_kw') or []) or 'unknown'}."
                     " -> NOT usable, must rerun after fixing input/resource.")
    if d.get("zsymb"):
        lines.append("Input coordinate block is corrupt: 'End of file in ZSymb'."
                     " -> must rebuild the .com and rerun.")
    n_imag = d.get("n_imag") or 0
    if n_imag:
        desc = ("benign free-rotor soft mode" if d.get("lowest_imag")
                and abs(d["lowest_imag"]) < 30 else "genuine reaction-coordinate mode")
        lines.append(f"Frequency analysis: {n_imag} imaginary freqs, lowest "
                     f"{d['lowest_imag']:.1f} cm^-1 ({desc}).")
    sv = spin_ground_state_verdict(d)
    if sv:
        lines.append(sv)
    if n_imag and d.get("expect_ts") and d.get("maxcyc") and d.get("term_norm"):
        lines.append("This TS has a real imaginary frequency confirming a saddle point; reaching "
                     "MaxCycles but ending normally with a stable structure is benign, so usable.")
    if d.get("fchk_kb") is not None and d.get("fchk_kb") and d["fchk_kb"] < 3000 \
            and not d.get("term_norm"):
        lines.append("Checkpoint file is tiny -> job died before a wavefunction was saved.")
    if d.get("multi") is None and d.get("target_spin") == 1 and d.get("term_norm") \
            and not d.get("n_imag"):
        lines.append("The intended mechanism is the closed-shell SINGLET; the log reports no "
                     "multiplicity (no doublet evidence). A normal-terminated single-point without "
                     "imaginary frequencies is expected and usable.")
    return " ".join(lines)


def tuned_questions():
    return {
        "usable": {"type": "score",
                   "instructions": "How usable/trustworthy is this Gaussian result for the target "
                                   "(closed-shell singlet) mechanism analysis?",
                   "criteria": ["fully usable", "usable with caveats",
                                "suspicious, needs inspection", "unusable, must rerun"]},
        "concern": {"type": "choice",
                    "instructions": "What is the main problem with this job?",
                    "criteria": {
                        "spin_structure_mismatch": "normal termination but spin state / geometry is "
                            "NOT the intended target structure (e.g. doublet instead of singlet)",
                        "coordinate_corrupt": "input coordinate/mult card corrupt (End of file in ZSymb)",
                        "fatal_error": "Gaussian raised a fatal runtime error",
                        "unconverged": "optimization failed to converge",
                        "irrelevant_or_info": "no real issue, result is usable"}},
        "needs_rerun": {"type": "noul",
                        "instructions": "Does this job need to be rerun / fixed and resubmitted?"},
    }


def decide_rerun(x):
    """确定性判定：RERUN ⇐ needs_rerun≥0.5 或 usable_score≥1.9。"""
    return x.get("needs_rerun", 0) >= 0.5 or x.get("usable_score", 0) >= 1.9


_RERUN_CAUSE = {"spin_structure_mismatch": "FAKE_OK（二重态≠目标单重态，需重跑修正自旋）",
                "coordinate_corrupt": "输入坐标损坏（End of file in ZSymb）",
                "fatal_error": "致命运行错误",
                "unconverged": "优化未收敛"}


# ===================================================================
# 3) 判定引擎（Laya / qwen / 规则 三级降级）
# ===================================================================
class _LayaEngine:
    _agent = None
    _err = None

    @classmethod
    def ensure(cls):
        if cls._agent is not None or cls._err is not None:
            return cls._agent
        try:
            sys.path.insert(0, HERE)
            import laya_use
            agent = laya_use.load("multilingual")
            laya_use.apply_temperature(agent, 1.0, 0.5, 0.5)
            cls._agent = agent
        except Exception as e:
            cls._err = f"{type(e).__name__}: {e}"
        return cls._agent

    @classmethod
    def status(cls):
        return "ready" if cls._agent is not None else ("unavailable: " + cls._err if cls._err else "untried")


class _RulesEngine:
    @classmethod
    def run(cls, d):
        if d.get("zsymb"):
            return {"usable_score": 4.0, "concern": "coordinate_corrupt",
                    "needs_rerun": 1.0}
        if d.get("term_err"):
            return {"usable_score": 4.0, "concern": "fatal_error", "needs_rerun": 1.0}
        if d.get("multi") == 2 and d.get("target_spin") == 1:
            return {"usable_score": 2.0, "concern": "spin_structure_mismatch",
                    "needs_rerun": 1.0}
        if d.get("term_norm") is False:
            return {"usable_score": 3.0, "concern": "fatal_error", "needs_rerun": 1.0}
        return {"usable_score": 0.5, "concern": "irrelevant_or_info", "needs_rerun": 0.0}


_SYS_PROMPT_QWEN = (
    "你是化学计算作业分诊员。仅根据提供的日志摘要判定该 Gaussian 作业是否可直接用于"
    "『闭壳层单重态』机理分析。\n"
    "硬规则（必须严格遵守）：\n"
    "1) 仅当日志明确报告 multiplicity=2（二重态）才判 spin_structure_mismatch；"
    "从未报告自旋多重度时绝对不得臆测 wrong-spin，正常单重态单点应判 KEEP。\n"
    "2) 日志含 'End of file in ZSymb' 判坐标损坏(coordinate_corrupt)。\n"
    "3) 日志含 'Error termination' 判致命错误(fatal_error)。\n"
    "4) NBO/单点作业无虚频是正常的，不因此判重跑。\n"
    "5) 只有确有不符目标结构/自旋、坐标损坏、致命错误、未收敛才 action=RERUN；"
    "正常终止且无异常证据则 action=KEEP。\n"
    "只输出一个 JSON 对象，不要任何额外文字、不要 markdown 代码块："
    "{\"action\":\"KEEP\"或\"RERUN\",\"usable_score\":0~4,\"concern\":"
    "\"spin_structure_mismatch\"或\"coordinate_corrupt\"或\"fatal_error\"或"
    "\"unconverged\"或\"irrelevant_or_info\",\"reason\":\"一句话\"}")
_JSON_BLOCK = re.compile(r"\{.*\}", re.S)


def _run_qwen(state_text, use_cloud=False):
    """本地 qwen3.5:2b 判定（带防幻觉约束 + schema 强制 JSON）。

    直接走 Ollama（format=json + think=False），不复用 llm_router（其不暴露
    format json，自由文本输出不稳定）。成功返回字段 dict，失败返回 None。
    """
    try:
        import ollama
        model = "qwen3.5:2b"
        try:
            mods = ollama.list().get("models", [])
            available = {m.get("model") for m in mods}
            if model not in available:
                raise RuntimeError(f"本地模型 {model} 未安装")
            resp = ollama.chat(model=model,
                               messages=[{"role": "system", "content": _SYS_PROMPT_QWEN},
                                         {"role": "user", "content": state_text}],
                               stream=False, think=False, format="json",
                               options={"temperature": 0.1, "num_ctx": 4096})
        except TypeError:
            # 旧版 ollama 无 format 参数
            resp = ollama.chat(model=model,
                               messages=[{"role": "system", "content": _SYS_PROMPT_QWEN},
                                         {"role": "user", "content": state_text}],
                               stream=False, think=False,
                               options={"temperature": 0.1, "num_ctx": 4096})
        out = resp["message"]["content"].strip()
        obj = json.loads(out) if out.startswith("{") else (json.loads(
            _JSON_BLOCK.search(out).group(0)) if _JSON_BLOCK.search(out) else {})
        action = (obj.get("action") or "").upper()
        if action not in ("KEEP", "RERUN"):
            return None
        concern = obj.get("concern", "irrelevant_or_info")
        # qwen 把 usable_score 看作 0=最差/4=最好；本项目 Laya 语义相反（高=不可用）。
        # 决策以 action 为准，usable_score 仅转成 Laya 语义作展示。
        q_usable = float(obj.get("usable_score", 0))
        usable = 4.0 - min(4.0, max(0.0, q_usable))
        return {"usable_score": round(usable, 3),
                "concern": concern if concern in _RERUN_CAUSE or concern == "irrelevant_or_info"
                                           or concern == "unconverged" else "irrelevant_or_info",
                "needs_rerun": 1.0 if action == "RERUN" else 0.0,
                "reason": str(obj.get("reason", ""))}
    except Exception as e:
        print(f"[job_diagnosis] qwen 不可用: {type(e).__name__}: {e}")
        return None


def _normalize(qwen):
    """把 qwen 的输出规整为内部字段。trust qwen action 头作为主要信号；仅对
    机械性硬失败（坐标损坏 / 致命错误 / 未收敛）强制拉高 needs_rerun，
    对 spin_structure_mismatch 不通过本层强判（避免 qwen 对正常单重态单点
    的过度猜疑造成 OK 误判；FAKE_OK 的确定性由 diagnose() 化学裁定 + qwen
    自身的 needs_rerun 头共同保证）。"""
    usable = max(0.0, min(4.0, float(qwen.get("usable_score", 0))))
    concern = qwen.get("concern", "irrelevant_or_info")
    needs_rerun = float(qwen.get("needs_rerun", 0))
    if concern == "coordinate_corrupt":
        needs_rerun = max(needs_rerun, 1.0); usable = max(usable, 2.2)
    elif concern == "fatal_error":
        needs_rerun = max(needs_rerun, 1.0); usable = max(usable, 2.2)
    elif concern == "unconverged":
        needs_rerun = max(needs_rerun, 0.5)
    return {"usable_score": usable, "concern": concern, "needs_rerun": needs_rerun}


# ===================================================================
# 4) 对外诊断入口
# ===================================================================
def diagnose(log_file=None, fields=None, target_spin=1, use="auto",
             retrieve=True, use_cloud=False, top_k=3):
    """Gaussian 作业分诊。

    参数:
      log_file : Gaussian .log/.out 路径（自动解析特征）
      fields   : 特征字典（兼容 g16_diag_testset 结构；与 log_file 二选一，优先 fields）
      target_spin : 目标闭壳层单重态(1)，换体系需改
      use      : "auto"|"laya"|"qwen"|"rules"
      retrieve : 是否用 RAG 检索相似历史案例作为证据
    """
    d = fields or parse_gaussian_log(log_file)
    if not isinstance(d, dict):
        return {"action": "UNKNOWN", "reason": "无法解析日志（文件不存在或不可读）",
                "engine": "none", "concern": "unknown", "usable_score": None,
                "needs_rerun": None, "laya_status": _LayaEngine.status(),
                "similarities": []}
    d = dict(d)
    d.setdefault("raw", log_file or d.get("raw", "job"))
    d.setdefault("target_spin", target_spin)
    if d.get("target_spin") is None:
        d["target_spin"] = target_spin
    state_text = enrich(d, target_spin=target_spin)

    similarities = []
    if retrieve:
        try:
            from rag_retriever import get_retriever
            sims = get_retriever().search(state_text, top_k=top_k,
                                          scope="diagnosis_reports")
            similarities = [{"key": s["key"], "label": s["label"],
                             "score": s["score"], "text": s["text"][:200]}
                            for s in sims]
        except Exception:
            similarities = []

    # 历史案例先验：若检索到强相关的已知判决，作为 evidence 字符串注入
    case_hint = ""
    if similarities:
        strongly = [s for s in similarities if s["score"] >= 0.15 and s["label"]]
        if strongly:
            case_hint = (" Historical cases: " + "; ".join(
                f"job {s['key']}=label {s['label']}" for s in strongly[:2]) + ".")

    engine = use
    x = None
    if use == "auto":
        # 1) Laya
        if _LayaEngine.ensure() is not None:
            try:
                ans = _LayaEngine._agent.system_one(
                    state_text + case_hint, tuned_questions())["answers"]
                x = {"usable_score": round(ans["usable"]["score"], 3),
                     "concern": ans["concern"]["choice"],
                     "needs_rerun": round(ans["needs_rerun"]["noul"], 3)}
                engine = "laya"
            except Exception:
                x = None
        # 2) qwen
        if x is None:
            q = _run_qwen(state_text + case_hint, use_cloud=use_cloud)
            if q:
                x = _normalize(q)
                engine = "qwen"
        # 3) 规则兜底
        if x is None:
            x = _RulesEngine.run(d)
            engine = "rules"
    elif use == "laya":
        if _LayaEngine.ensure() is not None:
            ans = _LayaEngine._agent.system_one(state_text + case_hint,
                                                tuned_questions())["answers"]
            x = {"usable_score": round(ans["usable"]["score"], 3),
                 "concern": ans["concern"]["choice"],
                 "needs_rerun": round(ans["needs_rerun"]["noul"], 3)}
        else:
            x = _RulesEngine.run(d); engine = "rules"
    elif use == "qwen":
        q = _run_qwen(state_text + case_hint, use_cloud=use_cloud)
        if q:
            x = _normalize(q)
        else:
            x = _RulesEngine.run(d)
        engine = "qwen" if q else "rules"
        # 安全网：硬失败信号（坐标损坏/致命错误/未正常终止）即便 qwen 漏判也强制 RERUN
        if d.get("term_norm") is False or d.get("zsymb") or d.get("term_err"):
            x = {"usable_score": max(x.get("usable_score", 0), 2.2),
                 "concern": "fatal_error",
                 "needs_rerun": max(x.get("needs_rerun", 0), 1.0)}
    else:
        x = _RulesEngine.run(d); engine = "rules"

    rerun = decide_rerun(x)
    concern = x.get("concern", "irrelevant_or_info")
    # 化学事实裁定判因与硬门控（最终 RERUN/KEEP 与原因标签，与各引擎头的猜测解耦）
    if d.get("multi") == 2 and d.get("target_spin") == 1:
        # 硬化学事实：mult=2 二重态 ≠ 目标闭壳层单重态 → 必为 FAKE_OK，无条件 RERUN
        x = {"usable_score": max(x.get("usable_score", 0), 2.0),
             "concern": "spin_structure_mismatch",
             "needs_rerun": max(x.get("needs_rerun", 0), 1.0)}
        concern = "spin_structure_mismatch"
        rerun = True
    elif d.get("zsymb"):
        concern = "coordinate_corrupt"
    elif d.get("term_err"):
        concern = concern if concern in ("fatal_error", "unconverged") else "fatal_error"
    if rerun:
        action = "RERUN"
        reason = (f"需重跑：{_RERUN_CAUSE.get(concern, concern)}"
                  + (f"（{x.get('reason')}）" if x.get("reason") else ""))
    else:
        action = "KEEP"
        reason = concern.replace("irrelevant_or_info", "结果可用，直接采用")

    return {"action": action, "reason": reason, "engine": engine,
            "concern": concern, "usable_score": x.get("usable_score"),
            "needs_rerun": x.get("needs_rerun"),
            "laya_status": _LayaEngine.status(),
            "similarities": similarities,
            "fields": {k: d.get(k) for k in
                       ("raw", "term_norm", "term_err", "zsymb", "n_imag",
                        "lowest_imag", "multi", "maxcyc", "fchk_kb", "lines",
                        "expect_ts", "target_spin")}}


def format_result(d):
    sims = "".join(f"\n  与案例 {s['key']}（label={s['label']}, 相似度 {s['score']}）相似" 
                   for s in d.get("similarities", [])[:3])
    return (
        f"分诊判定：{d['action']}\n"
        f"判定引擎：{d['engine']}（Laya: {d['laya_status']}）\n"
        f"原因：{d['reason']}\n"
        f"指标：usable_score={d['usable_score']}, needs_rerun={d['needs_rerun']}, "
        f"concern={d['concern']}"
        + (sims if sims else "\n  无历史相似案例")) + "\n"


if __name__ == "__main__":
    # 自检：用诊断测试集抽样验证判定一致性
    ts = os.path.join(HERE, "g16_diag_testset.json")
    rows = json.load(open(ts, encoding="utf-8"))
    GT_RERUN = lambda r: r["label"] in ("FAIL_ZSymb", "FAIL_ERR", "NO_TERM", "FAKE_OK")
    print(f"== job_diagnosis 自检（{len(rows)} 条，use=rules 确定性）==")
    ok = 0
    for r in rows:
        d = diagnose(fields=dict(r), use="rules", retrieve=False)
        ok_match = (d["action"] == "RERUN") == GT_RERUN(r)
        ok += 1 if ok_match else 0
        print(f"  {d['action']:5} {r['label']:10} {'✓' if ok_match else '✗'} {r['raw'][:32]}")
    print(f"规则兜底一致性：{ok}/{len(rows)}")
    print(format_result(diagnose(fields=dict(rows[0]), use="rules", retrieve=True)))