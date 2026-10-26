package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** 三维结构：简易球棍模型，拖拽旋转 / 缩放 / 拾取 / 线框 / 截图（对应 C++ crn3d） */
public class MolViewPage extends Page {
    private static final Color INK = new Color(4, 12, 24);

    private double yaw = -24, pitch = -14;
    private boolean wire;
    private final JLabel title = new JLabel();
    private final JLabel en = new JLabel();
    private final JButton bReset = Ui.secondary("");
    private final JButton bWire = Ui.secondary("");
    private final JButton bShot = Ui.secondary("");
    private final JLabel pickInfo = new JLabel();
    private final JLabel headCenter = new JLabel();
    private final JLabel headCoord = new JLabel();
    private final JLabel headCalc = new JLabel();
    private final Canvas3D canvas = new Canvas3D();

    public MolViewPage(ApiClient api) {
        super(api);
        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(2, 2, 2, 2));
        add(buildBar(), BorderLayout.NORTH);
        canvas.add(pickInfo, BorderLayout.SOUTH);
        add(canvas, BorderLayout.CENTER);
        add(buildCards(), BorderLayout.SOUTH);
        pickInfo.setForeground(Ui.MUTED);
        pickInfo.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pickInfo.setBorder(new EmptyBorder(4, 0, 4, 0));
        Langs.addListener(this::applyLang);
    }

    private void applyLang() {
        title.setText(Langs.t("mol.title"));
        title.setFont(Ui.FONT_TITLE);
        title.setForeground(Ui.TEXT);
        en.setText(Langs.t("mol.sub"));
        en.setFont(Ui.MONO);
        en.setForeground(Ui.FAINT);
        bReset.setText(Langs.t("mol.reset"));
        bWire.setText(Langs.t("mol.wire"));
        bShot.setText(Langs.t("mol.export"));
        if (pickInfo.getText() == null || pickInfo.getText().isEmpty()) pickInfo.setText(Langs.t("mol.pickInfo"));
        headCenter.setText(Langs.t("mol.infoCenter"));
        headCoord.setText(Langs.t("mol.infoCoord"));
        headCalc.setText(Langs.t("mol.infoCalc"));
    }

    private JPanel buildBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        JPanel t = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        t.setOpaque(false);
        t.add(title); t.add(en);
        bar.add(t, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        bReset.setPreferredSize(new Dimension(110, 30));
        bReset.addActionListener(e -> { yaw = 0; pitch = 0; canvas.repaint(); });
        bWire.setPreferredSize(new Dimension(110, 30));
        bWire.addActionListener(e -> { wire = !wire; canvas.repaint(); });
        bShot.setPreferredSize(new Dimension(110, 30));
        bShot.addActionListener(e -> pickInfo.setText(Langs.t("mol.exportMsg")));
        right.add(bReset); right.add(bWire); right.add(bShot);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildCards() {
        JPanel p = new JPanel(new GridLayout(1, 3, 10, 0));
        p.setOpaque(false);
        p.add(infoCard(headCenter, "Al", "配位 3 · 价态 A2 · 键长 1.751Å"));
        p.add(infoCard(headCoord, "XYZ", "Al 0.00 0.00 0.32<br>O  1.62 0.00 0.40"));
        p.add(infoCard(headCalc, "DFT", "ωB97X-D / def2-TZVP<br>E = -612.4 Hartree"));
        return p;
    }

    private JPanel infoCard(JLabel t, String tag, String body) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Ui.PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.LINE),
                new EmptyBorder(9, 12, 9, 12)));
        JPanel hd = new JPanel(new BorderLayout());
        hd.setOpaque(false);
        t.setFont(new Font("微软雅黑", Font.BOLD, 12));
        t.setForeground(Ui.SOFT);
        JLabel tg = new JLabel(tag);
        tg.setFont(Ui.MONO);
        tg.setForeground(Ui.FAINT);
        hd.add(t, BorderLayout.WEST); hd.add(tg, BorderLayout.EAST);
        JLabel b = new JLabel("<html><div style='font-family:Dialog;font-size:11px;color:#8497bd'>"
                + body + "</div></html>");
        p.add(hd); p.add(Box.createVerticalStrut(8)); p.add(b);
        return p;
    }

    /** 原子：{x,y,z, r} + 标签/颜色数组 */
    private static final double[][] ATOMS = {
            {250, 180, 0, 26}, {330, 120, 0, 15}, {330, 240, 0, 15},
            {395, 180, -12, 13}, {452, 180, 0, 15}};
    private static final String[] ATOMLABEL = {"Al", "O", "O", "C", "O"};
    private static final Color[] ATOMCOL = {
            new Color(255, 194, 75), new Color(255, 95, 110), new Color(255, 95, 110),
            new Color(60, 224, 255), new Color(255, 95, 110)};
    private static final int[][] BONDS = {{0, 1}, {0, 2}, {1, 3}, {2, 3}, {3, 4}};

    class Canvas3D extends JPanel {
        Canvas3D() {
            setLayout(new BorderLayout());
            setBackground(Ui.PANEL2);
            setBorder(BorderFactory.createLineBorder(Ui.LINE));
            setMinimumSize(new Dimension(0, 300));
            setPreferredSize(new Dimension(0, 320));
            MouseAdapter ma = new MouseAdapter() {
                private int lx, ly;
                public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
                public void mouseDragged(MouseEvent e) {
                    yaw += (e.getX() - lx) * 0.6; pitch += (e.getY() - ly) * 0.6; lx = e.getX(); ly = e.getY();
                    repaint();
                }
                public void mouseClicked(MouseEvent e) {
                    int near = -1; double best = 1e18;
                    for (int i = 0; i < ATOMS.length; i++) {
                        double[] p = project(i);
                        double d = Math.hypot(p[0] - e.getX(), p[1] - e.getY());
                        if (d < ATOMS[i][3] + 8 && d < best) { best = d; near = i; }
                    }
                    pickInfo.setText(near >= 0 ? Langs.t("mol.picked", ATOMLABEL[near]) : Langs.t("mol.clickHint"));
                }
                public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                    double s = e.getPreciseWheelRotation() > 0 ? 0.94 : 1.06;
                    for (int i = 0; i < ATOMS.length; i++) ATOMS[i][3] = Math.max(6, ATOMS[i][3] * s);
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);
        }

        double[] project(int i) {
            double x = ATOMS[i][0] - 340, y = ATOMS[i][1] - 180, z = ATOMS[i][2];
            double a = Math.toRadians(yaw), cy = Math.cos(a), sy = Math.sin(a);
            double x1 = x * cy - z * sy, z1 = x * sy + z * cy;
            a = Math.toRadians(pitch); double cx = Math.cos(a), sx = Math.sin(a);
            double y1 = y * cx - z1 * sx;
            double cx0 = getWidth() / 2.0, cy0 = getHeight() / 2.0 + 8;
            return new double[]{cx0 + x1, cy0 + y1, y1};
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 键
            g2.setStroke(new BasicStroke(3.5f));
            for (int[] b : BONDS) {
                double[] p1 = project(b[0]), p2 = project(b[1]);
                g2.setColor(wire ? Ui.ACCENT : new Color(120, 150, 210, 160));
                g2.drawLine((int) p1[0], (int) p1[1], (int) p2[0], (int) p2[1]);
            }
            // 原子（远→近）
            Integer[] idx = new Integer[ATOMS.length];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            java.util.Arrays.sort(idx, (a, b) -> Double.compare(project(b)[2], project(a)[2]));
            for (int i : idx) {
                double[] p = project(i);
                int rad = (int) ATOMS[i][3];
                int x = (int) Math.round(p[0]), y = (int) Math.round(p[1]);
                g2.setColor(ATOMCOL[i].darker().darker());
                g2.fillOval(x - rad + 2, y - rad + 2, rad * 2, rad * 2);
                g2.setColor(ATOMCOL[i]);
                g2.fillOval(x - rad, y - rad, rad * 2, rad * 2);
                g2.setColor(new Color(255, 255, 255, 200));
                g2.fillOval(x - rad / 3, y - rad / 3 - 2, rad * 2 / 3, rad * 2 / 3);
                g2.setFont(new Font("微软雅黑", Font.BOLD, rad > 20 ? 12 : 10));
                int w = g2.getFontMetrics().stringWidth(ATOMLABEL[i]);
                g2.setColor(INK);
                g2.drawString(ATOMLABEL[i], x - w / 2, y + (rad > 20 ? 13 : 10));
            }
            g2.dispose();
        }
    }

    @Override public void onShow() { applyLang(); }
}