package com.zhisuan.workflow.model;

/** 节点类型：对应智算铝宾工作流中的功能模块。 */
public enum WorkflowNodeType {
    CHAT("对话入口"),
    LLM_AGENT("LLM 智能体"),
    INPUT("分子输入"),
    MOL_OPTIMIZER("分子优化"),
    MDP_STAGE("MDP 结构优化"),
    TS_SEARCH("过渡态搜索"),
    FMO_STAGE("轨道引导"),
    DFT_JOB("DFT 计算"),
    DIAGNOSE("日志分诊"),
    DECISION("判定分支"),
    MULTIWFN("谱分析"),
    REPORT("报告生成"),
    KNOWLEDGE("知识库");

    private final String label;

    WorkflowNodeType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
