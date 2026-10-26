package com.zhisuan.workflow.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工作流图：节点与连线的容器，提供查找、删除与重置能力。 */
public class WorkflowGraph {

    private final Map<String, WorkflowNode> nodes = new LinkedHashMap<>();
    private final Map<String, WorkflowEdge> edges = new LinkedHashMap<>();

    public void addNode(WorkflowNode node) {
        nodes.put(node.getId(), node);
    }

    public void addEdge(WorkflowEdge edge) {
        edges.put(edge.getId(), edge);
    }

    public WorkflowNode getNode(String id) {
        return nodes.get(id);
    }

    public WorkflowEdge getEdge(String id) {
        return edges.get(id);
    }

    public List<WorkflowNode> getNodes() {
        return new ArrayList<>(nodes.values());
    }

    public List<WorkflowEdge> getEdges() {
        return new ArrayList<>(edges.values());
    }

    public List<WorkflowEdge> edgesFrom(String nodeId) {
        List<WorkflowEdge> result = new ArrayList<>();
        for (WorkflowEdge e : edges.values()) {
            if (e.getSourceId().equals(nodeId)) {
                result.add(e);
            }
        }
        return result;
    }

    public List<WorkflowEdge> edgesTo(String nodeId) {
        List<WorkflowEdge> result = new ArrayList<>();
        for (WorkflowEdge e : edges.values()) {
            if (e.getTargetId().equals(nodeId)) {
                result.add(e);
            }
        }
        return result;
    }

    /** 删除节点并级联删除关联连线。 */
    public void removeNode(String id) {
        nodes.remove(id);
        edges.entrySet().removeIf(e -> e.getValue().getSourceId().equals(id)
                || e.getValue().getTargetId().equals(id));
    }

    public void removeEdge(String id) {
        edges.remove(id);
    }

    /** 将所有节点/连线状态重置为 WAITING，active 分支全部恢复。 */
    public void resetStatus() {
        for (WorkflowNode n : nodes.values()) {
            n.setStatus(NodeStatus.WAITING);
            n.setStatusMessage("");
        }
        for (WorkflowEdge e : edges.values()) {
            e.setStatus(NodeStatus.WAITING);
            e.setActive(true);
        }
    }

    /**
     * 构建智算铝宾默认演示工作流：
     * 分子输入 → 【固定-放开法 MDP 子链】→ DFT 计算 → 日志分诊(Laya) → 判定分支 → 谱分析 → 报告生成；
     * 知识库(RAG)向分诊注入相似案例，判定分支提供 RERUN 回环。
     *
     * 子链把“分子优化 mdp_optimize 与固定-放开法 transition_state_search”合并显现为连续阶段：
     * 构象嵌入 → MDP 优化 → 约束收敛  (MDP 段)
     * → 复合物构建 → Al···C 松弛扫描 → TS 搜索 → 虚频验证  (固定-放开法段)
     */
    public static WorkflowGraph demoGraph() {
        WorkflowGraph g = new WorkflowGraph();

        // 蛇形布局：上排主流程 → MDP 子链 → 反向第二排 TS 子链 → 第三排下游主流程，
        // 跨排用垂直连线衔接（mdp_verify↔ts_build、ts_verify↔dft）。
        // 最左列(x=30)为编排入口链 chat→agent，主流程整体右移一列(x1=280)为其让位。
        // 行距与列距一致，保证整机可见；fitView 自动适配整图包围盒。
        int x1 = 280;
        int gap    = 250;  // node width 220 + 30 spacing
        int row1 = 40;
        int row2 = 320;
        int row3 = 560;

        // ---- 编排入口链（Chat → LLM Agent → 调度下游确定性流水线） ----
        WorkflowNode chat = new WorkflowNode("chat", WorkflowNodeType.CHAT,
                "对话输入", x1 - gap, row1);
        chat.setDescription("用户在对话栏下发自然语言指令，如：\"用固定-放开法优化该结构并验证虚频\"。");
        chat.setStatus(NodeStatus.COMPLETED);
        chat.setStatusMessage("已接收指令");

        WorkflowNode agent = new WorkflowNode("agent", WorkflowNodeType.LLM_AGENT,
                "LLM Agent", x1 - gap, row2);
        agent.setDescription("agent_server 驱动的 LLM 调度：解析意图，决策调用"
                + " mol_optimizer / rag_search / diagnose_job / QE / PySCF 驱动下游确定性节点；"
                + " DFT 参数与定量结论不经过 LLM。");
        agent.setStatus(NodeStatus.COMPLETED);
        agent.setStatusMessage("已调度下游流水线");

        WorkflowNode input = new WorkflowNode("input", WorkflowNodeType.INPUT,
                "分子输入", x1, row1);
        input.setDescription("接收 SMILES / XYZ 结构，经 RDKit 校验总电子数奇偶与自旋态。");
        input.setStatus(NodeStatus.COMPLETED);
        input.setStatusMessage("qwen_c1 结构就绪");

        // ---- MDP 结构优化段 ----
        WorkflowNode mdpEmbed = new WorkflowNode("mdp_embed", WorkflowNodeType.MDP_STAGE,
                "构象嵌入", x1 + gap, row1);
        mdpEmbed.setDescription("ETKDG 嵌入 + RDKit 校验电子数奇偶，生成初始三维构象。");
        mdpEmbed.setStatus(NodeStatus.COMPLETED);
        mdpEmbed.setStatusMessage("构象生成");

        WorkflowNode mdpOpt = new WorkflowNode("mdp_opt", WorkflowNodeType.MDP_STAGE,
                "MDP 优化", x1 + 2 * gap, row1);
        mdpOpt.setDescription("C++ 核心 molopt.exe 路径系综 MDP 强化学习，注入参考键长约束。");
        mdpOpt.setStatus(NodeStatus.COMPLETED);
        mdpOpt.setStatusMessage("约束能量收敛");

        WorkflowNode mdpVerify = new WorkflowNode("mdp_verify", WorkflowNodeType.MDP_STAGE,
                "约束收敛", x1 + 3 * gap, row1);
        mdpVerify.setDescription("校验力场/约束全部满足，输出 Gaussian 输入文件，进入 TS 准备。");
        mdpVerify.setStatus(NodeStatus.COMPLETED);
        mdpVerify.setStatusMessage("全部约束满足");

        // ---- (前置)轨道引导建模：底物单点 SP → FMO 分析 → 轨道引导逼近过渡态 ----
        WorkflowNode fmoSp = new WorkflowNode("fmo_sp", WorkflowNodeType.FMO_STAGE,
                "底物单点 SP", x1 + 4 * gap, row1);
        fmoSp.setDescription("dft_single_point 求底物电子结构，输出轨道能级，供 FMO 分析。");
        fmoSp.setStatus(NodeStatus.WAITING);
        fmoSp.setStatusMessage("待单点计算");

        WorkflowNode fmoAn = new WorkflowNode("fmo_an", WorkflowNodeType.FMO_STAGE,
                "FMO 分析", x1 + 5 * gap, row1);
        fmoAn.setDescription("analyze_fmo 解析 HOMO/LUMO 能级与组分，结合铝宾空 p 轨道(LUMO)"
                + " 判订进攻位点与端基/面向匹配（福井前线轨道）。");
        fmoAn.setStatus(NodeStatus.WAITING);
        fmoAn.setStatusMessage("待轨道解析");

        WorkflowNode fmoBuild = new WorkflowNode("fmo_build", WorkflowNodeType.FMO_STAGE,
                "轨道引导建模", x1 + 6 * gap, row1);
        fmoBuild.setDescription("按轨道对称守恒（Woodward-Hoffmann）推断允许的 TS 构型，"
                + "选定 η¹/η² 进攻模式生成初始几何，进入复合物构建。");
        fmoBuild.setStatus(NodeStatus.WAITING);
        fmoBuild.setStatusMessage("待初始几何");

        // ---- 固定-放开法 TS 搜索段（自右向左排布，垂直衔接上下排） ----
        WorkflowNode tsBuild = new WorkflowNode("ts_build", WorkflowNodeType.TS_SEARCH,
                "复合物构建", x1 + 3 * gap, row2);
        tsBuild.setDescription("按进攻模式构建 Al···CO₂ 复合物（η²-[2+2] 或 η¹-O 端返攻）。");
        tsBuild.setStatus(NodeStatus.COMPLETED);
        tsBuild.setStatusMessage("复合物就绪");

        WorkflowNode tsScan = new WorkflowNode("ts_scan", WorkflowNodeType.TS_SEARCH,
                "Al···C 松弛扫描", x1 + 2 * gap, row2);
        tsScan.setDescription("（固定）沿 Al···C 距离约束优化扫描，捕捉 CO₂ 弯曲活化鞍点。");
        tsScan.setStatus(NodeStatus.COMPLETED);
        tsScan.setStatusMessage("扫描完成");

        WorkflowNode tsTS = new WorkflowNode("ts_ts", WorkflowNodeType.TS_SEARCH,
                "TS 搜索", x1 + gap, row2);
        tsTS.setDescription("（放开）Opt=(TS,CalcFC,NoEigenTest)，MaxCycles=200 全自由度搜索过渡态。");
        tsTS.setStatus(NodeStatus.RUNNING);
        tsTS.setStatusMessage("Opt=(TS) 收敛中");

        WorkflowNode tsVerify = new WorkflowNode("ts_verify", WorkflowNodeType.TS_SEARCH,
                "虚频验证", x1, row2);
        tsVerify.setDescription("TS 后频率计算，验证唯一虚频方向指向产物，确认过渡态归属。");
        tsVerify.setStatus(NodeStatus.WAITING);
        tsVerify.setStatusMessage("待频率验证");

        // ---- 下排：DFT 主作业与下游 ----
        WorkflowNode dft = new WorkflowNode("dft", WorkflowNodeType.DFT_JOB,
                "DFT 计算", x1, row3);
        dft.setDescription("集群作业：B3LYP/def2SVP 单点、IRC、NBO、频率等确认计算。");
        dft.setStatus(NodeStatus.RUNNING);
        dft.setStatusMessage("c1_tsopt 收敛中");

        WorkflowNode diag = new WorkflowNode("diag", WorkflowNodeType.DIAGNOSE,
                "日志分诊", x1 + gap, row3);
        diag.setDescription("diagnose_job 三级降级：Laya → 本地 qwen3.5:2b → 确定性规则；"
                + "自旋失配 / 坐标损坏 / 致命错误强制 RERUN。");
        diag.setStatus(NodeStatus.WAITING);
        diag.setStatusMessage("等待作业日志");

        WorkflowNode decision = new WorkflowNode("decision", WorkflowNodeType.DECISION,
                "判定分支", x1 + 2 * gap, row3);
        decision.setDescription("按诊断结果分流：OK/可信 → 谱分析；RERUN → 回退 DFT 重跑。");
        decision.setStatus(NodeStatus.WAITING);
        decision.setStatusMessage("");

        WorkflowNode knowledge = new WorkflowNode("knowledge", WorkflowNodeType.KNOWLEDGE,
                "知识库 / RAG", x1 + gap - 40, row3 - 130);
        knowledge.setDescription("TF-IDF + Ollama embedding 语义检索，向日志分诊注入相似案例与领域规则。");
        knowledge.setStatus(NodeStatus.COMPLETED);
        knowledge.setStatusMessage("已注入 22 份历史案例");

        WorkflowNode multiwfn = new WorkflowNode("multiwfn", WorkflowNodeType.MULTIWFN,
                "谱分析", x1 + 3 * gap, row3);
        multiwfn.setDescription("Multiwfn 分析 fchk：Mayer 键级、NPA 电荷、轨道能级、IR 谱。");
        multiwfn.setStatus(NodeStatus.WAITING);
        multiwfn.setStatusMessage("");

        WorkflowNode report = new WorkflowNode("report", WorkflowNodeType.REPORT,
                "报告生成", x1 + 4 * gap, row3);
        report.setDescription("汇总势能面、能垒与谱学数据，输出中期/结题报告。");
        report.setStatus(NodeStatus.WAITING);
        report.setStatusMessage("");

        g.addNode(chat);
        g.addNode(agent);
        g.addNode(input);
        g.addNode(mdpEmbed);
        g.addNode(mdpOpt);
        g.addNode(mdpVerify);
        g.addNode(fmoSp);
        g.addNode(fmoAn);
        g.addNode(fmoBuild);
        g.addNode(tsBuild);
        g.addNode(tsScan);
        g.addNode(tsTS);
        g.addNode(tsVerify);
        g.addNode(dft);
        g.addNode(diag);
        g.addNode(decision);
        g.addNode(knowledge);
        g.addNode(multiwfn);
        g.addNode(report);

        // 子流程（固定-放开法 MDP）：input → MDP段 → TS段 → DFT
        // 编排入口链：chat → agent →（下发）→ input，agent 作为执行者触发整条确定性流水线。
        WorkflowEdge eChat = new WorkflowEdge("e-chat-agent", "chat", "agent");
        eChat.setLabel("指令解析");
        eChat.setSourceAnchor(WorkflowEdge.EdgeAnchor.BOTTOM);
        eChat.setTargetAnchor(WorkflowEdge.EdgeAnchor.TOP);
        WorkflowEdge eAgent = new WorkflowEdge("e-agent-input", "agent", "input");
        eAgent.setLabel("下发调度 · 触发流水线");
        add(g, "e-input-mdp", "input", "mdp_embed", "SMILES / XYZ");
        add(g, "e-mdp1", "mdp_embed", "mdp_opt", "初始构象");
        add(g, "e-mdp2", "mdp_opt", "mdp_verify", "约束达标");
        // ---- 前置换入：约束收敛 → 【底物单点 SP → FMO 分析 → 轨道引导建模】 → 复合物构建 ----
        WorkflowEdge eMdp3 = add(g, "e-mdp3", "mdp_verify", "fmo_sp", "进入轨道引导");
        eMdp3.setSourceAnchor(WorkflowEdge.EdgeAnchor.BOTTOM);
        eMdp3.setTargetAnchor(WorkflowEdge.EdgeAnchor.TOP);
        add(g, "e-fmo1", "fmo_sp", "fmo_an", "轨道能级");
        WorkflowEdge eFmo2 = add(g, "e-fmo2", "fmo_an", "fmo_build", "HOMO/LUMO 组分");
        eFmo2.setSourceAnchor(WorkflowEdge.EdgeAnchor.BOTTOM);
        eFmo2.setTargetAnchor(WorkflowEdge.EdgeAnchor.TOP);
        WorkflowEdge eFmo3 = add(g, "e-fmo3", "fmo_build", "ts_build", "TS 初始几何");
        eFmo3.setSourceAnchor(WorkflowEdge.EdgeAnchor.BOTTOM);
        eFmo3.setTargetAnchor(WorkflowEdge.EdgeAnchor.TOP); // 垂直/折线落回 TS 顶排锚点
        add(g, "e-ts1", "ts_build", "ts_scan", "复合物");
        add(g, "e-ts2", "ts_scan", "ts_ts", "鞍点猜测");
        add(g, "e-ts3", "ts_ts", "ts_verify", "TS 几何");
        WorkflowEdge eTs4 = add(g, "e-ts4", "ts_verify", "dft", "虚频确认");
        eTs4.setSourceAnchor(WorkflowEdge.EdgeAnchor.BOTTOM);
        eTs4.setTargetAnchor(WorkflowEdge.EdgeAnchor.TOP);

        // 虚频验证不通过的回退分支（两级）：
        //   1) 回 ts_ts 微调 TS 猜测；仍不通过 2) 回 ts_build 调整初始模型/进攻模式。
        WorkflowEdge fb1 = new WorkflowEdge("e-ts-fallback-1", "ts_verify", "ts_ts");
        fb1.setLabel("虚频失配 → 修正 TS 猜测");
        fb1.setSourceAnchor(WorkflowEdge.EdgeAnchor.RIGHT);
        fb1.setTargetAnchor(WorkflowEdge.EdgeAnchor.LEFT);
        fb1.setActive(false);
        WorkflowEdge fb2 = new WorkflowEdge("e-ts-fallback-2", "ts_verify", "fmo_build");
        fb2.setLabel("仍失配 → 调初始模型");
        fb2.setSourceAnchor(WorkflowEdge.EdgeAnchor.TOP);
        fb2.setTargetAnchor(WorkflowEdge.EdgeAnchor.LEFT);
        fb2.setActive(false);

        WorkflowEdge e1 = new WorkflowEdge("e-knowledge-diag", "knowledge", "diag");
        e1.setLabel("相似案例注入");
        e1.setSourceAnchor(WorkflowEdge.EdgeAnchor.BOTTOM);
        e1.setTargetAnchor(WorkflowEdge.EdgeAnchor.TOP);
        WorkflowEdge e3 = new WorkflowEdge("e-dft-diag", "dft", "diag");
        e3.setLabel("作业日志");
        WorkflowEdge e4 = new WorkflowEdge("e-diag-decision", "diag", "decision");
        e4.setLabel("诊断结果");
        WorkflowEdge e5 = new WorkflowEdge("e-decision-multiwfn", "decision", "multiwfn");
        e5.setLabel("OK / 可信");
        WorkflowEdge e7 = new WorkflowEdge("e-multiwfn-report", "multiwfn", "report");
        e7.setLabel("谱数据");
        WorkflowEdge e6 = new WorkflowEdge("e-decision-dft", "decision", "dft");
        e6.setLabel("RERUN 重跑");
        e6.setSourceAnchor(WorkflowEdge.EdgeAnchor.LEFT);
        e6.setTargetAnchor(WorkflowEdge.EdgeAnchor.BOTTOM);
        e6.setActive(false);

        g.addEdge(e1);
        g.addEdge(e3);
        g.addEdge(e4);
        g.addEdge(e5);
        g.addEdge(e6);
        g.addEdge(e7);
        g.addEdge(eChat);
        g.addEdge(eAgent);
        g.addEdge(fb1);
        g.addEdge(fb2);

        return g;
    }

    private static WorkflowEdge add(WorkflowGraph g, String id,
                                    String from, String to, String label) {
        WorkflowEdge e = new WorkflowEdge(id, from, to);
        e.setLabel(label);
        g.addEdge(e);
        return e;
    }
}
