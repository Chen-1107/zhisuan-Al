package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结果审查台（方案九）：
 * 任务完成 → 待审队列 → 选择任务 → 三级判定来源链（Laya → qwen3.5:2b → 规则）
 * + 证据链（reason / usable_score / needs_rerun / concern）+ 相似历史案例（RAG）
 * + 处置：采纳结果 / 修改参数重跑 / 转人工复核。
 * 数据接 /api/task/list 轮询 + /api/diagnose 直连分诊。
 */
public class AuditPage extends Page {
    private static final Pattern LOG_RE = Pattern.compile(
            "(?:[A-Za-z]:)?[\\\\/][^\\s\"'，。；）)]+\\.(?:log|out)(?:\\s|$|\\n|\\r)");

    private final DefaultListModel<Map<String, Object>> queueModel = new DefaultListModel<>();
    private final JList<Map<String, Object>> queue = new JList<>(queueModel);
    private final Set<String> adopted = new LinkedHashSet<>();
    private final Set<String> skipped = new LinkedHashSet<>();

    private final JLabel title = new JLabel();
    private final JLabel sub = new JLabel();
    private final JLabel verdictBadge = new JLabel();
    private final JLabel engineLabel = new JLabel();
    private final JLabel concernLabel = new JLabel();
    private final JLabel metricsLabel = new JLabel();
    private final JTextArea evidence = new JTextArea();
    private final DefaultListModel<String> simModel = new DefaultListModel<>();
    private final JList<String> simList = new JList<>(simModel);
    private final JTextArea infoArea = new JTextArea();
    private final JButton acceptBtn = Ui.button("");
    private final JButton rerunBtn = Ui.secondary("");
    private final JButton humanBtn = Ui.secondary("");
    private final JButton refreshBtn = Ui.secondary("");

    private Map<String, Object> current = null;
    private String currentVerdict = "";
    private String currentEngine = "";
    private volatile boolean diagnosing = false;

    public AuditPage(ApiClient api) {
        super(api);
        setLayout(new BorderLayout(0, 8));

        // ---- 头部 ----
        JPanel head = new JPanel(new BorderLayout(12, 0));
        head.setOpaque(false);
        JPanel tt = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tt.setOpaque(false);
        title.setFont(new Font("微软雅黑", Font.BOLD, 16));
        title.setForeground(Ui.ACCENT);
        tt.add(title);
        sub.setFont(Ui.FONT_SMALL);
        sub.setForeground(Ui.MUTED);
        tt.add(sub);
        head.add(tt, BorderLayout.WEST);
        refreshBtn.addActionListener(e -> refreshQueue());
        head.add(refreshBtn, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        // ---- 中部：左队列 + 右详情 ----
        JPanel queuePanel = new JPanel(new BorderLayout());
        queuePanel.setBackground(Ui.CARD);
        queuePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.LINE),
                new EmptyBorder(8, 8, 8, 8)));
        JLabel queueTitle = new JLabel();
        queueTitle.setFont(new Font("微软雅黑", Font.BOLD, 13));
        queueTitle.setForeground(Ui.TEXT);
        queuePanel.add(queueTitle, BorderLayout.NORTH);
        queue.setCellRenderer(new QueueRenderer());
        queue.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onSelect(queue.getSelectedValue());
        });
        JScrollPane qs = new JScrollPane(queue);
        qs.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        qs.getVerticalScrollBar().setUnitIncrement(20);
        queuePanel.add(qs, BorderLayout.CENTER);
        queuePanel.setPreferredSize(new Dimension(300, 0));

        JPanel detail = buildDetail();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, queuePanel, detail);
        split.setBorder(null);
        split.setDividerSize(6);
        split.setResizeWeight(0.18);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        // ---- 轮询 ----
        new Timer(10_000, e -> refreshQueue()).start();
        Langs.addListener(this::renderAll);
        refreshQueue();
    }

    private JPanel buildDetail() {
        JPanel d = new JPanel(new BorderLayout(0, 8));
        d.setBackground(Ui.CARD);
        d.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.LINE),
                new EmptyBorder(10, 12, 10, 12)));

        // 判定行
        JPanel verdictRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        verdictRow.setOpaque(false);
        verdictBadge.setFont(new Font("微软雅黑", Font.BOLD, 14));
        verdictBadge.setOpaque(true);
        verdictBadge.setBorder(BorderFactory.createEmptyBorder(3, 14, 3, 14));
        verdictRow.add(verdictBadge);
        engineLabel.setFont(Ui.FONT_SMALL);
        engineLabel.setForeground(Ui.MUTED);
        verdictRow.add(engineLabel);
        concernLabel.setFont(Ui.FONT_SMALL);
        concernLabel.setForeground(Ui.SOFT);
        verdictRow.add(concernLabel);
        metricsLabel.setFont(Ui.MONO);
        metricsLabel.setForeground(Ui.GOLD);
        verdictRow.add(metricsLabel);
        d.add(verdictRow, BorderLayout.NORTH);

        // 中部：证据链 + （相似案例 / 任务信息）
        evidence.setEditable(false);
        evidence.setLineWrap(true);
        evidence.setWrapStyleWord(true);
        evidence.setFont(new Font("Dialog", Font.PLAIN, 12));
        evidence.setForeground(Ui.SOFT);
        evidence.setBackground(Ui.PANEL);
        JScrollPane ev = new JScrollPane(evidence);
        ev.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Ui.LINE), ""));
        ev.getVerticalScrollBar().setUnitIncrement(18);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        simList.setFont(Ui.FONT_SMALL);
        simList.setForeground(Ui.SOFT);
        simList.setBackground(Ui.PANEL);
        simList.setBorder(new EmptyBorder(4, 6, 4, 6));
        JScrollPane sim = new JScrollPane(simList);
        sim.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Ui.LINE), ""));
        sim.getVerticalScrollBar().setUnitIncrement(16);
        infoArea.setEditable(false);
        infoArea.setFont(Ui.MONO);
        infoArea.setForeground(Ui.MUTED);
        infoArea.setBackground(Ui.PANEL);
        infoArea.setBorder(new EmptyBorder(4, 6, 4, 6));
        JScrollPane info = new JScrollPane(infoArea);
        info.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Ui.LINE), ""));
        bottom.add(sim, BorderLayout.CENTER);
        bottom.add(info, BorderLayout.EAST);
        info.setPreferredSize(new Dimension(330, 0));

        JSplitPane mid = new JSplitPane(JSplitPane.VERTICAL_SPLIT, ev, bottom);
        mid.setBorder(null);
        mid.setDividerSize(5);
        mid.setResizeWeight(0.62);
        mid.setContinuousLayout(true);
        d.add(mid, BorderLayout.CENTER);

        // 操作行
        JPanel ops = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        ops.setOpaque(false);
        acceptBtn.setBackground(Ui.EMERALD);
        acceptBtn.setOpaque(true);
        acceptBtn.setContentAreaFilled(true);
        acceptBtn.setForeground(Color.BLACK);
        acceptBtn.addActionListener(e -> doAccept());
        rerunBtn.addActionListener(e -> doRerun());
        humanBtn.addActionListener(e -> doHuman());
        ops.add(acceptBtn);
        ops.add(rerunBtn);
        ops.add(humanBtn);
        d.add(ops, BorderLayout.SOUTH);
        return d;
    }

    // ---------------- 语言刷新 ----------------
    private void renderAll() {
        title.setText(Langs.t("audit.title"));
        refreshBtn.setText(Langs.t("audit.refresh"));
        acceptBtn.setText(Langs.t("audit.accept"));
        rerunBtn.setText(Langs.t("audit.rerun"));
        humanBtn.setText(Langs.t("audit.human"));
        int pending = queueModel.size();
        sub.setText(String.format(Langs.t("audit.sub"), pending, adopted.size()));
        renderVerdict();
        renderDetails();
    }

    private void renderVerdict() {
        String v = currentVerdict;
        if (v == null || v.isEmpty()) {
            verdictBadge.setText("");
            verdictBadge.setBackground(Ui.CARD);
            engineLabel.setText("");
            concernLabel.setText("");
            metricsLabel.setText("");
            return;
        }
        Color c = "KEEP".equals(v) ? Ui.OK : "RERUN".equals(v) ? Ui.MAGENTA : Ui.WARN;
        verdictBadge.setText("  " + v + "  ");
        verdictBadge.setBackground(c);
        verdictBadge.setForeground(Color.BLACK);
        engineLabel.setText(currentEngine.isEmpty() ? ""
                : Langs.t("audit.engine") + ": " + Langs.engineName(currentEngine));
    }

    private void renderDetails() {
        if (current == null) {
            evidence.setText("");
            simModel.clear();
            infoArea.setText("");
            concernLabel.setText("");
            metricsLabel.setText("");
            return;
        }
        if (currentVerdict.isEmpty()) {
            concernLabel.setText(Langs.t("audit.noLog"));
        }
    }

    // ---------------- 数据 ------------
    private void refreshQueue() {
        new Thread(() -> {
            try {
                Object o = api.get("/api/task/list");
                List<Object> tasks = Json.arr(Json.obj(o).get("tasks"));
                List<Map<String, Object>> done = new ArrayList<>();
                for (Object t : tasks) {
                    Map<String, Object> m = Json.obj(t);
                    String st = Json.str(m.get("status"));
                    if ("done".equals(st) || "failed".equals(st)) done.add(m);
                }
                done.sort((a, b) -> Double.compare(Json.num(b.get("finished_at")), Json.num(a.get("finished_at"))));
                SwingUtilities.invokeLater(() -> applyQueue(done));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> sub.setText("⚠ " + e.getMessage()));
            }
        }, "audit-list").start();
    }

    private void applyQueue(List<Map<String, Object>> done) {
        String selectedId = current == null ? "" : Json.str(current.get("id"));
        queueModel.clear();
        for (Map<String, Object> t : done) queueModel.addElement(t);
        if (!selectedId.isEmpty()) {
            for (int i = 0; i < queueModel.size(); i++) {
                if (selectedId.equals(Json.str(queueModel.get(i).get("id")))) {
                    queue.setSelectedIndex(i);
                    break;
                }
            }
        }
        sub.setText(String.format(Langs.t("audit.sub"), queueModel.size(), adopted.size()));
    }

    private void onSelect(Map<String, Object> t) {
        if (t == null) return;
        current = t;
        String id = Json.str(t.get("id"));
        if (adopted.contains(id)) {
            currentVerdict = "KEEP";
            setActionButtons(false, false, true);
            evidence.setText(Langs.t("audit.acceptOk") + "\n\n" + rawResult(t));
        } else if (skipped.contains(id)) {
            currentVerdict = "";
            setActionButtons(true, true, true);
            evidence.setText(Langs.t("audit.skipOk"));
        } else {
            currentVerdict = "";
            setActionButtons(true, true, true);
            diagnose(t);
        }
        renderInfo(t);
        renderAll();
    }

    private void diagnose(Map<String, Object> t) {
        if (diagnosing) return;
        diagnosing = true;
        setActionButtons(false, false, false);
        evidence.setText(Langs.t("audit.loading"));
        new Thread(() -> {
            try {
                String result = rawResult(t);
                String logPath = findLogPath(result);
                Object body;
                if (logPath != null) {
                    body = Map.of("log", logPath, "target_spin", 1, "use", "auto");
                } else {
                    body = Map.of("log", "", "target_spin", 1, "use", "auto");
                }
                Object resp = api.post("/api/diagnose", body);
                Map<String, Object> d = Json.obj(resp);
                final String action = Json.str(d.get("action"), "UNKNOWN");
                final String reason = Json.str(d.get("reason"));
                final String engine = Json.str(d.get("engine"));
                final String concern = Json.str(d.get("concern"));
                final double usable = Json.num(d.get("usable_score"));
                final double rerun = Json.num(d.get("needs_rerun"));
                final List<Object> sims = Json.arr(d.get("similarities"));
                final String text = Json.str(d.get("text"));
                SwingUtilities.invokeLater(() -> {
                    currentVerdict = action;
                    currentEngine = engine;
                    concernLabel.setText(Langs.t("audit.reason") + ": " + reason);
                    metricsLabel.setText(String.format("usable=%.3f needs_rerun=%.3f  %s: %s",
                            usable, rerun, Langs.t("audit.metrics"), concern));
                    evidence.setText(logPath != null
                            ? "◆ " + logPath + "\n\n" + text
                            : Langs.t("audit.noLog") + "\n\n" + result);
                    simModel.clear();
                    for (Object s : sims) {
                        Map<String, Object> sm = Json.obj(s);
                        simModel.addElement(String.format("[%s] %s · " + Langs.t("audit.simScore") + "\n%s",
                                Json.str(sm.get("label")), Json.str(sm.get("key")),
                                Json.num(sm.get("score")), Json.str(sm.get("text"))));
                    }
                    if (simModel.isEmpty()) simModel.addElement(Langs.t("audit.none"));
                    renderVerdict();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    evidence.setText(String.format(Langs.t("audit.diagFail"), ex.getMessage())
                            + "\n\n" + rawResult(t));
                    simModel.clear();
                    simModel.addElement(Langs.t("audit.none"));
                });
            } finally {
                diagnosing = false;
                SwingUtilities.invokeLater(() -> setActionButtons(true, true, true));
            }
        }, "audit-diagnose").start();
    }

    private String findLogPath(String result) {
        if (result == null || result.isEmpty()) return null;
        Matcher m = LOG_RE.matcher(result);
        if (m.find()) {
            String p = m.group().trim();
            if (p.length() > 5 && (p.endsWith(".log") || p.endsWith(".out"))) return p;
        }
        return null;
    }

    private void renderInfo(Map<String, Object> t) {
        StringBuilder sb = new StringBuilder();
        sb.append("id      : ").append(Json.str(t.get("id"))).append('\n');
        sb.append("tool    : ").append(Json.str(t.get("tool"))).append('\n');
        sb.append("status  : ").append(Json.str(t.get("status"))).append('\n');
        sb.append("label   : ").append(Json.str(t.get("label"))).append('\n');
        sb.append("params  : ").append(Json.stringify(Json.obj(t.get("params")))).append('\n');
        sb.append("error   : ").append(Json.str(t.get("error"))).append('\n');
        infoArea.setText(sb.toString());
    }

    private String rawResult(Map<String, Object> t) {
        String r = Json.str(t.get("result"));
        if (r == null || r.isEmpty()) {
            String err = Json.str(t.get("error"));
            return err == null || err.isEmpty() ? "(no result)" : "error: " + err;
        }
        return r;
    }

    private void setActionButtons(boolean accept, boolean rerun, boolean human) {
        acceptBtn.setEnabled(accept);
        rerunBtn.setEnabled(rerun);
        humanBtn.setEnabled(human);
    }

    // ---------------- 处置 ----------------
    private void doAccept() {
        if (current == null) return;
        adopted.add(Json.str(current.get("id")));
        currentVerdict = "KEEP";
        evidence.setText(Langs.t("audit.acceptOk") + "\n\n" + rawResult(current));
        setActionButtons(false, false, true);
        renderVerdict();
        sub.setText(String.format(Langs.t("audit.sub"), queueModel.size(), adopted.size()));
    }

    private void doHuman() {
        if (current == null) return;
        skipped.add(Json.str(current.get("id")));
        currentVerdict = "";
        evidence.setText(Langs.t("audit.skipOk"));
        setActionButtons(true, true, true);
        renderVerdict();
    }

    private void doRerun() {
        if (current == null) return;
        String tool = Json.str(current.get("tool"));
        String paramsJson = Json.stringify(Json.obj(current.get("params")));
        JTextArea area = new JTextArea(paramsJson, 12, 46);
        area.setFont(new Font("Dialog", Font.PLAIN, 12));
        area.setBackground(Ui.PANEL);
        area.setForeground(Ui.TEXT);
        javax.swing.JScrollPane sp = new javax.swing.JScrollPane(area);
        Object[] msg = {
                String.format(Langs.t("audit.rerunMsg"), tool),
                sp,
        };
        int rc = javax.swing.JOptionPane.showConfirmDialog(this, msg,
                Langs.t("audit.rerunTitle"), javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (rc != javax.swing.JOptionPane.OK_OPTION) return;
        try {
            Map<String, Object> params = Json.obj(Json.parse(area.getText()));
            api.post("/api/task/submit", Map.of("tool", tool, "params", params,
                    "label", Json.str(current.get("label")) + " [rerun]"));
            currentVerdict = "";
            evidence.setText("↻ " + Langs.t("audit.rerunTitle"));
            renderVerdict();
            refreshQueue();
        } catch (Exception ex) {
            javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "ERR", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void onShow() {
        renderAll();
        refreshQueue();
    }

    // ---------------- 队列渲染 ----------------
    private static class QueueRenderer implements ListCellRenderer<Map<String, Object>> {
        @Override
        public Component getListCellRendererComponent(JList<? extends Map<String, Object>> list,
                                                      Map<String, Object> t, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setBorder(new EmptyBorder(5, 8, 5, 8));
            String status = Json.str(t.get("status"));
            Color statusColor = "done".equals(status) ? Ui.OK : Ui.BAD;
            row.setBackground(isSelected ? Ui.SIDEBAR_SEL : Ui.PANEL);

            JLabel statusDot = new JLabel("●");
            statusDot.setForeground(statusColor);
            statusDot.setFont(Ui.FONT_SMALL);
            row.add(statusDot, BorderLayout.WEST);

            JPanel mid = new JPanel(new GridBagLayout());
            mid.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.gridy = 0;
            gc.anchor = GridBagConstraints.WEST;
            JLabel name = new JLabel(Json.str(t.get("label"), "") + "  [" + Json.str(t.get("tool")) + "]");
            name.setFont(Ui.FONT_BOLD);
            name.setForeground(isSelected ? Ui.TEXT : Ui.SOFT);
            mid.add(name, gc);
            gc.gridy = 1;
            JLabel id = new JLabel(Json.str(t.get("id")) + "  ·  " + fmtTime(Json.num(t.get("finished_at"))));
            id.setFont(Ui.FONT_SMALL);
            id.setForeground(Ui.FAINT);
            mid.add(id, gc);
            row.add(mid, BorderLayout.CENTER);

            JLabel badge = new JLabel(Json.str(t.get("status")));
            badge.setFont(new Font("微软雅黑", Font.BOLD, 10));
            badge.setOpaque(true);
            badge.setBorder(new EmptyBorder(2, 6, 2, 6));
            badge.setBackground(statusColor);
            badge.setForeground(Color.BLACK);
            row.add(badge, BorderLayout.EAST);
            return row;
        }
    }

    private static String fmtTime(double sec) {
        if (sec <= 0) return "-";
        try {
            java.time.Instant inst = java.time.Instant.ofEpochSecond((long) sec);
            return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault()).format(inst);
        } catch (Exception e) {
            return "-";
        }
    }
}
