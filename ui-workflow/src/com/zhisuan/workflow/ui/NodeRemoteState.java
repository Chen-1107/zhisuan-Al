package com.zhisuan.workflow.ui;

import com.zhisuan.workflow.model.NodeStatus;

import java.util.Collections;
import java.util.List;

/** 后端 /api/workflow/status 下发的单个节点远程状态（真实任务数据，替代 M1 模拟）。 */
public final class NodeRemoteState {

    public final NodeStatus status;
    public final String message;
    public final String energy;
    public final List<String> logTail;
    /** 计算程序名（如本地 Gaussian16 / WSL 中的 QE / 容器中的 PySCF）。 */
    public final String program;
    /** 输入文件格式示例 / 实际提交内容。 */
    public final String inputFormat;
    /** Multiwfn 分析功能（如 Mayer 键级）。 */
    public final String analysis;

    public NodeRemoteState(NodeStatus status, String message, String energy, List<String> logTail,
                           String program, String inputFormat, String analysis) {
        this.status = status;
        this.message = message;
        this.energy = energy;
        this.logTail = logTail == null ? Collections.emptyList() : logTail;
        this.program = program;
        this.inputFormat = inputFormat;
        this.analysis = analysis;
    }

    /** 将后端状态字符串（WAITING/RUNNING/COMPLETED/FAILED/RERUN）映射为枚举。 */
    public static NodeStatus parseStatus(String s) {
        if (s == null) {
            return NodeStatus.WAITING;
        }
        switch (s.trim().toUpperCase()) {
            case "RUNNING":
                return NodeStatus.RUNNING;
            case "COMPLETED":
            case "DONE":
            case "OK":
                return NodeStatus.COMPLETED;
            case "FAILED":
                return NodeStatus.FAILED;
            case "RERUN":
            case "INTERRUPTED":
                return NodeStatus.RERUN;
            case "CANCELLED":
                return NodeStatus.CANCELLED;
            case "WAITING":
            default:
                return NodeStatus.WAITING;
        }
    }
}