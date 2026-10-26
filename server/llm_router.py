# -*- coding: utf-8 -*-
"""模型分层路由：本地优先 + 可选「本地 + 云端强模型」混合。

设计约束
--------
1. 数据不出机是默认（免责默认）：backend=local 时不发起任何云端请求。
2. 使用云端（hybrid 升级策略或 cloud 模式）必须显式开启 cloud_privacy_consent，
   否则路由到云端时直接报错并回退本地，绝不静默外发。
3. 兼容三类主流风格：Ollama（本地）与 OpenAI 兼容端点（Qwen/DeepSeek/GLM），
   云端调用仅用标准库 urllib，不引入 requests 等额外依赖。

配置字段（model_config.json，位于项目根目录）
    model_backend       : "local"(默认) | "hybrid" | "cloud"
    local_model         : 本地 Ollama 模型名，默认 "qwen3.5:2b"
    cloud_provider      : 统一 "openai_compat"
    cloud_base_url      : 兼容端点的 base url，如
                          Qwen  https://dashscope.aliyuncs.com/compatible-mode/v1
                          DeepSeek https://api.deepseek.com/v1
                          GLM   https://open.bigmodel.cn/api/paas/v4
    cloud_api_key       : 云端密钥（明文存本地，仅授权后使用）
    cloud_model         : 云端模型名，如 qwen-max / deepseek-chat / glm-4-plus
    cloud_privacy_consent: 是否允许数据出机（默认 false —— 本地优先免责默认）
    cloud_ts_auto       : hybrid 模式下，复杂 TS/扫描类请求自动升级云端（需同时授权）
"""
import json
import os
import re
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))

try:
    import ollama
    OLLAMA_AVAILABLE = True
except Exception:  # pragma: no cover - 打包环境无 ollama 时降级
    ollama = None
    OLLAMA_AVAILABLE = False

MODEL_CONFIG_PATH = os.environ.get("ZS_MODEL_CONFIG",
                                   os.path.join(HERE, "model_config.json"))

DEFAULT_MODEL_CONFIG = {
    "model_backend": "local",
    "local_model": "qwen3.5:2b",
    "local_ollama_host": "http://127.0.0.1:11434",
    "cloud_provider": "openai_compat",
    "cloud_base_url": "",
    "cloud_api_key": "",
    "cloud_model": "",
    "cloud_privacy_consent": False,
    "cloud_ts_auto": True,
}

# 复杂 TS / 扫描 / 活化类任务的关键词，用于 hybrid 自动升级判定
_TS_PATTERN = re.compile(
    r"过渡态|transition\s*state|TS|scan|扫描|relaxed\s*scan|活化|势垒|barrier|"
    r"transition|IRC|反应路径|reaction\s*path|NEB", re.IGNORECASE)


def _load_config():
    cfg = dict(DEFAULT_MODEL_CONFIG)
    try:
        if os.path.exists(MODEL_CONFIG_PATH):
            with open(MODEL_CONFIG_PATH, "r", encoding="utf-8") as f:
                loaded = json.load(f)
                if isinstance(loaded, dict):
                    for k in DEFAULT_MODEL_CONFIG:
                        if k in loaded:
                            cfg[k] = loaded[k]
    except Exception as e:
        print(f"[llm_router] 读取配置失败: {e}")
    return cfg


def save_config(cfg):
    merged = dict(DEFAULT_MODEL_CONFIG)
    merged.update({k: v for k, v in (cfg or {}).items()
                   if k in DEFAULT_MODEL_CONFIG})
    with open(MODEL_CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(merged, f, ensure_ascii=False, indent=2)
    return merged


class PrivacyError(RuntimeError):
    """数据出机未获授权时报错，强制回退本地。"""


class LlmRouter:
    def __init__(self, config=None):
        self.config = _load_config() if config is None else config
        self._local_models_cache = None
        self._local_models_ts = 0.0
        self._cli = None
        self._cli_host = ""

    # ---- 本地 Ollama 连接（可切换宿主/容器地址） ----
    def ollama_host(self):
        return str(self.config.get("local_ollama_host")
                   or "").strip() or "http://127.0.0.1:11434"

    def _client(self):
        """返回绑定到当前 local_ollama_host 的 ollama 客户端，地址变化时自动重建。"""
        host = self.ollama_host()
        if self._cli is None or self._cli_host != host:
            self._cli = ollama.Client(host=host) if OLLAMA_AVAILABLE else None
            self._cli_host = host
            # 地址可能变了，本地模型列表缓存作废
            self._local_models_cache = None
        return self._cli

    # ---- 只读状态 ----
    @property
    def backend(self):
        return str(self.config.get("model_backend", "local"))

    def local_model(self):
        return str(self.config.get("local_model", "") or "qwen3.5:2b")

    def cloud_configured(self):
        return bool((self.config.get("cloud_base_url") or "").strip()
                    and (self.config.get("cloud_api_key") or "").strip())

    def cloud_ready(self):
        return self.cloud_configured() and bool(
            (self.config.get("cloud_model") or "").strip())

    def privacy_consented(self):
        return bool(self.config.get("cloud_privacy_consent"))

    def set_config(self, cfg):
        self.config = dict(DEFAULT_MODEL_CONFIG)
        self.config.update({k: v for k, v in (cfg or {}).items()
                            if k in DEFAULT_MODEL_CONFIG})
        self._local_models_cache = None

    # ---- 本地模型枚举 ----
    def available_local_models(self):
        import time
        now = time.time()
        if self._local_models_cache is not None and now - self._local_models_ts < 10:
            return self._local_models_cache
        names = []
        client = self._client()
        if OLLAMA_AVAILABLE and client is not None:
            try:
                models = client.list().get("models", [])
                names = [m.get("model") for m in models]
            except Exception:
                names = []
        self._local_models_cache = names
        self._local_models_ts = now
        return names

    def local_ready(self):
        return self.local_model() in self.available_local_models()

    # ---- 路由决策 ----
    def _pick(self, use_cloud=None):
        """决定本次调用走本地还是云端。

        use_cloud: None=按配置；True=强制云端（未授权则报错）；False=强制本地。
        仅 hybrid/cloud 模式允许云端；local 模式永不外发。
        """
        if use_cloud is False:
            return "local"
        if use_cloud is True:
            if self.backend in ("hybrid", "cloud") and self.cloud_configured():
                if not self.privacy_consented():
                    raise PrivacyError(
                        "云端调用未获授权：请在设置中勾选“允许数据出机”后再启用云端，"
                        "或保持本地模式以保证数据不出机。")
                return "cloud"
            raise PrivacyError(
                "已请求云端，但未配置云端点或后端不是 hybrid/cloud，已回退本地。")
        # 按配置
        if self.backend == "cloud" and self.cloud_configured():
            if not self.privacy_consented():
                raise PrivacyError(
                    "当前选择云端模式但未授权数据出机；请勾选“允许数据出机”。")
            return "cloud"
        if self.backend == "hybrid":
            return "hybrid"  # 由调用方按任务类型决定（_keep_local 之外）
        return "local"

    def is_ts_task(self, text):
        return bool(_TS_PATTERN.search(text or ""))

    # ---- 统一入口 ----
    def _route_cloud(self, escalate, use_cloud):
        """返回 True 表示本次应走云端。"""
        mode = self._pick(use_cloud)
        if mode == "cloud":
            return True
        return (mode == "hybrid" and escalate
                and self.cloud_configured() and self.privacy_consented())

    def chat_plain(self, messages, *, temperature=0.3, num_ctx=16384,
                   think=None, use_cloud=None, escalate=False):
        """非流式补全，返回最终文本。hybrid 且 escalate(自动TS升级) 时走云端。"""
        use_cloud_route = self._route_cloud(escalate, use_cloud)
        if use_cloud_route:
            try:
                return self._cloud_plain(messages, temperature=temperature)
            except Exception as e:
                # 显式 use_cloud=True 时云端失败不静默回退；自动升级路径则回退本地
                if use_cloud is True:
                    raise
                print(f"[llm_router] 云端调用失败，回退本地: {type(e).__name__}: {e}")
        return self._local_plain(messages, temperature=temperature,
                                 num_ctx=num_ctx, think=think)

    def chat_stream(self, messages, *, temperature=0.7, num_ctx=16384,
                    think=None, use_cloud=None, escalate=False):
        """流式生成（逐个文本片段）。hybrid 且 escalate(自动TS升级) 时走云端。"""
        use_cloud_route = self._route_cloud(escalate, use_cloud)
        if use_cloud_route:
            try:
                yield from self._cloud_stream(messages, temperature=temperature)
                return
            except Exception as e:
                if use_cloud is True:
                    raise
                print(f"[llm_router] 云端调用失败，回退本地: {type(e).__name__}: {e}")
        yield from self._local_stream(messages, temperature=temperature,
                                      num_ctx=num_ctx, think=think)

    # ---- 本地 Ollama ----
    def _local_plain(self, messages, *, temperature, num_ctx, think):
        client = self._client()
        if not OLLAMA_AVAILABLE or client is None:
            raise RuntimeError("ollama 库不可用")
        kwargs = {"model": self.local_model(), "messages": messages,
                  "stream": False, "options": {"temperature": temperature,
                                               "num_ctx": num_ctx,
                                               "repeat_penalty": 1.25,
                                               "min_p": 0.02,
                                               "top_p": 0.9}}
        if think is not None:
            kwargs["think"] = think
        resp = client.chat(**kwargs)
        return resp["message"]["content"].strip()

    def _local_stream(self, messages, *, temperature, num_ctx, think):
        client = self._client()
        if not OLLAMA_AVAILABLE or client is None:
            raise RuntimeError("ollama 库不可用")
        kwargs = {"model": self.local_model(), "messages": messages,
                  "stream": True, "options": {"temperature": temperature,
                                              "num_ctx": num_ctx,
                                              "repeat_penalty": 1.25,
                                              "min_p": 0.02,
                                              "top_p": 0.9}}
        if think is not None:
            kwargs["think"] = think
        for chunk in client.chat(**kwargs):
            piece = chunk.get("message", {}).get("content", "")
            if piece:
                yield piece

    # ---- 云端 OpenAI 兼容 ----
    def _cloud_endpoint(self):
        base = (self.config.get("cloud_base_url") or "").strip().rstrip("/")
        return base + "/chat/completions"

    def _cloud_headers(self):
        key = (self.config.get("cloud_api_key") or "").strip()
        return {"Content-Type": "application/json",
                "Authorization": f"Bearer {key}"}

    def _cloud_plain(self, messages, *, temperature):
        payload = {"model": self.config.get("cloud_model"), "messages": messages,
                   "stream": False, "temperature": temperature}
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(self._cloud_endpoint(), data=data,
                                     headers=self._cloud_headers(),
                                     method="POST")
        with urllib.request.urlopen(req, timeout=600) as resp:
            obj = json.loads(resp.read().decode("utf-8"))
        return (obj["choices"][0]["message"]["content"] or "").strip()

    def _cloud_stream(self, messages, *, temperature):
        payload = {"model": self.config.get("cloud_model"), "messages": messages,
                   "stream": True, "temperature": temperature}
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(self._cloud_endpoint(), data=data,
                                     headers=self._cloud_headers(),
                                     method="POST")
        with urllib.request.urlopen(req, timeout=600) as resp:
            buf = ""
            for raw in resp:
                line = raw.decode("utf-8", "ignore").strip()
                if not line.startswith("data:"):
                    continue
                chunk = line[5:].strip()
                if chunk == "[DONE]":
                    break
                try:
                    obj = json.loads(chunk)
                    delta = obj["choices"][0]["delta"]
                except (KeyError, ValueError, IndexError):
                    continue
                piece = delta.get("content") or ""
                if piece:
                    yield piece

    # ---- 诊断信息 ----
    def summary(self):
        return {
            "backend": self.backend,
            "local_model": self.local_model(),
            "ollama_host": self.ollama_host(),
            "local_ready": self.local_ready(),
            "cloud_configured": self.cloud_configured(),
            "cloud_model": self.config.get("cloud_model") or "",
            "cloud_privacy_consent": self.privacy_consented(),
            "cloud_ts_auto": bool(self.config.get("cloud_ts_auto")),
            "available_local_models": self.available_local_models(),
        }


_router = LlmRouter()


def router():
    return _router