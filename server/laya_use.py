"""laya 便捷封装：自动走国内镜像，一行加载模型、跑决策。

用法（在 Python 中）:
    import laya_use
    agent = laya_use.load()                 # 英文模型（已缓存）
    agent = laya_use.load("multilingual")   # 多语言模型（第一次会下载）
    res = laya_use.judge(agent, state, questions)
"""
import os

# 国内访问 HuggingFace 必需：走镜像 + 禁用 Xet 大文件加速
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")
os.environ.setdefault("HF_HUB_DISABLE_XET", "1")

import json
import laya


def load(model: str = "english", device: str = "cpu"):
    """加载模型。model 取 'english' / 'multilingual' / 'typed-decisions'。"""
    repo, sub = laya.DEFAULT_MODELS[model]
    return laya.load(repo, subfolder=sub, device=device)


def judge(agent, state, questions):
    """执行一次并行决策。questions 见 laya.Agent.system_one 文档。"""
    res = agent.system_one(state, questions)
    return res["answers"], res["usage"]


def apply_temperature(agent, choice=1.0, score=1.0, noul=1.0):
    """覆盖决策头温度 (softmax 温度 p=softmax(logits/T), clamp 范围 [0.5,5.0])。

    Laya 的温度在运行时实时读取 self.temperature[t] = [choice, score, noul] 与
    temperature_by_options; 本函数清空按选项覆盖, 统一按决策头温度生效。
    默认中性 1.0; 本项目调校值 [1.0, 0.5, 0.5] (见 laya_temp_calibration.*).
    """
    lo, hi = 0.5, 5.0
    agent.temperature = [min(hi, max(lo, float(t))) for t in (choice, score, noul)]
    agent.temperature_by_options = {}
    return agent


def dump(res, indent=2):
    return json.dumps(res, indent=indent, ensure_ascii=False)


if __name__ == "__main__":
    import sys
    print("== laya_use 自检 ==")
    name = sys.argv[1] if len(sys.argv) > 1 else "english"
    print(f"加载 {name} ...")
    agent = load(name)
    print("设备:", agent.device, "| dtype:", agent.dtype)
    s = "A user reported the build failed at the Docker image upload stage."
    q = {
        "severity": {"type": "score", "instructions": "How severe?", "criteria": ["low", "med", "high", "critical"]},
        "topic": {"type": "choice", "instructions": "Main topic?", "criteria": {"ci": "CI/CD", "docker": "container", "code": "source"}},
    }
    ans, usage = judge(agent, s, q)
    print(dump(ans))
    print("tokens:", usage)
    print("== 自检通过 ==")