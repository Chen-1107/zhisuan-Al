package com.zhisuan.workflow.ui;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 球棍结构渲染器：输入原子(symbol/x/y/z)与键明细，Java2D 正交投影绘制，
 * 支持左键拖拽旋转、滚轮缩放。空态/错误态显示占位文案。
 */
public class MolRenderPanel extends JPanel {

    private List<String> symbols = new ArrayList<>();
    private List<double[]> coords = new ArrayList<>();
    private List<int[]> bonds = new ArrayList<>();
    private String message = "（未选择任务）";

    private double yaw = 0.7;    // 绕 Y 轴
    private double pitch = 0.25; // 绕 X 轴
    private double zoom = 1.0;

    public MolRenderPanel() {
        setBackground(Theme.PANEL);
        setOpaque(true);
        MouseAdapter m = new MouseAdapter() {
            private int lx, ly;
            private boolean dragging;

            @Override
            public void mousePressed(MouseEvent e) {
                lx = e.getX();
                ly = e.getY();
                dragging = true;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragging) {
                    return;
                }
                yaw += (e.getX() - lx) * 0.01;
                pitch += (e.getY() - ly) * 0.01;
                lx = e.getX();
                ly = e.getY();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(this::onWheel);
    }

    private void onWheel(MouseWheelEvent e) {
        double f = e.getPreciseWheelRotation() < 0 ? 1.12 : 1 / 1.12;
        zoom = Math.max(0.2, Math.min(6.0, zoom * f));
        repaint();
    }

    /** 设置待渲染结构；symbols/coords 长度须一致。 */
    public void setStructure(List<String> s, List<double[]> c, List<int[]> b) {
        this.symbols = s == null ? new ArrayList<>() : new ArrayList<>(s);
        this.coords = c == null ? new ArrayList<>() : new ArrayList<>(c);
        this.bonds = b == null ? new ArrayList<>() : new ArrayList<>(b);
        this.message = "";
        repaint();
    }

    /** 空态 / 错误态占位文案。 */
    public void setMessage(String msg) {
        this.message = msg == null ? "" : msg;
        symbols.clear();
        coords.clear();
        bonds.clear();
        repaint();
    }

    public void clear() {
        setMessage("（未选择任务）");
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        g2.setColor(Theme.PANEL);
        g2.fillRect(0, 0, w, h);

        if (coords.isEmpty() || symbols.size() != coords.size()) {
            FontMetrics fm = g2.getFontMetrics(Theme.uiFont(12));
            if (message == null || message.isEmpty()) {
                message = "（无结构数据）";
            }
            g2.setColor(Theme.TEXT_SECONDARY);
            drawWrapped(g2, message, w, h, fm);
            g2.dispose();
            return;
        }

        int n = coords.size();
        double[] cx = new double[n], cy = new double[n], cz = new double[n];
        double cmx = 0, cmy = 0, cmz = 0;
        double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        double cosP = Math.cos(pitch), sinP = Math.sin(pitch);
        for (int i = 0; i < n; i++) {
            double[] p = coords.get(i);
            double x = p[0], y = p[1], z = p[2];
            // yaw then pitch
            double x1 = x * cosY + z * sinY;
            double z1 = -x * sinY + z * cosY;
            double y2 = y * cosP - z1 * sinP;
            double z2 = y * sinP + z1 * cosP;
            cx[i] = x1;
            cy[i] = y2;
            cz[i] = z2;
            cmx += x1;
            cmy += y2;
            cmz += z2;
        }
        cmx /= n;
        cmy /= n;
        cmz /= n;

        double maxR = 1.0;
        for (int i = 0; i < n; i++) {
            double d2 = (cx[i] - cmx) * (cx[i] - cmx) + (cy[i] - cmy) * (cy[i] - cmy)
                    + (cz[i] - cmz) * (cz[i] - cmz);
            maxR = Math.max(maxR, Math.sqrt(d2));
        }
        double scaleBase = Math.min(w, h) / (2.2 * maxR) * zoom;

        // 按深度排序，远处先画
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        final double[] fcz = cz;
        List<Integer> list = new ArrayList<>();
        Collections.addAll(list, order);
        list.sort(Comparator.comparingDouble(i -> fcz[i]));
        int[] sorted = new int[n];
        for (int i = 0; i < n; i++) {
            sorted[i] = list.get(i);
        }

        double ox = w / 2.0 - cmx * scaleBase;
        double oy = h / 2.0 + cmy * scaleBase;

        // 键
        for (int[] b : bonds) {
            if (b.length < 2) {
                continue;
            }
            int i = b[0], j = b[1];
            double sx = ox + cx[i] * scaleBase;
            double sy = oy - cy[i] * scaleBase;
            double tx = ox + cx[j] * scaleBase;
            double ty = oy - cy[j] * scaleBase;
            double depth = (fcz[i] + fcz[j]) / 2.0 / maxR;
            int lum = (int) (255 - depth * 90);
            int comp = (int) (240 - depth * 90);
            g2.setColor(new Color(lum, comp, 235));
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawLine((int) sx, (int) sy, (int) tx, (int) ty);
        }

        // 原子（近->远并填充前；此处按 sorted 从最远到最近画，保证近者覆盖）
        for (int k = n - 1; k >= 0; k--) {
            int i = sorted[k];
            double sx = ox + cx[i] * scaleBase;
            double sy = oy - cy[i] * scaleBase;
            double depth = fcz[i] / maxR;
            double radiusPx = Math.max(2.5, covRadius(symbols.get(i)) * 0.5 * scaleBase);
            // 深度：远处的球略小、偏暗
            double df = 0.75 + 0.25 * (0.5 - depth / 2.0 + 0.5);
            radiusPx *= Math.max(0.62, Math.min(1.0, df));
            Color base = elementColor(symbols.get(i));
            g2.setColor(base);
            g2.fillOval((int) (sx - radiusPx), (int) (sy - radiusPx),
                    (int) (radiusPx * 2), (int) (radiusPx * 2));
            g2.setColor(Theme.alpha(Color.WHITE, 60));
            g2.drawOval((int) (sx - radiusPx), (int) (sy - radiusPx),
                    (int) (radiusPx * 2), (int) (radiusPx * 2));
        }

        // 顶部叠加原子符号（浅色），仅当球足够大
        for (int k = n - 1; k >= 0; k--) {
            int i = sorted[k];
            double sx = ox + cx[i] * scaleBase;
            double sy = oy - cy[i] * scaleBase;
            double radiusPx = Math.max(2.5, covRadius(symbols.get(i)) * 0.5 * scaleBase);
            double df = 0.75 + 0.25 * (0.5 - fcz[i] / maxR / 2.0 + 0.5);
            radiusPx *= Math.max(0.62, Math.min(1.0, df));
            if (radiusPx >= 5.5) {
                g2.setFont(Theme.uiFont(Math.max(8, (int) Math.round(radiusPx * 0.9))));
                FontMetrics fm = g2.getFontMetrics();
                String sym = symbols.get(i);
                g2.setColor(Theme.TEXT_PRIMARY);
                g2.drawString(sym, (int) (sx - fm.stringWidth(sym) / 2.0),
                        (int) (sy + fm.getAscent() / 2.0 - 1));
            }
        }

        // 角落提示 + 原子数
        g2.setFont(Theme.uiFont(10));
        g2.setColor(Theme.TEXT_SECONDARY);
        g2.drawString("原子 " + n + " · 左键拖拽旋转 · 滚轮缩放", 8, h - 8);

        g2.dispose();
    }

    private void drawWrapped(Graphics2D g2, String text, int w, int h, FontMetrics fm) {
        String[] lines = text.split("\n");
        g2.setFont(Theme.uiFont(12));
        int y = h / 2;
        for (String ln : lines) {
            y += fm.getHeight();
            int x = (w - fm.stringWidth(ln)) / 2;
            g2.drawString(ln, x, y - fm.getHeight());
        }
    }

    private static double covRadius(String sym) {
        if (sym == null) {
            return 1.0;
        }
        switch (sym) {
            case "H": return 0.37;
            case "C": return 0.77;
            case "N": return 0.75;
            case "O": return 0.73;
            case "F": return 0.71;
            case "P": return 1.10;
            case "S": return 1.03;
            case "Cl": return 0.99;
            case "Se": return 1.20;
            case "Te": return 1.38;
            case "Al": return 1.18;
            case "Si": return 1.11;
            case "Br": return 1.14;
            default: return 1.0;
        }
    }

    private static Color elementColor(String sym) {
        if (sym == null) {
            return new Color(0x9FB6C8);
        }
        switch (sym) {
            case "C": return new Color(0x909090);
            case "H": return new Color(0xE6E6E6);
            case "N": return new Color(0x3050F8);
            case "O": return new Color(0xFF0D0D);
            case "F": return new Color(0x90E050);
            case "P": return new Color(0xFF8000);
            case "S": return new Color(0xFFFF30);
            case "Cl": return new Color(0x1FF01F);
            case "Se": return new Color(0xE5A000);
            case "Te": return new Color(0xD39B7A);
            case "Al": return new Color(0xBFA6A6);
            case "Si": return new Color(0xA0A0A0);
            case "Br": return new Color(0xA62929);
            default: return new Color(0x9FB6C8);
        }
    }
}