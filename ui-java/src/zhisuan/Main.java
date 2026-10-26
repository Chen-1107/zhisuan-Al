package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智算铝宾 · 三语言智能体工作台（Java Swing GUI）
 * 架构：Python(agent_server.py, Agent+服务层) / Java(本 GUI) / C++(molopt 计算核心 + molview 可视化)
 *
 * 三种装配模式（同一内核，不同 GUI）：
 *  - default   全功能工作台（AI 助手 / 知识星图 / 光谱 / 构型优化 / 计算任务 / 化合物库 / 3D / 知识库 / 记忆 / 设置）
 *  - audit     结果审查台（方案九）：AI 助手 / 审查台 / 设置
 *  - dispatch  计算调度中心（方案七）：AI 助手 / 调度中心 / 设置
 * 启动参数：java zhisuan.Main [--mode=audit|dispatch] [--port=8899]
 */
public class Main {
    public static final String DEFAULT_SERVER = "http://127.0.0.1:8899";
    public static final String MODE_AUDIT = "audit";
    public static final String MODE_DISPATCH = "dispatch";

    public static void main(String[] args) {
        String mode = "default";
        boolean portGiven = false;
        int port = 8899;
        for (String a : args) {
            if (a == null) continue;
            if (a.startsWith("--mode=")) mode = a.substring(7).trim();
            else if (a.startsWith("--port=")) {
                try { port = Integer.parseInt(a.substring(7).trim()); portGiven = true; }
                catch (NumberFormatException ignored) { }
            } else if (a.equals(MODE_AUDIT) || a.equals(MODE_DISPATCH)) mode = a;
        }
        Config.setMode(mode);
        Langs.setLang(Config.loadLang(Langs.ZH));
        // 命令行显式指定端口时优先使用（双实例部署），否则回退到本模式保存的 URL
        String url = portGiven ? "http://127.0.0.1:" + port : Config.loadServerUrl(DEFAULT_SERVER);
        final String url0 = url, mode0 = mode;
        zhisuan.Ui.applyDark();
        SwingUtilities.invokeLater(() -> new MainFrame(url0, mode0).setVisible(true));
    }
}

class MainFrame extends JFrame {
    private final ApiClient api = new ApiClient(Main.DEFAULT_SERVER);
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, Object[]> meta = new LinkedHashMap<>(); // key -> {titleKey, icon}
    private final List<String> navKeys = new ArrayList<>();
    private final Map<String, NavButton> navButtons = new LinkedHashMap<>();
    private final Map<String, TabButton> tabButtons = new LinkedHashMap<>();
    private final JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JLabel dot = new JLabel("●");
    private final JLabel statusText = new JLabel(Langs.t("status.connecting"));
    private final JTextArea footerLog = new JTextArea();
    private final JPanel outputWrap = new JPanel(new BorderLayout());
    private final JLabel cppState = new JLabel();
    private final JLabel secNote = new JLabel();
    private final JLabel modelChip = new JLabel();
    private final JLabel brandSub = new JLabel();
    private final JLabel segAgent = new JLabel();
    private final JLabel segTerm = new JLabel();
    private final String mode;
    private String current = "chat";

    MainFrame(String serverUrl, String mode) {
        super(Langs.t("app.title"));
        this.mode = mode;
        api.setBase(serverUrl);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1340, 730);
        setMinimumSize(new Dimension(1120, 660));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        applyWindowIcon();

        registerForMode();

        // 页面注册须在 buildMid() 之前：buildActivityBar() 会读取已注册的导航按钮
        add(buildTopBar(), BorderLayout.NORTH);
        add(buildMid(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        // F1–F10 页面切换快捷键
        for (int i = 0; i < navKeys.size(); i++) {
            final String key = navKeys.get(i);
            javax.swing.KeyStroke ks = javax.swing.KeyStroke.getKeyStroke("F" + (i + 1));
            getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, "nav-" + key);
            getRootPane().getActionMap().put("nav-" + key, new javax.swing.AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { select(key); }
            });
        }
        // F12：将窗口内容渲染导出为 PNG（自动化验证用）
        javax.swing.KeyStroke f12 = javax.swing.KeyStroke.getKeyStroke("F12");
        getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(f12, "shot");
        getRootPane().getActionMap().put("shot", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { exportWindow(); }
        });

        Langs.addListener(this::applyLang);
        applyLang();
        select("chat");
        refreshStatus();
        new Timer(30_000, e -> refreshStatus()).start();
    }

    // ---------------- 窗口图标 ----------------
    /** 从 assets/icons 读取多尺寸 PNG（类路径 ..\\assets 由启动器与 run.bat 提供）。 */
    private void applyWindowIcon() {
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        List<Image> images = new ArrayList<>();
        for (int sz : sizes) {
            java.net.URL url = MainFrame.class.getResource("/icons/app_" + sz + ".png");
            if (url == null) continue;
            try {
                images.add(javax.imageio.ImageIO.read(url));
            } catch (java.io.IOException ignored) {
                // 单个尺寸读取失败时跳过，其余尺寸仍可用
            }
        }
        if (!images.isEmpty()) setIconImages(images);
    }

    // ---------------- 模式装配 ----------------
    private void registerForMode() {
        if (Main.MODE_AUDIT.equals(mode)) {
            registerPage("chat", "nav.chat", Icons.chat(), new ChatPage(api));
            registerPage("audit", "nav.audit", Icons.jobs(), new AuditPage(api));
            registerPage("settings", "nav.settings", Icons.gear(), new SettingsPage(api, this::refreshStatus));
        } else if (Main.MODE_DISPATCH.equals(mode)) {
            registerPage("chat", "nav.chat", Icons.chat(), new ChatPage(api));
            DispatchPage dp = new DispatchPage(api);
            dp.onBackToChat = () -> select("chat");
            registerPage("dispatch", "nav.dispatch", Icons.dispatch(), dp);
            registerPage("settings", "nav.settings", Icons.gear(), new SettingsPage(api, this::refreshStatus));
        } else {
            registerPage("chat", "nav.chat", Icons.chat(), new ChatPage(api));
            registerPage("starmap", "nav.starmap", Icons.star(), new StarMapPage(api));
            registerPage("spectrum", "nav.spectrum", Icons.spectrum(), new SpectrumPage(api));
            registerPage("optimize", "nav.optimize", Icons.optimize(), new OptimizePage(api));
            registerPage("jobs", "nav.jobs", Icons.jobs(), new JobsPage(api));
            registerPage("library", "nav.library", Icons.library(), new LibraryPage(api));
            registerPage("mol", "nav.mol", Icons.molview(), new MolViewPage(api));
            registerPage("knowledge", "nav.knowledge", Icons.book(), new KnowledgePage(api));
            registerPage("memory", "nav.memory", Icons.memory(), new MemoryPage(api));
            registerPage("workflow", "nav.workflow", Icons.workflow(), new WorkflowPage(api));
            registerPage("settings", "nav.settings", Icons.gear(), new SettingsPage(api, this::refreshStatus));
        }
    }

    // ---------------- 语言刷新 ----------------
    private void applyLang() {
        setTitle(Langs.t("app.title"));
        brandSub.setText("<html>智算铝宾 <b style='color:#dce7ff'>" + Langs.t("brand.sub") + "</b></html>");
        modelChip.setText("<html>" + Langs.t("top.model") + " <b style='color:#3ce0ff'>qwen3.5:2b</b> · "
                + Langs.t("top.local") + "</html>");
        for (String key : navKeys) {
            NavButton nb = navButtons.get(key);
            String title = Langs.t((String) meta.get(key)[0]);
            nb.setNavTitle(title);
            tabButtons.get(key).setTabTitle(title);
        }
        segAgent.setText(Langs.t("footer.agent"));
        segTerm.setText(Langs.t("footer.term"));
        cppState.setText("<html>" + Langs.t("status.cpp") + " <b style='color:#2fe6a1'>"
                + Langs.t("status.ready") + "</b></html>");
        secNote.setText(Langs.t("status.datasec"));
        refreshStatus();
        Page p = pages.get(current);
        if (p != null) SwingUtilities.invokeLater(p::onShow);
    }

    // ---------------- 顶栏 ----------------
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(Ui.SIDEBAR);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.LINE));
        bar.setPreferredSize(new Dimension(0, 38));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        left.setOpaque(false);
        JPanel logo = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientSpan(0, 0, getWidth(), getHeight()));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
            }
        };
        logo.setPreferredSize(new Dimension(20, 20));
        left.add(logo);
        brandSub.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        brandSub.setForeground(Ui.MUTED);
        left.add(brandSub);
        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(buildLangSwitcher());
        modelChip.setFont(Ui.FONT_SMALL);
        modelChip.setForeground(Ui.SOFT);
        modelChip.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.LINE),
                new EmptyBorder(3, 9, 3, 9)));
        right.add(modelChip);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** 三语言切换按钮组（中 / EN / DE） */
    private JPanel buildLangSwitcher() {
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 5));
        wrap.setOpaque(false);
        JLabel lbl = new JLabel(Langs.t("top.lang"));
        lbl.setFont(Ui.FONT_SMALL);
        lbl.setForeground(Ui.FAINT);
        wrap.add(lbl);
        for (String code : Langs.LANGS) {
            String label = Langs.ZH.equals(code) ? "中" : code.toUpperCase();
            LangButton b = new LangButton(code, label);
            b.addActionListener(e -> {
                if (Langs.lang().equals(code)) return;
                Langs.setLang(code);
                try { Config.saveLang(code); } catch (Exception ignored) {}
            });
            wrap.add(b);
            Langs.addListener(() -> b.setSelected(Langs.lang().equals(code)));
            b.setSelected(Langs.lang().equals(code));
        }
        return wrap;
    }

    private static class GradientSpan extends java.awt.GradientPaint {
        GradientSpan(float x1, float y1, float x2, float y2) {
            super(x1, y1, new Color(60, 224, 255), x2, y2, new Color(255, 95, 217));
        }
    }

    /** 语言切换按钮：当前语言高亮 */
    static class LangButton extends JButton {
        private boolean selected;

        LangButton(String code, String text) {
            super(text);
            setFont(Ui.FONT_SMALL);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMargin(new Insets(2, 8, 2, 8));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setSelected(false);
        }

        public void setSelected(boolean sel) {
            this.selected = sel;
            setForeground(sel ? Ui.ACCENT : Ui.FAINT);
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            if (selected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(60, 224, 255, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(60, 224, 255, 160));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    // ---------------- 中部 ----------------
    private JPanel buildMid() {
        JPanel mid = new JPanel(new BorderLayout());
        mid.setBackground(Ui.BG);

        JPanel nav = buildActivityBar();
        mid.add(nav, BorderLayout.WEST);

        content.setBackground(Ui.BG);
        content.setBorder(new EmptyBorder(12, 14, 10, 14));
        JPanel editor = new JPanel(new BorderLayout());
        editor.setOpaque(false);
        editor.add(buildTabs(), BorderLayout.NORTH);
        editor.add(content, BorderLayout.CENTER);

        JPanel stack = new JPanel(new BorderLayout());
        stack.setOpaque(false);
        stack.add(editor, BorderLayout.CENTER);
        stack.add(buildOutput(), BorderLayout.SOUTH);
        mid.add(stack, BorderLayout.CENTER);
        return mid;
    }

    private JPanel buildTabs() {
        tabs.setOpaque(false);
        tabs.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.LINE));
        tabs.setPreferredSize(new Dimension(0, 32));
        return tabs;
    }

    @SuppressWarnings("unchecked")
    private void registerPage(String key, String titleKey, javax.swing.Icon icon, Page page) {
        pages.put(key, page);
        content.add(page, key);
        navKeys.add(key);
        meta.put(key, new Object[]{titleKey, icon});

        NavButton nb = new NavButton(Langs.t(titleKey), icon);
        nb.setToolTipText(Langs.t(titleKey) + "（F" + navKeys.size() + "）");
        nb.addActionListener(e -> select(key));
        navButtons.put(key, nb);

        TabButton tb = new TabButton(Langs.t(titleKey), icon);
        tb.addActionListener(e -> select(key));
        tabButtons.put(key, tb);
        tabs.add(tb);
    }

    private JPanel buildActivityBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(Ui.SIDEBAR);
        bar.setPreferredSize(new Dimension(104, 0));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Ui.LINE));

        JLabel brand = new JLabel("<html><div style='color:#5ffcff;font-weight:bold;text-align:center'>◈</div>"
                + "<div style='color:#dce7ff;font-size:9px;text-align:center'>智算铝宾</div></html>", JLabel.CENTER);
        brand.setAlignmentX(Component.CENTER_ALIGNMENT);
        brand.setBorder(new EmptyBorder(10, 0, 12, 0));
        bar.add(brand);

        for (String key : navKeys) {
            if ("settings".equals(key)) continue;
            NavButton b = navButtons.get(key);
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            bar.add(b);
        }

        bar.add(Box.createVerticalGlue());

        NavButton gear = navButtons.get("settings");
        gear.setAlignmentX(Component.CENTER_ALIGNMENT);
        bar.add(gear);

        JLabel foot = new JLabel("<html><div style='color:#5c6f94;text-align:center;font-size:9px'>"
                + Langs.t("top.local") + "</div>"
                + "<div style='color:#3ce0ff;text-align:center;font-size:9px'>" + Langs.t("top.offline")
                + "</div></html>", JLabel.CENTER);
        foot.setAlignmentX(Component.CENTER_ALIGNMENT);
        foot.setBorder(new EmptyBorder(8, 0, 8, 0));
        bar.add(foot);
        return bar;
    }

    private JPanel buildOutput() {
        outputWrap.setBackground(Ui.PANEL);
        outputWrap.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.LINE));
        outputWrap.setPreferredSize(new Dimension(0, 128));
        outputWrap.setMinimumSize(new Dimension(0, 96));

        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Ui.PANEL2);
        head.setBorder(new EmptyBorder(4, 10, 4, 8));
        JPanel segL = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        segL.setOpaque(false);
        segAgent.setFont(new Font("微软雅黑", Font.BOLD, 11));
        segAgent.setForeground(Ui.ACCENT);
        segL.add(segAgent);
        segTerm.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        segTerm.setForeground(Ui.FAINT);
        segL.add(segTerm);
        head.add(segL, BorderLayout.WEST);

        JPanel segR = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        segR.setOpaque(false);
        JButton cfg = new JButton("⌁");
        JButton clear = new JButton("×");
        JButton fold = new JButton("⌄");
        for (JButton b : new JButton[]{cfg, clear, fold}) {
            b.setFont(Ui.FONT_SMALL);
            b.setOpaque(false); b.setContentAreaFilled(false); b.setFocusPainted(false);
            b.setBorder(new EmptyBorder(1, 7, 1, 7));
            b.setForeground(Ui.MUTED);
            b.setToolTipText(Langs.t("footer.panel"));
            b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            segR.add(b);
        }
        clear.addActionListener(e -> footerLog.setText(""));
        fold.addActionListener(e -> {
            boolean show = outputWrap.getPreferredSize().height == 0;
            outputWrap.setPreferredSize(new Dimension(0, show ? 128 : 0));
            outputWrap.setMinimumSize(new Dimension(0, show ? 96 : 0));
            fold.setText(show ? "⌄" : "⌃");
            outputWrap.revalidate();
        });
        head.add(segR, BorderLayout.EAST);
        outputWrap.add(head, BorderLayout.NORTH);

        footerLog.setEditable(false);
        footerLog.setFont(new Font("Dialog", Font.PLAIN, 11));
        footerLog.setForeground(Ui.MUTED);
        footerLog.setBackground(Ui.PANEL);
        footerLog.setLineWrap(true);
        footerLog.setWrapStyleWord(true);
        footerLog.setText(Langs.t("footer.ready") + "\n");
        JScrollPane sp = new JScrollPane(footerLog);
        sp.setBorder(null);
        sp.getViewport().setBackground(Ui.PANEL);
        outputWrap.add(sp, BorderLayout.CENTER);
        return outputWrap;
    }

    private void addFooterLog(String line) {
        footerLog.append(line + "\n");
        footerLog.setCaretPosition(footerLog.getDocument().getLength());
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Ui.PANEL2);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.LINE));
        bar.setPreferredSize(new Dimension(0, 26));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        left.setOpaque(false);
        dot.setFont(Ui.FONT_BOLD);
        dot.setForeground(Ui.WARN);
        statusText.setFont(Ui.FONT_SMALL);
        statusText.setForeground(Ui.MUTED);
        left.add(dot);
        left.add(statusText);
        bar.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 3));
        right.setOpaque(false);
        cppState.setFont(Ui.FONT_SMALL);
        cppState.setForeground(Ui.MUTED);
        secNote.setFont(Ui.MONO);
        secNote.setForeground(Ui.FAINT);
        right.add(cppState); right.add(secNote);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void exportWindow() {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                    getContentPane().getWidth(), getContentPane().getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            getContentPane().printAll(g);
            g.dispose();
            String path = System.getProperty("java.io.tmpdir") + "zhisuan_export_" + mode + ".png";
            javax.imageio.ImageIO.write(img, "png", new java.io.File(path));
            statusText.setText(Langs.t("status.connected") + " · " + path);
            System.out.println("[export] " + path + " " + img.getWidth() + "x" + img.getHeight());
        } catch (Exception ex) {
            System.out.println("[export] failed: " + ex);
        }
    }

    void select(String key) {
        current = key;
        for (String k : navKeys) navButtons.get(k).setSelectedState(k.equals(key));
        for (String k : navKeys) tabButtons.get(k).setSelectedState(k.equals(key));
        cards.show(content, key);
        Page p = pages.get(key);
        if (p != null) SwingUtilities.invokeLater(p::onShow);
        addFooterLog("[NAV] → " + current);
    }

    String currentPage() { return current; }

    void refreshStatus() {
        new Thread(() -> {
            String text;
            Color color;
            try {
                Object h = api.get("/api/health");
                Map<String, Object> m = Json.obj(h);
                boolean modelReady = Json.bool(m.get("model_ready"));
                color = modelReady ? Ui.OK : Ui.WARN;
                text = String.format(Langs.t("status.connFmt"),
                        api.getBase(), Json.str(m.get("model")),
                        modelReady ? Langs.t("status.ready") : Langs.t("status.notready"),
                        Json.Int(m.get("compounds")), Json.Int(m.get("skills")),
                        (Json.bool(m.get("molopt")) && Json.bool(m.get("molview")))
                                ? Langs.t("status.ready") : Langs.t("status.missing"));
            } catch (Exception e) {
                color = Ui.BAD;
                text = String.format(Langs.t("status.offline"), api.getBase());
            }
            final Color c = color;
            final String t = text;
            SwingUtilities.invokeLater(() -> { dot.setForeground(c); statusText.setText(t); });
        }, "health-check").start();
    }

    // ---------------- 活动栏按钮（图标在上、小字在下，左边缘指示条） ----------------
    static class NavButton extends JButton {
        private boolean selected, hover;

        NavButton(String text, javax.swing.Icon icon) {
            super(text, icon);
            setFont(new java.awt.Font("微软雅黑", Font.PLAIN, 10));
            setVerticalTextPosition(BOTTOM);
            setHorizontalTextPosition(CENTER);
            setIconTextGap(2);
            setMargin(new Insets(0, 0, 0, 0));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setAlignmentX(Component.CENTER_ALIGNMENT);
            setMaximumSize(new Dimension(104, 50));
            setPreferredSize(new Dimension(104, 50));
            setForeground(new Color(203, 213, 225));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        void setNavTitle(String text) {
            setText(text);
        }

        void setSelectedState(boolean sel) {
            this.selected = sel;
            setForeground(sel ? Ui.ACCENT : new Color(203, 213, 225));
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // VSCode 活动栏：选中项在左侧外缘画一条细指示条，辅以轻微底衬
            if (selected) {
                g2.setColor(new Color(60, 224, 255, 26));
                g2.fillRect(0, 4, getWidth(), getHeight() - 8);
                g2.setColor(new Color(60, 224, 255));
                g2.fillRect(0, 8, 3, getHeight() - 16);
            } else if (hover) {
                g2.setColor(new Color(24, 40, 72));
                g2.fillRect(2, 3, getWidth() - 4, getHeight() - 6);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------- 页签按钮 ----------------
    static class TabButton extends JButton {
        private boolean selected;

        TabButton(String text, javax.swing.Icon icon) {
            super(text);
            setFont(Ui.FONT_SMALL);
            setForeground(Ui.MUTED);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMargin(new Insets(4, 12, 4, 12));
            setPreferredSize(new Dimension(96, 30));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        void setTabTitle(String text) {
            setText(text);
        }

        void setSelectedState(boolean sel) {
            this.selected = sel;
            setForeground(sel ? Ui.TEXT : Ui.MUTED);
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            if (selected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(18, 30, 56, 200));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(60, 224, 255));
                g2.fillRect(0, 0, getWidth(), 2);
                g2.dispose();
                setForeground(Ui.TEXT);
            }
            super.paintComponent(g);
        }
    }
}
