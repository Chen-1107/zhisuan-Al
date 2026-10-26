package com.zhisuan.workflow.model;

/** 节点/连线运行状态，M2 实时状态更新时复用。 */
public enum NodeStatus {
    WAITING("排队中"),
    RUNNING("运行中"),
    COMPLETED("已完成"),
    FAILED("失败"),
    RERUN("需重跑"),
    CANCELLED("已取消");

    private final String label;

    NodeStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
