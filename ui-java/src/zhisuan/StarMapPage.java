package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** 知识星图：技能分类为星座（星形枢纽），技能为星体，共享标签生成星链 */
public class StarMapPage extends Page {
    private static final Color[] PALETTE = {
            new Color(96, 165, 250), new Color(167, 139, 250), new Color(52, 211, 153),
            new Color(251, 191, 36), new Color(248, 113, 113), new Color(45, 212, 191),
            new Color(232, 121, 249), new Color(148, 163, 184)
    };

    static final class Node {
        String id, title, category, description;
        List<String> tags = new ArrayList<>();
        int size;
        double x, y, vx, vy;
        boolean hub, fixed;
        Color color;
    }

    static final class Edge {
        final Node a, b;
        final boolean constellation;

        Edge(Node a, Node b, boolean constellation) {
            this.a = a;
            this.b = b;
            this.constellation = constellation;
        }
    }

    private final StarCanvas canvas = new StarCanvas();
    private final JPanel detail = new JPanel(new BorderLayout());
    private final JLabel detailTitle = new JLabel();
    private final JLabel detailCat = new JLabel(" ");
    private final JLabel detailTags = new JLabel(" ");
    private final JTextArea detailDesc = new JTextArea();
    private final JButton fullBtn = Ui.secondary("");
    private final JButton bReload = Ui.secondary("");
    private final JButton bRelayout = Ui.secondary("");
    private final JLabel info = Ui.muted("");
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<int[]> bgStars = new ArrayList<>();
    private final javax.swing.Timer timer;
    private final Random random = new Random(42);
    private double alpha = 0;
    private Node selected;
    private Node dragNode;

    StarMapPage(ApiClient api) {
        super(api);
        setLayout(new BorderLayout(0, 8));
        Langs.addListener(this::applyLang);

        // 顶部工具栏
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.setBackground(Ui.CARD);
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.LINE), BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        bReload.addActionListener(e -> load());
        bRelayout.addActionListener(e -> initLayout());
        top.add(bReload);
        top.add(bRelayout);
        top.add(javax.swing.Box.createHorizontalStrut(12));
        top.add(info);
        add(top, BorderLayout.NORTH);

        // 主体：星图 + 右侧详情
        detailDesc.setWrapStyleWord(true);
        detailDesc.setLineWrap(true);
        detailDesc.setEditable(false);
        detailDesc.setFont(Ui.FONT);
        detailDesc.setBackground(Ui.BG);
        detailDesc.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        detailTitle.setFont(new Font("微软雅黑", Font.BOLD, 15));
        detailCat.setFont(Ui.FONT_SMALL);
        detailTags.setFont(Ui.FONT_SMALL);
        JPanel head = new JPanel();
        head.setLayout(new javax.swing.BoxLayout(head, javax.swing.BoxLayout.Y_AXIS));
        head.setBackground(Ui.CARD);
        head.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        head.add(detailTitle);
        head.add(detailCat);
        head.add(javax.swing.Box.createVerticalStrut(4));
        head.add(detailTags);
        detail.setBackground(Ui.CARD);
        detail.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Ui.LINE));
        detail.add(head, BorderLayout.NORTH);
        detail.add(new JScrollPane(detailDesc), BorderLayout.CENTER);
        JPanel dbtn = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        dbtn.setBackground(Ui.CARD);
        dbtn.add(fullBtn);
        detail.add(dbtn, BorderLayout.SOUTH);
        fullBtn.addActionListener(e -> showFullContent());
        detail.setPreferredSize(new Dimension(320, 0));
        detail.setMinimumSize(new Dimension(280, 0));
        add(canvas, BorderLayout.CENTER);
        add(detail, BorderLayout.EAST);

        timer = new Timer(16, e -> {
            step();
            canvas.repaint();
        });

        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Node n = canvas.hit(e.getX(), e.getY());
                if (n != null) {
                    dragNode = n;
                    n.fixed = true;
                    alpha = Math.max(alpha, 0.25);
                    if (!timer.isRunning()) timer.start();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragNode != null) {
                    if (!dragNode.hub) dragNode.fixed = false;
                    if (dragNode == selected || selected == null) selectNode(dragNode);
                    dragNode = null;
                } else {
                    Node n = canvas.hit(e.getX(), e.getY());
                    selectNode(n);
                }
            }
        });
        canvas.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragNode != null) {
                    dragNode.x = e.getX();
                    dragNode.y = e.getY();
                    dragNode.vx = 0;
                    dragNode.vy = 0;
                    canvas.repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Node n = canvas.hit(e.getX(), e.getY());
                canvas.setCursor(n != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                canvas.setToolTipText(n == null ? null : n.title);
            }
        });
    }

    private void applyLang() {
        bReload.setText(Langs.t("starmap.reload"));
        bRelayout.setText(Langs.t("starmap.relayout"));
        fullBtn.setText(Langs.t("starmap.full"));
        if (info.getText() == null || info.getText().isEmpty() || Langs.t("starmap.loading").equals(info.getText())) {
            info.setText(Langs.t("starmap.loading"));
        }
        if (selected == null) showHint();
        else showNodeDetail(selected);
    }

    private void showHint() {
        detailTitle.setText(Langs.t("starmap.hint"));
        detailCat.setText(" ");
        detailTags.setText(" ");
        detailDesc.setText(Langs.t("starmap.desc"));
    }

    private void showNodeDetail(Node n) {
        detailTitle.setText(n.title);
        detailCat.setText(String.format(Langs.t("starmap.cat"), n.category, n.hub ? Langs.t("starmap.catHub") : ""));
        detailCat.setForeground(n.color);
        detailTags.setText(n.tags.isEmpty() ? Langs.t("starmap.noTags") : String.format(Langs.t("starmap.tags"), String.join(" · ", n.tags)));
        detailDesc.setText(n.description == null || n.description.isEmpty()
                ? Langs.t("starmap.noDesc") : n.description);
        detailDesc.setCaretPosition(0);
    }

    @Override
    public void onShow() {
        SwingUtilities.invokeLater(() -> System.out.println("[starmap] page W=" + getWidth()
                + " canvas=" + canvas.getWidth() + "x" + canvas.getHeight()
                + " detail=" + detail.getWidth() + "x" + detail.getHeight()));
        applyLang();
        if (nodes.isEmpty()) load();
    }

    private void load() {
        info.setText(Langs.t("starmap.loading"));
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.get("/api/knowledge/skills");
            }

            @Override
            protected void done() {
                try {
                    build(Json.arr(Json.obj(get()).get("skills")));
                    info.setText(String.format(Langs.t("starmap.info"),
                            (int) nodes.values().stream().filter(n -> !n.hub).count(),
                            (int) nodes.values().stream().filter(n -> n.hub).count(),
                            edges.size()));
                } catch (Exception e) {
                    info.setText(Langs.t("starmap.loadFail") + e.getMessage());
                }
            }
        }.execute();
    }

    private void build(List<Object> skills) {
        nodes.clear();
        edges.clear();
        Map<String, Color> catColor = new HashMap<>();
        int ci = 0;
        List<Node> skillNodes = new ArrayList<>();

        // 分类枢纽
        for (Object o : skills) {
            Map<String, Object> m = Json.obj(o);
            String cat = Json.str(m.get("category"));
            if (!catColor.containsKey(cat)) {
                catColor.put(cat, PALETTE[ci % PALETTE.length]);
                ci++;
            }
        }
        for (Map.Entry<String, Color> e : catColor.entrySet()) {
            Node hub = new Node();
            hub.id = "hub:" + e.getKey();
            hub.title = e.getKey();
            hub.category = e.getKey();
            hub.hub = true;
            hub.fixed = true;
            hub.color = e.getValue();
            nodes.put(hub.id, hub);
        }

        // 技能星体
        for (Object o : skills) {
            Map<String, Object> m = Json.obj(o);
            Node n = new Node();
            n.id = Json.str(m.get("name"));
            n.title = Json.str(m.get("title"));
            n.category = Json.str(m.get("category"));
            n.description = Json.str(m.get("description"));
            n.size = Json.Int(m.get("size"));
            n.tags = new ArrayList<>();
            for (Object t : Json.arr(m.get("tags"))) n.tags.add(Json.str(t));
            n.color = catColor.getOrDefault(n.category, PALETTE[PALETTE.length - 1]);
            nodes.put(n.id, n);
            skillNodes.add(n);
            Node hub = nodes.get("hub:" + n.category);
            if (hub != null) edges.add(new Edge(n, hub, true));
        }

        // 共享标签 → 星链
        for (int i = 0; i < skillNodes.size(); i++) {
            for (int j = i + 1; j < skillNodes.size(); j++) {
                Set<String> common = new HashSet<>(skillNodes.get(i).tags);
                common.retainAll(skillNodes.get(j).tags);
                if (!common.isEmpty()) edges.add(new Edge(skillNodes.get(i), skillNodes.get(j), false));
            }
        }

        initLayout();
    }

    private void initLayout() {
        if (nodes.isEmpty()) return;
        int w = Math.max(canvas.getWidth(), 700);
        int h = Math.max(canvas.getHeight(), 460);
        List<Node> hubs = new ArrayList<>();
        nodes.values().forEach(n -> {
            if (n.hub) hubs.add(n);
            else n.fixed = false;
        });
        double r = Math.min(w, h) * 0.30;
        for (int k = 0; k < hubs.size(); k++) {
            double ang = -Math.PI / 2 + 2 * Math.PI * k / hubs.size();
            hubs.get(k).x = w / 2.0 + r * Math.cos(ang);
            hubs.get(k).y = h / 2.0 + r * Math.sin(ang);
            hubs.get(k).x = Math.max(w * 0.14, Math.min(w * 0.86, hubs.get(k).x));
            hubs.get(k).y = Math.max(h * 0.16, Math.min(h * 0.84, hubs.get(k).y));
            hubs.get(k).vx = 0;
            hubs.get(k).vy = 0;
        }
        for (Node n : nodes.values()) {
            if (n.hub) continue;
            Node hub = nodes.get("hub:" + n.category);
            if (hub == null) continue;
            n.x = hub.x + (random.nextDouble() - 0.5) * 200;
            n.y = hub.y + (random.nextDouble() - 0.5) * 200;
            n.vx = 0;
            n.vy = 0;
        }
        alpha = 1.0;
        if (!timer.isRunning()) timer.start();
        canvas.repaint();
    }

    private void step() {
        List<Node> ns = new ArrayList<>(nodes.values());
        int n = ns.size();
        double a = alpha;
        // 库仑斥力
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Node na = ns.get(i), nb = ns.get(j);
                double dx = nb.x - na.x, dy = nb.y - na.y;
                double d2 = dx * dx + dy * dy;
                if (d2 < 4) {
                    dx = random.nextDouble() - 0.5;
                    dy = random.nextDouble() - 0.5;
                    d2 = 4;
                }
                double d = Math.sqrt(d2);
                double f = 2500.0 / d2 * a;
                double fx = f * dx / d, fy = f * dy / d;
                na.vx -= fx;
                na.vy -= fy;
                nb.vx += fx;
                nb.vy += fy;
            }
        }
        // 弹簧引力（星座链更紧）
        for (Edge e : edges) {
            double dx = e.b.x - e.a.x, dy = e.b.y - e.a.y;
            double d = Math.max(1, Math.hypot(dx, dy));
            double rest = e.constellation ? 110 : 170;
            double f = (d - rest) * (e.constellation ? 0.08 : 0.03) * a;
            double fx = f * dx / d, fy = f * dy / d;
            e.a.vx += fx;
            e.a.vy += fy;
            e.b.vx -= fx;
            e.b.vy -= fy;
        }
        // 向心 + 积分
        int w = Math.max(canvas.getWidth(), 700);
        int h = Math.max(canvas.getHeight(), 460);
        for (Node node : ns) {
            node.vx += (w / 2.0 - node.x) * 0.004 * a;
            node.vy += (h / 2.0 - node.y) * 0.004 * a;
            // 软边界：靠近边缘时受向内推力，保证星座整体可见
            double m = 80;
            if (node.x < m) node.vx += (m - node.x) * 0.018 * a;
            if (node.x > w - m) node.vx -= (node.x - (w - m)) * 0.018 * a;
            if (node.y < m) node.vy += (m - node.y) * 0.018 * a;
            if (node.y > h - m) node.vy -= (node.y - (h - m)) * 0.018 * a;
            if (node.fixed) {
                node.vx = 0;
                node.vy = 0;
                continue;
            }
            node.vx *= 0.82;
            node.vy *= 0.82;
            node.vx = Math.max(-10, Math.min(10, node.vx));
            node.vy = Math.max(-10, Math.min(10, node.vy));
            node.x = Math.max(26, Math.min(w - 26, node.x + node.vx));
            node.y = Math.max(26, Math.min(h - 26, node.y + node.vy));
        }
        alpha *= 0.992;
        if (alpha < 0.02 && dragNode == null) {
            timer.stop();
        }
    }

    private void selectNode(Node n) {
        selected = n;
        if (n == null) {
            showHint();
            return;
        }
        showNodeDetail(n);
        canvas.repaint();
    }

    private void showFullContent() {
        if (selected == null || selected.hub) return;
        final String name = selected.id;
        final String title = selected.title;
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.get("/api/knowledge/skill?name=" + java.net.URLEncoder.encode(name,
                        java.nio.charset.StandardCharsets.UTF_8));
            }

            @Override
            protected void done() {
                String content;
                try {
                    Map<String, Object> m = Json.obj(get());
                    content = Json.str(m.get("content"));
                } catch (Exception e) {
                    content = "读取失败: " + e.getMessage();
                }
                JDialog d = new JDialog(javax.swing.SwingUtilities.getWindowAncestor(StarMapPage.this),
                        title, java.awt.Dialog.ModalityType.MODELESS);
                d.setSize(640, 560);
                d.setLocationRelativeTo(StarMapPage.this);
                JTextArea ta = new JTextArea(content);
                ta.setEditable(false);
                ta.setFont(new Font("微软雅黑", Font.PLAIN, 13));
                ta.setLineWrap(true);
                ta.setWrapStyleWord(true);
                d.add(new JScrollPane(ta));
                d.setVisible(true);
            }
        }.execute();
    }

    // ---------------- 星图画布 ----------------
    private class StarCanvas extends JPanel {
        StarCanvas() {
            setBackground(new Color(10, 16, 32));
            setPreferredSize(new Dimension(700, 460));
        }

        Node hit(int mx, int my) {
            Node best = null;
            double bestD = 18 * 18;
            for (Node n : nodes.values()) {
                double r = n.hub ? 16 : radius(n) + 6;
                double d = (n.x - mx) * (n.x - mx) + (n.y - my) * (n.y - my);
                if (d < Math.max(r * r, 100) && d < bestD) {
                    bestD = d;
                    best = n;
                }
            }
            return best;
        }

        private double radius(Node n) {
            return n.hub ? 15 : 6 + Math.min(7, n.size / 1500.0);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            // 深空渐变
            g2.setPaint(new java.awt.GradientPaint(0, 0, new Color(9, 14, 30),
                    0, h, new Color(25, 36, 64)));
            g2.fillRect(0, 0, w, h);

            // 背景小星星
            if (bgStars.isEmpty() || bgStars.get(0)[0] != w) {
                bgStars.clear();
                Random r = new Random(7);
                for (int k = 0; k < 130; k++)
                    bgStars.add(new int[]{w, r.nextInt(w), r.nextInt(h), 80 + r.nextInt(90),
                            r.nextInt(255)});
            }
            for (int[] s : bgStars) {
                g2.setColor(new Color(190, 210, 240, s[4] * s[3] / 600));
                g2.fillOval(s[1], s[2], 2, 2);
            }

            // 星链
            for (Edge e : edges) {
                if (e.constellation) {
                    g2.setColor(new Color(e.a.color.getRed(), e.a.color.getGreen(),
                            e.a.color.getBlue(), 110));
                    g2.setStroke(new BasicStroke(1.4f));
                } else {
                    g2.setColor(new Color(148, 180, 255, 42));
                    g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND, 0, new float[]{4, 5}, 0));
                }
                g2.drawLine((int) e.a.x, (int) e.a.y, (int) e.b.x, (int) e.b.y);
            }
            g2.setStroke(new BasicStroke(1f));

            // 星体
            g2.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            for (Node n : nodes.values()) {
                if (n.hub) {
                    // 光晕
                    g2.setColor(new Color(n.color.getRed(), n.color.getGreen(), n.color.getBlue(), 46));
                    g2.fillOval((int) n.x - 30, (int) n.y - 30, 60, 60);
                    // 星形
                    Polygon p = new Polygon();
                    for (int k = 0; k < 10; k++) {
                        double ang = -Math.PI / 2 + k * Math.PI / 5;
                        double rr = (k % 2 == 0) ? 15 : 6.5;
                        p.addPoint((int) Math.round(n.x + rr * Math.cos(ang)),
                                (int) Math.round(n.y + rr * Math.sin(ang)));
                    }
                    g2.setColor(n.color);
                    g2.fillPolygon(p);
                    g2.setFont(new Font("微软雅黑", Font.BOLD, 14));
                    drawLabel(g2, n.title, n.x, n.y + 32, true);
                    g2.setFont(new Font("微软雅黑", Font.PLAIN, 12));
                } else {
                    double r = radius(n);
                    // 光晕
                    Color glow = new Color(n.color.getRed(), n.color.getGreen(), n.color.getBlue(), 60);
                    g2.setColor(glow);
                    g2.fillOval((int) (n.x - r - 5), (int) (n.y - r - 5), (int) (2 * r + 10), (int) (2 * r + 10));
                    // 本体
                    g2.setColor(n.color);
                    g2.fillOval((int) (n.x - r), (int) (n.y - r), (int) (2 * r), (int) (2 * r));
                    g2.setColor(new Color(255, 255, 255, 170));
                    g2.fillOval((int) (n.x - r * 0.35), (int) (n.y - r * 0.45), (int) r, (int) (r * 0.6));
                    drawLabel(g2, n.title, n.x, n.y + r + 13, false);
                }
                // 选中环
                if (n == selected) {
                    double r = n.hub ? 20 : radius(n) + 5;
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            0, new float[]{5, 4}, 0));
                    g2.drawOval((int) (n.x - r), (int) (n.y - r), (int) (2 * r), (int) (2 * r));
                    g2.setStroke(new BasicStroke(1f));
                }
            }

            // 图例
            drawLegend(g2, w);
            g2.dispose();
        }

        private void drawLabel(Graphics2D g2, String text, double x, double y, boolean bold) {
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(text);
            g2.setColor(bold ? Color.WHITE : new Color(219, 231, 250));
            g2.drawString(text, (int) (x - tw / 2.0), (int) y);
        }

        private void drawLegend(Graphics2D g2, int w) {
            Map<String, Color> cats = new LinkedHashMap<>();
            nodes.values().forEach(n -> {
                if (n.hub) cats.put(n.title, n.color);
            });
            if (cats.isEmpty()) return;
            g2.setFont(Ui.FONT_SMALL);
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int x = w - 14, y = 16;
            for (Map.Entry<String, Color> e : cats.entrySet()) {
                String label = e.getKey();
                int tw = fm.stringWidth(label) + 30;
                g2.setColor(new Color(15, 23, 42, 200));
                g2.fillRoundRect(x - tw, y - 11, tw, 20, 8, 8);
                g2.setColor(e.getValue());
                g2.fillOval(x - tw + 8, y - 7, 8, 8);
                g2.setColor(new Color(226, 232, 240));
                g2.drawString(label, x - tw + 20, y + 2);
                y += 24;
            }
            g2.setColor(new Color(148, 163, 184, 160));
            g2.setFont(Ui.FONT_SMALL);
            g2.drawString(Langs.t("starmap.legend"), 14, h());
        }

        private int h() {
            return getHeight() - 12;
        }
    }
}
