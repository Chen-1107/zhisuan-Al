package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/** 统一视觉规范：科幻深色调色板 / 字体 / 按钮尺寸 / 卡片（IDE·Agent 工作台） */
public final class Ui {
    /* ---------- 版面底色 ---------- */
    /** 最底层深空 */
    public static final Color BG0 = new Color(4, 7, 15);
    /** 内容区主流背景 */
    public static final Color BG = new Color(8, 15, 34);
    /** 面板 / 卡片 */
    public static final Color PANEL = new Color(13, 23, 46);
    /** 旧版卡片命名兼容别名 */
    public static final Color CARD = PANEL;
    /** 面板 2（略亮） */
    public static final Color PANEL2 = new Color(15, 28, 56);
    /** 面板 3（悬停） */
    public static final Color PANEL3 = new Color(16, 28, 56);

    /* ---------- 线条与边框 ---------- */
    /** 常规边框 */
    public static final Color LINE = new Color(150, 185, 255, 34);
    /** 高亮边框（青色） */
    public static final Color LINE_HOT = new Color(80, 200, 255, 102);

    /* ---------- 文字 ---------- */
    public static final Color TEXT = new Color(224, 234, 255);
    public static final Color SOFT = new Color(204, 214, 240);
    public static final Color MUTED = new Color(164, 183, 214);
    public static final Color FAINT = new Color(124, 146, 187);

    /* ---------- 主题强调色（科幻霓虹） ---------- */
    /** 主强调 · 青 */
    public static final Color ACCENT = new Color(60, 224, 255);
    public static final Color ACCENT_DARK = new Color(47, 123, 255);
    /** 品红 */
    public static final Color MAGENTA = new Color(255, 95, 217);
    /** 金（铝族） */
    public static final Color GOLD = new Color(255, 194, 75);
    /** 产物绿 */
    public static final Color EMERALD = new Color(47, 230, 161);
    /** 辅助蓝 */
    public static final Color ACC = new Color(92, 139, 255);

    /* ---------- 状态色 ---------- */
    public static final Color OK = new Color(57, 217, 138);
    public static final Color BAD = new Color(255, 92, 102);
    public static final Color WARN = new Color(255, 182, 77);

    /* ---------- 侧栏（活动栏 / 面板） ---------- */
    public static final Color SIDEBAR = new Color(6, 11, 24);
    public static final Color SIDEBAR_HOVER = new Color(18, 30, 54);
    public static final Color SIDEBAR_SEL = new Color(22, 52, 96);

    public static final Font FONT = new Font("微软雅黑", Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font("微软雅黑", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("微软雅黑", Font.PLAIN, 12);
    public static final Font FONT_TITLE = new Font("微软雅黑", Font.BOLD, 15);
    /** 逻辑字体：保证中文回退（Consolas 等物理字体缺中文字形会渲染为方块） */
    public static final Font MONO = new Font("Dialog", Font.PLAIN, 12);

    private Ui() {}

    /** 在创建任何 UI 前调用：将 Swing 默认外观压成深色调，避免未手动设色的控件（输入框/下拉框/列表/表格/滚动区等）残留刺眼的浅色白框 */
    public static void applyDark() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignore) {}

        java.awt.Color sel = new Color(60, 224, 255, 80);
        UIManager.put("Panel.background", BG);
        UIManager.put("Panel.foreground", TEXT);
        UIManager.put("Label.background", BG);
        UIManager.put("Label.foreground", TEXT);

        UIManager.put("TextField.background", PANEL);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextField.selectionBackground", sel);
        UIManager.put("TextField.selectionForeground", TEXT);
        UIManager.put("PasswordField.background", PANEL);
        UIManager.put("PasswordField.foreground", TEXT);
        UIManager.put("FormattedTextField.background", PANEL);
        UIManager.put("FormattedTextField.foreground", TEXT);
        UIManager.put("TextArea.background", PANEL);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", ACCENT);
        UIManager.put("TextArea.selectionBackground", sel);
        UIManager.put("TextPane.background", PANEL);
        UIManager.put("TextPane.foreground", TEXT);
        UIManager.put("EditorPane.background", PANEL);
        UIManager.put("EditorPane.foreground", TEXT);

        UIManager.put("ComboBox.background", PANEL);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.disabledBackground", PANEL2);
        UIManager.put("ComboBox.disabledForeground", MUTED);
        UIManager.put("Spinner.background", PANEL);
        UIManager.put("Spinner.foreground", TEXT);

        UIManager.put("List.background", PANEL);
        UIManager.put("List.foreground", SOFT);
        UIManager.put("List.selectionBackground", sel);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("Table.background", PANEL);
        UIManager.put("Table.foreground", SOFT);
        UIManager.put("Table.selectionBackground", sel);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TableHeader.background", PANEL2);
        UIManager.put("TableHeader.foreground", SOFT);

        UIManager.put("ScrollPane.background", BG);
        UIManager.put("ScrollPane.viewportBorder", new LineBorder(new Color(0, 0, 0, 0)));
        UIManager.put("Viewport.background", BG);

        UIManager.put("Button.background", PANEL);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));

        UIManager.put("CheckBox.background", BG);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("CheckBox.focus", new Color(0, 0, 0, 0));
        UIManager.put("RadioButton.background", BG);
        UIManager.put("RadioButton.foreground", TEXT);
        UIManager.put("RadioButton.focus", new Color(0, 0, 0, 0));

        UIManager.put("TitledBorder.titleColor", SOFT);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("MenuItem.background", PANEL);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("Menu.background", PANEL);
        UIManager.put("Menu.foreground", TEXT);
        UIManager.put("PopupMenu.background", PANEL);
        UIManager.put("PopupMenu.foreground", TEXT);
        UIManager.put("ToolTip.background", PANEL);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", new LineBorder(LINE_HOT));
    }

    /** 统一尺寸的主按钮（110×32，青色圆角） */
    public static JButton button(String text) {
        return new RoundedButton(text, true);
    }

    /** 统一尺寸的次要按钮（110×32，深色面板圆角） */
    public static JButton secondary(String text) {
        return new RoundedButton(text, false);
    }

    /** 圆角卡片，带标题（自绘圆角背景 + 圆角描边 + 左上角标题） */
    public static JPanel card(String title) {
        RoundedPanel p = new RoundedPanel(BG, 12);
        p.stroke(LINE);
        p.setBorder(new RoundedTitledBorder(title));
        return p;
    }

    /** 更新由 card(title) 创建的卡片标题边框文字（用于语言切换后的界面刷新） */
    public static void setCardText(JPanel p, String title) {
        if (p != null && p.getBorder() instanceof RoundedTitledBorder) {
            RoundedTitledBorder b = (RoundedTitledBorder) p.getBorder();
            b.setTitle(title);
            p.repaint();
        }
    }

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT);
        l.setForeground(TEXT);
        return l;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_SMALL);
        l.setForeground(MUTED);
        return l;
    }

    public static JTextField textField(int columns) {
        JTextField f = new JTextField(columns);
        f.setFont(FONT);
        f.setCaretColor(ACCENT);
        f.setBackground(PANEL2);
        f.setForeground(TEXT);
        f.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(4, 8, 4, 8)));
        return f;
    }

    public static JSpinner spinner(int value, int min, int max, int step) {
        JSpinner s = new JSpinner(new javax.swing.SpinnerNumberModel(value, min, max, step));
        s.setFont(FONT);
        ((JSpinner.DefaultEditor) s.getEditor()).getTextField().setColumns(5);
        return s;
    }

    // ---------------- 圆角组件 ----------------

    /** 圆角面板：非不透明，自绘圆角背景，可选圆角描边。子组件按普通 JPanel 方式添加。 */
    public static class RoundedPanel extends JPanel {
        private final int arc;
        private Color stroke;
        private final Color fill;

        public RoundedPanel(Color fill, int arc) {
            super(false);
            this.fill = fill;
            this.arc = arc;
        }

        public RoundedPanel stroke(Color c) { this.stroke = c; return this; }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            if (stroke != null) {
                g2.setColor(stroke);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }
            g2.dispose();
        }
    }

    /** 圆角卡片标题边框：只在左上角绘制标题文字（圆角描边由 RoundedPanel 绘制） */
    static class RoundedTitledBorder extends AbstractBorder {
        private String title;

        RoundedTitledBorder(String title) { this.title = title; }

        void setTitle(String title) { this.title = title; }

        @Override public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(20, 12, 8, 12);
            return insets;
        }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(FONT_BOLD);
            g2.setColor(SOFT);
            g2.drawString(title, x + 10, y + 18);
            g2.dispose();
        }
    }

    /** 圆角按钮：自绘圆角底色（主按钮=青色，次按钮=深面板），悬停微亮 */
    private static class RoundedButton extends JButton {
        private final boolean primary;

        RoundedButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(primary ? FONT_BOLD : FONT);
            setPreferredSize(new Dimension(110, 32));
            setForeground(primary ? new Color(4, 18, 31) : TEXT);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(new EmptyBorder(0, 10, 0, 10));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = primary ? ACCENT : PANEL;
            if (getModel().isRollover() || isFocusOwner()) bg = bg.brighter();
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 9, 9);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}