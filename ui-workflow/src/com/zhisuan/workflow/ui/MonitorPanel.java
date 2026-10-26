package com.zhisuan.workflow.ui;

import com.zhisuan.workflow.model.NodeStatus;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 底部监控抽屉（复刻 n8n 底边栏）：
 * 可折叠，含【执行记录】【日志】【节点状态】标签页；演示推进/回环时填充作业与日志。
 * 顶部标签条为一条连续横带，底部用 ACCENT 高亮线突出。
 */
public class MonitorPanel extends JPanel {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int CONTENT_H = 232;

    private final Runnable onGraphChanged;
    private final ExecutionListPanel execPanel = new ExecutionListPanel();
    private final JTextArea logArea = buildLogArea();
    private final StatsPanel statsPanel;
    private final JobMonitorPanel jobPanel;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final Map<String, JLabel> tabLabels = new LinkedHashMap<>();
    private JToggleButton expandBtn;
    private boolean expanded = false;
    private int demoStep = 0;

    public MonitorPanel(Runnable onGraphChanged) {
        this(onGraphChanged, JobMonitorPanel.DEFAULT_BACKEND_URL);
    }

    public MonitorPanel(Runnable onGraphChanged, String baseUrl) {
        this.onGraphChanged = onGraphChanged;
        this.statsPanel = new StatsPanel();
        this.jobPanel = new JobMonitorPanel(baseUrl, 3000);

        setLayout(new BorderLayout());
        setBackground(Theme.PANEL);
        setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, Theme.ACCENT));

        cardHost.setBackground(Theme.PANEL);
        cardHost.add(execPanel, "exec");
        cardHost.add(new LogScroll(logArea), "log");
        cardHost.add(statsPanel, "stats");
        cardHost.add(jobPanel, "jobs");

        add(buildTabBar(), BorderLayout.NORTH);
        add(cardHost, BorderLayout.CENTER);

        selectTab("exec");
        expand();
    }

    /** 关停任务进程子面板的定时轮询（窗口关闭时调用）。 */
    public void stopJobPolling() {
        jobPanel.stop();
    }

    // ---- 顶部标签条（连续横带）----

    private JPanel buildTabBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.PANEL_RAISED);
        bar.setBorder(new EmptyBorder(0, 12, 0, 8));

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabs.setBackground(Theme.PANEL_RAISED);
        tabs.add(tab("执行记录", "exec"));
        tabs.add(tab("日志", "log"));
        tabs.add(tab("节点状态", "stats"));
        tabs.add(tab("任务进程", "jobs"));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        right.setBackground(Theme.PANEL_RAISED);
        right.add(toolButton("▶ 模拟推进", this::advance));
        right.add(toolButton("↻ 模拟 RERUN", this::rerun));
        right.add(toolButton("清空日志", () -> {
            execPanel.clear();
            logArea.setText("");
        }));

        JToggleButton collapse = new JToggleButton("▼ 收起");
        collapse.setPreferredSize(new Dimension(74, 26));
        collapse.setOpaque(true);
        collapse.setContentAreaFilled(false);
        collapse.setBackground(Theme.PANEL_RAISED);
        collapse.setForeground(Theme.TEXT_PRIMARY);
        collapse.setFont(Theme.uiFont(11));
        collapse.setFocusPainted(false);
        collapse.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        collapse.addActionListener(e -> {
            if (collapse.isSelected()) {
                collapse.setText("▲ 展开");
                collapse();
            } else {
                collapse.setText("▼ 收起");
                expand();
            }
        });
        expandBtn = collapse;
        right.add(collapse);

        bar.add(tabs, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JLabel tab(final String title, final String key) {
        JLabel l = new JLabel(title, SwingConstants.CENTER);
        l.setOpaque(true);
        l.setForeground(Theme.TEXT_SECONDARY);
        l.setFont(Theme.uiFont(11));
        l.setBorder(new EmptyBorder(7, 12, 7, 12));
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectTab(key);
            }
        });
        tabLabels.put(key, l);
        return l;
    }

    private void selectTab(String key) {
        cards.show(cardHost, key);
        for (Map.Entry<String, JLabel> en : tabLabels.entrySet()) {
            boolean active = en.getKey().equals(key);
            JLabel l = en.getValue();
            l.setForeground(active ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY);
            l.setFont(active ? Theme.uiFontBold(11) : Theme.uiFont(11));
            l.setBackground(active ? Theme.PANEL : Theme.PANEL_RAISED);
        }
    }

    private JButton toolButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setPreferredSize(new Dimension(112, 26));
        b.setOpaque(true);
        b.setContentAreaFilled(false);
        b.setBackground(Theme.PANEL_RAISED);
        b.setForeground(Theme.TEXT_PRIMARY);
        b.setFont(Theme.uiFont(11));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        b.addActionListener(e -> action.run());
        return b;
    }

    // ---- 折叠 / 展开 ----

    public void expand() {
        expanded = true;
        cardHost.setVisible(true);
        cardHost.setPreferredSize(new Dimension(0, CONTENT_H));
        expandBtn.setText("▼ 收起");
        expandBtn.setSelected(false);
        revalidate();
        repaint();
    }

    public void collapse() {
        expanded = false;
        cardHost.setVisible(false);
        cardHost.setPreferredSize(new Dimension(0, 0));
        expandBtn.setText("▲ 展开");
        expandBtn.setSelected(true);
        revalidate();
        repaint();
    }

    public boolean isExpanded() {
        return expanded;
    }

    // ---- 演示数据源 ----

    /** 模拟推进：逐步骤推进节点状态，并写入执行记录与日志。 */
    public void advance() {
        String log;
        ExecEntry exec = null;
        switch (demoStep) {
            case 0:
                log = "Step 1/5  DFT 完成：c1_tsopt 收敛，虚频 -204.33 cm^-1 → 日志分诊";
                setStatus("dft", NodeStatus.COMPLETED, "TS 收敛，虚频 -204.33 cm^-1");
                setStatus("diag", NodeStatus.RUNNING, "Laya 分诊中");
                exec = new ExecEntry(nodeName("dft"), "c1_tsopt",
                        NodeStatus.COMPLETED, "TS 收敛 · 虚频 -204.33 cm^-1");
                break;
            case 1:
                log = "Step 2/5  日志分诊：term_norm=True，判 OK / 可信 → 判定分支";
                setStatus("diag", NodeStatus.COMPLETED, "可信：term_norm=True");
                setStatus("decision", NodeStatus.RUNNING, "");
                exec = new ExecEntry(nodeName("diag"), "diag-job",
                        NodeStatus.COMPLETED, "可信：term_norm=True");
                break;
            case 2:
                log = "Step 3/5  判定分支：OK/可信 → 谱分析（Multiwfn）";
                setStatus("decision", NodeStatus.COMPLETED, "OK / 可信 → 谱分析");
                setStatus("multiwfn", NodeStatus.RUNNING, "Multiwfn 分析中");
                setEdgeActive("e-decision-multiwfn", true);
                setEdgeActive("e-decision-dft", false);
                exec = new ExecEntry(nodeName("decision"), "branch-ok",
                        NodeStatus.COMPLETED, "OK / 可信 → 谱分析");
                break;
            case 3:
                log = "Step 4/5  谱分析：Mayer 键级 / NPA 电荷已出 → 报告生成";
                setStatus("multiwfn", NodeStatus.COMPLETED, "Mayer 键级 / NPA 电荷已出");
                setStatus("report", NodeStatus.RUNNING, "汇总报告生成中");
                exec = new ExecEntry(nodeName("multiwfn"), "multiwfn",
                        NodeStatus.COMPLETED, "Mayer 键级 / NPA 电荷已出");
                break;
            case 4:
                log = "Step 5/5  报告生成：中期报告已产出，链路完成";
                setStatus("report", NodeStatus.COMPLETED, "报告已生成");
                exec = new ExecEntry(nodeName("report"), "mid-report",
                        NodeStatus.COMPLETED, "中期报告已产出");
                break;
            default:
                resetDemo();
                return;
        }
        logArea.append(time() + "  " + log + "\n");
        if (exec != null) {
            execPanel.append(exec);
        }
        demoStep = (demoStep + 1);
        statsPanel.refresh();
        onGraphChanged.run();
    }

    /** 模拟 RERUN 回环：判定分支触发 DFT 重跑。 */
    public void rerun() {
        setStatus("dft", NodeStatus.RUNNING, "RERUN 重跑中");
        setStatus("diag", NodeStatus.COMPLETED, "判 RERUN：needs_rerun≥0.5");
        setStatus("decision", NodeStatus.RERUN, "自旋失配 → 回退重跑");
        setStatus("multiwfn", NodeStatus.WAITING, "");
        setStatus("report", NodeStatus.WAITING, "");
        setEdgeActive("e-decision-dft", true);
        setEdgeActive("e-decision-multiwfn", false);
        logArea.append(time() + "  日志分诊判 RERUN：自旋失配（needs_rerun=0.72）→ 回退 DFT 重跑\n");
        execPanel.append(new ExecEntry(nodeName("decision"), "rerun-branch",
                NodeStatus.RERUN, "需重跑 · 回退 DFT"));
        statsPanel.refresh();
        onGraphChanged.run();
    }

    /** 恢复演示初始状态，清空监控数据。 */
    public void resetDemo() {
        demoStep = 0;
        graphReset();
        execPanel.clear();
        logArea.setText("");
        logArea.append(time() + "  已重置演示状态 · 等待执行\n");
        statsPanel.refresh();
        onGraphChanged.run();
    }

    // ---- 内部状态访问（经 WorkflowGraph 静态演示图外置经由 Main 传递不方便，
    // 这里直接操作静态图的引用由构造时注入）----

    private com.zhisuan.workflow.model.WorkflowGraph injectedGraph;

    public void bind(com.zhisuan.workflow.model.WorkflowGraph graph) {
        this.injectedGraph = graph;
        statsPanel.bind(graph);
        graphReset();
    }

    private void setStatus(String id, NodeStatus s, String msg) {
        if (injectedGraph == null) {
            return;
        }
        com.zhisuan.workflow.model.WorkflowNode n = injectedGraph.getNode(id);
        if (n != null) {
            n.setStatus(s);
            n.setStatusMessage(msg);
        }
    }

    private void setEdgeActive(String id, boolean active) {
        if (injectedGraph == null) {
            return;
        }
        com.zhisuan.workflow.model.WorkflowEdge e = injectedGraph.getEdge(id);
        if (e != null) {
            e.setActive(active);
        }
    }

    private void graphReset() {
        if (injectedGraph == null) {
            return;
        }
        injectedGraph.resetStatus();
        // 编排入口与上游子链（固定-放开法 MDP → FMO 引导 → TS 搜索）演示初始即完成，
        // 当前时刻停在「DFT 收敛中」，随后由 advance() 推进下游 分诊→判定→谱分析→报告。
        setStatus("chat", NodeStatus.COMPLETED, "已接收指令");
        setStatus("agent", NodeStatus.COMPLETED, "已调度下游流水线");
        setStatus("input", NodeStatus.COMPLETED, "qwen_c1 结构就绪");
        setStatus("mdp_embed", NodeStatus.COMPLETED, "构象生成");
        setStatus("mdp_opt", NodeStatus.COMPLETED, "约束能量收敛");
        setStatus("mdp_verify", NodeStatus.COMPLETED, "全部约束满足");
        setStatus("fmo_sp", NodeStatus.COMPLETED, "单点完成");
        setStatus("fmo_an", NodeStatus.COMPLETED, "轨道解析完成");
        setStatus("fmo_build", NodeStatus.COMPLETED, "TS 初始几何就绪");
        setStatus("ts_build", NodeStatus.COMPLETED, "复合物就绪");
        setStatus("ts_scan", NodeStatus.COMPLETED, "扫描完成");
        setStatus("ts_ts", NodeStatus.COMPLETED, "TS 收敛");
        setStatus("ts_verify", NodeStatus.COMPLETED, "虚频确认");
        setStatus("knowledge", NodeStatus.COMPLETED, "已注入 22 份历史案例");
        setStatus("dft", NodeStatus.RUNNING, "c1_tsopt 收敛中");
        setEdgeActive("e-decision-dft", false);
    }

    private String nodeName(String id) {
        if (injectedGraph == null) {
            return id;
        }
        com.zhisuan.workflow.model.WorkflowNode n = injectedGraph.getNode(id);
        return n == null ? id : n.getLabel();
    }

    private static String time() {
        return LocalTime.now().format(CLOCK);
    }

    // ---- 执行记录列表（自绘）----

    private static final class ExecEntry {
        final String node;
        final String job;
        final NodeStatus status;
        final String note;
        final String ts;

        ExecEntry(String node, String job, NodeStatus status, String note) {
            this.node = node;
            this.job = job;
            this.status = status;
            this.note = note;
            this.ts = time();
        }
    }

    private static final class ExecutionListPanel extends JPanel {
        private final List<ExecEntry> entries = new ArrayList<>();

        ExecutionListPanel() {
            setBackground(Theme.PANEL);
            setOpaque(true);
        }

        void append(ExecEntry e) {
            entries.add(0, e);
            revalidate();
            repaint();
        }

        void clear() {
            entries.clear();
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, CONTENT_H);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.PANEL);
            g2.fillRect(0, 0, getWidth(), getHeight());

            if (entries.isEmpty()) {
                FontMetrics fm = g2.getFontMetrics(Theme.uiFont(12));
                String msg = "尚无执行记录 · 点击右上角「模拟推进」开始演示";
                g2.setColor(Theme.TEXT_SECONDARY);
                int x = (getWidth() - fm.stringWidth(msg)) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 6;
                g2.drawString(msg, x, y);
                return;
            }

            int rowH = 30;
            int y = 6;
            for (ExecEntry e : entries) {
                if (y + rowH > getHeight()) {
                    break;
                }
                // 状态圆点
                Color sc = Theme.statusColor(e.status);
                g2.setColor(sc);
                g2.fillOval(14, y + (rowH - 10) / 2, 10, 10);
                // 时间戳
                g2.setColor(Theme.TEXT_SECONDARY);
                g2.setFont(Theme.uiFont(11));
                g2.drawString(e.ts, 34, y + (rowH + 8) / 2 - 4);
                // 节点
                g2.setColor(Theme.TEXT_PRIMARY);
                g2.setFont(Theme.uiFontBold(12));
                g2.drawString(e.node, 96, y + (rowH + 8) / 2 - 4);
                // 作业
                g2.setFont(Theme.uiFont(11));
                g2.setColor(Theme.TEXT_SECONDARY);
                g2.drawString(e.job, 96 + g2.getFontMetrics().stringWidth(e.node) + 22,
                        y + (rowH + 8) / 2 - 4);
                // 说明
                g2.setColor(Theme.TEXT_SECONDARY);
                g2.drawString(e.note, 300, y + (rowH + 8) / 2 - 4);
                // 分隔线
                g2.setColor(Theme.BORDER);
                g2.fillRect(14, y + rowH - 1, getWidth() - 28, 1);
                y += rowH;
            }
        }
    }

    // ---- 节点状态统计 ----

    private static final class StatsPanel extends JPanel {
        private com.zhisuan.workflow.model.WorkflowGraph bound;
        private final Map<NodeStatus, Integer> counts = new LinkedHashMap<>();

        StatsPanel() {
            setBackground(Theme.PANEL);
            setOpaque(true);
        }

        void bind(com.zhisuan.workflow.model.WorkflowGraph graph) {
            this.bound = graph;
            refresh();
        }

        void refresh() {
            recount();
            repaint();
        }

        private void recount() {
            counts.clear();
            int total = 0;
            if (bound != null) {
                for (com.zhisuan.workflow.model.WorkflowNode n : bound.getNodes()) {
                    counts.merge(n.getStatus(), 1, Integer::sum);
                    total++;
                }
            }
            counts.put(null, total);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, CONTENT_H);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.PANEL);
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setFont(Theme.uiFontBold(13));
            g2.setColor(Theme.TEXT_PRIMARY);
            g2.drawString("节点状态监控", 20, 28);

            NodeStatus[] order = {NodeStatus.RUNNING, NodeStatus.COMPLETED, NodeStatus.RERUN,
                    NodeStatus.FAILED, NodeStatus.WAITING};
            String[] names = {"运行中", "已完成", "需重跑", "失败", "排队中"};
            int cardW = 130;
            int cardH = 64;
            int gap = 12;
            int x = 20;
            int y = 46;
            for (int i = 0; i < order.length; i++) {
                drawCard(g2, x, y, cardW, cardH, Theme.statusColor(order[i]), names[i],
                        counts.getOrDefault(order[i], 0));
                x += cardW + gap;
            }
            // 汇总
            drawCard(g2, x, y, cardW, cardH, Theme.ACCENT, "节点总数",
                    counts.getOrDefault(null, 0));
        }

        private void drawCard(Graphics2D g2, int x, int y, int w, int h, Color accent,
                              String name, int value) {
            g2.setColor(Theme.NODE_BODY);
            g2.fillRoundRect(x, y, w, h, 8, 8);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(x, y, w, h, 8, 8);
            // 顶部色条
            g2.setColor(accent);
            g2.fillRect(x, y, w, 3);
            g2.setColor(Theme.TEXT_SECONDARY);
            g2.setFont(Theme.uiFont(11));
            g2.drawString(name, x + 12, y + 22);
            g2.setFont(Theme.uiFontBold(24));
            g2.setColor(accent);
            g2.drawString(String.valueOf(value), x + 12, y + h - 12);
        }
    }

    // ---- 日志区 ----

    private static JTextArea buildLogArea() {
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        ta.setBackground(Theme.PANEL);
        ta.setForeground(Theme.TEXT_PRIMARY);
        ta.setCaretColor(Theme.ACCENT);
        // 日志区字体用中文字体：Consolas 无中文/上标字形会渲染成 □ 占位符，
        // 微软雅黑完整覆盖中文、拉丁、箭头与 °、· 等科学符号。
        ta.setFont(Theme.uiFont(12));
        return ta;
    }

    private static final class LogScroll extends JScrollPane {
        LogScroll(JTextArea ta) {
            super(ta);
            setBorder(null);
            setBackground(Theme.PANEL);
            getViewport().setBackground(Theme.PANEL);
        }
    }
}