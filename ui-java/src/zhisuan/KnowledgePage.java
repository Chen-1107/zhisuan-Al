package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 知识库页：分类浏览 + 搜索 + 全文阅读（技能全文为 Markdown，富文本渲染） */
public class KnowledgePage extends Page {
    private final JTextField search = Ui.textField(16);
    private final javax.swing.JComboBox<String> category = new javax.swing.JComboBox<>();
    private final DefaultListModel<SkillItem> model = new DefaultListModel<>();
    private final JList<SkillItem> list = new JList<>(model);
    private final JLabel title = new JLabel();
    private final JLabel meta = Ui.muted(" ");
    private final javax.swing.JTextPane content = new javax.swing.JTextPane();
    private final JPanel leftCard = Ui.card("");
    private final JPanel rightCard = Ui.card("");
    private final JLabel searchLbl = Ui.label("");
    private final JLabel catLbl = Ui.label("");

    /** 与对话页一致的阅读样式（Markdown → HTML，深色主题） */
    private static final String CSS = "<style>"
            + "body { font-family: 微软雅黑; font-size: 13px; color: #dbe7ff; background-color: #0c1530; margin: 4px 10px; }"
            + "p { margin: 4px 0 8px 0; }"
            + "code { font-family: Consolas, Dialog; color: #ff7ac8; }"
            + "blockquote { color: #b8cdf0; font-style: italic; margin-left: 12px; border-left: 2px solid #3c4a66; }"
            + ".h1 { font-size: 19px; font-weight: bold; margin: 8px 0 6px 0; color: #ffffff; }"
            + ".h2 { font-size: 16px; font-weight: bold; margin: 8px 0 4px 0; color: #ffffff; }"
            + ".h3 { font-size: 14px; font-weight: bold; margin: 6px 0 4px 0; color: #e3edff; }"
            + ".h4 { font-size: 13px; font-weight: bold; margin: 6px 0 4px 0; color: #e3edff; }"
            + ".codeblock { background-color: #0a1225; margin: 6px 0; border: 1px solid #2a3752; }"
            + ".codelang { font-family: Consolas, Dialog; font-size: 11px; color: #8ba3cb;"
            + " background-color: #101b33; padding: 2px 6px; border-bottom: 1px solid #2a3752; }"
            + ".codeblock pre { font-family: Consolas, Dialog; font-size: 12px; color: #cfe0ff; margin: 2px; padding: 2px; }"
            + "table { border: 1px solid #33415c; margin: 4px 0 8px 0; }"
            + "th { background-color: #17233c; padding: 4px 8px; border: 1px solid #33415c; font-weight: bold; color: #eef4ff; }"
            + "td { padding: 3px 8px; border: 1px solid #2b3a57; }"
            + "pre { font-family: Consolas, Dialog; font-size: 12px; color: #cfe0ff; margin: 2px; }"
            + "img { margin: 4px 0; }"
            + "a { color: #5ab0ff; }"
            + "</style>";

    static final class SkillItem {
        final String name, title, category, description;
        final List<String> tags = new ArrayList<>();
        final int size;

        SkillItem(Map<String, Object> m) {
            this.name = Json.str(m.get("name"));
            this.title = Json.str(m.get("title"));
            this.category = Json.str(m.get("category"));
            this.description = Json.str(m.get("description"));
            this.size = Json.Int(m.get("size"));
            for (Object t : Json.arr(m.get("tags"))) tags.add(Json.str(t));
        }

        @Override
        public String toString() {
            return title + "  [" + category + "]";
        }
    }

    KnowledgePage(ApiClient api) {
        super(api);
        setLayout(new BorderLayout(0, 10));

        // 左侧列表
        leftCard.setPreferredSize(new Dimension(340, 0));
        leftCard.setLayout(new BorderLayout(0, 6));
        JPanel filter = new JPanel(new BorderLayout(6, 4));
        filter.setOpaque(false);
        filter.setBorder(BorderFactory.createEmptyBorder(2, 8, 0, 8));
        JPanel row1 = new JPanel(new BorderLayout(4, 0));
        row1.setOpaque(false);
        row1.add(searchLbl, BorderLayout.WEST);
        row1.add(search, BorderLayout.CENTER);
        search.setToolTipText(Langs.t("kb.searchHint"));
        category.setModel(new DefaultComboBoxModel<>(new String[]{Langs.t("kb.all")}));
        category.setFont(Ui.FONT);
        category.addActionListener(e -> applyFilter());
        filter.add(row1, BorderLayout.NORTH);
        JPanel row2 = new JPanel(new BorderLayout(4, 0));
        row2.setOpaque(false);
        row2.add(catLbl, BorderLayout.WEST);
        row2.add(category, BorderLayout.CENTER);
        filter.add(row2, BorderLayout.SOUTH);
        leftCard.add(filter, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new SkillRenderer());
        list.addListSelectionListener(e -> {
            SkillItem it = list.getSelectedValue();
            if (it != null) openSkill(it);
        });
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        leftCard.add(sp, BorderLayout.CENTER);
        add(leftCard, BorderLayout.WEST);

        // 右侧内容
        rightCard.setLayout(new BorderLayout(0, 4));
        title.setFont(new Font("微软雅黑", Font.BOLD, 16));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(2, 10, 0, 10));
        head.add(title, BorderLayout.NORTH);
        head.add(meta, BorderLayout.SOUTH);
        rightCard.add(head, BorderLayout.NORTH);
        content.setEditable(false);
        content.setContentType("text/html");
        content.setBackground(new Color(0x0c1530));
        content.setCaretColor(new Color(60, 224, 255));
        content.setMargin(new java.awt.Insets(6, 8, 6, 8));
        content.setText(CSS + "<p>" + Langs.t("kb.placeholder") + "</p>");
        content.addHyperlinkListener(e -> {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
            String href = e.getDescription();
            if (href != null && href.startsWith("http")) {
                try {
                    Desktop.getDesktop().browse(new java.net.URI(href));
                } catch (Exception ignored) {
                }
            }
        });
        JScrollPane sp2 = new JScrollPane(content);
        sp2.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));
        rightCard.add(sp2, BorderLayout.CENTER);
        add(rightCard, BorderLayout.CENTER);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFilter(); }

            @Override
            public void removeUpdate(DocumentEvent e) { applyFilter(); }

            @Override
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        Langs.addListener(this::applyLang);
    }

    private void applyLang() {
        Ui.setCardText(leftCard, Langs.t("kb.left"));
        Ui.setCardText(rightCard, Langs.t("kb.right"));
        searchLbl.setText(Langs.t("kb.search"));
        catLbl.setText(Langs.t("kb.category"));
        search.setToolTipText(Langs.t("kb.searchHint"));
        if (title.getText().isEmpty()) title.setText(Langs.t("kb.hint"));
    }

    private final List<SkillItem> all = new ArrayList<>();

    @Override
    public void onShow() {
        applyLang();
        if (all.isEmpty()) reload();
    }

    private void reload() {
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.get("/api/knowledge/skills");
            }

            @Override
            protected void done() {
                try {
                    all.clear();
                    for (Object o : Json.arr(Json.obj(get()).get("skills"))) all.add(new SkillItem(Json.obj(o)));
                    // 分类下拉
                    List<String> cats = new ArrayList<>();
                    cats.add(Langs.t("kb.all"));
                    all.stream().map(s -> s.category).distinct().sorted().forEach(cats::add);
                    category.setModel(new DefaultComboBoxModel<>(cats.toArray(new String[0])));
                    applyFilter();
                } catch (Exception e) {
                    content.setText(CSS + "<p>" + Markdown.render(
                            String.format(Langs.t("kb.loadFail"), e.getMessage())) + "</p>");
                }
            }
        }.execute();
    }

    private void applyFilter() {
        String q = search.getText().trim().toLowerCase();
        String cat = (String) category.getSelectedItem();
        model.clear();
        String allCat = Langs.t("kb.all");
        for (SkillItem s : all) {
            if (cat != null && !allCat.equals(cat) && !cat.equals(s.category)) continue;
            if (!q.isEmpty() && !match(s, q)) continue;
            model.addElement(s);
        }
        if (model.isEmpty() && !all.isEmpty()) {
            title.setText(Langs.t("kb.noMatch"));
            meta.setText(" ");
        }
    }

    private boolean match(SkillItem s, String q) {
        if (s.name.toLowerCase().contains(q) || s.title.toLowerCase().contains(q)
                || s.description.toLowerCase().contains(q)) return true;
        for (String t : s.tags) if (t.toLowerCase().contains(q)) return true;
        return false;
    }

    private void openSkill(SkillItem item) {
        title.setText(item.title);
        meta.setText(String.format(Langs.t("kb.meta"), item.category,
                item.tags.isEmpty() ? "—" : String.join(" · ", item.tags), item.size));
        content.setText(CSS + "<p>" + Langs.t("kb.loadingContent") + "</p>");
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.get("/api/knowledge/skill?name=" + java.net.URLEncoder.encode(item.name,
                        java.nio.charset.StandardCharsets.UTF_8));
            }

            @Override
            protected void done() {
                try {
                    // 技能全文为 Markdown：富文本渲染（标题/表格/代码块/公式说明等）
                    content.setText(CSS + Markdown.render(Json.str(Json.obj(get()).get("content"))));
                } catch (Exception e) {
                    content.setText(CSS + "<p>" + String.format(Langs.t("kb.readFail"), e.getMessage()) + "</p>");
                }
                content.setCaretPosition(0);
            }
        }.execute();
    }

    private static class SkillRenderer extends JPanel implements javax.swing.ListCellRenderer<KnowledgePage.SkillItem> {
        private final JLabel t = new JLabel();
        private final JLabel m = new JLabel();
        private final Color[] colors = {new Color(96, 165, 250), new Color(167, 139, 250),
                new Color(52, 211, 153), new Color(251, 191, 36), new Color(248, 113, 113),
                new Color(45, 212, 191), new Color(232, 121, 249), new Color(148, 163, 184)};

        SkillRenderer() {
            setLayout(new BorderLayout(4, 2));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            add(t, BorderLayout.NORTH);
            add(m, BorderLayout.SOUTH);
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(javax.swing.JList<? extends KnowledgePage.SkillItem> l,
                                                      KnowledgePage.SkillItem v, int i, boolean sel, boolean foc) {
            t.setText(v.title);
            t.setFont(new Font("微软雅黑", Font.BOLD, 13));
            t.setForeground(sel ? Color.WHITE : Ui.TEXT);
            m.setText(String.format(Langs.t("kb.itemMeta"), v.category, v.size));
            m.setFont(Ui.FONT_SMALL);
            m.setForeground(sel ? new Color(224, 241, 255) : Ui.MUTED);
            if (sel) {
                setBackground(Ui.ACCENT);
            } else {
                setBackground(i % 2 == 0 ? Ui.PANEL : Ui.PANEL2);
            }
            return this;
        }
    }
}
