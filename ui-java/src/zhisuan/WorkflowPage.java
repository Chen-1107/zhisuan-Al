package zhisuan;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.zhisuan.workflow.model.WorkflowGraph;
import com.zhisuan.workflow.model.WorkflowNode;
import com.zhisuan.workflow.ui.MonitorPanel;
import com.zhisuan.workflow.ui.NodeDetailPanel;
import com.zhisuan.workflow.ui.NodeRemoteState;
import com.zhisuan.workflow.ui.StatusClient;
import com.zhisuan.workflow.ui.Theme;
import com.zhisuan.workflow.ui.WorkflowCanvas;

/**
 * 工作流视图页：将 n8n 式画布（WorkflowCanvas）嵌入主程序，作为「工作流」导航页。
 * 数据源与主程序共用同一后端（API 通过了 agent_server.py 的 /api/workflow/* 接口），
 * 通过后台轮询在 EDT 上刷新节点状态。
 */
public class WorkflowPage extends Page {

    private static final int POLL_MS = 3000;

    private final String baseUrl;
    private final WorkflowCanvas canvas;
    private final NodeDetailPanel detail = new NodeDetailPanel();
    private final Map<String, NodeRemoteState>[] latestStates = new Map[1];
    private final JLabel connLabel = new JLabel();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private boolean connected = false;
    private Timer timer;
    private MonitorPanel monitor;
    private final JButton pillFit;
    private final JButton pillIn;
    private final JButton pillOut;

    @SuppressWarnings("unchecked")
    public WorkflowPage(ApiClient api) {
        super(api);
        this.baseUrl = api.getBase();
        this.latestStates[0] = java.util.Collections.emptyMap();

        WorkflowGraph graph = WorkflowGraph.demoGraph();
        this.canvas = new WorkflowCanvas(graph);
        this.pillFit = pill(Langs.t("workflow.fit"), () -> canvas.fitView());
        this.pillIn = pill(Langs.t("workflow.zoomIn"), () -> canvas.zoomIn());
        this.pillOut = pill(Langs.t("workflow.zoomOut"), () -> canvas.zoomOut());
        canvas.setSelectionListener(id -> {
            WorkflowNode n = id == null ? null : graph.getNode(id);
            detail.showNode(n, n == null ? null : latestStates[0].get(n.getId()));
        });

        this.monitor = new MonitorPanel(canvas::repaint, baseUrl);
        this.monitor.bind(graph);

        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        head.setOpaque(false);
        head.add(pillFit);
        head.add(pillIn);
        head.add(pillOut);
        connLabel.setFont(Theme.uiFont(11));
        connLabel.setForeground(Theme.STATUS_FAILED);
        connLabel.setText(Langs.t("workflow.offline", baseUrl + "/api/workflow/status"));
        head.add(connLabel);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(canvas, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(top, BorderLayout.CENTER);
        add(detail, BorderLayout.EAST);
        add(monitor, BorderLayout.SOUTH);

        Langs.addListener(this::applyLang);
        startPolling(baseUrl);
        canvas.fitView();
    }

    private void applyLang() {
        pillFit.setText(Langs.t("workflow.fit"));
        pillIn.setText(Langs.t("workflow.zoomIn"));
        pillOut.setText(Langs.t("workflow.zoomOut"));
        connLabel.setText(Langs.t(connected ? "workflow.online" : "workflow.offline",
                baseUrl + "/api/workflow/status"));
    }

    private JButton pill(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setPreferredSize(new Dimension(84, 26));
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

    private void startPolling(String backend) {
        StatusClient client = new StatusClient(backend, 2000);
        AtomicBoolean inFlight = new AtomicBoolean(false);
        running.set(true);
        timer = new Timer(POLL_MS, e -> {
            if (!running.get() || !inFlight.compareAndSet(false, true)) {
                return;
            }
            Thread worker = new Thread(() -> {
                Map<String, NodeRemoteState> st = client.poll();
                inFlight.set(false);
                SwingUtilities.invokeLater(() -> {
                    connected = !st.isEmpty();
                    connLabel.setForeground(connected ? Theme.STATUS_COMPLETED : Theme.STATUS_FAILED);
                    connLabel.setText(Langs.t(connected ? "workflow.online" : "workflow.offline",
                            backend + "/api/workflow/status"));
                    if (connected) {
                        latestStates[0] = st;
                    }
                    canvas.applyRemoteStates(st);
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
    }

    @Override
    public void onShow() {
        applyLang();
        SwingUtilities.invokeLater(canvas::fitView);
    }

    /** 停止后端状态轮询（演示/截图场景使用，避免远端状态覆盖演示推进）。 */
    public void stopPolling() {
        running.set(false);
        if (timer != null) {
            timer.stop();
        }
    }

    /** 返回底部监控抽屉（执行记录/日志/节点状态/任务进程）。 */
    public MonitorPanel monitor() {
        return monitor;
    }
}