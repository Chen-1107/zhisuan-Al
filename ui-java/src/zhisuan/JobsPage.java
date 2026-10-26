package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

/** 计算任务：G16 / ORCA / SSH 集群作业队列 */
public class JobsPage extends Page {
    private DefaultTableModel model;
    private JLabel logArea;
    private final JLabel head = new JLabel();
    private final JButton bNew = Ui.secondary("");
    private final JButton bCluster = Ui.secondary("");
    private final JButton bBatch = Ui.secondary("");
    private final JButton bUpload = Ui.secondary("");
    private int seq = 100;
    private String stRunning, stQueued, stDone;

    public JobsPage(ApiClient api) {
        super(api);
        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(2, 2, 2, 2));

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);
        add(buildOutput(), BorderLayout.SOUTH);
        Langs.addListener(this::applyLang);

        startProgress();
    }

    private void applyLang() {
        bNew.setText(Langs.t("jobs.new"));
        bCluster.setText(Langs.t("jobs.cluster"));
        bBatch.setText(Langs.t("jobs.batch"));
        bUpload.setText(Langs.t("jobs.upload"));
        head.setText(Langs.t("jobs.header"));
        stRunning = Langs.t("jobs.stRunning");
        stQueued = Langs.t("jobs.stQueued");
        stDone = Langs.t("jobs.stDone");
        model.setColumnIdentifiers(new Object[]{
                Langs.t("jobs.colJob"), Langs.t("jobs.colName"), Langs.t("jobs.colBackend"),
                Langs.t("jobs.colQueue"), Langs.t("jobs.colStatus"), Langs.t("jobs.colProgress")});
        resetData();
    }

    private void resetData() {
        model.setRowCount(0);
        addJob("#ts19", "铝烯 · CO₂ TS 优化", "G16", "cpu.serial", stRunning, 76);
        addJob("#ts20", "铝烯 · TS 续算", "G16·SSH", "cpu.serial", stQueued, 6);
        addJob("#fmo11", "前线轨道分析", "ORCA", "local", stDone, 100);
        addJob("#ive9", "Multiwfn 波函数分析", "本地", "local", stDone, 100);
        addJob("#se12", "Se 取代 · 势垒对比", "G16", "cpu.serial", stQueued, 0);
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        bNew.setPreferredSize(new Dimension(124, 30)); bNew.addActionListener(e -> openNewJob());
        bCluster.setPreferredSize(new Dimension(124, 30)); bCluster.addActionListener(e -> openCluster());
        bBatch.setPreferredSize(new Dimension(124, 30)); bBatch.addActionListener(e -> batchSubmit());
        bUpload.setPreferredSize(new Dimension(124, 30)); bUpload.addActionListener(e -> uploadStructure());
        bar.add(bNew); bar.add(bCluster); bar.add(bBatch); bar.add(bUpload);
        return bar;
    }

    private JScrollPane buildTable() {
        Object[] header = {Langs.t("jobs.colJob"), Langs.t("jobs.colName"), Langs.t("jobs.colBackend"),
                Langs.t("jobs.colQueue"), Langs.t("jobs.colStatus"), Langs.t("jobs.colProgress")};
        model = new DefaultTableModel(header, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        stRunning = Langs.t("jobs.stRunning");
        stQueued = Langs.t("jobs.stQueued");
        stDone = Langs.t("jobs.stDone");
        addJob("#ts19", "铝烯 · CO₂ TS 优化", "G16", "cpu.serial", stRunning, 76);
        addJob("#ts20", "铝烯 · TS 续算", "G16·SSH", "cpu.serial", stQueued, 6);
        addJob("#fmo11", "前线轨道分析", "ORCA", "local", stDone, 100);
        addJob("#ive9", "Multiwfn 波函数分析", "本地", "local", stDone, 100);
        addJob("#se12", "Se 取代 · 势垒对比", "G16", "cpu.serial", stQueued, 0);

        JTable table = new JTable(model);
        table.setOpaque(true);
        table.setBackground(Ui.PANEL);
        table.setForeground(Ui.SOFT);
        table.setSelectionBackground(new Color(60, 224, 255, 40));
        table.setSelectionForeground(Ui.TEXT);
        table.setRowHeight(30);
        table.setFont(Ui.FONT_SMALL);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setGridColor(Ui.LINE);
        table.getTableHeader().setBackground(Ui.PANEL2);
        table.getTableHeader().setForeground(Ui.FAINT);
        table.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 11));
        table.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String s = value == null ? "" : value.toString();
                if (isSelected) {
                    setBackground(new Color(60, 224, 255, 46));
                    setForeground(Ui.TEXT);
                } else {
                    setBackground((row % 2 == 0) ? Ui.PANEL : new Color(15, 27, 52));
                    setForeground("-Int-".equals(s) ? Ui.ACCENT : Ui.SOFT);
                }
                setText(s);
                return this;
            }
        };
        r.setOpaque(true);
        r.setBorder(new EmptyBorder(0, 8, 0, 8));
        table.setDefaultRenderer(Object.class, r);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            if (row < 0) return;
            String id = (String) model.getValueAt(row, 0);
            String name = (String) model.getValueAt(row, 1);
            appendLog("[JOB] " + id + " · " + name + " · 查看日志");
        });

        JScrollPane sp = new JScrollPane(table);
        sp.setOpaque(false);
        sp.getViewport().setBackground(Ui.PANEL);
        sp.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        return sp;
    }

    private JPanel buildOutput() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Ui.PANEL2);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.LINE), new EmptyBorder(6, 8, 6, 8)));
        head.setFont(new Font("微软雅黑", Font.BOLD, 11));
        head.setForeground(Ui.FAINT);
        logArea = new JLabel("<html><div style='color:#8497bd'>$ qsub g16.pbs -N ts19 -v input_file=tsres19_resume.com<br>"
                + "[07:00] 提交成功 · job=762913 · EST 00:14:00</div></html>");
        logArea.setFont(new Font("Dialog", Font.PLAIN, 11));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(head, BorderLayout.WEST);
        JScrollPane sc = new JScrollPane(logArea);
        sc.setPreferredSize(new Dimension(0, 74));
        sc.setOpaque(false);
        sc.getViewport().setOpaque(false);
        sc.setBorder(null);
        p.add(top, BorderLayout.NORTH);
        p.add(sc, BorderLayout.CENTER);
        p.setPreferredSize(new Dimension(0, 104));
        return p;
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            String cur = logArea.getText();
            String span = "<div style='color:#8497bd'>" + escape(line) + "</div>";
            String body = cur.replaceAll("(?s).*<body>", "");
            logArea.setText("<html><body style='font-family:Dialog;font-size:11px'>"
                    + span + body + "</body></html>");
            logArea.revalidate();
        });
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void startProgress() {
        new Timer(1600, e -> {
            int row = 0;
            if (row >= model.getRowCount()) return;
            Object st = model.getValueAt(row, 4);
            if (!stRunning.equals(st)) return;
            int w = (Integer) model.getValueAt(row, 5);
            if (w > 94) { model.setValueAt(stDone, row, 4); model.setValueAt(100, row, 5); return; }
            model.setValueAt(Math.min(98, w + 3), row, 5);
        }).start();
    }

    private void addJob(String id, String name, String be, String qu, String st, int pg) {
        model.addRow(new Object[]{id, name, be, qu, st, pg});
    }

    /** 将"排队中"的作业批量置为运行态并写日志 */
    private void batchSubmit() {
        boolean any = false;
        for (int i = 0; i < model.getRowCount(); i++) {
            if (stQueued.equals(model.getValueAt(i, 4))) {
                model.setValueAt(stRunning, i, 4);
                model.setValueAt(5, i, 5);
                any = true;
            }
        }
        appendLog("[CLUSTER] 批量提交 → " + (any ? "作业已转入运行" : "当前无排队作业"));
    }

    private void openNewJob() {
        appendLog("[JOB] 新建 DFT 任务 · ωB97X-D / def2-TZVP · 队列 cpu.serial");
    }

    private void openCluster() {
        appendLog("[CLUSTER] login01 · PBS cpu 队列 · 空闲 112/128 · 墙时 72:00:00");
    }

    private void uploadStructure() {
        appendLog("[STRUCT] 结构文件已读取 · 解析为坐标");
    }

    @Override public void onShow() { applyLang(); }
}