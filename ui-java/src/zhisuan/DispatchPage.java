package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 计算调度中心（方案七）：
 * 三后端状态卡片（本地 G16 / 本地 ORCA / SSH 集群 G16）
 * + 四列看板（排队 / 计算中 / 回传 / 待解读）
 * + 完成通知条（任务完成 → 提示 Agent 已恢复会话 → 一键回到对话）。
 * 数据接 /api/health + /api/dft_config + /api/task/list 轮询。
 */
public class DispatchPage extends Page {
    /** 由 MainFrame 注入：完成通知条「回到对话」按钮的跳转 */
    public Runnable onBackToChat = null;

    private final JLabel title = new JLabel();
    private final JLabel syncLabel = new JLabel();
    private final JButton newJobBtn = Ui.secondary("");
    private final JButton refreshBtn = Ui.secondary("");

    private final BackendCard[] cards = {
            new BackendCard("local_g16"),
            new BackendCard("local_orca"),
            new BackendCard("cluster_g16"),
    };

    private final JPanel[] columns = new JPanel[4];
    private final JLabel[] colTitles = new JLabel[4];
    private final DefaultListModel<Map<String, Object>>[] colModels = new DefaultListModel[4];

    private final JPanel banner = new JPanel(new BorderLayout(10, 0));
    private final JLabel bannerText = new JLabel();
    private final JButton backBtn = Ui.button("");
    private final Set<String> seenDone = new LinkedHashSet<>();

    private long lastSync = System.currentTimeMillis();

    public DispatchPage(ApiClient api) {
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
        syncLabel.setFont(Ui.FONT_SMALL);
        syncLabel.setForeground(Ui.MUTED);
        tt.add(syncLabel);
        head.add(tt, BorderLayout.WEST);
        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        r.setOpaque(false);
        newJobBtn.addActionListener(e -> newJobDialog());
        r.add(newJobBtn);
        refreshBtn.addActionListener(e -> poll());
        r.add(refreshBtn);
        head.add(r, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        // ---- 中部 ----
        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setOpaque(false);

        // 后端状态卡片行
        JPanel cardsRow = new JPanel(new GridLayout(1, 3, 10, 0));
        cardsRow.setOpaque(false);
        for (BackendCard c : cards) cardsRow.add(c.build());
        center.add(cardsRow, BorderLayout.NORTH);

        // 四列看板
        JPanel board = new JPanel(new GridLayout(1, 4, 10, 0));
        board.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            colModels[i] = new DefaultListModel<>();
            JList<Map<String, Object>> list = new JList<>(colModels[i]);
            list.setCellRenderer(new TaskCell(i));
            JScrollPane sp = new JScrollPane(list);
            sp.setBorder(BorderFactory.createLineBorder(Ui.LINE));
            sp.getVerticalScrollBar().setUnitIncrement(18);
            columns[i] = new JPanel(new BorderLayout(0, 6));
            columns[i].setOpaque(false);
            colTitles[i] = new JLabel();
            colTitles[i].setFont(new Font("微软雅黑", Font.BOLD, 13));
            columns[i].add(colTitles[i], BorderLayout.NORTH);
            columns[i].add(sp, BorderLayout.CENTER);
            board.add(columns[i]);
        }
        center.add(board, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        // ---- 完成通知条 ----
        banner.setBackground(new Color(13, 40, 36));
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(47, 230, 161, 90)),
                new EmptyBorder(8, 12, 8, 12)));
        bannerText.setFont(Ui.FONT_SMALL);
        bannerText.setForeground(Ui.EMERALD);
        banner.add(bannerText, BorderLayout.CENTER);
        backBtn.addActionListener(e -> {
            if (onBackToChat != null) onBackToChat.run();
        });
        banner.add(backBtn, BorderLayout.EAST);
        banner.setVisible(false);
        add(banner, BorderLayout.SOUTH);

        new Timer(5_000, e -> poll()).start();
        Langs.addListener(this::renderAll);
        poll();
    }

    // ---------------- 语言刷新 ----------------
    private void renderAll() {
        title.setText(Langs.t("disp.title"));
        newJobBtn.setText(Langs.t("disp.newJob"));
        refreshBtn.setText(Langs.t("audit.refresh"));
        backBtn.setText(Langs.t("disp.backToChat"));
        String[] cols = {Langs.t("disp.queued"), Langs.t("disp.calc"),
                Langs.t("disp.transfer"), Langs.t("disp.review")};
        for (int i = 0; i < 4; i++) {
            colTitles[i].setText(cols[i] + "  (" + colModels[i].size() + ")");
            Color c = i == 0 ? Ui.WARN : i == 1 ? Ui.ACCENT : i == 2 ? Ui.MAGENTA : Ui.EMERALD;
            colTitles[i].setForeground(c);
        }
        long sec = (System.currentTimeMillis() - lastSync) / 1000;
        syncLabel.setText(String.format(Langs.t("disp.sync"), sec, 3));
        for (BackendCard c : cards) c.render();
    }

    private static String clusterInfo(Map<String, Object> cfg) {
        String host = Json.str(cfg.get("ssh_host"), "");
        String user = Json.str(cfg.get("ssh_username"), "");
        String queue = Json.str(cfg.get("ssh_queue_name"), "-");
        if (host.isEmpty() || user.isEmpty()) return Langs.t("disp.unconfigured");
        return (host + "@" + user + " · " + queue).trim();
    }

    // ---------------- 轮询 ----------------
    private void poll() {
        lastSync = System.currentTimeMillis();
        new Thread(() -> {
            try {
                Map<String, Object> health = Json.obj(api.get("/api/health"));
                List<Object> backends = Json.arr(health.get("dft_backends"));
                boolean dftOk = Json.bool(health.get("dft"));
                Map<String, Object> cfg = Json.obj(Json.obj(api.get("/api/dft_config")).get("config"));
                Object listResp = api.get("/api/task/list");
                List<Object> tasks = Json.arr(Json.obj(listResp).get("tasks"));
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Object t : tasks) rows.add(Json.obj(t));
                rows.sort((a, b) -> Double.compare(Json.num(b.get("submitted_at")), Json.num(a.get("submitted_at"))));
                SwingUtilities.invokeLater(() -> apply(backends, dftOk, cfg, rows));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> syncLabel.setText("⚠ " + e.getMessage()));
            }
        }, "disp-poll").start();
    }

    private void apply(List<Object> backends, boolean dftOk, Map<String, Object> cfg, List<Map<String, Object>> rows) {
        Set<String> bk = new LinkedHashSet<>();
        for (Object b : backends) bk.add(Json.str(b));
        cards[0].update(bk.contains("local_g16"), Json.str(cfg.get("g16_command")));
        cards[1].update(bk.contains("local_orca"), Json.str(cfg.get("orca_command")));
        cards[2].update(bk.contains("cluster_g16"),
                clusterInfo(cfg));
        cards[0].dftOk = dftOk;

        for (int i = 0; i < 4; i++) colModels[i].clear();
        List<Map<String, Object>> freshDone = new ArrayList<>();
        for (Map<String, Object> t : rows) {
            String st = Json.str(t.get("status"));
            int col;
            if ("running".equals(st)) col = 1;
            else if ("done".equals(st)) { col = 3; if (!seenDone.contains(Json.str(t.get("id")))) freshDone.add(t); }
            else if ("failed".equals(st) || "interrupted".equals(st)) col = 3;
            else col = 0; // queued / 其他
            colModels[col].addElement(t);
        }
        for (Map<String, Object> t : freshDone) {
            seenDone.add(Json.str(t.get("id")));
            showBanner(t);
        }
        renderAll();
    }

    private void showBanner(Map<String, Object> t) {
        String label = Json.str(t.get("label"), "");
        if (label.isEmpty()) label = Json.str(t.get("tool"));
        bannerText.setText(String.format(Langs.t("disp.doneBanner"), label, 0));
        banner.setVisible(true);
        new Timer(12_000, e -> { banner.setVisible(false); ((Timer) e.getSource()).stop(); }).start();
    }

    // ---------------- 新建 DFT 任务 ----------------
    private void newJobDialog() {
        JTextArea area = new JTextArea("{\n  \"smiles\": \"\",\n  \"job_type\": \"sp\",\n  \"method\": \"B3LYP\",\n  \"basis\": \"def2SVP\",\n  \"backend\": \"cluster_g16\"\n}", 12, 46);
        area.setFont(new Font("Dialog", Font.PLAIN, 12));
        area.setBackground(Ui.PANEL);
        area.setForeground(Ui.TEXT);
        JScrollPane sp = new JScrollPane(area);
        Object[] msg = {Langs.t("disp.newJob") + " — submit_dft_job", sp};
        int rc = javax.swing.JOptionPane.showConfirmDialog(this, msg, Langs.t("disp.newJob"),
                javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (rc != javax.swing.JOptionPane.OK_OPTION) return;
        try {
            Map<String, Object> params = Json.obj(Json.parse(area.getText()));
            api.post("/api/task/submit", Map.of("tool", "submit_dft_job", "params", params,
                    "label", params.get("smiles")));
            poll();
        } catch (Exception ex) {
            javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "ERR", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void onShow() {
        renderAll();
        poll();
    }

    // ---------------- 后端状态卡片 ----------------
    private static class BackendCard {
        final String id;
        final JLabel name = new JLabel();
        final JLabel status = new JLabel();
        final JLabel detail = new JLabel();
        boolean online;
        boolean dftOk;
        String info = "";

        BackendCard(String id) { this.id = id; }

        JPanel build() {
            JPanel p = new JPanel(new BorderLayout(0, 6));
            p.setBackground(Ui.CARD);
            p.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Ui.LINE),
                    new EmptyBorder(10, 12, 10, 12)));
            name.setFont(new Font("微软雅黑", Font.BOLD, 13));
            name.setForeground(Ui.TEXT);
            p.add(name, BorderLayout.NORTH);
            JPanel mid = new JPanel(new GridBagLayout());
            mid.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.gridy = 0;
            gc.anchor = GridBagConstraints.WEST;
            status.setFont(Ui.FONT_BOLD);
            mid.add(status, gc);
            gc.gridy = 1;
            detail.setFont(Ui.FONT_SMALL);
            detail.setForeground(Ui.MUTED);
            mid.add(detail, gc);
            p.add(mid, BorderLayout.CENTER);
            return p;
        }

        void update(boolean online, String info) {
            this.online = online;
            this.info = info == null ? "" : info;
        }

        void render() {
            String n;
            switch (id) {
                case "local_g16": n = Langs.t("disp.g16"); break;
                case "local_orca": n = Langs.t("disp.orca"); break;
                default: n = Langs.t("disp.cluster");
            }
            name.setText(n);
            if (online) {
                status.setText("● " + Langs.t("disp.online", 0));
                status.setForeground(Ui.OK);
                detail.setText(info.isEmpty() ? Langs.t("disp.idle", 0) : info);
            } else {
                status.setText("○ " + Langs.t("disp.down"));
                status.setForeground(Ui.BAD);
                detail.setText(info.isEmpty() ? "-" : info);
            }
        }
    }

    // ---------------- 看板单元格 ----------------
    private static class TaskCell implements ListCellRenderer<Map<String, Object>> {
        private final int col;

        TaskCell(int col) { this.col = col; }

        @Override
        public Component getListCellRendererComponent(JList<? extends Map<String, Object>> list,
                                                      Map<String, Object> t, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setBorder(new EmptyBorder(5, 8, 5, 8));
            row.setBackground(isSelected ? Ui.SIDEBAR_SEL : Ui.PANEL);
            String st = Json.str(t.get("status"));
            Color c = "done".equals(st) ? Ui.OK
                    : ("failed".equals(st) || "interrupted".equals(st)) ? Ui.BAD
                    : "running".equals(st) ? Ui.ACCENT : Ui.WARN;
            JLabel dot = new JLabel("●");
            dot.setForeground(c);
            row.add(dot, BorderLayout.WEST);
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
            JLabel id = new JLabel(Json.str(t.get("id")) + "  ·  " + fmtTime(Json.num(t.get("submitted_at"))));
            id.setFont(Ui.FONT_SMALL);
            id.setForeground(Ui.FAINT);
            mid.add(id, gc);
            row.add(mid, BorderLayout.CENTER);
            if ("failed".equals(st) || "interrupted".equals(st)) {
                JLabel badge = new JLabel(st);
                badge.setFont(new Font("微软雅黑", Font.BOLD, 10));
                badge.setOpaque(true);
                badge.setBorder(new EmptyBorder(2, 6, 2, 6));
                badge.setBackground(Ui.BAD);
                badge.setForeground(Color.BLACK);
                row.add(badge, BorderLayout.EAST);
            }
            return row;
        }
    }

    private static String fmtTime(double sec) {
        if (sec <= 0) return "-";
        long t = (long) sec;
        long h = t / 3600, m = (t % 3600) / 60, s = t % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}
