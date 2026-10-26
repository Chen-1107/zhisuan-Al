package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 谱分析页：cclib 解析 Gaussian/ORCA 输出 → 分子轨道能级图 + 红外光谱 */
public class SpectrumPage extends Page {
    private final JTextField fileField = Ui.textField(40);
    private final JLabel lblFile = Ui.label("");
    private final JButton browseBtn = Ui.secondary("");
    private final JButton parseBtn = Ui.button("");
    private final JButton demoBtn = Ui.secondary("");
    private final JLabel sumScf = Ui.muted("");
    private final JLabel sumGap = Ui.muted("");
    private final JLabel sumImag = Ui.muted("");
    private final JLabel sumAtoms = Ui.muted("");

    private final MoPanel moPanel = new MoPanel();
    private final IrPanel irPanel = new IrPanel();
    private final Mol3DPanel mol3dPanel = new Mol3DPanel();
    private final JButton renderBtn = Ui.button("");
    private final JLabel renderStatus = Ui.muted("");
    private final JTextArea logArea = new JTextArea() {{
        setBackground(new Color(10, 17, 34));
        setForeground(Ui.MUTED);
        setCaretColor(Ui.ACCENT);
    }};
    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel topCard = Ui.card("");
    private final JPanel bottomCard = Ui.card("");
    private final JPanel card3d = Ui.card("");
    private final JLabel hint3d = Ui.label("<html>" + Langs.t("spec.title3dHint") + "</html>");
    private int lastAtoms;
    private Double lastScf;
    private Double lastGap;
    private int lastImag;

    SpectrumPage(ApiClient api) {
        super(api);
        setLayout(new BorderLayout(0, 10));
        Langs.addListener(this::applyLang);

        // 顶部：文件选择 + 汇总
        topCard.setLayout(new BorderLayout(8, 8));
        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        fileRow.setOpaque(false);
        fileRow.add(lblFile);
        fileField.setToolTipText(Langs.t("spec.fileHint"));
        fileRow.add(fileField);
        fileRow.add(browseBtn);
        fileRow.add(parseBtn);
        demoBtn.setMnemonic(java.awt.event.KeyEvent.VK_D);
        demoBtn.setToolTipText(Langs.t("spec.demoHint"));
        fileRow.add(demoBtn);
        topCard.add(fileRow, BorderLayout.NORTH);

        JPanel summary = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 4));
        summary.setOpaque(false);
        summary.add(sumAtoms);
        summary.add(sumScf);
        summary.add(sumGap);
        summary.add(sumImag);
        topCard.add(summary, BorderLayout.CENTER);
        add(topCard, BorderLayout.NORTH);

        // 中部：谱图
        tabs.setFont(Ui.FONT_BOLD);
        tabs.addTab(Langs.t("spec.tabMo"), moPanel);
        tabs.addTab(Langs.t("spec.tabIr"), irPanel);
        tabs.addTab(Langs.t("spec.tab3d"), build3dPanel());
        add(tabs, BorderLayout.CENTER);

        // 底部：解析日志
        bottomCard.setPreferredSize(new Dimension(0, 120));
        bottomCard.setLayout(new BorderLayout());
        logArea.setEditable(false);
        logArea.setFont(Ui.MONO);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(logArea);
        sp.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bottomCard.add(sp, BorderLayout.CENTER);
        add(bottomCard, BorderLayout.SOUTH);

        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle(Langs.t("spec.chooseTitle"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                fileField.setText(fc.getSelectedFile().getAbsolutePath());
        });
        parseBtn.addActionListener(e -> parseFile());
        demoBtn.addActionListener(e -> loadDemo());
        renderBtn.addActionListener(e -> render3d());
    }

    private void applyLang() {
        Ui.setCardText(topCard, Langs.t("spec.title"));
        Ui.setCardText(bottomCard, Langs.t("spec.info"));
        lblFile.setText(Langs.t("spec.lblFile"));
        browseBtn.setText(Langs.t("spec.browse"));
        parseBtn.setText(Langs.t("spec.parse"));
        demoBtn.setText(Langs.t("spec.demo"));
        demoBtn.setToolTipText(Langs.t("spec.demoHint"));
        fileField.setToolTipText(Langs.t("spec.fileHint"));
        renderBtn.setText(Langs.t("spec.render"));
        if (renderStatus.getText().isEmpty()) renderStatus.setText(Langs.t("spec.renderStatus"));
        tabs.setTitleAt(0, Langs.t("spec.tabMo"));
        tabs.setTitleAt(1, Langs.t("spec.tabIr"));
        tabs.setTitleAt(2, Langs.t("spec.tab3d"));
        Ui.setCardText(card3d, Langs.t("spec.title3d"));
        hint3d.setText("<html>" + Langs.t("spec.title3dHint") + "</html>");
        setSummary(lastAtoms, lastScf, lastGap, lastImag);
        logAreaInit();
        moPanel.repaint();
        irPanel.repaint();
        mol3dPanel.repaint();
    }

    private void logAreaInit() {
        if (logArea.getText() == null || logArea.getText().isEmpty()) logArea.setText(Langs.t("spec.logInit"));
    }

    /** 3D 结构 / FMO 标签页：说明 + 渲染按钮 + 内嵌球棍预览 */
    private JPanel build3dPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(Ui.BG);

        JPanel top = Ui.card(Langs.t("spec.title3d"));
        top.setLayout(new BorderLayout(8, 8));
        hint3d.setOpaque(false);
        top.add(hint3d, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        btnRow.setOpaque(false);
        btnRow.add(renderBtn);
        renderStatus.setOpaque(false);
        btnRow.add(renderStatus);
        top.add(btnRow, BorderLayout.SOUTH);
        p.add(top, BorderLayout.NORTH);
        p.add(mol3dPanel, BorderLayout.CENTER);
        return p;
    }

    /** 调用服务层渲染计算结果（生成 XYZ + FMO/密度 cube 并启动 molview.exe） */
    private void render3d() {
        final String path = fileField.getText().trim();
        if (path.isEmpty()) {
            renderStatus.setText(Langs.t("spec.renderSelect"));
            return;
        }
        renderStatus.setText(Langs.t("spec.renderWorking"));
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.post("/api/visualize_result", Map.of("file", path));
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> r = Json.obj(get());
                    int natom = Json.Int(r.get("natom"));
                    List<Object> cubes = Json.arr(r.get("cubes"));
                    String viewer = Json.str(r.get("viewer"));
                    renderStatus.setForeground(Ui.OK);
                    renderStatus.setText(Langs.t("spec.renderDone",
                            "molview".equals(viewer) ? Langs.t("spec.viewer3d") : Langs.t("spec.viewerOther"),
                            natom, cubes.size()));
                    logArea.append("\n· 3D 渲染已启动: " + Json.str(r.get("xyz_path")));
                    if (cubes.isEmpty())
                        logArea.append("\n· 未发现等值面 cube（需结果同目录存在同名 .fchk 且安装 Multiwfn）");
                } catch (Exception e) {
                    renderStatus.setForeground(Ui.BAD);
                    renderStatus.setText(Langs.t("spec.renderFail", e.getMessage()));
                }
            }
        }.execute();
    }

    private void setSummary(int atoms, Double scf, Double gap, int imag) {
        lastAtoms = atoms; lastScf = scf; lastGap = gap; lastImag = imag;
        sumAtoms.setText(Langs.t("spec.moatoms", atoms > 0 ? String.valueOf(atoms) : "—"));
        sumScf.setText(Langs.t("spec.moscf", scf != null ? String.format("%.6f Ha", scf) : "—"));
        sumGap.setText(Langs.t("spec.mogap", gap != null ? String.format("%.3f eV", gap) : "—"));
        sumImag.setText(Langs.t("spec.moimag", imag));
        sumImag.setForeground(imag > 0 ? Ui.BAD : Ui.OK);
    }

    private void parseFile() {
        final String path = fileField.getText().trim();
        if (path.isEmpty()) {
            logArea.setText(Langs.t("spec.selectParse"));
            return;
        }
        logArea.setText(Langs.t("spec.parsing", path));
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.post("/api/parse_spectrum", Map.of("file", path));
            }

            @Override
            protected void done() {
                try {
                    apply(Json.obj(get()), path);
                } catch (Exception e) {
                    logArea.append("\n" + Langs.t("spec.parseFail", e.getMessage()));
                }
            }
        }.execute();
    }

    private void apply(Map<String, Object> r, String source) {
        int atoms = Json.Int(r.get("natom"));
        Double scf = r.containsKey("scfenergy") ? Json.num(r.get("scfenergy")) : null;
        List<Double> mo = new ArrayList<>();
        for (Object o : Json.arr(r.get("moenergies"))) mo.add(Json.num(o));
        Integer homo = r.get("homos") == null ? null : Json.Int(r.get("homos"));
        List<Double> freqs = new ArrayList<>();
        for (Object o : Json.arr(r.get("vibfreqs"))) freqs.add(Json.num(o));
        List<Double> irs = new ArrayList<>();
        for (Object o : Json.arr(r.get("vibirs"))) irs.add(Json.num(o));
        int imag = Json.Int(r.get("imaginary"));

        moPanel.setData(mo, homo);
        irPanel.setData(freqs, irs);
        mol3dPanel.setAtoms(Json.arr(r.get("atoms")));
        Double gap = null;
        if (mo.size() > 1 && homo != null && homo >= 0 && homo + 1 < mo.size())
            gap = mo.get(homo + 1) - mo.get(homo);
        setSummary(atoms, scf, gap, imag);

        StringBuilder sb = new StringBuilder("解析完成: " + source + "\n");
        sb.append("· 原子数 ").append(atoms);
        if (scf != null) sb.append(" · SCF 能量 ").append(String.format("%.6f Ha", scf));
        if (!mo.isEmpty()) sb.append("\n· 分子轨道 ").append(mo.size()).append(" 条");
        if (gap != null) sb.append("（HOMO-LUMO 间隙 ").append(String.format("%.3f eV", gap)).append("）");
        if (!freqs.isEmpty())
            sb.append("\n· 振动频率 ").append(freqs.size()).append(" 个").append(imag > 0 ? "，其中虚频 " + imag + " 个（结构非极小点！）" : "");
        else sb.append("\n· 无频率数据（需 freq 任务输出）");
        logArea.setText(sb.toString());
    }

    private void loadDemo() {
        // 模拟一个典型有机分子的 MO 能级与红外光谱（演示用）
        double[] levels = {-14.23, -13.87, -12.95, -11.42, -10.86, -10.12, -9.66, -8.71,
                -7.93, -7.02, -6.35, -5.48, -4.77, -3.92, -3.05, -1.88,
                0.42, 1.35, 2.61, 3.78, 4.95, 6.31, 7.82, 9.14};
        List<Double> mo = new ArrayList<>();
        for (double v : levels) mo.add(v);
        moPanel.setData(mo, 15);

        double[][] irData = {
                {3352, 34}, {3068, 22}, {2957, 38}, {2852, 15}, {1743, 88}, {1689, 12},
                {1604, 41}, {1487, 19}, {1365, 26}, {1247, 55}, {1182, 33}, {1088, 47},
                {1029, 24}, {918, 14}, {847, 31}, {756, 68}, {698, 39}, {602, 8}, {517, 11}, {428, 6}};
        List<Double> f = new ArrayList<>(), ir = new ArrayList<>();
        for (double[] d : irData) {
            f.add(d[0]);
            ir.add(d[1]);
        }
        irPanel.setData(f, ir);
        setSummary(13, -604.217384, 0.42 + 1.88, 0);
        logArea.setText(Langs.t("spec.demoTitle") + "\n"
                + "· 24 条分子轨道，HOMO = 第 16 条（-1.88 eV），LUMO = 第 17 条（+0.42 eV），间隙 2.30 eV\n"
                + "· 20 个振动频率，无虚频（结构为极小点）\n"
                + "· 谱峰：3352 cm⁻¹ (O-H), 2957/2852 cm⁻¹ (C-H), 1743 cm⁻¹ (C=O), 756 cm⁻¹ (芳环）");
    }

    // ---------------- 分子轨道能级图 ----------------
    static class MoPanel extends JPanel {
        private List<Double> energies = new ArrayList<>();
        private int homo = -1;

        MoPanel() {
            setBackground(Ui.PANEL);
            setPreferredSize(new Dimension(600, 400));
        }

        void setData(List<Double> energies, Integer homo) {
            this.energies = new ArrayList<>(energies);
            this.homo = homo == null ? -1 : homo;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(Ui.PANEL);
            g2.fillRect(0, 0, w, h);

            if (energies.isEmpty()) {
                g2.setColor(Ui.MUTED);
                g2.setFont(new Font("微软雅黑", Font.PLAIN, 14));
                String t = Langs.t("spec.noData");
                g2.drawString(t, w / 2 - g2.getFontMetrics().stringWidth(t) / 2, h / 2);
                g2.dispose();
                return;
            }

            // 显示窗口 HOMO-7 … LUMO+8
            int from = Math.max(0, homo - 7);
            int to = Math.min(energies.size() - 1, homo + 8);
            if (homo < 0) {
                from = 0;
                to = Math.min(energies.size() - 1, 15);
            }
            double eMin = Double.MAX_VALUE, eMax = -Double.MAX_VALUE;
            for (int i = from; i <= to; i++) {
                eMin = Math.min(eMin, energies.get(i));
                eMax = Math.max(eMax, energies.get(i));
            }
            double pad = Math.max(0.5, (eMax - eMin) * 0.08);
            eMin -= pad;
            eMax += pad;

            int top = 44, bottom = h - 40;
            double yScale = (bottom - top) / (eMax - eMin);
            int lineHalf = Math.min(130, w / 3);
            int cx = w / 2 - 30;

            // 坐标轴
            g2.setColor(Ui.MUTED);
            g2.setFont(Ui.FONT_SMALL);
            g2.setStroke(new BasicStroke(1f));
            int ticks = 6;
            for (int k = 0; k <= ticks; k++) {
                double e = eMin + (eMax - eMin) * k / ticks;
                int y = (int) (bottom - (e - eMin) * yScale);
                g2.setColor(new Color(44, 60, 96));
                g2.drawLine(60, y, w - 20, y);
                g2.setColor(Ui.MUTED);
                String lab = String.format("%.1f", e);
                g2.drawString(lab + " eV", 6, y + 4);
            }

            g2.setFont(new Font("微软雅黑", Font.BOLD, 13));
            for (int i = from; i <= to; i++) {
                double e = energies.get(i);
                int y = (int) (bottom - (e - eMin) * yScale);
                boolean occ = i <= homo;
                boolean isHomo = i == homo, isLumo = i == homo + 1;
                if (isHomo || isLumo) {
                    g2.setStroke(new BasicStroke(2.6f));
                    g2.setColor(isHomo ? new Color(56, 189, 248) : new Color(255, 92, 130));
                } else {
                    g2.setStroke(new BasicStroke(1.8f));
                    g2.setColor(occ ? new Color(150, 166, 190) : new Color(126, 146, 184));
                }
                g2.drawLine(cx - lineHalf, y, cx + lineHalf, y);
                if (!occ && !isLumo) { // 虚线表示空轨道
                    g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            0, new float[]{6, 5}, 0));
                    g2.setColor(new Color(148, 163, 184, 160));
                    g2.drawLine(cx - lineHalf, y, cx + lineHalf, y);
                }
                g2.setStroke(new BasicStroke(1.8f));
                // 编号与标注
                g2.setFont(Ui.FONT_SMALL);
                g2.setColor(Ui.MUTED);
                g2.drawString(String.valueOf(i + 1), cx + lineHalf + 10, y + 4);
                if (isHomo || isLumo) {
                    g2.setFont(new Font("微软雅黑", Font.BOLD, 13));
                    g2.setColor(isHomo ? new Color(56, 189, 248) : new Color(255, 92, 130));
                    g2.drawString(isHomo ? "HOMO" : "LUMO", cx - lineHalf - 62, y + 4);
                }
            }
            // 图例
            g2.setFont(Ui.FONT_SMALL);
            int lx = w - 200, ly = 24;
            g2.setColor(new Color(56, 189, 248));
            g2.setStroke(new BasicStroke(2.6f));
            g2.drawLine(lx, ly, lx + 26, ly);
            g2.drawString(Langs.t("spec.homo"), lx + 32, ly + 4);
            g2.setColor(new Color(255, 92, 130));
            g2.drawLine(lx, ly + 20, lx + 26, ly + 20);
            g2.drawString(Langs.t("spec.lumo"), lx + 32, ly + 24);
            g2.setColor(new Color(150, 166, 190));
            g2.drawLine(lx, ly + 40, lx + 26, ly + 40);
            g2.drawString(Langs.t("spec.legendOcc"), lx + 32, ly + 44);
            g2.dispose();
        }
    }

    // ---------------- 红外光谱 ----------------
    static class IrPanel extends JPanel {
        private List<Double> freqs = new ArrayList<>();
        private List<Double> ints = new ArrayList<>();
        private final List<double[]> curve = new ArrayList<>();

        IrPanel() {
            setBackground(Ui.PANEL);
            setPreferredSize(new Dimension(600, 400));
        }

        void setData(List<Double> freqs, List<Double> ints) {
            this.freqs = new ArrayList<>(freqs);
            this.ints = new ArrayList<>(ints);
            buildCurve();
            repaint();
        }

        /** 高斯展宽曲线（σ=12 cm⁻¹） */
        private void buildCurve() {
            curve.clear();
            if (freqs.isEmpty() || ints.isEmpty()) return;
            double xMin = Math.min(0, freqs.stream().min(Double::compare).orElse(0.0)) - 80;
            double xMax = Math.max(4000, freqs.stream().max(Double::compare).orElse(4000.0)) + 80;
            double sigma = 12.0, max = 0;
            for (int px = 0; px <= 1000; px++) {
                double x = xMin + (xMax - xMin) * px / 1000.0;
                double y = 0;
                for (int k = 0; k < Math.min(freqs.size(), ints.size()); k++) {
                    double d = x - freqs.get(k);
                    y += ints.get(k) * Math.exp(-d * d / (2 * sigma * sigma));
                }
                max = Math.max(max, y);
                curve.add(new double[]{x, y});
            }
            if (max > 0) for (double[] c : curve) c[1] /= max;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(Ui.PANEL);
            g2.fillRect(0, 0, w, h);

            if (freqs.isEmpty()) {
                g2.setColor(Ui.MUTED);
                g2.setFont(new Font("微软雅黑", Font.PLAIN, 14));
                String t = Langs.t("spec.noIr");
                g2.drawString(t, w / 2 - g2.getFontMetrics().stringWidth(t) / 2, h / 2);
                g2.dispose();
                return;
            }

            double xMin = curve.isEmpty() ? -80 : curve.get(0)[0];
            double xMax = curve.isEmpty() ? 4080 : curve.get(curve.size() - 1)[0];
            int left = 56, right = w - 20, top = 40, bottom = h - 46;
            double maxInt = 1;
            for (double v : ints) maxInt = Math.max(maxInt, v);

            // 坐标轴与网格
            g2.setFont(Ui.FONT_SMALL);
            g2.setStroke(new BasicStroke(1f));
            for (int fq : new int[]{0, 1000, 2000, 3000, 4000}) {
                if (fq < xMin || fq > xMax) continue;
                int x = (int) (left + (fq - xMin) / (xMax - xMin) * (right - left));
                g2.setColor(new Color(44, 60, 96));
                g2.drawLine(x, top, x, bottom);
                g2.setColor(Ui.MUTED);
                String lab = String.valueOf(fq);
                g2.drawString(lab, x - g2.getFontMetrics().stringWidth(lab) / 2, bottom + 18);
            }
            for (int k = 0; k <= 4; k++) {
                int y = bottom - (int) ((bottom - top) * k / 4.0);
                g2.setColor(new Color(44, 60, 96));
                g2.drawLine(left, y, right, y);
            }
            g2.setColor(Ui.MUTED);
            g2.drawString(Langs.t("spec.wavenum"), w / 2 - 40, bottom + 34);
            g2.drawString(Langs.t("spec.intensity"), 8, top - 12);

            // 展宽曲线
            if (!curve.isEmpty()) {
                Path2D path = new Path2D.Double();
                boolean first = true;
                for (double[] c : curve) {
                    int x = (int) (left + (c[0] - xMin) / (xMax - xMin) * (right - left));
                    int y = (int) (bottom - c[1] * (bottom - top) * 0.92);
                    if (first) {
                        path.moveTo(x, y);
                        first = false;
                    } else path.lineTo(x, y);
                }
                g2.setColor(new Color(60, 224, 255, 90));
                g2.setStroke(new BasicStroke(2f));
                g2.draw(path);
                // 填充
                Path2D fill = new Path2D.Double(path);
                fill.lineTo(right, bottom);
                fill.lineTo(left, bottom);
                fill.closePath();
                g2.setColor(new Color(60, 224, 255, 34));
                g2.fill(fill);
            }

            // 棒状谱
            for (int k = 0; k < Math.min(freqs.size(), ints.size()); k++) {
                double fq = freqs.get(k), it = ints.get(k);
                int x = (int) (left + (fq - xMin) / (xMax - xMin) * (right - left));
                int y = (int) (bottom - it / maxInt * (bottom - top) * 0.92);
                g2.setStroke(new BasicStroke(1.6f));
                if (fq < 0) {
                    g2.setColor(new Color(255, 92, 130));
                    g2.drawLine(x, bottom, x, y);
                    g2.setColor(new Color(255, 92, 130));
                    g2.drawString(Langs.t("spec.imagTag"), x - 12, y - 6);
                } else {
                    g2.setColor(new Color(150, 175, 220, 220));
                    g2.drawLine(x, bottom, x, y);
                }
            }
            g2.dispose();
        }
    }

    // ---------------- 3D 结构预览（内嵌 2D 球棍投影，支持拖动旋转） ----------------
    static class Mol3DPanel extends JPanel {
        private static final double[] COV = {
                // Z=1..30 + Se/Te/Ag/Au，单位 Å；未列出的用 0.77 默认
                0.31, 0.28, 1.28, 0.96, 0.84, 0.76, 0.71, 0.66, 0.57, 0.58,
                1.66, 1.41, 1.21, 1.11, 1.07, 1.05, 1.02, 1.06, 2.03, 1.76,
                1.70, 1.60, 1.44, 1.41, 1.32, 1.26, 1.24, 1.32, 1.22, 1.22,
        };

        private final List<String> sym = new ArrayList<>();
        private final List<double[]> xyz = new ArrayList<>();
        private final List<int[]> bonds = new ArrayList<>();
        private float rotX = 0.55f, rotY = -0.6f;
        private float zoom = 1f;
        private java.awt.Point dragFrom;

        Mol3DPanel() {
            setBackground(new Color(15, 23, 42)); // 深空底色，与科技感主题一致
            setPreferredSize(new Dimension(620, 330));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { dragFrom = e.getPoint(); }
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragFrom == null) return;
                    rotY += (e.getX() - dragFrom.x) * 0.008f;
                    rotX += (e.getY() - dragFrom.y) * 0.008f;
                    dragFrom = e.getPoint();
                    repaint();
                }
                @Override public void mouseWheelMoved(MouseWheelEvent e) {
                    zoom *= (e.getWheelRotation() > 0) ? 0.9f : 1.1f;
                    zoom = Math.max(0.3f, Math.min(4f, zoom));
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);
        }

        void setAtoms(List<Object> atoms) {
            sym.clear();
            xyz.clear();
            if (atoms != null) {
                for (Object o : atoms) {
                    Map<String, Object> a = Json.obj(o);
                    sym.add(Json.str(a.get("symbol")));
                    xyz.add(new double[]{
                            Json.num(a.get("x")), Json.num(a.get("y")), Json.num(a.get("z"))});
                }
            }
            rebuildBonds();
            repaint();
        }

        /** 键检测：距离 < 共价半径和 + 0.40 Å */
        private void rebuildBonds() {
            bonds.clear();
            for (int i = 0; i < xyz.size(); i++)
                for (int j = i + 1; j < xyz.size(); j++) {
                    double dx = xyz.get(i)[0] - xyz.get(j)[0];
                    double dy = xyz.get(i)[1] - xyz.get(j)[1];
                    double dz = xyz.get(i)[2] - xyz.get(j)[2];
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d < covRadius(sym.get(i)) + covRadius(sym.get(j)) + 0.40)
                        bonds.add(new int[]{i, j});
                }
        }

        private static double covRadius(String s) {
            int z = atomicNumber(s);
            if (z >= 1 && z <= 30) return COV[z - 1];
            switch (s) {
                case "Se": return 1.20;
                case "Br": return 1.20;
                case "Te": return 1.38;
                case "Ag": return 1.45;
                case "Au": return 1.36;
                default: return 0.77;
            }
        }

        private static int atomicNumber(String s) {
            switch (s) {
                case "H": return 1;  case "He": return 2; case "Li": return 3; case "Be": return 4;
                case "B": return 5;  case "C": return 6;  case "N": return 7;  case "O": return 8;
                case "F": return 9;  case "Ne": return 10; case "Na": return 11; case "Mg": return 12;
                case "Al": return 13; case "Si": return 14; case "P": return 15; case "S": return 16;
                case "Cl": return 17; case "Ar": return 18; case "K": return 19; case "Ca": return 20;
                case "Sc": return 21; case "Ti": return 22; case "V": return 23; case "Cr": return 24;
                case "Mn": return 25; case "Fe": return 26; case "Co": return 27; case "Ni": return 28;
                case "Cu": return 29; case "Zn": return 30; case "Se": return 34; case "Br": return 35;
                case "Te": return 52; case "Ag": return 47; case "Au": return 79;
                default: return 0;
            }
        }

        private static Color atomColor(String s) {
            switch (s) {
                case "H":  return new Color(235, 235, 235);
                case "C":  return new Color(90, 100, 110);
                case "N":  return new Color(80, 130, 255);
                case "O":  return new Color(255, 60, 60);
                case "F":  return new Color(120, 200, 80);
                case "P":  return new Color(255, 150, 40);
                case "S":  return new Color(230, 220, 60);
                case "Cl": return new Color(80, 220, 80);
                case "Se": return new Color(235, 170, 90);
                case "Te": return new Color(210, 150, 60);
                case "Al": return new Color(190, 200, 215);
                case "Cu": return new Color(220, 140, 70);
                case "Au": return new Color(255, 215, 60);
                default:  return new Color(200, 120, 200);
            }
        }

        /** 球棍绘制半径（像素） */
        private static int atomRadius(String s) {
            switch (s) {
                case "H":  return 4;
                case "C":  return 7;
                case "N":  return 7;
                case "O":  return 7;
                case "Al": return 8;
                case "Cu": return 8;
                case "Au": return 9;
                case "Se": return 9;
                case "Te": return 10;
                default:  return 6;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(getBackground());
            g2.fillRect(0, 0, w, h);

            if (xyz.isEmpty()) {
                g2.setColor(new Color(126, 146, 184));
                g2.setFont(Ui.FONT);
                String t = Langs.t("spec.no3d");
                g2.drawString(t, w / 2 - g2.getFontMetrics().stringWidth(t) / 2, h / 2);
                g2.dispose();
                return;
            }

            // 中心化 + 适配缩放
            double cx = 0, cy = 0, cz = 0;
            for (double[] a : xyz) { cx += a[0]; cy += a[1]; cz += a[2]; }
            cx /= xyz.size(); cy /= xyz.size(); cz /= xyz.size();
            double maxR = 1.0;
            for (double[] a : xyz)
                maxR = Math.max(maxR, Math.sqrt(
                        (a[0] - cx) * (a[0] - cx) + (a[1] - cy) * (a[1] - cy) + (a[2] - cz) * (a[2] - cz)));
            double scale = Math.min(w, h) * 0.32 / maxR * zoom;

            float sr = (float) Math.sin(rotX), cr = (float) Math.cos(rotX);
            float sy = (float) Math.sin(rotY), cyy = (float) Math.cos(rotY);
            int ox = w / 2, oy = h / 2;

            // 投影（绕 Y 再绕 X），按深度排序保证前后遮挡
            double[][] proj = new double[xyz.size()][3];
            Integer[] order = new Integer[xyz.size()];
            for (int i = 0; i < xyz.size(); i++) {
                double x = xyz.get(i)[0] - cx, y = xyz.get(i)[1] - cy, z = xyz.get(i)[2] - cz;
                double x1 = x * cyy - z * sy;
                double z1 = x * sy + z * cyy;
                double y1 = y * cr - z1 * sr;
                double z2 = y * sr + z1 * cr;
                proj[i][0] = ox + x1 * scale;
                proj[i][1] = oy - y1 * scale;
                proj[i][2] = z2;
                order[i] = i;
            }
            java.util.Arrays.sort(order, (a, b) -> Double.compare(proj[a][2], proj[b][2]));

            // 键（粗线，先画在原子下层）
            g2.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int[] b : bonds) {
                int i = b[0], j = b[1];
                g2.setColor(new Color(160, 170, 190, 220));
                g2.drawLine((int) proj[i][0], (int) proj[i][1], (int) proj[j][0], (int) proj[j][1]);
            }

            // 原子（画家算法）
            for (int i : order) {
                int r = atomRadius(sym.get(i));
                int px = (int) proj[i][0], py = (int) proj[i][1];
                Color c = atomColor(sym.get(i));
                g2.setColor(c);
                g2.fillOval(px - r, py - r, r * 2, r * 2);
                g2.setColor(new Color(255, 255, 255, 70));
                g2.drawOval(px - r, py - r, r * 2, r * 2);
            }

            // 图例
            g2.setFont(Ui.FONT_SMALL);
            g2.setColor(new Color(126, 146, 184));
            g2.drawString(Langs.t("spec.molHint", xyz.size(), bonds.size()), 10, h - 10);
            g2.dispose();
        }
    }
}
