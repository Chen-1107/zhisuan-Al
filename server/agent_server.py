# -*- coding: utf-8 -*-
"""
agent_server.py - 智算铝宾 Python 服务层（Java GUI 的后端桥）

职责：
  1. 以 HTTP + SSE 对外提供能力（Java Swing GUI 为主要客户端）
  2. 复用 zhisuan_aluminylene.py 的 Agent 工具逻辑（剥离 Tkinter 依赖，现注册 33 个工具）
  3. 复用 ReAct Agent 循环（Ollama, qwen3.5:2b）
  4. 调度 C++ 核心：molopt.exe（力场/MDP/Q-Learning）与 molview.exe（3D 查看器）

通信约定（全部 JSON，UTF-8）：
  GET  /api/health                     服务与各组件状态
  POST /api/chat       {messages:[...]} SSE 流式对话
  POST /api/agent      {messages:[...]} SSE ReAct Agent（工具调用事件流）
  GET  /api/compounds?q=名称           查询本地化合物库
  POST /api/optimize   {smiles,episodes,max_steps}   C++/Python MDP 优化
  POST /api/properties {smiles}        RDKit 分子性质
  POST /api/visualize  {smiles,show_hydrogen}        导出XYZ并启动molview.exe
  POST /api/parse_spectrum {file}      cclib 解析 Gaussian/ORCA 输出
  GET  /api/knowledge/categories       知识库分类
  GET  /api/knowledge/skills?category= 知识库技能列表（支持q=搜索）
  GET  /api/knowledge/skill?name=      技能全文
  GET  /api/workflows                  工作流列表
  POST /api/workflow/run {id}           执行工作流
  GET  /api/settings                   读取设置（~/zhisuan_config.json）
  GET  /api/dft_config                 DFT 后端配置（Gaussian/ORCA/Multiwfn/SSH/工作区）
  POST /api/dft_config {...}           保存并热应用 DFT 后端配置（dft_config.json）
  POST /api/dft_test {target}         测试连通性：g16/orca/multiwfn/workspace/ssh
  POST /api/task/submit {tool,params} 异步提交任务（立即返回 task_id）
  GET  /api/task/status?id=            任务状态轮询（running/done/failed/interrupted）
  GET  /api/task/list                  最近 50 条任务列表
  GET  /api/workflow/status            工作流状态快照（节点id→status/message/energy/log_tail，供Swing轮询）
  POST /api/diagnose   {log,fields_json,target_spin,use}  结果审查台直连分诊（KEEP/RERUN + 证据链）

启动：python agent_server.py [端口，默认8765]
测试：curl http://127.0.0.1:8765/api/health
"""
import asyncio
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

# ---------- 实例状态隔离（多实例并行：GUI-A 审查台 / GUI-B 调度中心 各自独立状态） ----------
# 通过环境变量 ZHISUAN_STATE_DIR 指定独立状态目录（tasks.json / dft_config.json /
# zhisuan_config.json / model_config.json 均落入该目录），未设置时保持原工作目录行为。
_STATE_DIR = os.environ.get("ZHISUAN_STATE_DIR", "").strip()
if _STATE_DIR:
    try:
        os.makedirs(_STATE_DIR, exist_ok=True)
    except Exception:
        _STATE_DIR = ""


def _state_path(filename):
    if _STATE_DIR:
        return os.path.join(_STATE_DIR, filename)
    return os.path.join(HERE, filename)

# ---------- 可选依赖（与主程序相同的降级策略） ----------
def _dump_import_err(tag):
    """打包环境导入失败时落盘 traceback，便于诊断（开发环境同样生效）"""
    import traceback
    try:
        with open(os.path.join(tempfile.gettempdir(), "zs_import_err.log"), "a",
                  encoding="utf-8") as f:
            f.write(f"[{tag}]\n{traceback.format_exc()}\n")
    except Exception:
        pass

MODEL_NAME = "qwen3.5:2b"

try:
    import ollama
    OLLAMA_AVAILABLE = True
except Exception:
    OLLAMA_AVAILABLE = False
    _dump_import_err("ollama")

# LLM 上下文窗口（tokens）：Ollama 默认 num_ctx 较小（qwen 系列默认 4096），
# ReAct 多轮工具调用的 system prompt + 历史消息会迅速挤满窗口导致遗忘/错乱，故显式扩容
LLM_NUM_CTX = int(os.environ.get("ZS_NUM_CTX", "16384"))

# ---------- 模型分层路由（本地优先，可选「本地 + 云端强模型」混合） ----------
# 默认 local：数据不出机；hybrid/cloud 需在设置中显式授权云端数据出机（免责默认）。
try:
    import llm_router
    LLM_ROUTER = llm_router.router()
    MODEL_NAME = LLM_ROUTER.local_model()          # 兼容既有引用：默认本地模型
except Exception as _router_err:
    LLM_ROUTER = None
    MODEL_NAME = "qwen3.5:2b"


def _hybrid_escalate(body, text):
    """hybrid 模式下，复杂 TS/扫描类请求是否自动升级到云端强模型。

    需同时满足：后端为 hybrid、开启 cloud_ts_auto、云端已配置且已授权数据出机。
    未授权或纯 local 模式一律返回 False（数据不出机的免责默认）。
    """
    if not (LLM_ROUTER and LLM_ROUTER.backend == "hybrid"
            and LLM_ROUTER.config.get("cloud_ts_auto")
            and LLM_ROUTER.cloud_configured()
            and LLM_ROUTER.privacy_consented()):
        return False
    if body.get("use_cloud") is True:
        return True
    return bool(text) and LLM_ROUTER.is_ts_task(text)

try:
    from mol_optimizer import (run_optimization, smiles_to_molecule_graph,
                               visualize_molecule_3d)
    MOL_AVAILABLE = True
except Exception:
    MOL_AVAILABLE = False
    _dump_import_err("mol_optimizer")

try:
    from knowledge_base import get_knowledge_base
    KB = get_knowledge_base()
    KB_AVAILABLE = True
except Exception:
    KB = None
    KB_AVAILABLE = False

try:
    from automation import AutomationEngine
    AUTOMATION = AutomationEngine()
    AUTOMATION_AVAILABLE = True
except Exception:
    AUTOMATION = None
    AUTOMATION_AVAILABLE = False

try:
    from dft_backend import DFTTaskManager, DFTInput
    DFT_MANAGER = DFTTaskManager()
    DFT_AVAILABLE = True
except Exception:
    DFT_MANAGER = None
    DFT_AVAILABLE = False

try:
    from mcp.workflow_engine import WorkflowEngine
    from mcp.mcp_chat import ChatModule
    from mcp.mcp_structure import StructureModule
    from mcp.mcp_dft import DFTModule
    from mcp.mcp_analysis import AnalysisModule
    from mcp.mcp_method import MethodModule
    WF_ENGINE = WorkflowEngine()
    for _n, _m in {"chat": ChatModule(), "structure": StructureModule(),
                   "dft": DFTModule(), "analysis": AnalysisModule(),
                   "method": MethodModule()}.items():
        WF_ENGINE.register_module(_n, _m)
    WF_ENGINE.create_workflow(
        "fixed_release_ts",
        [{"id": "load_smiles", "type": "load_from_pubchem", "module": "structure", "config": {}},
         {"id": "optimize", "type": "optimize_structure", "module": "structure", "config": {"episodes": 30}},
         {"id": "gaussian_ts", "type": "gaussian_ts", "module": "dft", "config": {"method": "B3LYP", "basis": "6-31G(d)"}},
         {"id": "parse", "type": "parse_log", "module": "analysis", "config": {}}],
        [{"source": "load_smiles", "source_port": "smiles", "target": "optimize", "target_port": "smiles"},
         {"source": "optimize", "source_port": "optimized_file", "target": "gaussian_ts", "target_port": "structure"},
         {"source": "gaussian_ts", "source_port": "log_file", "target": "parse", "target_port": "log_file"}])
    WF_AVAILABLE = True
except Exception:
    WF_ENGINE = None
    WF_AVAILABLE = False

MOLOPT_EXE = os.path.join(HERE, "molopt.exe")
MOLVIEW_EXE = os.path.join(HERE, "molview.exe")
CONFIG_PATH = _state_path("zhisuan_config.json")
DFT_CONFIG_PATH = _state_path("dft_config.json")

_ATNUM2SYM = {1: "H", 2: "He", 3: "Li", 4: "Be", 5: "B", 6: "C", 7: "N", 8: "O",
              9: "F", 10: "Ne", 11: "Na", 12: "Mg", 13: "Al", 14: "Si", 15: "P",
              16: "S", 17: "Cl", 18: "Ar", 19: "K", 20: "Ca", 21: "Sc", 22: "Ti",
              23: "V", 24: "Cr", 25: "Mn", 26: "Fe", 27: "Co", 28: "Ni", 29: "Cu",
              30: "Zn", 31: "Ga", 32: "Ge", 33: "As", 34: "Se", 35: "Br", 36: "Kr",
              37: "Rb", 38: "Sr", 39: "Y", 40: "Zr", 41: "Nb", 42: "Mo", 43: "Tc",
              44: "Ru", 45: "Rh", 46: "Pd", 47: "Ag", 48: "Cd", 49: "In", 50: "Sn",
              51: "Sb", 52: "Te", 53: "I", 54: "Xe", 55: "Cs", 56: "Ba", 57: "La",
              58: "Ce", 59: "Pr", 60: "Nd", 61: "Pm", 62: "Sm", 63: "Eu", 64: "Gd",
              65: "Tb", 66: "Dy", 67: "Ho", 68: "Er", 69: "Tm", 70: "Yb", 71: "Lu",
              72: "Hf", 73: "Ta", 74: "W", 75: "Re", 76: "Os", 77: "Ir", 78: "Pt",
              79: "Au", 80: "Hg", 81: "Tl", 82: "Pb", 83: "Bi", 84: "Po", 85: "At",
              86: "Rn", 87: "Fr", 88: "Ra", 89: "Ac", 90: "Th", 91: "Pa", 92: "U"}

DFT_CONFIG_FIELDS = ("g16_command", "orca_command", "multiwfn_command", "workspace",
                     "default_backend", "ssh_host", "ssh_username", "ssh_password",
                     "ssh_key", "ssh_work_dir", "ssh_queue", "ssh_queue_name",
                     "ssh_g16_root", "ssh_g16_scratch",
                     "qe_wsl_distro", "qe_command",
                     "pyscf_docker_container", "pyscf_docker_python")


# ---------- DFT 计算后端配置（Gaussian / ORCA / Multiwfn / SSH / 工作区） ----------
def _load_dft_config():
    cfg = {}
    try:
        if os.path.exists(DFT_CONFIG_PATH):
            with open(DFT_CONFIG_PATH, "r", encoding="utf-8") as f:
                loaded = json.load(f)
                if isinstance(loaded, dict):
                    cfg = {k: str(loaded.get(k, "") or "") for k in DFT_CONFIG_FIELDS}
    except Exception as e:
        print(f"[dft_config] 配置文件读取失败: {e}")
    return cfg


def _save_dft_config(cfg):
    with open(DFT_CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(cfg, f, ensure_ascii=False, indent=2)


def _apply_dft_config(cfg):
    """将配置热应用到运行中的 DFT_MANAGER；返回可用后端列表"""
    if not DFT_MANAGER or not hasattr(DFT_MANAGER, "apply_config"):
        return []
    try:
        return DFT_MANAGER.apply_config(cfg or {})
    except Exception as e:
        print(f"[dft_config] 应用失败: {e}")
        return list(DFT_MANAGER.backends.keys())


_apply_dft_config(_load_dft_config())

# ---------- 化合物数据库 ----------
_compound_db = None
_compound_db_error = None


def _load_compound_db():
    global _compound_db, _compound_db_error
    if _compound_db is None:
        path = os.path.join(HERE, "data", "compound_map.json")
        try:
            with open(path, "r", encoding="utf-8") as f:
                _compound_db = json.load(f).get("name_to_smiles", {})
        except Exception as e:
            _compound_db = {}
            _compound_db_error = f"无法加载化合物数据库（{path}）：{e}"
    return _compound_db


def _lookup_compound(name):
    db = _load_compound_db()
    if not db:
        return []
    name = (name or "").strip()
    if name in db:
        return [(name, db[name])]

    def _norm(s):
        return re.sub(r"[\s（）()：:]", "", s).lower()

    target = _norm(name)
    for k, v in db.items():
        if _norm(k) == target:
            return [(k, v)]
    hits = [(k, v) for k, v in db.items() if target and target in _norm(k)]
    if not hits:
        m = re.search(r"(\d+)", name)
        if m:
            num = m.group(1)
            hits = [(k, v) for k, v in db.items() if re.search(rf"\(\s*{num}\s*\)", k)]
    return hits


def _resolve_smiles(smiles):
    """中文名称 -> SMILES 自动解析"""
    if smiles and re.search(r"[\u4e00-\u9fff]", smiles):
        hits = _lookup_compound(smiles)
        if hits:
            return hits[0][1]
    return smiles


# ======================================================================
# 会话级短期记忆（解决跨轮遗忘）
# 原理：GUI 每次把完整消息历史传给 /api/agent，但旧实现只用最后 8 轮，
# 导致更早会话中已确认的 SMILES、参考键长、结论在窗口滚动后被忘记。
# 这里从整段历史中抽取关键事实压缩成「记忆块」注入 prompt，早期事实
# 不再丢失，同时仍保留最近若干轮原文作为即时上下文。
# ======================================================================
_SESSION_FACTS_LOCK = threading.Lock()
_SESSION_FACT_CACHE = {}          # session_id -> list[str]（跨请求缓存，可选用）
_SESSION_CACHE_MAX = 200
_SESSION_CACHE_TTL = 3600.0       # 秒，过期条目不再复用
_SHORT_MEM_MAX = 12               # 单次注入的短期事实条数上限

# ---------- 长时记忆（跨会话持久化，落地 long_term_memory.json） ----------
_LTM_PATH = os.path.join(HERE, "long_term_memory.json")
_LTM_LOCK = threading.Lock()
_LTM_STATE = {"entries": []}      # 惰性加载
_LTM_LOADED = False
_LTM_MAX = 300                    # 条目总数上限
_LTM_MAX_AGE = 180 * 86400        # 低于 180 天且命中少的条目将被剪枝
_LTM_INJECT_MAX = 8               # 单次注入的长时记忆条数上限
_RELEVANCE_MIN = 0.12             # 相关性（bigram Dice）阈值
_MEMORY_CHAR_CAP = 1400           # 记忆块字符预算（防记忆反噬上下文）
_PRIOR_CHAR_BUDGET = 8000         # 上文历史字符预算（防历史原文稀释当前意图）

# 可识别的化合物中文名（含优先级较高的番号形式，如 "Se-铝宾 (19)"），
# 用于从文本里锁定「名称 -> SMILES」映射行。
_COMPOUND_KEY_RE = re.compile(
    r"((?:[A-Za-z0-9\-]*\s*)?[\u4e00-\u9fff][\u4e00-\u9fff()a-zA-Z0-9\-*·]*(?:\s*\d+)?)")

_FACT_METHOD_BASIS = re.compile(
    r"(B3LYP[-\w]*|PBE0|M06-2X|wB97(?:X|XD)[-\w]*|\w?B97X[-\w]*|"
    r"def2[A-Za-z-]*|6-31G[dx]*|6-311G[dx]*|cc-pVDZ|cc-pVTZ|SDD)",
    re.IGNORECASE)
_FACT_BONDLEN = re.compile(
    r"([A-Z][a-z]?)\s*[\-–—]\s*([A-Z][a-z]?)\s*"
    r"(?:键长|距离|间距|约为|约)?\s*[=:：≈]?\s*([0-9]+\.?[0-9]*)\s*Å")
_FACT_MAYER = re.compile(r"Mayer\s*键级[^\d]{0,12}([0-9]\.[0-9]{2,3})", re.IGNORECASE)
_FACT_VIBFREQ = re.compile(r"虚频数[:：]\s*([0-9]+)")

# ======================================================================
# 决策记忆：把「任务类型 + 精度 → 泛函/基组（体系特征）」沉淀为结构化事实，
# 使 query_functional / query_basis_set 等智能选择结论可跨会话复用；
# 并对含否定词的句子做极性判定，避免把「不建议使用 B3LYP」误沉淀成
# 正向设置（多轮降智 / 决策漂移的来源之一）。
# ======================================================================
_TASK_ZH = {"sp": "单点能", "opt": "几何优化", "freq": "频率分析",
            "ts": "过渡态搜索", "props": "性质/光谱"}
_PREC_ZH = {"fast": "快速", "standard": "标准", "high": "高精度"}

_DEC_FUNC = re.compile(
    r"(B3LYP[-\w]*|PBE0|M06-2X|wB97(?:X|XD)[-\w]*|\w?B97X[-\w]*|"
    r"CAM-B3LYP|B2PLYP|PBE[-\w]*|BP86[-\w]*)", re.IGNORECASE)
_DEC_BASIS = re.compile(
    r"(def2[A-Za-z-]*|6-31G[dx]*|6-311G[dx]*|cc-pV[DT]Z(?:-PP)?|SDD|STO-3G)",
    re.IGNORECASE)
_DEC_HEAD_TASK = re.compile(r"任务(?:类型)?[:：]\s*([\u4e00-\u9fff]+)")
_DEC_HEAD_PREC = re.compile(r"精度(?:档)?[:：]\s*([\u4e00-\u9fff]+)")
_DEC_REC_FUNC = re.compile(r"推荐\s*泛函[:：]\s*([^\n]+)")
_DEC_REC_BOTH = re.compile(r"推荐\s*泛函\s*/\s*基组[:：]\s*([^\n]+)")

# 强否定→判负；正向动词（含"不"前缀的"不建议/不使用"会被上文先拦下）
_NEG_MARK = ("不建议", "不使用", "不能用", "不用于", "不宜", "不需要", "无需",
             "不考虑", "不用", "勿用", "勿使用", "忌", "慎用", "避免", "不可",
             "劣于", "不如", "别用", "不要用", "不要")
_POS_MARK = ("推荐", "选用", "优先", "改用", "采用", "适合", "宜用", "设定",
             "建议使用", "建议用", "使用", "用")


def _sentence_around(text, token):
    """返回包含 token 的句子片段，用于局部极性判定（避免整段范围误判）。"""
    for seg in re.split(r"[。；;，,、()（）：:/\n]+", text or ""):
        if token.lower() in seg.lower():
            return seg
    return text or ""


def _token_polarity(text, token):
    """对文本中泛函/基组 token 判定正负向：仅当句内出现强否定词时返回 -1，
    否则视为正向（保守，保持旧行为对裸提及仍沉淀）。"""
    seg = _sentence_around(text, token)
    if any(w in seg for w in _NEG_MARK):
        return -1
    return 1


def _extract_decision_facts(content):
    """从决策工具输出中抽取结构化决策记忆，返回 (决策事实列表, 推荐行覆盖token集)。

    rec_covered 用于主抽取循环：推荐行已结构化沉淀，无需再按裸泛函/基组
    重复沉淀一条「计算设置」，避免信息冗余。
    """
    res, rec_covered = [], set()
    task_zh = _DEC_HEAD_TASK.search(content)
    prec_zh = _DEC_HEAD_PREC.search(content)
    if task_zh:
        tname = task_zh.group(1).rstrip("搜索") or "通用"
    else:
        tname = "通用"
    tag = f"{tname}/{prec_zh.group(1)}" if prec_zh else tname
    # 解析推荐行（泛函 或 泛函/基组 两种形态）
    func, basis = None, None
    for m in _DEC_REC_FUNC.finditer(content):
        val = m.group(1).strip()
        fm = _DEC_FUNC.search(val)
        if fm:
            func = func or fm.group(1)
            rec_covered.add(fm.group(1))
        bm = _DEC_BASIS.search(val)
        if bm:
            basis = basis or bm.group(1)
            rec_covered.add(bm.group(1))
        break
    for m in _DEC_REC_BOTH.finditer(content):
        val = m.group(1).strip()
        fm, bm = _DEC_FUNC.search(val), _DEC_BASIS.search(val)
        if fm:
            func = func or fm.group(1)
            rec_covered.add(fm.group(1))
        if bm:
            basis = basis or bm.group(1)
            rec_covered.add(bm.group(1))
        break
    if not func and not basis:
        return res, rec_covered
    feats = []
    if "重元素" in content or "金属" in content:
        feats.append("含重元素")
    if "激发态" in content or "性质/光谱" in content:
        feats.append("激发态/性质")
    feat = f" ({','.join(feats)})" if feats else ""
    if func and basis:
        res.append(f"决策: {tag} 泛函{func}/基组{basis}{feat}")
    elif func:
        res.append(f"决策: {tag} 泛函{func}{feat}")
    else:
        res.append(f"决策: {tag} 基组{basis}{feat}")
    return res, rec_covered


def _extract_session_facts(messages):
    """从完整消息历史中抽取关键事实，去重后返回事实列表。"""
    facts = set()

    def _add(x):
        if x and len(x) <= 200:
            facts.add(x.strip())

    for m in messages:
        role = m.get("role", "")
        content = str(m.get("content") or "")
        # 1) 工具返回的「名称的SMILES：...」映射
        for mm in re.finditer(r"([^：\n:，。]+?)\s*的SMILES[：:]\s*([A-Za-z0-9()=#@+\-[\]\\/]+)",
                              content):
            name, smi = mm.group(1).strip(), mm.group(2).strip()
            if 1 < len(smi) <= 400:
                _add(f"化合物映射: 「{name}」 = {smi}")
        # 2) 决策记忆：结构化沉淀「任务×精度→泛函/基组(体系特征)」
        #    使 query_functional / query_basis_set 等选择结论可跨会话复用
        dec_facts, rec_covered = _extract_decision_facts(content)
        for d in dec_facts:
            _add(d)
        # 3) 显式的 泛函/基组 偏好（带极性判定：
        #    否定句如「不建议使用 B3LYP」不误沉淀为正向设置）
        for mm in _FACT_METHOD_BASIS.finditer(content):
            t = mm.group(1)
            if not t or t in rec_covered:
                continue
            if _token_polarity(content, t) < 0:
                _add(f"设置应避免: {t}")
            else:
                _add(f"计算设置: {t}")
        # 4) 关键结构/结果事实（键长、键级、虚频）
        for mm in _FACT_BONDLEN.finditer(content):
            _add(f"键长: {mm.group(1)}-{mm.group(2)} ≈ {mm.group(3)} Å")
        for mm in _FACT_MAYER.finditer(content):
            _add(f"Mayer键级: 约 {mm.group(1)}")
        for mm in _FACT_VIBFREQ.finditer(content):
            _add(f"虚频数: {mm.group(1)}")
        # 5) 段落性结论：会话里 last assistant 的「…完成/失败」总结句整段留痕
        if role == "assistant" and content:
            short = content.strip().replace("\n", " ")[:160]
            if ("完成" in short or "失败" in short or "结论" in short
                    or "建议" in short):
                _add(f"[结论] {short}")
    return list(facts)


def _session_key(body):
    """从请求体解析会话标识；缺省用 'default'，保证单会话可用。"""
    sid = str(body.get("session_id") or body.get("session") or "default").strip()
    return sid or "default"


def _render_memory_block(session_id, messages):
    """构造注入用的记忆块（短期事实 + 与当前问题相关的长时记忆）；无有效记忆返回空串。"""
    facts = _extract_session_facts(messages)
    # 合并跨请求缓存中的历史事实（可能含上个请求已确认的结论）
    with _SESSION_FACTS_LOCK:
        cached = _SESSION_FACT_CACHE.get(session_id) or []
    now = time.time()
    seen = set(facts)
    merged = list(facts)
    for entry in cached:
        ts, _, s = entry.partition("|")
        if ts.isdigit() and now - float(ts) > _SESSION_CACHE_TTL:
            continue
        if not s:
            continue
        s = s.strip()
        if s not in seen:
            merged.append(s)
            seen.add(s)
    # 定位当前用户输入（用于长时记忆相关性检索与去重）
    last_user = ""
    for m in reversed(messages or []):
        if m.get("role") == "user" and m.get("content"):
            last_user = str(m.get("content"))
            break
    # 长时记忆：只取与当前问题相关的跨会话沉淀事实；短时已含则跳过（避免重复扰动）
    ltm_keep = []
    for t in _query_long_term(last_user, merged):
        if _ltm_norm(t) in {_ltm_norm(x) for x in merged}:
            continue
        ltm_keep.append(t)
        seen.add(_ltm_norm(t))
    parts = []
    if ltm_keep:
        body = "\n".join("- " + x for x in ltm_keep[:_LTM_INJECT_MAX])
        parts.append("【长期记忆】以下是你跨会话沉淀过的可靠事实，若与本次对话相关可直接引用；"
                     "若与当前会话最新结论冲突，以当前会话为准：\n" + body)
    if merged:
        body = "\n".join("- " + x for x in merged[:_SHORT_MEM_MAX])
        parts.append("【会话短期记忆】本次会话此前已确认的事实，可直接引用，"
                     "不必让用户重复提供，也不要与之矛盾：\n" + body)
    block = "\n\n".join(parts)
    if not block:
        return ""
    # 记忆块同样受字符预算约束：内容过多反而压制当前问题的权重（降智主因之一）
    if len(block) > _MEMORY_CHAR_CAP:
        block = block[:_MEMORY_CHAR_CAP] + "\n…（记忆已按预算截断）"
    return "\n" + block + "\n"


def _remember_session(session_id, text):
    """把一段文本里的关键事实并入跨请求缓存（供下个请求复用）。"""
    if not session_id or not text:
        return
    facts = _extract_session_facts([{"role": "assistant", "content": text}])
    if not facts:
        return
    with _SESSION_FACTS_LOCK:
        now = time.time()
        # 过期清理：丢弃超过 TTL 的旧条目（每个条目整体带时间戳）
        merged = []
        for f in _SESSION_FACT_CACHE.get(session_id) or []:
            ts, _, s = f.partition("|")
            if ts.isdigit() and now - float(ts) > _SESSION_CACHE_TTL:
                continue
            merged.append(s)
        merged = [s for s in merged if s]  # 兼容历史无时间戳条目
        for f in facts:
            if f not in merged:
                merged.append(f)
        stamped = [f"{now:.0f}|{f}" for f in merged[-_SESSION_CACHE_MAX:]]
        _SESSION_FACT_CACHE[session_id] = stamped


# ======================================================================
# 长时记忆：跨会话持久化 + 相关性检索 + 去重/剪枝
# 与短期记忆（进程内、按会话、TTL 1h）互补：长期记忆沉淀「可靠结论、
# 化合物映射、关键结构数据」到磁盘文件，跨会话复用；为防止历史信息
# 淹没当前问题（多轮降智），只按相关性注入有限条数，并对记忆块与
# 上文历史做字符预算。
# ======================================================================
def _digraphs(text):
    """抽取字符 bigram 集合（中英文通用，用于相关性/去重）。"""
    t = re.sub(r"[^\u4e00-\u9fff0-9a-z]", "", (text or "").lower())
    return {t[i:i + 2] for i in range(len(t) - 1)}


def _dice(a, b):
    if not a or not b:
        return 0.0
    inter = len(a & b)
    return 0.0 if inter == 0 else 2.0 * inter / (len(a) + len(b))


def _ltm_norm(text):
    """长时记忆条目去重键（归一化后的小写文本）。"""
    return (text or "").strip().lower()


def _load_long_term():
    """惰性加载长时记忆文件到 _LTM_STATE。"""
    global _LTM_LOADED
    if _LTM_LOADED:
        return _LTM_STATE
    with _LTM_LOCK:
        if _LTM_LOADED:
            return _LTM_STATE
        data = {"entries": []}
        try:
            if os.path.exists(_LTM_PATH):
                with open(_LTM_PATH, "r", encoding="utf-8") as f:
                    d = json.load(f)
                    if isinstance(d, dict) and isinstance(d.get("entries"), list):
                        data = d
        except Exception as e:
            print(f"[ltm] 读取长时记忆失败: {e}")
        data.setdefault("entries", [])
        _LTM_STATE.clear()
        _LTM_STATE.update(data)
        _LTM_LOADED = True
    return _LTM_STATE


def _prune_locked():
    """在持有 _LTM_LOCK 时调用：去重 + 按命中/新鲜度剪枝到上限。"""
    now = time.time()
    entries = _LTM_STATE.get("entries") or []
    keep, seen_n = [], set()
    for e in sorted(entries, key=lambda x: (x.get("seen") or 0), reverse=True):
        text = e.get("text") or ""
        key = _ltm_norm(text)
        if not text or key in seen_n:
            continue
        seen_n.add(key)
        age = now - (e.get("last") or 0)
        # 删太多或过旧且命中少的条目；优先保留高命中/较新的
        if len(keep) < _LTM_MAX and not (age > _LTM_MAX_AGE and (e.get("seen") or 0) < 2):
            keep.append(e)
    _LTM_STATE["entries"] = keep


def _save_locked():
    """在持有 _LTM_LOCK 时调用：原子写回磁盘。"""
    try:
        data = {"entries": _LTM_STATE.get("entries") or [], "updated": time.time()}
        tmp = _LTM_PATH + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=1)
        os.replace(tmp, _LTM_PATH)
    except Exception as e:
        print(f"[ltm] 写入长时记忆失败: {e}")


def _remember_long_term(session_id, text):
    """把一段最终答复里的可靠事实沉淀进长时记忆（去重 + 命中和），并落盘。"""
    if not text:
        return
    facts = _extract_session_facts([{"role": "assistant", "content": text}])
    if not facts:
        return
    now = time.time()
    with _LTM_LOCK:
        entries = _LTM_STATE.get("entries") or []
        ind = {_ltm_norm(x.get("text") or ""): x for x in entries if x.get("text")}
        for f in facts:
            key = _ltm_norm(f)
            if not key:
                continue
            if key in ind:
                ind[key]["seen"] = (ind[key].get("seen") or 0) + 1
                ind[key]["last"] = now
            else:
                ind[key] = {"text": f, "kind": "fact", "seen": 1,
                            "created": now, "last": now, "src": session_id}
        _LTM_STATE["entries"] = list(ind.values())
        _prune_locked()
        _save_locked()


def _query_long_term(user_input, short_facts, limit=_LTM_INJECT_MAX):
    """按（当前输入 + 短期事实）的相关性抽取长时记忆，返回最多 limit 条文本。

    用 bigram Dice 评分（中英文通用），只取超过阈值且已沉淀的条目，
    避免把与当前问题无关的历史事实灌进上下文（多轮降智主因之一）。
    """
    state = _load_long_term()
    entries = state.get("entries") or []
    if not entries or not (user_input or short_facts):
        return []
    q = _digraphs(" ".join([user_input or ""] + list(short_facts or [])))
    scored = []
    for e in entries:
        mem = e.get("text") or ""
        if len(mem) < 4:
            continue
        s = _dice(q, _digraphs(mem))
        if s >= _RELEVANCE_MIN:
            scored.append((s, e.get("last") or 0, mem))
    scored.sort(key=lambda x: (-x[0], -x[1]))
    return [t for _, _, t in scored[:limit]]


# ======================================================================
# ReAct 上下文预算（防止 tool_result 撑爆上下文窗口）
# 触发点：工具返回很长——特别是 DFT 日志、坐标块、Multiwfn 大表格。
# 旧行为：SSE 显示层已截断到 8000 字，但 append 进 agent_messages 的
# 仍是完整 tool_result，多次长结果会迅速挤满窗口导致后续推理错乱。
# ======================================================================
# 保留前 N 字 + 尾部 M 字（关键数字/结论通常在首尾）
_BUDGET_HEAD = 6000
_BUDGET_TAIL = 1200
# 异步提交/查询类工具返回简短，即便超长也统一按预算截断；
# 下列工具为"可能返回大块文本"的类型，其余（query_compound 等）也兜底截断
_LONG_RESULT_TOOLS = {
    "read_file", "list_files", "dft_single_point", "dft_optimize",
    "dft_frequency", "multiwfn_bond_order", "multiwfn_charge",
    "multiwfn_rdg", "multiwfn_iri", "visualize_result", "plan_reaction",
    "run_command", "run_python", "analyze_properties", "query_pubchem",
    "transition_state_search", "mdp_optimize", "query_basis_set",
    "query_functional", "qe_calculation", "pyscf_calculation",
}


def _budget_tool_result(tool_result, tool_name):
    """按上下文预算截断 tool_result，返回 (截断后文本, 是否发生截断)。

    超长结果保留头部 + 尾部，中间省略（结果的关键数字/结论通常在首尾）。
    没有上限恒截断；对不在超长清单的工具（如 query_compound / submit_dft_job）
    放宽到 20k 字符才触发，避免给本就短小的结果加无谓的省略标记。
    """
    if not tool_result:
        return tool_result, False
    if tool_name not in _LONG_RESULT_TOOLS and len(tool_result) <= 20000:
        return tool_result, False
    if len(tool_result) <= _BUDGET_HEAD + _BUDGET_TAIL:
        return tool_result, False
    head = tool_result[:_BUDGET_HEAD]
    tail = tool_result[-_BUDGET_TAIL:]
    return (f"{head}\n...（结果过长，已按上下文预算省略中间 "
            f"{len(tool_result) - _BUDGET_HEAD - _BUDGET_TAIL} 字）\n{tail}",
            True)


# ======================================================================
# DFT 参数智能选择：关系库 + BSE(Basis Set Exchange) 校验
#
# 通过一张「关系表」把 DFT 参数选择的依据关联起来：
#   任务类型 × 泛函体系 × 基组档次 × 元素类别 × 分子大小
# 再由 BSE 的真实基组定义校验『候选基组是否覆盖分子中的每个元素』，
# 若某元素在基组中无定义（如 cc-pVTZ 缺 Te、STO-3G 缺过渡金属），
# 自动升级/替换到可用基组。用户显式指定的参数始终优先。
# ======================================================================
# 需用带 ECP/合适基组的原子（典型重元素 / 项目关注金属）
_HEAVY_ATOMS = {"Cu", "Zn", "Se", "Te", "Ag", "Au", "Sn", "Sb", "Bi",
                "Pd", "Pt", "Fe", "Co", "Ni", "Mo", "W", "Ru", "Rh",
                "Ir", "Os", "Re", "Ge", "As", "Ga", "In", "Tl", "Pb"}

# 碱金属 / 碱土金属 / 常见主族（本身不要求特殊基组，但可能出现在项目分子里）
_ALKYL_ATOMS = {"Li", "Na", "K", "Rb", "Cs", "Be", "Mg", "Ca", "Sr", "Ba",
                "Al", "Ga", "Si", "Ge", "Sn", "Pb", "P", "As", "Sb", "Bi"}
_ELEMENT_TOKEN = re.compile(r"[A-Z][a-z]?_?\d*")

# ----------------------------------------------------------------------
# 1) 任务类型 → 泛函体系（task_kind: sp/opt/freq/ts/props）
#    参考项目负载与既有默认，优先选择经济且覆盖良好的杂化泛函。
# ----------------------------------------------------------------------
_METHOD_BY_KIND = {
    "sp":    "B3LYP",
    "opt":   "B3LYP",
    "freq":  "B3LYP",
    "ts":    "B3LYP",
    "props": "CAM-B3LYP",   # 光谱/激发态/性质，长程修正泛函
}

# ----------------------------------------------------------------------
# 1.6) 泛函关系库：任务类型 × 精度 × 体系特征 → 泛函体系
#      与基组 BSE 校验对称：基组校验『元素覆盖』，泛函校验『特征适配』
#      （色散缺失/长程修正/双杂化/开壳层/重元素 ECP/激发态）。
#      method 会原样拼入 `#P {method}/{basis}` 路由，故只用 G16 单 token
#      合法名；需要色散时选用自身内建色散的泛函（ωB97X-D/ωB97M(2)）而非
#      追加 EmpiricalDispersion 关键词，避免破坏 route 拼接。
# ----------------------------------------------------------------------

# 泛函体系属性表：(族, 含色散, 长程修正, 双杂化, 适合重元素/金属, 适合开壳层, 适合激发态/CT)
_FUNCTIONAL_PROPS = {
    "pbe":        ("GGA杂化",   False, False, False, True,  True,  False),
    "bp86":       ("GGA杂化",   False, False, False, True,  True,  False),
    "b3lyp":      ("杂化GGA",   False, False, False, True,  True,  False),
    "m06-2x":     ("meta-杂化", True,  False, False, False, True,  True),
    "cam-b3lyp":  ("长程修正",  False, True,  False, False, True,  True),
    "wb97xd":     ("长程修正",  True,  True,  False, False, True,  True),
    "wb97m2":     ("双杂化",    True,  True,  True,  False, True,  True),
    "b2plyp":     ("双杂化",    True,  True,  True,  False, True,  True),
}

# 精度档位 → 泛函：high=双杂化（最高精度，贵），fast=轻量 GGA
_FUNCTIONAL_BY_PREC = {
    "high":  ("wb97m2", "ωB97M(2)"),   # 高精度单点/热化学/基准
    "fast":  ("b3lyp",  "B3LYP"),      # 快速扫描/构象初筛
}

# 任务类型 → (默认精度, 结构出错的兜底泛函名)
_PRECISION_BY_KIND = {"sp": "standard", "opt": "standard",
                      "freq": "standard", "ts": "standard", "props": "standard"}

# ----------------------------------------------------------------------
# 2) 泛函体系 → 对应基组推荐档次（键：向用户/外部展示的名称）
#    key 为 BSE 可查询的真实基组名（小写），value 为展示名
# ----------------------------------------------------------------------
_BSE_NAMES = {
    "6-31g":        "6-31G(d)",     # 轻体系经济默认；BSE 用 6-31g 存参(极化在程序端加)
    "def2-svp":     "def2-SVP",     # 重元素/过渡金属带 ECP，项目默认
    "def2-tzvp":    "def2-TZVP",    # 精度更高
    "def2-qzvp":    "def2-QZVP",    # 高精度
    "cc-pvdz":      "cc-pVDZ",
    "cc-pvtz":      "cc-pVTZ",
}

# 3) 分子大小 → 基组克制档位（估算重原子数，越大越倾向经济基组）
_BASIS_BY_SIZE = {   # (阈值重原子数, 优先基组BSE名)
    12: "def2-svp",
    40: "def2-svp",
    80: "def2-svp",
}

# 4) 元素类别 → 基组升级映射：轻主族用经济档；重元素/金属需要 ECP，走 def2 系
_LIGHT_ELEMENTS = {"H", "He", "B", "C", "N", "O", "F", "Ne",
                   "Li", "Be", "Na", "Mg", "Si", "P", "S", "Cl", "Ar"}


def _smiles_elements(smiles):
    """提取 SMILES 里的元素符号集合（轻量正则，无需 RDKit）。"""
    s = (smiles or "").replace("[", " ").replace("]", " ").replace("(", " ")
    s = s.replace(")", " ").replace("=", " ").replace("#", " ").replace("+", " ")
    s = s.replace("-", " ").replace("/", " ").replace("\\", " ")
    return set(_ELEMENT_TOKEN.findall(s))


def _clean_elements(elements):
    """把 SMILES 提取的元素符号规整为常见符号集合（去掉计数后缀）。"""
    out = set()
    for e in elements or set():
        m = re.match(r"([A-Z][a-z]?)", e)
        if m:
            out.add(m.group(1))
    return out


def _heavy_required(elements):
    """分子是否含重元素/金属（需要 ECP 级基组）。"""
    return bool(_HEAVY_ATOMS & _clean_elements(elements))


# ----------------------------------------------------------------------
# 5) BSE 校验客户端（本地兜底：网络不可用时跳过校验，不阻塞任务）
#    通过查询『基组 × 元素集合』确认候选基组确实覆盖分子中的每个元素。
# ----------------------------------------------------------------------
_BSE_TIMEOUT = 12
_BSE_CACHE = {}
_BSE_LOCK = threading.Lock()


def _bse_check_basis(name, elements, timeout=_BSE_TIMEOUT):
    """校验 BSE 中基组 name 是否覆盖 elements 全部元素。

    返回 'ok' | 'missing:Cu' | 'unavailable'（无法联网/基组不存在）。
    依赖 200 vs 404：404 错误正文会指出缺失的原子。
    """
    cleaned = sorted(_clean_elements(elements))
    if not cleaned:
        return "ok"
    key = (name.lower(), tuple(cleaned))
    with _BSE_LOCK:
        if key in _BSE_CACHE:
            return _BSE_CACHE[key]
    import urllib.error
    import urllib.request
    try:
        url = ("https://www.basissetexchange.org/api/basis/%s/format/"
               "gaussian94/?elements=%s" % (name.lower(), ",".join(cleaned)))
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (chem-agent)"})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            ok = r.status == 200
            result = ("ok" if ok else "unavailable")
    except urllib.error.HTTPError as e:
        # 能返回错误说明服务可达；解析缺失元素（如 "Element te (Z=52) not found"）
        body = ""
        try:
            body = e.read().decode("utf-8", "replace")
        except Exception:
            body = ""
        if e.code == 404 and "not found" in body:
            mm = re.search(r"Element\s+([A-Za-z]+)\s+\(Z=", body)
            result = "missing:%s" % (mm.group(1) if mm else "unknown")
        else:
            result = "unavailable"
    except Exception:
        result = "unavailable"   # 断网/超时：不阻塞本地计算，跳过校验
    with _BSE_LOCK:
        _BSE_CACHE[key] = result
    return result


def _pick_basis_by_rule(smiles, task_kind, elements):
    """关系库规则推荐基组（不经 BSE，纯本地、快速、可离线）。"""
    cleaned = _clean_elements(elements)
    heavy = bool(_HEAVY_ATOMS & cleaned)
    # 重原子数估算：不含 C/H 的元素个数近似
    non_heavy_non_c_hal = cleaned - _LIGHT_ELEMENTS
    size = len(elements) if elements else 1
    if task_kind == "ts":
        return "def2-svp", "def2-SVP"       # 过渡态：必须覆盖金属/重元素
    if heavy:
        return "def2-svp", "def2-SVP"
    if size >= 60:
        return "def2-svp", "def2-SVP"       # 大体系也倾向 def2（稳健）
    return "6-31g", "6-31G(d)"


# ----------------------------------------------------------------------
# 泛函选择：关系库规则推荐 + 特征校验（结构与基组 BSE 校验对称）
#  -- rule 逻辑无法依赖网络，纯本地快速推荐；
#  -- verify 对『用户显式指定的泛函』做体系适配检查（色散/长程/双杂化/重元素/激发态）。
# ----------------------------------------------------------------------
_KEY_BY_SIG = {
    "pbe": "pbe", "bp86": "bp86", "b3lyp": "b3lyp",
    "m062x": "m06-2x", "camb3lyp": "cam-b3lyp",
    "wb97xd": "wb97xd", "wb97m2": "wb97m2", "b2plyp": "b2plyp",
}


def _canon_functional(name):
    """把用户给定泛函名规范化到 _FUNCTIONAL_PROPS 的键；无法识别返回原串。"""
    s = (name or "").lower()
    for a, b in (("ω", "w"), ("-", ""), (" ", ""), ("(", ""), (")", ""), (".", "")):
        s = s.replace(a, b)
    return _KEY_BY_SIG.get(s, s)


def _pick_functional_by_rule(smiles, task_kind, elements, precision="standard"):
    """关系库规则推荐泛函。返回 (G16 名, 展示名)。"""
    prec = (precision or "standard").lower()
    if prec == "high":
        return ("wb97m2", "ωB97M(2)")       # 高精度单点/基准 → 双杂化（含色散+长程）
    if prec == "fast":
        return ("b3lyp", "B3LYP")           # 快速初筛
    cleaned = _clean_elements(elements)
    if task_kind == "props":
        return ("cam-b3lyp", "CAM-B3LYP")   # 激发态/光谱/性质 → 长程修正
    if task_kind == "ts":
        # 过渡态：势垒精度要求长程修正 + 色散（CO₂ 插入 / 甲基迁移等体系）
        return ("wb97xd", "ωB97X-D")
    if bool(_HEAVY_ATOMS & cleaned):
        return ("b3lyp", "B3LYP")           # 含 Al/Cu/Se/Te 等：ECP 友好且项目既有默认
    return ("b3lyp", "B3LYP")


def _functional_verify(name, elements, task_kind, precision="standard"):
    """校验用户指定泛函与体系特征/任务是否适配。
    返回 (适配度, [提示])；适配度 ∈ ok|warn。作用类似基组的 BSE 覆盖校验。
    """
    key = _canon_functional(name)
    if key is None or key not in _FUNCTIONAL_PROPS:
        return "warn", ["无法识别该泛函，未做适配校验"]
    family, disp, lr, dh, heavy_ok, open_ok, excite_ok = _FUNCTIONAL_PROPS[key]
    cleaned = _clean_elements(elements)
    heavy = bool(_HEAVY_ATOMS & cleaned)
    tips = []
    if heavy and not heavy_ok:
        tips.append("体系含重元素/金属，该泛函适配性一般（建议换用 B3LYP/PBE 等 ECP 友好泛函）")
    if task_kind == "props" and not excite_ok:
        tips.append("任务为性质/激发态，该泛函无长程修正，可靠性有限（建议 CAM-B3LYP/ωB97X-D）")
    if task_kind == "ts" and not lr:
        tips.append("过渡态建议长程修正+色散泛函（如 ωB97X-D）以保障势垒精度")
    if not disp and (task_kind == "ts" or heavy or len(cleaned) >= 20):
        tips.append("该泛函不含色散修正，对弱相互作用/大分子体系误差偏大")
    if precision == "high" and not dh:
        tips.append("高精度需求下双杂化泛函（ωB97M(2)）精度更高")
    return ("ok" if not tips else "warn", tips)


def _resolve_functional(smiles, task_kind, elements, requested_method=None,
                        precision="standard"):
    """输出对外展示且 G16 单 token 合法的泛函名。
    requested_method 显式给定：做适配校验但尊重显式选择（不覆盖）；
    否则走关系库规则推荐。
    """
    if requested_method:
        _functional_verify(requested_method, elements, task_kind, precision)
        return requested_method
    key, disp = _pick_functional_by_rule(smiles, task_kind, elements, precision)
    return disp


def _resolve_basis(smiles, task_kind, elements, requested_basis=None, timeout=_BSE_TIMEOUT):
    """输出对外展示的基组名，经关系库推荐 + BSE 校验 + 升级兜底。

    requested_basis 若非空：把它映射到 BSE 名并校验，缺元素则升级。
    否则：关系库推荐；若推荐基组在 BSE 中不全覆盖，升级到 def2-TZVP。
    网络不可用时返回纯规则推荐（不抛错）。
    """
    cleaned = _clean_elements(elements)

    # —— 用户/调用方指定的基组：尽力校验并升级 ——
    if requested_basis:
        bse_name = None
        for n, disp in _BSE_NAMES.items():
            if requested_basis.lower().replace(" ", "") in (n, disp.lower().replace(" ", "")):
                bse_name = n
                break
        if bse_name is None:
            bse_name = requested_basis.lower().replace("-", "")
            bse_name = "def2-svp" if bse_name and "def2" in bse_name else requested_basis.lower()
        chk = _bse_check_basis(bse_name, elements, timeout)
        if chk == "ok":
            return requested_basis
        # 升级兜底：选择的基组缺元素 → def2-TZVP（项目体系几乎全能覆盖）
        if chk.startswith("missing:"):
            up = _bse_check_basis("def2-tzvp", elements, timeout)
            return "def2-TZVP" if up == "ok" else requested_basis
        return requested_basis   # 断网/不可用：保留用户选择

    # —— 关系库推荐 ——
    bse_name, disp = _pick_basis_by_rule(smiles, task_kind, elements)
    chk = _bse_check_basis(bse_name, elements, timeout)
    if chk == "ok":
        return disp
    if chk.startswith("missing:"):
        # 推荐基组缺元素（如某金属无 6-31g 或 def2-svp 不含该重元素）→ 升级
        for name in ("def2-tzvp", "def2-svp"):
            if _bse_check_basis(name, elements, timeout) == "ok":
                return _BSE_NAMES[name]
    return disp   # 联网失败：回退纯规则推荐


# ----------------------------------------------------------------------
# 对外推荐入口（被 dft 工具调用）
# ----------------------------------------------------------------------
def recommend_dft_params(smiles, task_kind="sp", method=None, basis=None,
                         precision=None, timeout=_BSE_TIMEOUT):
    """按 (任务类型, 分子成分, 精度) 推荐 (method, basis)。

    优先级：用户显式 method/basis > 关系库泛函体系 > BSE 校验后的基组。
    precision ∈ fast/standard/high；缺省由 task_kind 推得。
    """
    precision = precision or _PRECISION_BY_KIND.get(task_kind, "standard")
    elements = _smiles_elements(smiles)
    method_out = _resolve_functional(smiles, task_kind, elements, method, precision)
    basis_out = _resolve_basis(smiles, task_kind, elements, basis, timeout)
    return method_out, basis_out


def auto_method_basis(smiles, method=None, basis=None, default_basis="6-31G(d)",
                      task_kind="sp", precision=None):
    """兼容旧签名：根据分子成分返回 (method, basis)。

    - 用户指明 method/basis -> 原样返回（显式优先，但仍做泛函适配校验/基组 BSE 校验）
    - 否则经 关系库泛函 + 关系库基组 + BSE 校验 推荐
    - default_basis 仅在“纯规则且任意基组候选可用”时兜底（对齐旧调用）
    """
    precision = precision or _PRECISION_BY_KIND.get(task_kind, "standard")
    if method or basis:
        # 存在显式指定的部分：method 显式则用；basis 显式则交由 BSE 校验/升级
        method = _resolve_functional(smiles, task_kind, _smiles_elements(smiles),
                                     method, precision)
        basis = _resolve_basis(smiles, task_kind, _smiles_elements(smiles),
                               basis or None, _BSE_TIMEOUT)
        return method, basis
    return recommend_dft_params(smiles, task_kind=task_kind,
                                method=None, basis=None, precision=precision)


# ---------- 工具实现（无 GUI 依赖，逻辑与主程序一致） ----------
def _t_query_basis_set(smiles, task_kind="sp", basis=None):
    """agent 工具：查询/选择 DFT 基组。

    - 提供 basis：校验该基组是否覆盖分子全部元素，缺则建议升级基组
    - 省略 basis：返回项目关系库推荐（任务类型×泛函×基组×元素×分子大小）
    """
    task_kind = (task_kind or "sp").strip().lower()
    valid_kind = {"sp", "opt", "freq", "ts", "props"}
    if task_kind not in valid_kind:
        task_kind = "sp"
    mapping = {"sp": "单点能", "opt": "几何优化", "freq": "频率分析",
               "ts": "过渡态搜索", "props": "性质/光谱"}
    if not smiles:
        return (f"❌ 需要 smiles 参数。\n可用任务类型：sp单点/opt优化/freq频率/ts过渡态/props性质"
                f"（默认 sp）。\n例：query_basis_set smiles=\"[Se][Al]\" task_kind=\"ts\"")
    elements = _clean_elements(_smiles_elements(smiles))
    heavy = sorted(_HEAVY_ATOMS & elements)
    lines = [f"🔬 基组选择分析（任务类型: {mapping.get(task_kind, task_kind)}）",
             f"SMILES: {smiles}", f"元素组成: {', '.join(sorted(elements))}"]
    if heavy:
        lines.append(f"⚠️ 含重元素/金属: {', '.join(heavy)} → 建议 def2 系列（自带ECP）")
    if basis:
        mapped = None
        for n, disp in _BSE_NAMES.items():
            if basis.lower().replace(" ", "") in (n, disp.lower().replace(" ", "")):
                mapped = n
                break
        if mapped is None:
            lines.append(f"\n未识别基组名「{basis}」，改用推荐流程…")
            basis = None
    if basis is None:
        method, chosen = auto_method_basis(smiles, None, None, task_kind=task_kind)
        lines.append(f"\n✅ 推荐泛函/基组: {method} / {chosen}")
        lines.append("   （依据：任务类型→泛函；重元素→def2系；网络可用时经BSE校验）")
        return "\n".join(lines)
    chk = _bse_check_basis(mapped, elements)
    lines.append(f"\n检查基组「{basis}」(BSE 名 {mapped}) 对分子元素覆盖:")
    if chk == "ok":
        lines.append(f"✅ 覆盖全部元素（{', '.join(sorted(elements))}），可直接使用")
    elif chk.startswith("missing:"):
        missing = chk.split(":", 1)[1]
        lines.append(f"❌ 不含元素 {missing}（需 ECP）→ 请改用 def2-SVP 或 def2-TZVP")
        up = auto_method_basis(smiles, None, None, task_kind=task_kind)
        lines.append(f"   推荐替代: {up[0]} / {up[1]}")
    else:
        lines.append("⚠️ 无法联网校验（可能断网），请以关系库建议为准")
        method, chosen = auto_method_basis(smiles, None, None, task_kind=task_kind)
        lines.append(f"   推荐: {method} / {chosen}")
    return "\n".join(lines)


def _t_query_functional(smiles, task_kind="sp", precision=None, method=None):
    """agent 工具：分析/推荐 DFT 泛函选择。

    - 提供 method：校验该泛函与体系特征/任务的适配，并给出更优建议
    - 省略 method：返回关系库按照 (任务类型×精度×体系特征) 的推荐与理由
    """
    task_kind = (task_kind or "sp").strip().lower()
    valid_kind = {"sp", "opt", "freq", "ts", "props"}
    if task_kind not in valid_kind:
        task_kind = "sp"
    prec = (precision or "").strip().lower() or _PRECISION_BY_KIND.get(task_kind, "standard")
    if prec not in ("fast", "standard", "high"):
        prec = "standard"
    mapping = {"sp": "单点能", "opt": "几何优化", "freq": "频率分析",
               "ts": "过渡态搜索", "props": "性质/光谱"}
    prec_label = {"fast": "快速", "standard": "标准", "high": "高精度"}[prec]
    if not smiles:
        return (f"❌ 需要 smiles 参数。\n可用任务类型：sp单点/opt优化/freq频率/ts过渡态/props性质"
                f"（默认 sp）；precision：fast快速/standard标准/high高精度。"
                f"\n例：query_functional smiles=\"C=C\" task_kind=\"ts\" precision=\"high\"")
    elements = _clean_elements(_smiles_elements(smiles))
    heavy = sorted(_HEAVY_ATOMS & elements)
    lines = [f"⚗️ 泛函选择分析（任务类型: {mapping.get(task_kind, task_kind)}，"
             f"精度档: {prec_label}）",
             f"SMILES: {smiles}", f"元素组成: {', '.join(sorted(elements)) or '(空)'}"]
    if heavy:
        lines.append(f"⚠️ 含重元素/金属: {', '.join(heavy)} → 保留 ECP 友好的杂化泛函")
    if prec == "high":
        lines.append("「高精度」档：推荐双杂化泛函（含色散+长程修正），适合作单点基准/热化学")
    if method:
        feas, tips = _functional_verify(method, elements, task_kind, prec)
        disp = {"ok": "✅ 适配良好", "warn": "⚠️ 有注意点"}[feas]
        reason = "；".join(tips) if tips else "未发现明显不适配"
        lines.append(f"\n校验泛函「{method}」: {disp}\n   {reason}")
        if feas == "warn":
            key, rec = _pick_functional_by_rule(smiles, task_kind, elements, prec)
            lines.append(f"   更优建议: {rec}（关系库按任务/体系推荐）")
    else:
        key, rec = _pick_functional_by_rule(smiles, task_kind, elements, prec)
        features = []
        if heavy:
            features.append("含重元素")
        if task_kind == "props":
            features.append("激发态/性质")
        elif task_kind == "ts":
            features.append("过渡态（需长程+色散）")
        lines.append(f"\n✅ 推荐泛函: {rec}")
        lines.append(f"   依据: 任务=「{mapping.get(task_kind, task_kind)}」+ 体系特征"
                     f"{('（'+'、'.join(features)+'）') if features else ''} + 精度=「{prec_label}」")
        lines.append("   备选: 过渡态亦可 M06-2X；含重元素亦可用 PBE；高精度亦可用 B2PLYP")
    return "\n".join(lines)


def _t_query_compound(name=""):
    db = _load_compound_db()
    if not db:
        return f"错误：{getattr(_compound_db_error, 'x', '') or '化合物数据库为空'}"
    name = (name or "").strip()
    if not name:
        names = list(db.keys())
        return f"数据库共 {len(names)} 个化合物：\n" + "\n".join(names)
    hits = _lookup_compound(name)
    if not hits:
        return (f"❌ 数据库中未找到 '{name}'。"
                f"可调用 query_compound 且不带参数，查看全部可用化合物列表。")
    if len(hits) == 1:
        return f"✅ {hits[0][0]} 的SMILES：{hits[0][1]}"
    lines = [f"找到 {len(hits)} 个匹配的化合物："]
    lines += [f"- {k}: {v}" for k, v in hits[:10]]
    if len(hits) > 10:
        lines.append(f"...（还有 {len(hits) - 10} 个）")
    return "\n".join(lines)


def _t_mdp_optimize(smiles, episodes=30, max_steps=60, ref_bonds=None, ref_xyz=None):
    if not MOL_AVAILABLE:
        return "mol_optimizer不可用。"
    try:
        result = run_optimization(smiles, episodes=episodes, max_steps=max_steps,
                                  ref_bonds=ref_bonds, ref_xyz=ref_xyz)
        backend = result.get('backend', 'python')
        core = 'C++' if backend == 'cpp' else 'Python'
        lines = [f"MDP优化完成({core}核心, {result.get('episodes_run', episodes)}轮, "
                 f"耗时{result.get('elapsed_ms', 0):.0f}ms):",
                 f"总能量: {result['total_energy']:.3f}",
                 f"约束能量: {result['constraint_energy']:.3f}",
                 f"违规数: {result['violations']}"]
        if result.get('ref_bonds'):
            refs = ", ".join(f"{a}-{b}={v:.3f}Å" for (a, b), v in sorted(result['ref_bonds'].items()))
            lines.append(f"实验参考键长(CIF修正): {refs}")
        return "\n".join(lines)
    except Exception as e:
        return f"优化失败: {e}"


def _t_visualize_molecule(smiles, show_hydrogen=True):
    if not MOL_AVAILABLE:
        return "mol_optimizer不可用。"
    try:
        mol_graph = smiles_to_molecule_graph(smiles, add_hs=True)
        atoms = [a for a in mol_graph.atoms if show_hydrogen or a.symbol != "H"]
        if os.path.isfile(MOLVIEW_EXE):
            fd, xyz_path = tempfile.mkstemp(suffix=".xyz", prefix="zhisuan_mol_")
            with os.fdopen(fd, "w", encoding="utf-8") as f:
                f.write(f"{len(atoms)}\n{smiles}\n")
                for a in atoms:
                    c = a.coords
                    f.write(f"{a.symbol} {c[0]:.6f} {c[1]:.6f} {c[2]:.6f}\n")
            subprocess.Popen([MOLVIEW_EXE, xyz_path])
            return f"分子 {smiles} 的3D结构已在原生分子查看器(molview.exe)中打开。"
        visualize_molecule_3d(mol_graph, title=smiles, show_hydrogen=show_hydrogen)
        return f"分子 {smiles} 的3D结构已在浏览器中打开（未找到molview.exe，使用备用方案）。"
    except Exception as e:
        return f"可视化失败: {e}"


def _prepare_result_visualization(result_file):
    """解析计算结果文件：写 XYZ、生成 FMO/密度 cube，返回 (xyz_path, cubes, natom, error)。

    cube 与 XYZ 写入结果文件同目录（molview 自动发现等值面依赖同目录）。
    同目录同名 .fchk 存在且 Multiwfn 可用时生成 HOMO/LUMO/密度等值面。
    """
    try:
        import cclib
        data = cclib.io.ccread(result_file)
        if data is None or not hasattr(data, "atomcoords") or not len(data.atomcoords):
            return None, [], 0, "无法从结果文件解析几何结构（cclib 返回空）"
        coords = data.atomcoords[-1]
        natom = int(getattr(data, "natom", len(coords)))
        atomnos = list(getattr(data, "atomnos", []))[:natom]
        while len(atomnos) < natom:
            atomnos.append(0)
        out_dir = os.path.dirname(os.path.abspath(result_file))
        stem = os.path.splitext(os.path.basename(result_file))[0]
        xyz_path = os.path.join(out_dir, stem + "_view.xyz")
        with open(xyz_path, "w", encoding="utf-8") as f:
            f.write(f"{natom}\n{os.path.basename(result_file)} geometry\n")
            for i in range(natom):
                c = coords[i]
                f.write(f"{_ATNUM2SYM.get(int(atomnos[i]), 'X')} "
                        f"{c[0]:.6f} {c[1]:.6f} {c[2]:.6f}\n")
        cubes = []
        fchk = os.path.join(out_dir, stem + ".fchk")
        if os.path.isfile(fchk) and DFT_MANAGER and getattr(DFT_MANAGER, "analyzer", None):
            try:
                # prefix 取 XYZ 文件主干（stem_view），保证 <xyz前缀>_<tag>.cub 与
                # molview 的自动发现规则（同目录 <basename>_<tag>.cub）匹配
                r = DFT_MANAGER.analyzer.analyze_fmo_cubes(fchk, prefix=stem + "_view")
                cubes = list(r.get("cubes", {}).values())
            except Exception:
                cubes = []
        return xyz_path, cubes, natom, ""
    except ImportError:
        return None, [], 0, "未安装 cclib（pip install cclib）"
    except Exception as e:
        return None, [], 0, f"{type(e).__name__}: {e}"


def _t_visualize_result(result_file):
    if not result_file or not os.path.isfile(result_file):
        return "错误：需要有效的计算结果文件路径（file 参数）。"
    xyz_path, cubes, natom, err = _prepare_result_visualization(result_file)
    if err:
        return f"错误：{err}"
    if os.path.isfile(MOLVIEW_EXE):
        subprocess.Popen([MOLVIEW_EXE, xyz_path])
        note = f"，已生成 {len(cubes)} 个等值面 cube（FMO/电子云）" if cubes else ""
        return (f"已在3D渲染窗口打开 {os.path.basename(xyz_path)}（{natom} 原子）{note}。"
                "快捷键：B=切换FMO等值面，D=切换电子云，R=复位视图，S=截图，1/2/3=球棍/比例/线框。")
    return f"未找到 molview.exe，3D渲染不可用。XYZ 已保存: {xyz_path}"


def _t_analyze_properties(smiles):
    try:
        from rdkit import Chem
        from rdkit.Chem import Descriptors
        mol = Chem.MolFromSmiles(smiles)
        if not mol:
            return "无效的SMILES"
        return (f"分子: {smiles}\n分子量: {Descriptors.ExactMolWt(mol):.2f}\n"
                f"LogP: {Descriptors.MolLogP(mol):.2f}\n"
                f"氢键供体: {Descriptors.NumHDonors(mol)}\n"
                f"氢键受体: {Descriptors.NumHAcceptors(mol)}")
    except ImportError:
        return "RDKit未安装，无法计算性质。"
    except Exception as e:
        return f"计算失败: {e}"


# ---------- 结构/过渡态结果语义解读（把原始数据翻译成模型与用户都能懂的化学结论） ----------
_LEWIS_DONOR = {"N", "O", "S", "Se", "Te", "P"}
_METAL_LIKE = {"Cu", "Au", "Ag", "Ni", "Pd", "Pt", "Fe", "Co", "Zn", "Mg",
               "Na", "K", "Li", "Be", "Ca", "Sr", "Ba", "Ga", "In", "Tl",
               "Sn", "Pb", "Bi", "Li"}


def _al_species(al, mol):
    """按成键模式判定一个 Al 中心的化学物种/价态（面向铝宾 A1-A4 分类）"""
    if al.GetFormalCharge() < 0:
        return "铝阴离子 (A1 负电荷铝中心，亲核)"
    double_partner = [b.GetOtherAtom(al).GetSymbol() for b in al.GetBonds()
                      if b.GetBondType().name.startswith("DOUBLE")]
    if "Al" in double_partner:
        return "dialumene（Al=Al 双铝烯，A4）"
    if "O" in double_partner:
        return "铝烯氧 [Al]=O"
    if "N" in double_partner:
        return "亚胺铝宾 Al=N"
    if "C" in double_partner:
        return "铝烯基（Al=C 亚甲基铝烯）"
    metals = [a.GetSymbol() for a in al.GetNeighbors() if a.GetSymbol() in _METAL_LIKE]
    if metals:
        return f"金属-铝 σ 复合物（Al 键连 {','.join(sorted(set(metals)))}, A3）"
    deg = al.GetDegree()
    if deg <= 2 and al.GetTotalValence() < 3:
        return "低配位一价/二价铝宾（类碳烯, A2）——铝具空 p 轨道(路易斯酸)与孤对"
    return f"配位饱和铝（当前配位数 {deg}）"


def _interpret_structure(smiles):
    """把分子结构的键级/价态/配位环境翻译成铝宾化学可读结论"""
    try:
        from rdkit import Chem
        mol = Chem.MolFromSmiles(smiles)
        if not mol:
            return "无效的SMILES，无法解读结构。"
    except ImportError:
        return "RDKit未安装，无法解读结构（服务层需在 rdkit-env 环境运行）。"
    lines = [f"结构解读: {smiles}"]
    al_atoms = [a for a in mol.GetAtoms() if a.GetSymbol() == "Al"]
    if not al_atoms:
        lines.append(f"· 本结构不含铝（Al），重原子 {mol.GetNumHeavyAtoms()} 个。")
        nrings = sum(1 for r in mol.GetRingInfo().AtomRings())
        if nrings:
            lines.append(f"· 含 {nrings} 个环。")
        return "\n".join(lines)
    lines.append(f"· 含 {len(al_atoms)} 个铝中心")
    for i, al in enumerate(al_atoms, 1):
        sp = _al_species(al, mol)
        bonds = ", ".join(
            f"{b.GetBeginAtom().GetSymbol()}-{b.GetBondType().name.lower()}-"
            f"{b.GetEndAtom().GetSymbol()}" for b in al.GetBonds()) or "无直接键"
        lines.append(f"· 铝#{i}: {sp}")
        lines.append(f"    邻接配体 {[a.GetSymbol() for a in al.GetNeighbors()]}；"
                     f"形式电荷 {al.GetFormalCharge()}；键: {bonds}")
        if _is_lowcoordinate_al(sp):
            donors = [a.GetSymbol() for a in al.GetNeighbors()
                      if a.GetSymbol() in _LEWIS_DONOR]
            if donors:
                lines.append(f"    配位环境: 路易斯碱 {','.join(sorted(set(donors)))}"
                             " 向铝供电子 → 稳定中性铝宾(NHBO 型)")
            elif "阴离子" in sp:
                lines.append("    反应位点: 富电子铝中心(带负电荷)，亲核性强，"
                             "易向亲电体(如CO₂的C)给出电子")
            else:
                lines.append("    反应位点: 铝具空轨道(路易斯酸，易被配体/亲核体进攻)，"
                             "或以其孤对参与亲电反应")
    return "\n".join(lines)


def _is_lowcoordinate_al(species):
    return any(k in species for k in ("铝宾", "铝烯基", "铝烯氧", "亚胺", "双铝烯",
                                      "复合物", "阴离子"))


_TS_IMAG = re.compile(r"(?:虚频数[:：]?\s*(\d+))|(?:虚频: ([^\n;]*(?:cm⁻¹|i)))")
_TS_CONSIST = re.compile(r"一致性: 反应原子（Al\+CO₂）位移占比\s*([0-9.]+)%\s*（(.+?)）")
_TS_BARRIER = re.compile(r"势垒（近似）[:：]?\s*([0-9.]+)\s*kcal/mol")
_TS_WIGNER = re.compile(r"Wigner 隧道修正因子 κ\(298K\): ([0-9.]+)")


def _interpret_ts(report):
    """把过渡态原始报告提炼为化学可读结论（可信度/活化能/隧穿/能否发生）"""
    if not report:
        return "无可解读的过渡态报告。"
    imag_m = _TS_IMAG.search(report)
    cons = _TS_CONSIST.search(report)
    bar = _TS_BARRIER.search(report)
    wig = _TS_WIGNER.search(report)
    lines = ["过渡态结果解读:"]
    if cons:
        share, verdict = cons.group(1), cons.group(2)
        ok = verdict.startswith("合格")
        lines.append(f"· 过渡态可信度: {'可信' if ok else '存疑'}（虚频位移中反应原子占 "
                     f"{share}%，{verdict}）")
    if imag_m:
        lines.append(f"· 虚频: {imag_m.group(1) or imag_m.group(2)}"
                     "（唯一虚频即过渡态；多个虚频或虚频数为0则非真TS）")
    if not imag_m and not cons:
        lines.append("· 报告中未检出虚频/一致性信息，无法判置信度")
    if bar:
        b = float(bar.group(1))
        if b < 10:
            level = "低势垒，室温即可自发"
        elif b <= 25:
            level = "中等势垒，需热活化（加热或较高能量供给）"
        else:
            level = "高势垒，难以自发，需显著加热或催化"
        lines.append(f"· 活化能判读: 势垒 ≈ {b:.1f} kcal/mol → {level}")
    if wig:
        w = float(wig.group(1))
        if w > 1.5:
            line = "隧穿效应显著，低温也能穿越势垒"
        elif w > 1:
            line = "隧穿略有增强速率"
        else:
            line = "隧穿可忽略"
        lines.append(f"· 量子隧穿: Wigner κ(298K)={w} → {line}")
    if not ("TS 优化" in report or "过渡态搜索" in report or "虚频" in report):
        lines.append("· 注意: 该文本不含过渡态搜索报告结构，请确认输入是否为 "
                     "transition_state_search / submit_dft_job(ts) 的结果。")
    return "\n".join(lines)


def _t_interpret_result(kind=None, smiles="", report=""):
    """把分子结构或过渡态结果翻译成化学可读结论，供模型与用户直接理解"""
    k = (kind or "structure").strip().lower()
    if k in ("structure", "struct", "str", "分子", "结构"):
        if not smiles:
            return "错误: 解读结构需要 smiles 参数。"
        return _interpret_structure(_resolve_smiles(smiles))
    if k in ("ts", "transition", "过渡态", "过渡态结果"):
        if not report:
            return ("错误: 解读过渡态需要 report 参数，请把 transition_state_search 或 "
                    "submit_dft_job(job_type=ts) 返回的结果文本整体传给它。")
        return _interpret_ts(report)
    return (f"错误: kind 应为 structure(解读结构, 传smiles) 或 ts(解读过渡态, 传report)，"
            f"当前为 '{kind}'。")


# ---------- 计算任务理解 / 前线轨道分析 / 2D结构式 / 谱图 ----------
_TASKS_GUIDE = {
    "sp": "单点能: 对单一构型算能量，用于比较异构体/反应物-产物能量、判定稳定性。工具: dft_single_point / submit_dft_job(job_type=sp)。关键: 先几何优化获得稳定构型，否则单点能无意义。",
    "优化": "几何优化(opt): 找势能面极小点（稳定构型）。工具: dft_optimize / submit(opt)。关键: 检查是否收敛、无虚频（配合freq）。",
    "频率": "频率(freq): 算振动频率，验证极小点(全正)/过渡态(唯一虚频)，温度校正、ZPE。工具: dft_frequency / submit(freq)。关键: 赝振动，配合opt用。",
    "ts": "过渡态搜索(TS): 找鞍点(反应能垒最高点)。工具: transition_state_search 或 submit(ts)。关键: 需先扫描自由反应路径；唯一虚频+虚频位移沿反应坐标才算可信TS；建议ωB97X-D+def2-SVP。",
    "irc": "IRC: 从过渡态沿最陡下降路径确认连接的反应物与产物，验证TS归属。工具: 需提交IRC计算(G16 IRC关键字)，当前未内置专门工具，可经submit_dft_job自定义或run_python封装。关键: TS左右两方向各走到收敛即确认通道。",
    "激发": "激发态/TDDFT: 算电子跃迁(UV-Vis吸收、发射)。工具: 需TDDFT关键字，当前未内置专门工具，可经run_python/自定义后端支撑；光谱分析用 visualize_spectrum(type=uv)。关键: 泛函需含长程修正(如CAM-B3LYP/ωB97X-D)对电荷转移态才准。",
    "溶剂": "溶剂化/隐式溶剂: 体系在溶剂中的能量与性质(CPCM/SMD)。工具: 需SCRF关键字，当前未内置专门工具。关键: 重元素+溶剂需组合基组与泛函策略。",
    "扫描": "势能面扫描: 沿某键长/二面角逐步扫描得能量曲线，找转折点/交叉点。工具: transition_state_search 内部含松弛扫描；也常被TS/IRC前置。关键: 选择合适的反应坐标与步长。",
    "分子": "结构理解: 识别物种/配位/反应位点。工具: interpret_result(structure) 或 analyze_properties + query_compound。",
    "分析": "波函数分析: 键级/电荷/弱相互作用/前线轨道/等值面。工具: multiwfn_bond_order/charge/rdg/iri、analyze_fmo、visualize_spectrum。",
}


def _t_task_advisor(task="", smiles=""):
    """根据科研需求回答『该跑哪种计算任务、为什么、用哪个工具』，并给出体系相关注意点"""
    t = (task or "").strip().lower()
    if not t:
        return ("请描述想要的计算任务或科研问题，例如：'这个反应的过渡态怎么算'、'帮我做一下几何优化'、"
                "'判断Al2O3复合物稳定性'。我会推荐对应的计算任务、工具与注意点。\n"
                "可选任务：单点能(sp)、几何优化、频率(freq)、过渡态(ts)、IRC、激发态(TDDFT)、"
                "溶剂化、势能面扫描、结构理解、波函数分析。")
    # 关键词打分：命中的专属词越多越可靠，选最优任务类型
    rules = [
        ("irc", ["irc", "内禀反应坐标", "连接反应", "反应路径确认", "验证过渡态归属"]),
        ("激发", ["激发", "tddft", "吸收", "发射", "荧光", "电子跃迁", "能级跃迁"]),
        ("溶剂", ["溶剂化", "溶剂", "smd", "cpc第一", "隐式溶剂"]),
        ("扫描", ["扫描", "势能面", "反应坐标", "pes", "scan"]),
        ("sp", ["单点能", "单点", "能量比较", "稳定性", "比哪个稳定"]),
        ("优化", ["几何优化", "构型优化", "优化", "找极小", "稳定构型", "弛豫", "opt"]),
        ("频率", ["频率", "振动", "虚频", "吉布斯", "零点能", "zpe", "freq"]),
        ("ts", ["过渡态", "能垒", "反应势垒", "鞍点", "活化能", "barrier", "transition", "ts"]),
        ("分子", ["结构理解", "分子结构", "配位", "物种识别", "这是什么", "结构式"]),
        ("分析", ["分析", "键级", "电荷", "前线轨道", "homo", "lumo", "弱相互作用", "等值面"]),
    ]
    best = None
    bestk = 0
    for key, kws in rules:
        hit = sum(1 for k in kws if k in t)
        if hit > bestk:
            bestk, best = hit, key
    lines = []
    if best:
        lines.append(f"根据你的描述「{task.strip()}」，推荐的计算任务为：")
        lines.append("· " + _TASKS_GUIDE[best])
    else:
        lines.append("暂未识别出明确的任务关键词，可帮你做以下事情：")
        for k in ("sp", "优化", "频率", "ts", "分析"):
            lines.append("· " + _TASKS_GUIDE[k])
    if smiles:
        sm = _resolve_smiles(smiles)
        lines.append(f"\n对应分子 {sm} 的后续建议：")
        lines.append("· 执行 DFT 前建议先用 query_basis_set / query_functional 确认基组与泛函"
                     "是否覆盖全部元素（含 Cu/Se/Te 等重元素需 ECP 与 def2 系基组）。")
        lines.append("· 结构呈现：可画 2D 结构式（draw_structure）或 3D 球棍模型（visualize_molecule）。")
        lines.append("· 波函数分析（键级/电荷/前线轨道）：先跑 dft_single_point 生成 .fchk，"
                     "再用 multiwfn_bond_order / multiwfn_charge / analyze_fmo；"
                     "激发态/溶剂效应则用 visualize_spectrum(type=uv) 与对应 DFT 关键字。")
    lines.append("\n若上述不符预期，直接描述研究目标（如'判断这个复合物稳不稳定'、'这个反应能垒多高'），"
                 "我会推荐任务、工具并说明注意点。")
    return "\n".join(lines)


def _t_search_skills(keyword=""):
    """在知识库中搜索技能文档"""
    if not KB_AVAILABLE:
        return "知识库不可用。"
    skills = KB.search_skills(keyword or "")
    if not skills:
        return f"未找到包含 '{keyword}' 的技能。"
    result = f"找到 {len(skills)} 个技能：\n"
    for s in skills:
        result += f"- [{s.category}] {s.title} ({s.name})\n"
    return result


def _t_rag_search(query="", top_k=5):
    """RAG 语义检索：按语义相似度查找 Laya 高斯作业分诊/laya 调校/自旋基态性质等
    历史经验。独立于关键词知识库，供智能体对『未写在规则里的经验』做语义召回。"""
    if not query:
        return "请提供检索 query。"
    try:
        import rag_retriever
        hits = rag_retriever.get_retriever().search(query, top_k=int(top_k or 5))
        if not hits:
            return f"未找到与 '{query}' 语义相似的历史案例。"
        lines = [f"基于 RAG 的 {len(hits)} 条相似经验："]
        for h in hits:
            label = h.get("label") or "-"
            lines.append(f"- [{h.get('scope')}] 案例 {h.get('key')}（label={label}, "
                         f"相似度 {h.get('score')}）: {h.get('text','')[:120]}")
        return "\n".join(lines)
    except Exception as e:
        return f"RAG 检索不可用：{type(e).__name__}: {e}"


def _t_diagnose_job(log="", fields_json="", target_spin=1, use="auto"):
    """Gaussian 作业分诊：判定 DFT/TS/NBO 日志可否直接采用还是需重跑。
    使用 Laya 决策头 + 领域规则(enrich 自旋基态校验) + 本地 qwen + RAG 历史案例，
    三级降级(laya → qwen → 规则)保证打包环境也可用。"""
    try:
        import job_diagnosis
    except Exception as e:
        return f"分诊模块不可用：{type(e).__name__}: {e}"
    fields = None
    if fields_json:
        try:
            fields = json.loads(fields_json)
        except Exception:
            return "fields_json 无法解析，请提供合法 JSON 对象。"
    d = job_diagnosis.diagnose(log_file=log, fields=fields,
                               target_spin=int(target_spin or 1), use=use)
    return job_diagnosis.format_result(d)


def _t_analyze_fmo(smiles="", file=""):
    """前线轨道(HOMO/LUMO)分析：解析 DFT 结果文件，或用 smiles 现场跑 dft_single_point，
    给出 HOMO/LUMO 能级与能隙并做化学解读（活性/电子光谱倾向）。"""
    if not file or not os.path.isfile(file):
        if not (smiles and DFT_MANAGER and DFT_AVAILABLE):
            return ("错误：analyze_fmo 需要 file（DFT 结果文件路径）或 smiles（将现场运行 "
                    "dft_single_point 生成结果）之一。")
        try:
            r = DFT_MANAGER.run_dft(DFTInput(smiles=_resolve_smiles(smiles)), backend=None)
        except Exception as e:
            return f"错误：现场单点计算启动失败: {type(e).__name__}: {e}"
        if not r.success:
            return f"错误：现场单点计算失败: {r.error_message}"
        file = r.log_file or r.fchk_file or ""
        if not file:
            return "错误：单点计算未返回可解析的结果文件。"
        generated = True
    else:
        generated = False
    try:
        import cclib
        data = cclib.io.ccread(file)
        if data is None:
            return "错误：无法用 cclib 解析结果文件（请确认是 Gaussian/ORCA 输出）。"
        homos = getattr(data, "homos", None)
        mos = getattr(data, "moenergies", None)
        if not homos or not mos:
            return "错误：结果文件缺少分子轨道能级数据（请确认计算了波函数/单点任务）。"
        homo_idx = int(homos[0] if isinstance(homos, (list, tuple)) else homos)
        arr = mos[0]
        if homo_idx >= len(arr) - 1:
            return "错误：轨道能级数据不足，无法取 LUMO。"
        h_au = float(arr[homo_idx])
        l_au = float(arr[homo_idx + 1])
        gap_au = l_au - h_au
    except ImportError:
        return "未安装 cclib（pip install cclib）"
    except Exception as e:
        return f"错误：解析轨道能级失败: {type(e).__name__}: {e}"
    ev = 27.2114
    h_ev, l_ev, gap_ev = h_au * ev, l_au * ev, gap_au * ev
    if gap_ev >= 5.0:
        nature = "能隙很大，接近绝缘体，电子跃迁需真空/远紫外区"
    elif gap_ev >= 2.0:
        nature = "半导体型能隙，可见/近紫外区可能有跃迁，适合做 UV-Vis 光谱"
    else:
        nature = "能隙偏小，电子易被激发（需留意自旋多重度描述是否恰当）"
    redox = ("HOMO 能量绝对值偏大，分子较难被氧化（给电子/亲核能力较弱）" if h_ev < -6.5
             else "HOMO 能量适中，具有相当给电子（亲核/还原）活性")
    fname = os.path.basename(file) if not generated else "dft_single_point 结果"
    return (f"前线轨道分析（{'现场单点计算' if generated else fname}）:\n"
            f"· HOMO = {h_ev:.3f} eV（{h_au:.4f} au）\n"
            f"· LUMO = {l_ev:.3f} eV（{l_au:.4f} au）\n"
            f"· 能隙 E(gap) = {gap_ev:.3f} eV = {gap_au * 627.509:.1f} kcal/mol\n"
            f"· 解读: {nature}；{redox}。\n"
            "· 需要 HOMO/LUMO 等值面图时，用 visualize_result 打开同目录 .log/.fchk 即可自动生成。")


def _t_draw_structure(smiles="", outfile="", title=""):
    """绘制 2D 结构式并保存为 PNG（含元素着色、单/双/三键区分）。

    用 RDKit Compute2DCoords 生成平面坐标，再用 matplotlib 自绘，规避 rdMolDraw2D/cairo 缺失。
    """
    if not smiles:
        return "错误：draw_structure 需要 smiles 参数。"
    smiles = _resolve_smiles(smiles)
    try:
        import matplotlib
        matplotlib.use("Agg")
        from matplotlib import pyplot as plt
        from rdkit import Chem
        from rdkit.Chem import AllChem, Descriptors
    except ImportError as e:
        return f"绘图依赖未安装（需要 rdkit + matplotlib）: {e}"
    try:
        mol = Chem.MolFromSmiles(smiles)
        if mol is None:
            return f"错误：无效的 SMILES: {smiles}"
        AllChem.Compute2DCoords(mol)
    except Exception as e:
        return f"错误：无法生成 2D 坐标: {type(e).__name__}: {e}"
    conf = mol.GetConformer(0)
    _use_cjk_font(plt)
    _ELE_RGB = {"C": "#404040", "N": "#1a5fd0", "O": "#d03020", "H": "#c0c0c0",
                "Al": "#a07840", "S": "#b08020", "P": "#a050a0", "Se": "#a020b0",
                "Te": "#7a2bb0", "Cu": "#b06030", "F": "#30b08a", "Cl": "#2f8f3f",
                "Br": "#a03a20", "I": "#5a2aa0"}
    fig, ax = plt.subplots(figsize=(7.2, 5.0), dpi=150)
    xs = [conf.GetAtomPosition(a.GetIdx()).x for a in mol.GetAtoms()]
    ys = [conf.GetAtomPosition(a.GetIdx()).y for a in mol.GetAtoms()]
    pad = 1.0
    xmin, xmax = min(xs) - pad, max(xs) + pad
    ymin, ymax = min(ys) - pad, max(ys) + pad
    for b in mol.GetBonds():
        i, j = b.GetBeginAtomIdx(), b.GetEndAtomIdx()
        p1 = conf.GetAtomPosition(i)
        p2 = conf.GetAtomPosition(j)
        order = b.GetBondTypeAsDouble()
        if order >= 1.5:  # 双/三键：偏移 0.07 画双线
            d = ((p2.x - p1.x) ** 2 + (p2.y - p1.y) ** 2) ** 0.5
            if d == 0:
                d = 1.0
            ox, oy = -(p2.y - p1.y) / d * 0.07, (p2.x - p1.x) / d * 0.07
            ax.plot([p1.x + ox, p2.x + ox], [p1.y + oy, p2.y + oy], color="#202020", lw=1.3, zorder=1)
            ax.plot([p1.x - ox, p2.x - ox], [p1.y - oy, p2.y - oy], color="#202020", lw=1.3, zorder=1)
        else:
            ax.plot([p1.x, p2.x], [p1.y, p2.y], color="#202020", lw=1.3, zorder=1)
    for a in mol.GetAtoms():
        p = conf.GetAtomPosition(a.GetIdx())
        sym = a.GetSymbol()
        if sym == "H":
            continue  # 端基氢省略，避免杂乱
        color = _ELE_RGB.get(sym, "#7a8fa8")
        if sym == "C" and a.GetDegree() > 1:
            ax.plot(p.x, p.y, "o", ms=3.4, color=color, zorder=2)
            continue
        r = 0.30 if sym == "C" else 0.34
        ax.add_patch(plt.Circle((p.x, p.y), r, facecolor=color, edgecolor="black",
                                linewidth=0.8, zorder=3))
        ax.text(p.x + 0.42, p.y + 0.18, sym, fontsize=11, fontweight="bold",
                color=color, zorder=4,
                bbox=dict(facecolor="white", edgecolor="none", alpha=0.95, pad=0.4))
    ax.set_xlim(xmin, xmax)
    ax.set_ylim(ymin, ymax)
    ax.set_aspect("equal")
    ax.axis("off")
    try:
        formula = Chem.rdMolDescriptors.CalcMolFormula(mol)
        mw = Descriptors.ExactMolWt(mol)
    except Exception:
        formula, mw = "?", 0.0
    ut = title or f"{smiles}  |  {formula}  (MW {mw:.1f})"
    ax.set_title(ut, fontsize=12)
    gen_dir = os.path.join(HERE, "workspace")
    if not os.path.isdir(gen_dir):
        gen_dir = HERE
    if not outfile:
        outfile = os.path.join(gen_dir, f"struct_{int(time.time() * 1000)}.png")
    try:
        fig.tight_layout()
        fig.savefig(outfile, bbox_inches="tight", facecolor="white")
        plt.close(fig)
    except Exception as e:
        plt.close(fig)
        return f"错误：保存 PNG 失败: {type(e).__name__}: {e}"
    return f"已绘制 2D 结构式并保存: {outfile}\n分子式: {formula} | 分子量: {mw:.1f} g/mol | 原子数: {mol.GetNumAtoms()}"


# ---------- 化合物库卡片 2D 结构式（PNG 字节，供 Java 端 ImageIO 直接加载） ----------
_COMPOUND_PNG_CACHE = {}
_COMPOUND_ELE_RGB = {"C": "#404040", "N": "#1a5fd0", "O": "#d03020", "H": "#c0c0c0",
                     "Al": "#a07840", "S": "#b08020", "P": "#a050a0", "Se": "#a020b0",
                     "Te": "#7a2bb0", "Cu": "#b06030", "F": "#30b08a", "Cl": "#2f8f3f",
                     "Br": "#a03a20", "I": "#5a2aa0"}


def _compound_png_bytes(smiles="", name=""):
    """把合法 SMILES 绘制成白底紧凑 2D 结构式 PNG 字节。失败返回 b''，绝不抛异常。"""
    try:
        import io
        import matplotlib
        matplotlib.use("Agg")
        from matplotlib import pyplot as plt
        from rdkit import Chem
        from rdkit.Chem import AllChem
    except Exception:
        return b""
    mol = Chem.MolFromSmiles(smiles)
    if mol is None:
        return b""
    try:
        AllChem.Compute2DCoords(mol)
    except Exception:
        return b""
    try:
        _use_cjk_font(plt)
        conf = mol.GetConformer(0)
        xs = [conf.GetAtomPosition(a.GetIdx()).x for a in mol.GetAtoms()]
        ys = [conf.GetAtomPosition(a.GetIdx()).y for a in mol.GetAtoms()]
        n = len(xs)
        fig, ax = plt.subplots(figsize=(4.2 + 0.25 * n, 3.2 + 0.25 * n), dpi=220)
        pad = 1.1
        xmin, xmax = (min(xs) - pad, max(xs) + pad) if n else (0, 1)
        ymin, ymax = (min(ys) - pad, max(ys) + pad) if n else (0, 1)
        for b in mol.GetBonds():
            i, j = b.GetBeginAtomIdx(), b.GetEndAtomIdx()
            p1, p2 = conf.GetAtomPosition(i), conf.GetAtomPosition(j)
            order = b.GetBondTypeAsDouble()
            if order >= 1.5:
                d = (((p2.x - p1.x) ** 2 + (p2.y - p1.y) ** 2) ** 0.5) or 1.0
                ox, oy = -(p2.y - p1.y) / d * 0.07, (p2.x - p1.x) / d * 0.07
                ax.plot([p1.x + ox, p2.x + ox], [p1.y + oy, p2.y + oy], color="#202020", lw=1.8, zorder=1)
                ax.plot([p1.x - ox, p2.x - ox], [p1.y - oy, p2.y - oy], color="#202020", lw=1.8, zorder=1)
            else:
                ax.plot([p1.x, p2.x], [p1.y, p2.y], color="#202020", lw=1.8, zorder=1)
        for a in mol.GetAtoms():
            p = conf.GetAtomPosition(a.GetIdx())
            sym = a.GetSymbol()
            if sym == "H":
                continue
            color = _COMPOUND_ELE_RGB.get(sym, "#7a8fa8")
            if sym == "C" and a.GetDegree() > 1:
                ax.plot(p.x, p.y, "o", ms=4.2, color=color, zorder=2)
                continue
            r = 0.34 if sym == "C" else 0.40
            ax.add_patch(plt.Circle((p.x, p.y), r, facecolor=color, edgecolor="black", linewidth=1.0, zorder=3))
            ax.text(p.x + 0.52, p.y + 0.24, sym, fontsize=13, fontweight="bold", color=color, zorder=4,
                    bbox=dict(facecolor="white", edgecolor="none", alpha=0.95, pad=0.4))
        ax.set_xlim(xmin, xmax)
        ax.set_ylim(ymin, ymax)
        ax.set_aspect("equal")
        ax.axis("off")
        if name:
            ax.set_title(name, fontsize=12)
        buf = io.BytesIO()
        fig.tight_layout(pad=0.3)
        fig.savefig(buf, format="png", bbox_inches="tight", facecolor="white")
        plt.close(fig)
        return buf.getvalue()
    except Exception:
        try:
            plt.close("all")
        except Exception:
            pass
        return b""


# ---------- 谱图绘制（IR / UV-Vis，基于 cclib 解析的能量/频率与强度） ----------
def _use_cjk_font(plt):
    """尽力把 matplotlib 全局字体切到 Windows 中文字体，避免标题/标签显示成方框。"""
    try:
        import matplotlib.font_manager as _fm
        names = set(_fm.get_font_names())
        for cand in ("Microsoft YaHei", "SimHei", "SimSun", "PingFang SC",
                     "Noto Sans CJK SC", "Source Han Sans SC"):
            if cand in names:
                plt.rcParams["font.sans-serif"] = [cand]
                plt.rcParams["axes.unicode_minus"] = False
                return
    except Exception:
        pass


def _save_ax(fig, outfile):
    from matplotlib import pyplot as plt
    gen_dir = os.path.join(HERE, "workspace")
    if not os.path.isdir(gen_dir):
        gen_dir = HERE
    if not outfile:
        outfile = os.path.join(gen_dir, f"spectrum_{int(time.time() * 1000)}.png")
    try:
        fig.tight_layout()
        fig.savefig(outfile, bbox_inches="tight", facecolor="white")
        plt.close(fig)
        return outfile, ""
    except Exception as e:
        plt.close(fig)
        return "", f"错误：保存 PNG 失败: {type(e).__name__}: {e}"


# IR 官能团归属表（lo, hi, 标签）：把峰位翻译成化学键振动，低频段含 Al 系金属-配体键
_IR_ASSIGN = [
    (320, 560, "金属-碳/卤素键 (Al–C、Al–X)，低配位铝宾特征振动"),
    (560, 700, "金属-氮/氧键 (Al–N、Al–O) 伸缩，路易斯酸碱配位"),
    (700, 820, "Al=O 双键或芳环/烯 C–H 面外弯曲；长链 –(CH₂)n–(n≥4) 面内摇摆 ~720"),
    (820, 950, "烯/芳环 C–H 面外弯曲 (对位取代 ~830)；环骨架呼吸"),
    (950, 1080, "C–O(醇/醚)、C–N 伸缩；面内弯曲组合"),
    (1080, 1180, "C–O–C / C–N 伸缩；醚类 vs 或 s 带；C–F"),
    (1180, 1300, "C–N(芳胺) / C–O(酚) / C–S 伸缩；C–H 弯曲"),
    (1300, 1430, "O–H 面内弯曲、C–H 剪式；C–N 伸缩"),
    (1430, 1490, "CH₂/CH₃ 弯曲 (剪式/反对称)；芳环骨架"),
    (1490, 1580, "芳环 C=C 骨架振动 (环伸缩)"),
    (1580, 1650, "C=N / 环内 C=C 伸缩；N–H 弯曲 (酰胺 I)"),
    (1650, 1720, "C=C 伸缩、C=N 伸缩、N–H 弯曲"),
    (1720, 1860, "C=O 羰基伸缩：(~1740 酯/酸，~1715 酮/醛，~1680 共轭红移)"),
    (1980, 2120, "累积双键/异氰酸酯 (N=C=O ~2270 除外)；金属-羰基"),
    (2120, 2320, "C≡N / C≡C 叁键伸缩；累积双键 (R-N≡C / CO₂ ~2349)"),
    (2450, 2620, "S–H / Se–H / Te–H 伸缩 (弱而尖)"),
    (2850, 3000, "饱和 C–H 伸缩 (CH₂ 反对称 ~2925、对称 ~2850，CH₃ ~2960)"),
    (3000, 3200, "不饱和/芳香 C–H 伸缩 (sp²/sp, ~3030–3080)"),
    (3200, 3500, "N–H 伸缩；O–H 游离 ~3600、氢键/缔合红移至 3300 附近 (宽)"),
]
_IR_ASSIGN.sort(key=lambda x: x[0])  # 低频在前，保证选中最窄/最近的归属


def _ir_assign(freq):
    """返回给定波数对应的官能团归属描述；找不到则给通用说明。"""
    best, bd = None, None
    for lo, hi, lab in _IR_ASSIGN:
        if lo <= freq <= hi:
            d = min(abs(freq - lo), abs(freq - hi))
            if bd is None or d < bd:
                best, bd = lab, d
    if best:
        return best
    if freq < 0:
        return "负频率(虚频)：对应鞍点/过渡态特征，沿该简正坐标的能量极大方向"
    return "指纹区/低频组合振动，具体归属需结合参考谱或振动动画判定"


def _uv_interpret(nm, f):
    """按吸收波长区间给出电子跃迁类型与化学意义解读（nm 作对照参考）。"""
    if nm < 200:
        return "高能段：σ→σ* 或高能 Rydberg，真空紫外，多为饱和骨架/孤对电子跃迁"
    if nm < 300:
        return "π→π* 或 n→π*，对应碳基发色团/中等共轭体系"
    if nm < 370:
        return "n→π* 或中等共轭 π→π*（C=O、N=O、共轭烯/芳环），偏长波吸收"
    if nm < 500:
        return "长共轭/电荷转移(CT)跃迁，多发生在给体-受体或金属-配体体系"
    return "可见区：金属 d–d 或弱电荷转移，分子呈有色(吸收补色)"


def _plot_ir(data, file, outfile, title):
    from matplotlib import pyplot as plt
    _use_cjk_font(plt)
    _vib = getattr(data, "vibfreqs", None)
    freqs = list(_vib) if _vib is not None else []
    if not freqs:
        return "错误：结果文件没有振动频率数据（请确认是 freq 任务输出）。"
    _st = getattr(data, "vibirs", None)
    strengths = list(_st) if _st is not None else []
    have_inten = len(strengths) == len(freqs)
    peaks = []
    for i, f in enumerate(freqs):
        s = strengths[i] if have_inten else 1.0
        peaks.append((f, s))
    peaks.sort(key=lambda x: x[1], reverse=True)
    top = [f"{int(round(f))} cm⁻¹" for f, _ in peaks[:5]]
    fig, ax = plt.subplots(figsize=(7.5, 4.6), dpi=150)
    ax.bar([f for f, _ in peaks], [s for _, s in peaks], width=4.0,
           color="#3a6fa8", edgecolor="#274b73", label="IR 峰")
    ax.invert_xaxis()
    ax.set_xlabel("波数 (cm-1)")
    ax.set_ylabel("强度" + (" (km/mol)" if have_inten else ""))
    ax.set_title(title or f"IR 谱图 — {os.path.basename(file)}")
    ax.legend()
    for f, st in peaks[:3]:
        ax.annotate(f"{int(round(f))} cm-1", xy=(f, st), xytext=(0, 4),
                    textcoords="offset points", fontsize=7, ha="center", color="#444")
    out, err = _save_ax(fig, outfile)
    if err:
        return err
    summary = "、".join(top)
    # 谱图解读：把前 5 个最强峰逐一带上官能团归属
    lines = [f"IR 谱图已保存: {out}\n共 {len(freqs)} 个振动模式；最强 5 个峰: {summary}\n",
             "【谱图解读】最强峰归属:"]
    for f, s in peaks[:5]:
        lines.append(f"· {int(round(f))} cm⁻¹（强度 {s:7.1f}" + (" km/mol" if have_inten else " arb")
                     + f"）：{_ir_assign(f)}")
    if [f for f in freqs if f < 0]:
        lines.append(f"· 检测到虚频 {len([f for f in freqs if f < 0])} 个：为过渡态/鞍点特征，"
                     f"沿其简正坐标是能量极大方向（对应唯一大虚频则近似 transition state）。")
    lines.append("· 提示：C=O ~1715、O–H ~3300(宽)、C≡N ~2200 是强鉴别信号；低频 Al–N/O/C 键可佐证配位/金属成键。")
    return "\n".join(lines)


def _plot_uv(data, file, outfile, title):
    from matplotlib import pyplot as plt
    _use_cjk_font(plt)
    _e = getattr(data, "etenergies", None)
    eng = list(_e) if _e is not None else []
    if not eng:
        return "错误：结果文件没有激发态能级数据（请确认是 TDDFT/激发态任务输出）。"
    _o = getattr(data, "etoscs", None)
    osc = list(_o) if _o is not None else []
    nm = [1239.8 / e if e > 1e-6 else 0.0 for e in eng]
    peaks = []
    for i, e in enumerate(eng):
        s = osc[i] if len(osc) > i else 0.0
        peaks.append((nm[i], e, s))
    peaks.sort(key=lambda x: x[2], reverse=True)
    showed = f"；最强跃迁 λ ≈ {peaks[0][0]:.1f} nm（f = {peaks[0][2]:.3f}）" if peaks else ""
    fig, ax = plt.subplots(figsize=(7.5, 4.6), dpi=150)
    ax.plot(nm, osc if len(osc) == len(nm) else [0.0] * len(nm),
            color="#a0303a", marker="o", ms=3, lw=1.4, label="振荡强度 f  (eV→nm)")
    ax.set_xlabel("波长 (nm)")
    ax.set_ylabel("振荡强度 (f)")
    ax.set_title(title or f"UV-Vis 谱图 — {os.path.basename(file)}")
    ax.legend()
    # 高亮最强跃迁
    if peaks:
        ax.annotate(f"{peaks[0][0]:.1f} nm", xy=(peaks[0][0], peaks[0][2]),
                    xytext=(8, 8), textcoords="offset points", color="#a0303a",
                    arrowprops=dict(arrowstyle="->", color="#a0303a", lw=0.8))
    out, err = _save_ax(fig, outfile)
    if err:
        return err
    # 谱图解读：前 3 个强跃迁按波长区间给出电子跃迁类型 + 综合判读
    lines = [f"UV-Vis 谱图已保存: {out}\n共列出 {len(eng)} 个激发态跃迁{showed}\n",
             "【谱图解读】若干强吸收跃迁:"]
    for nm, e, s in peaks[:3]:
        lines.append(f"· λ≈{nm:.1f} nm（E={e:.2f} eV，f={s:.3f}）：{_uv_interpret(nm, s)}")
    if peaks:
        sd = peaks[0][2]
        block = ("（强吸收，π→π*，发色团明确）" if sd >= 0.1
                 else "（弱吸收，n→π* 或对称禁阻跃迁）")
        lines.append(f"· 综合：最强跃迁 λ≈{peaks[0][0]:.1f} nm、f={sd:.3f}{block}；"
                     f"f>0.1 为强 π→π* 吸收，f过小则跃迁弱/禁阻。")
    lines.append("· 能量换算 E(eV)=1240/λ(nm)；激发态计算建议用带长程修正泛函（CAM-B3LYP/ωB97X-D）。")
    return "\n".join(lines)


_IMAGE_EXTS = (".png", ".jpg", ".jpeg", ".gif", ".bmp", ".tif", ".tiff", ".webp")


def _ir_image_profile(path):
    """从 IR 谱图图片提取 (cm-1[] , 吸收相对强度[]) 曲线。返回 (cm, p, error)。
    仅做坐标重建，不保证绝对定量；依赖 scipy/PIL（在 rdkit-env 提供）。"""
    try:
        import numpy as np
        from PIL import Image
        from scipy.ndimage import gaussian_filter1d
    except ImportError as e:
        return None, None, f"图像谱图解析需要 numpy/pillow/scipy：{e}"
    try:
        a = np.asarray(Image.open(path).convert("RGB")).astype(int)
    except Exception as e:
        return None, None, f"无法读取图片 {path}: {type(e).__name__}: {e}"
    H, W, _ = a.shape
    if W < 60 or H < 60:
        return None, None, "图片过小，无法识别谱图"
    mx = a.max(axis=2)
    mn = a.min(axis=2)
    ink = ((mx - mn > 40) & (mx > 90)) | (mx < 150)  # 强彩色线 或 深色线
    m = max(2, int(W * 0.012))
    mm = max(2, int(H * 0.012))
    ink[:mm, :] = False
    ink[-mm:, :] = False
    ink[:, :m] = False
    ink[:, -m:] = False
    # 去掉满幅直线（边框/网格）：曲线是弯曲的，不会整行/整列铺满
    rs = ink.sum(axis=1)
    ink[np.nonzero(rs > 0.6 * W)[0], :] = False
    csum = ink.sum(axis=0)
    ink[:, np.nonzero(csum > 0.9 * H)[0]] = False
    col_ys = [np.nonzero(ink[:, x])[0] for x in range(W)]
    ys = np.full(W, np.nan, dtype=float)
    for x in range(W):
        v = col_ys[x]
        if len(v):
            ys[x] = np.median(v[: min(len(v), 5)])  # 上包络 = 曲线上缘
    x0 = np.nonzero(np.isfinite(ys))[0]
    if len(x0) < 60:
        return None, None, "未能提取到清晰的谱图曲线（可能曲线颜色/对比度较弱或有网格干扰）"
    p = np.interp(np.arange(W), x0, ys[x0])
    p = gaussian_filter1d(gaussian_filter1d(p, 7), 7)  # 平滑线宽/抗锯齿抖动
    return np.arange(W), p, None  # H 供调用方判显著度


def _ir_image_peaks(cm, p, H, hi_cm=4000.0, lo_cm=400.0):
    """把像素 x 映射到波数，中阈识峰，返回 [(cm-1, score0-1)] 按显著度降序。"""
    import numpy as np
    from scipy.signal import find_peaks
    W = len(p)
    wcm = hi_cm - (np.arange(W) / (W - 1)) * (hi_cm - lo_cm)
    amp = float(np.nanpercentile(p, 5))
    pk, props = find_peaks(p,
                           prominence=max(H * 0.008, 0.07 * (np.nanpercentile(p, 99) - amp)),
                           width=7, distance=12)
    if not len(pk):
        return []
    pr = np.asarray(props["prominences"], dtype=float)
    pmax = pr.max() or 1.0
    keep = []
    for i, w in zip(pk, pr):
        if lo_cm - 2 <= wcm[i] <= hi_cm + 2:
            keep.append((float(wcm[i]), float(w / pmax)))
    keep.sort(key=lambda t: -t[1])
    return keep[:14]


def _mark_image_peaks(path, peaks, outfile, wcm_min=4000.0, wcm_max=400.0, title=""):
    """在原图上以红色竖线+波数标签标记识别的峰位，保存并返回标记图路径（便于人工核对）。"""
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError as e:
        return "", f"标记峰图需 pillow：{e}"
    try:
        im = Image.open(path).convert("RGB")
        W, H = im.size
        d = ImageDraw.Draw(im)
        try:
            fnt = ImageFont.truetype("arial.ttf", 16)
        except Exception:
            fnt = ImageFont.load_default()
        def xof(w):
            return int(round((wcm_min - w) / (wcm_min - wcm_max + 1e-9) * (W - 1)))
        for w, s in peaks:
            x = max(0, min(W - 1, xof(w)))
            d.line([(x, int(H * 0.04)), (x, int(H * 0.96))], fill=(220, 40, 40), width=2)
            d.text((x + 2, int(H * 0.03)), f"{int(round(w))}",
                   fill=(220, 40, 40), font=fnt)
        if title:
            d.text((8, 8), title, fill=(0, 0, 0), font=fnt)
        im.save(outfile, "PNG")
        return outfile, ""
    except Exception as e:
        return "", f"标记峰图失败: {type(e).__name__}: {e}"


def _interpret_peak_list(peaks):
    """对一组波数(cm-1)做官能团归属（手动/自动谱峰通用）。peaks=[(cm,score)...] 或 [cm,...]。"""
    lines = ["【谱图解读】峰位归属："]
    for item in peaks:
        if isinstance(item, (tuple, list)):
            w, score = item[0], item[1]
            suffix = (f"（自动识峰显著度 {score:.2f}）" if score is not None else "")
        else:
            w, score = item, None
            suffix = ""
        lines.append(f"· {int(round(w))} cm⁻¹{suffix}：{_ir_assign(w)}")
    return "\n".join(lines)


def _t_visualize_spectrum(file="", type="", outfile="", title="", peaks=""):
    """从 Gaussian/ORCA 输出文件绘制并解读 IR/UV-Vis 谱图；或从 IR 谱图图片自动识峰解读；
    也支持用 peaks= 直接给峰列表解读（外部/实测谱图最可靠途径）。"""
    # 方式一：外部峰列表（手动提供，最可靠）
    if (peaks or "").strip():
        nums = []
        for tok in str(peaks).replace("，", ",").replace("；", ",").split(","):
            tok = tok.strip()
            if not tok:
                continue
            try:
                v = float(tok)
            except ValueError:
                continue
            if 100 <= v <= 4000:
                nums.append(v)
        if nums:
            if not file or file.endswith(_IMAGE_EXTS):
                re = _interpret_peak_list(nums)
                return re
            # file 为 DFT 文本且给了 peaks：直接解读峰列表
            return _interpret_peak_list(nums)
        return "错误：peaks 未解析到有效波数（示例 peaks='3434,1734,1465'，单位 cm-1）。"
    # 方式二：IR 谱图图片 → 自动识峰
    is_img = bool(file) and file.lower().endswith(_IMAGE_EXTS)
    if is_img:
        return _t_ir_image(file=file, outfile=outfile, title=title)
    # 方式三：Gaussian/ORCA 结果文件（cclib 常规路径）
    return _t_spectrum_from_log(file, type, outfile, title)


def _t_ir_image(file="", outfile="", title="", hi_cm=0.0, lo_cm=0.0):
    """IR 谱图图片自动识峰并解读：重建曲线→识峰→官能团归属，并生成带标记的原图核对。"""
    import numpy as np
    import os
    hi = hi_cm or 4000.0
    lo = lo_cm or 400.0
    if hi <= lo:
        hi, lo = 4000.0, 400.0
    if not file or not os.path.isfile(file):
        return "错误：visualize_spectrum 需要 file（图片路径）。"
    cm, p, err = _ir_image_profile(file)
    if err:
        # 无曲线也可退回：若无自动判型，直接返回错误
        return err
    H = int(np.asarray(__import__("PIL").Image.open(file)).shape[0])
    peaks = _ir_image_peaks(cm, p, H, hi, lo)
    gen_dir = os.path.join(HERE, "workspace")
    if not os.path.isdir(gen_dir):
        gen_dir = HERE
    marked = outfile or os.path.join(gen_dir, f"ir_marked_{int(time.time() * 1000)}.png")
    marked, merr = _mark_image_peaks(file, peaks, marked, hi, lo, title or "自动识峰标记")
    lines = [f"IR 谱图（图片）已解析：共识别 {len(peaks)} 个吸收带。" if peaks
             else "IR 谱图（图片）已解析：未检测到显著吸收带（可改用 peaks='3434,1734' 手动指定）。",
             f"自动识峰标记图：{marked or merr}"]
    lines.append("说明：自动识峰依据坐标重建与峰值检测，扫描质量/基线/网格会影响精度，请以标记图核对，"
                 "必要时用 peaks= 手动指定峰位。")
    lines.append(_interpret_peak_list(peaks))
    return "\n".join(lines)


def _t_spectrum_from_log(file, type, outfile, title):
    """cclib 常规路径：解析 Gaussian/ORCA 文本结果文件。"""
    if not file or not os.path.isfile(file):
        return "错误：visualize_spectrum 需要 file（Gaussian/ORCA 输出文件路径）。"
    try:
        import cclib
    except ImportError:
        return "未安装 cclib（pip install cclib）。"
    try:
        import matplotlib
    except ImportError:
        return "未安装 matplotlib（pip install matplotlib）。"
    try:
        data = cclib.io.ccread(file)
        if data is None:
            return "错误：无法用 cclib 解析该文件（可能是非量子化学输出或解析失败）。"
    except Exception as e:
        return f"错误：谱图解析失败: {type(e).__name__}: {e}"
    vib = getattr(data, "vibfreqs", None)
    uv_e = getattr(data, "etenergies", None)
    t = (type or "").strip().lower()
    if not t:
        if uv_e is not None and len(uv_e):
            t = "uv"
        elif vib is not None and len(vib):
            t = "ir"
    if t in ("ir", "vib", "红外", "振动", "freq", "频率"):
        return _plot_ir(data, file, outfile, title)
    if t in ("uv", "uv-vis", "uvvis", "紫外", "吸收", "uv-vis"):
        return _plot_uv(data, file, outfile, title)
    return (f"错误：需用 type=ir（红外）或 type=uv（紫外可见）指定谱类型。\n"
            f"该文件含振动频率 {len(vib) if vib is not None else 0} 个、激发态 "
            f"{len(uv_e) if uv_e is not None else 0} 个。")


def _t_get_skill_content(name):
    if not KB_AVAILABLE:
        return "知识库不可用。"
    skill = KB.get_skill(name)
    return skill.content if skill else f"未找到技能: {name}"


def _t_query_pubchem(identifier, id_type="name"):
    if not KB_AVAILABLE:
        return "知识库不可用。"
    result = KB.query_pubchem(identifier, id_type)
    if not result:
        return f"❌ 未找到化合物：{identifier}"
    output = f"🔬 PubChem 化合物查询结果（{identifier}）：\n"
    if "ConnectivitySMILES" in result:
        output += f"✅ ConnectivitySMILES: {result['ConnectivitySMILES']}\n"
    for key, value in result.items():
        if key in ["CID", "ConnectivitySMILES"]:
            continue
        output += f"  {key}: {value}\n"
    return output


def _t_plan_reaction(reactant, product):
    return (f"反应路径规划（模拟）:\n反应物: {reactant}\n产物: {product}\n"
            f"建议步骤: \n1. 氧化加成\n2. 配体交换\n3. 还原消除")


def _t_run_python(code=None, file="", timeout=60, cwd=None):
    """在隔离的子进程里执行 Python 脚本（脚本能力：写→跑→读→改→再跑）。

    - code：直接传入的 Python 源码
    - file：已有脚本路径（优先于 code）
    - timeout：超时秒（默认 60，上限 300）
    - cwd：工作目录，默认系统工作目录
    会用当前 Python 环境（含 RDKit）执行；code 自动落盘到 workspace/scripts/
    以便追踪、读取与复用，返回 returncode/stdout/stderr 与脚本路径。
    """
    if not code and not file:
        return "错误：run_python 需要 code（Python源码）或 file（已有脚本路径）参数之一。"
    script = file
    if script:
        if not os.path.isfile(script):
            return f"错误：脚本文件不存在: {script}"
    else:
        script_dir = os.path.join(os.getcwd(), "scripts")
        try:
            os.makedirs(script_dir, exist_ok=True)
            script = os.path.join(script_dir, f"agent_{int(time.time()*1000)}.py")
            with open(script, "w", encoding="utf-8") as f:
                f.write(code)
        except Exception as e:
            return f"错误：写入脚本失败: {e}"
    try:
        timeout = max(1, min(int(timeout or 60), 300))
    except Exception:
        timeout = 60
    try:
        proc = subprocess.run([sys.executable, "-u", script],
                              capture_output=True, text=True, timeout=timeout,
                              cwd=cwd or os.getcwd())
    except subprocess.TimeoutExpired:
        return (f"脚本超时（>{timeout}s），路径: {script}\n"
                f"可先 read_file 该脚本检查，改进后带 file 参数再次 run_python。")
    except Exception as e:
        return f"脚本执行启动失败: {e}（脚本已保存于 {script}）"
    stdout, stderr, rc = proc.stdout, proc.stderr, proc.returncode
    return (f"脚本: {script}\n返回码: {rc}\n"
            f"stdout:\n{stdout}\nstderr:\n{stderr}")


# ---------- 跨环境量子化学工具：WSL 中的 QE / Docker 容器中的 PySCF ----------
def _tail(s, n=3000):
    s = s or ""
    if len(s) <= n:
        return s
    return f"...(截断 {len(s) - n} 字符)...\n{s[-n:]}"


def _win_to_wsl_path(p):
    """C:\\a\\b → /mnt/c/a/b；已是 Linux 风格路径则原样返回"""
    p = os.path.normpath(p).replace("\\", "/")
    m = re.match(r"^([A-Za-z]):(/.*)$", p)
    return f"/mnt/{m.group(1).lower()}{m.group(2)}" if m else p


def _t_qe_calculation(input_data="", file="", command="pw.x", extra_args="", timeout=300):
    """调用 WSL 中的 Quantum Espresso 执行周期体系电子结构计算。

    - input_data：QE 标准输入 .in 文本（含 &CONTROL/&SYSTEM/&ELECTRONS 等卡，
      ATOMIC_POSITIONS/CELL/K_POINTS 等块）；与 file 二选一。
    - file：Windows 路径下的 .in 输入文件（优先于 input_data）。
    - command：QE 可执行名，默认 pw.x（可选 cp.x / bands.x / pp.x 等）。
    - extra_args：附加命令行参数，可覆盖默认的 ``-in <input>``（如 pp.x 用 ``-pp``）。
    QE 在输入文件所在目录内运行（波函数/电荷密度按 prefix 写回该目录），
    返回输出尾部，并对 pw.x 自动提取最后一步总能量。
    """
    cfg = _load_dft_config()
    distro = (cfg.get("qe_wsl_distro") or "Ubuntu").strip() or "Ubuntu"
    cmd = ((cfg.get("qe_command") or "").strip() or command or "pw.x").strip()
    try:
        timeout = max(10, min(int(timeout or 300), 1800))
    except Exception:
        timeout = 300
    in_path = ""
    if file:
        if not os.path.isfile(file):
            return f"错误：QE输入文件不存在: {file}"
        in_path = file
    elif (input_data or "").strip():
        workspace = (cfg.get("workspace") or os.path.join(os.getcwd(), "workspace"))
        runs_dir = os.path.join(workspace, "qe_runs")
        try:
            os.makedirs(runs_dir, exist_ok=True)
            in_path = os.path.join(runs_dir, f"qe_{int(time.time()*1000)}.in")
            with open(in_path, "w", encoding="utf-8", newline="\n") as f:
                f.write(input_data)
        except Exception as e:
            return f"错误：QE输入落盘失败: {e}"
    else:
        return "错误：qe_calculation 需要 input（QE输入文本）或 file（.in文件路径）参数之一。"
    wsl_in = _win_to_wsl_path(in_path)
    wsl_dir = _win_to_wsl_path(os.path.dirname(in_path) or os.getcwd())
    in_flag = f"-in {wsl_in}"
    run = f"cd '{wsl_dir}' && {cmd} {(extra_args or '').strip() or in_flag} 2>&1"
    argv = ["wsl.exe", "-d", distro, "-e", "bash", "-lc", run]
    try:
        proc = subprocess.run(argv, capture_output=True, text=True, timeout=timeout)
    except FileNotFoundError:
        return ("错误：未找到 wsl.exe（当前环境 WSL CLI 不可用，请确认 WSL 已安装）。"
                "可在「设置与状态 → DFT 计算后端配置」核对 qe_wsl_distro。")
    except subprocess.TimeoutExpired:
        return f"QE计算超时（>{timeout}s，命令 {cmd}）。可增大 timeout 或改用更小的体系。"
    out = (proc.stdout or "") + ("\n" + proc.stderr if proc.stderr else "")
    summary = []
    if f"{cmd}".rstrip() == "pw.x":
        ms = re.findall(r"!\s+total energy\s*=\s*(-?[\d.]+)\s+Ry", out)
        if ms:
            ry = float(ms[-1])
            summary.append(f"总能量: {ry:.8f} Ry = {2*ry:.6f} Hartree (最后一步)")
        if "JOB DONE" in out:
            summary.append("状态: JOB DONE（正常结束）")
        elif "aborting calculation" in out or "Error in routine" in out:
            summary.append("状态: 计算中止（见输出尾部错误）")
    head = "\n".join(summary) + "\n" if summary else ""
    return (f"QE（WSL {distro} / {cmd}）返回码: {proc.returncode}\n"
            f"输入: {in_path}\n{head}输出尾部:\n{_tail(out, 4000)}")


def _t_pyscf_calculation(code="", file="", timeout=300):
    """在 Docker 容器(molssi/pyscf221-mamba141, PySCF 2.2.1)中执行 PySCF 代码。

    - code：PySCF Python 源码，必须在代码内以内联字符串自行提供分子结构
      （容器不共享本地文件系统）；容器内以 /opt/conda/bin/python -u - 逐字执行。
    - file：本地 .py 脚本路径，将其内容读入并喂给容器执行（优先于 code）。
    返回返回码与输出尾部；可用分子/周期体系 HF/DFT、几何优化、激发态、Mulliken等。
    """
    if file:
        if not os.path.isfile(file):
            return f"错误：脚本文件不存在: {file}"
        with open(file, encoding="utf-8") as f:
            code = f.read()
    if not (code or "").strip():
        return "错误：pyscf_calculation 需要 code（PySCF Python源码）或 file（.py脚本路径）参数之一。"
    cfg = _load_dft_config()
    container = (cfg.get("pyscf_docker_container") or "pyscf").strip() or "pyscf"
    py = (cfg.get("pyscf_docker_python") or "/opt/conda/bin/python").strip() \
        or "/opt/conda/bin/python"
    try:
        timeout = max(5, min(int(timeout or 300), 1800))
    except Exception:
        timeout = 300
    argv = ["docker", "exec", "-i", container, py, "-u", "-"]
    try:
        proc = subprocess.run(argv, input=code, capture_output=True, text=True, timeout=timeout)
    except FileNotFoundError:
        return ("错误：未找到 docker 命令（当前环境 Docker CLI 不可用）。"
                "可在「设置与状态 → DFT 计算后端配置」核对 pyscf_docker_container。")
    except Exception as e:
        return f"PySCF执行启动失败: {e}"
    out = _tail(proc.stdout or "", 4000)
    err = _tail(proc.stderr or "", 2000)
    return (f"PySCF（容器 {container}，PySCF 2.2.1）返回码: {proc.returncode}\n"
            f"stdout尾部:\n{out}\nstderr尾部:\n{err}")


def _dft_job(smiles, job_type, method=None, basis=None, backend=None, precision=None):
    if not DFT_AVAILABLE:
        return "DFT功能不可用。"
    try:
        # 泛函/基组智能选择：任务类型 + 分子成分 + BSE 校验
        task_kind = job_type if job_type in ("sp", "opt", "freq", "ts") else "sp"
        method, basis = auto_method_basis(smiles, method, basis, task_kind=task_kind,
                                          precision=precision)
        inp = DFTInput(smiles=smiles, method=method, basis=basis, job_type=job_type)
        result = DFT_MANAGER.run_dft(inp, backend=backend)
        # 显示实际使用的后端：显式指定优先，否则用管理器默认值
        backend = backend or getattr(DFT_MANAGER, "default_backend", "local_g16")
        if not result.success:
            reason = (result.error_message or "").strip() or "后端未返回错误信息（本机可能未安装该量子化学程序）"
            return (f"计算失败（后端 {backend}）: {reason}\n"
                    "提示：可在「设置与状态 → DFT 计算后端配置」中设置 Gaussian/ORCA 程序路径、"
                    "SSH 集群连接或工作区目录。")
        # 输出文件清单（GUI 依此渲染附件卡片）
        files = [p for p in (result.log_file, result.fchk_file, result.molden_file)
                 if p and os.path.exists(p)]
        files_note = ("\n输出文件:\n" + "\n".join(f"- {p}" for p in files)) if files else ""
        if job_type == "freq":
            if result.frequencies:
                imag = sum(1 for f in result.frequencies if f < 0)
                return (f"频率计算完成（后端 {backend}），虚频数: {imag}"
                        f"（全部为正则结构为稳定极小点）{files_note}")
            return f"频率计算完成，但输出中未包含频率数据。{files_note}"
        if result.energy is None:
            return f"计算完成（后端 {backend}），但未能从输出文件解析出能量。{files_note}"
        label = "单点能计算完成" if job_type == "sp" else "几何优化完成"
        energy_au = f"（{result.energy_au:.6f} Hartree）" if result.energy_au else ""
        return (f"{label}（后端 {backend}）:\nSMILES: {smiles}\n"
                f"能量: {result.energy:.6f} kcal/mol {energy_au}{files_note}")
    except Exception as e:
        backend = getattr(DFT_MANAGER, "default_backend", "local_g16") if DFT_MANAGER else ""
        return (f"计算失败（后端 {backend}）: {e}\n"
                "提示：请在「设置与状态 → DFT 计算后端配置」中检查程序路径或 SSH 集群连接。")


def _format_ts_report(r: dict) -> str:
    """将过渡态搜索报告格式化为用户可读文本"""
    ts = r.get("ts") or {}
    has_data = bool(r.get("scan_points") or ts.get("energy_hartree") is not None
                    or r.get("scan_log"))
    if r.get("error") and not has_data:
        return f"过渡态搜索失败: {r['error']}"
    status = "完成（含告警）" if r.get("error") else "完成"
    mode = r.get("mode") or "eta2"
    mode_desc = "η¹-O 端进攻（Lewis 酸碱加成）" if mode == "eta1" else "[2+2] 型侧向配位"
    lines = [f"过渡态搜索{status}（{r.get('method')}/{r.get('basis')}，后端 {r.get('backend')}）",
             f"SMILES: {r.get('smiles')}（复合物 {r.get('n_atoms')} 原子，Al 为第 {r.get('al_idx')} 号原子）",
             f"反应网络模式: {mode} —— {mode_desc}"]
    if r.get("error"):
        lines.append(f"告警: {r['error']}")

    freeze = r.get("freeze_bonds") or []
    if freeze:
        pairs = ", ".join(f"({i}-{j})" for i, j in freeze)
        lines.append(f"迁移基团保护: 已冻结金属-甲基键 {pairs}（防止 release 弛豫到"
                     "甲基迁移插入产物，见铝宾19/20 教训）")

    pts = r.get("scan_points") or []
    if pts:
        e0 = pts[0]["energy_hartree"]
        rc_label = "Al···O" if mode == "eta1" else "Al···C"
        lines.append(f"\n{rc_label} 松弛扫描（freeze 阶段，反应进度 ξ 正方向 = 距离缩短）能量曲线:")
        if r.get("scan_incomplete"):
            lines.append(f"  （扫描未完整终止：鞍点附近约束优化发散，"
                         f"已用前 {len(pts)} 个收敛点继续）")
        for p in pts:
            dE = (p["energy_hartree"] - e0) * 627.509
            lines.append(f"  步 {p['step']:>2d}: E = {p['energy_hartree']:.6f} Hartree "
                         f"(ΔE = {dE:+.2f} kcal/mol)")
    if r.get("barrier_estimate_kcal") is not None:
        lines.append(f"扫描最高点相对起点势垒（近似）: {r['barrier_estimate_kcal']:.2f} kcal/mol")
    if r.get("scan_boundary") == "end":
        lines.append("警告：能量最高点位于扫描终点，势垒顶可能在更短 Al···C 距离处，"
                     "TS 优化若不收敛建议延长扫描范围重试")

    lines.append("\nTS 优化（release 阶段）:")
    if ts.get("energy_hartree") is not None:
        lines.append(f"  收敛: {'是' if ts.get('converged') else '否'}；"
                     f"能量: {ts['energy_hartree']:.6f} Hartree"
                     + (f"（ZPE {ts.get('zpe_hartree'):.6f}）" if ts.get("zpe_hartree") else ""))
    imag = ts.get("imaginary_freqs_cm1") or []
    if imag:
        lines.append(f"  虚频: {', '.join(f'{f:.1f}i cm⁻¹' for f in imag)}"
                     f"（共 {ts.get('n_freqs')} 个频率，唯一虚频即过渡态）")
        if ts.get("imag_mode_share") is not None:
            share = ts["imag_mode_share"]
            verdict = ("（合格）" if ts.get("imag_mode_consistent")
                       else "（不合格：旁观者运动主导，并非目标反应过渡态）")
            lines.append(f"  虚频-反应坐标一致性: 反应原子（Al+CO₂）位移占比 "
                         f"{share*100:.1f}% {verdict}")
        if ts.get("wigner_kappa_298K") is not None:
            lines.append(f"  Wigner 隧道修正因子 κ(298K): {ts['wigner_kappa_298K']}"
                         "（κ>1 表示隧穿增强穿越势垒的有效速率）")
    else:
        lines.append("  未检出虚频：该构型为极小点而非过渡态，建议加严扫描步长或更换反应坐标重试")

    files = [p for p in (r.get("scan_log"), r.get("ts_log"), r.get("ts_fchk"))
             if p and os.path.exists(p)]
    if files:
        lines.append("\n输出文件:\n" + "\n".join(f"- {p}" for p in files))
    if not r.get("success"):
        lines.append("\n注意：TS 优化未完全收敛或未正常终止，结果仅供参考。")
    return "\n".join(lines)


def _format_mw_charge(r: dict, fchk: str, scheme: str) -> str:
    """Multiwfn 电荷分析结果格式化为用户可读文本"""
    atoms = r.get("atoms") or []
    if not atoms:
        return f"Multiwfn {scheme} 电荷分析完成，但未能解析原子电荷（原始输出见 raw_output）。"
    lines = [f"Multiwfn {scheme} 原子电荷: {fchk}",
             "原子电荷 (e):"]
    for a in atoms:
        lines.append(f"  {a['index']:>3d} {a['symbol']:<2s} {a['charge']:+.4f}")
    lines.append(f"总电荷: {r.get('total_charge', 0):+.6f}")
    return "\n".join(lines)


def _format_mw_cubes(r: dict, fchk: str, kind: str) -> str:
    """Multiwfn RDG/IRI 分析结果格式化为用户可读文本"""
    cubes = r.get("cube_files") or {}
    lines = [f"Multiwfn {kind} 弱相互作用分析完成: {fchk}"]
    if cubes:
        lines.append("等值面数据文件（sign(lambda2)rho 与 " + kind.split("/")[0]
                     + "，可导入 VMD/GaussView 作等值面图）:")
        lines += [f"- {p}" for p in cubes.values()]
    else:
        lines.append("警告：未生成 cube 文件。原始输出尾部：\n" + (r.get("raw_output") or "")[-800:])
    return "\n".join(lines)


# ---------- 异步任务管理器（提交后中断对话，脚本监控，完成后恢复对话） ----------
TASKS_PATH = _state_path("tasks.json")
_TASKS = {}
_TASKS_LOCK = threading.Lock()
TASK_POLL_SECONDS = 3
# 区分任务后台线程与智能体 HTTP 线程：仅前者允许同步执行耗时工具
_TASK_THREAD = threading.local()


def _load_tasks():
    global _TASKS
    try:
        if os.path.exists(TASKS_PATH):
            with open(TASKS_PATH, "r", encoding="utf-8") as f:
                _TASKS = json.load(f)
    except Exception:
        _TASKS = {}
    # 服务重启后，旧线程已丢失：标记为中断
    for t in _TASKS.values():
        if t.get("status") == "running":
            t["status"] = "interrupted"
            t["error"] = "服务层重启，任务中断，请重新提交"


def _save_tasks_locked():
    try:
        with open(TASKS_PATH, "w", encoding="utf-8") as f:
            json.dump(_TASKS, f, ensure_ascii=False, indent=1)
    except Exception as e:
        print(f"[tasks] 保存失败: {e}")


def _run_task(task_id):
    """后台线程：执行工具并更新任务状态（监控脚本可通过 /api/task/status 轮询）"""
    t = _TASKS.get(task_id)
    if not t:
        return
    _TASK_THREAD.is_task = True
    try:
        result = execute_tool(t["tool"], t["params"])
        t["result"] = result
        t["status"] = "done"
    except Exception as e:
        t["result"] = None
        t["error"] = f"{type(e).__name__}: {e}"
        t["status"] = "failed"
    finally:
        t["finished_at"] = time.time()
        with _TASKS_LOCK:
            _save_tasks_locked()


def _submit_task(tool, params, label=""):
    task_id = f"zs-task-{int(time.time() * 1000)}-{len(_TASKS) + 1}"
    with _TASKS_LOCK:
        _TASKS[task_id] = {
            "id": task_id, "tool": tool, "params": params, "label": label,
            "status": "running", "result": None, "error": None,
            "submitted_at": time.time(), "finished_at": None,
        }
        _save_tasks_locked()
    threading.Thread(target=_run_task, args=(task_id,), daemon=True).start()
    return task_id


def _task_view(t):
    """对外任务视图：运行中隐藏 result，避免大结果反复传输"""
    v = {"id": t["id"], "tool": t["tool"], "label": t.get("label", ""),
         "status": t["status"], "error": t.get("error"),
         "submitted_at": t.get("submitted_at"),
         "finished_at": t.get("finished_at"),
         "params": t.get("params") or {}}
    if t["status"] in ("done", "failed"):
        v["result"] = (t.get("result") or "")[:4000]
    return v


# ---------- 工作流状态快照（供 Java Swing GUI 轮询，替代 M1 模拟数据） ----------
# 节点 id 与 Java WorkflowGraph 保持一致：knowledge/input/molopt/dft/diag/decision/multiwfn/report
_WF_NODE_TOOLS = {
    "input":   set(),
    "knowledge": set(),
    "molopt":  {"mdp_optimize"},
    "dft":     {"dft_single_point", "dft_optimize", "dft_frequency",
                "transition_state_search", "submit_dft_job",
                "qe_calculation", "pyscf_calculation"},
    "diag":    {"diagnose_job"},
    "multiwfn": {"multiwfn_bond_order", "multiwfn_charge", "multiwfn_rdg",
                 "multiwfn_iri", "analyze_fmo"},
    "report":  set(),
}
_ENERGY_RE = re.compile(
    r"能量\s*[:：=]?\s*([-+]?\d+\.\d+)|"
    r"E\([^)]*\)\s*=\s*([-+]?\d+\.\d+)|"
    r"Total Energy\s*=\s*([-+]?\d+\.\d+)|"
    r"SCF Done[^\n]*=\s*([-+]?\d+\.\d+)", re.IGNORECASE)


# 任务已 done 但结果文本含科学不达标信号 → 不应视为干净完成（DFT/TS 专用）。
# 注意：仅在确认性失败信号上降级，刻意不含宽泛的 `warning:` 匹配，
# 避免 normal termination 的 Gaussian 日志误判 RERUN。
_UNHEALTHY_RE = re.compile(
    r"收敛[:：]\s*(否|False)|未收敛|不收敛|"
    r"Error termination|非正常终止|非正常退出|abnormal|"
    r"极小点而非法过渡态|极小点而非过渡态|未检出虚频|无虚频|"
    r"虚频未检出|TS优化未完全收敛", re.IGNORECASE)


def _apply_dft_health(node, raw=""):
    """DFT 完成但结果含科学不达标信号 → 降级为 RERUN，供前端着色不再变绿。
    扫描完整结果文本（不只尾部6行），避免遗漏中段信号。"""
    if node.get("status") != "COMPLETED":
        return node
    blob = raw or "\n".join(node.get("log_tail") or [])
    if _UNHEALTHY_RE.search(blob):
        node["status"] = "RERUN"
        node["message"] = "DFT 结果未达标 → 需重跑"
    return node


def _extract_energy(text):
    if not text:
        return ""
    m = _ENERGY_RE.search(str(text))
    if not m:
        return ""
    for grp in m.groups():
        if grp:
            try:
                return format(float(grp), ".6f")
            except ValueError:
                return grp
    return ""


def _last_task_for(toolset):
    """工具集合中最近提交且已确定状态的任务；无则返回 None"""
    best = None
    with _TASKS_LOCK:
        for t in sorted(_TASKS.values(),
                        key=lambda x: x.get("submitted_at", 0), reverse=True):
            if t.get("tool") in toolset:
                best = t
                break
    return best


# 编排入口（对话 / Agent 调度）最近一次真实调用记录，供 /api/workflow/status 反映给画布。
_LAST_CHAT = {}
_LAST_AGENT = {}


def _mark_chat(state, text=""):
    _LAST_CHAT.update({"at": time.time(), "state": state, "text": str(text)})


def _mark_agent(state, tools=None, answer=""):
    _LAST_AGENT.update({"at": time.time(), "state": state,
                        "tools": [str(t) for t in (tools or [])],
                        "answer": str(answer)})


def _workflow_status():
    """聚合每节点真实状态（status/message/energy/log_tail）。status 取值与
    Java NodeStatus 一致：WAITING/RUNNING/COMPLETED/FAILED/RERUN。"""
    nodes = {}

    # 编排入口：chat / agent 由最近一次真实调用驱动
    if _LAST_CHAT:
        c_st = _LAST_CHAT.get("state")
        c_txt = ("：“" + str(_LAST_CHAT.get("text"))[:40] + "”"
                 if _LAST_CHAT.get("text") else "")
        nodes["chat"] = {
            "status": ("RUNNING" if c_st == "running"
                       else ("FAILED" if c_st == "failed" else "COMPLETED")),
            "message": ("对话进行中…" if c_st == "running"
                        else ("最后一次对话失败" if c_st == "failed"
                              else "已接收指令")) + c_txt,
            "energy": "",
            "log_tail": ([time.strftime("%H:%M:%S", time.localtime(_LAST_CHAT["at"]))]
                         if _LAST_CHAT.get("at") else []),
        }
    else:
        nodes["chat"] = {"status": "WAITING", "message": "等待对话指令",
                         "energy": "", "log_tail": []}
    if _LAST_AGENT:
        a_st = _LAST_AGENT.get("state")
        a_tools = _LAST_AGENT.get("tools") or []
        a_msg = ("LLM Agent 调度中…" if a_st == "running"
                 else ("LLM Agent 调度失败" if a_st == "failed"
                       else ("已调度工具: " + " / ".join(a_tools)
                             if a_tools else "Agent 已就绪（无工具调用）")))
        nodes["agent"] = {
            "status": ("RUNNING" if a_st == "running"
                       else ("FAILED" if a_st == "failed" else "COMPLETED")),
            "message": a_msg, "energy": "",
            "log_tail": ([time.strftime("%H:%M:%S", time.localtime(_LAST_AGENT["at"]))]
                         if _LAST_AGENT.get("at") else []),
        }
    else:
        nodes["agent"] = {"status": "WAITING", "message": "等待 Agent 调度",
                          "energy": "", "log_tail": []}

    # 前置静态节点：由模块可用性给出真实结论
    nodes["knowledge"] = {
        "status": "COMPLETED" if RAG_AVAILABLE else "WAITING",
        "message": ("RAG 语义检索就绪" if RAG_AVAILABLE else "RAG 模块不可用"),
        "energy": "", "log_tail": [],
    }
    nodes["input"] = {
        "status": "COMPLETED" if MOL_AVAILABLE else "WAITING",
        "message": ("RDKit 结构校验就绪" if MOL_AVAILABLE else "RDKit 不可用"),
        "energy": "", "log_tail": [],
    }
    nodes["molopt"] = _node_from_tasks("molopt", fallback_message="MDP 分子优化待执行")

    # DFT 节点：来自最近一次真实 DFT 任务
    dft_t = _last_task_for(_WF_NODE_TOOLS["dft"])
    nodes["dft"] = _task_to_node(dft_t, "DFT 计算待提交",
                                 tool_label="DFT 作业")
    _apply_dft_health(nodes["dft"], raw=dft_t.get("result") or ""
                      if dft_t and dft_t.get("status") == "done" else "")
    dft_status = nodes["dft"]["status"]

    # 分诊节点：来自最近一次 diagnose_job
    diag_t = _last_task_for(_WF_NODE_TOOLS["diag"])
    nodes["diag"] = _task_to_node(diag_t, "日志分诊待执行", tool_label="Laya 分诊")
    if not diag_t and dft_status == "COMPLETED":
        # 无住院诊记录但 DFT 已完成：按可复现主机输入提示就绪
        if DIAGNOSE_AVAILABLE:
            nodes["diag"] = {"status": "COMPLETED",
                             "message": "Laya 分诊就绪（可复现端）",
                             "energy": "", "log_tail": []}

    # 判定分支：由上游推导
    if dft_status == "FAILED":
        nodes["decision"] = {"status": "RERUN",
                             "message": "DFT 失败 → 回退重跑", "energy": "",
                             "log_tail": []}
    elif dft_status == "RERUN":
        nodes["decision"] = {"status": "RERUN",
                             "message": "DFT 未达标 → 修正后重跑", "energy": "",
                             "log_tail": []}
    elif dft_status == "RUNNING":
        nodes["decision"] = {"status": "WAITING", "message": "等待 DFT 结果",
                             "energy": "", "log_tail": []}
    elif dft_status == "COMPLETED":
        nodes["decision"] = {"status": "COMPLETED",
                             "message": "OK/可信 → 谱分析", "energy": "",
                             "log_tail": []}
    else:
        nodes["decision"] = {"status": "WAITING", "message": "待上游就绪",
                             "energy": "", "log_tail": []}

    # 谱分析 / 报告：来自真实分析任务
    nodes["multiwfn"] = _node_from_tasks("multiwfn", fallback_message="待执行")
    nodes["report"] = _node_from_tasks("report", fallback_message="待调用")

    return {"nodes": nodes, "total": len(nodes),
            "as_of": time.time(), "poll_seconds": TASK_POLL_SECONDS,
            "live": True}


def _node_from_tasks(node, fallback_message):
    t = _last_task_for(_WF_NODE_TOOLS[node])
    if not t:
        return {"status": "WAITING", "message": fallback_message,
                "energy": "", "log_tail": []}
    return _task_to_node(t, fallback_message, tool_label=node)


def _task_to_node(t, fallback_message, tool_label=""):
    """把一条真实任务折叠成节点视图。detail 提供程序名/输入文件格式/Multiwfn 分析功能。"""
    if not t:
        return {"status": "WAITING", "message": fallback_message,
                "energy": "", "log_tail": [], "detail": {"program": "", "input_format": "", "analysis": ""}}
    st = t.get("status")
    if st == "running":
        st_enum, tail = "RUNNING", ["任务进行中…"]
    elif st == "failed":
        st_enum = "FAILED"
        err = t.get("error")
        tail = [err] if isinstance(err, str) and err else ["执行失败"]
    elif st == "interrupted":
        st_enum = "RERUN"
        tail = [t.get("error") or "任务被中断"]
    else:  # done
        st_enum = "COMPLETED"
        raw = t.get("result") or ""
        tail = _tail_lines(str(raw), 6)
    msg = t.get("label") or tool_label + ((" · " + str(t.get("tool")))
                                          if t.get("tool") else "")
    return {"status": st_enum, "message": msg,
            "energy": _extract_energy(t.get("result") or "") if st == "done" else "",
            "log_tail": tail,
            "detail": _task_program_detail(t.get("tool"), t.get("params") or {})}


def _task_program_detail(tool, params):
    """返回 {program, input_format, analysis}，供节点详情展示用哪个程序/什么输入格式/哪个分析。"""
    empty = {"program": "", "input_format": "", "analysis": ""}
    params = params or {}
    if tool in ("qe_calculation", "pyscf_calculation"):
        code = (params.get("code") or "").strip()
        text = _input_sample(code, "qe" if tool == "qe_calculation" else "pyscf")
        return {"program": _DFT_PROGRAM["qe" if tool == "qe_calculation" else "pyscf"][0],
                "input_format": text, "analysis": ""}
    if tool in _WF_NODE_TOOLS["dft"]:
        backend = _resolve_backend(params)
        name, _ext, sample = _DFT_PROGRAM.get(backend,
                                              ("DFT 作业（" + (backend or "") + "）", "", "DFT 输入（格式见后端）"))
        return {"program": name, "input_format": sample, "analysis": ""}
    if tool in _MULTIWFN_ANALYSIS:
        a = _MULTIWFN_ANALYSIS[tool]
        if tool == "multiwfn_charge" and params.get("scheme"):
            a = f"Multiwfn · 原子电荷（{params['scheme']}）"
        return {"program": "Multiwfn（Multiwfn 程序）", "input_format": "",
                "analysis": a}
    return empty


def _resolve_backend(params):
    b = (params or {}).get("backend")
    if b:
        return b
    if DFT_MANAGER and getattr(DFT_MANAGER, "default_backend", None):
        return DFT_MANAGER.default_backend
    return "local_g16"


def _input_sample(code, kind):
    """QE/PySCF 展示实际提交代码前 12 行；Gaussian/ORCA 展示格式模板。"""
    code = (code or "").strip()
    if code:
        lines = [ln for ln in code.splitlines() if ln.strip()]
        head = lines[:12]
        body = "\n".join(head) + ("\n…" if len(lines) > 12 else "")
        return "（实际提交内容）\n" + body
    note = "（输入文件格式示例，运行时会写入真实坐标块）\n"
    if kind == "pyscf":
        return note + "from pyscf import gto, scf\nmol = gto.M(atom='''<坐标块>''', basis='def2-svp')\nmf = scf.RKS(mol, xc='b3lyp').run()"
    return note + "&CONTROL\n  calculation='scf'\n  prefix='job'\n  pseudo_dir='./'\n/\n" \
        "&SYSTEM\n  ibrav=0\n  nat=<nat>  ntyp=<ntyp>\n/\n" \
        "&ELECTRONS\n  mixing_beta=0.7\n/\nATOMIC_POSITIONS (angstrom)\n<坐标块>\nK_POINTS {gamma}"


# 计算程序显示名 + 输入文件格式示例（Gaussian/ORCA/集群用）
_DFT_PROGRAM = {
    "local_g16": ("本地 Gaussian16", ".gjf",
        "%chk=job.chk\n#P <method>/<basis>  Opt Freq\n\n<作业标题>\n\n0 1\n<坐标块>\n\n"),
    "local_orca": ("本地 ORCA", ".inp",
        "! <method> <basis> Opt Freq\n* xyz 0 1\n<坐标块>\n*\n\n<轨道/电荷设置>"),
    "cluster_g16": ("集群 Gaussian16（SSH 排队）", ".com",
        "%chk=job.chk\n#P <method>/<basis>  Opt=(TS,CalcFC,NoEigenTest)\n\n<作业标题>\n\n0 1\n<坐标块>\n\n"),
    "qe": ("WSL 中的 Quantum ESPRESSO", ".in", "QE 输入（.in）"),
    "pyscf": ("容器中的 PySCF", "内联 Python", "PySCF 内联代码"),
}
_MULTIWFN_ANALYSIS = {
    "multiwfn_bond_order": "Multiwfn · Mayer 键级",
    "multiwfn_charge": "Multiwfn · 原子电荷",
    "multiwfn_rdg": "Multiwfn · RDG/NCI 弱相互作用",
    "multiwfn_iri": "Multiwfn · IRI 弱相互作用",
    "analyze_fmo": "Multiwfn · HOMO/LUMO 前线轨道",
}


def _tail_lines(text, n):
    lines = [ln for ln in text.splitlines() if ln.strip()]
    return lines[-n:]


def _workflow_jobs():
    """任务进程快照：跨 WSL/容器/本地/集群的已提交任务（供 Java 监控标签页轮询）。"""
    jobs = []
    with _TASKS_LOCK:
        items = sorted(_TASKS.values(),
                       key=lambda x: x.get("submitted_at", 0), reverse=True)
        for t in items:
            tool = t.get("tool", "")
            detail = _task_program_detail(tool, t.get("params") or {})
            jobs.append({
                "id": t.get("id"), "label": t.get("label") or detail["program"] or tool,
                "tool": tool, "program": detail["program"],
                "status": t.get("status"), "error": t.get("error"),
                "submitted_at": t.get("submitted_at"),
                "finished_at": t.get("finished_at"),
                "params": {k: v for k, v in (t.get("params") or {}).items()
                           if k in ("backend", "scheme")},
            })
    return {"jobs": jobs, "total": len(jobs),
            "as_of": time.time(), "poll_seconds": TASK_POLL_SECONDS}


# 按 task_id 缓存结构，避免每次轮询重复用 RDKit 生成坐标
_STRUCT_CACHE = {}


def _structure_for_task(task_id):
    """返回某任务的提交时分子结构（球棍渲染用）：symbols/coords/bonds。"""
    with _TASKS_LOCK:
        t = _TASKS.get(task_id)
    if not t:
        return {"ok": False, "error": "任务不存在"}
    params = t.get("params") or {}
    smiles = params.get("smiles")
    key = task_id or str(t.get("tool"))
    if key in _STRUCT_CACHE:
        return _STRUCT_CACHE[key]
    if not smiles or not MOL_AVAILABLE:
        out = {"ok": False,
               "error": "该任务没有可解析的 SMILES 结构（如 QE/PySCF 内联代码），无法渲染结构"}
        _STRUCT_CACHE[key] = out
        return out
    try:
        mol_graph = smiles_to_molecule_graph(smiles, add_hs=True)
    except Exception as e:
        out = {"ok": False, "error": f"结构生成失败: {type(e).__name__}: {e}"}
        _STRUCT_CACHE[key] = out
        return out
    atoms = mol_graph.atoms
    out = {
        "ok": True, "smiles": smiles, "natom": len(atoms),
        "symbols": [a.symbol for a in atoms],
        "coords": [[float(a.coords[0]), float(a.coords[1]), float(a.coords[2])]
                   for a in atoms],
        "bonds": [[int(i), int(j)] for (i, j, _b) in mol_graph.bond_list],
    }
    _STRUCT_CACHE[key] = out
    return out


if not hasattr(globals(), "RAG_AVAILABLE"):
    try:
        import rag_retriever  # noqa: F401
        RAG_AVAILABLE = True
    except Exception:
        RAG_AVAILABLE = False
if not hasattr(globals(), "DIAGNOSE_AVAILABLE"):
    try:
        import job_diagnosis  # noqa: F401
        DIAGNOSE_AVAILABLE = True
    except Exception:
        DIAGNOSE_AVAILABLE = False


_load_tasks()


# ---------- 工具注册表（Agent 可用工具） ----------
TOOLS = {
    "dft_single_point": {"description": ("计算分子的DFT单点能（同步阻塞，适合快速计算）。"
                                         "参数：smiles（SMILES字符串，或先经query_compound查询得到）、"
                                         "backend（可选：local_g16本地Gaussian16 / local_orca本地ORCA / "
                                         "cluster_g16 SSH集群Gaussian16，省略则用设置页配置的默认后端）")},
    "dft_optimize": {"description": ("对分子进行DFT几何优化（同步阻塞）。参数：smiles、"
                                    "backend（可选，同dft_single_point）")},
    "dft_frequency": {"description": ("计算分子的DFT频率振动分析（同步阻塞）。参数：smiles、"
                                     "backend（可选，同dft_single_point）")},
    "submit_dft_job": {"description": ("异步提交DFT计算任务并立即返回task_id，适合长时间计算"
                                       "（特别是SSH集群cluster_g16后端，可能耗时数分钟到数小时）。"
                                       "提交后对话会中断，系统会自动监控任务，完成后自动恢复对话并告知结果。"
                                       "参数：smiles、job_type（sp单点能/opt几何优化/freq频率/ts过渡态搜索，默认sp）、"
                                       "method（可选，默认B3LYP）、basis（可选，默认def2SVP，覆盖Cu/Se/Te等重元素）、"
                                       "backend（可选，同dft_single_point）。返回值含 task_id 字段")},
    "mdp_optimize": {"description": ("使用MDP强化学习优化分子结构(C++加速核心molopt.exe,不可用时自动回退Python)。"
                                     "参数：smiles、episodes、max_steps、"
                                     "ref_bonds（实验参考键长修正，如{\"Al-Se\":2.1935}，单位Å，优先于内置力场参数）、"
                                     "ref_xyz（实验结构XYZ路径，如CIF解析产物，自动提取Al/Se/Te/Cu等关键键长作为参考）")},
    "transition_state_search": {"description": ("使用固定-放开法(freeze-release)搜索过渡态（面向铝宾+CO₂等加成体系）："
                                                "ModRedundant松弛扫描反应坐标→取最高点→Opt=TS+Freq全自由度优化→虚频验证。"
                                                "参数：smiles、method（默认B3LYP）、basis（默认def2SVP，覆盖Cu/Se/Te等重元素）、"
                                                "mode（eta2=[2+2]型，扫描Al···C；eta1=η¹-O酸碱加成，扫描Al···O——反应网络两条边），"
                                                "freeze_migration（默认true：自动冻结金属-甲基键防止基团迁移，见铝宾19/20教训）、"
                                                "backend（需SSH集群cluster_g16）。结果含虚频-反应坐标一致性校验与Wigner隧道因子。"
                                                "多步集群计算耗时长，建议通过submit_dft_job以job_type=ts异步提交")},
    "visualize_molecule": {"description": "显示分子的3D球棍模型（原生molview.exe查看器）"},
    "visualize_result": {"description": ("渲染计算结果文件的3D结构（含FMO/电子云等值面）。"
                                         "参数：file（计算结果文件路径，Gaussian .log/.out 或 ORCA 输出）。"
                                         "从结果提取几何并打开molview.exe查看器；同目录存在同名.fchk时"
                                         "自动生成HOMO/LUMO轨道与电子密度等值面（查看器内 B键=切换FMO，D键=切换电子云）")},
    "run_command": {"description": "执行系统命令"},
    "list_files": {"description": "列出目录内容"},
    "read_file": {"description": "读取文本文件内容"},
    "write_file": {"description": "写入文本文件"},
    "run_python": {"description": ("在隔离的 Python 子进程里执行并运行一段 Python 源码（脚本能力：写→跑→读输出→改了再跑，迭代式脚本开发）。"
                                   "参数：code（Python 源码字符串，必填其一）、file（已有 .py 脚本路径，优先于 code）、"
                                   "timeout（超时秒，默认60，最大300）、cwd（工作目录，默认系统工作目录）。"
                                   "会自动把 code 保存到 workspace/scripts/agent_*.py 以便追踪与复用，并返回 returncode/stdout/stderr。"
                                   "适合批量解析文本、生成坐标/CIF、自写数据处理算法等需运行自定义代码的场景。")},
    "open_webpage": {"description": "在浏览器中打开网址"},
    "search_skills": {"description": "在知识库中搜索技能"},
    "get_skill": {"description": "获取指定技能的完整内容"},
    "plan_reaction": {"description": "规划从反应物到产物的合成路径"},
    "analyze_properties": {"description": "预测分子的基本性质（分子量、LogP、氢键供体/受体数）。参数：smiles（SMILES字符串，或先经query_compound查询得到）"},
    "interpret_result": {"description": ("解读分子结构或过渡态结果，把原始计算数据翻译成可直接引用的化学结论"
                                         "（帮助模型/用户理解它到底意味着什么）。两种模式：\n"
                                         "· kind=structure：解读分子结构 → 传smiles。返回铝中心物种/价态"
                                         "（铝宾/双铝烯/铝烯氧/亚胺铝宾/金属-铝复合物等,A1-A4分类）、成键模式、"
                                         "配位环境与路易斯酸碱反应位点。\n"
                                         "· kind=ts：解读过渡态结果 → 传report（把transition_state_search或"
                                         "submit_dft_job(job_type=ts)返回的结果文本整体粘贴）。返回过渡态可信度"
                                         "（唯一虚频+反应坐标一致性判定）、活化能判读（势垒高低→能否自发/需加热）、"
                                         "量子隧穿(Wigner κ)含义。")},
    "multiwfn_bond_order": {"description": ("使用Multiwfn分析Mayer键级（使用设置页配置的Multiwfn程序）。"
                                           "参数：fchk_file（Gaussian计算生成的.fchk波函数文件路径）")},
    "multiwfn_charge": {"description": ("使用Multiwfn分析原子电荷，支持 mulliken（默认）/lowdin/hirshfeld 三种方案。"
                                        "参数：fchk_file（.fchk波函数文件路径），scheme（可选：mulliken/lowdin/hirshfeld）")},
    "multiwfn_rdg": {"description": ("使用Multiwfn进行RDG/NCI弱相互作用分析，导出sign(lambda2)rho与RDG等值面cube文件"
                                     "（可用VMD/GaussView可视化非共价相互作用）。"
                                     "参数：fchk_file（.fchk波函数文件路径）")},
    "multiwfn_iri": {"description": ("使用Multiwfn进行IRI（相互作用区域指示符）分析，导出sign(lambda2)rho与IRI等值面cube文件"
                                     "（可同时展现共价与非共价相互作用区域）。"
                                     "参数：fchk_file（.fchk波函数文件路径）")},
    "query_pubchem": {"description": "通过PubChem查询化合物的物理化学性质（使用ConnectivitySMILES）"},
    "query_compound": {"description": ("查询本地铝宾化合物数据库（含铝宾系列1-52、阿司匹林、苯等常见化合物），"
                                       "将化合物名称转换为SMILES。当用户提到化合物中文名称（如'Se-铝宾 (19)'、'咔唑基铝烯前体'、"
                                       "'阿司匹林'）而非直接给出SMILES时，必须先调用此工具获取SMILES，"
                                       "再将SMILES用于其他计算工具。参数：name（化合物名称，字符串；"
                                       "省略name或传空字符串则返回数据库中全部化合物名称列表）")},
    "query_basis_set": {"description": ("查询/选择DFT计算基组：从Basis Set Exchange(BSE)校验某基组是否覆盖分子中全部元素，"
                                        "并获取项目推荐的关系库建议（任务类型×泛函×基组×元素类别×分子大小）。"
                                        "当用户询问'该用什么基组'、'def2-SVP覆盖Se吗'、或分子含Cu/Se/Te等重元素需确认基组时使用。"
                                        "参数：smiles（SMILES字符串，必填）、task_kind（可选：sp单点/opt优化/freq频率/ts过渡态/"
                                        "props性质，默认sp）、basis（可选：要查询的基组名如 def2-SVP/6-31G(d)/cc-pVTZ，省略时返回推荐结果；"
                                        "提供时返回该校验是否覆盖所有元素及是否需升级）")},
    "query_functional": {"description": ("分析/推荐DFT泛函选择：按 (任务类型×精度×体系特征) 返回项目关系库推荐，"
                                         "或校验用户指定泛函的体系适配（色散/长程修正/双杂化/重元素/激发态）。"
                                         "当用户询问'过渡态该用什么泛函'、'高精度单点用什么泛函'、'B3LYP适合这个体系吗'时使用。"
                                         "参数：smiles（SMILES字符串，必填）、task_kind（可选：sp单点/opt优化/freq频率/ts过渡态/"
                                         "props性质，默认sp）、precision（可选：fast快速/standard标准/high高精度，"
                                         "默认按任务推得）、method（可选：要校验的泛函名如 B3LYP/CAM-B3LYP/ωB97X-D/M06-2X，"
                                         "省略时返回推荐结果与理由）")},
    "task_advisor": {"description": ("计算任务理解与推荐：把研究需求（如'这个反应的过渡态怎么算'、"
                                     "'判断复合物稳定性'）翻译成『该跑哪种计算任务、为什么、用哪个工具』，"
                                     "并给出对应分子的体系注意点（基组/泛函/后续分析）。"
                                     "当用户表述含糊、不确定该用哪个计算任务时优先调用。"
                                     "参数：task（科研需求/想算的内容，必填）、smiles（可选，对应分子，给出定制建议）")},
    "analyze_fmo": {"description": ("前线轨道(HOMO/LUMO)分析：给出 HOMO/LUMO 能级、能隙并做化学解读"
                                    "（分子活性、电子光谱倾向）。两种输入二选一：\n"
                                    "· file：DFT 结果文件路径（Gaussian .log/.out 或 ORCA 输出），直接解析；\n"
                                    "· smiles：现场自动跑一次 dft_single_point 生成结果再解析。\n"
                                    "当用户问'看看 HOMO/LUMO'、'算一下能隙'、'判断给电子能力'时使用。"
                                    "参数：file（可选）、smiles（可选，二者至少其一）")},
    "draw_structure": {"description": ("绘制分子 2D 结构式并保存为 PNG 图片（元素着色、单/双/三键区分），"
                                       "返回图片路径。当用户想看分子的二维结构、确认键连方式时使用。"
                                       "参数：smiles（SMILES，必填）、outfile（可选输出路径）、title（可选标题）")},
    "visualize_spectrum": {"description": ("从 DFT 结果文件绘制并解读 IR 或 UV-Vis 谱图（PNG），"
                                           "返回图片路径与峰位摘要。当用户想看红外/紫外吸收谱、"
                                           "或提出'画一下谱图'时使用。参数：file（Gaussian/ORCA 输出文件，必填）、"
                                           "type（可选：ir红外 或 uv紫外可见；省略时按文件内容自动判断）")},
    "rag_search": {"description": ("语义检索项目知识库（RAG）：按语义相似度查找 Laya 高斯作业分诊案例、"
                                   "调校报告、自旋基态性质等历史经验。当用户问'以前有没有遇到过XX'、"
                                   "'类似错误怎么处理的'、'这个虚频/未收敛/自旋问题历史怎么判'时使用。"
                                   "参数：query（检索问题/关键词，必填）、top_k（可选，返回条数默认5）")},
    "diagnose_job": {"description": ("Gaussian 作业分诊：判定一个 DFT/TS/NBO 日志（.log/.out）可否直接采用，"
                                     "还是需重跑。用 Laya 决策头 + 领域规则（自旋基态校验/虚频/MaxCycles）+ "
                                     "RAG 历史案例，Laya 不可用时自动降级本地 qwen3.5:2b，再降级确定性规则。"
                                     "返回 KEEP/RERUN、判因、可用性分数与相似历史案例。"
                                     "当用户问'这个作业成功了吗'、'这步要不要重跑'、'二重态是不是假成功'、"
                                     "或需要在自动化链中先验证 DFT 结果可信度时使用。"
                                     "参数：log（.log/.out 文件路径，必填，或传 fields_json 特征字典），"
                                     "target_spin（可选，默认1=闭壳层单重态，换体系要改），"
                                     "use（可选：auto/laya/qwen/rules，默认auto）")},
    "qe_calculation": {"description": ("调用WSL中的Quantum Espresso进行周期体系电子结构计算"
                                       "（pw.x SCF/几何优化/nscf/能带，或 cp.x/bands.x/pp.x 等）。"
                                       "输入为QE标准 .in 文本（含 &CONTROL/&SYSTEM/&ELECTRONS 等卡，"
                                       "ATOMIC_POSITIONS/CELL/K_POINTS 等块）。两种方式二选一："
                                       "input（原始输入文本，最常用）或 file（Windows路径下已写好的.in文件）。"
                                       "QE在输入目录内运行（波函数/电荷密度按prefix写回该目录），"
                                       "返回输出尾部，pw.x自动给出总能量(Ry/Hartree)。"
                                       "参数：input（QE输入文本）、file（.in路径）、command（默认pw.x，可选cp.x/bands.x/pp.x）、"
                                       "extra_args（附加参数，可覆盖默认的-in，如pp.x用-pp）、timeout（秒，默认300）")},
    "pyscf_calculation": {"description": ("在Docker容器(molssi/pyscf221-mamba141, PySCF 2.2.1)中执行PySCF代码完成量子化学计算"
                                          "（分子HF/DFT单点、几何优化、振动、激发态、Mulliken等，也支持pbc周期体系）。"
                                          "代码在容器内以 /opt/conda/bin/python -u 执行；容器不共享本地文件系统，"
                                          "分子结构须在 code 内以内联字符串自行提供（坐标/SMILES/字符串）。"
                                          "参数：code（PySCF Python源码，必填）或 file（本地.py路径，内容喂给容器），timeout（秒，默认300）")},
}

SMILES_TOOLS = {"dft_single_point", "dft_optimize", "dft_frequency",
                "mdp_optimize", "visualize_molecule"}
DFT_JOB_TOOLS = {"dft_single_point": "sp", "dft_optimize": "opt", "dft_frequency": "freq"}
_MULTIWFN_TOOLS = {"multiwfn_bond_order", "multiwfn_charge", "multiwfn_rdg", "multiwfn_iri"}

# 各工具的关键参数提示（紧凑注入系统提示，提升本地小模型工具调用可靠性）
_TOOL_HINTS = {
    "dft_single_point": "参数: smiles(必填), backend(可选)",
    "dft_optimize": "参数: smiles(必填), backend(可选)",
    "dft_frequency": "参数: smiles(必填), backend(可选)",
    "submit_dft_job": "参数: smiles(必填), job_type=sp/opt/freq/ts, method, basis, backend",
    "transition_state_search": "参数: smiles(必填), method, basis, mode=eta2/eta1, freeze_migration, backend",
    "analyze_properties": "参数: smiles(必填)",
    "query_basis_set": "参数: smiles(必填), task_kind=sp/opt/freq/ts/props, basis(可选)",
    "query_functional": "参数: smiles(必填), task_kind, precision=fast/standard/high, method(可选)",
    "multiwfn_bond_order": "参数: fchk_file(必填)",
    "multiwfn_charge": "参数: fchk_file(必填), scheme=mulliken/lowdin/hirshfeld",
    "multiwfn_rdg": "参数: fchk_file(必填)",
    "multiwfn_iri": "参数: fchk_file(必填)",
    "query_compound": "参数: name(化合物中文名或番号, 可省略返回全部)",
    "plan_reaction": "参数: reactant(必填), product(必填)",
    "interpret_result": "参数: kind=structure(传smiles)/ts(传report), smiles, report",
    "run_python": "参数: code(源码) 或 file(脚本路径), timeout, cwd",
    "run_command": "参数: command(必填), timeout(可选)",
    "read_file": "参数: path(必填)",
    "write_file": "参数: path(必填), content(必填)",
    "list_files": "参数: path(可选)",
    "open_webpage": "参数: url(必填)",
    "visualize_result": "参数: file(计算结果文件路径, 必填)",
    "visualize_molecule": "参数: smiles(必填), show_hydrogen(可选)",
    "search_skills": "参数: keyword(搜索关键词)",
    "get_skill": "参数: name(技能名, 必填)",
    "task_advisor": "参数: task(科研需求/想算的内容, 必填), smiles(可选)",
    "analyze_fmo": "参数: file(DFT结果文件) 或 smiles(现场单点)，二者至少其一",
    "draw_structure": "参数: smiles(必填), outfile(可选), title(可选)",
    "visualize_spectrum": "参数(三选一): file=Gaussian/ORCA输出文本; file=IR谱图图片(自动识峰); peaks=\"3434,1734\"外部峰列表(最可靠); type=ir/uv/outfile/title可选",
    "rag_search": "参数: query(检索问题,必填), top_k(可选)",
    "diagnose_job": "参数: log(日志路径,必填) 或 fields_json(特征字典), target_spin, use=auto/laya/qwen/rules",
    "qe_calculation": "参数: input(QE输入文本) 或 file(.in路径), command=pw.x, extra_args, timeout",
    "pyscf_calculation": "参数: code(PySCF源码,必填) 或 file(.py路径), timeout",
}


def _valid_backend(backend):
    if not backend:
        return None
    backends = list(DFT_MANAGER.backends.keys()) if DFT_MANAGER else []
    return backend if backend in backends else None


def execute_tool(tool_name, params):
    """执行工具，返回字符串结果。逻辑与主程序 _execute_tool 一致。"""
    if tool_name not in TOOLS:
        return f"错误：未找到工具 '{tool_name}'，可用工具：{', '.join(TOOLS.keys())}"
    try:
        # 参数归一化：小模型常用 {"name":"<参数名>","value":"<值>"} 而非 {参数名:<值>}，
        # 解码回原生键；query_compound 等"name 本身就是参数"的工具不受影响（无 value 键）。
        if isinstance(params, dict) and "value" in params and "name" in params:
            _pname = params["name"]
            if isinstance(_pname, str) and _pname != "name" and re.match(r'^[a-zA-Z_][a-zA-Z0-9_]*$', _pname):
                _decoded = {_pname: params["value"]}
                for _k, _v in params.items():
                    if _k not in ("name", "value"):
                        _decoded[_k] = _v
                params = _decoded
        if tool_name in SMILES_TOOLS:
            smiles = (params.get("smiles") or params.get("identifier")
                      or params.get("molecule") or params.get("name")
                      or params.get("value") or params.get("compound"))
            if not smiles:
                return f"错误：{tool_name} 需要 smiles 参数"
            smiles = _resolve_smiles(smiles)
            if tool_name == "mdp_optimize":
                ref_bonds = params.get("ref_bonds")
                if isinstance(ref_bonds, dict):
                    ref_bonds = {tuple(str(k).split("-")) if isinstance(k, str) else k: v
                                 for k, v in ref_bonds.items()}
                return _t_mdp_optimize(smiles, int(params.get("episodes", 30)),
                                       int(params.get("max_steps", 60)),
                                       ref_bonds=ref_bonds,
                                       ref_xyz=params.get("ref_xyz"))
            if tool_name == "visualize_molecule":
                return _t_visualize_molecule(smiles, params.get("show_hydrogen", True))
            if tool_name in DFT_JOB_TOOLS:
                backend = params.get("backend")
                if backend and not _valid_backend(backend):
                    available = ", ".join(DFT_MANAGER.backends.keys()) if DFT_MANAGER else "无"
                    return (f"错误：未知后端 '{backend}'，当前可用后端：{available}。"
                            "可在设置页「DFT 计算后端配置」中添加。")
                return _dft_job(smiles, DFT_JOB_TOOLS[tool_name], backend=backend,
                                precision=params.get("precision"))
        if tool_name == "visualize_result":
            return _t_visualize_result(params.get("file") or params.get("result_file")
                                       or params.get("path") or "")
        if tool_name == "interpret_result":
            return _t_interpret_result(
                params.get("kind"),
                params.get("smiles") or params.get("identifier") or params.get("molecule") or "",
                params.get("report") or params.get("data") or "")
        if tool_name == "submit_dft_job":
            smiles = (params.get("smiles") or params.get("identifier")
                      or params.get("molecule") or params.get("name"))
            if not smiles:
                return "错误：submit_dft_job 需要 smiles 参数"
            smiles = _resolve_smiles(smiles)
            job_type = params.get("job_type", "sp")
            if job_type not in ("sp", "opt", "freq", "ts"):
                job_type = "sp"
            backend = params.get("backend")
            if backend and not _valid_backend(backend):
                available = ", ".join(DFT_MANAGER.backends.keys()) if DFT_MANAGER else "无"
                return f"错误：未知后端 '{backend}'，当前可用后端：{available}。"
            label = params.get("label") or f"{job_type} {smiles[:40]}"
            precision = params.get("precision")
            # 泛函/基组智能选择：任务类型（ts→def2 系 ωB97X-D）+ 分子成分 + BSE 校验。
            method, basis = auto_method_basis(
                smiles,
                (params.get("method") or "").strip() or None,
                (params.get("basis") or "").strip() or None,
                task_kind=job_type if job_type in ("sp", "opt", "freq", "ts") else "sp",
                precision=precision)
            if job_type == "ts":
                task_id = _submit_task("transition_state_search",
                                       {"smiles": smiles, "method": method,
                                        "basis": basis, "backend": backend,
                                        "precision": precision}, label)
                job_names_ts = {"ts": "过渡态搜索（固定-放开法）"}
                return (f"任务已提交（task_id: {task_id}）。\n"
                        f"· 计算类型：{job_names_ts[job_type]}（后端 {backend or getattr(DFT_MANAGER, 'default_backend', 'local_g16')}）\n"
                        f"· SMILES: {smiles}\n"
                        "· 流程：构建复合物 → Al···C松弛扫描 → TS优化+虚频验证（2个集群作业，耗时较长）\n"
                        "· 系统正在后台监控任务，完成后会自动恢复对话并告知结果。\n"
                        "请向用户说明任务正在后台运行，然后结束本轮对话即可。")
            task_id = _submit_task("dft_single_point" if job_type == "sp" else
                                  ("dft_optimize" if job_type == "opt" else "dft_frequency"),
                                  {"smiles": smiles, "backend": backend,
                                   "precision": precision}, label)
            job_names = {"sp": "单点能", "opt": "几何优化", "freq": "频率分析"}
            return (f"任务已提交（task_id: {task_id}）。\n"
                    f"· 计算类型：{job_names[job_type]}（后端 {backend or getattr(DFT_MANAGER, 'default_backend', 'local_g16')}）\n"
                    f"· SMILES: {smiles}\n"
                    "· 系统正在后台监控任务，完成后会自动恢复对话并告知结果。\n"
                    "请向用户说明任务正在后台运行，然后结束本轮对话即可。")
        if tool_name == "transition_state_search":
            smiles = params.get("smiles") or params.get("molecule")
            if not smiles:
                return "错误：transition_state_search 需要 smiles 参数"
            smiles = _resolve_smiles(smiles)
            if not DFT_MANAGER:
                return "DFT 模块不可用（dft_backend 未加载）。"
            # 泛函/基组智能选择：过渡态（ts）→ 关系库 ωB97X-D + def2 系 + BSE 校验
            method, basis = auto_method_basis(
                smiles,
                (params.get("method") or "").strip() or None,
                (params.get("basis") or "").strip() or None,
                task_kind="ts", precision=params.get("precision"))
            backend = params.get("backend")
            mode = params.get("mode") or "eta2"
            freeze_migration = params.get("freeze_migration", True)
            if mode not in ("eta1", "eta2"):
                mode = "eta2"
            if backend and not _valid_backend(backend):
                available = ", ".join(DFT_MANAGER.backends.keys()) if DFT_MANAGER else "无"
                return (f"错误：未知后端 '{backend}'，当前可用后端：{available}。"
                        "可在设置页「DFT 计算后端配置」中添加。")
            if not getattr(_TASK_THREAD, "is_task", False):
                # 智能体 HTTP 线程直接调用：转为异步任务，避免集群多步计算
                # （数小时级）阻塞 SSE 流；任务线程内则同步执行
                task_id = _submit_task("transition_state_search",
                                       {"smiles": smiles, "method": method,
                                        "basis": basis, "backend": backend,
                                        "mode": mode,
                                        "precision": params.get("precision"),
                                        "freeze_migration": freeze_migration},
                                       params.get("label") or f"ts {smiles[:40]}")
                return (f"任务已提交（task_id: {task_id}）。\n"
                        "· 过渡态搜索（固定-放开法）已在后台运行：构型构建（"
                        f"{'η¹-O 端进攻' if mode == 'eta1' else '[2+2] 型'}）→ "
                        "松弛扫描 → TS优化+虚频验证\n"
                        "· 系统正在后台监控任务，完成后会自动恢复对话并告知结果。\n"
                        "请向用户说明任务正在后台运行，然后结束本轮对话即可。")
            report = DFT_MANAGER.run_ts_search(smiles, method=method, basis=basis,
                                               backend=backend, mode=mode,
                                               freeze_migration=freeze_migration)
            return _format_ts_report(report)
        if tool_name == "run_command":
            command = params.get("command") or params.get("cmd")
            if not command or not AUTOMATION_AVAILABLE:
                return "错误：run_command 需要 command 参数" if not command else "自动化模块不可用"
            r = AUTOMATION.run_command(command)
            return (f"返回码: {r.get('returncode')}\nstdout:\n{r.get('stdout', '')}\n"
                    f"stderr:\n{r.get('stderr', '')}")
        if tool_name == "list_files":
            if not AUTOMATION_AVAILABLE:
                return "自动化模块不可用"
            files = AUTOMATION.list_directory(params.get("path") or params.get("directory"))
            return "\n".join(files) if isinstance(files, list) else str(files)
        if tool_name == "read_file":
            path = params.get("path") or params.get("file") or params.get("filepath")
            if not path or not AUTOMATION_AVAILABLE:
                return "错误：read_file 需要 path 参数"
            content = AUTOMATION.read_file(path)
            return f"读取文件失败: {AUTOMATION.get_last_result()}" if content is None \
                else f"文件内容 ({path}):\n{content[:2000]}"
        if tool_name == "write_file":
            path = params.get("path") or params.get("file") or params.get("filepath")
            content = params.get("content") or params.get("text")
            if not path or content is None:
                return "错误：write_file 需要 path 和 content 参数"
            ok = AUTOMATION.write_file(path, content) if AUTOMATION_AVAILABLE else False
            return f"文件已写入：{path}" if ok else "写入文件失败"
        if tool_name == "run_python":
            return _t_run_python(code=params.get("code") or params.get("script"),
                                 file=params.get("file") or params.get("path") or "",
                                 timeout=params.get("timeout"),
                                 cwd=params.get("cwd"))
        if tool_name == "open_webpage":
            url = params.get("url") or params.get("link")
            if not url:
                return "错误：open_webpage 需要 url 参数"
            ok = AUTOMATION.open_webpage(url) if AUTOMATION_AVAILABLE else False
            return f"已打开网页：{url}" if ok else "打开网页失败"
        if tool_name == "search_skills":
            return _t_search_skills(params.get("keyword") or params.get("query") or "")
        if tool_name == "get_skill":
            return _t_get_skill_content(params.get("name") or params.get("skill_name") or "")
        if tool_name == "task_advisor":
            return _t_task_advisor(params.get("task") or params.get("query") or "",
                                   params.get("smiles") or params.get("molecule")
                                   or params.get("name") or "")
        if tool_name == "analyze_fmo":
            return _t_analyze_fmo(
                params.get("smiles") or params.get("molecule") or params.get("name") or "",
                params.get("file") or params.get("fchk_file") or params.get("result_file") or "")
        if tool_name == "draw_structure":
            sm = params.get("smiles") or params.get("molecule") or params.get("name")
            if not sm:
                return "错误：draw_structure 需要 smiles 参数"
            return _t_draw_structure(sm, params.get("outfile") or params.get("path") or "",
                                     params.get("title") or "")
        if tool_name == "visualize_spectrum":
            return _t_visualize_spectrum(
                params.get("file") or params.get("result_file") or params.get("path") or "",
                params.get("type") or "", params.get("outfile") or params.get("path") or "",
                params.get("title") or "", params.get("peaks") or params.get("峰位") or "")
        if tool_name == "plan_reaction":
            return _t_plan_reaction(params.get("reactant") or params.get("反应物") or "",
                                    params.get("product") or params.get("产物") or "")
        if tool_name == "analyze_properties":
            smiles = (params.get("smiles") or params.get("molecule") or params.get("name")
                      or params.get("value") or params.get("compound") or "")
            if not smiles.strip():
                return "错误：analyze_properties 需要 smiles 参数"
            return _t_analyze_properties(_resolve_smiles(smiles))
        if tool_name in _MULTIWFN_TOOLS:
            fchk = params.get("fchk_file") or params.get("file") or ""
            if not fchk:
                return f"错误：{tool_name} 需要 fchk_file 参数（Gaussian计算生成的 .fchk 文件）"
            if not os.path.isfile(fchk):
                return f"错误：文件不存在: {fchk}"
            if not DFT_MANAGER or not getattr(DFT_MANAGER, "analyzer", None):
                return "Multiwfn 分析模块不可用（dft_backend 未加载）。"
            try:
                if tool_name == "multiwfn_charge":
                    scheme = (params.get("scheme") or "mulliken").lower().strip()
                    if scheme not in ("mulliken", "lowdin", "hirshfeld"):
                        return (f"错误：未知电荷方案 '{scheme}'"
                                "（支持 mulliken / lowdin / hirshfeld）")
                    return _format_mw_charge(
                        DFT_MANAGER.analyzer.analyze_charge(fchk, scheme), fchk, scheme)
                if tool_name == "multiwfn_rdg":
                    return _format_mw_cubes(
                        DFT_MANAGER.analyzer.analyze_rdg(fchk), fchk, "RDG/NCI")
                if tool_name == "multiwfn_iri":
                    return _format_mw_cubes(
                        DFT_MANAGER.analyzer.analyze_iri(fchk), fchk, "IRI")
                return str(DFT_MANAGER.analyzer.analyze_bond_order(fchk))
            except FileNotFoundError:
                cfg = _load_dft_config()
                mw = (cfg.get("multiwfn_command") or "multiwfn").strip()
                return (f"Multiwfn 程序未找到: '{mw}'。"
                        "请在「设置与状态 → DFT 计算后端配置」中填写 Multiwfn 完整路径并保存，然后重试。")
            except Exception as e:
                return f"分析失败: {e}"
        if tool_name == "query_compound":
            return _t_query_compound(params.get("name") or params.get("keyword")
                                     or params.get("query") or "")
        if tool_name == "query_pubchem":
            return _t_query_pubchem(params.get("identifier") or params.get("name")
                                    or params.get("compound") or "",
                                    params.get("id_type", "name"))
        if tool_name == "query_basis_set":
            return _t_query_basis_set(params.get("smiles") or "",
                                      params.get("task_kind") or "sp",
                                      params.get("basis") or None)
        if tool_name == "query_functional":
            return _t_query_functional(params.get("smiles") or "",
                                       params.get("task_kind") or "sp",
                                       params.get("precision"),
                                       params.get("method"))
        if tool_name == "rag_search":
            return _t_rag_search(params.get("query") or params.get("q") or "",
                                 int(params.get("top_k") or 5))
        if tool_name == "diagnose_job":
            return _t_diagnose_job(
                params.get("log") or params.get("log_file") or params.get("file") or "",
                params.get("fields_json") or params.get("fields") or "",
                int(params.get("target_spin") or 1),
                params.get("use") or "auto")
        if tool_name == "qe_calculation":
            return _t_qe_calculation(
                params.get("input") or params.get("input_text") or params.get("content") or "",
                params.get("file") or params.get("in_file") or "",
                params.get("command") or "pw.x",
                params.get("extra_args") or "",
                params.get("timeout"))
        if tool_name == "pyscf_calculation":
            return _t_pyscf_calculation(
                params.get("code") or params.get("script") or "",
                params.get("file") or params.get("script_file") or "",
                params.get("timeout"))
        return "未知工具"
    except Exception as e:
        return f"工具执行出错 ({tool_name}): {type(e).__name__}: {e}"


# ---------- 工具调用解析（与主程序一致，含 JSON 修复） ----------
def _repair_json(s):
    s = re.sub(r',\s*([}\]])', r'\1', s)
    stack, in_str, escape, out = [], False, False, []
    for ch in s:
        if escape:
            escape = False
            out.append(ch)
            continue
        if ch == '\\':
            escape = True
            out.append(ch)
            continue
        if ch == '"':
            in_str = not in_str
            out.append(ch)
            continue
        if in_str:
            out.append(ch)
            continue
        if ch in '{[':
            stack.append('}' if ch == '{' else ']')
            out.append(ch)
        elif ch in '}]':
            while stack and stack[-1] != ch:
                out.append(stack.pop())
            if stack:
                stack.pop()
            out.append(ch)
        else:
            out.append(ch)
    if not in_str:
        out.extend(reversed(stack))
    return ''.join(out)


def _looks_like_tool_call(response_text):
    """判断模型是否试图调用工具（即便格式不完整）。用于把「损坏调用」
    当作可恢复错误重试，而不是误当成最终答案终止任务。"""
    return ("FunctionCallBegin" in response_text
            or "FunctionCallEnd" in response_text
            or re.search(r'["\']?(?:name|tool|function)["\']?\s*[:：]\s*["\']',
                         response_text) is not None)


def _parse_call_items(d):
    """把解析出的 JSON items 归一化为 [(name, params), ...]。"""
    items = d if isinstance(d, list) else [d]
    out = []
    for it in items:
        if not isinstance(it, dict):
            continue
        name = it.get("name") or it.get("tool") or it.get("function")
        if not name:
            continue
        p = it.get("parameters")
        if not isinstance(p, dict):
            p = it.get("args")
        if not isinstance(p, dict):
            p = it.get("arguments")
        out.append((str(name), p if isinstance(p, dict) else {}))
    return out


def _collect_call_objects(text):
    """最兜底：按出现顺序收集形如 {..工具调用..} 的对象，返回 [(name, params)]。
    仅处理不含嵌套大括号的平坦对象；嵌套结构由整体 JSON 解析路径承担。"""
    out = []
    for mm in re.finditer(r'\{[^{}\n]*\}', text):
        raw = mm.group(0)
        for cand in (raw, _repair_json(raw)):
            try:
                it = json.loads(cand)
            except Exception:
                continue
            if not isinstance(it, dict):
                continue
            name = it.get("name") or it.get("tool") or it.get("function")
            if not name:
                continue
            p = it.get("parameters")
            if not isinstance(p, dict):
                p = it.get("args")
            if not isinstance(p, dict):
                p = it.get("arguments")
            out.append((str(name), p if isinstance(p, dict) else {}))
            break
    return out


def parse_tool_calls(response_text):
    """解析一个响应中可能包含的一个或多个工具调用，按出现顺序返回
    [(name, params), ...]。支持 FunctionCallBegin/End 标记块、JSON 数组、
    json 围栏、单个对象等形态；可解析多个调用以实现一轮多工具。"""
    # 1) 优先走要求的标记块（块内整体视为 JSON 数组或单对象）
    m = re.search(r'<\|FunctionCallBegin\|>\s*(.*?)\s*<\|FunctionCallEnd\|>',
                  response_text, re.DOTALL)
    if m:
        body = m.group(1).strip()
        for cand in (body, _repair_json(body)):
            try:
                calls = _parse_call_items(json.loads(cand))
            except Exception:
                continue
            if calls:
                return calls
        return _collect_call_objects(body)
    # 2) 兜底：整段整体 JSON（数组或单对象）
    for cand in (response_text.strip(), _repair_json(response_text.strip())):
        try:
            calls = _parse_call_items(json.loads(cand))
        except Exception:
            continue
        if calls:
            return calls
    # 3) json 围栏块整体解析
    for block in re.findall(r'```(?:json)?\s*(.*?)```', response_text, re.DOTALL):
        for cand in (block, _repair_json(block)):
            try:
                calls = _parse_call_items(json.loads(cand))
            except Exception:
                continue
            if calls:
                return calls
    # 4) 最兜底：按出现顺序扫描对象
    return _collect_call_objects(response_text)


def parse_tool_call(response_text):
    """兼容旧接口：返回第一个工具调用 (name, params)，无则 None。"""
    calls = parse_tool_calls(response_text)
    return calls[0] if calls else None


def _is_tool_error(result_text):
    """粗略判断工具结果是否为错误（用于自纠错提示，而非终止任务）。"""
    if not result_text:
        return False
    head = result_text.strip()[:40]
    return (head.startswith("错误")
            or head.startswith("❌")
            or "不存在" in head or "不识别" in head
            or head.startswith("失败")
            or "不可用" in head
            or "需要 " in head or "需要参数" in head)


def build_tool_descriptions():
    parts = ["你可以调用以下工具来完成任务：\n"]
    for name, info in TOOLS.items():
        line = f"- {name}: {info['description']}"
        hint = _TOOL_HINTS.get(name)
        if hint:
            line += f" [{hint}]"
        parts.append(line)
    parts += ["\n调用格式：",
              "需要调用工具时就严格按照以下JSON输出（可一次调用多个工具，用数组并列）：",
              '<|FunctionCallBegin|>[{"name": "工具1", "parameters": {...}}, {"name": "工具2", "parameters": {...}}]<|FunctionCallEnd|>',
              "也可以一次只调用一个工具（单个对象）。",
              "\n当任务完成，不需要再调用工具时，直接用自然语言给出最终答案。",
              "拿到工具结果后继续推理；如需继续调用工具就再输出下一个工具调用。",
              "如果用户的问题可以直接回答，不需要调用工具，请直接回答。"]
    return "\n".join(parts)


# 少样本示例：非 f-string，避免其中的 JSON 花括号被当成格式占位符
_FEWSHOT_EXAMPLES = """工具调用示例（照抄这个精确格式，参数键名要与工具说明里一致，不要发明新键）：
示例1（先查再算，SMILES原子整体使用）：
用户：查一下苯的分子量
助手：<|FunctionCallBegin|>[{"name": "query_compound", "parameters": {"name": "苯"}}]<|FunctionCallEnd|>
结果：苯 的SMILES：C1=CC=CC=C1
助手：<|FunctionCallBegin|>[{"name": "analyze_properties", "parameters": {"smiles": "C1=CC=CC=C1"}}]<|FunctionCallEnd|>
结果：分子量: 78.11
助手：苯的分子量为 78.11 g/mol。
（要点：analyze_properties 的参数键是 smiles，值必须是上一步 query_compound 原样返回的 SMILES，
不能改键名、不能加 name/value 这种包装、不能拼铝宾前缀。）

示例2（单次查询即完成）：
用户：阿司匹林的 SMILES 是什么？
助手：<|FunctionCallBegin|>[{"name": "query_compound", "parameters": {"name": "阿司匹林"}}]<|FunctionCallEnd|>"""


AGENT_SYSTEM_PROMPT = f"""你是智算铝宾化学研究智能体，专门为铝宾（Aluminylene）化学研究设计。

你的任务是通过调用工具来帮助用户完成化学研究任务。
{build_tool_descriptions()}

工具优先策略（务必先看这里）：
- 凡涉及「查询数据 / 计算性质 / 优化结构 / 搜索过渡态、能垒 / 谱图 / 波函数分析 / 跑计算任务」的请求，
  都【必须】先调用对应工具，禁止仅凭记忆给出这类结果或直接跳过工具。
- 常用请求 → 首选工具 对照：
  · 查化合物 / SMILES / 性质  → query_compound（中文名或别名），必要时再用拿到的 SMILES 调 query_pubchem
  · 几何优化 / 分子构建        → mdp_optimize（快速）或 dft_optimize；耗时任务用 submit_dft_job
  · DFT 单点 / 频率            → dft_single_point / dft_frequency，或 submit_dft_job(job_type=sp/freq)
  · 泛函 / 基组推荐            → query_functional / query_basis_set
  · 过渡态 / 能垒 / 反应机理    → task_advisor → transition_state_search / submit_dft_job(job_type=ts)
  · 键级 / 原子电荷 / 波函数    → multiwfn_bond_order / multiwfn_charge / multiwfn_rdg
  · 结构理解 / 配位识别        → interpret_result / analyze_properties
  · 谱图可视化                → visualize_spectrum
  · 运行自定义脚本             → write_file + run_python
- 工具返回的值（SMILES、能量、键长、文件名）是【下一个工具/判断】的输入，
  绝不是工具名本身：不要把它们填进 JSON 的 name 字段，也不要原样当成工具再次调用。
- 化合物SMILES按"原子整体"使用：query_compound 返回某个化合物的SMILES后，
  必须【原样、独立】地作为整体传给下一个计算工具，绝不可以在其前后拼接或插入任何片段
  （尤其不要把 '[N][Al]' 等铝宾背景SMILES加到其它化合物前面）。'[N][Al]' 只属于用户
  明确询问的纯铝宾物种，与用户查询的其它化合物无关。
- 禁止用相同的（或空的）参数重复调用同一个工具：工具已经返回结果就基于结果继续，
  不要反复空查询「兜底列表」。

{_FEWSHOT_EXAMPLES}

工作方式（计划→行动→反思→下一步）：
- 行动前先在内心规划需要哪些信息、按什么顺序调用工具；不要盲目逐一尝试。
- 每个工具结果回来后，先提炼它告诉了你什么、是否解决当前子问题，再决定下一步：
  继续调工具、调整参数重试，或给出最终答案。
- 遇到工具报错：先定位原因（参数缺失/类型不对/文件路径不存在/后端未配置/格式非法），
  修正后重试；若连续多次相同报错、确属环境或配置问题，就停止重试并向用户说明原因和可行建议。

重要规则：
1. 依照上面的「工具优先策略」判断是否调用工具：凡涉及查询、计算、优化、能垒、谱图、波函数分析的请求，必须调用工具；仅当用户只问纯概念性、且确实无需任何计算/查询时才可直接回答。
2. 需要调用工具时，按上面的JSON格式输出；不要在工具调用里夹带多余解释文字。
3. 一次可调用多个互相独立的工具（数组并列），它们会依次执行；有依赖关系的工具必须分步调用。
4. 拿到工具结果后，总结信息并决定下一步，而不是重复上一步。
5. 当你认为任务已经完成，直接用自然语言给出最终总结（含关键结论与数据），不要再调用工具。
6. 回答要专业、准确，符合化学研究规范。绝不可编造工具结果中没有出现的具体数值
   （能量、键长、能垒、CAS号、分子量、天然产物官能团数等）：若工具未返回某数据，明确告知用户"未获取到/需另查"，
   而不是凭空给出一个数字。
7. 工具返回的具体数值是【权威结果】：最终回答必须原样采用工具给出的数值（如分子量 180.04），
   绝不允许再自行代入原子量另算、或凭记忆"修正"成别的数字（如把 180.04 改成 180.16），
   也不允许补充工具没有返回的 CAS 号等字段。
8. 工具返回错误时，尝试修正参数或改用其他方法/工具，不要连续盲目重试同一种错误调用。
9. 当用户提到化合物中文名称（如"芳基咔唑铝宾 (10)"、"阿司匹林"、"咔唑基铝烯前体"）而非直接给出SMILES时，必须先调用 query_compound 工具获取对应的SMILES，再用该SMILES调用计算类工具。
10. DFT计算后端（Gaussian/ORCA/SSH集群）由用户在设置页配置。计算工具可选backend参数（local_g16/local_orca/cluster_g16），用户未指定时省略该参数即可。当同步DFT工具失败且提示后端问题时，建议用户检查设置页配置或改用submit_dft_job异步提交。
11. 长时间计算（DFT几何优化、频率分析，或使用SSH集群后端）必须使用 submit_dft_job 异步提交：提交后向用户说明任务正在后台运行、完成后会自动恢复对话，然后立即结束本轮回答，不要同步等待。
12. 需要运行自定义代码（批量解析、生成坐标/CIF、自写数据处理算法）时，使用 run_python：先write_file/直接code写脚本，再run_python跑，根据stdout/stderr迭代修正。
13. JSON 中 name 字段只能填写上面的真实工具名；任何 SMILES、数值、文件名都不允许出现在 name 位置。"""


# ---------- HTTP 处理 ----------
class ApiHandler(BaseHTTPRequestHandler):
    server_version = "ZhisuanAgent/1.0"

    def log_message(self, fmt, *args):
        pass  # 静默访问日志

    # -- 工具方法 --
    def _json_body(self):
        length = int(self.headers.get("Content-Length", 0))
        if length <= 0:
            return {}
        try:
            return json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception:
            return {}

    def _send_json(self, obj, code=200):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _sse_start(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()

    def _sse(self, event_type, payload):
        data = json.dumps({"type": event_type, **payload}, ensure_ascii=False).encode("utf-8")
        self.wfile.write(b"data: " + data + b"\n\n")
        self.wfile.flush()

    def _error(self, msg, code=400):
        self._send_json({"error": msg}, code)

    # -- GET --
    def do_GET(self):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        if u.path == "/api/health":
            ollama_ok, model_ok, err = False, False, ""
            if OLLAMA_AVAILABLE:
                try:
                    llm_models = (LLM_ROUTER.available_local_models()
                                  if LLM_ROUTER else
                                  [m.get("model") for m in ollama.list().get("models", [])])
                    ollama_ok = True
                    model_name = MODEL_NAME if not (LLM_ROUTER and LLM_ROUTER.backend in ("hybrid", "cloud")) else LLM_ROUTER.local_model()
                    model_ok = model_name in llm_models
                    err = "" if model_ok else f"模型 {model_name} 未下载"
                except Exception as e:
                    err = f"Ollama服务不可达: {e}"
            router_info = (LLM_ROUTER.summary() if LLM_ROUTER
                           else {"backend": "local", "local_model": MODEL_NAME})
            self._send_json({
                "status": "ok", "server": "agent_server.py",
                "ollama": ollama_ok, "model": MODEL_NAME, "model_ready": model_ok,
                "ollama_error": err,
                "model_backend": router_info.get("backend", "local"),
                "local_model": router_info.get("local_model", MODEL_NAME),
                "ollama_host": router_info.get("ollama_host", "http://127.0.0.1:11434"),
                "cloud_configured": router_info.get("cloud_configured", False),
                "cloud_model": router_info.get("cloud_model", ""),
                "cloud_privacy_consent": router_info.get("cloud_privacy_consent", False),
                "molopt": os.path.isfile(MOLOPT_EXE),
                "molview": os.path.isfile(MOLVIEW_EXE),
                "rdkit": MOL_AVAILABLE, "knowledge_base": KB_AVAILABLE,
                "dft": DFT_AVAILABLE, "workflow": WF_AVAILABLE,
                "dft_backends": list(DFT_MANAGER.backends.keys()) if DFT_MANAGER else [],
                "dft_default": getattr(DFT_MANAGER, "default_backend", None) if DFT_MANAGER else None,
                "compounds": len(_load_compound_db()),
                "skills": len(KB.list_skills()) if KB_AVAILABLE else 0,
                "python": sys.version.split()[0],
            })
        elif u.path == "/api/compound/png":
            sm = (q.get("smiles") or "").strip()
            nm = (q.get("name") or "").strip()
            if not sm:
                self._error("缺少 smiles 参数", 400)
                return
            cache_key = (sm, nm)
            data = _COMPOUND_PNG_CACHE.get(cache_key)
            if data is None:
                data = _compound_png_bytes(sm, nm)
                _COMPOUND_PNG_CACHE[cache_key] = data
            if not data:
                self._error("无法为给定 SMILES 生成结构图", 422)
                return
            self.send_response(200)
            self.send_header("Content-Type", "image/png")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        elif u.path == "/api/compounds":
            db = _load_compound_db()
            query = (q.get("q") or "").strip()
            if query:
                hits = _lookup_compound(query)
                self._send_json({"query": query, "matches": [
                    {"name": k, "smiles": v} for k, v in hits]})
            else:
                self._send_json({"total": len(db), "compounds": [
                    {"name": k, "smiles": v} for k, v in db.items()]})
        elif u.path == "/api/knowledge/categories":
            cats = KB.list_categories() if KB_AVAILABLE else []
            counts = {}
            if KB_AVAILABLE:
                for s in KB.list_skills():
                    counts[s.category] = counts.get(s.category, 0) + 1
            self._send_json({"categories": [
                {"name": c, "count": counts.get(c, 0)} for c in cats]})
        elif u.path == "/api/knowledge/skills":
            if not KB_AVAILABLE:
                self._send_json({"skills": [], "error": "知识库不可用"})
                return
            category, keyword = q.get("category", ""), q.get("q", "")
            skills = KB.search_skills(keyword) if keyword.strip() else \
                (KB.list_skills() if category in ("", "全部") else KB.list_skills(category))
            self._send_json({"skills": [{
                "name": s.name, "title": s.title, "category": s.category,
                "description": (s.description or "")[:120],
                "tags": s.tags, "size": len(s.content or "")} for s in skills]})
        elif u.path == "/api/knowledge/skill":
            name = q.get("name", "")
            skill = KB.get_skill(name) if KB_AVAILABLE else None
            if skill:
                self._send_json({"name": skill.name, "title": skill.title,
                                 "category": skill.category, "content": skill.content})
            else:
                self._error(f"未找到技能: {name}", 404)
        elif u.path == "/api/workflows":
            wfs = []
            if WF_AVAILABLE:
                for wf_id, wf in WF_ENGINE.workflows.items():
                    wfs.append({"id": wf_id, "nodes": len(wf.get("nodes", [])),
                                "connections": len(wf.get("connections", []))})
            self._send_json({"workflows": wfs})
        elif u.path == "/api/settings":
            config = {}
            if os.path.exists(CONFIG_PATH):
                try:
                    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
                        config = json.load(f)
                except Exception:
                    config = {}
            self._send_json({"config": config, "model": MODEL_NAME})
        elif u.path == "/api/dft_config":
            self._send_json({
                "config": _load_dft_config(),
                "config_path": DFT_CONFIG_PATH,
                "backends": list(DFT_MANAGER.backends.keys()) if DFT_MANAGER else [],
                "default_backend": getattr(DFT_MANAGER, "default_backend", None)
                                    if DFT_MANAGER else None,
            })
        elif u.path == "/api/model_config":
            if LLM_ROUTER:
                self._send_json({
                    "config": LLM_ROUTER.config,
                    "config_path": llm_router.MODEL_CONFIG_PATH,
                    "summary": LLM_ROUTER.summary(),
                })
            else:
                self._send_json({"config": {}, "error": "模型路由不可用"})
        elif u.path == "/api/models":
            info = LLM_ROUTER.summary() if LLM_ROUTER else {}
            self._send_json({
                "backend": info.get("backend", "local"),
                "local_models": info.get("available_local_models", []),
                "local_selected": info.get("local_model", MODEL_NAME),
                "cloud_configured": info.get("cloud_configured", False),
                "cloud_model": info.get("cloud_model", ""),
            })
        elif u.path == "/api/task/status":
            task_id = q.get("id", "")
            t = _TASKS.get(task_id)
            if not t:
                self._error(f"未找到任务: {task_id}", 404)
            else:
                self._send_json(_task_view(t))
        elif u.path == "/api/task/list":
            with _TASKS_LOCK:
                items = [_task_view(t) for t in
                         sorted(_TASKS.values(), key=lambda x: x.get("submitted_at", 0),
                                reverse=True)[:50]]
            self._send_json({"tasks": items, "total": len(_TASKS)})
        elif u.path == "/api/workflow/status":
            # 工作流状态快照：Java Swing GUI 以 poll_seconds 周期轮询，
            # 返回每节点 status/message/energy/log_tail/detail，替代 M1 模拟数据。
            self._send_json(_workflow_status())
        elif u.path == "/api/workflow/jobs":
            # 任务进程快照：跨 WSL/容器/本地/集群，供「任务进程」标签页轮询。
            self._send_json(_workflow_jobs())
        elif u.path == "/api/workflow/structure":
            # 指定任务的提交时分子结构（symbols/coords/bonds），供球棍渲染。
            task_id = q.get("task_id", "")
            self._send_json(_structure_for_task(task_id))
        else:
            self._error("未知端点", 404)

    # -- POST --
    def do_POST(self):
        u = urlparse(self.path)
        body = self._json_body()
        if u.path == "/api/chat":
            self._handle_chat(body)
        elif u.path == "/api/agent":
            self._handle_agent(body)
        elif u.path == "/api/optimize":
            self._handle_optimize(body)
        elif u.path == "/api/properties":
            self._handle_properties(body)
        elif u.path == "/api/visualize":
            self._handle_visualize(body)
        elif u.path == "/api/visualize_result":
            self._handle_visualize_result(body)
        elif u.path == "/api/parse_spectrum":
            self._handle_parse_spectrum(body)
        elif u.path == "/api/workflow/run":
            self._handle_workflow_run(body)
        elif u.path == "/api/dft_config":
            cfg = {k: str(body.get(k, "") or "").strip() for k in DFT_CONFIG_FIELDS}
            _save_dft_config(cfg)
            backends = _apply_dft_config(cfg)
            default = getattr(DFT_MANAGER, "default_backend", "local_g16") \
                if DFT_MANAGER else "local_g16"
            self._send_json({
                "ok": True, "saved_to": DFT_CONFIG_PATH,
                "backends": backends, "default_backend": default,
                "default_applied": default == cfg.get("default_backend"),
            })
        elif u.path == "/api/model_config":
            if not (LLM_ROUTER and llm_router):
                self._error("模型路由不可用")
                return
            # 仅接收已知字段；未显式提交 consent 时保持原值，绝不静默开启云端授权
            wanted = ["model_backend", "local_model", "cloud_provider",
                      "cloud_base_url", "cloud_api_key", "cloud_model",
                      "cloud_privacy_consent", "cloud_ts_auto"]
            cur = dict(LLM_ROUTER.config)
            for k in wanted:
                if k in body:
                    v = body[k]
                    if isinstance(v, bool) or isinstance(v, int):
                        cur[k] = bool(v)
                    else:
                        cur[k] = str(v)
            saved = llm_router.save_config(cur)
            LLM_ROUTER.set_config(saved)
            global MODEL_NAME
            MODEL_NAME = LLM_ROUTER.local_model()
            self._send_json({
                "ok": True, "saved_to": llm_router.MODEL_CONFIG_PATH,
                "config": LLM_ROUTER.config,
                "summary": LLM_ROUTER.summary(),
                "privacy_note": ("本地默认：数据不出机" if LLM_ROUTER.backend == "local"
                                 else "已启用云端/混合模式：请确认已勾选允许数据出机"),
            })
        elif u.path == "/api/dft_test":
            self._handle_dft_test(body)
        elif u.path == "/api/task/submit":
            tool = str(body.get("tool", "")).strip()
            params = body.get("params", {})
            if not isinstance(params, dict):
                params = {}
            if tool not in TOOLS:
                self._error(f"未知工具: {tool}，可用：{', '.join(TOOLS.keys())}")
                return
            task_id = _submit_task(tool, params, str(body.get("label", "")))
            self._send_json({"ok": True, "task_id": task_id,
                             "status": "running", "poll_seconds": TASK_POLL_SECONDS})
        elif u.path == "/api/diagnose":
            log = str(body.get("log") or "").strip()
            fields_json = str(body.get("fields_json") or "").strip()
            use = str(body.get("use") or "auto").strip()
            try:
                target_spin = int(body.get("target_spin") or 1)
            except Exception:
                target_spin = 1
            try:
                import job_diagnosis
            except Exception as e:
                self._error(f"分诊模块不可用: {type(e).__name__}: {e}")
                return
            fields = None
            if fields_json:
                try:
                    fields = json.loads(fields_json)
                except Exception:
                    self._error("fields_json 无法解析，请提供合法 JSON 对象")
                    return
            d = job_diagnosis.diagnose(log_file=log, fields=fields,
                                       target_spin=target_spin, use=use)
            d["text"] = job_diagnosis.format_result(d)
            self._send_json(d)
        else:
            self._error("未知端点", 404)

    # -- 端点实现 --
    def _handle_chat(self, body):
        messages = body.get("messages", [])
        if not messages:
            self._error("messages 不能为空")
            return
        self._sse_start()
        last_user = ""
        try:
            if not (LLM_ROUTER and OLLAMA_AVAILABLE):
                self._sse("error", {"message": "模型路由不可用（llm_router/ollama 未加载）"})
                _mark_chat("failed", "模型路由不可用")
                return
            last_user = ""
            for m in messages:
                if m.get("role") == "user":
                    last_user = str(m.get("content") or "")
            _mark_chat("running", last_user[:120])
            escalate = _hybrid_escalate(body, last_user)
            # 长时 + 短期记忆注入（跨会话沉淀/本次会话事实），降低多轮遗忘
            session_id = _session_key(body)
            memory_block = _render_memory_block(session_id, messages)
            if memory_block:
                messages = [{"role": "system", "content": memory_block.strip()}] + messages
            for piece in LLM_ROUTER.chat_stream(
                    messages, temperature=0.7, num_ctx=LLM_NUM_CTX,
                    use_cloud=body.get("use_cloud"), escalate=escalate):
                if piece:
                    self._sse("chunk", {"content": piece})
            self._sse("done", {})
            _mark_chat("done", last_user[:120])
        except Exception as e:
            try:
                self._sse("error", {"message": f"{type(e).__name__}: {e}"})
            except Exception:
                pass
            _mark_chat("failed", last_user[:120])

    def _handle_agent(self, body):
        messages = body.get("messages", [])
        if not messages:
            self._error("messages 不能为空")
            return
        self._sse_start()
        try:
            if not (LLM_ROUTER and OLLAMA_AVAILABLE):
                self._sse("error", {"message": "模型路由不可用（llm_router/ollama 未加载）"})
                return
            session_id = _session_key(body)
            _called_tools = []
            _mark_agent("running")
            agent_messages = [{"role": "system", "content": AGENT_SYSTEM_PROMPT}]
            # —— 会话级短期记忆：从整段历史抽取事实，注入 system 之后 ——
            memory_block = _render_memory_block(session_id, messages)
            if memory_block:
                agent_messages.append({"role": "system",
                                       "content": memory_block.strip()})
            # 上下文支持：GUI 维护完整对话历史，这里保留最近若干轮作为上文
            # （只取 user/assistant 文本消息，避免历史中的工具原始输出撑爆上下文）
            prior = [m for m in messages[:-1]
                     if m.get("role") in ("user", "assistant") and m.get("content")]
            # 降智抑制：对之上文按字符预算裁剪——历史原文过长会稀释当前意图。
            # 从最近一条往回取，累计超预算即停止，但始终保留最近 1 条保证连贯。
            budget_used = 0
            picked = []
            for m in reversed(prior):
                c = str(m.get("content") or "")
                if picked and budget_used + len(c) > _PRIOR_CHAR_BUDGET:
                    break
                picked.append(m)
                budget_used += len(c)
            for m in reversed(picked or prior[-1:]):
                agent_messages.append({"role": m["role"], "content": str(m["content"])})
            user_input = messages[-1].get("content", "")
            agent_messages.append({"role": "user", "content": user_input})
            # 复杂 TS/扫描类请求在 hybrid 模式下自动升级云端强模型
            escalate = _hybrid_escalate(body, user_input)
            max_iterations = 12
            assistant_reply = ""
            # 估算当前加载进上下文的字符量，用于 tool_result 截断预算
            ctx_chars = sum(len(str(m.get("content") or "")) for m in agent_messages)
            # 连续报错计数：触发自纠错提示，而非让模型盲目重试同一种错误
            consecutive_errors = 0
            # 本轮已实际执行的调用签名（工具名+规范化参数），用于拦截重复/空参空转
            executed_calls = set()
            # 同一签名被守卫2拦截的次数：首次是温和纠正，再次则强制收尾给出最终答案
            blocked_sigs = {}
            for iteration in range(max_iterations):
                self._sse("status", {"message": f"正在思考…（第 {iteration + 1} 轮）",
                                     "iteration": iteration + 1})
                assistant_reply = LLM_ROUTER.chat_plain(
                    agent_messages, temperature=0.3, num_ctx=LLM_NUM_CTX,
                    think=False, use_cloud=body.get("use_cloud"), escalate=escalate)
                agent_messages.append({"role": "assistant", "content": assistant_reply})
                calls = parse_tool_calls(assistant_reply)
                # —— 模型想调工具但格式损坏：当作可恢复错误重试，而非误当最终答案 ——
                if not calls and _looks_like_tool_call(assistant_reply):
                    self._sse("tool_error", {"message": "工具调用格式解析失败，要求重试",
                                             "iteration": iteration + 1})
                    agent_messages.append({
                        "role": "user",
                        "content": ("你上一步想调用工具但输出的JSON格式无法解析。"
                                    "请只输出一个干净的工具调用，严格按格式："
                                    '<|FunctionCallBegin|>[{"name":"工具名","parameters":{...}}]'
                                    "<|FunctionCallEnd|>，不要夹带解释文字或重复标记。")})
                    continue
                if not calls:
                    # —— 真正完成：无需再调用工具 ——
                    _remember_session(session_id, assistant_reply)
                    _remember_long_term(session_id, assistant_reply)
                    self._sse("chunk", {"content": assistant_reply})
                    self._sse("done", {"answer": assistant_reply})
                    _mark_agent("done", _called_tools, assistant_reply)
                    return
                # —— 执行本轮解析出的一个或多个工具（串行） ——
                for tool_name, params in calls:
                    tool_name = str(tool_name)
                    params = params if isinstance(params, dict) else {}
                    sig = (tool_name, tuple(sorted((str(k), str(v))
                                                   for k, v in params.items())))
                    # —— 守卫1：模型把工具结果/内容(如SMILES)误当工具名 ——
                    if tool_name not in TOOLS:
                        _called_tools.append(tool_name)
                        _mark_agent("running", _called_tools)
                        self._sse("tool_error", {
                            "message": f"'{tool_name}' 不是合法工具，已纠正",
                            "iteration": iteration + 1})
                        valid = "、".join(list(TOOLS))
                        agent_messages.append({
                            "role": "user",
                            "content": (f"你把「{tool_name}」当成了工具名，但它不是可用工具。"
                                        f"可用工具只有：{valid}。"
                                        "请用上面列出的真实工具名重新构造调用。")})
                        continue
                    # —— 守卫2：与已执行过且参数完全相同的调用重复 → 空转，纠正而非再次执行 ——
                    if sig in executed_calls:
                        _called_tools.append(tool_name)
                        _mark_agent("running", _called_tools)
                        n_block = blocked_sigs.get(sig, 0) + 1
                        blocked_sigs[sig] = n_block
                        self._sse("tool_error", {
                            "message": f"已用相同参数调用过 {tool_name}，跳过重复",
                            "iteration": iteration + 1})
                        if n_block >= 2:
                            # 同一重复已被纠正过却仍在空转：不再提示继续推理，强制用已有结果收尾
                            agent_messages.append({
                                "role": "user",
                                "content": ("你已被多次警告禁止用相同参数重复调用工具，却仍在空转。"
                                            "请【立即停止输出任何工具调用】，把你在前面已经拿到的所有工具结果"
                                            "（如化合物的SMILES与分子量）综合起来，用中文向用户直接给出最终答案。"
                                            "如果信息仍不足以回答，就明确告诉用户还缺哪一步，而不是再调工具。")})
                            break
                        agent_messages.append({
                            "role": "user",
                            "content": (f"你刚刚已经用完全相同的参数调用过 {tool_name}（{params}）。"
                                        "不要在拿到结果后重复调用相同内容的工具："
                                        "请基于已有结果继续推理，或给出最终答案，或改用真正需要的新工具/新参数。")})
                        continue
                    executed_calls.add(sig)
                    _called_tools.append(tool_name)
                    _mark_agent("running", _called_tools)
                    self._sse("tool_call", {"tool": tool_name, "params": params,
                                            "iteration": iteration + 1})
                    tool_result = execute_tool(tool_name, params)
                    display = tool_result[:8000]
                    if len(tool_result) > 8000:
                        display += "\n...（结果已截断）"
                    self._sse("tool_result", {"tool": tool_name, "result": display})
                    # —— 上下文预算：append 进 agent_messages 的 tool_result 也做截断 ——
                    truncated_result, _ = _budget_tool_result(tool_result, tool_name)
                    ctx_chars += len(truncated_result)
                    is_err = _is_tool_error(truncated_result)
                    consecutive_errors = consecutive_errors + 1 if is_err else 0
                    if is_err and consecutive_errors >= 3:
                        # 连续多次同样报错：结束本轮并收起尾，避免无限重试
                        agent_messages.append({
                            "role": "user",
                            "content": (f"工具 {tool_name} 连续报错：\n{truncated_result}"
                                        "\n\n请停止重试，直接给出最终回答：说明失败原因，"
                                        "并给出用户可操作的建议（修正参数/检查文件或后端配置/改用其他工具）。")})
                        assistant_reply = ("⚠️ 任务因工具持续报错未能完成。报错信息：\n"
                                           + truncated_result)
                        _remember_session(session_id, assistant_reply)
                        _remember_long_term(session_id, assistant_reply)
                        self._sse("chunk", {"content": assistant_reply})
                        self._sse("done", {"answer": assistant_reply})
                        _mark_agent("done", _called_tools, assistant_reply)
                        return
                    if is_err and consecutive_errors >= 2:
                        tail = ("\n\n注意：该工具已连续报错。请先根据错误信息定位原因"
                                "（参数缺失/类型不对/文件路径不存在/后端未配置），"
                                "修正后重试；若确属环境或配置问题，请向用户说明原因并给出可操作建议。")
                    else:
                        tail = ("\n\n请根据工具结果继续推理：若任务已完成请直接给出最终答案；"
                                "如需继续调用工具请继续输出下一个工具调用。")
                    agent_messages.append({
                        "role": "user",
                        "content": (f"工具 {tool_name} 执行结果：\n{truncated_result}" + tail)})
                # 多次调用后附一句统一引导，保持循环推进
                agent_messages.append({
                    "role": "user",
                    "content": ("请结合以上所有工具结果继续。任务若完成请直接给出最终答案；"
                                "否则继续输出下一个工具调用。")})
            final = ("⚠️ 已达到最大工具调用次数（12次），任务可能未完全完成。"
                     "以下是当前进展总结：\n" + assistant_reply)
            # 若最后一条仍是未执行的工具调用文本，剥掉格式标记，避免把原始JSON抛给用户
            import re as _re
            final = _re.sub(r'<\|FunctionCall(Begin|End)\|>', '', final).strip()
            if _re.search(r'"name"\s*:', final):
                final = ("⚠️ 任务已达最大工具调用次数，未能形成最终回答，但关键数据已取回。\n"
                         + final)
            _remember_session(session_id, assistant_reply)
            _remember_long_term(session_id, assistant_reply)
            self._sse("chunk", {"content": final})
            self._sse("done", {"answer": final})
            _mark_agent("done", _called_tools, final)
        except Exception as e:
            try:
                self._sse("error", {"message": f"{type(e).__name__}: {e}"})
            except Exception:
                pass
            _mark_agent("failed", _called_tools)

    def _handle_optimize(self, body):
        smiles = _resolve_smiles(body.get("smiles", ""))
        if not smiles:
            self._error("smiles 不能为空")
            return
        if not MOL_AVAILABLE:
            self._error("mol_optimizer 不可用（RDKit 未安装？）")
            return
        try:
            t0 = time.perf_counter()
            result = run_optimization(
                smiles, episodes=int(body.get("episodes", 30)),
                max_steps=int(body.get("max_steps", 60)),
                seed=body.get("seed"))
            self._send_json({
                "smiles": smiles,
                "backend": result.get("backend"),
                "total_energy": result.get("total_energy"),
                "constraint_energy": result.get("constraint_energy"),
                "violations": result.get("violations"),
                "is_valid": result.get("is_valid"),
                "episodes_run": result.get("episodes_run"),
                "elapsed_ms": result.get("elapsed_ms"),
                "wall_ms": (time.perf_counter() - t0) * 1000,
                "html_path": result.get("html_path"),
            })
        except Exception as e:
            self._error(f"优化失败: {type(e).__name__}: {e}", 500)

    def _handle_properties(self, body):
        smiles = _resolve_smiles(body.get("smiles", ""))
        if not smiles:
            self._error("smiles 不能为空")
            return
        try:
            from rdkit import Chem
            from rdkit.Chem import Descriptors
            mol = Chem.MolFromSmiles(smiles)
            if not mol:
                self._error("无效的SMILES")
                return
            self._send_json({
                "smiles": smiles,
                "formula": Chem.rdMolDescriptors.CalcMolFormula(mol),
                "exact_mw": Descriptors.ExactMolWt(mol),
                "logp": Descriptors.MolLogP(mol),
                "hbd": Descriptors.NumHDonors(mol),
                "hba": Descriptors.NumHAcceptors(mol),
                "rotatable": Descriptors.NumRotatableBonds(mol),
                "tpsa": Descriptors.TPSA(mol),
                "heavy_atoms": mol.GetNumHeavyAtoms(),
            })
        except ImportError:
            self._error("RDKit未安装", 500)
        except Exception as e:
            self._error(f"计算失败: {e}", 500)

    def _handle_visualize(self, body):
        smiles = _resolve_smiles(body.get("smiles", ""))
        show_h = body.get("show_hydrogen", True)
        if not smiles:
            self._error("smiles 不能为空")
            return
        if not MOL_AVAILABLE:
            self._error("mol_optimizer 不可用")
            return
        try:
            mol_graph = smiles_to_molecule_graph(smiles, add_hs=True)
            atoms = [a for a in mol_graph.atoms if show_h or a.symbol != "H"]
            n_shown = len(atoms)
            html_path = None
            viewer = "molview"
            if os.path.isfile(MOLVIEW_EXE):
                fd, xyz_path = tempfile.mkstemp(suffix=".xyz", prefix="zhisuan_mol_")
                with os.fdopen(fd, "w", encoding="utf-8") as f:
                    f.write(f"{n_shown}\n{smiles}\n")
                    for a in atoms:
                        c = a.coords
                        f.write(f"{a.symbol} {c[0]:.6f} {c[1]:.6f} {c[2]:.6f}\n")
                subprocess.Popen([MOLVIEW_EXE, xyz_path])
            else:
                html_path = visualize_molecule_3d(mol_graph, title=smiles,
                                                   show_hydrogen=show_h)
                viewer = "browser"
            counts = {}
            for a in atoms:
                counts[a.symbol] = counts.get(a.symbol, 0) + 1
            self._send_json({
                "smiles": smiles, "viewer": viewer,
                "atoms": n_shown, "bonds": len(mol_graph.bond_list),
                "composition": counts, "html_path": html_path,
            })
        except Exception as e:
            self._error(f"可视化失败: {type(e).__name__}: {e}", 500)

    def _handle_visualize_result(self, body):
        path = body.get("file", "")
        if not path or not os.path.isfile(path):
            self._error(f"文件不存在: {path}")
            return
        xyz_path, cubes, natom, err = _prepare_result_visualization(path)
        if err:
            self._error(f"渲染失败: {err}", 500)
            return
        viewer = "molview" if os.path.isfile(MOLVIEW_EXE) else "unavailable"
        if os.path.isfile(MOLVIEW_EXE):
            subprocess.Popen([MOLVIEW_EXE, xyz_path])
        self._send_json({
            "ok": True, "viewer": viewer, "xyz_path": xyz_path,
            "cubes": cubes, "natom": natom,
        })

    def _handle_parse_spectrum(self, body):
        path = body.get("file", "")
        if not path or not os.path.isfile(path):
            self._error(f"文件不存在: {path}")
            return
        try:
            import cclib
            data = cclib.io.ccread(path)
            if data is None:
                self._error("无法解析文件（cclib 返回空）")
                return
            out = {"file": path, "natom": int(getattr(data, "natom", 0))}
            if hasattr(data, "atomcoords") and len(data.atomcoords):
                coords = data.atomcoords[-1]
                natom = int(getattr(data, "natom", len(coords)))
                atomnos = list(getattr(data, "atomnos", []))[:natom]
                while len(atomnos) < natom:
                    atomnos.append(0)
                out["atoms"] = [
                    {"index": i + 1,
                     "symbol": _ATNUM2SYM.get(int(atomnos[i]), "X"),
                     "x": float(coords[i][0]), "y": float(coords[i][1]),
                     "z": float(coords[i][2])}
                    for i in range(natom)
                ]
            if hasattr(data, "scfenergies") and len(data.scfenergies):
                out["scfenergy"] = float(data.scfenergies[-1])
            if hasattr(data, "moenergies") and len(data.moenergies):
                out["moenergies"] = [float(e) for e in data.moenergies[-1]]
                out["homos"] = int(data.homos[-1]) if hasattr(data, "homos") and len(data.homos) else None
            if hasattr(data, "vibfreqs") and len(data.vibfreqs):
                out["vibfreqs"] = [float(f) for f in data.vibfreqs]
                out["imaginary"] = sum(1 for f in data.vibfreqs if f < 0)
            if hasattr(data, "vibirs") and len(data.vibirs):
                out["vibirs"] = [float(i) for i in data.vibirs]
            self._send_json(out)
        except ImportError:
            self._error("未安装 cclib（pip install cclib）", 500)
        except Exception as e:
            self._error(f"解析失败: {type(e).__name__}: {e}", 500)

    def _handle_workflow_run(self, body):
        wf_id = body.get("id", "")
        if not WF_AVAILABLE:
            self._error("工作流引擎不可用")
            return
        if wf_id not in WF_ENGINE.workflows:
            self._error(f"未找到工作流: {wf_id}", 404)
            return
        try:
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            result = loop.run_until_complete(WF_ENGINE.execute_workflow(wf_id, {}))
            loop.close()
            self._send_json({"id": wf_id, "result": str(result)})
        except Exception as e:
            self._error(f"执行失败: {type(e).__name__}: {e}", 500)

    def _handle_dft_test(self, body):
        """测试 DFT 计算后端连通性：g16 / orca / multiwfn / workspace / ssh"""
        target = str(body.get("target", "")).strip()
        cfg = _load_dft_config()
        if target in ("g16", "orca", "multiwfn"):
            key = {"g16": "g16_command", "orca": "orca_command",
                   "multiwfn": "multiwfn_command"}[target]
            default_cmd = {"g16": "g16", "orca": "orca", "multiwfn": "multiwfn"}[target]
            cmd = (cfg.get(key) or default_cmd).strip()
            resolved = shutil.which(cmd)
            if not resolved and os.path.isfile(cmd):
                resolved = os.path.abspath(cmd)
            if resolved:
                self._send_json({"ok": True, "resolved": resolved,
                                 "detail": f"已找到程序: {resolved}"})
            else:
                self._send_json({"ok": False,
                                 "detail": f"未找到 '{cmd}'，请填写完整可执行路径"
                                           f"（或将其目录加入 PATH 后重试）"})
        elif target == "workspace":
            ws = (cfg.get("workspace") or tempfile.gettempdir()).strip()
            try:
                os.makedirs(ws, exist_ok=True)
                probe = os.path.join(ws, ".zs_write_probe")
                with open(probe, "w") as f:
                    f.write("ok")
                os.remove(probe)
                self._send_json({"ok": True, "detail": f"工作区可写: {ws}"})
            except Exception as e:
                self._send_json({"ok": False, "detail": f"工作区不可用: {e}"})
        elif target == "ssh":
            host = (cfg.get("ssh_host") or "").strip()
            username = (cfg.get("ssh_username") or "").strip()
            if not host or not username:
                self._send_json({"ok": False, "detail": "请先填写并保存 SSH 主机与用户名"})
                return
            try:
                import paramiko
                cli = paramiko.SSHClient()
                cli.set_missing_host_key_policy(paramiko.AutoAddPolicy())
                kwargs = {"hostname": host, "username": username, "timeout": 10}
                if (cfg.get("ssh_key") or "").strip():
                    kwargs["key_filename"] = cfg["ssh_key"].strip()
                if (cfg.get("ssh_password") or "").strip():
                    kwargs["password"] = cfg["ssh_password"].strip()
                cli.connect(**kwargs)
                _in, out, _err = cli.exec_command("hostname", timeout=10)
                hostname = out.read().decode("utf-8", "ignore").strip()
                cli.close()
                self._send_json({"ok": True,
                                 "detail": f"SSH 连接成功：{hostname or host}"})
            except ImportError:
                self._send_json({"ok": False, "detail": "未安装 paramiko，无法测试 SSH（pip install paramiko）"})
            except Exception as e:
                self._send_json({"ok": False, "detail": f"SSH 连接失败: {e}"})
        else:
            self._error("未知测试目标（支持 g16 / orca / multiwfn / workspace / ssh）")


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    server = ThreadingHTTPServer(("127.0.0.1", port), ApiHandler)
    print(f"[agent_server] 智算铝宾服务层已启动: http://127.0.0.1:{port}")
    _rsum = LLM_ROUTER.summary() if LLM_ROUTER else {}
    _mode = _rsum.get("backend", "local")
    _priv = _rsum.get("cloud_privacy_consent", False) if _mode != "local" else True
    print(f"[agent_server] 模型: {_rsum.get('local_model', MODEL_NAME)} | 后端: {_mode} "
          f"| 本地就绪: {_rsum.get('local_ready', OLLAMA_AVAILABLE)} "
          f"| 云端授权: {'已勾选(数据可出机)' if _mode != 'local' and _priv else '本地(数据不出机)'}")
    print(f"[agent_server] molopt: {os.path.isfile(MOLOPT_EXE)} | molview: {os.path.isfile(MOLVIEW_EXE)}")
    print(f"[agent_server] 化合物: {len(_load_compound_db())} | "
          f"技能: {len(KB.list_skills()) if KB_AVAILABLE else 0} | DFT: {DFT_AVAILABLE}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[agent_server] 已停止")


if __name__ == "__main__":
    main()
