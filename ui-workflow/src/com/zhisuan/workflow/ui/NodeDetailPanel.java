package com.zhisuan.workflow.ui;

import com.zhisuan.workflow.model.WorkflowNode;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

/** 右侧节点详情面板：展示选中节点的类型、状态、能量与日志尾部（真实任务数据）。 */
public class NodeDetailPanel extends JPanel {

    private final JLabel typeValue;
    private final JLabel idValue;
    private final JLabel statusValue;
    private final JLabel msgValue;
    private final JLabel energyValue;
    private final JLabel programValue;
    private final JLabel analysisValue;
    private final JLabel inputFormatValue;
    private final JLabel suggestionValue;
    private final JTextArea logArea;
    private final JLabel descValue;

    /** 虚频验证节点修正建议的强调色（与 TS_SEARCH 类型配色一致）。 */
    private static final java.awt.Color TS_SUGGESTION_COLOR = new java.awt.Color(0xF97316);

    public NodeDetailPanel() {
        setBackground(Theme.PANEL);
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER));
        setPreferredSize(new Dimension(320, 0));
        setLayout(new BorderLayout());

        JLabel title = new JLabel("节点详情");
        title.setFont(Theme.uiFontBold(15));
        title.setForeground(Theme.TEXT_PRIMARY);
        title.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 4, 0);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        typeValue = valueLabel();
        idValue = valueLabel();
        statusValue = valueLabel();
        msgValue = valueLabel();
        energyValue = valueLabel();
        programValue = valueLabel();
        analysisValue = valueLabel();
        inputFormatValue = valueLabel();
        suggestionValue = valueLabel();
        descValue = valueLabel();

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(Theme.uiFont(11));
        logArea.setBackground(Theme.PANEL_RAISED);
        logArea.setForeground(Theme.TEXT_SECONDARY);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setOpaque(false);
        logScroll.getViewport().setOpaque(false);
        logScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        logScroll.setPreferredSize(new Dimension(280, 120));
        logScroll.getVerticalScrollBar().setUnitIncrement(12);

        addRow(body, c, 0, "类型", typeValue);
        addRow(body, c, 1, "标识", idValue);
        addRow(body, c, 2, "状态", statusValue);
        addRow(body, c, 3, "状态信息", msgValue);
        addRow(body, c, 4, "能量", energyValue);
        addRow(body, c, 5, "计算程序", programValue);
        addRow(body, c, 6, "分析方法", analysisValue);
        addRow(body, c, 7, "输入格式", inputFormatValue);
        addRow(body, c, 8, "虚频修正建议", suggestionValue);
        addScrollRow(body, c, 9, "日志尾部", logScroll);
        addRow(body, c, 10, "说明", descValue);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        add(title, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        clear();
    }

    /** 无远程快照时仅显示节点静态信息。 */
    public void showNode(WorkflowNode n) {
        showNode(n, null);
    }

    /** 显示节点；若附带远程快照则同时填充能量与日志尾部。 */
    public void showNode(WorkflowNode n, NodeRemoteState st) {
        if (n == null) {
            clear();
            return;
        }
        setValue(typeValue, n.getType().getLabel(), Theme.TEXT_PRIMARY);
        setValue(idValue, n.getId(), Theme.TEXT_PRIMARY);
        statusValue.setForeground(Theme.statusColor(n.getStatus()));
        setValue(statusValue, n.getStatus().getLabel(), Theme.statusColor(n.getStatus()));
        String msg = n.getStatusMessage() == null || n.getStatusMessage().isEmpty()
                ? "—" : n.getStatusMessage();
        setValue(msgValue, msg, Theme.TEXT_PRIMARY);

        if (st != null && (st.energy != null && !st.energy.isEmpty())) {
            setValue(energyValue, st.energy + " a.u.", Theme.ACCENT);
        } else {
            setValue(energyValue, "—", Theme.TEXT_SECONDARY);
        }

        setValue(programValue, plainOrDash(st.program), Theme.TEXT_PRIMARY);
        setValue(analysisValue, plainOrDash(st.analysis), Theme.TEXT_PRIMARY);
        setValue(inputFormatValue, wrap(plainOrDash(st.inputFormat)), Theme.TEXT_SECONDARY);
        setValue(suggestionValue, suggestionFor(n), TS_SUGGESTION_COLOR);

        if (st != null && st.logTail != null && !st.logTail.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String line : st.logTail) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            logArea.setText(sb.toString());
        } else {
            logArea.setText("（无执行日志）");
        }
        logArea.setCaretPosition(0);

        String desc = n.getDescription() == null || n.getDescription().isEmpty()
                ? "—" : n.getDescription();
        setValue(descValue, wrap(desc), Theme.TEXT_PRIMARY);
    }

    public void clear() {
        setValue(typeValue, "—", Theme.TEXT_SECONDARY);
        setValue(idValue, "—", Theme.TEXT_SECONDARY);
        setValue(statusValue, "未选中节点", Theme.TEXT_SECONDARY);
        setValue(msgValue, "点击画布中的节点查看详情", Theme.TEXT_SECONDARY);
        setValue(energyValue, "—", Theme.TEXT_SECONDARY);
        setValue(programValue, "—", Theme.TEXT_SECONDARY);
        setValue(analysisValue, "—", Theme.TEXT_SECONDARY);
        setValue(inputFormatValue, "—", Theme.TEXT_SECONDARY);
        setValue(suggestionValue, "—", Theme.TEXT_SECONDARY);
        logArea.setText("（无执行日志）");
        setValue(descValue, wrap("支持缩放（滚轮）、平移（拖空白处）、拖动节点（拖节点）。"),
                Theme.TEXT_SECONDARY);
    }

    private static String plainOrDash(String s) {
        return s == null || s.trim().isEmpty() ? "—" : s;
    }

    /** 虚频验证失配时的两级修正建议；非虚频节点返回 “—”。 */
    private static String suggestionFor(WorkflowNode n) {
        if (n == null || !"ts_verify".equals(n.getId())) {
            return "—";
        }
        return "<html><div style='width:250px'>"
                + "· 未检出虚频 → 回「复合物构建」换进攻模式(η²↔η¹)或改 Al···CO₂ 初始距离<br>"
                + "· 2+ 个虚频 → 沿最大虚频方向扰动初始几何，回「TS 搜索」重搜<br>"
                + "· 1 个虚频方向不对 → 调初始几何使本征矢量指向产物端<br>"
                + "· 仍不收敛 → 换 ωB97X-D/def2 系或拉长 MaxCycles"
                + "</div></html>";
    }

    private void setValue(JLabel label, String text, java.awt.Color color) {
        label.setText(text);
        label.setForeground(color);
    }

    private static String wrap(String s) {
        return "<html><div style='width:250px'>" + s.replace("\n", "<br>") + "</div></html>";
    }

    private JLabel valueLabel() {
        JLabel l = new JLabel();
        l.setFont(Theme.uiFont(12));
        return l;
    }

    private void addRow(JPanel body, GridBagConstraints c, int row, String key, JLabel value) {
        JLabel k = new JLabel(key);
        k.setFont(Theme.uiFont(12));
        k.setForeground(Theme.TEXT_SECONDARY);
        k.setPreferredSize(new Dimension(62, 18));
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        c.insets = new Insets(4, 16, 4, 4);
        body.add(k, c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(4, 4, 4, 16);
        body.add(value, c);
    }

    private void addScrollRow(JPanel body, GridBagConstraints c, int row, String key,
                              JScrollPane value) {
        JLabel k = new JLabel(key);
        k.setFont(Theme.uiFont(12));
        k.setForeground(Theme.TEXT_SECONDARY);
        k.setPreferredSize(new Dimension(62, 18));
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(4, 16, 4, 4);
        body.add(k, c);
        c.gridx = 1;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 16);
        body.add(value, c);
    }
}
