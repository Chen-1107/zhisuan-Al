package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** 分子优化页：MDP 优化（C++ 核心）+ 3D 可视化（molview.exe）+ 分子性质 */
public class OptimizePage extends Page {
    private final JTextField smilesField = Ui.textField(28);
    private final javax.swing.JSpinner episodes = Ui.spinner(30, 0, 500, 5);
    private final javax.swing.JSpinner steps = Ui.spinner(60, 10, 500, 10);
    private final JButton pickBtn = Ui.secondary("");
    private final JButton runBtn = Ui.button("");
    private final JButton vizBtn = Ui.secondary("");
    private final JButton propBtn = Ui.secondary("");
    private final JProgressBar progress = new JProgressBar();
    private final JLabel status = Ui.muted("");
    private final JLabel quickTry = Ui.muted("");
    private final JPanel topCard = Ui.card("");
    private final JPanel resCard = Ui.card("");
    private final JPanel propCard = Ui.card("");

    // 结果 / 性质卡片左侧名称标签（语言切换时刷新）
    private final JLabel[] resLabels = new JLabel[7];
    private final JLabel[] propLabels = new JLabel[8];

    private final JLabel rBackend = valueLabel();
    private final JLabel rEnergy = valueLabel();
    private final JLabel rConstraint = valueLabel();
    private final JLabel rViolations = valueLabel();
    private final JLabel rValid = valueLabel();
    private final JLabel rEpisodes = valueLabel();
    private final JLabel rElapsed = valueLabel();

    private final JLabel pFormula = valueLabel();
    private final JLabel pMw = valueLabel();
    private final JLabel pLogp = valueLabel();
    private final JLabel pHbd = valueLabel();
    private final JLabel pHba = valueLabel();
    private final JLabel pTpsa = valueLabel();
    private final JLabel pRot = valueLabel();
    private final JLabel pHeavy = valueLabel();

    OptimizePage(ApiClient api) {
        super(api);
        setLayout(new BorderLayout(0, 10));
        Langs.addListener(this::applyLang);

        // ---- 顶部输入卡片 ----
        topCard.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 8, 4, 8);
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0;
        g.gridy = 0;
        JLabel lblSmiles = Ui.label("");
        topComponents[0] = lblSmiles;
        topCard.add(lblSmiles, g);
        g.gridx = 1;
        g.gridwidth = 3;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        smilesField.setToolTipText(Langs.t("opt.smilesHint"));
        topCard.add(smilesField, g);
        g.gridwidth = 1;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        g.gridx = 4;
        topCard.add(pickBtn, g);

        g.gridx = 0;
        g.gridy = 1;
        JLabel lblEpisodes = Ui.label("");
        topComponents[1] = lblEpisodes;
        topCard.add(lblEpisodes, g);
        g.gridx = 1;
        topCard.add(episodes, g);
        g.gridx = 2;
        JLabel lblSteps = Ui.label("");
        topComponents[2] = lblSteps;
        topCard.add(lblSteps, g);
        g.gridx = 3;
        topCard.add(steps, g);

        g.gridx = 5;
        g.gridy = 0;
        topCard.add(runBtn, g);
        g.gridx = 5;
        g.gridy = 1;
        topCard.add(vizBtn, g);
        g.gridx = 6;
        g.gridy = 0;
        topCard.add(propBtn, g);

        g.gridx = 0;
        g.gridy = 2;
        g.gridwidth = 7;
        g.fill = GridBagConstraints.HORIZONTAL;
        progress.setVisible(false);
        progress.setIndeterminate(true);
        progress.setStringPainted(false);
        progress.setPreferredSize(new Dimension(200, 14));
        topCard.add(progress, g);
        g.gridy = 3;
        topCard.add(status, g);
        add(topCard, BorderLayout.NORTH);

        // ---- 结果两栏 ----
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0, 0, 0, 10);
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 0.62;
        gc.weighty = 1;
        gc.fill = GridBagConstraints.BOTH;
        center.add(buildResultCard(), gc);
        gc.gridx = 1;
        gc.weightx = 0.38;
        gc.insets = new Insets(0, 0, 0, 0);
        center.add(buildPropertyCard(), gc);
        add(center, BorderLayout.CENTER);

        // 快捷示例
        JPanel examples = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        examples.setOpaque(false);
        examples.add(quickTry);
        for (String s : new String[]{"CCO 乙醇", "CC(=O)Oc1ccccc1C(=O)O 阿司匹林", "咔唑基铝烯前体"}) {
            JButton b = new JButton(s);
            b.setFont(Ui.FONT_SMALL);
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setForeground(Ui.ACCENT);
            b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            b.addActionListener(e -> smilesField.setText(s));
            examples.add(b);
        }
        add(examples, BorderLayout.SOUTH);

        pickBtn.addActionListener(e -> pickCompound());
        runBtn.addActionListener(e -> runOptimize());
        vizBtn.addActionListener(e -> visualize());
        propBtn.addActionListener(e -> properties());
        applyLang();
    }

    private void applyLang() {
        Ui.setCardText(topCard, Langs.t("opt.title"));
        Ui.setCardText(resCard, Langs.t("opt.resTitle"));
        Ui.setCardText(propCard, Langs.t("opt.pTitle"));
        setTopLabel(0, Langs.t("opt.lblSmiles"));
        setTopLabel(1, Langs.t("opt.episodes"));
        setTopLabel(2, Langs.t("opt.steps"));
        pickBtn.setText(Langs.t("opt.pick"));
        runBtn.setText(Langs.t("opt.run"));
        vizBtn.setText(Langs.t("opt.viz"));
        propBtn.setText(Langs.t("opt.props"));
        smilesField.setToolTipText(Langs.t("opt.smilesHint"));
        if (status.getText() == null || status.getText().isEmpty()) status.setText(Langs.t("opt.ready"));
        quickTry.setText(Langs.t("opt.quickTry"));
        String[] rk = {Langs.t("opt.rBackend"), Langs.t("opt.rEnergy"), Langs.t("opt.rConstraint"),
                Langs.t("opt.rViolations"), Langs.t("opt.rValid"), Langs.t("opt.rEpisodes"), Langs.t("opt.rElapsed")};
        for (int i = 0; i < rk.length; i++) if (resLabels[i] != null) resLabels[i].setText(rk[i]);
        String[] pk = {Langs.t("opt.pFormula"), Langs.t("opt.pMw"), Langs.t("opt.pLogp"),
                Langs.t("opt.pHbd"), Langs.t("opt.pHba"), Langs.t("opt.pTpsa"),
                Langs.t("opt.pRot"), Langs.t("opt.pHeavy")};
        for (int i = 0; i < pk.length; i++) if (propLabels[i] != null) propLabels[i].setText(pk[i]);
    }

    // 顶部输入区占位标签（0:lblSmiles 1:lblEpisodes 2:lblSteps）——保留引用以便 setText
    private final JLabel[] topComponents = new JLabel[3];

    private void setTopLabel(int idx, String text) {
        if (topComponents[idx] != null) topComponents[idx].setText(text);
    }

    private JPanel buildResultCard() {
        resCard.setLayout(new GridBagLayout());
        resLabels[0] = addRow(resCard, resLabels, 0, rBackend);
        resLabels[1] = addRow(resCard, resLabels, 1, rEnergy);
        resLabels[2] = addRow(resCard, resLabels, 2, rConstraint);
        resLabels[3] = addRow(resCard, resLabels, 3, rViolations);
        resLabels[4] = addRow(resCard, resLabels, 4, rValid);
        resLabels[5] = addRow(resCard, resLabels, 5, rEpisodes);
        resLabels[6] = addRow(resCard, resLabels, 6, rElapsed);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 7;
        g.weighty = 1;
        resCard.add(new JLabel(), g);
        return resCard;
    }

    private JPanel buildPropertyCard() {
        propCard.setLayout(new GridBagLayout());
        propLabels[0] = addRow(propCard, propLabels, 0, pFormula);
        propLabels[1] = addRow(propCard, propLabels, 1, pMw);
        propLabels[2] = addRow(propCard, propLabels, 2, pLogp);
        propLabels[3] = addRow(propCard, propLabels, 3, pHbd);
        propLabels[4] = addRow(propCard, propLabels, 4, pHba);
        propLabels[5] = addRow(propCard, propLabels, 5, pTpsa);
        propLabels[6] = addRow(propCard, propLabels, 6, pRot);
        propLabels[7] = addRow(propCard, propLabels, 7, pHeavy);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 8;
        g.weighty = 1;
        propCard.add(new JLabel(), g);
        return propCard;
    }

    /** 新增一行名称标签；返回该名称标签以便语言切换时更新 */
    private JLabel addRow(JPanel card, JLabel[] store, int row, JLabel value) {
        JLabel name = Ui.muted("");
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 10, 5, 8);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        g.gridy = row;
        card.add(name, g);
        g.gridx = 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        card.add(value, g);
        store[row] = name;
        return name;
    }

    private static JLabel valueLabel() {
        JLabel l = new JLabel("—");
        l.setFont(new Font("微软雅黑", Font.BOLD, 13));
        l.setForeground(Ui.TEXT);
        return l;
    }

    private String smiles() { return smilesField.getText().trim(); }

    private void setBusy(boolean b, String msg) {
        SwingUtilities.invokeLater(() -> {
            runBtn.setEnabled(!b);
            vizBtn.setEnabled(!b);
            propBtn.setEnabled(!b);
            progress.setVisible(b);
            status.setText(msg);
        });
    }

    private void runOptimize() {
        String smi = smiles();
        if (smi.isEmpty()) {
            status.setText(Langs.t("opt.noInput"));
            return;
        }
        setBusy(true, Langs.t("opt.busyRun", smi));
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                Map<String, Object> body = Map.of(
                        "smiles", smi,
                        "episodes", (Integer) episodes.getValue(),
                        "max_steps", (Integer) steps.getValue());
                return api.post("/api/optimize", body);
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> r = Json.obj(get());
                    rBackend.setText("cpp".equals(Json.str(r.get("backend"))) ? Langs.t("opt.cpp") : Langs.t("opt.py"));
                    rEnergy.setText(String.format("%.3f", Json.num(r.get("total_energy"))));
                    rConstraint.setText(String.format("%.3f", Json.num(r.get("constraint_energy"))));
                    int viol = Json.Int(r.get("violations"));
                    rViolations.setText(String.valueOf(viol));
                    rViolations.setForeground(viol == 0 ? Ui.OK : Ui.WARN);
                    rValid.setText(Json.bool(r.get("is_valid")) ? Langs.t("opt.yes") : Langs.t("opt.no"));
                    rValid.setForeground(Json.bool(r.get("is_valid")) ? Ui.OK : Ui.BAD);
                    rEpisodes.setText(String.valueOf(Json.Int(r.get("episodes_run"))));
                    rElapsed.setText(String.format("%.1f ms", Json.num(r.get("elapsed_ms"))));
                    setBusy(false, Langs.t("opt.doneRun"));
                } catch (Exception e) {
                    setBusy(false, Langs.t("opt.failRun", e.getMessage()));
                }
            }
        }.execute();
    }

    private void visualize() {
        String smi = smiles();
        if (smi.isEmpty()) {
            status.setText(Langs.t("opt.noInput"));
            return;
        }
        setBusy(true, Langs.t("opt.busyViz"));
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.post("/api/visualize", Map.of("smiles", smi, "show_hydrogen", true));
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> r = Json.obj(get());
                    String viewer = Json.str(r.get("viewer"));
                    String viewerName = "molview".equals(viewer) ? Langs.t("opt.viewerNative") : Langs.t("opt.viewerWeb");
                    setBusy(false, Langs.t("opt.doneViz", viewerName,
                            Json.Int(r.get("atoms")), Json.Int(r.get("bonds"))));
                } catch (Exception e) {
                    setBusy(false, Langs.t("opt.failViz", e.getMessage()));
                }
            }
        }.execute();
    }

    private void properties() {
        String smi = smiles();
        if (smi.isEmpty()) {
            status.setText(Langs.t("opt.noInput"));
            return;
        }
        setBusy(true, Langs.t("opt.busyProps"));
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.post("/api/properties", Map.of("smiles", smi));
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> r = Json.obj(get());
                    pFormula.setText(Json.str(r.get("formula")));
                    pMw.setText(String.format("%.2f g/mol", Json.num(r.get("exact_mw"))));
                    pLogp.setText(String.format("%.2f", Json.num(r.get("logp"))));
                    pHbd.setText(String.valueOf(Json.Int(r.get("hbd"))));
                    pHba.setText(String.valueOf(Json.Int(r.get("hba"))));
                    pTpsa.setText(String.format("%.1f", Json.num(r.get("tpsa"))));
                    pRot.setText(String.valueOf(Json.Int(r.get("rotatable"))));
                    pHeavy.setText(String.valueOf(Json.Int(r.get("heavy_atoms"))));
                    setBusy(false, Langs.t("opt.doneProps"));
                } catch (Exception e) {
                    setBusy(false, Langs.t("opt.failProps", e.getMessage()));
                }
            }
        }.execute();
    }

    // ---------------- 化合物选择对话框 ----------------
    private void pickCompound() {
        if (status.getText() == null || status.getText().isEmpty()) status.setText(Langs.t("opt.ready"));
        status.setText(Langs.t("opt.loadingLib"));
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.get("/api/compounds");
            }

            @Override
            protected void done() {
                List<Map<String, String>> compounds = new ArrayList<>();
                try {
                    for (Object o : Json.arr(get())) {
                        Map<String, Object> m = Json.obj(o);
                        compounds.add(Map.of(
                                "name", Json.str(m.get("name")),
                                "smiles", Json.str(m.get("smiles"))));
                    }
                } catch (Exception e) {
                    status.setText(Langs.t("opt.libFail", e.getMessage()));
                    return;
                }
                CompoundDialog d = new CompoundDialog(compounds, smiles(), chosen -> {
                    if (chosen != null) smilesField.setText(chosen);
                    status.setText(Langs.t("opt.chosen"));
                });
                d.setLocationRelativeTo(OptimizePage.this);
                d.setVisible(true);
            }
        }.execute();
    }

    /** 化合物选择对话框（含搜索过滤） */
    private static class CompoundDialog extends JDialog {
        private final JList<String> list;
        private final transient List<Map<String, String>> compounds;
        private final transient Consumer<String> onSelect;

        CompoundDialog(List<Map<String, String>> compounds, String initial,
                       Consumer<String> onSelect) {
            this.compounds = compounds;
            this.onSelect = onSelect;
            setTitle(Langs.t("opt.dlgTitle", compounds.size()));
            setModal(true);
            setSize(560, 520);
            setLayout(new BorderLayout(8, 8));

            JPanel top = new JPanel(new BorderLayout(6, 0));
            top.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
            JTextField search = Ui.textField(20);
            search.setToolTipText(Langs.t("opt.dlgSearchHint"));
            top.add(Ui.label(Langs.t("opt.dlgSearch")), BorderLayout.WEST);
            top.add(search, BorderLayout.CENTER);
            add(top, BorderLayout.NORTH);

            DefaultListModel<String> model = new DefaultListModel<>();
            for (Map<String, String> c : compounds) model.addElement(c.get("name"));
            list = new JList<>(model);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setFont(Ui.FONT);
            search.addActionListener(e -> {
                String q = search.getText().trim().toLowerCase();
                model.clear();
                for (Map<String, String> c : compounds)
                    if (q.isEmpty() || c.get("name").toLowerCase().contains(q)
                            || c.get("smiles").toLowerCase().contains(q))
                        model.addElement(c.get("name"));
            });
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) confirm();
                }
            });
            add(new JScrollPane(list), BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton ok = Ui.button(Langs.t("opt.ok"));
            JButton cancel = Ui.secondary(Langs.t("opt.cancel"));
            bottom.add(ok);
            bottom.add(cancel);
            bottom.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
            add(bottom, BorderLayout.SOUTH);

            ok.addActionListener(e -> confirm());
            cancel.addActionListener(e -> {
                onSelect.accept(null);
                dispose();
            });
            if (initial != null && !initial.isEmpty()) search.setText(initial);
        }

        private void confirm() {
            String name = list.getSelectedValue();
            if (name != null) {
                for (Map<String, String> c : compounds) {
                    if (name.equals(c.get("name"))) {
                        onSelect.accept(c.get("smiles"));
                        break;
                    }
                }
            }
            dispose();
        }
    }
}