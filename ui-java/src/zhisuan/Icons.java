package zhisuan;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/** 代码绘制矢量小图标（零资源依赖） */
public final class Icons {
    private Icons() {}

    private abstract static class ShapeIcon implements Icon {
        private final Color color;

        ShapeIcon(Color color) { this.color = color; }

        @Override
        public int getIconWidth() { return 20; }

        @Override
        public int getIconHeight() { return 20; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(x, y);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.6f));
            paint(g2);
            g2.dispose();
        }

        abstract void paint(Graphics2D g);
    }

    /** 对话气泡 */
    public static Icon chat() {
        return new ShapeIcon(Color.WHITE) {
            void paint(Graphics2D g) {
                g.draw(new RoundRectangle2D.Double(2, 2, 16, 11, 4, 4));
                int[] x = {6, 6, 10}, y = {13, 17, 13};
                g.fillPolygon(x, y, 3);
                g.fill(new Ellipse2D.Double(6, 6.5, 2, 2));
                g.fill(new Ellipse2D.Double(9.5, 6.5, 2, 2));
                g.fill(new Ellipse2D.Double(13, 6.5, 2, 2));
            }
        };
    }

    /** 分子（两原子一键） */
    public static Icon optimize() {
        return new ShapeIcon(Color.WHITE) {
            void paint(Graphics2D g) {
                g.drawLine(7, 7, 14, 14);
                g.draw(new Ellipse2D.Double(3, 3, 7, 7));
                g.draw(new Ellipse2D.Double(11, 11, 7, 7));
            }
        };
    }

    /** 五角星（知识星图） */
    public static Icon star() {
        return new ShapeIcon(Color.WHITE) {
            void paint(Graphics2D g) {
                double cx = 10, cy = 10.5, r = 8, ri = r * 0.42;
                double[] pts = new double[20];
                for (int k = 0; k < 10; k++) {
                    double ang = -Math.PI / 2 + k * Math.PI / 5;
                    double rr = (k % 2 == 0) ? r : ri;
                    pts[2 * k] = cx + rr * Math.cos(ang);
                    pts[2 * k + 1] = cy + rr * Math.sin(ang);
                }
                int[] xs = new int[10], ys = new int[10];
                for (int k = 0; k < 10; k++) { xs[k] = (int) Math.round(pts[2 * k]); ys[k] = (int) Math.round(pts[2 * k + 1]); }
                g.drawPolygon(xs, ys, 10);
            }
        };
    }

    /** 谱图（递增柱） */
        public static Icon spectrum() {
            return new ShapeIcon(Color.WHITE) {
                void paint(Graphics2D g) {
                    g.drawLine(2, 17, 18, 17);
                    g.drawLine(4, 13, 4, 17);
                    g.drawLine(8, 9, 8, 17);
                    g.drawLine(12, 5, 12, 17);
                    g.drawOval(15, 2, 3, 3);
                    g.drawLine(14, 5, 14, 9);
                }
            };
        }

        /** 书本（知识库） */
        public static Icon book() {
            return new ShapeIcon(Color.WHITE) {
                void paint(Graphics2D g) {
                    g.draw(new RoundRectangle2D.Double(3, 3, 15, 14, 3, 3));
                    g.drawLine(10, 4, 10, 16);
                    g.drawLine(5, 7, 8, 7);
                    g.drawLine(12, 7, 15, 7);
                    g.drawLine(5, 10, 8, 10);
                    g.drawLine(12, 10, 15, 10);
                }
            };
        }

    /** 工作流（节点网络连线） */
    public static Icon workflow() {
        return new ShapeIcon(Color.WHITE) {
            void paint(Graphics2D g) {
                g.drawLine(4, 6, 16, 4);
                g.drawLine(4, 6, 10, 16);
                g.drawLine(4, 6, 15, 15);
                g.drawLine(16, 4, 15, 15);
                g.drawLine(10, 16, 15, 15);
                g.draw(new Ellipse2D.Double(2, 4, 4, 4));
                g.draw(new Ellipse2D.Double(14, 2, 4, 4));
                g.draw(new Ellipse2D.Double(8, 14, 4, 4));
                g.draw(new Ellipse2D.Double(13, 13, 4, 4));
            }
        };
    }

    /** 齿轮（设置） */
    public static Icon gear() {
        return new ShapeIcon(Color.WHITE) {
            void paint(Graphics2D g) {
                g.draw(new Ellipse2D.Double(4.5, 4.5, 11, 11));
                g.draw(new Ellipse2D.Double(8, 8, 4, 4));
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    int x1 = (int) Math.round(10 + 7.5 * Math.cos(a));
                    int y1 = (int) Math.round(10 + 7.5 * Math.sin(a));
                    int x2 = (int) Math.round(10 + 9.5 * Math.cos(a));
                    int y2 = (int) Math.round(10 + 9.5 * Math.sin(a));
                    g.drawLine(x1, y1, x2, y2);
                }
            }
        };
    }

    /** 计算任务（调度面板 + 进度条） */
    public static Icon jobs() {
        return new ShapeIcon(Color.WHITE) {
            void paint(Graphics2D g) {
                g.draw(new RoundRectangle2D.Double(2, 2, 16, 12, 3, 3));
                g.drawLine(2, 8, 18, 8);
                g.drawLine(5, 14, 18, 14);
                g.drawLine(7, 6, 11, 6);
                g.drawLine(7, 12, 13, 12);
                g.drawLine(6, 17, 18, 17);
            }
        };
    }

    /** 化合物库（烧瓶） */
    public static Icon library() {
        return new ShapeIcon(Color.WHITE) {
            void paint(Graphics2D g) {
                g.drawLine(9, 3, 9, 9);
                g.drawLine(9, 15, 9, 18);
                int[] x = {6, 6, 14, 14};
                int[] y = {8, 11, 15, 8};
                g.drawPolyline(x, y, 4);
            }
        };
    }

    /** 三维结构（球棍骨架旋转） */
    public static Icon molview() {
        return new ShapeIcon(Color.WHITE) {
            void paint(Graphics2D g) {
                g.drawLine(5, 5, 15, 15);
                g.draw(new Ellipse2D.Double(2, 2, 6, 6));
                g.draw(new Ellipse2D.Double(12, 12, 6, 6));
                g.draw(new Ellipse2D.Double(11, 3, 5, 5));
                g.drawLine(14, 6, 8, 14);
            }
        };
    }

    /** 记忆中心（星状回路） */
    public static Icon memory() {
        return new ShapeIcon(Color.WHITE) {
            void paint(Graphics2D g) {
                double cx = 10, cy = 10;
                for (int k = 0; k < 3; k++) {
                    double a = -Math.PI / 2 + k * 2 * Math.PI / 3;
                    g.draw(new Ellipse2D.Double(cx + 6 * Math.cos(a) - 2.2,
                            cy + 6 * Math.sin(a) - 2.2, 4.4, 4.4));
                }
                g.drawLine(7, 10, 10, 7);
                g.drawLine(10, 7, 13, 10);
                g.drawLine(13, 10, 10, 13);
                g.drawLine(10, 13, 7, 10);
            }
        };
    }

    /** 结果审查台（盾牌 + 对勾） */
    public static Icon audit() {
        return new ShapeIcon(new Color(60, 224, 255)) {
            void paint(Graphics2D g) {
                int[] xs = {10, 16, 16, 10, 4, 4};
                int[] ys = {1, 4, 12, 16, 12, 4};
                g.drawPolygon(xs, ys, 6);
                g.drawPolyline(new int[]{7, 10, 14}, new int[]{9, 12, 6}, 3);
            }
        };
    }

    /** 计算调度中心（发射塔网格） */
    public static Icon dispatch() {
        return new ShapeIcon(new Color(255, 194, 75)) {
            void paint(Graphics2D g) {
                g.drawLine(10, 3, 10, 10);
                g.drawLine(3, 10, 17, 10);
                g.drawLine(5, 6, 15, 6);
                g.drawLine(5, 14, 15, 14);
                g.drawLine(10, 10, 6, 17);
                g.drawLine(10, 10, 14, 17);
            }
        };
    }
}
