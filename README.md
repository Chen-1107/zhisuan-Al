# ZhiSuan-Al · 三语言智能体科研工作台

面向金属有机化学（铝宾 / Aluminylene）研究的桌面工作台示例：以本地大语言模型驱动的 ReAct 智能体为核心，串联「对话推理 → 化合物查询 → 分子构型优化 → DFT 量子化学计算 → 波函数分析 → 文献知识检索」。

- **Java Swing GUI** — 中文界面，导航栏图标 + 文字，支持中/英/德三语切换
- **Python 服务层** — HTTP + SSE，ReAct 智能体与 31 个工具
- **C++ 计算核心** — MDP / Q-Learning 分子优化、原生 3D 分子查看器、FMO 渲染

> 本仓库只包含**框架代码与 UI**，不含研究数据、计算结果、凭据与本机路径。

---

## 目录结构

```
zhisuan-Al/
├── server/                  # Python 服务层
│   ├── agent_server.py      # 入口：HTTP/SSE、ReAct 循环、工具注册、任务管理
│   ├── dft_backend.py       # DFT 后端抽象（本地 G16 / 本地 ORCA / SSH 集群）
│   ├── mol_optimizer.py     # 分子优化（C++ 核心的 Python 等价实现）
│   ├── job_diagnosis.py     # 计算日志分诊
│   ├── llm_router.py        # 本地 Ollama / 云端 OpenAI 兼容后端路由
│   ├── rag_retriever.py     # 知识库检索（BM25 风格）
│   ├── knowledge_base.py    # 技能文档管理
│   ├── automation.py        # 自动化脚本
│   ├── mcp/                 # 工作流引擎与工具模块（base/chat/dft/analysis/method/structure）
│   ├── data/                # 示例化合物库（名称 → SMILES）
│   └── requirements.txt
├── ui-java/                 # 主 GUI（Java Swing）
│   ├── src/zhisuan/         # 24 个源文件
│   ├── tests/               # 6 个手写测试入口（无 JUnit 依赖）
│   ├── lib/                 # 第三方 jar
│   └── build.bat / run.bat
├── ui-workflow/             # 工作流画布（可独立运行）
│   ├── src/com/zhisuan/workflow/
│   └── build.bat / run.bat
├── native/                  # C++ 核心与构建脚本
│   ├── molopt.cpp           # 分子优化（MDP / Q-Learning）
│   ├── molview.cpp          # 3D 球棍模型查看器
│   └── fmo_render_cpp.cpp   # 轨道渲染
├── skills/                  # 19 个 Markdown 知识库文档
├── examples/                # 配置模板
├── installer/               # 安装包源码（C# 向导 + 载荷打包脚本）
├── launcher/                # 启动器源码（Launcher.cs）
└── start.bat                # 一键启动（编译 → 服务层 → GUI）
```

---

## 环境要求

| 组件 | 要求 |
| --- | --- |
| 操作系统 | Windows 10/11 x64 |
| JDK | 17 及以上（开发环境用 JDK 21 验证） |
| Python | 3.9+，建议 conda 环境（rdkit、paramiko、scp、ollama、cclib、numpy） |
| Ollama | 本地运行并拉取所用模型（默认 `qwen3.5:2b`） |
| 计算软件 | 按需安装 Gaussian16 / ORCA / Multiwfn |
| C++ | Visual Studio 2019+（仅编译 native 时需要） |

---

## 快速开始

### 1. 配置

```bat
copy examples\dft_config.example.json  server\dft_config.json
copy examples\model_config.example.json server\model_config.json
```

按本机环境修改 `server/dft_config.json` 中的计算软件路径；集群相关字段留空即不启用 SSH 后端。
`server/model_config.json` 默认走本地 Ollama，`cloud_api_key` 留空。

### 2. 安装依赖

```powershell
pip install -r server\requirements.txt
ollama pull qwen3.5:2b
```

### 3. 启动

方式一：双击根目录的 `ZhiSuan-Al.exe`（无需命令行参数）。启动器会依次检查编译产物、拉起服务层、等待健康检查通过，再打开 GUI；参数可原样透传，例如 `ZhiSuan-Al.exe --port=8899 --mode=audit`。运行日志写入 `logs\`。

> 编译后的 `ZhiSuan-Al.exe`、`ZhiSuan-Al-Setup.exe` 与 `jlatexmath-1.0.7.jar` 不再随仓库分发，请从 [GitHub Releases](https://github.com/Chen-1107/zhisuan-Al/releases) 下载对应版本。

启动器源码在 `launcher\Launcher.cs`，重新生成执行：

```bat
launcher\build_launcher.bat
```

方式二：批处理

```bat
start.bat
```

脚本依次完成：编译 GUI → 启动 `python server\agent_server.py 8899` → 健康检查 → 打开 GUI。

也可分步执行：

```bat
ui-java\build.bat                 :: 编译 ui-workflow + ui-java
python server\agent_server.py 8899
ui-java\run.bat                   :: 启动 GUI
```

`server\` 未启动时 GUI 以离线模式运行（设置页可以测连通性）。

### 4. 编译 C++ 核心（可选）

在 Visual Studio 的「x64 Native Tools Command Prompt」中：

```bat
native\build_molopt.bat
native\build_molview.bat
native\build_fmo.bat
```

未编译时 `mol_optimizer.py` 自动回退到纯 Python 实现。

---

## 安装包

`ZhiSuan-Al-Setup.exe` 是自包含安装程序，内嵌运行文件与启动器（约 1.2 MB），请从 [GitHub Releases](https://github.com/Chen-1107/zhisuan-Al/releases) 下载。

- 默认安装到 `%LOCALAPPDATA%\Programs\ZhiSuan-Al`，用户级安装，无需管理员权限
- 创建桌面快捷方式与开始菜单项（含卸载入口）
- 卸载信息写入 `HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\ZhiSuan-Al`，可在「设置 → 应用」中卸载
- 覆盖安装时保留已修改的 `server\dft_config.json` 与 `server\model_config.json`

安装前仍需自备 Python 3.9+（rdkit / paramiko / requests）、JDK 17+ 与 Ollama 模型；首次启动由启动器自动编译 Java 源码。

可选命令行参数：

```bat
ZhiSuan-Al-Setup.exe --silent --dir=D:\ZhiSuan-Al --no-shortcuts
ZhiSuan-Al-Setup.exe --uninstall
```

重新生成安装包：

```bat
installer\build_installer.bat
```

脚本先调用 `installer\make_payload.ps1` 打包载荷，再用 .NET Framework 自带 `csc` 编译 `installer\Installer.cs`。`installer\payload.zip` 与 `installer\stage\` 属中间产物，已被 `.gitignore` 排除。

---

## 界面

### 装配模式

同一内核可切换三种 GUI 装配（启动参数 `--mode=`）：

| 模式 | 导航页 |
| --- | --- |
| `default`（默认） | AI 助手 / 知识星图 / 谱分析 / 构型优化 / 计算任务 / 化合物库 / 3D 视图 / 知识库 / 记忆 / 工作流 / 设置 |
| `audit` | AI 助手 / 审查台 / 设置 |
| `dispatch` | AI 助手 / 调度中心 / 设置 |

```bat
ui-java\run.bat --mode=audit --port=8899
```

### 页面说明

| 页面 | 功能 |
| --- | --- |
| AI 助手 | 对话；Markdown / 公式渲染、思考过程打字机效果、工具调用卡片、后台任务状态行 |
| 知识星图 | 知识库技能关系可视化 |
| 谱分析 | 上传 Gaussian / ORCA 输出，解析能级、频率、光谱 |
| 构型优化 | SMILES → 3D 结构优化（MDP / Q-Learning） |
| 计算任务 | DFT 任务提交与状态轮询、日志分诊 |
| 化合物库 | 中文名 → SMILES 查询、结构图预览 |
| 3D 视图 | 原生球棍模型查看（`molview.exe`） |
| 知识库 | 分类浏览技能文档，全文渲染与搜索 |
| 记忆 | 决策事实与偏好记忆 |
| 工作流 | 研究链节点状态与执行记录 |
| 设置 | DFT 后端路径、SSH 集群、模型后端、连通性测试 |

---

## 服务层 API

`agent_server.py` 使用 `ThreadingHTTPServer`，默认端口 8899。

**GET**：`/api/health`、`/api/compounds`、`/api/compound/png`、`/api/knowledge/categories`、`/api/knowledge/skills`、`/api/knowledge/skill`、`/api/workflows`、`/api/settings`、`/api/dft_config`、`/api/model_config`、`/api/models`、`/api/task/status`、`/api/task/list`、`/api/workflow/status`、`/api/workflow/jobs`、`/api/workflow/structure`

**POST**：`/api/chat`、`/api/agent`（SSE）、`/api/optimize`、`/api/properties`、`/api/visualize`、`/api/visualize_result`、`/api/parse_spectrum`、`/api/workflow/run`、`/api/dft_config`、`/api/model_config`、`/api/dft_test`、`/api/task/submit`、`/api/diagnose`

健康检查：

```powershell
curl http://127.0.0.1:8899/api/health
```

---

## 分子优化算法

由 `native/molopt.cpp` 执行，`server/mol_optimizer.py` 为等价回退。

| 组件 | 配置 |
| --- | --- |
| 状态向量 | `7N + 5B` 维（N = 原子数，B = 键数） |
| Q 网络 | 3 层 MLP `7N+5B → 64 → 64 → 6`，ReLU，手写反向传播 |
| 参数更新 | 自然梯度 + 对角 Fisher 预处理器（`lr = 0.01`） |
| 探索 | ε-greedy（`1.0 → 0.05`）+ Q 值温度 softmax |
| 路径系综 | `K = 4` 副本并行，各记历史最优构象 |
| 熵正则 | `λ·H[π_θ]`，`λ = 0.1` |
| 默认轮次 | 30 episodes × 60 steps |

奖励函数：

```
R = -0.1·ln(1 + E_ff/n)
  - 0.5·ln(1 + E_c/n)
  - 2.0·ln(1 + E_err/n)
  - 5.0·ln(1 + 10·V/n)
R = clip(R, -50, 50)
```

键长约束通过 `REFBOND <i> <j> <r0_pm>`（拉向实验键长，覆盖内建谐振子 `r0`）与 `ERRBOND <i> <j> <r_err_pm> <w>`（对已知错误键长施加高斯惩罚）双向编码，两套公式在 C++ 与 Python 两侧逐项对齐。

---

## 配置说明

| 文件 | 用途 |
| --- | --- |
| `server/dft_config.json` | 计算软件路径、工作区、默认后端、SSH 集群参数 |
| `server/model_config.json` | 本地 Ollama 与云端 OpenAI 兼容后端 |
| `server/tasks.json` | 异步任务队列（运行时生成） |

服务层也支持环境变量 `ZHISUAN_STATE_DIR` 指定状态目录；`ZS_MODEL_CONFIG` 可覆盖模型配置文件路径。

**凭据处理**：SSH 密码与云端 API Key 只保存于本机上述配置文件中，不随仓库分发；`.gitignore` 已排除。

---

## 测试

测试为手写 `main()` 入口，无需测试框架：

```bat
cd ui-java
:: 若 lib\ 下缺少 jlatexmath-1.0.7.jar，请从 GitHub Releases 下载并放入该目录
javac -encoding UTF-8 -cp "out;lib\jlatexmath-1.0.7.jar;..\ui-workflow\out" -d out tests\TestMarkdown.java
java -cp "out;lib\jlatexmath-1.0.7.jar;..\ui-workflow\out" TestMarkdown
```

`TestE2E` / `TestLive` 需要服务层已启动；`TestTaskCard` / `TestTaskLine` / `TestTypewriter` 为纯渲染用例。

GUI 截图验证：`java -cp ... zhisuan.UiShot <输出目录>`。

---

## 安全提示

- 服务层默认仅监听 `127.0.0.1`，不建议直接暴露到公网。
- `server/dft_config.json` 中的 `g16_command`、`orca_command` 等路径会被本地 shell 执行，请确保路径可信。
- SSH 密码与云端 API Key 仅保存在本机配置文件中，不随仓库分发。

---

## 第三方组件

| 组件 | 用途 | 许可证 |
| --- | --- | --- |
| [JLaTeXMath 1.0.7](https://github.com/opencollab/jlatexmath) | 对话页 LaTeX 公式渲染（`ui-java/lib/`） | GPL-2.0-or-later，独立分发，不受本仓库许可证覆盖 |
| RDKit / NumPy / pandas / Matplotlib / paramiko / scp / ollama / cclib | Python 依赖，由 pip 安装 | 各自许可证 |

---

## 许可证

本仓库源代码采用 [Apache License 2.0](LICENSE)。`skills/` 下的知识库文档建议以 CC BY 4.0 方式引用。
