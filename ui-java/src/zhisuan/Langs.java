package zhisuan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三语言资源（中文 / English / Deutsch）。
 * 语言切换：Langs.setLang(...) 触发所有监听器（主框架刷新导航/顶栏/状态栏，
 * 各页面在 onShow() 时按当前语言渲染）。
 */
public final class Langs {
    public static final String ZH = "zh";
    public static final String EN = "en";
    public static final String DE = "de";
    public static final String[] LANGS = {ZH, EN, DE};

    private static String current = ZH;
    private static final List<Runnable> listeners = new ArrayList<>();
    private static final Map<String, Map<String, String>> T = new LinkedHashMap<>();

    static {
        Map<String, String> zh = new LinkedHashMap<>();
        zh.put("app.title", "智算铝宾 · 三语言智能体工作台");
        zh.put("brand.sub", "/ AL-AGENT");
        zh.put("nav.chat", "AI 助手");
        zh.put("nav.audit", "审查台");
        zh.put("nav.dispatch", "调度中心");
        zh.put("nav.starmap", "知识星图");
        zh.put("nav.spectrum", "光谱分析");
        zh.put("nav.optimize", "构型优化");
        zh.put("nav.jobs", "计算任务");
        zh.put("nav.library", "化合物库");
        zh.put("nav.mol", "三维结构");
        zh.put("nav.knowledge", "知识库");
        zh.put("nav.memory", "记忆中心");
        zh.put("nav.workflow", "工作流");
        zh.put("nav.settings", "设置与状态");
        zh.put("top.model", "模型");
        zh.put("top.local", "本地");
        zh.put("top.offline", "· 离线优先");
        zh.put("top.lang", "语言");
        zh.put("sys.prompt", "你是智算铝宾化学研究助手，请用中文回答。");
        zh.put("status.connecting", "正在连接服务层…");
        zh.put("status.connected", "已连接");
        zh.put("status.connFmt", "已连接 %s · 模型 %s%s · 化合物 %d · 技能 %d · C++核心 %s");
        zh.put("status.notready", "(未下载)");
        zh.put("status.ready", "(就绪)");
        zh.put("status.missing", "缺失");
        zh.put("status.offline", "服务未连接（%s）— 请运行 start_zhisuan.bat 或 agent_server.py");
        zh.put("status.cpp", "C++ 核心");
        zh.put("status.datasec", "◉ 数据不出本机");
        zh.put("footer.agent", "代理输出");
        zh.put("footer.term", "终端");
        zh.put("footer.panel", "面板操作");
        zh.put("footer.ready", "[AL-AGENT] 就绪 · 三语言工作台启动");
        zh.put("chat.send", "发 送");
        zh.put("chat.busy", "响应中…");
        zh.put("chat.clear", "清空对话");
        zh.put("chat.agent", "智能体模式");
        zh.put("chat.hint", "输入消息后回车发送 · Ctrl+Enter 换行 · 回复支持 Markdown");
        zh.put("chat.attach", "上传 / 加载文件");
        zh.put("chat.attachHint", "可附加本地文件，路径会插入输入框后随消息发送");
        zh.put("chat.welcome", "欢迎使用智算铝宾智能体工作台。\n"
                + "· 普通模式：直接与 qwen 大模型对话（Markdown 渲染）\n"
                + "· 智能体模式：模型可调用 23 个研究工具（化合物查询 / DFT 计算（含 SSH 集群异步任务）/ MDP 结构优化 / 3D 可视化 / 知识库检索…）\n"
                + "试试：「查询芳基咔唑铝宾 (10) 并优化它的结构」「异步提交阿司匹林的单点能计算」「分析阿司匹林的分子性质」\n\n");
        zh.put("chat.you", "你：");
        zh.put("chat.open", "打开");
        zh.put("chat.openDir", "所在目录");
        zh.put("chat.taskRunning", "后台任务运行中…");
        zh.put("chat.taskSubmitted", "后台任务已提交");
        zh.put("chat.attachTitle", "选择要附加的文件");
        zh.put("chat.agentModeTip", "开启后模型可调用 23 个工具：查询化合物、DFT 计算（含异步提交）、MDP 优化、3D 可视化等");
        zh.put("chat.fileMissing", "文件不存在: %s");
        zh.put("chat.openFail", "打开失败: %s");
        zh.put("chat.agentStart", "⚙ 已进入智能体模式，开始推理…\n");
        zh.put("chat.assistant", "● 助手：");
        zh.put("chat.tool", "🔧 调用工具 ");
        zh.put("chat.param", "参数: ");
        zh.put("chat.result", "结果: ");
        zh.put("chat.taskName", "任务 %s");
        zh.put("chat.connFail", "服务连接失败（已重试 %d 次）…");
        zh.put("chat.runningWith", "运行中（已 %s）");
        zh.put("chat.noOutput", "（无输出）");
        zh.put("chat.elapsedSec", "%d 秒");
        zh.put("chat.elapsedMin", "%d 分 %d 秒");
        zh.put("chat.sysDone", "[系统·后台任务完成] 任务 %s");
        zh.put("chat.sysFail", "[系统·后台任务失败] 任务 %s");
        zh.put("chat.rsmOkCtrl", "请基于该结果继续完成之前的任务，并给出最终结论。");
        zh.put("chat.rsmFailCtrl", "请向用户说明任务失败的原因，并给出排查建议（如检查设置页的 DFT 后端配置）。");
        zh.put("chat.rsmOkTitle", "后台任务完成，自动恢复对话");
        zh.put("chat.rsmFailTitle", "后台任务失败，自动恢复对话");
        zh.put("chat.rsmOkHint", "结果已注入上下文，模型继续推理…");
        zh.put("chat.rsmFailHint", "错误详情已注入上下文");
        zh.put("chat.clearDone", "对话已清空。");

        // ---- 审查台（方案九）----
        zh.put("audit.title", "结果审查台");
        zh.put("audit.sub", "4 项待审 · 今日已采纳 %d 项");
        zh.put("audit.queue", "待审队列");
        zh.put("audit.pending", "待审");
        zh.put("audit.source", "判定来源");
        zh.put("audit.evidence", "证据链");
        zh.put("audit.similar", "相似历史案例");
        zh.put("audit.accept", "采纳结果");
        zh.put("audit.rerun", "修改参数重跑");
        zh.put("audit.human", "转人工复核");
        zh.put("audit.acceptHint", "采纳后结果将写入知识库，供后续相似案例检索");
        zh.put("audit.engine.laya", "Laya 决策头");
        zh.put("audit.engine.qwen", "qwen3.5:2b");
        zh.put("audit.engine.rules", "确定性规则");
        zh.put("audit.adopted", "已采用");
        zh.put("audit.skipped", "已跳过");
        zh.put("audit.keep", "KEEP");
        zh.put("audit.keepHint", "结果可信，可采纳");
        zh.put("audit.rerunV", "RERUN");
        zh.put("audit.rerunHint", "需重跑");
        zh.put("audit.unknown", "未知");
        zh.put("audit.unknownHint", "判定结果未知，请展开日志核查");
        zh.put("audit.empty", "暂无待审任务");
        zh.put("audit.tool", "分诊引擎");
        zh.put("audit.noResult", "该任务无可展示的诊断结果（点右上角刷新）");
        zh.put("audit.diagTip", "任务完成 → 结果审查台自动展示 KEEP/RERUN 判定与证据链");
        zh.put("audit.verdict", "判定");
        zh.put("audit.reason", "原因");
        zh.put("audit.metrics", "指标");
        zh.put("audit.basic", "任务信息");
        zh.put("audit.params", "参数");
        zh.put("audit.refresh", "刷新");
        zh.put("audit.noLog", "未发现可诊断的 .log/.out 日志，仅展示原始结果");
        zh.put("audit.loading", "正在分诊…");
        zh.put("audit.diagFail", "分诊失败：%s");
        zh.put("audit.acceptOk", "已采纳（写入知识库）");
        zh.put("audit.skipOk", "已转人工复核");
        zh.put("audit.rerunTitle", "修改参数重跑");
        zh.put("audit.rerunMsg", "修改 JSON 参数后提交（工具：%s）");
        zh.put("audit.submit", "提 交");
        zh.put("audit.cancel", "取 消");
        zh.put("audit.none", "无");
        zh.put("audit.simScore", "相似度 %.2f");
        zh.put("audit.engine", "判定来源");

        // ---- 调度中心（方案七）----
        zh.put("disp.title", "计算调度中心");
        zh.put("disp.sync", "%d 秒前同步 · %d 个后端");
        zh.put("disp.g16", "本地 Gaussian16");
        zh.put("disp.orca", "本地 ORCA");
        zh.put("disp.cluster", "SSH 集群 G16");
        zh.put("disp.idle", "空闲 · %d 核可用");
        zh.put("disp.running", "运行中 %d / %d 作业");
        zh.put("disp.online", "在线 · 延迟 %d ms");
        zh.put("disp.down", "离线");
        zh.put("disp.queued", "排队");
        zh.put("disp.calc", "计算中");
        zh.put("disp.transfer", "回传");
        zh.put("disp.review", "待解读");
        zh.put("disp.doneBanner", "%s 已完成，Agent 已自动恢复对应会话并生成解读。另有 %d 个作业等待你确认参数。");
        zh.put("disp.backToChat", "回到对话");
        zh.put("disp.empty", "（空）");
        zh.put("disp.download", "下载 .log %.1f / %.1f MB");
        zh.put("disp.license", "许可证剩余 %d 天");
        zh.put("disp.queuelen", "队列等待约 %d 分钟");
        zh.put("disp.newJob", "新建 DFT 任务");
        zh.put("disp.failed", "失败");
        zh.put("disp.interrupted", "中断");
        zh.put("disp.unconfigured", "未配置 SSH 连接");

        // ---- 化合物库 ----
        zh.put("library.title", "化合物库");
        zh.put("library.sub", "  /library · A1–A4 /*");
        zh.put("library.import", "导入 .mol / SMILES");
        zh.put("library.add", "＋ 新建");
        zh.put("library.loaded", "已加载 %s 到工作台");
        zh.put("library.saved", "已保存到化合物库 · 编号 71");
        zh.put("library.parsed", "已解析 SMILES · 家族 A1");
        zh.put("library.detail", "结构详情 · %s");
        zh.put("library.family", "家族 %s  |  SMILES: %s");
        zh.put("library.loading", "加载中…");
        zh.put("library.detailHint", "点击列表任意卡片可打开结构式详情");
        zh.put("library.fail", "× 加载失败");
        zh.put("library.lookup", "输入名称或结构式检索，或选择左侧家族筛选");
        zh.put("library.backend", "后端库");
        zh.put("library.multiMatch", "%d 条匹配（缓存 %d）");
        zh.put("library.cached", "%d 条缓存");
        zh.put("library.backendTooltip", "从 Python 服务层动态拉取铝宾化合物");
        zh.put("library.relayout", "重载");
        zh.put("library.fAll", "全部");
        zh.put("library.fAllTooltip", "显示全部化合物");

        // ---- 构型优化 ----
        zh.put("opt.title", "分子优化（C++ 力场 + MDP 强化学习核心 molopt.exe）");
        zh.put("opt.lblSmiles", "SMILES / 名称:");
        zh.put("opt.lblEp", "训练轮数:");
        zh.put("opt.lblSteps", "每轮步数:");
        zh.put("opt.examples", "快速尝试:");
        zh.put("opt.pick", "化合物库");
        zh.put("opt.run", "开始优化");
        zh.put("opt.viz", "3D 可视化");
        zh.put("opt.prop", "性质分析");
        zh.put("opt.statusIdle", "就绪 — 输入 SMILES 或从化合物库选择");
        zh.put("opt.msgEnter", "请先输入 SMILES 或名称");
        zh.put("opt.result", "优化结果");
        zh.put("opt.propCard", "分子性质（RDKit）");
        zh.put("opt.rowCore", "计算核心");
        zh.put("opt.rowEnergy", "总能量");
        zh.put("opt.rowConstraint", "约束能量");
        zh.put("opt.rowViolations", "违规数");
        zh.put("opt.rowValid", "结构有效");
        zh.put("opt.rowEpisodes", "训练轮数");
        zh.put("opt.rowElapsed", "核心耗时");
        zh.put("opt.rowFormula", "分子式");
        zh.put("opt.rowMw", "精确分子量");
        zh.put("opt.rowHbd", "氢键供体");
        zh.put("opt.rowHba", "氢键受体");
        zh.put("opt.rowRot", "可旋转键");
        zh.put("opt.rowHeavy", "重原子数");
        zh.put("opt.search", "搜索:");
        zh.put("opt.ok", "选 择");
        zh.put("opt.cancel", "取 消");

        // ---- 光谱分析 ----
        zh.put("spec.title", "量子化学输出解析（cclib）");
        zh.put("spec.lblFile", "输出文件:");
        zh.put("spec.browse", "浏览…");
        zh.put("spec.parse", "解 析");
        zh.put("spec.demo", "示例数据");
        zh.put("spec.sumAtoms", "原子数: —");
        zh.put("spec.sumScf", "SCF 能量: —");
        zh.put("spec.sumGap", "HOMO-LUMO 间隙: —");
        zh.put("spec.sumImag", "虚频数: —");
        zh.put("spec.tabMo", "分子轨道能级");
        zh.put("spec.tabIr", "红外光谱");
        zh.put("spec.tab3d", "3D 结构 / FMO");
        zh.put("spec.title3d", "3D 结构渲染 — molview.exe（FMO / 电子云等值面）");
        zh.put("spec.info", "解析信息");
        zh.put("spec.render", "渲染 3D 结构");
        zh.put("spec.renderStatus", "尚未渲染 — 解析文件后可点击「渲染 3D 结构」启动查看器");
        zh.put("spec.logInit", "解析 Gaussian/ORCA 输出文件（.log/.out），或点击「示例数据」体验。");
        zh.put("spec.moatoms", "原子数: %s");
        zh.put("spec.moscf", "SCF 能量: %s");
        zh.put("spec.mogap", "HOMO-LUMO 间隙: %s");
        zh.put("spec.moimag", "虚频数: %d");
        zh.put("spec.noData", "尚无数据 — 解析输出文件或点击「示例数据」");
        zh.put("spec.noIr", "尚无数据 — 需含频率计算的输出文件（freq 任务）");
        zh.put("spec.no3d", "暂无结构 — 解析输出文件后在此显示球棍模型预览");
        zh.put("spec.title3dHint", "解析完成后点击「渲染 3D 结构」：生成 <b>&lt;结果名&gt;_view.xyz</b>，"
                + "并自动发现同目录 FMO / 电子云等值面数据启动 3D 查看器。<br>"
                + "配色：HOMO⁺ 蓝 · HOMO⁻ 红 · LUMO⁺ 橙 · LUMO⁻ 紫 · 电子云 青绿（半透明）。<br>"
                + "快捷键：B=切换FMO D=切换电子云 R=复位 S=截图 1/2/3=球棍/比例/线框；鼠标拖动旋转、滚轮缩放。");
        zh.put("spec.renderSelect", "请先选择输出文件并解析。");
        zh.put("spec.renderWorking", "正在生成 XYZ 与 FMO/电子云等值面（Multiwfn 计算中，可能需要 1–2 分钟）…");
        zh.put("spec.renderDone", "已启动 %s（%d 原子，发现 %d 个等值面：FMO+电子云）");
        zh.put("spec.renderFail", "渲染失败: %s");
        zh.put("spec.viewer3d", "3D 查看器");
        zh.put("spec.viewerOther", "渲染器");
        zh.put("spec.selectParse", "请先选择输出文件。");
        zh.put("spec.parsing", "正在解析 %s …");
        zh.put("spec.parseFail", "解析失败: %s");
        zh.put("spec.homo", "HOMO（最高占据）");
        zh.put("spec.lumo", "LUMO（最低空轨道）");
        zh.put("spec.legendOcc", "占据轨道（实线）/ 空轨道（虚线）");
        zh.put("spec.wavenum", "波数 (cm⁻¹)");
        zh.put("spec.intensity", "强度");
        zh.put("spec.imagTag", "虚频!");
        zh.put("spec.molHint", "%d 原子 · %d 键 · 拖动旋转 / 滚轮缩放");
        zh.put("spec.demoTitle", "示例数据（模拟有机分子，类阿司匹林骨架）：");

        // ---- 分子优化 ----
        zh.put("opt.title", "分子优化（C++ 力场 + MDP 强化学习核心 molopt.exe）");
        zh.put("opt.lblSmiles", "SMILES / 名称:");
        zh.put("opt.smilesHint", "可直接输入 SMILES（如 CCO）或中文名称（如 铝宾 (10)），服务端会自动解析");
        zh.put("opt.episodes", "训练轮数:");
        zh.put("opt.steps", "每轮步数:");
        zh.put("opt.pick", "化合物库");
        zh.put("opt.run", "开始优化");
        zh.put("opt.viz", "3D 可视化");
        zh.put("opt.props", "性质分析");
        zh.put("opt.ready", "就绪 — 输入 SMILES 或从化合物库选择");
        zh.put("opt.quickTry", "快速尝试:");
        zh.put("opt.resTitle", "优化结果");
        zh.put("opt.rBackend", "计算核心");
        zh.put("opt.rEnergy", "总能量");
        zh.put("opt.rConstraint", "约束能量");
        zh.put("opt.rViolations", "违规数");
        zh.put("opt.rValid", "结构有效");
        zh.put("opt.rEpisodes", "训练轮数");
        zh.put("opt.rElapsed", "核心耗时");
        zh.put("opt.cpp", "C++ (molopt.exe)");
        zh.put("opt.py", "Python (回退)");
        zh.put("opt.yes", "✔ 是");
        zh.put("opt.no", "✘ 否");
        zh.put("opt.pTitle", "分子性质（RDKit）");
        zh.put("opt.pFormula", "分子式");
        zh.put("opt.pMw", "精确分子量");
        zh.put("opt.pLogp", "LogP");
        zh.put("opt.pHbd", "氢键供体");
        zh.put("opt.pHba", "氢键受体");
        zh.put("opt.pTpsa", "TPSA (Å²)");
        zh.put("opt.pRot", "可旋转键");
        zh.put("opt.pHeavy", "重原子数");
        zh.put("opt.noInput", "请先输入 SMILES 或名称");
        zh.put("opt.busyRun", "正在优化 %s …");
        zh.put("opt.doneRun", "优化完成 — 可点击 3D 可视化查看结构");
        zh.put("opt.failRun", "优化失败: %s");
        zh.put("opt.busyViz", "正在生成 3D 结构并启动 molview.exe…");
        zh.put("opt.doneViz", "已打开 3D 结构（%s）· %d 原子 / %d 键");
        zh.put("opt.viewerNative", "原生查看器 molview.exe");
        zh.put("opt.viewerWeb", "浏览器");
        zh.put("opt.failViz", "可视化失败: %s");
        zh.put("opt.busyProps", "正在计算分子性质…");
        zh.put("opt.doneProps", "性质计算完成");
        zh.put("opt.failProps", "性质计算失败: %s");
        zh.put("opt.loadingLib", "正在加载化合物库…");
        zh.put("opt.libFail", "加载化合物库失败: %s");
        zh.put("opt.chosen", "已选择化合物");
        zh.put("opt.dlgTitle", "化合物库（%d 个）");
        zh.put("opt.dlgSearch", "搜索:");
        zh.put("opt.dlgSearchHint", "输入名称或 SMILES 片段过滤");
        zh.put("opt.ok", "选 择");
        zh.put("opt.cancel", "取 消");

        // ---- 设置与状态 ----
        zh.put("set.refresh", "刷新状态");
        zh.put("set.saveUrl", "保存地址");
        zh.put("set.healthTitle", "组件健康状态");
        zh.put("set.checking", "检测中…");
        zh.put("set.ok", "● 正常");
        zh.put("set.bad", "✘ 异常");
        zh.put("set.unreach", "✘ 不可达");
        zh.put("set.connTitle", "服务连接");
        zh.put("set.url", "服务地址:");
        zh.put("set.dftTitle", "DFT 计算后端配置（Gaussian / ORCA / Multiwfn / SSH 集群 / 工作区）");
        zh.put("set.g16", "Gaussian (g16):");
        zh.put("set.orca", "ORCA:");
        zh.put("set.multiwfn", "Multiwfn:");
        zh.put("set.workspace", "工作区目录:");
        zh.put("set.pathHint", "留空则使用 PATH 中的默认命令");
        zh.put("set.browse", "浏览…");
        zh.put("set.test", "测 试");
        zh.put("set.defaultBackend", "默认计算后端:");
        zh.put("set.sshHint2", "本机未装 Gaussian/ORCA 时，可配置 SSH 集群后端");
        zh.put("set.sshTitle", "SSH 集群（远程 Gaussian16 · PBS/Slurm）");
        zh.put("set.sshHost", "主机:");
        zh.put("set.sshUser", "用户名:");
        zh.put("set.sshPass", "密码:");
        zh.put("set.sshKey", "密钥文件:");
        zh.put("set.sshDir", "远程目录:");
        zh.put("set.sshQueue", "队列系统:");
        zh.put("set.sshQueueName", "队列名称:");
        zh.put("set.sshG16Root", "G16 安装目录:");
        zh.put("set.sshScratch", "集群 SCRATCH:");
        zh.put("set.sshTest", "测试 SSH 连接");
        zh.put("set.sshHint", "填写主机与用户名并保存后即可测试连接");
        zh.put("set.modelTitle", "大模型分层配置（本地优先 · 可选混合/云端）");
        zh.put("set.modelBackend", "模型后端:");
        zh.put("set.localModel", "本地模型:");
        zh.put("set.cloudUrl", "云端Base URL:");
        zh.put("set.cloudKey", "云端API Key:");
        zh.put("set.cloudModel", "云端模型:");
        zh.put("set.cloudOnlyAuth", "仅授权后生效");
        zh.put("set.cloudConsent", "允许数据出机（以下云端调用才生效）");
        zh.put("set.cloudTsAuto", "混合模式下，复杂 TS/扫描类任务自动升级云端");
        zh.put("set.refreshModels", "刷新本地模型");
        zh.put("set.saveModelCfg", "保存并应用模型配置");
        zh.put("set.saveDftCfg", "保存并应用配置");
        zh.put("set.modelLocal", "本地 (local) · 数据不出机（默认）");
        zh.put("set.modelHybrid", "混合 (hybrid) · 本地+复杂任务自动升云端");
        zh.put("set.modelCloud", "云端 (cloud)");
        zh.put("set.aboutTitle", "关于 · 三语言架构");
        zh.put("set.modelHint", "config: local 数据不出机（默认）· hybrid 复杂TS自动升云端 · cloud 全云端");
        zh.put("set.backendHint", "dft_single_point / dft_optimize / dft_frequency 等工具使用的默认后端");
        zh.put("set.chooseWs", "选择工作区目录");
        zh.put("set.chooseProg", "选择可执行程序");
        zh.put("set.cfgPath", "配置文件: %s · 保存后立即生效，无需重启服务层");
        zh.put("set.liveBackends", "当前可用后端: %s · 默认: %s");
        zh.put("set.savingDft", "正在保存并应用配置…");
        zh.put("set.savedDft", "✔ 配置已保存并热应用 · 可用后端: %s · 默认: %s");
        zh.put("set.savedFallback", "（所选默认后端未就绪，已回退，请检查对应程序或 SSH 配置）");
        zh.put("set.saveDftFail", "✘ 保存失败: %s");
        zh.put("set.testing", "正在测试 %s …");
        zh.put("set.testFail", "✘ 测试请求失败: %s");
        zh.put("set.theServer", "Python 服务层 (agent_server.py)");
        zh.put("set.ollama", "Ollama 服务");
        zh.put("set.modelReady", "大模型就绪");
        zh.put("set.molopt", "C++ 优化核心 (molopt.exe)");
        zh.put("set.molview", "C++ 分子查看器 (molview.exe)");
        zh.put("set.rdkit", "RDKit 化学库");
        zh.put("set.kb", "知识库");
        zh.put("set.dft", "DFT 后端");
        zh.put("set.workflow", "工作流引擎");
        zh.put("set.localNoModels", "本地 Ollama 无已下载模型");
        zh.put("set.downloaded", "已下载: %s");
        zh.put("set.savingModel", "正在保存并应用模型配置…");
        zh.put("set.savedModel", "✔ 已保存并热应用 · 后端: %s（%s）");
        zh.put("set.saveModelFail", "✘ 保存失败: %s");
        zh.put("set.currentBackend", "当前后端: %s（%s）");
        zh.put("set.dataLocal", "数据不出机");
        zh.put("set.dataCloud", "云端/混合，请确认已授权数据出机");
        zh.put("set.aboutHtml", "<b>智算铝宾智能体</b> — 铝宾（Aluminylene）化学研究平台<br>"
                + "· <b>Python 服务层</b> agent_server.py：ReAct 智能体循环（qwen3.5:2b）+ 22 个研究工具 + HTTP/SSE 接口<br>"
                + "· <b>Java 图形界面</b>（本程序）：Swing 工作台 — AI 助手 / 分子优化 / 知识星图 / 谱分析 / 知识库<br>"
                + "· <b>C++ 计算核心</b>：molopt.exe 力场+MDP+Q-Learning 优化引擎（较 Python 提速 60~1300×）；molview.exe OpenGL 3D 分子查看器<br>"
                + "· <b>量子化学后端</b>：Gaussian16 / ORCA / Multiwfn（本页配置，支持 SSH 集群提交）<br>"
                + "· 本地化合物库 67 条（含 52 个铝宾结构）· 技能知识库 19 篇<br>"
                + "启动顺序：start_zhisuan.bat → Python 服务层 → Java GUI（自动检测并连接）");

        // ---- 知识星图 ----
        zh.put("starmap.hint", "点击星体查看详情");
        zh.put("starmap.desc", "从服务层加载知识库后，按分类形成星座。\n拖拽星体可调整布局，点击星体查看技能详情。");
        zh.put("starmap.full", "查看全文");
        zh.put("starmap.loading", "加载中…");
        zh.put("starmap.reload", "重新加载");
        zh.put("starmap.relayout", "重新布局");
        zh.put("starmap.info", "星体 × %d · 星座 × %d · 星链 × %d");
        zh.put("starmap.cat", "分类: %s%s");
        zh.put("starmap.catHub", "（星座枢纽）");
        zh.put("starmap.tags", "标签: %s");
        zh.put("starmap.noTags", "标签: —");
        zh.put("starmap.noDesc", "（无描述，点击「查看全文」读取完整技能文档）");
        zh.put("starmap.none", "分类: —\n标签: —\n\n点击星体查看技能详情。");
        zh.put("starmap.legend", "★ 分类星座   ● 技能星体   ─ 星链(共享标签)");

        // ---- 知识库 ----
        zh.put("kb.left", "技能列表");
        zh.put("kb.right", "技能详情");
        zh.put("kb.search", "搜索:");
        zh.put("kb.category", "分类:");
        zh.put("kb.all", "全部分类");
        zh.put("kb.hint", "选择左侧技能查看内容");
        zh.put("kb.noMatch", "无匹配技能");
        zh.put("kb.meta", "分类: %s · 标签: %s · %d 字");
        zh.put("kb.loadFail", "加载失败: %s");
        zh.put("kb.searchHint", "按名称/标题/描述/标签过滤");

        // ---- 记忆中心 ----
        zh.put("mem.title", "记忆中心");
        zh.put("mem.sub", "  /memory · long-term · atomic facts");
        zh.put("mem.search", "检索记忆 ⌕");
        zh.put("mem.extract", "提取会话事实");
        zh.put("mem.statusIdle", "bigram-Dice 相关性检索 · 阈值 0.12");
        zh.put("mem.searching", "检索中…（bigram-Dice）");
        zh.put("mem.hits3", "命中 3 条 · 已按相关性排序");
        zh.put("mem.extracted1", "已提取 1 条原子事实");
        zh.put("mem.extracted", "任务/光谱 → 解读条目沉淀 · 新提取");
        zh.put("mem.blockQuiz", "决策记忆");
        zh.put("mem.blockFacts", "会话事实");

        // ---- 三维结构 ----
        zh.put("mol.title", "三维结构");
        zh.put("mol.sub", "  /crn3d · C++ OpenGL");
        zh.put("mol.pickInfo", "拖动旋转 · 滚轮缩放 · 点击原子拾取");
        zh.put("mol.reset", "↺ 重置视角");
        zh.put("mol.wire", "◐ 线框");
        zh.put("mol.export", "截图导出");
        zh.put("mol.exportMsg", "视角已导出 workspace/shot3d.png");
        zh.put("mol.infoCenter", "Al 中心");
        zh.put("mol.infoCoord", "坐标");
        zh.put("mol.infoCalc", "计算");
        zh.put("mol.picked", "拾取：%s · 已展示分子信息卡");
        zh.put("mol.clickHint", "点击原子获得概览");

        // ---- 计算任务 ----
        zh.put("jobs.new", "＋ 新建 DFT 任务");
        zh.put("jobs.cluster", "集群");
        zh.put("jobs.batch", "批量提交");
        zh.put("jobs.upload", "上传结构");
        zh.put("jobs.header", "OUTPUT · 调度日志");
        zh.put("jobs.colJob", "JOB");
        zh.put("jobs.colName", "名称");
        zh.put("jobs.colBackend", "后端");
        zh.put("jobs.colQueue", "队列");
        zh.put("jobs.colStatus", "状态");
        zh.put("jobs.colProgress", "进度");
        zh.put("jobs.stRunning", "运行中");
        zh.put("jobs.stQueued", "排队中");
        zh.put("jobs.stDone", "完成");

        // ---- 设置与状态 ----
        zh.put("set.health", "组件健康状态");
        zh.put("set.conn", "服务连接");
        zh.put("set.connUrl", "服务地址:");
        zh.put("set.dft", "DFT 计算后端配置（Gaussian / ORCA / Multiwfn / SSH 集群 / 工作区）");
        zh.put("set.backend", "默认计算后端:");
        zh.put("set.about", "关于 · 三语言架构");
        zh.put("set.refresh", "刷新状态");
        zh.put("set.saveUrl", "保存地址");
        zh.put("set.browse", "浏览…");
        zh.put("set.test", "测 试");
        zh.put("set.version", "版本");
        zh.put("set.refreshModels", "刷新本地模型");
        zh.put("set.models", "大模型分层配置（本地优先 · 可选混合/云端）");
        zh.put("set.checking", "检测中…");
        zh.put("set.normal", "● 正常");
        zh.put("set.abnormal", "✘ 异常");
        zh.put("set.version", "版本");
        zh.put("set.backendLocalG16", "本地 Gaussian16 (local_g16)");
        zh.put("set.backendLocalOrca", "本地 ORCA (local_orca)");
        zh.put("set.backendCluster", "SSH 集群 Gaussian16 (cluster_g16)");
        zh.put("set.sshTitle", "SSH 集群（远程 Gaussian16 · PBS/Slurm）");
        zh.put("set.workDir", "工作区目录:");
        zh.put("set.host", "主机:");
        zh.put("set.user", "用户名:");
        zh.put("set.pass", "密码:");
        zh.put("set.keyfile", "密钥文件:");
        zh.put("set.remoteDir", "远程目录:");
        zh.put("set.queueSys", "队列系统:");
        zh.put("set.queueName", "队列名称:");
        zh.put("set.g16Root", "G16 安装目录:");
        zh.put("set.scratch", "集群 SCRATCH:");
        zh.put("set.testSsh", "测试 SSH 连接");
        zh.put("set.modelBackend", "模型后端:");
        zh.put("set.localModel", "本地模型:");
        zh.put("set.cloudUrl", "云端Base URL:");
        zh.put("set.cloudKey", "云端API Key:");
        zh.put("set.cloudModel", "云端模型:");

        // ---- 工作流 ----
        zh.put("workflow.fit", "适应视图");
        zh.put("workflow.zoomIn", "放大");
        zh.put("workflow.zoomOut", "缩小");
        zh.put("workflow.online", "● 已连接 %s");
        zh.put("workflow.offline", "○ 离线 %s");

        Map<String, String> en = new LinkedHashMap<>();
        en.put("app.title", "Aluminylene Agent · Trilingual Workbench");
        en.put("brand.sub", "/ AL-AGENT");
        en.put("nav.chat", "AI Assistant");
        en.put("nav.audit", "Audit Desk");
        en.put("nav.dispatch", "Dispatch");
        en.put("nav.starmap", "Star Map");
        en.put("nav.spectrum", "Spectra");
        en.put("nav.optimize", "Optimize");
        en.put("nav.jobs", "Compute Jobs");
        en.put("nav.library", "Compounds");
        en.put("nav.mol", "3D View");
        en.put("nav.knowledge", "Knowledge");
        en.put("nav.memory", "Memory");
        en.put("nav.workflow", "Workflow");
        en.put("nav.settings", "Settings");
        en.put("top.model", "Model");
        en.put("top.local", "Local");
        en.put("top.offline", "· Offline-first");
        en.put("top.lang", "Language");
        en.put("sys.prompt", "You are the Aluminylene chemistry research assistant. Please respond in English.");
        en.put("status.connecting", "Connecting to server…");
        en.put("status.connected", "Connected");
        en.put("status.connFmt", "Connected %s · model %s%s · compounds %d · skills %d · C++ core %s");
        en.put("status.notready", "(not ready)");
        en.put("status.ready", "(ready)");
        en.put("status.missing", "missing");
        en.put("status.offline", "Server unreachable (%s) — run start_zhisuan.bat or agent_server.py");
        en.put("status.cpp", "C++ core");
        en.put("status.datasec", "◉ Data stays local");
        en.put("footer.agent", "Agent Output");
        en.put("footer.term", "Terminal");
        en.put("footer.panel", "Panel actions");
        en.put("footer.ready", "[AL-AGENT] Ready · trilingual workbench started");
        en.put("chat.send", "Send");
        en.put("chat.busy", "Responding…");
        en.put("chat.clear", "Clear");
        en.put("chat.agent", "Agent mode");
        en.put("chat.hint", "Enter to send · Ctrl+Enter for newline · Markdown supported");
        en.put("chat.attach", "Attach / Load");
        en.put("chat.attachHint", "Attach a local file; its path is inserted into the input box");
        en.put("chat.welcome", "Welcome to the Zhisuan Aluminylene Agent workbench.\n"
                + "· Normal mode: talk directly to the qwen model (Markdown rendering)\n"
                + "· Agent mode: the model can call 23 research tools (compound lookup / DFT calc (incl. SSH async submit) / MDP optimize / 3D view / knowledge-base search…)\n"
                + "Try: \"look up aryl-carbazolyl aluminylene (10) and optimize it\" · \"submit aspirin single-point energy asynchronously\" · \"analyze aspirin's molecular properties\"\n\n");
        en.put("chat.you", "You:");
        en.put("chat.open", "Open");
        en.put("chat.openDir", "Open folder");
        en.put("chat.taskRunning", "Background task running…");
        en.put("chat.taskSubmitted", "Background task submitted");
        en.put("chat.attachTitle", "Select files to attach");
        en.put("chat.agentModeTip", "When enabled the model can call 23 tools: compound lookup, DFT calc (incl. async submit), MDP optimize, 3D view, etc.");
        en.put("chat.fileMissing", "File not found: %s");
        en.put("chat.openFail", "Open failed: %s");
        en.put("chat.agentStart", "⚙ Entered agent mode, reasoning…\n");
        en.put("chat.assistant", "● Assistant:");
        en.put("chat.tool", "🔧 Tool call ");
        en.put("chat.param", "Args: ");
        en.put("chat.result", "Result: ");
        en.put("chat.taskName", "Task %s");
        en.put("chat.connFail", "Connection failed (retried %d times)…");
        en.put("chat.runningWith", "Running (%s)");
        en.put("chat.noOutput", "(no output)");
        en.put("chat.elapsedSec", "%d s");
        en.put("chat.elapsedMin", "%d m %d s");
        en.put("chat.sysDone", "[System · task done] task %s");
        en.put("chat.sysFail", "[System · task failed] task %s");
        en.put("chat.rsmOkCtrl", "Based on this result, continue the previous task and give a final conclusion.");
        en.put("chat.rsmFailCtrl", "Explain to the user why the task failed and suggest next steps (e.g. check the DFT backend config in Settings).");
        en.put("chat.rsmOkTitle", "Background task done, resuming conversation");
        en.put("chat.rsmFailTitle", "Background task failed, resuming conversation");
        en.put("chat.rsmOkHint", "Result injected into context, model keeps reasoning…");
        en.put("chat.rsmFailHint", "Error details injected into context");
        en.put("chat.clearDone", "Conversation cleared.");

        en.put("audit.title", "Result Audit Desk");
        en.put("audit.sub", "%d pending · %d accepted today");
        en.put("audit.queue", "Review Queue");
        en.put("audit.pending", "pending");
        en.put("audit.source", "Verdict Source");
        en.put("audit.evidence", "Evidence Chain");
        en.put("audit.similar", "Similar Past Cases");
        en.put("audit.accept", "Accept Result");
        en.put("audit.rerun", "Rerun with Changes");
        en.put("audit.human", "Manual Review");
        en.put("audit.acceptHint", "Accepted results are written to the knowledge base for future case retrieval");
        en.put("audit.engine.laya", "Laya Head");
        en.put("audit.engine.qwen", "qwen3.5:2b");
        en.put("audit.engine.rules", "Rule Engine");
        en.put("audit.adopted", "Adopted");
        en.put("audit.skipped", "Skipped");
        en.put("audit.keep", "KEEP");
        en.put("audit.keepHint", "Trustworthy, adopt");
        en.put("audit.rerunV", "RERUN");
        en.put("audit.rerunHint", "Rerun required");
        en.put("audit.unknown", "Unknown");
        en.put("audit.unknownHint", "Verdict unknown, inspect the log");
        en.put("audit.empty", "No pending tasks");
        en.put("audit.tool", "Triage engine");
        en.put("audit.noResult", "No diagnosis result to show for this task (refresh)");
        en.put("audit.diagTip", "On task completion the audit desk shows KEEP/RERUN verdict with evidence chain");
        en.put("audit.verdict", "Verdict");
        en.put("audit.reason", "Reason");
        en.put("audit.metrics", "Metrics");
        en.put("audit.basic", "Task Info");
        en.put("audit.params", "Params");
        en.put("audit.refresh", "Refresh");
        en.put("audit.noLog", "No .log/.out log found for diagnosis; showing raw result only");
        en.put("audit.loading", "Diagnosing…");
        en.put("audit.diagFail", "Diagnosis failed: %s");
        en.put("audit.acceptOk", "Adopted (written to knowledge base)");
        en.put("audit.skipOk", "Sent to manual review");
        en.put("audit.rerunTitle", "Rerun with Changes");
        en.put("audit.rerunMsg", "Edit JSON params and submit (tool: %s)");
        en.put("audit.submit", "Submit");
        en.put("audit.cancel", "Cancel");
        en.put("audit.none", "none");
        en.put("audit.simScore", "similarity %.2f");
        en.put("audit.engine", "Verdict Source");

        en.put("disp.title", "Compute Dispatch Center");
        en.put("disp.sync", "synced %d s ago · %d backends");
        en.put("disp.g16", "Local Gaussian16");
        en.put("disp.orca", "Local ORCA");
        en.put("disp.cluster", "SSH Cluster G16");
        en.put("disp.idle", "Idle · %d cores free");
        en.put("disp.running", "%d / %d jobs running");
        en.put("disp.online", "Online · %d ms latency");
        en.put("disp.down", "Offline");
        en.put("disp.queued", "Queued");
        en.put("disp.calc", "Running");
        en.put("disp.transfer", "Fetching");
        en.put("disp.review", "To Review");
        en.put("disp.doneBanner", "%s finished — Agent resumed the session and generated an interpretation. %d more job(s) await parameter confirmation.");
        en.put("disp.backToChat", "Back to Chat");
        en.put("disp.empty", "(empty)");
        en.put("disp.download", "download .log %.1f / %.1f MB");
        en.put("disp.license", "license expires in %d days");
        en.put("disp.queuelen", "queue wait ~%d min");
        en.put("disp.newJob", "New DFT Job");
        en.put("disp.failed", "Failed");
        en.put("disp.interrupted", "Interrupted");
        en.put("disp.unconfigured", "SSH not configured");

        // ---- Compound Library ----
        en.put("library.title", "Compound Library");
        en.put("library.sub", "  /library · A1–A4 /*");
        en.put("library.import", "Import .mol / SMILES");
        en.put("library.add", "＋ New");
        en.put("library.loaded", "Loaded %s to workbench");
        en.put("library.saved", "Saved to compound library · #71");
        en.put("library.parsed", "Parsed SMILES · family A1");
        en.put("library.detail", "Structure Details · %s");
        en.put("library.family", "Family %s  |  SMILES: %s");
        en.put("library.loading", "Loading…");
        en.put("library.detailHint", "Click any card to open the structure details");
        en.put("library.fail", "× load failed");
        en.put("library.lookup", "Search by name or SMILES, or filter by family on the left");
        en.put("library.backend", "Backend Library");
        en.put("library.multiMatch", "%d matches (cache %d)");
        en.put("library.cached", "%d cached");
        en.put("library.backendTooltip", "Dynamically fetched aluminylene compounds from the Python server");
        en.put("library.relayout", "Reload");
        en.put("library.fAll", "All");
        en.put("library.fAllTooltip", "Show all compounds");

        // ---- Optimize ----
        en.put("opt.title", "Molecular Optimization (C++ force field + MDP RL core molopt.exe)");
        en.put("opt.lblSmiles", "SMILES / Name:");
        en.put("opt.lblEp", "Episodes:");
        en.put("opt.lblSteps", "Steps/episode:");
        en.put("opt.examples", "Quick try:");
        en.put("opt.pick", "Compounds");
        en.put("opt.run", "Start");
        en.put("opt.viz", "3D View");
        en.put("opt.prop", "Properties");
        en.put("opt.statusIdle", "Ready — enter SMILES or pick from the compound library");
        en.put("opt.msgEnter", "Please enter a SMILES or name first");
        en.put("opt.result", "Optimization Result");
        en.put("opt.propCard", "Molecular Properties (RDKit)");
        en.put("opt.rowCore", "Engine");
        en.put("opt.rowEnergy", "Total Energy");
        en.put("opt.rowConstraint", "Constraint Energy");
        en.put("opt.rowViolations", "Violations");
        en.put("opt.rowValid", "Valid");
        en.put("opt.rowEpisodes", "Episodes");
        en.put("opt.rowElapsed", "Elapsed");
        en.put("opt.rowFormula", "Formula");
        en.put("opt.rowMw", "Exact Mass");
        en.put("opt.rowHbd", "H-bond Donors");
        en.put("opt.rowHba", "H-bond Acceptors");
        en.put("opt.rowRot", "Rotatable Bonds");
        en.put("opt.rowHeavy", "Heavy Atoms");
        en.put("opt.search", "Search:");
        en.put("opt.ok", "OK");
        en.put("opt.cancel", "Cancel");

        // ---- Spectrum ----
        en.put("spec.title", "Quantum Chemistry Output Parser (cclib)");
        en.put("spec.lblFile", "Output file:");
        en.put("spec.browse", "Browse…");
        en.put("spec.parse", "Parse");
        en.put("spec.demo", "Demo Data");
        en.put("spec.sumAtoms", "Atoms: —");
        en.put("spec.sumScf", "SCF Energy: —");
        en.put("spec.sumGap", "HOMO-LUMO Gap: —");
        en.put("spec.sumImag", "Imag. Freq: —");
        en.put("spec.tabMo", "MO Levels");
        en.put("spec.tabIr", "IR Spectrum");
        en.put("spec.tab3d", "3D / FMO");
        en.put("spec.title3d", "3D Rendering — molview.exe (FMO / electron-density isosurfaces)");
        en.put("spec.info", "Parse Log");
        en.put("spec.render", "Render 3D");
        en.put("spec.renderStatus", "Not rendered — after parsing you can click \"Render 3D\" to launch the viewer");
        en.put("spec.logInit", "Parse Gaussian/ORCA output files (.log/.out), or click \"Demo Data\" to try.");
        en.put("spec.moatoms", "Atoms: %s");
        en.put("spec.moscf", "SCF Energy: %s");
        en.put("spec.mogap", "HOMO-LUMO Gap: %s");
        en.put("spec.moimag", "Imag. Freq: %d");
        en.put("spec.noData", "No data — parse an output file or click \"Demo Data\"");
        en.put("spec.noIr", "No data — need an output file with freq calculation");
        en.put("spec.no3d", "No structure — this ball-and-stick preview appears after parsing");
        en.put("spec.title3dHint", "After parsing click \"Render 3D\": generates <b>&lt;result&gt;_view.xyz</b>, "
                + "auto-detects FMO / electron-density isosurfaces in the same folder and launches the 3D viewer.<br>"
                + "Colors: HOMO⁺ blue · HOMO⁻ red · LUMO⁺ orange · LUMO⁻ purple · electron cloud teal (translucent).<br>"
                + "Hotkeys: B=toggle FMO D=toggle density R=reset S=screenshot 1/2/3=ball/stick/scale/wire; drag to rotate, wheel to zoom.");
        en.put("spec.renderSelect", "Please select and parse an output file first.");
        en.put("spec.renderWorking", "Generating XYZ and FMO/electron-density isosurfaces (Multiwfn running, may take 1–2 min)…");
        en.put("spec.renderDone", "Launched %s (%d atoms, found %d isosurfaces: FMO + density)");
        en.put("spec.renderFail", "Render failed: %s");
        en.put("spec.viewer3d", "3D Viewer");
        en.put("spec.viewerOther", "Renderer");
        en.put("spec.selectParse", "Please select an output file first.");
        en.put("spec.parsing", "Parsing %s …");
        en.put("spec.parseFail", "Parse failed: %s");
        en.put("spec.homo", "HOMO (highest occupied)");
        en.put("spec.lumo", "LUMO (lowest unoccupied)");
        en.put("spec.legendOcc", "Occupied (solid) / virtual (dashed)");
        en.put("spec.wavenum", "Wavenumber (cm⁻¹)");
        en.put("spec.intensity", "Intensity");
        en.put("spec.imagTag", "Imag!");
        en.put("spec.molHint", "%d atoms · %d bonds · drag to rotate / wheel to zoom");
        en.put("spec.demoTitle", "Demo data (simulated organic molecule, aspirin-like skeleton):");

        // ---- Molecular Optimization ----
        en.put("opt.title", "Molecular Optimization (C++ force field + MDP RL core molopt.exe)");
        en.put("opt.lblSmiles", "SMILES / name:");
        en.put("opt.smilesHint", "Enter a SMILES (e.g. CCO) or a Chinese name (e.g. 铝宾 (10)); the server parses it automatically");
        en.put("opt.episodes", "Episodes:");
        en.put("opt.steps", "Steps/episode:");
        en.put("opt.pick", "Compound Lib");
        en.put("opt.run", "Start Optimize");
        en.put("opt.viz", "3D Visualize");
        en.put("opt.props", "Properties");
        en.put("opt.ready", "Ready — enter SMILES or pick from the compound library");
        en.put("opt.quickTry", "Quick try:");
        en.put("opt.resTitle", "Optimization Result");
        en.put("opt.rBackend", "Engine");
        en.put("opt.rEnergy", "Total energy");
        en.put("opt.rConstraint", "Constraint energy");
        en.put("opt.rViolations", "Violations");
        en.put("opt.rValid", "Structure valid");
        en.put("opt.rEpisodes", "Episodes run");
        en.put("opt.rElapsed", "Core time");
        en.put("opt.cpp", "C++ (molopt.exe)");
        en.put("opt.py", "Python (fallback)");
        en.put("opt.yes", "✔ yes");
        en.put("opt.no", "✘ no");
        en.put("opt.pTitle", "Molecular Properties (RDKit)");
        en.put("opt.pFormula", "Formula");
        en.put("opt.pMw", "Exact MW");
        en.put("opt.pLogp", "LogP");
        en.put("opt.pHbd", "H-bond donors");
        en.put("opt.pHba", "H-bond acceptors");
        en.put("opt.pTpsa", "TPSA (Å²)");
        en.put("opt.pRot", "Rotatable bonds");
        en.put("opt.pHeavy", "Heavy atoms");
        en.put("opt.noInput", "Enter a SMILES or name first");
        en.put("opt.busyRun", "Optimizing %s …");
        en.put("opt.doneRun", "Optimization done — open the 3D view to inspect the structure");
        en.put("opt.failRun", "Optimization failed: %s");
        en.put("opt.busyViz", "Generating 3D structure and launching molview.exe…");
        en.put("opt.doneViz", "3D structure opened (%s) · %d atoms / %d bonds");
        en.put("opt.viewerNative", "native viewer molview.exe");
        en.put("opt.viewerWeb", "browser");
        en.put("opt.failViz", "Visualization failed: %s");
        en.put("opt.busyProps", "Computing molecular properties…");
        en.put("opt.doneProps", "Properties computed");
        en.put("opt.failProps", "Property computation failed: %s");
        en.put("opt.loadingLib", "Loading compound library…");
        en.put("opt.libFail", "Failed to load compound library: %s");
        en.put("opt.chosen", "Compound selected");
        en.put("opt.dlgTitle", "Compound Library (%d)");
        en.put("opt.dlgSearch", "Search:");
        en.put("opt.dlgSearchHint", "Filter by name or SMILES fragment");
        en.put("opt.ok", "Select");
        en.put("opt.cancel", "Cancel");

        // ---- Star Map ----
        en.put("starmap.hint", "Click a node for details");
        en.put("starmap.desc", "Loaded from the server, skills form constellations by category.\nDrag nodes to adjust the layout; click a node for skill details.");
        en.put("starmap.full", "Full Text");
        en.put("starmap.loading", "Loading…");
        en.put("starmap.reload", "Reload");
        en.put("starmap.relayout", "Relayout");
        en.put("starmap.info", "nodes × %d · constellations × %d · chains × %d");
        en.put("starmap.cat", "Category: %s%s");
        en.put("starmap.catHub", " (constellation hub)");
        en.put("starmap.tags", "Tags: %s");
        en.put("starmap.noTags", "Tags: —");
        en.put("starmap.noDesc", " (no description, click \"Full Text\" to read the full skill doc)");
        en.put("starmap.none", "Category: —\nTags: —\n\nClick a node for skill details.");
        en.put("starmap.legend", "★ category   ● skill node   ─ chain (shared tag)");

        // ---- Knowledge Base ----
        en.put("kb.left", "Skill List");
        en.put("kb.right", "Skill Detail");
        en.put("kb.search", "Search:");
        en.put("kb.category", "Category:");
        en.put("kb.all", "All Categories");
        en.put("kb.hint", "Select a skill on the left to view its content");
        en.put("kb.noMatch", "No matching skill");
        en.put("kb.meta", "Category: %s · Tags: %s · %d chars");
        en.put("kb.loadFail", "Load failed: %s");
        en.put("kb.searchHint", "Filter by name/title/description/tags");

        // ---- Memory ----
        en.put("mem.title", "Memory Center");
        en.put("mem.sub", "  /memory · long-term · atomic facts");
        en.put("mem.search", "Retrieve ⌕");
        en.put("mem.extract", "Extract Facts");
        en.put("mem.statusIdle", "bigram-Dice similarity retrieval · threshold 0.12");
        en.put("mem.blockQuiz", "Decision Memory");
        en.put("mem.blockFacts", "Session Facts");

        // ---- 3D View ----
        en.put("mol.title", "3D Structure");
        en.put("mol.sub", "  /crn3d · C++ OpenGL");
        en.put("mol.pickInfo", "Drag to rotate · Scroll to zoom · Click to pick an atom");
        en.put("mol.reset", "↺ Reset View");
        en.put("mol.wire", "◐ Wireframe");
        en.put("mol.export", "Screenshot");

        // ---- Jobs ----
        en.put("jobs.new", "＋ New DFT Job");
        en.put("jobs.cluster", "Cluster");
        en.put("jobs.batch", "Batch Submit");
        en.put("jobs.upload", "Upload Structure");
        en.put("jobs.header", "OUTPUT · Dispatch Log");
        en.put("jobs.colJob", "JOB");
        en.put("jobs.colName", "Name");
        en.put("jobs.colBackend", "Backend");
        en.put("jobs.colQueue", "Queue");
        en.put("jobs.colStatus", "Status");
        en.put("jobs.colProgress", "Progress");
        en.put("jobs.stRunning", "Running");
        en.put("jobs.stQueued", "Queued");
        en.put("jobs.stDone", "Done");

        // ---- Settings ----
        en.put("set.health", "Component Health");
        en.put("set.conn", "Server Connection");
        en.put("set.connUrl", "Server URL:");
        en.put("set.dft", "DFT Backend Config (Gaussian / ORCA / Multiwfn / SSH Cluster / Workspace)");
        en.put("set.backend", "Default backend:");
        en.put("set.about", "About · Trilingual Architecture");
        en.put("set.refresh", "Refresh");
        en.put("set.saveUrl", "Save URL");
        en.put("set.browse", "Browse…");
        en.put("set.test", "Test");
        en.put("set.version", "Version");
        en.put("set.refreshModels", "Refresh Local Models");
        en.put("set.models", "LLM Tiering (local-first · optional hybrid/cloud)");
        en.put("set.healthTitle", "Component Health");
        en.put("set.connTitle", "Server Connection");
        en.put("set.dftTitle", "DFT Backend Config (Gaussian / ORCA / Multiwfn / SSH Cluster / Workspace)");
        en.put("set.modelTitle", "LLM Tiering (local-first · optional hybrid/cloud)");
        en.put("set.aboutTitle", "About · Trilingual Architecture");
        en.put("set.url", "Server URL:");
        en.put("set.checking", "Checking…");
        en.put("set.ok", "● OK");
        en.put("set.bad", "✘ Error");
        en.put("set.unreach", "✘ Unreachable");
        en.put("set.theServer", "Python service layer (agent_server.py)");
        en.put("set.ollama", "Ollama service");
        en.put("set.modelReady", "LLM ready");
        en.put("set.molopt", "C++ optimizer core (molopt.exe)");
        en.put("set.molview", "C++ molecule viewer (molview.exe)");
        en.put("set.rdkit", "RDKit chemistry library");
        en.put("set.kb", "Knowledge base");
        en.put("set.dft", "DFT backend");
        en.put("set.workflow", "Workflow engine");
        en.put("set.g16", "Gaussian (g16):");
        en.put("set.orca", "ORCA:");
        en.put("set.multiwfn", "Multiwfn:");
        en.put("set.workspace", "Workspace directory:");
        en.put("set.pathHint", "Leave blank to use the default command in PATH");
        en.put("set.browse", "Browse…");
        en.put("set.test", "Test");
        en.put("set.defaultBackend", "Default backend:");
        en.put("set.backendHint", "Used as default by dft_single_point / dft_optimize / dft_frequency tools");
        en.put("set.sshHint2", "If Gaussian/ORCA is not installed locally, an SSH cluster backend can be configured");
        en.put("set.sshTitle", "SSH Cluster (Remote Gaussian16 · PBS/Slurm)");
        en.put("set.sshHost", "Host:");
        en.put("set.sshUser", "Username:");
        en.put("set.sshPass", "Password:");
        en.put("set.sshKey", "Key file:");
        en.put("set.sshDir", "Remote directory:");
        en.put("set.sshQueue", "Queue system:");
        en.put("set.sshQueueName", "Queue name:");
        en.put("set.sshG16Root", "G16 install dir:");
        en.put("set.sshScratch", "Cluster SCRATCH:");
        en.put("set.sshTest", "Test SSH Connection");
        en.put("set.sshHint", "Enter host and username then save to test the connection");
        en.put("set.modelBackend", "Model backend:");
        en.put("set.modelHint", "config: local data stays local (default) · hybrid auto-upgrades complex TS · cloud all-cloud");
        en.put("set.localModel", "Local model:");
        en.put("set.cloudUrl", "Cloud Base URL:");
        en.put("set.cloudKey", "Cloud API Key:");
        en.put("set.cloudModel", "Cloud model:");
        en.put("set.cloudOnlyAuth", "Effective only after authorization");
        en.put("set.cloudConsent", "Allow data to leave the machine (required for the cloud calls below)");
        en.put("set.cloudTsAuto", "In hybrid mode, auto-upgrade complex TS/scan tasks to cloud");
        en.put("set.saveModelCfg", "Save & Apply Model Config");
        en.put("set.saveDftCfg", "Save & Apply Config");
        en.put("set.chooseWs", "Choose workspace directory");
        en.put("set.chooseProg", "Choose executable");
        en.put("set.cfgPath", "Config file: %s · takes effect immediately, no service restart needed");
        en.put("set.liveBackends", "Available backends: %s · default: %s");
        en.put("set.savingDft", "Saving and applying config…");
        en.put("set.savedDft", "✔ Config saved and hot-applied · available backends: %s · default: %s");
        en.put("set.savedFallback", " (selected default backend not ready, fell back — check the program or SSH config)");
        en.put("set.saveDftFail", "✘ Save failed: %s");
        en.put("set.testing", "Testing %s …");
        en.put("set.testFail", "✘ Test request failed: %s");
        en.put("set.localNoModels", "No models downloaded in local Ollama");
        en.put("set.downloaded", "Downloaded: %s");
        en.put("set.savingModel", "Saving and applying model config…");
        en.put("set.savedModel", "✔ Saved and hot-applied · backend: %s（%s）");
        en.put("set.saveModelFail", "✘ Save failed: %s");
        en.put("set.currentBackend", "Current backend: %s（%s）");
        en.put("set.dataLocal", "data stays local");
        en.put("set.dataCloud", "cloud/hybrid — confirm data-out authorization");
        en.put("set.backendLocalG16", "Local Gaussian16 (local_g16)");
        en.put("set.backendLocalOrca", "Local ORCA (local_orca)");
        en.put("set.backendCluster", "SSH Cluster Gaussian16 (cluster_g16)");
        en.put("set.modelLocal", "Local (local) · data never leaves the machine (default)");
        en.put("set.modelHybrid", "Hybrid (hybrid) · local + auto-upgrade complex tasks to cloud");
        en.put("set.modelCloud", "Cloud (cloud)");
        en.put("set.aboutHtml", "<b>Zhisuan Aluminylene Agent</b> — Aluminylene chemistry research platform<br>"
                + "· <b>Python service layer</b> agent_server.py: ReAct agent loop (qwen3.5:2b) + 22 research tools + HTTP/SSE interface<br>"
                + "· <b>Java GUI</b> (this program): Swing workbench — AI Assistant / Molecular Optimize / Star Map / Spectra / Knowledge Base<br>"
                + "· <b>C++ compute core</b>: molopt.exe force-field+MDP+Q-Learning optimizer (60~1300× faster than Python); molview.exe OpenGL 3D viewer<br>"
                + "· <b>Quantum-chemistry backend</b>: Gaussian16 / ORCA / Multiwfn (configured here, SSH cluster submission supported)<br>"
                + "· Local compound library: 67 entries (52 aluminylene) · 19 skill/knowledge articles<br>"
                + "Startup order: start_zhisuan.bat → Python service → Java GUI (auto-detect & connect)</div></html>");

        // ---- Workflow ----
        en.put("workflow.fit", "Fit View");
        en.put("workflow.zoomIn", "Zoom In");
        en.put("workflow.zoomOut", "Zoom Out");
        en.put("workflow.online", "● connected %s");
        en.put("workflow.offline", "○ offline %s");

        Map<String, String> de = new LinkedHashMap<>();
        de.put("app.title", "Aluminylen-Agent · Dreisprachiges Labor");
        de.put("brand.sub", "/ AL-AGENT");
        de.put("nav.chat", "KI-Assistent");
        de.put("nav.audit", "Prüfstand");
        de.put("nav.dispatch", "Leitzentrale");
        de.put("nav.starmap", "Sternenkarte");
        de.put("nav.spectrum", "Spektren");
        de.put("nav.optimize", "Optimierung");
        de.put("nav.jobs", "Rechenaufträge");
        de.put("nav.library", "Verbindungen");
        de.put("nav.mol", "3D-Ansicht");
        de.put("nav.knowledge", "Wissensbasis");
        de.put("nav.memory", "Gedächtnis");
        de.put("nav.workflow", "Workflow");
        de.put("nav.settings", "Einstellungen");
        de.put("top.model", "Modell");
        de.put("top.local", "Lokal");
        de.put("top.offline", "· Offline zuerst");
        de.put("top.lang", "Sprache");
        de.put("sys.prompt", "Sie sind der Chemie-Forschungsassistent für Aluminylene. Bitte antworten Sie auf Deutsch.");
        de.put("status.connecting", "Verbindung zum Server wird aufgebaut…");
        de.put("status.connected", "Verbunden");
        de.put("status.connFmt", "Verbunden %s · Modell %s%s · Verbindungen %d · Skills %d · C++-Kern %s");
        de.put("status.notready", "(nicht bereit)");
        de.put("status.ready", "(bereit)");
        de.put("status.missing", "fehlt");
        de.put("status.offline", "Server nicht erreichbar (%s) — starten Sie start_zhisuan.bat oder agent_server.py");
        de.put("status.cpp", "C++-Kern");
        de.put("status.datasec", "◉ Daten bleiben lokal");
        de.put("footer.agent", "Agent-Ausgabe");
        de.put("footer.term", "Terminal");
        de.put("footer.panel", "Panel-Aktionen");
        de.put("footer.ready", "[AL-AGENT] Bereit · dreisprachiges Labor gestartet");
        de.put("chat.send", "Senden");
        de.put("chat.busy", "Antworte…");
        de.put("chat.clear", "Löschen");
        de.put("chat.agent", "Agentenmodus");
        de.put("chat.welcome", "Willkommen im Arbeitsplatz des Zhisuan-Aluminylen-Agenten.\n"
                + "· Normalmodus: spreche direkt mit dem qwen-Modell (Markdown-Rendering)\n"
                + "· Agentenmodus: das Modell kann 23 Forschungswerkzeuge aufrufen (Verbindungssuche / DFT-Berechnung (inkl. SSH-Async-Submit) / MDP-Optimierung / 3D-Ansicht / Wissensbasis-Suche…)\n"
                + "Probiere: \"aryl-carbazolyl aluminylene (10) nachschlagen und optimieren\" · \"aspirin single-point energy asynchron absenden\" · \"Moleküleigenschaften von Aspirin analysieren\"\n\n");
        de.put("chat.you", "Du:");
        de.put("chat.open", "Öffnen");
        de.put("chat.openDir", "Ordner öffnen");
        de.put("chat.taskRunning", "Hintergrundaufgabe läuft…");
        de.put("chat.taskSubmitted", "Hintergrundaufgabe übermittelt");
        de.put("chat.attachTitle", "Dateien zum Anhängen auswählen");
        de.put("chat.agentModeTip", "Wenn aktiviert kann das Modell 23 Werkzeuge aufrufen: Verbindungssuche, DFT-Berechnung (inkl. Async-Submit), MDP-Optimierung, 3D-Ansicht usw.");
        de.put("chat.fileMissing", "Datei nicht gefunden: %s");
        de.put("chat.openFail", "Öffnen fehlgeschlagen: %s");
        de.put("chat.agentStart", "⚙ Agentenmodus gestartet, denke nach…\n");
        de.put("chat.assistant", "● Assistent:");
        de.put("chat.tool", "🔧 Werkzeugaufruf ");
        de.put("chat.param", "Args: ");
        de.put("chat.result", "Ergebnis: ");
        de.put("chat.taskName", "Aufgabe %s");
        de.put("chat.hint", "Eingabetaste zum Senden · Strg+Enter für Zeilenumbruch · Markdown unterstützt");
        de.put("chat.attach", "Anhängen / Laden");
        de.put("chat.attachHint", "Lokale Datei anhängen; der Pfad wird in das Eingabefeld eingefügt");
        de.put("chat.connFail", "Verbindung fehlgeschlagen (%d Versuche)…");
        de.put("chat.runningWith", "Läuft (%s)");
        de.put("chat.noOutput", "(keine Ausgabe)");
        de.put("chat.elapsedSec", "%d s");
        de.put("chat.elapsedMin", "%d min %d s");
        de.put("chat.sysDone", "[System · Aufgabe erledigt] Aufgabe %s");
        de.put("chat.sysFail", "[System · Aufgabe fehlgeschlagen] Aufgabe %s");
        de.put("chat.rsmOkCtrl", "Setze die vorherige Aufgabe auf Basis dieses Ergebnisses fort und gib eine Abschlussfolgerung.");
        de.put("chat.rsmFailCtrl", "Erkläre dem Benutzer den Grund für das Scheitern und schlage nächste Schritte vor (z. B. DFT-Backend in den Einstellungen prüfen).");
        de.put("chat.rsmOkTitle", "Hintergrundaufgabe erledigt, Konversation wird fortgesetzt");
        de.put("chat.rsmFailTitle", "Hintergrundaufgabe fehlgeschlagen, Konversation wird fortgesetzt");
        de.put("chat.rsmOkHint", "Ergebnis in den Kontext eingefügt, Modell denkt weiter…");
        de.put("chat.rsmFailHint", "Fehlerdetails in den Kontext eingefügt");
        de.put("chat.clearDone", "Konversation geleert.");

        de.put("audit.title", "Ergebnis-Prüfstand");
        de.put("audit.sub", "%d offen · %d heute übernommen");
        de.put("audit.queue", "Prüfwarteschlange");
        de.put("audit.pending", "offen");
        de.put("audit.source", "Urteilsquelle");
        de.put("audit.evidence", "Beweiskette");
        de.put("audit.similar", "Ähnliche frühere Fälle");
        de.put("audit.accept", "Ergebnis übernehmen");
        de.put("audit.rerun", "Mit Änderungen erneut");
        de.put("audit.human", "Manuelle Prüfung");
        de.put("audit.acceptHint", "Übernommene Ergebnisse werden für spätere Ähnlichkeitssuche in die Wissensbasis geschrieben");
        de.put("audit.engine.laya", "Laya-Entscheidungskopf");
        de.put("audit.engine.qwen", "qwen3.5:2b");
        de.put("audit.engine.rules", "Regelwerk");
        de.put("audit.adopted", "Übernommen");
        de.put("audit.skipped", "Übersprungen");
        de.put("audit.keep", "KEEP");
        de.put("audit.keepHint", "Vertrauenswürdig, übernehmen");
        de.put("audit.rerunV", "RERUN");
        de.put("audit.rerunHint", "Erneut erforderlich");
        de.put("audit.unknown", "Unbekannt");
        de.put("audit.unknownHint", "Urteil unbekannt, Protokoll prüfen");
        de.put("audit.empty", "Keine offenen Aufgaben");
        de.put("audit.tool", "Triage-Motor");
        de.put("audit.noResult", "Kein Diagnoseergebnis für diese Aufgabe (aktualisieren)");
        de.put("audit.diagTip", "Nach Abschluss zeigt der Prüfstand KEEP/RERUN-Urteil und Beweiskette");
        de.put("audit.verdict", "Urteil");
        de.put("audit.reason", "Grund");
        de.put("audit.metrics", "Kennzahlen");
        de.put("audit.basic", "Aufgabeninfo");
        de.put("audit.params", "Parameter");
        de.put("audit.refresh", "Aktualisieren");
        de.put("audit.noLog", "Kein .log/.out-Protokoll für Diagnose gefunden; nur Rohdaten");
        de.put("audit.loading", "Diagnose läuft…");
        de.put("audit.diagFail", "Diagnose fehlgeschlagen: %s");
        de.put("audit.acceptOk", "Übernommen (in Wissensbasis geschrieben)");
        de.put("audit.skipOk", "Zur manuellen Prüfung gesendet");
        de.put("audit.rerunTitle", "Mit Änderungen erneut");
        de.put("audit.rerunMsg", "JSON-Parameter bearbeiten und senden (Werkzeug: %s)");
        de.put("audit.submit", "Senden");
        de.put("audit.cancel", "Abbrechen");
        de.put("audit.none", "keine");
        de.put("audit.simScore", "Ähnlichkeit %.2f");
        de.put("audit.engine", "Urteilsquelle");

        de.put("disp.title", "Rechen-Einsatzzentrale");
        de.put("disp.sync", "vor %d s synchronisiert · %d Backends");
        de.put("disp.g16", "Lokal Gaussian16");
        de.put("disp.orca", "Lokal ORCA");
        de.put("disp.cluster", "SSH-Cluster G16");
        de.put("disp.idle", "Frei · %d Kerne verfügbar");
        de.put("disp.running", "%d / %d Aufträge aktiv");
        de.put("disp.down", "Nicht verbunden");
        de.put("disp.online", "Online · %d ms Latenz");
        de.put("disp.queued", "Warteschlange");
        de.put("disp.calc", "Berechnung");
        de.put("disp.transfer", "Rückübertragung");
        de.put("disp.review", "Zur Auswertung");
        de.put("disp.doneBanner", "%s abgeschlossen — der Agent hat die Sitzung fortgesetzt und eine Interpretation erstellt. %d Auftrag/Aufträge warten auf Parameterbestätigung.");
        de.put("disp.backToChat", "Zurück zum Chat");
        de.put("disp.empty", "(leer)");
        de.put("disp.download", ".log-Download %.1f / %.1f MB");
        de.put("disp.license", "Lizenz läuft in %d Tagen ab");
        de.put("disp.queuelen", "Wartezeit ~%d min");
        de.put("disp.newJob", "Neuer DFT-Auftrag");
        de.put("disp.failed", "Fehlgeschlagen");
        de.put("disp.interrupted", "Unterbrochen");
        de.put("disp.unconfigured", "SSH nicht konfiguriert");

        // ---- Verbindungsbibliothek ----
        de.put("library.title", "Verbindungsbibliothek");
        de.put("library.sub", "  /library · A1–A4 /*");
        de.put("library.import", "Import .mol / SMILES");
        de.put("library.add", "＋ Neu");
        de.put("library.loaded", "%s zur Werkbank geladen");
        de.put("library.saved", "In Verbindungsbibliothek gespeichert · Nr. 71");
        de.put("library.parsed", "SMILES geparst · Familie A1");
        de.put("library.detail", "Strukturdetails · %s");
        de.put("library.family", "Familie %s  |  SMILES: %s");
        de.put("library.loading", "Laden…");
        de.put("library.detailHint", "Klicken Sie eine Karte, um die Strukturdetails zu öffnen");
        de.put("library.fail", "× Laden fehlgeschlagen");
        de.put("library.lookup", "Nach Name oder SMILES suchen oder links nach Familie filtern");
        de.put("library.backend", "Backend-Bibliothek");
        de.put("library.multiMatch", "%d Treffer (Cache %d)");
        de.put("library.cached", "%d im Cache");
        de.put("library.backendTooltip", "Aluminylene-Verbindungen dynamisch vom Python-Server geladen");
        de.put("library.relayout", "Neu laden");
        de.put("library.fAll", "Alle");
        de.put("library.fAllTooltip", "Alle Verbindungen anzeigen");

        // ---- Optimierung ----
        de.put("opt.title", "Moleküloptimierung (C++-Kraftfeld + MDP-RL-Kern molopt.exe)");
        de.put("opt.lblSmiles", "SMILES / Name:");
        de.put("opt.lblEp", "Episoden:");
        de.put("opt.lblSteps", "Schritte/Episode:");
        de.put("opt.examples", "Schnelltest:");
        de.put("opt.pick", "Verbindungen");
        de.put("opt.run", "Starten");
        de.put("opt.viz", "3D-Ansicht");
        de.put("opt.prop", "Eigenschaften");
        de.put("opt.statusIdle", "Bereit — SMILES eingeben oder aus der Verbindungsbibliothek wählen");
        de.put("opt.msgEnter", "Bitte zuerst SMILES oder Namen eingeben");
        de.put("opt.result", "Optimierungsergebnis");
        de.put("opt.propCard", "Moleküleigenschaften (RDKit)");
        de.put("opt.rowCore", "Motor");
        de.put("opt.rowEnergy", "Gesamtenergie");
        de.put("opt.rowConstraint", "Randenergie");
        de.put("opt.rowViolations", "Verletzungen");
        de.put("opt.rowValid", "Gültig");
        de.put("opt.rowEpisodes", "Episoden");
        de.put("opt.rowElapsed", "Dauer");
        de.put("opt.rowFormula", "Summenformel");
        de.put("opt.rowMw", "Exakte Masse");
        de.put("opt.rowHbd", "H-Brückendonoren");
        de.put("opt.rowHba", "H-Brückenakzeptoren");
        de.put("opt.rowRot", "Rotierbare Bindungen");
        de.put("opt.rowHeavy", "Schwere Atome");
        de.put("opt.search", "Suche:");
        de.put("opt.ok", "OK");
        de.put("opt.cancel", "Abbrechen");

        // ---- Spektren ----
        de.put("spec.title", "Quantenchemie-Ausgabeparser (cclib)");
        de.put("spec.lblFile", "Ausgabedatei:");
        de.put("spec.browse", "Durchsuchen…");
        de.put("spec.parse", "Analysieren");
        de.put("spec.demo", "Beispieldaten");
        de.put("spec.sumAtoms", "Atome: —");
        de.put("spec.sumScf", "SCF-Energie: —");
        de.put("spec.sumGap", "HOMO-LUMO-Lücke: —");
        de.put("spec.sumImag", "Imaginäre Freq: —");
        de.put("spec.tabMo", "MO-Niveaus");
        de.put("spec.tabIr", "IR-Spektrum");
        de.put("spec.tab3d", "3D / FMO");
        de.put("spec.title3d", "3D-Rendering — molview.exe (FMO / Elektronendichte-Isosurfaces)");
        de.put("spec.info", "Analyselog");
        de.put("spec.render", "3D rendern");
        de.put("spec.renderStatus", "Noch nicht gerendert — nach der Analyse klicken Sie „3D rendern\" zum Starten");
        de.put("spec.logInit", "Gaussian/ORCA-Ausgabedateien (.log/.out) analysieren oder „Beispieldaten\" wählen.");
        de.put("spec.moatoms", "Atome: %s");
        de.put("spec.moscf", "SCF-Energie: %s");
        de.put("spec.mogap", "HOMO-LUMO-Lücke: %s");
        de.put("spec.moimag", "Imaginäre Freq: %d");
        de.put("spec.noData", "Keine Daten — Ausgabedatei analysieren oder „Beispieldaten\" wählen");
        de.put("spec.noIr", "Keine Daten — Ausgabedatei mit Frequenzberechnung nötig");
        de.put("spec.no3d", "Keine Struktur — Stäbchenmodell erscheint nach der Analyse");
        de.put("spec.title3dHint", "Nach der Analyse „3D rendern\" wählen: erzeugt <b>&lt;Ergebnis&gt;_view.xyz</b>, "
                + "erkennt FMO / Elektronendichte-Isosurfaces im selben Ordner und startet den 3D-Viewer.<br>"
                + "Farben: HOMO⁺ blau · HOMO⁻ rot · LUMO⁺ orange · LUMO⁻ lila · Elektronenwolke türkis (transparent).<br>"
                + "Tasten: B=FMO umschalten D=Dichte umschalten R=Zurücksetzen S=Screenshot 1/2/3=Stäbchen/Maß/Drahtgitter; ziehen zum Drehen, Rad zum Zoomen.");
        de.put("spec.renderSelect", "Bitte zuerst eine Ausgabedatei wählen und analysieren.");
        de.put("spec.renderWorking", "XYZ und FMO/Elektronendichte-Isosurfaces werden erzeugt (Multiwfn läuft, dauert 1–2 Min)…");
        de.put("spec.renderDone", "%s gestartet (%d Atome, %d Isosurfaces gefunden: FMO + Dichte)");
        de.put("spec.renderFail", "Rendering fehlgeschlagen: %s");
        de.put("spec.viewer3d", "3D-Viewer");
        de.put("spec.viewerOther", "Renderer");
        de.put("spec.selectParse", "Bitte zuerst eine Ausgabedatei wählen.");
        de.put("spec.parsing", "Analysiere %s …");
        de.put("spec.parseFail", "Analyse fehlgeschlagen: %s");
        de.put("spec.homo", "HOMO (höchstes besetztes)");
        de.put("spec.lumo", "LUMO (niedrigstes unbesetztes)");
        de.put("spec.legendOcc", "Besetzt (durchgezogen) / virtuell (gestrichelt)");
        de.put("spec.wavenum", "Wellenzahl (cm⁻¹)");
        de.put("spec.intensity", "Intensität");
        de.put("spec.imagTag", "Imaginär!");
        de.put("spec.molHint", "%d Atome · %d Bindungen · ziehen zum Drehen / Rad zum Zoomen");
        de.put("spec.demoTitle", "Beispieldaten (simuliertes organisches Molekül, Aspirin-ähnliches Gerüst):");

        // ---- Moleküloptimierung ----
        de.put("opt.title", "Moleküloptimierung (C++-Kraftfeld + MDP-RL-Kern molopt.exe)");
        de.put("opt.lblSmiles", "SMILES / Name:");
        de.put("opt.smilesHint", "Geben Sie eine SMILES (z. B. CCO) oder einen chinesischen Namen (z. B. 铝宾 (10)) ein; der Server parst ihn automatisch");
        de.put("opt.episodes", "Episoden:");
        de.put("opt.steps", "Schritte/Episode:");
        de.put("opt.pick", "Verbindungs-Bibl.");
        de.put("opt.run", "Optimierung starten");
        de.put("opt.viz", "3D-Visualisierung");
        de.put("opt.props", "Eigenschaften");
        de.put("opt.ready", "Bereit — SMILES eingeben oder aus der Verbindungsbibliothek wählen");
        de.put("opt.quickTry", "Schnelltest:");
        de.put("opt.resTitle", "Optimierungsergebnis");
        de.put("opt.rBackend", "Kern");
        de.put("opt.rEnergy", "Gesamtenergie");
        de.put("opt.rConstraint", "Randenergie");
        de.put("opt.rViolations", "Verletzungen");
        de.put("opt.rValid", "Struktur gültig");
        de.put("opt.rEpisodes", "Episoden");
        de.put("opt.rElapsed", "Kernzeit");
        de.put("opt.cpp", "C++ (molopt.exe)");
        de.put("opt.py", "Python (Fallback)");
        de.put("opt.yes", "✔ ja");
        de.put("opt.no", "✘ nein");
        de.put("opt.pTitle", "Moleküleigenschaften (RDKit)");
        de.put("opt.pFormula", "Summenformel");
        de.put("opt.pMw", "Exakte molare Masse");
        de.put("opt.pLogp", "LogP");
        de.put("opt.pHbd", "H-Brückendonoren");
        de.put("opt.pHba", "H-Brückenakzeptoren");
        de.put("opt.pTpsa", "TPSA (Å²)");
        de.put("opt.pRot", "Drehbare Bindungen");
        de.put("opt.pHeavy", "Schwere Atome");
        de.put("opt.noInput", "Bitte zuerst SMILES oder Namen eingeben");
        de.put("opt.busyRun", "Optimiere %s …");
        de.put("opt.doneRun", "Optimierung fertig — 3D-Ansicht öffnen, um die Struktur zu prüfen");
        de.put("opt.failRun", "Optimierung fehlgeschlagen: %s");
        de.put("opt.busyViz", "Erzeuge 3D-Struktur und starte molview.exe…");
        de.put("opt.doneViz", "3D-Struktur geöffnet (%s) · %d Atome / %d Bindungen");
        de.put("opt.viewerNative", "nativer Viewer molview.exe");
        de.put("opt.viewerWeb", "Browser");
        de.put("opt.failViz", "Visualisierung fehlgeschlagen: %s");
        de.put("opt.busyProps", "Berechne Moleküleigenschaften…");
        de.put("opt.doneProps", "Eigenschaften berechnet");
        de.put("opt.failProps", "Eigenschaftenberechnung fehlgeschlagen: %s");
        de.put("opt.loadingLib", "Lade Verbindungsbibliothek…");
        de.put("opt.libFail", "Laden der Verbindungsbibliothek fehlgeschlagen: %s");
        de.put("opt.chosen", "Verbindung ausgewählt");
        de.put("opt.dlgTitle", "Verbindungsbibliothek (%d)");
        de.put("opt.dlgSearch", "Suchen:");
        de.put("opt.dlgSearchHint", "Nach Name oder SMILES-Fragment filtern");
        de.put("opt.ok", "Auswählen");
        de.put("opt.cancel", "Abbrechen");

        // ---- Sternenkarte ----
        de.put("starmap.hint", "Klicken Sie einen Knoten für Details");
        de.put("starmap.desc", "Vom Server geladen, bilden Skills nach Kategorie Sternbilder.\nZiehen Sie Knoten zum Anordnen; klicken Sie einen Knoten für Skill-Details.");
        de.put("starmap.full", "Volltext");
        de.put("starmap.loading", "Laden…");
    de.put("starmap.loadFail", "Laden fehlgeschlagen: ");
        de.put("starmap.reload", "Neu laden");
        de.put("starmap.relayout", "Neu anordnen");
        de.put("starmap.info", "Knoten × %d · Sternbilder × %d · Ketten × %d");
        de.put("starmap.cat", "Kategorie: %s%s");
        de.put("starmap.catHub", " (Sternbild-Hub)");
        de.put("starmap.tags", "Tags: %s");
        de.put("starmap.noTags", "Tags: —");
        de.put("starmap.noDesc", " (keine Beschreibung, „Volltext\" für das gesamte Skill-Dokument)");
        de.put("starmap.none", "Kategorie: —\nTags: —\n\nKlicken Sie einen Knoten für Skill-Details.");
        de.put("starmap.legend", "★ Kategorie   ● Skill-Knoten   ─ Kette (gemeinsamer Tag)");

        // ---- Wissensbasis ----
        de.put("kb.left", "Skill-Liste");
        de.put("kb.right", "Skill-Detail");
        de.put("kb.search", "Suche:");
        de.put("kb.category", "Kategorie:");
        de.put("kb.all", "Alle Kategorien");
        de.put("kb.hint", "Wählen Sie links einen Skill, um dessen Inhalt zu sehen");
        de.put("kb.noMatch", "Kein passender Skill");
        de.put("kb.meta", "Kategorie: %s · Tags: %s · %d Zeichen");
        de.put("kb.loadFail", "Laden fehlgeschlagen: %s");
        de.put("kb.searchHint", "Nach Name/Titel/Beschreibung/Tags filtern");
    de.put("kb.placeholder", "Tipp: Nach Name, Titel, Beschreibung und Tags durchsuchbar.");
    de.put("kb.loadingContent", "Volltext wird geladen…");
    de.put("kb.readFail", "Lesen fehlgeschlagen: %s");
    de.put("kb.itemMeta", "%s · %d Zeichen");

        // ---- Gedächtnis ----
        de.put("mem.title", "Gedächtniszentrum");
        de.put("mem.sub", "  /memory · long-term · atomic facts");
        de.put("mem.search", "Abrufen ⌕");
        de.put("mem.extract", "Fakten extrahieren");
        de.put("mem.statusIdle", "bigram-Dice-Ähnlichkeitssuche · Schwellwert 0.12");
    de.put("mem.searching", "Suche… (bigram-Dice)");
    de.put("mem.hits3", "3 Treffer · nach Relevanz sortiert");
    de.put("mem.extracted1", "1 atomare Tatsache extrahiert");
    de.put("mem.extracted", "Aufgabe/Spektrum → Einsichtseintrag · neu");
    de.put("mem.blockQuiz", "Entscheidungsgedächtnis");
        de.put("mem.blockFacts", "Sitzungs-Fakten");

        // ---- 3D-Ansicht ----
        de.put("mol.title", "3D-Struktur");
        de.put("mol.sub", "  /crn3d · C++ OpenGL");
        de.put("mol.pickInfo", "Ziehen zum Drehen · Scrollen zum Zoomen · Klicken zum Atomwählen");
        de.put("mol.reset", "↺ Ansicht zurücksetzen");
        de.put("mol.wire", "◐ Drahtgitter");
        de.put("mol.export", "Screenshot");
    de.put("mol.exportMsg", "Ansicht exportiert nach workspace/shot3d.png");
    de.put("mol.infoCenter", "Al-Zentrum");
    de.put("mol.infoCoord", "Koordinaten");
    de.put("mol.infoCalc", "Berechnung");
    de.put("mol.picked", "Gewählt: %s · Infokarte angezeigt");
    de.put("mol.clickHint", "Klicken Sie ein Atom für Details");

        // ---- Rechenaufträge ----
        de.put("jobs.new", "＋ Neuer DFT-Auftrag");
        de.put("jobs.cluster", "Cluster");
        de.put("jobs.batch", "Stapelübermittlung");
        de.put("jobs.upload", "Struktur hochladen");
        de.put("jobs.header", "OUTPUT · Einsatzlog");
        de.put("jobs.colJob", "JOB");
        de.put("jobs.colName", "Name");
        de.put("jobs.colBackend", "Backend");
        de.put("jobs.colQueue", "Warteschlange");
        de.put("jobs.colStatus", "Status");
        de.put("jobs.colProgress", "Fortschritt");
        de.put("jobs.stRunning", "Aktiv");
        de.put("jobs.stQueued", "Wartend");
        de.put("jobs.stDone", "Fertig");

        // ---- Einstellungen ----
        de.put("set.health", "Komponenten-Gesundheit");
        de.put("set.conn", "Serververbindung");
        de.put("set.connUrl", "Server-URL:");
        de.put("set.dft", "DFT-Backend-Konfig (Gaussian / ORCA / Multiwfn / SSH-Cluster / Arbeitsbereich)");
        de.put("set.backend", "Standard-Backend:");
        de.put("set.about", "Über · Dreisprachige Architektur");
        de.put("set.refresh", "Aktualisieren");
        de.put("set.saveUrl", "URL speichern");
        de.put("set.browse", "Durchsuchen…");
        de.put("set.test", "Test");
        de.put("set.version", "Version");
        de.put("set.refreshModels", "Lokale Modelle aktualisieren");
        de.put("set.models", "LLM-Stufung (lokal-zuerst · optional hybrid/cloud)");
        de.put("set.healthTitle", "Komponenten-Gesundheit");
        de.put("set.connTitle", "Serververbindung");
        de.put("set.dftTitle", "DFT-Backend-Konfig (Gaussian / ORCA / Multiwfn / SSH-Cluster / Arbeitsbereich)");
        de.put("set.modelTitle", "LLM-Stufung (lokal-zuerst · optional hybrid/cloud)");
        de.put("set.aboutTitle", "Über · Dreisprachige Architektur");
        de.put("set.url", "Server-URL:");
        de.put("set.checking", "Prüfung…");
        de.put("set.ok", "● OK");
        de.put("set.bad", "✘ Fehlerhaft");
        de.put("set.unreach", "✘ Unzugänglich");
        de.put("set.theServer", "Python-Diensteschicht (agent_server.py)");
        de.put("set.ollama", "Ollama-Dienst");
        de.put("set.modelReady", "LLM bereit");
        de.put("set.molopt", "C++-Optimierungskern (molopt.exe)");
        de.put("set.molview", "C++-Molekülbetrachter (molview.exe)");
        de.put("set.rdkit", "RDKit-Chemiebibliothek");
        de.put("set.kb", "Wissensbasis");
        de.put("set.dft", "DFT-Backend");
        de.put("set.workflow", "Workflow-Engine");
        de.put("set.g16", "Gaussian (g16):");
        de.put("set.orca", "ORCA:");
        de.put("set.multiwfn", "Multiwfn:");
        de.put("set.workspace", "Arbeitsverzeichnis:");
        de.put("set.pathHint", "Leer lassen, um den Standardbefehl im PATH zu verwenden");
        de.put("set.browse", "Durchsuchen…");
        de.put("set.test", "Test");
        de.put("set.defaultBackend", "Standard-Backend:");
        de.put("set.backendHint", "Wird von dft_single_point / dft_optimize / dft_frequency als Standard genutzt");
        de.put("set.sshHint2", "Wenn Gaussian/ORCA lokal nicht installiert ist, kann ein SSH-Cluster-Backend konfiguriert werden");
        de.put("set.sshTitle", "SSH-Cluster (Remote Gaussian16 · PBS/Slurm)");
        de.put("set.sshHost", "Host:");
        de.put("set.sshUser", "Benutzername:");
        de.put("set.sshPass", "Passwort:");
        de.put("set.sshKey", "Schlüsseldatei:");
        de.put("set.sshDir", "Remote-Verzeichnis:");
        de.put("set.sshQueue", "Warteschlangensystem:");
        de.put("set.sshQueueName", "Warteschlangenname:");
        de.put("set.sshG16Root", "G16-Installationsverzeichnis:");
        de.put("set.sshScratch", "Cluster-SCRATCH:");
        de.put("set.sshTest", "SSH-Verbindung testen");
        de.put("set.sshHint", "Host und Benutzername eingeben und speichern, um die Verbindung zu testen");
        de.put("set.modelBackend", "Modell-Backend:");
        de.put("set.modelHint", "config: lokal Daten bleiben lokal (Standard) · hybrid TS-Auto-Upgrades · cloud volle Cloud");
        de.put("set.localModel", "Lokales Modell:");
        de.put("set.cloudUrl", "Cloud-Basis-URL:");
        de.put("set.cloudKey", "Cloud-API-Schlüssel:");
        de.put("set.cloudModel", "Cloud-Modell:");
        de.put("set.cloudOnlyAuth", "Nur nach Autorisierung wirksam");
        de.put("set.cloudConsent", "Datenabgabe erlauben (für die folgenden Cloud-Aufrufe erforderlich)");
        de.put("set.cloudTsAuto", "Im Hybrid-Modus komplexe TS/Scan-Aufgaben automatisch in die Cloud heben");
        de.put("set.saveModelCfg", "Konfig speichern und anwenden");
        de.put("set.saveDftCfg", "Konfig speichern und anwenden");
        de.put("set.chooseWs", "Arbeitsverzeichnis wählen");
        de.put("set.chooseProg", "Programm wählen");
        de.put("set.cfgPath", "Konfigdatei: %s · sofort wirksam, kein Dienst-Neustart nötig");
        de.put("set.liveBackends", "Verfügbare Backends: %s · Standard: %s");
        de.put("set.savingDft", "Konfig wird gespeichert und angewendet…");
        de.put("set.savedDft", "✔ Konfig gespeichert und angewendet · verfügbare Backends: %s · Standard: %s");
        de.put("set.savedFallback", " (ausgewähltes Standard-Backend nicht bereit, zurückgefallen — Programm oder SSH-Konfig prüfen)");
        de.put("set.saveDftFail", "✘ Speichern fehlgeschlagen: %s");
        de.put("set.testing", "%s wird getestet …");
        de.put("set.testFail", "✘ Testanfrage fehlgeschlagen: %s");
        de.put("set.localNoModels", "Keine Modelle im lokalen Ollama heruntergeladen");
        de.put("set.downloaded", "Heruntergeladen: %s");
        de.put("set.savingModel", "Modellkonfig wird gespeichert und angewendet…");
        de.put("set.savedModel", "✔ Gespeichert und angewendet · Backend: %s（%s）");
        de.put("set.saveModelFail", "✘ Speichern fehlgeschlagen: %s");
        de.put("set.currentBackend", "Aktuelles Backend: %s（%s）");
        de.put("set.dataLocal", "Daten bleiben lokal");
        de.put("set.dataCloud", "Cloud/Hybrid — Datenabgabe bestätigen");
        de.put("set.backendLocalG16", "Lokales Gaussian16 (local_g16)");
        de.put("set.backendLocalOrca", "Lokales ORCA (local_orca)");
        de.put("set.backendCluster", "SSH-Cluster Gaussian16 (cluster_g16)");
        de.put("set.modelLocal", "Lokal (local) · Daten verlassen nie die Maschine (Standard)");
        de.put("set.modelHybrid", "Hybrid (hybrid) · lokal + komplexe Aufgaben automatisch in die Cloud");
        de.put("set.modelCloud", "Cloud (cloud)");
        de.put("set.aboutHtml", "<b>Zhisuan-Aluminylen-Agent</b> — Aluminylen-Chemie-Forschungsplattform<br>"
                + "· <b>Python-Diensteschicht</b> agent_server.py: ReAct-Agenten-Loop (qwen3.5:2b) + 22 Forschungswerkzeuge + HTTP/SSE-Schnittstelle<br>"
                + "· <b>Java-GUI</b> (dieses Programm): Swing-Arbeitsplatz — KI-Assistent / Moleküloptimierung / Sternenkarte / Spektren / Wissensbasis<br>"
                + "· <b>C++-Rechenkern</b>: molopt.exe Kraftfeld+MDP+Q-Learning-Optimierer (60~1300× schneller als Python); molview.exe OpenGL-3D-Viewer<br>"
                + "· <b>Quantenchemie-Backend</b>: Gaussian16 / ORCA / Multiwfn (hier konfiguriert, SSH-Cluster-Submission unterstützt)<br>"
                + "· Lokale Verbindungsbibliothek: 67 Einträge (52 Aluminylen) · 19 Wissensartikel<br>"
                + "Startreihenfolge: start_zhisuan.bat → Python-Dienst → Java-GUI (automatisch erkennen und verbinden)</div></html>");

        // ---- Workflow ----
        de.put("workflow.fit", "Anpassen");
        de.put("workflow.zoomIn", "Vergrößern");
        de.put("workflow.zoomOut", "Verkleinern");
        de.put("workflow.online", "● verbunden %s");
        de.put("workflow.offline", "○ offline %s");

        // ---- 补齐：knowledge / memory / molview / spectrum / starmap 三语言残留键 ----
        zh.put("kb.placeholder", "提示：可按名称、标题、描述与标签检索。");
        zh.put("kb.loadingContent", "正在加载全文…");
        zh.put("kb.readFail", "读取失败: %s");
        zh.put("kb.itemMeta", "%s · %d 字");
        zh.put("starmap.loadFail", "加载失败: ");
        zh.put("spec.fileHint", "支持 .xyz / .log / .out / .gjf，左键添加多个文件后点“解析”");
        zh.put("spec.demoHint", "载入一段示例光谱数据，快速体验分析流程");
        zh.put("spec.chooseTitle", "选择光谱数据文件");

        en.put("kb.placeholder", "Tip: searchable by name, title, description and tags.");
        en.put("kb.loadingContent", "Loading full text…");
        en.put("kb.readFail", "Read failed: %s");
        en.put("kb.itemMeta", "%s · %d chars");
        en.put("starmap.loadFail", "Load failed: ");
        en.put("spec.fileHint", "Supports .xyz / .log / .out / .gjf; select multiple files then click Parse");
        en.put("spec.demoHint", "Load a sample spectrum to try out the analysis flow");
        en.put("spec.chooseTitle", "Choose spectrum data file");
        en.put("mem.searching", "Searching… (bigram-Dice)");
        en.put("mem.hits3", "3 hits · sorted by relevance");
        en.put("mem.extracted1", "1 atomic fact extracted");
        en.put("mem.extracted", "task/spectrum → insight entry · newly extracted");
        en.put("mol.exportMsg", "View exported to workspace/shot3d.png");
        en.put("mol.infoCenter", "Al center");
        en.put("mol.infoCoord", "Coordinates");
        en.put("mol.infoCalc", "Calculation");
        en.put("mol.picked", "Picked: %s · info card shown");

        de.put("spec.fileHint", "Unterstützt .xyz / .log / .out / .gjf; mehrere Dateien wählen, dann Analysieren");
        de.put("spec.demoHint", "Beispielspektrum laden, um den Analyseablauf auszuprobieren");
        de.put("spec.chooseTitle", "Spektrendatei wählen");

        T.put(ZH, zh);
        T.put(EN, en);
        T.put(DE, de);
    }

    private Langs() {}

    public static String lang() { return current; }

    public static void setLang(String lang) {
        String l = (lang == null || !T.containsKey(lang)) ? ZH : lang;
        if (current.equals(l)) return;
        current = l;
        for (Runnable r : listeners) {
            try { r.run(); } catch (Exception ignored) {}
        }
    }

    public static void addListener(Runnable r) { listeners.add(r); }

    public static String t(String key) {
        String v = T.get(current).get(key);
        return v == null ? key : v;
    }

    public static String t(String key, Object... args) {
        String v = t(key);
        try { return String.format(v, args); } catch (Exception e) { return v; }
    }

    /** 分诊引擎显示名（laya → Laya 决策头 / qwen / rules / 其他原样） */
    public static String engineName(String engine) {
        if (engine == null) return t("audit.unknown");
        switch (engine) {
            case "laya": return t("audit.engine.laya");
            case "qwen": return t("audit.engine.qwen");
            case "rules": return t("audit.engine.rules");
            default: return engine;
        }
    }

    public static String sysPrompt() { return t("sys.prompt"); }
}
