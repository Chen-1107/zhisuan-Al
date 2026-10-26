package com.zhisuan.workflow.ui;

import com.zhisuan.workflow.model.NodeStatus;
import com.zhisuan.workflow.model.WorkflowNodeType;

import java.awt.Color;
import java.awt.Font;

/** 深色科幻主题：配色、字体与通用色工具。 */
public final class Theme {

    public static final Color BACKGROUND = new Color(0x0F1420);
    public static final Color GRID = new Color(0x1B2436);
    public static final Color GRID_MAJOR = new Color(0x242F47);
    public static final Color PANEL = new Color(0x151C2C);
    public static final Color PANEL_RAISED = new Color(0x1D2740);
    public static final Color BORDER = new Color(0x2E3B55);
    public static final Color PORT = new Color(0x51627F);
    public static final Color NODE_BODY = new Color(0x1C2636);
    public static final Color TEXT_PRIMARY = new Color(0xE8EEF9);
    public static final Color TEXT_SECONDARY = new Color(0xAEB8D4);
    public static final Color ACCENT = new Color(0x35B5FF);

    public static final Color STATUS_WAITING = new Color(0x7C8AA5);
    public static final Color STATUS_RUNNING = new Color(0x4DA3FF);
    public static final Color STATUS_COMPLETED = new Color(0x34D399);
    public static final Color STATUS_FAILED = new Color(0xF87171);
    public static final Color STATUS_RERUN = new Color(0xFBBF24);
    public static final Color STATUS_CANCELLED = new Color(0x55617A);

    private Theme() {
    }

    public static Color statusColor(NodeStatus status) {
        switch (status) {
            case RUNNING:
                return STATUS_RUNNING;
            case COMPLETED:
                return STATUS_COMPLETED;
            case FAILED:
                return STATUS_FAILED;
            case RERUN:
                return STATUS_RERUN;
            case CANCELLED:
                return STATUS_CANCELLED;
            case WAITING:
            default:
                return STATUS_WAITING;
        }
    }

    public static Color typeColor(WorkflowNodeType type) {
        switch (type) {
            case CHAT:
                return new Color(0x22D3EE);
            case LLM_AGENT:
                return new Color(0xA78BFA);
            case INPUT:
                return new Color(0x3B82F6);
            case MOL_OPTIMIZER:
                return new Color(0x8B5CF6);
            case MDP_STAGE:
                return new Color(0xA78BFA);
            case TS_SEARCH:
                return new Color(0xF97316);
            case FMO_STAGE:
                return new Color(0x34D399);
            case DFT_JOB:
                return new Color(0x22D3EE);
            case DIAGNOSE:
                return new Color(0x14B8A6);
            case DECISION:
                return new Color(0xF59E0B);
            case MULTIWFN:
                return new Color(0xEC4899);
            case REPORT:
                return new Color(0x34D399);
            case KNOWLEDGE:
            default:
                return new Color(0x6366F1);
        }
    }

    public static Font uiFont(int size) {
        return new Font("Microsoft YaHei UI", Font.PLAIN, size);
    }

    public static Font uiFontBold(int size) {
        return new Font("Microsoft YaHei UI", Font.BOLD, size);
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** 按 t 比例混合两色：t=0 返回 a，t=1 返回 b。 */
    public static Color blend(Color a, Color b, double t) {
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, g, bl);
    }
}
