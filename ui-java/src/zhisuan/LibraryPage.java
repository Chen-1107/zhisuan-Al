package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 化合物库：A1–A4 铝宾家族（本地精选）＋ Python 服务层动态拉取的完整后端库，检索 / 家族筛选 / 详情 */
public class LibraryPage extends Page {
    // 精选库（列：名称 | 家族 | 展示化学式 | 描述 | 真实SMILES）。家族键：A1/A2/A3/A4/P/L。
    private static final String[][] POOL = {
            {"铝烯 A1 阴离子", "A1", "[Al]-Cl·NON", "Al–C 键长 1.96Å · 阴离子铝宾", "C[Al-](Cl)N(C)C"},
            {"Al 碱稳定烯", "A2", "NHBO 配体", "碱稳定中性铝烯 · 强配体", "[Al](C)(N(C)C)C"},
            {"Cu–Al 键合物", "A3", "Cu–Al", "金属–铝 键合 A3", "[Al](CC)(CC)[Cu]"},
            {"Dialumene", "A4", "Al=Al", "铝双键二铝烯", "Cc1ccc(-[Al]=[Al]-c2ccc(C)cc2)cc1"},
            {"自由铝烯", "A2", ":Al–R", "低配位自由基", "[Al]C"},
            {"铝烯氧", "A2", "[Al]=O", "端氧铝双键", "[Al]=O"},
            {"铝烯亚胺", "A2", "Al=N", "N–Al 亚胺", "[Al]=N"},
            {"Se 取代铝烯", "A2", "Al–Se", "Se 替代 · 势垒 65.4", "[Al](C)(C)[Se]C"},
            {"碳酸铝产物", "P", "O=Al(O)·CO₂", "CO₂ 活化产物", "O=[Al]OC(=O)[O-]"},
            {"端式 CO₂ 加合物", "P", "CO₂·Al", "Al 亲氧端", "O=C=O.[AlH3]"},
            {"咔唑基铝宾 (1)", "A2", "N–Al", "经典咔唑铝宾", "[N][Al]"},
            {"Se-铝宾 (19)", "A2", "Al–Se·杂环", "硒羟基铝宾 · 经实测结构", "CC(CC)c1cc2c(n([Al+]([Se-])C)c3c2cc(cc3c2ccccc2)C(C)CC)c(c1)c1ccccc1"},
            {"Te-铝宾 (20)", "A2", "Al–Te·杂环", "碲羟基铝宾 · 经实测结构", "CC(CC)c1cc2c(n([Al+]([Te-])C)c3c2cc(cc3c2ccccc2)C(C)CC)c(c1)c1ccccc1"},
            {"THF-钨铝宾 (11)", "A3", "W–Al·THF", "钨铝宾 · CO₂ 活化活性", "[W+3][Al](O1CCCC1)n1c2c(cc(cc2c2ccccc2)C(C)CC)c2cc(cc(c12)c1ccccc1)C(C)CC.CC(CC)c1cc2c(n([Al]O3CCCC3)c3c2cc(cc3c2ccccc2)C(C)CC)c(c1)c1ccccc1"},
            {"硼烷基铝宾 (15)", "A2", "Al–B", "C₆F₅ 硼烷基铝宾", "Fc1c(F)c(F)c(B([Al][n@]2c3c(cc(cc3c3ccccc3)C(C)CC)c3cc(cc(c23)c2ccccc2)C(C)CC)c2c(F)c(F)c(F)c(F)c2F)c(F)c1F"},
            {"NON-铝宾阴离子 (37)", "A1", "[Al]⁻·NON", "阴离子铝宾 NON 骨架", "CC(C)(C)c1cc2c(n(c3c2cc(cc3c2ccccc2)C(C)(C)C)[Al-]2(C)C(=C2[Si](C)(C)C)[Si](C)(C)C)c(c1)c1ccccc1"},
            {"NHC-硅基铝宾 (38)", "A2", "Al–NHC", "NHC 稳定硅基铝宾", "C/C(C)=N1/c2ccccc2N(C(C)C)[Al]1(N)C1=C(C1[Si](C)(C)C)[Si](C)(C)C"},
            {"硒氮氧杂环铝宾 (32)", "A2", "Se–Se·N–O", "含硒氧杂环", "C[Al]1([Se][Se]N(O1)c1ccccc1)n1c2c(cc(cc2c2ccccc2)C(C)CC)c2cc(cc(c12)c1ccccc1)C(C)CC"},
    };

    // 家族筛选片：{键, 显示key}；键用于匹配 family，"BACKEND" 键映射后端动态库
    private static final String[][] FILTERS = {{"ALL", "A1"}, {"A1", "A1"}, {"A2", "A2"},
            {"A3", "A3"}, {"A4", "A4"}, {"P", "P"}, {"BACKEND", "library.backend"}};

    private final JPanel grid = new JPanel(new GridLayout(0, 4, 10, 10));
    private final JLabel count = Ui.muted(Langs.t("library.cached", 67));
    private final List<JButton> chips = new ArrayList<>();
    private final JTextField search = new JTextField(20);
    private final JLabel title = new JLabel(Langs.t("library.title"));
    private final JLabel en = new JLabel(Langs.t("library.sub"));
    private final JButton imp = Ui.secondary(Langs.t("library.import"));
    private final JButton add = Ui.button(Langs.t("library.add"));
    private String filter = "";
    private final List<String[]> backend = new ArrayList<>();      // 后端库条目 {name,fam,sm,note,smiles}
    private final Map<String, String> backendSeen = new LinkedHashMap<>(); // smiles -> name（去重，防与精选重复展示）
    private boolean backendLoaded = false;
    private int cacheTotal = 67;

    public LibraryPage(ApiClient api) {
        super(api);
        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(2, 2, 2, 2));
        add(buildTop(), BorderLayout.NORTH);
        render();

        JScrollPane sc = new JScrollPane(grid);
        sc.getViewport().setBackground(Ui.BG);
        sc.setBorder(null);
        sc.getVerticalScrollBar().setUnitIncrement(14);
        add(sc, BorderLayout.CENTER);

        loadBackend();
        Langs.addListener(this::refreshLang);
    }

    private void refreshLang() {
        title.setText(Langs.t("library.title"));
        en.setText(Langs.t("library.sub"));
        imp.setText(Langs.t("library.import"));
        add.setText(Langs.t("library.add"));
        search.setToolTipText(Langs.t("library.lookup"));
        for (JButton chip : chips) {
            String key = chipFam.getOrDefault(chip, "");
            if ("ALL".equals(key)) chip.setText(Langs.t("library.fAll"));
            else if ("L".equals(key)) chip.setText(Langs.t("library.backend"));
        }
        render();
    }

    private JPanel buildTop() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JPanel t = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        t.setOpaque(false);
        title.setFont(Ui.FONT_TITLE);
        title.setForeground(Ui.TEXT);
        en.setFont(Ui.MONO);
        en.setForeground(Ui.FAINT);
        t.add(title); t.add(en);
        row.add(t, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        imp.setPreferredSize(new Dimension(158, 30));
        imp.addActionListener(e -> note(Langs.t("library.parsed")));
        add.setPreferredSize(new Dimension(96, 30));
        add.addActionListener(e -> note(Langs.t("library.saved")));
        right.add(imp); right.add(add);
        row.add(right, BorderLayout.EAST);
        top.add(row);
        top.add(Box.createVerticalStrut(9));

        JPanel mid = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        mid.setOpaque(false);
        search.setFont(Ui.FONT_SMALL);
        search.setCaretColor(Ui.ACCENT);
        search.setBackground(Ui.PANEL2);
        search.setForeground(Ui.TEXT);
        search.setToolTipText(Langs.t("library.lookup"));
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.LINE), new EmptyBorder(5, 10, 5, 10)));
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { render(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { render(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { render(); }
        });
        mid.add(search);
        for (String[] f : FILTERS) mid.add(makeChip(f));
        mid.add(Box.createHorizontalStrut(6));
        mid.add(count);
        top.add(mid);
        return top;
    }

    private JButton makeChip(String[] f) {
        String key = f[0];
        boolean all = "ALL".equals(key);
        String label = all ? Langs.t("library.fAll") : ("BACKEND".equals(key) ? Langs.t("library.backend") : key);
        String tooltip = all ? Langs.t("library.fAllTooltip")
                : ("BACKEND".equals(key) ? Langs.t("library.backendTooltip") : "family " + key);
        JButton c = new JButton(label);
        c.setFont(Ui.FONT_SMALL);
        c.setToolTipText(tooltip);
        c.setFocusPainted(false);
        c.setPreferredSize(new Dimension(
                all ? 58 : ("BACKEND".equals(key) ? 92 : 62), 26));
        c.setContentAreaFilled(false);
        c.setOpaque(false);
        c.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        c.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        c.addActionListener(e -> {
            filter = all ? "" : ("BACKEND".equals(key) ? "L" : key);
            chips.forEach(x -> {
                boolean on = (all && x == c) || filter.equals(famKey(x));
                x.setForeground(on ? Ui.ACCENT : Ui.MUTED);
                x.setBorder(BorderFactory.createLineBorder(on ? Ui.LINE_HOT : Ui.LINE));
            });
            render();
        });
        if (all) c.setForeground(Ui.ACCENT);
        else c.setForeground(Ui.MUTED);
        chipFam.put(c, all ? "ALL" : ("BACKEND".equals(key) ? "L" : key));
        chips.add(c);
        return c;
    }

    /** 由 chip 反向得到其家族键："{key}+{tooltip}" 形式存于 tooltip rarity，现通过内存映射维护 */
    private final java.util.Map<JButton, String> chipFam = new java.util.HashMap<>();
    private String famKey(JButton x) {
        return chipFam.getOrDefault(x, "");
    }

    private JPanel card(String name, String fam, String sm, String note, String smiles) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setBackground(Ui.PANEL);
        p.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        p.setPreferredSize(new Dimension(0, 118));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.LINE),
                new EmptyBorder(10, 10, 10, 10)));

        // 左侧：RDKit 渲染的白底结构缩略图
        JPanel tv = new JPanel(new GridBagLayout());
        tv.setBackground(Color.WHITE);
        tv.setPreferredSize(new Dimension(104, 104));
        tv.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        JLabel img = new JLabel("…", SwingConstants.CENTER);
        img.setForeground(Ui.FAINT);
        tv.add(img);
        p.add(tv, BorderLayout.WEST);

        // 右侧：名称 + 家族 + 化学式 + 描述
        JPanel rc = new JPanel();
        rc.setLayout(new BoxLayout(rc, BoxLayout.Y_AXIS));
        rc.setOpaque(false);
        JPanel hd = new JPanel(new BorderLayout());
        hd.setOpaque(false);
        JLabel nm = new JLabel(toHtml(name));
        nm.setFont(new Font("微软雅黑", Font.BOLD, 13));
        nm.setForeground(Ui.SOFT);
        JLabel f = new JLabel(famDisplay(fam));
        f.setFont(Ui.MONO);
        f.setForeground(Ui.ACCENT);
        hd.add(nm, BorderLayout.WEST);
        hd.add(f, BorderLayout.EAST);
        JLabel sml = new JLabel(toHtml(sm));
        sml.setFont(Ui.MONO);
        sml.setForeground(Ui.MUTED);
        JLabel ds = new JLabel(toHtml(note));
        ds.setFont(Ui.FONT_SMALL);
        ds.setForeground(Ui.FAINT);
        rc.add(hd); rc.add(Box.createVerticalStrut(8));
        rc.add(sml); rc.add(Box.createVerticalStrut(6)); rc.add(ds);
        p.add(rc, BorderLayout.CENTER);

        loadThumb(img, smiles, name);
        p.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (smiles != null && !smiles.isEmpty()) openDetail(name, fam, sm, note, smiles);
                else note(Langs.t("library.loaded", name));
            }
            public void mouseEntered(java.awt.event.MouseEvent e) { p.setBorder(BorderFactory.createLineBorder(Ui.LINE_HOT)); }
            public void mouseExited(java.awt.event.MouseEvent e) { p.setBorder(BorderFactory.createLineBorder(Ui.LINE)); }
        });
        return p;
    }

    /** 家族键 → 显示文本（A1-A4 原样，后端库本地化） */
    private static String famDisplay(String key) {
        return "L".equals(key) ? Langs.t("library.backend") : key;
    }

    /** 后台拉取 Python 服务层化合物库并合并（去重） */
    private void loadBackend() {
        if (backendLoaded) return;
        backendLoaded = true;
        Thread t = new Thread(() -> {
            try {
                Object o = api.get("/api/compounds");
                Map<String, Object> m = Json.obj(o);
                List<?> cs = (List<?>) m.get("compounds");
                cacheTotal = Json.Int(m.get("total"));
                List<String[]> list = new ArrayList<>();
                if (cs != null) {
                    for (Object c : cs) {
                        Map<String, Object> cm = Json.obj(c);
                        String name = Json.str(cm.get("name"));
                        String smi = Json.str(cm.get("smiles"));
                        if (name == null || smi == null || smi.isEmpty()) continue;
                        if (!backendSeen.containsKey(smi)) {
                            backendSeen.put(smi, name);
                            list.add(new String[]{name, "L", trunc(smi, 30), Langs.t("library.backend"), smi});
                        }
                    }
                }
                synchronized (backend) {
                    backend.clear();
                    backend.addAll(list);
                }
                SwingUtilities.invokeLater(this::render);
            } catch (Exception e) {
                SwingUtilities.invokeLater(this::render);
            }
        }, "compounds-backend");
        t.setDaemon(true);
        t.start();
    }

    private static String trunc(String s, int n) {
        if (s == null || s.length() <= n) return s;
        return s.substring(0, n) + "…";
    }

    /** 后台拉取后端渲染的 2D 结构式 PNG，缩放后显示到缩略图（离线/失败显示 ×） */
    private void loadThumb(JLabel img, String smiles, String name) {
        Thread t = new Thread(() -> {
            try {
                String enc = java.net.URLEncoder.encode(smiles, "UTF-8");
                String encN = java.net.URLEncoder.encode(name == null ? "" : name, "UTF-8");
                byte[] bytes = api.getBinary("/api/compound/png?smiles=" + enc + "&name=" + encN);
                java.awt.image.BufferedImage bi =
                        javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                if (bi == null) throw new IllegalStateException("非图片");
                int w = 92;
                double k = w / (double) bi.getWidth();
                int h = Math.max(1, (int) (bi.getHeight() * k));
                java.awt.Image sc = bi.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH);
                final javax.swing.Icon ic = new javax.swing.ImageIcon(sc);
                SwingUtilities.invokeLater(() -> {
                    img.setIcon(ic);
                    img.setText("");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> img.setText("×"));
            }
        }, "thumb-" + smiles);
        t.setDaemon(true);
        t.start();
    }

    /** 点击卡片弹出结构详情对话框，展示大图 + 化合物信息 */
    private void openDetail(String name, String fam, String sm, String note, String smiles) {
        javax.swing.JDialog dlg = new javax.swing.JDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this),
                Langs.t("library.detail", name),
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setBackground(Ui.BG);
        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setBackground(Ui.BG);
        body.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel dp = new JPanel(new GridBagLayout());
        dp.setBackground(Color.WHITE);
        dp.setPreferredSize(new Dimension(360, 300));
        dp.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        JLabel big = new JLabel(Langs.t("library.loading"), SwingConstants.CENTER);
        big.setForeground(Ui.FAINT);
        dp.add(big);
        body.add(dp, BorderLayout.CENTER);

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        JLabel nm = new JLabel(toHtml(name));
        nm.setFont(Ui.FONT_TITLE);
        nm.setForeground(Ui.TEXT);
        JLabel fm = new JLabel(Langs.t("library.family", famDisplay(fam), smiles));
        fm.setFont(Ui.MONO);
        fm.setForeground(Ui.MUTED);
        JLabel smt = new JLabel(toHtml(sm));
        smt.setFont(Ui.MONO);
        smt.setForeground(Ui.SOFT);
        JLabel nt = new JLabel(toHtml("<html>" + note));
        nt.setFont(Ui.FONT_SMALL);
        nt.setForeground(Ui.FAINT);
        JLabel hint = Ui.muted(Langs.t("library.detailHint"));
        info.add(nm); info.add(Box.createVerticalStrut(10));
        info.add(fm); info.add(Box.createVerticalStrut(12));
        info.add(smt); info.add(Box.createVerticalStrut(10));
        info.add(nt);
        info.add(Box.createVerticalGlue());
        info.add(hint);
        body.add(info, BorderLayout.EAST);

        dlg.setContentPane(body);
        dlg.pack();
        dlg.setLocationRelativeTo(this);

        Thread t = new Thread(() -> {
            try {
                String enc = java.net.URLEncoder.encode(smiles, "UTF-8");
                String encN = java.net.URLEncoder.encode(name, "UTF-8");
                byte[] bytes = api.getBinary("/api/compound/png?smiles=" + enc + "&name=" + encN);
                java.awt.image.BufferedImage bi =
                        javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                if (bi == null) throw new IllegalStateException("非图片");
                final java.awt.Image sc = bigSize(bi, 340, 280);
                SwingUtilities.invokeLater(() -> {
                    big.setIcon(new javax.swing.ImageIcon(sc));
                    big.setText("");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> big.setText(Langs.t("library.fail")));
            }
        }, "dlg-" + smiles);
        t.setDaemon(true);
        t.start();

        dlg.setVisible(true);
    }

    private static java.awt.Image bigSize(java.awt.image.BufferedImage bi, int maxW, int maxH) {
        double k = Math.min(maxW / (double) bi.getWidth(), maxH / (double) bi.getHeight());
        if (k > 1.5) k = 1.5; // 放大封顶，避免过糊
        int w = Math.max(1, (int) (bi.getWidth() * k));
        int h = Math.max(1, (int) (bi.getHeight() * k));
        return bi.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH);
    }

    /** 规范化化学式/描述文本为 HTML：en-dash→'-'、Unicode 下标数字→<sub>、转义特殊字符 */
    private static String toHtml(String s) {
        if (s == null) return "";
        String t = s.replace('\u2013', '-').replace('\u2014', '-'); // en/em-dash → ASCII '-'
        t = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        char[] subs = {'\u2080', '\u2081', '\u2082', '\u2083', '\u2084',
                '\u2085', '\u2086', '\u2087', '\u2088', '\u2089'};
        for (int i = 0; i < subs.length; i++) {
            t = t.replace(String.valueOf(subs[i]), "<sub>" + i + "</sub>");
        }
        return "<html>" + t + "</html>";
    }

    private void render() {
        grid.removeAll();
        String kw = search.getText().trim().toLowerCase();
        int n = 0;
        for (String[] c : POOL) {
            if (matches(c, kw)) { grid.add(card(c[0], c[1], c[2], c[3], c[4])); n++; }
        }
        List<String[]> bk;
        synchronized (backend) { bk = new ArrayList<>(backend); }
        for (String[] c : bk) {
            if (matches(c, kw)) { grid.add(card(c[0], c[1], c[2], c[3], c[4])); n++; }
        }
        count.setText("  " + Langs.t("library.multiMatch", n, cacheTotal));
        grid.revalidate();
        grid.repaint();
    }

    private boolean matches(String[] c, String kw) {
        boolean famOk = filter.isEmpty() || filter.equals(c[1]);
        boolean kwOk = kw.isEmpty() || (c[0] != null && c[0].toLowerCase().contains(kw))
                || (c[1] != null && c[1].toLowerCase().contains(kw))
                || (c[2] != null && c[2].toLowerCase().contains(kw));
        return famOk && kwOk;
    }

    private void note(String msg) {
        count.setText("  " + msg);
        new javax.swing.Timer(2200, e -> count.setText(Langs.t("library.cached", cacheTotal))).start();
    }

    @Override public void onShow() {
        // 语言切换后刷新静态文案；后端库懒加载与重渲
        refreshLang();
        if (backend.isEmpty() && !backendLoaded) loadBackend();
    }
}