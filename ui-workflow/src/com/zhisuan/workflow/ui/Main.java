package com.zhisuan.workflow.ui;

import com.zhisuan.workflow.model.NodeStatus;
import com.zhisuan.workflow.model.WorkflowGraph;
import com.zhisuan.workflow.model.WorkflowNode;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** M1/M2 主窗口：顶部工具栏 + 画布 + 详情面板 + 底部监控抽屉 + 状态栏 + 状态轮询。 */
public class Main {

    private static final Dimension BUTTON_SIZE = new Dimension(96, 30);
    /** 后端地址：默认 agent_server.py (127.0.0.1:8765)，可用 -Dzhisuan.backend=http://host:port 覆盖。 */
    private static final String BACKEND_URL = System.getProperty("zhisuan.backend", "http://127.0.0.1:8765");
    private static final int POLL_MS = 3000;
    /** 启动时预选节点（如 -Dzhisuan.preselect=dft），用于演示/验证详情面板。 */
    private static final String PRESELECT = System.getProperty("zhisuan.preselect", "");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // 保持默认外观
            }

            JFrame frame = new JFrame("智算铝宾 · N8N 式工作流视图 (M1 画布)");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1280, 720);
            frame.setMinimumSize(new Dimension(1100, 640));
            frame.setLocationRelativeTo(null);

            WorkflowGraph graph = WorkflowGraph.demoGraph();
            WorkflowCanvas canvas = new WorkflowCanvas(graph);
            NodeDetailPanel detail = new NodeDetailPanel();
            // 选中节点 → 展示静态信息 + 最近一次轮询到的真实能量/日志
            final Map<String, NodeRemoteState>[] latestStates = new Map[1];
            latestStates[0] = java.util.Collections.emptyMap();
            canvas.setSelectionListener(id -> {
                WorkflowNode n = id == null ? null : graph.getNode(id);
                detail.showNode(n, n == null ? null : latestStates[0].get(n.getId()));
            });

            MonitorPanel monitor = new MonitorPanel(canvas::repaint);
            monitor.bind(graph);

            JLabel zoomLabel = new JLabel("缩放 100%");
            zoomLabel.setForeground(Theme.TEXT_SECONDARY);
            zoomLabel.setFont(Theme.uiFont(11));
            canvas.setZoomListener(() -> zoomLabel.setText(
                    String.format("缩放 %.0f%%", canvas.getZoom() * 100)));

            JLabel statsLabel = new JLabel();
            statsLabel.setFont(Theme.uiFont(11));
            statsLabel.setForeground(Theme.TEXT_SECONDARY);

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
            toolbar.setBackground(Theme.PANEL);
            toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
            JLabel appTitle = new JLabel("智算铝宾 · 工作流");
            appTitle.setFont(Theme.uiFontBold(13));
            appTitle.setForeground(Theme.TEXT_PRIMARY);
            toolbar.add(appTitle);
            toolbar.add(button("适应视图", canvas::fitView));
            toolbar.add(button("放大", canvas::zoomIn));
            toolbar.add(button("缩小", canvas::zoomOut));
            toolbar.add(button("100%", canvas::resetZoom));
            toolbar.add(button("重置状态", () -> {
                monitor.resetDemo();
                canvas.setSelectedNode(null);
                canvas.fitView();
            }));

            // ---- 后端连接指示 + 轮询开关 ----
            JLabel backendLabel = new JLabel("● 正在连接 " + BACKEND_URL + "/api/workflow/status");
            backendLabel.setFont(Theme.uiFont(11));
            backendLabel.setForeground(Theme.ACCENT);
            final AtomicBoolean running = new AtomicBoolean(true);
            JButton pollToggle = button("暂停轮询", () -> {});
            pollToggle.addActionListener(e -> {
                boolean now = running.getAndSet(!running.get());
                pollToggle.setText(now ? "继续轮询" : "暂停轮询");
            });
            toolbar.add(pollToggle);
            toolbar.add(backendLabel);

            // ---- 底部监控抽屉（复刻 n8n 边栏）: MonitorPanel + 状态栏 ----
            JLabel infoLabel = new JLabel();
            infoLabel.setFont(Theme.uiFont(11));
            infoLabel.setForeground(Theme.TEXT_SECONDARY);

            JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 5));
            statusBar.setBackground(Theme.PANEL);
            statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
            statusBar.add(infoLabel);
            statusBar.add(zoomLabel);

            JPanel south = new JPanel(new BorderLayout());
            south.setBackground(Theme.PANEL);
            south.add(monitor, BorderLayout.NORTH);
            south.add(statusBar, BorderLayout.SOUTH);

            frame.setLayout(new BorderLayout());
            frame.add(toolbar, BorderLayout.NORTH);
            frame.add(canvas, BorderLayout.CENTER);
            frame.add(detail, BorderLayout.EAST);
            frame.add(south, BorderLayout.SOUTH);

            infoLabel.setText("节点 " + graph.getNodes().size() + " · 连线 " + graph.getEdges().size());
            Runnable refreshStats = () -> infoLabel.setText(
                    "节点 " + graph.getNodes().size() + " · 连线 " + graph.getEdges().size()
                            + " · 后端 " + (backendLabel.getForeground().equals(Theme.STATUS_COMPLETED)
                            ? "已连接" : "离线"));
            canvas.setGraphChangedListener(refreshStats);

            // ---- M2 状态轮询：后台线程拉取，EDT 着色 ----
            StatusClient client = new StatusClient(BACKEND_URL, 2000);
            AtomicBoolean inFlight = new AtomicBoolean(false);
            Timer timer = new Timer(POLL_MS, e -> {
                if (!running.get() || !inFlight.compareAndSet(false, true)) {
                    return; // 暂停轮询或上次请求未返回时跳过本拍
                }
                Thread worker = new Thread(() -> {
                    Map<String, NodeRemoteState> st = client.poll();
                    inFlight.set(false);
                    SwingUtilities.invokeLater(() -> {
                        boolean connected = !st.isEmpty();
                        latestStates[0] = connected ? st : latestStates[0];
                        backendLabel.setForeground(connected ? Theme.STATUS_COMPLETED : Theme.STATUS_FAILED);
                        backendLabel.setText((connected ? "● 已连接 " : "○ 离线 ")
                                + BACKEND_URL + "/api/workflow/status");
                        canvas.applyRemoteStates(st);
                        refreshStats.run();
                        // 首次连接成功后若指定了预选节点且尚未选中，自动选中以展示详情
                        if (PRESELECT != null && !PRESELECT.isEmpty()
                                && canvas.getSelectedNode() == null
                                && graph.getNode(PRESELECT) != null) {
                            canvas.setSelectedNode(PRESELECT);
                        }
                        // 选中节点保持详情实时
                        WorkflowNode sel = canvas.getSelectedNode();
                        if (sel != null) {
                            detail.showNode(sel, latestStates[0].get(sel.getId()));
                        }
                    });
                });
                worker.setDaemon(true);
                worker.start();
            });
            timer.setInitialDelay(300);
            timer.start();

            frame.setVisible(true);
        });
    }

    // ---- 顶部/底部控制按钮 ----

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setPreferredSize(BUTTON_SIZE);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setBackground(Theme.PANEL_RAISED);
        b.setForeground(Theme.TEXT_PRIMARY);
        b.setFont(Theme.uiFont(12));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        b.addActionListener(e -> action.run());
        return b;
    }
}
