package com.zhisuan.workflow.model;

/** 工作流节点：携带位置、尺寸、状态与描述信息。 */
public class WorkflowNode {

    private final String id;
    private final WorkflowNodeType type;
    private String label;
    private double x;
    private double y;
    private double width;
    private double height;
    private NodeStatus status;
    private String statusMessage;
    private String description;

    public WorkflowNode(String id, WorkflowNodeType type, String label, double x, double y) {
        this.id = id;
        this.type = type;
        this.label = label;
        this.x = x;
        this.y = y;
        this.width = 220;
        this.height = 88;
        this.status = NodeStatus.WAITING;
        this.statusMessage = "";
        this.description = "";
    }

    public boolean contains(double px, double py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    public String getId() {
        return id;
    }

    public WorkflowNodeType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
