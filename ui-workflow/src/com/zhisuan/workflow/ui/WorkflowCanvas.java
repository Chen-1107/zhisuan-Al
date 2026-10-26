package com.zhisuan.workflow.ui;

import com.zhisuan.workflow.model.NodeStatus;
import com.zhisuan.workflow.model.WorkflowEdge;
import com.zhisuan.workflow.model.WorkflowGraph;
import com.zhisuan.workflow.model.WorkflowNode;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;
import java.util.function.Consumer;

/** N8N 风格画布：Java2D 渲染节点/连线，支持缩放、平移与点击选中。 */
public class WorkflowCanvas extends JPanel {

    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 2.0;
    private static final double GRID_MINOR = 24;
    private static final double GRID_MAJOR = 120;
    private static final double NODE_RADIUS = 10;
    /** 节点大小调节的整体下界 / 上界（世界坐标宽度、高度）。 */
    private static final double MIN_NODE_W = 140;
    private static final double MIN_NODE_H = 64;
    private static final double MAX_NODE_W = 720;
    private static final double MAX_NODE_H = 460;

    private final WorkflowGraph graph;
    private double zoom = 1.0;
    private double offsetX = 0;
    private double offsetY = 0;
    private String selectedNodeId;
    private Consumer<String> selectionListener;
    private Runnable zoomListener;
    private Runnable graphChangedListener;
    private boolean fitPending = true;
    private Point dragStart;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean dragged;
    private WorkflowNode dragNode;
    private double dragNodeStartX;
    private double dragNodeStartY;
    /** 节点右下角大小调节状态。 */
    private boolean resizing;
    private WorkflowNode resizeNode;

    public WorkflowCanvas(WorkflowGraph graph) {
        this.graph = graph;
        setOpaque(true);
        setBackground(Theme.BACKGROUND);
        setFocusable(true);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                dragOffsetX = offsetX;
                dragOffsetY = offsetY;
                dragged = false;
                resizing = false;
                resizeNode = null;
                dragNode = null;
                WorkflowNode handle = hitResizeHandle(e);
                if (handle != null) {
                    // 命中右下角调节把手：进入大小调节（左上角锚定）
                    resizing = true;
                    resizeNode = handle;
                    return;
                }
                WorkflowNode hit = hitNode(e);
                if (hit != null) {
                    // 命中节点：记录为待拖动节点，并选中
                    dragNode = hit;
                    dragNodeStartX = hit.getX();
                    dragNodeStartY = hit.getY();
                    setSelectedNode(hit.getId());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null) {
                    return;
                }
                if (resizing && resizeNode != null) {
                    Point2D.Double w = screenToWorld(e.getX(), e.getY());
                    double nw = clamp(w.x - resizeNode.getX(), MIN_NODE_W, MAX_NODE_W);
                    double nh = clamp(w.y - resizeNode.getY(), MIN_NODE_H, MAX_NODE_H);
                    resizeNode.setWidth(nw);
                    resizeNode.setHeight(nh);
                    if (graphChangedListener != null) {
                        graphChangedListener.run();
                    }
                    repaint();
                    return;
                }
                double dx = e.getX() - dragStart.x;
                double dy = e.getY() - dragStart.y;
                if (Math.abs(dx) + Math.abs(dy) > 3) {
                    dragged = true;
                }
                if (dragNode != null) {
                    // 节点拖动：将屏幕位移折算回世界坐标
                    double wx = dragNodeStartX + dx / zoom;
                    double wy = dragNodeStartY + dy / zoom;
                    dragNode.setX(wx);
                    dragNode.setY(wy);
                    // 拖动改变布局时同步到监听者（工作栏统计等）
                    if (graphChangedListener != null) {
                        graphChangedListener.run();
                    }
                } else {
                    // 空白处拖动：平移画布
                    offsetX = dragOffsetX + dx;
                    offsetY = dragOffsetY + dy;
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragNode = null;
                resizing = false;
                resizeNode = null;
                if (!dragged && dragStart != null) {
                    if (hitNode(e) == null) {
                        setSelectedNode(null);
                    }
                }
                dragStart = null;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (hitResizeHandle(e) != null) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                } else {
                    WorkflowNode hit = hitNode(e);
                    setCursor(hit != null ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                            : Cursor.getDefaultCursor());
                }
                setToolTipText(tooltipFor(hitNode(e)));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(this::onMouseWheel);
    }

    public void setSelectionListener(Consumer<String> listener) {
        this.selectionListener = listener;
    }

    public void setZoomListener(Runnable listener) {
        this.zoomListener = listener;
    }

    /** 布局变更（节点被拖动）时回调，供工作栏/详情面板同步。 */
    public void setGraphChangedListener(Runnable listener) {
        this.graphChangedListener = listener;
    }

    /** 将节点按 id 批量应用远程状态（status + 状态信息），并触发重绘。 */
    public void applyRemoteStates(Map<String, NodeRemoteState> states) {
        for (Map.Entry<String, NodeRemoteState> en : states.entrySet()) {
            WorkflowNode n = graph.getNode(en.getKey());
            if (n == null) {
                continue;
            }
            NodeRemoteState st = en.getValue();
            n.setStatus(st.status);
            if (st.message != null && !st.message.isEmpty()) {
                n.setStatusMessage(st.message);
            }
        }
        repaint();
        if (graphChangedListener != null) {
            graphChangedListener.run();
        }
    }

    public WorkflowNode getSelectedNode() {
        return selectedNodeId == null ? null : graph.getNode(selectedNodeId);
    }

    public void setSelectedNode(String id) {
        selectedNodeId = id;
        if (selectionListener != null) {
            selectionListener.accept(id);
        }
        repaint();
    }

    public double getZoom() {
        return zoom;
    }

    public void zoomIn() {
        setZoomAround(zoom * 1.2, getWidth() / 2.0, getHeight() / 2.0);
    }

    public void zoomOut() {
        setZoomAround(zoom / 1.2, getWidth() / 2.0, getHeight() / 2.0);
    }

    public void resetZoom() {
        setZoomAround(1.0, getWidth() / 2.0, getHeight() / 2.0);
    }

    public void fitView() {
        if (graph.getNodes().isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (WorkflowNode n : graph.getNodes()) {
            minX = Math.min(minX, n.getX());
            minY = Math.min(minY, n.getY());
            maxX = Math.max(maxX, n.getX() + n.getWidth());
            maxY = Math.max(maxY, n.getY() + n.getHeight());
        }
        double pad = 60;
        double w = Math.max(1, maxX - minX + pad * 2);
        double h = Math.max(1, maxY - minY + pad * 2);
        double z = Math.min(getWidth() / w, getHeight() / h);
        zoom = clamp(z, 0.55, 1.15);
        offsetX = (getWidth() - (minX + maxX) * zoom) / 2.0;
        offsetY = (getHeight() - (minY + maxY) * zoom) / 2.0;
        repaint();
        notifyZoom();
    }

    private void onMouseWheel(MouseWheelEvent e) {
        double factor = e.getPreciseWheelRotation() < 0 ? 1.1 : 1 / 1.1;
        setZoomAround(zoom * factor, e.getX(), e.getY());
    }

    private void setZoomAround(double newZoom, double screenX, double screenY) {
        double z = clamp(newZoom, MIN_ZOOM, MAX_ZOOM);
        Point2D.Double world = screenToWorld(screenX, screenY);
        zoom = z;
        offsetX = screenX - world.x * zoom;
        offsetY = screenY - world.y * zoom;
        repaint();
        notifyZoom();
    }

    private void notifyZoom() {
        if (zoomListener != null) {
            zoomListener.run();
        }
    }

    private WorkflowNode hitNode(MouseEvent e) {
        Point2D.Double w = screenToWorld(e.getX(), e.getY());
        for (WorkflowNode n : graph.getNodes()) {
            if (n.contains(w.x, w.y)) {
                return n;
            }
        }
        return null;
    }

    /** 命中选中节点右下角的调节把手（屏幕坐标热区），命中返回该节点否则 null。 */
    private WorkflowNode hitResizeHandle(MouseEvent e) {
        if (selectedNodeId == null) {
            return null;
        }
        WorkflowNode n = graph.getNode(selectedNodeId);
        if (n == null) {
            return null;
        }
        int handleScreen = 20; // 屏幕空间热区半宽；节点缩放/平移时仍保持可点
        double bx = worldToScreenX(n.getX() + n.getWidth());
        double by = worldToScreenY(n.getY() + n.getHeight());
        double ex = e.getX(), ey = e.getY();
        if (ex >= bx - handleScreen && ex <= bx + 2 && ey >= by - handleScreen && ey <= by + 2) {
            return n;
        }
        return null;
    }

    private String tooltipFor(WorkflowNode n) {
        if (n == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<html><b>").append(n.getLabel()).append("</b><br>")
                .append("类型：").append(n.getType().getLabel()).append("<br>")
                .append("状态：").append(n.getStatus().getLabel());
        if (n.getStatusMessage() != null && !n.getStatusMessage().isEmpty()) {
            sb.append("<br>详情：").append(n.getStatusMessage());
        }
        if (n.getDescription() != null && !n.getDescription().isEmpty()) {
            sb.append("<br><br>").append(n.getDescription());
        }
        sb.append("</html>");
        return sb.toString();
    }

    private double screenToWorldX(double sx) {
        return (sx - offsetX) / zoom;
    }

    private double screenToWorldY(double sy) {
        return (sy - offsetY) / zoom;
    }

    private Point2D.Double screenToWorld(double sx, double sy) {
        return new Point2D.Double(screenToWorldX(sx), screenToWorldY(sy));
    }

    private double worldToScreenX(double wx) {
        return wx * zoom + offsetX;
    }

    private double worldToScreenY(double wy) {
        return wy * zoom + offsetY;
    }

    private int fHeader() {
        return Math.max(11, (int) Math.round(11 * zoom));
    }

    private int fName() {
        return Math.max(13, (int) Math.round(15 * zoom));
    }

    private int fMsg() {
        return Math.max(12, (int) Math.round(12 * zoom));
    }

    private int fPill() {
        return Math.max(10, (int) Math.round(10 * zoom));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (fitPending && getWidth() > 0 && getHeight() > 0) {
            fitPending = false;
            fitView();
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paintGrid(g2);
        for (WorkflowEdge e : graph.getEdges()) {
            paintEdge(g2, e);
        }
        for (WorkflowNode n : graph.getNodes()) {
            paintNode(g2, n);
        }
        g2.dispose();
    }

    /** n8n 风格点阵网格：小间距暗点 + 大间距亮点。 */
    private void paintGrid(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        double wx0 = screenToWorldX(0);
        double wx1 = screenToWorldX(w);
        double wy0 = screenToWorldY(0);
        double wy1 = screenToWorldY(h);

        if (GRID_MINOR * zoom >= 5) {
            g2.setColor(Theme.GRID);
            double startX = Math.floor(wx0 / GRID_MINOR) * GRID_MINOR;
            double startY = Math.floor(wy0 / GRID_MINOR) * GRID_MINOR;
            for (double x = startX; x <= wx1; x += GRID_MINOR) {
                int sx = (int) Math.round(worldToScreenX(x)) - 1;
                for (double y = startY; y <= wy1; y += GRID_MINOR) {
                    g2.fillRect(sx, (int) Math.round(worldToScreenY(y)) - 1, 2, 2);
                }
            }
        }

        g2.setColor(Theme.GRID_MAJOR);
        double startX = Math.floor(wx0 / GRID_MAJOR) * GRID_MAJOR;
        double startY = Math.floor(wy0 / GRID_MAJOR) * GRID_MAJOR;
        for (double x = startX; x <= wx1; x += GRID_MAJOR) {
            int sx = (int) Math.round(worldToScreenX(x)) - 1;
            for (double y = startY; y <= wy1; y += GRID_MAJOR) {
                g2.fillRect(sx, (int) Math.round(worldToScreenY(y)) - 1, 3, 3);
            }
        }
    }

    private void paintEdge(Graphics2D g2, WorkflowEdge edge) {
        WorkflowNode src = graph.getNode(edge.getSourceId());
        WorkflowNode tgt = graph.getNode(edge.getTargetId());
        if (src == null || tgt == null) {
            return;
        }

        WorkflowEdge.EdgeAnchor sa = edge.getSourceAnchor();
        WorkflowEdge.EdgeAnchor ta = edge.getTargetAnchor();
        boolean explicit = sa != WorkflowEdge.EdgeAnchor.AUTO || ta != WorkflowEdge.EdgeAnchor.AUTO;

        Point2D.Double p0;
        Point2D.Double p3;
        Point2D.Double c1;
        Point2D.Double c2;

        if (sa == WorkflowEdge.EdgeAnchor.LEFT && ta == WorkflowEdge.EdgeAnchor.BOTTOM) {
            // RERUN 回环：自源左侧端口出发，在节点下方大弧绕过主流程行后从目标底部端口进入。
            // 弧深取源-目标跨距的 40%，保证弧线落在第二行节点上方、不与谱分析分支交叉。
            p0 = anchorPoint(src, WorkflowEdge.EdgeAnchor.LEFT);
            p3 = anchorPoint(tgt, WorkflowEdge.EdgeAnchor.BOTTOM);
            double dx = p3.x - p0.x;
            double dip = clamp(Math.abs(dx) * 0.4 + 40, 70, 150);
            double cy = Math.max(p0.y, p3.y) + dip;
            c1 = new Point2D.Double(p0.x + dx * 0.35, cy);
            c2 = new Point2D.Double(p3.x - dx * 0.35, cy);
        } else if (explicit) {
            p0 = anchorPoint(src, sa == WorkflowEdge.EdgeAnchor.AUTO ? WorkflowEdge.EdgeAnchor.RIGHT : sa);
            p3 = anchorPoint(tgt, ta == WorkflowEdge.EdgeAnchor.AUTO ? WorkflowEdge.EdgeAnchor.LEFT : ta);
            double spread = clamp(Math.abs(p3.y - p0.y) * 0.5 + 40, 40, 140);
            if (p3.y >= p0.y) {
                c1 = new Point2D.Double(p0.x, p0.y + spread);
                c2 = new Point2D.Double(p3.x, p3.y - spread);
            } else {
                c1 = new Point2D.Double(p0.x, p0.y + spread);
                c2 = new Point2D.Double(p3.x, p3.y + spread);
            }
        } else {
            p0 = anchorPoint(src, WorkflowEdge.EdgeAnchor.RIGHT);
            p3 = anchorPoint(tgt, WorkflowEdge.EdgeAnchor.LEFT);
            boolean backward = tgt.getX() + tgt.getWidth() / 2 < src.getX() + src.getWidth() / 2;
            if (backward) {
                double dip = Math.max(60, 90 * zoom);
                c1 = new Point2D.Double(p0.x + 50 * zoom, p0.y + dip);
                c2 = new Point2D.Double(p3.x - 50 * zoom, p3.y + dip);
            } else {
                double spread = clamp((p3.x - p0.x) * 0.5, 40, 160);
                c1 = new Point2D.Double(p0.x + spread, p0.y);
                c2 = new Point2D.Double(p3.x - spread, p3.y);
            }
        }

        CubicCurve2D.Double curve = new CubicCurve2D.Double(
                p0.x, p0.y, c1.x, c1.y, c2.x, c2.y, p3.x, p3.y);

        if (edge.isActive()) {
            g2.setColor(Theme.alpha(Theme.ACCENT, 200));
            g2.setStroke(new BasicStroke(2f));
        } else {
            g2.setColor(Theme.alpha(new Color(0x64748B), 150));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                    10f, new float[]{6f, 4f}, 0f));
        }
        g2.draw(curve);

        double dx = p3.x - c2.x;
        double dy = p3.y - c2.y;
        double len = Math.max(1, Math.hypot(dx, dy));
        double ux = dx / len;
        double uy = dy / len;
        double arrowSize = 8;
        Path2D path = new Path2D.Double();
        path.moveTo(p3.x, p3.y);
        path.lineTo(p3.x - ux * arrowSize - uy * arrowSize * 0.5, p3.y - uy * arrowSize + ux * arrowSize * 0.5);
        path.lineTo(p3.x - ux * arrowSize + uy * arrowSize * 0.5, p3.y - uy * arrowSize - ux * arrowSize * 0.5);
        path.closePath();
        g2.setColor(edge.isActive() ? Theme.ACCENT : new Color(0x64748B));
        g2.fill(path);

        if (edge.getLabel() != null && !edge.getLabel().isEmpty()) {
            double mx = (p0.x + 3 * c1.x + 3 * c2.x + p3.x) / 8;
            double my = (p0.y + 3 * c1.y + 3 * c2.y + p3.y) / 8;
            paintEdgeLabel(g2, edge.getLabel(), edge.isActive(), mx, my);
        }
    }

    private Point2D.Double anchorPoint(WorkflowNode n, WorkflowEdge.EdgeAnchor a) {
        double sx = worldToScreenX(n.getX());
        double sy = worldToScreenY(n.getY());
        double w = n.getWidth() * zoom;
        double h = n.getHeight() * zoom;
        switch (a) {
            case LEFT:
                return new Point2D.Double(sx, sy + h / 2);
            case TOP:
                return new Point2D.Double(sx + w / 2, sy);
            case BOTTOM:
                return new Point2D.Double(sx + w / 2, sy + h);
            case RIGHT:
            default:
                return new Point2D.Double(sx + w, sy + h / 2);
        }
    }

    private void paintEdgeLabel(Graphics2D g2, String text, boolean active, double x, double y) {
        int fs = Math.max(11, (int) Math.round(13 * zoom));
        Font font = Theme.uiFont(fs);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);
        int padX = 6;
        int padY = 3;
        int w = tw + padX * 2;
        int h = fm.getHeight() + padY;
        int lx = (int) Math.round(x - w / 2.0);
        int ly = (int) Math.round(y - h / 2.0);

        Color bg = active ? new Color(0x14304F) : new Color(0x202C42);
        Color fg = active ? new Color(0xBFE3FF) : new Color(0xAAB9D1);
        g2.setColor(bg);
        g2.fill(new RoundRectangle2D.Double(lx, ly, w, h, 8, 8));
        g2.setColor(active ? Theme.alpha(Theme.ACCENT, 170) : Theme.alpha(new Color(0x64748B), 150));
        g2.draw(new RoundRectangle2D.Double(lx, ly, w, h, 8, 8));
        g2.setColor(fg);
        g2.drawString(text, lx + padX, ly + padY + fm.getAscent());
    }

    /** n8n 风格节点卡片：彩色头部条(类型) + 名称 + 状态信息 + 右下状态徽标。 */
    private void paintNode(Graphics2D g2, WorkflowNode n) {
        double sx = worldToScreenX(n.getX());
        double sy = worldToScreenY(n.getY());
        int w = (int) Math.round(n.getWidth() * zoom);
        int h = (int) Math.round(n.getHeight() * zoom);
        if (w < 16 || h < 10) {
            return;
        }
        double radius = NODE_RADIUS * zoom;

        boolean selected = n.getId().equals(selectedNodeId);
        NodeStatus status = n.getStatus();
        Color statusColor = Theme.statusColor(status);
        Color typeColor = Theme.typeColor(n.getType());

        if (selected) {
            g2.setColor(Theme.alpha(Theme.ACCENT, 55));
            g2.fill(new RoundRectangle2D.Double(sx - 3, sy - 3, w + 6, h + 6, radius + 3, radius + 3));
        }

        RoundRectangle2D.Double body = new RoundRectangle2D.Double(sx, sy, w, h, radius, radius);
        g2.setColor(Theme.NODE_BODY);
        g2.fill(body);

        int headerH = Math.max(12, (int) Math.round(h * 0.30));
        Shape oldClip = g2.getClip();
        g2.clip(body);
        g2.setColor(Theme.blend(typeColor, new Color(0x0A0F1A), 0.45));
        g2.fillRect((int) sx, (int) sy, w, headerH);
        g2.setClip(oldClip);

        int padX = Math.max(6, (int) Math.round(10 * zoom));

        g2.setFont(Theme.uiFontBold(fHeader()));
        g2.setColor(new Color(0xF2F8FF));
        String typeText = n.getType().getLabel();
        g2.drawString(ellipsize(g2, typeText, g2.getFont(), w - padX * 2),
                (int) sx + padX, (int) (sy + headerH) - Math.max(3, (int) Math.round(4 * zoom)));

        int dotR = Math.max(3, (int) Math.round(4 * zoom));
        int dotCx = (int) Math.round(sx + w - 10 * zoom);
        int dotCy = (int) Math.round(sy + headerH / 2.0);
        g2.setColor(statusColor);
        g2.fill(new Ellipse2D.Double(dotCx - dotR, dotCy - dotR, dotR * 2, dotR * 2));
        g2.setColor(Theme.alpha(Color.WHITE, 150));
        g2.draw(new Ellipse2D.Double(dotCx - dotR, dotCy - dotR, dotR * 2, dotR * 2));

        Color border = Theme.BORDER;
        float borderW = 1f;
        if (status == NodeStatus.RUNNING) {
            border = statusColor;
            borderW = 1.5f;
        } else if (selected) {
            border = Theme.ACCENT;
            borderW = 2f;
        } else if (status == NodeStatus.COMPLETED || status == NodeStatus.RERUN
                || status == NodeStatus.FAILED) {
            border = Theme.alpha(statusColor, 200);
        }
        g2.setColor(border);
        g2.setStroke(new BasicStroke(borderW));
        g2.draw(body);

        // 名称行仅在区别于头部类型条文本时绘制，避免标题重复（如“报告生成/报告生成”）
        String typeLabel = n.getType().getLabel();
        boolean nameDuplicatesType = n.getLabel().equals(typeLabel);
        if (!nameDuplicatesType) {
            g2.setFont(Theme.uiFontBold(fName()));
            g2.setColor(Theme.TEXT_PRIMARY);
            int nameY = (int) Math.round(sy + h * 0.50);
            g2.drawString(ellipsize(g2, n.getLabel(), g2.getFont(), w - padX * 2),
                    (int) sx + padX, nameY);
        }

        String msg = n.getStatusMessage();
        if (msg != null && !msg.isEmpty() && h >= 40) {
            g2.setFont(Theme.uiFont(fMsg()));
            g2.setColor(Theme.TEXT_SECONDARY);
            // 无名称行时将状态信息居中，避免卡片内容偏下
            int msgY = nameDuplicatesType
                    ? (int) Math.round(sy + h * 0.54)
                    : (int) Math.round(sy + h * 0.66);
            g2.drawString(ellipsize(g2, msg, g2.getFont(), w - padX * 2),
                    (int) sx + padX, msgY);
        }

        if (h >= 54) {
            paintStatusPill(g2, n, statusColor, sx, sy, w, h);
        }

        paintPort(g2, sx, sy + h / 2.0);
        paintPort(g2, sx + w, sy + h / 2.0);

        if (selected) {
            paintResizeHandle(g2, sx + w, sy + h);
        }
    }

    /** 选中节点的右下角大小调节把手：带斜纹的小方块，拖拽可改变节点宽高。 */
    private void paintResizeHandle(Graphics2D g2, double bx, double by) {
        int size = 14;
        double hx = bx - size + 3;
        double hy = by - size + 3;
        g2.setColor(Theme.alpha(Theme.ACCENT, 90));
        g2.fill(new RoundRectangle2D.Double(hx, hy, size, size, 4, 4));
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(Theme.ACCENT);
        g2.drawLine((int) (hx + size - 6), (int) (hy + 3),
                (int) (hx + 3), (int) (hy + size - 6));
        g2.drawLine((int) (hx + size - 6), (int) (hy + size - 6),
                (int) (hx + 3.5), (int) (hy + 3.5));
    }

    private void paintStatusPill(Graphics2D g2, WorkflowNode n, Color statusColor,
                                 double sx, double sy, int w, int h) {
        Font font = Theme.uiFontBold(fPill());
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        String text = n.getStatus().getLabel();
        int tw = fm.stringWidth(text);
        int padX = Math.max(4, (int) Math.round(6 * zoom));
        int pillW = tw + padX * 2;
        int pillH = Math.max(13, fm.getHeight() + Math.max(2, (int) Math.round(2 * zoom)));
        int pillX = (int) Math.round(sx + w - pillW - 8 * zoom);
        int pillY = (int) Math.round(sy + h - pillH - 6 * zoom);

        g2.setColor(statusColor);
        g2.fill(new RoundRectangle2D.Double(pillX, pillY, pillW, pillH, pillH / 2.0, pillH / 2.0));
        g2.setColor(new Color(0x0F1420));
        g2.drawString(text, pillX + padX,
                pillY + (pillH - fm.getHeight()) / 2 + fm.getAscent());
    }

    private void paintPort(Graphics2D g2, double x, double y) {
        double r = 4.5;
        g2.setColor(new Color(0x0E1524));
        g2.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        g2.setColor(Theme.PORT);
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }

    /** 按像素宽度截断文本并追加省略号。 */
    private String ellipsize(Graphics2D g2, String s, Font f, int maxPx) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        FontMetrics fm = g2.getFontMetrics(f);
        if (fm.stringWidth(s) <= maxPx) {
            return s;
        }
        String ellipsis = "…";
        int ellW = fm.stringWidth(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            String prefix = s.substring(0, i + 1);
            if (fm.stringWidth(prefix) + ellW > maxPx) {
                break;
            }
            sb.append(s.charAt(i));
        }
        return sb + ellipsis;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
