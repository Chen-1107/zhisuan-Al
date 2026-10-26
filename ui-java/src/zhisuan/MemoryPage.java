package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

/** 记忆中心：长时记忆 / 决策记忆 / 原子事实，跨会话复用 */
public class MemoryPage extends Page {
    private final JPanel decCard = cardLines();
    private final JPanel sesCard = cardLines();
    private final JLabel title = new JLabel();
    private final JLabel en = new JLabel();
    private final JLabel status = new JLabel();
    private final JButton sr = Ui.secondary("");
    private final JButton ex = Ui.button("");

    public MemoryPage(ApiClient api) {
        super(api);
        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(2, 2, 2, 2));
        add(buildTop(), BorderLayout.NORTH);
        add(buildGrid(), BorderLayout.CENTER);
        Langs.addListener(this::applyLang);
    }

    private void applyLang() {
        title.setText(Langs.t("mem.title"));
        title.setFont(Ui.FONT_TITLE);
        title.setForeground(Ui.TEXT);
        en.setText(Langs.t("mem.sub"));
        en.setFont(Ui.MONO);
        en.setForeground(Ui.FAINT);
        if (status.getText() == null || status.getText().isEmpty()) status.setText(Langs.t("mem.statusIdle"));
        sr.setText(Langs.t("mem.search"));
        ex.setText(Langs.t("mem.extract"));
        blockQuiz.setTitle(Langs.t("mem.blockQuiz"));
        blockFacts.setTitle(Langs.t("mem.blockFacts"));
    }

    private final Block blockQuiz = new Block("");
    private final Block blockFacts = new Block("");

    private JPanel buildTop() {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel t = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        t.setOpaque(false);
        t.add(title); t.add(en);
        top.add(t, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        sr.setPreferredSize(new Dimension(110, 30));
        sr.addActionListener(e -> {
            status.setText(Langs.t("mem.searching"));
            new javax.swing.Timer(800, ev -> status.setText(Langs.t("mem.hits3"))).start();
        });
        ex.setPreferredSize(new Dimension(120, 30));
        ex.addActionListener(e -> {
            sesCard.add(line(Langs.t("mem.extracted"), "· key"));
            status.setText(Langs.t("mem.extracted1"));
            sesCard.revalidate(); sesCard.repaint();
        });
        right.add(sr); right.add(ex);
        top.add(right, BorderLayout.EAST);
        return top;
    }

    private JPanel buildGrid() {
        JPanel p = new JPanel(new GridLayout(1, 2, 10, 0));
        p.setOpaque(false);

        JPanel d = blockQuiz.build("决策记忆", "RECALL 87%", Ui.GOLD, decCard);
        d.add(line("任务/TS/高精度 → 泛函 ωB97X-D · def2-TZVP（含重元素）", ""));
        d.add(line("任务/激发态 → CAM-B3LYP", ""));
        d.add(line("任务/轻体系 → B3LYP", ""));
        d.add(line("CO₂ 活化 → Al 亲氧端（非碳端）· 键级 0.887", ""));
        d.add(line("TS 采用 Opt=(TS,CalcFC,NoEigenTest) · 虚频校验", ""));

        JPanel s = blockFacts.build("会话事实", "LTM 300 · 剪枝阈值", Ui.ACCENT, sesCard);
        s.add(line("Se 替代 势垒 65.4 vs Te 66.3 kcal/mol", "· 多次命中"));
        s.add(line("TS 端点能量比扫描起点低 25–27 kcal/mol", ""));
        s.add(line("文档四层星图 · 输入青/模式品/铝金/产物绿", ""));

        p.add(d); p.add(s);
        return p;
    }

    private static final class Block {
        private JLabel tl = new JLabel();
        Block(String h) { tl.setText(h); }
        void setTitle(String h) { tl.setText(h); }
        JPanel build(String h, String tag, Color bar, JPanel body) {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(Ui.PANEL);
            p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.LINE),
                    new EmptyBorder(12, 14, 12, 14)));
            JPanel hd = new JPanel(new BorderLayout());
            hd.setOpaque(false);
            tl.setText(h);
            tl.setFont(new Font("微软雅黑", Font.BOLD, 13));
            tl.setForeground(Ui.SOFT);
            JLabel tg = new JLabel(tag);
            tg.setFont(Ui.MONO);
            tg.setForeground(Ui.FAINT);
            hd.add(tl, BorderLayout.WEST); hd.add(tg, BorderLayout.EAST);
            p.add(hd); p.add(Box.createVerticalStrut(10));
            p.add(body);
            return p;
        }
    }

    private JPanel cardLines() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    private JLabel line(String text, String tail) {
        JLabel l = new JLabel("<html><div style='font-family:微软雅黑;font-size:11px;color:#c3d0ee;line-height:1.7'>"
                + "▸ " + text
                + (tail.isEmpty() ? "" : " <span style='color:#8497bd'>" + tail + "</span>")
                + "</div></html>");
        l.setBorder(new EmptyBorder(3, 0, 3, 0));
        return l;
    }

    @Override public void onShow() { applyLang(); }
}