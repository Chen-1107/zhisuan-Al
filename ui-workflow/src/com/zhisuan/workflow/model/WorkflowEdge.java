package com.zhisuan.workflow.model;

/** 工作流连线：连接两个节点，active 表示该分支当前是否被走通。 */
public class WorkflowEdge {

    /** 连线锚点：AUTO 按流向自动选择左右端口，其余强制指定端口方位。 */
    public enum EdgeAnchor {
        AUTO, LEFT, RIGHT, TOP, BOTTOM
    }

    private final String id;
    private final String sourceId;
    private final String targetId;
    private String label;
    private NodeStatus status;
    private boolean active;
    private EdgeAnchor sourceAnchor = EdgeAnchor.AUTO;
    private EdgeAnchor targetAnchor = EdgeAnchor.AUTO;

    public WorkflowEdge(String id, String sourceId, String targetId) {
        this.id = id;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.label = "";
        this.status = NodeStatus.WAITING;
        this.active = true;
    }

    public String getId() {
        return id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public EdgeAnchor getSourceAnchor() {
        return sourceAnchor;
    }

    public void setSourceAnchor(EdgeAnchor sourceAnchor) {
        this.sourceAnchor = sourceAnchor;
    }

    public EdgeAnchor getTargetAnchor() {
        return targetAnchor;
    }

    public void setTargetAnchor(EdgeAnchor targetAnchor) {
        this.targetAnchor = targetAnchor;
    }
}
