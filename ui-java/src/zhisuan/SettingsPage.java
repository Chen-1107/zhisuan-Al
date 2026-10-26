package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置与状态页：组件健康检查 + 服务地址 + DFT 计算后端配置。
 * DFT 配置通过 agent_server.py 的 /api/dft_config 与 /api/dft_test 接口
 * 连接真实计算程序：Gaussian(g16) / ORCA / Multiwfn 路径、SSH 集群、工作区目录，
 * 保存后立即热应用（无需重启服务层）。
 */
public class SettingsPage extends Page {
    private static final String[] BACKEND_KEYS = {"local_g16", "local_orca", "cluster_g16"};
    private static final String[] MODEL_BACKEND_KEYS = {"local", "hybrid", "cloud"};
    private static final String[] HEALTH_KEYS = {"set.theServer", "set.ollama", "set.modelReady", "set.molopt",
            "set.molview", "set.rdkit", "set.kb", "set.dft", "set.workflow"};

    private final Runnable onServerChange;
    private final JTextField urlField = Ui.textField(24);
    private final JButton refreshBtn = Ui.button("");
    private final JButton saveBtn = Ui.button("");
    private final Map<String, JLabel> statusLabels = new LinkedHashMap<>();
    private final List<JLabel> healthNameLabels = new ArrayList<>();

    private final JPanel healthCard = Ui.card("");
    private final JPanel connCard = Ui.card("");
    private final JPanel dftCard = Ui.card("");
    private final JPanel modelCard = Ui.card("");
    private final JPanel aboutCard = Ui.card("");
    private final TitledBorder sshBorder;

    // ---- DFT 计算后端配置 ----
    private final JTextField g16Field = Ui.textField(28);
    private final JTextField orcaField = Ui.textField(28);
    private final JTextField mwField = Ui.textField(28);
    private final JTextField wsField = Ui.textField(28);
    private final JTextField sshHost = Ui.textField(14);
    private final JTextField sshUser = Ui.textField(10);
    private final JPasswordField sshPass = new JPasswordField(10);
    private final JTextField sshKey = Ui.textField(16);
    private final JTextField sshDir = Ui.textField(10);
    private final JComboBox<String> sshQueue = new JComboBox<>(new String[]{"pbs", "slurm"});
    private final JTextField sshQueueName = Ui.textField(8);
    private final JTextField sshG16Root = Ui.textField(14);
    private final JTextField sshG16Scratch = Ui.textField(14);
    private final JComboBox<String> backendCombo = new JComboBox<>();
    private final JButton dftSaveBtn = Ui.button("");
    private final JLabel dftStatus = Ui.muted(" ");
    private final JLabel cfgPathLabel = Ui.muted(" ");

    // ---- 大模型分层配置（本地优先 · 可选混合/云端）----
    private final JComboBox<String> modelBackendCombo = new JComboBox<>();
    private final JTextField localModelField = Ui.textField(18);
    private final JButton refreshModelsBtn = Ui.secondary("");
    private final JLabel localModelsLabel = Ui.muted(" ");
    private final JTextField cloudUrlField = Ui.textField(32);
    private final JPasswordField cloudKeyField = new JPasswordField(18);
    private final JTextField cloudModelField = Ui.textField(20);
    private final JCheckBox cloudConsent = new JCheckBox("");
    private final JCheckBox cloudTsAuto = new JCheckBox("");
    private final JButton modelSaveBtn = Ui.button("");
    private final JLabel modelStatus = Ui.muted(" ");
    private final JLabel modelCfgPathLabel = Ui.muted(" ");

    SettingsPage(ApiClient api, Runnable onServerChange) {
        super(api);
        this.onServerChange = onServerChange;
        setLayout(new BorderLayout(0, 10));
        backendCombo.setFont(Ui.FONT);
        modelBackendCombo.setFont(Ui.FONT);
        sshBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Ui.LINE), "",
                TitledBorder.LEFT, TitledBorder.TOP, Ui.FONT_BOLD, Ui.TEXT);
        sshPass.setFont(Ui.FONT);
        sshQueue.setFont(Ui.FONT);
        sshQueueName.setFont(Ui.FONT);
        sshG16Root.setFont(Ui.FONT);
        sshG16Scratch.setFont(Ui.FONT);
        cloudKeyField.setFont(Ui.FONT);
        Langs.addListener(this::applyLang);
        applyLang();

        // ===== 顶部：健康状态 + 服务连接 =====
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(buildHealthCard(), BorderLayout.NORTH);
        top.add(buildConnCard(), BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        // ===== 中部（可滚动）：大模型配置 + DFT 计算后端配置 + 关于 =====
        JPanel scrollBody = new JPanel(new BorderLayout(0, 10));
        scrollBody.setOpaque(false);
        scrollBody.add(buildModelCard(), BorderLayout.NORTH);
        scrollBody.add(buildDftCard(), BorderLayout.CENTER);
        scrollBody.add(buildAboutCard(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(scrollBody);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        add(scroll, BorderLayout.CENTER);

        refreshBtn.addActionListener(e -> refresh());
        saveBtn.addActionListener(e -> {
            api.setBase(urlField.getText().trim());
            urlField.setText(api.getBase());
            try {
                Config.saveServerUrl(api.getBase());
            } catch (Exception ex) {
                // 保存失败不阻塞
            }
            if (onServerChange != null) onServerChange.run();
            refresh();
        });
        dftSaveBtn.addActionListener(e -> saveDft());
        modelSaveBtn.addActionListener(e -> saveModel());
        refreshModelsBtn.addActionListener(e -> refreshLocalModels());
    }

    private void applyLang() {
        Ui.setCardText(healthCard, Langs.t("set.healthTitle"));
        Ui.setCardText(connCard, Langs.t("set.connTitle"));
        Ui.setCardText(dftCard, Langs.t("set.dftTitle"));
        Ui.setCardText(modelCard, Langs.t("set.modelTitle"));
        Ui.setCardText(aboutCard, Langs.t("set.aboutTitle"));
        refreshBtn.setText(Langs.t("set.refresh"));
        saveBtn.setText(Langs.t("set.saveUrl"));
        dftSaveBtn.setText(Langs.t("set.saveDftCfg"));
        modelSaveBtn.setText(Langs.t("set.saveModelCfg"));
        refreshModelsBtn.setText(Langs.t("set.refreshModels"));
        cloudConsent.setText(Langs.t("set.cloudConsent"));
        cloudTsAuto.setText(Langs.t("set.cloudTsAuto"));
        sshBorder.setTitle(Langs.t("set.sshTitle"));
        for (int i = 0; i < healthNameLabels.size(); i++) {
            if (i < HEALTH_KEYS.length)
                healthNameLabels.get(i).setText(Langs.t(HEALTH_KEYS[i]));
        }

        if (backendCombo.getItemCount() == 3) {
            backendCombo.setSelectedIndex(prefBackendIdx);
        } else {
            backendCombo.removeAllItems();
            backendCombo.addItem(Langs.t("set.backendLocalG16"));
            backendCombo.addItem(Langs.t("set.backendLocalOrca"));
            backendCombo.addItem(Langs.t("set.backendCluster"));
            backendCombo.setSelectedIndex(Math.min(prefBackendIdx, 2));
        }
        if (modelBackendCombo.getItemCount() == 3) {
            modelBackendCombo.setSelectedIndex(prefModelIdx);
        } else {
            modelBackendCombo.removeAllItems();
            modelBackendCombo.addItem(Langs.t("set.modelLocal"));
            modelBackendCombo.addItem(Langs.t("set.modelHybrid"));
            modelBackendCombo.addItem(Langs.t("set.modelCloud"));
            modelBackendCombo.setSelectedIndex(Math.min(prefModelIdx, 2));
        }
    }

    // 语言切换时保持用户已选后端/模型索引
    private static int prefBackendIdx = 0;
    private static int prefModelIdx = 0;

    // ---------- 卡片构建 ----------

    private JPanel buildHealthCard() {
        healthCard.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 12, 4, 8);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.gridwidth = 2;
        String[][] rows = {
                {"set.theServer", "server"},
                {"set.ollama", "ollama"},
                {"set.modelReady", "model_ready"},
                {"set.molopt", "molopt"},
                {"set.molview", "molview"},
                {"set.rdkit", "rdkit"},
                {"set.kb", "knowledge_base"},
                {"set.dft", "dft"},
                {"set.workflow", "workflow"},
        };
        int r = 0;
        for (String[] row : rows) {
            g.gridy = r;
            JLabel name = Ui.label(Langs.t(row[0]));
            healthNameLabels.add(name);
            healthCard.add(name, g);
            g.gridwidth = 1;
            g.weightx = 0;
            g.gridx = 2;
            JLabel v = new JLabel(Langs.t("set.checking"));
            v.setFont(Ui.FONT_BOLD);
            statusLabels.put(row[1], v);
            healthCard.add(v, g);
            g.gridx = 0;
            g.gridwidth = 2;
            g.weightx = 1;
            r++;
        }
        g.gridy = r;
        g.gridwidth = 3;
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        btns.setOpaque(false);
        btns.add(refreshBtn);
        healthCard.add(btns, g);
        return healthCard;
    }

    private JPanel buildConnCard() {
        connCard.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 8));
        connCard.add(Ui.label(Langs.t("set.url")));
        urlField.setText(api.getBase());
        connCard.add(urlField);
        connCard.add(saveBtn);
        return connCard;
    }

    private JPanel buildDftCard() {
        dftCard.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 12, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        addPathRow(dftCard, g, 0, "set.g16", g16Field, "g16");
        addPathRow(dftCard, g, 1, "set.orca", orcaField, "orca");
        addPathRow(dftCard, g, 2, "set.multiwfn", mwField, "multiwfn");
        addPathRow(dftCard, g, 3, "set.workspace", wsField, "workspace");

        // 默认计算后端
        g.gridy = 4;
        g.gridx = 0;
        g.weightx = 0;
        dftCard.add(Ui.label(Langs.t("set.defaultBackend")), g);
        g.gridx = 1;
        g.weightx = 1;
        JPanel defPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        defPanel.setOpaque(false);
        backendCombo.setToolTipText(Langs.t("set.backendHint"));
        defPanel.add(backendCombo);
        defPanel.add(Ui.muted(Langs.t("set.sshHint2")));
        dftCard.add(defPanel, g);
        g.gridx = 2;
        g.gridwidth = 2;
        g.weightx = 0;
        dftCard.add(new JPanel(), g);
        g.gridwidth = 1;

        // SSH 子面板
        g.gridy = 5;
        g.gridx = 0;
        g.gridwidth = 4;
        g.weightx = 1;
        dftCard.add(buildSshPanel(), g);
        g.gridwidth = 1;

        // 操作按钮 + 状态
        g.gridy = 6;
        g.gridx = 0;
        g.gridwidth = 4;
        JPanel dftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        dftBtns.setOpaque(false);
        dftBtns.add(dftSaveBtn);
        dftCard.add(dftBtns, g);

        g.gridy = 7;
        g.anchor = GridBagConstraints.WEST;
        dftStatus.setFont(Ui.FONT_BOLD);
        dftCard.add(dftStatus, g);

        g.gridy = 8;
        dftCard.add(cfgPathLabel, g);
        g.gridwidth = 1;
        return dftCard;
    }

    /** 一行：标签 + 路径输入框 + 浏览 + 测试 */
    private void addPathRow(JPanel p, GridBagConstraints g, int row, String labelKey,
                            JTextField field, String testTarget) {
        g.gridy = row;
        g.gridx = 0;
        g.weightx = 0;
        p.add(Ui.label(Langs.t(labelKey)), g);
        g.gridx = 1;
        g.weightx = 1;
        field.setToolTipText(Langs.t("set.pathHint"));
        p.add(field, g);
        g.gridx = 2;
        g.weightx = 0;
        JButton browse = Ui.secondary(Langs.t("set.browse"));
        browse.addActionListener(e -> choosePath(field, testTarget.equals("workspace")));
        p.add(browse, g);
        g.gridx = 3;
        JButton test = Ui.secondary(Langs.t("set.test"));
        test.addActionListener(e -> testDft(testTarget));
        p.add(test, g);
    }

    private JPanel buildSshPanel() {
        JPanel ssh = new JPanel(new GridBagLayout());
        ssh.setBackground(Ui.CARD);
        ssh.setBorder(sshBorder);

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 10, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridy = 0;
        g.gridx = 0;
        ssh.add(Ui.label(Langs.t("set.sshHost")), g);
        g.gridx = 1;
        sshHost.setToolTipText("192.168.1.100 · cluster.example.edu");
        ssh.add(sshHost, g);
        g.gridx = 2;
        ssh.add(Ui.label(Langs.t("set.sshUser")), g);
        g.gridx = 3;
        ssh.add(sshUser, g);
        g.gridx = 4;
        ssh.add(Ui.label(Langs.t("set.sshPass")), g);
        g.gridx = 5;
        sshPass.setToolTipText(null);
        ssh.add(sshPass, g);

        g.gridy = 1;
        g.gridx = 0;
        ssh.add(Ui.label(Langs.t("set.sshKey")), g);
        g.gridx = 1;
        sshKey.setToolTipText(null);
        ssh.add(sshKey, g);
        g.gridx = 2;
        JButton keyBrowse = Ui.secondary(Langs.t("set.browse"));
        keyBrowse.addActionListener(e -> choosePath(sshKey, false));
        ssh.add(keyBrowse, g);
        g.gridx = 3;
        ssh.add(Ui.label(Langs.t("set.sshDir")), g);
        g.gridx = 4;
        sshDir.setToolTipText(null);
        ssh.add(sshDir, g);
        g.gridx = 5;
        ssh.add(Ui.label(Langs.t("set.sshQueue")), g);
        g.gridx = 6;
        ssh.add(sshQueue, g);

        g.gridy = 2;
        g.gridx = 0;
        ssh.add(Ui.label(Langs.t("set.sshQueueName")), g);
        g.gridx = 1;
        sshQueueName.setToolTipText(null);
        ssh.add(sshQueueName, g);
        g.gridx = 2;
        ssh.add(Ui.label(Langs.t("set.sshG16Root")), g);
        g.gridx = 3;
        sshG16Root.setToolTipText(null);
        ssh.add(sshG16Root, g);
        g.gridx = 4;
        ssh.add(Ui.label(Langs.t("set.sshScratch")), g);
        g.gridx = 5;
        sshG16Scratch.setToolTipText(null);
        ssh.add(sshG16Scratch, g);

        g.gridy = 3;
        g.gridx = 0;
        g.gridwidth = 7;
        JPanel sshBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        sshBtns.setOpaque(false);
        JButton sshTest = Ui.secondary(Langs.t("set.sshTest"));
        sshTest.addActionListener(e -> testDft("ssh"));
        sshBtns.add(sshTest);
        sshBtns.add(Ui.muted(Langs.t("set.sshHint")));
        ssh.add(sshBtns, g);
        return ssh;
    }

    private JPanel buildModelCard() {
        modelCard.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 12, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridy = 0;
        g.gridx = 0;
        g.weightx = 0;
        modelCard.add(Ui.label(Langs.t("set.modelBackend")), g);
        g.gridx = 1;
        g.weightx = 1;
        modelCard.add(modelBackendCombo, g);
        g.gridx = 2;
        g.gridwidth = 3;
        modelCard.add(Ui.muted(Langs.t("set.modelHint")), g);
        g.gridwidth = 1;

        g.gridy = 1;
        g.gridx = 0;
        g.weightx = 0;
        modelCard.add(Ui.label(Langs.t("set.localModel")), g);
        g.gridx = 1;
        g.weightx = 1;
        localModelField.setToolTipText(Langs.t("set.pathHint"));
        modelCard.add(localModelField, g);
        g.gridx = 2;
        g.weightx = 0;
        modelCard.add(refreshModelsBtn, g);
        g.gridx = 3;
        g.gridwidth = 2;
        modelCard.add(localModelsLabel, g);
        g.gridwidth = 1;

        g.gridy = 2;
        g.gridx = 0;
        modelCard.add(Ui.label(Langs.t("set.cloudUrl")), g);
        g.gridx = 1;
        g.gridwidth = 4;
        modelCard.add(cloudUrlField, g);
        g.gridwidth = 1;

        g.gridy = 3;
        g.gridx = 0;
        modelCard.add(Ui.label(Langs.t("set.cloudKey")), g);
        g.gridx = 1;
        modelCard.add(cloudKeyField, g);
        g.gridx = 2;
        modelCard.add(Ui.label(Langs.t("set.cloudModel")), g);
        g.gridx = 3;
        modelCard.add(cloudModelField, g);
        g.gridx = 4;
        g.gridwidth = 2;
        modelCard.add(Ui.muted(Langs.t("set.cloudOnlyAuth")), g);
        g.gridwidth = 1;

        g.gridy = 4;
        g.gridx = 0;
        g.gridwidth = 5;
        modelCard.add(cloudConsent, g);
        g.gridwidth = 1;

        g.gridy = 5;
        g.gridx = 0;
        g.gridwidth = 5;
        modelCard.add(cloudTsAuto, g);
        g.gridwidth = 1;

        g.gridy = 6;
        g.gridx = 0;
        g.gridwidth = 5;
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        btns.setOpaque(false);
        btns.add(modelSaveBtn);
        modelCard.add(btns, g);
        g.gridwidth = 1;

        g.gridy = 7;
        g.gridx = 0;
        modelStatus.setFont(Ui.FONT_BOLD);
        modelCard.add(modelStatus, g);

        g.gridy = 8;
        g.gridx = 0;
        modelCard.add(modelCfgPathLabel, g);
        return modelCard;
    }

    private JPanel buildAboutCard() {
        aboutCard.setLayout(new BorderLayout());
        JLabel arch = new JLabel("<html><div style='padding:4px 8px;line-height:170%'>"
                + Langs.t("set.aboutHtml") + "</div></html>");
        arch.setFont(Ui.FONT);
        aboutCard.add(arch, BorderLayout.CENTER);
        aboutCard.setPreferredSize(new Dimension(0, 180));
        return aboutCard;
    }

    // ---------- 交互 ----------

    private void choosePath(JTextField field, boolean directory) {
        String cur = field.getText().trim();
        JFileChooser fc = new JFileChooser(cur.isEmpty() ? null : cur);
        fc.setFileSelectionMode(directory ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        fc.setDialogTitle(directory ? Langs.t("set.chooseWs") : Langs.t("set.chooseProg"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            field.setText(fc.getSelectedFile().getAbsolutePath());
    }

    @Override
    public void onShow() {
        refresh();
        loadDft();
        loadModel();
    }

    private void refresh() {
        statusLabels.values().forEach(l -> l.setText(Langs.t("set.checking")));
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.get("/api/health");
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> h = Json.obj(get());
                    set("server", true);
                    set("ollama", Json.bool(h.get("ollama")));
                    set("model_ready", Json.bool(h.get("model_ready")));
                    set("molopt", Json.bool(h.get("molopt")));
                    set("molview", Json.bool(h.get("molview")));
                    set("rdkit", Json.bool(h.get("rdkit")));
                    set("knowledge_base", Json.bool(h.get("knowledge_base")));
                    set("workflow", Json.bool(h.get("workflow")));
                    List<Object> backends = Json.arr(h.get("dft_backends"));
                    if (Json.bool(h.get("dft")) && !backends.isEmpty()) {
                        StringBuilder sb = new StringBuilder(Langs.t("set.ok") + " · ");
                        for (int i = 0; i < backends.size(); i++) {
                            if (i > 0) sb.append(" / ");
                            sb.append(backends.get(i));
                        }
                        JLabel l = statusLabels.get("dft");
                        l.setText(sb.toString());
                        l.setForeground(Ui.OK);
                    } else {
                        set("dft", false);
                    }
                } catch (Exception e) {
                    statusLabels.forEach((k, l) -> {
                        l.setText(Langs.t("set.unreach"));
                        l.setForeground(Ui.BAD);
                    });
                }
            }
        }.execute();
    }

    private void set(String key, boolean ok) {
        JLabel l = statusLabels.get(key);
        if (l == null) return;
        l.setText(ok ? Langs.t("set.ok") : Langs.t("set.bad"));
        l.setForeground(ok ? Ui.OK : Ui.BAD);
    }

    /** 从服务层加载已保存的 DFT 配置并填充表单 */
    private void loadDft() {
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.get("/api/dft_config");
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> res = Json.obj(get());
                    Map<String, Object> cfg = Json.obj(res.get("config"));
                    g16Field.setText(Json.str(cfg.get("g16_command")));
                    orcaField.setText(Json.str(cfg.get("orca_command")));
                    mwField.setText(Json.str(cfg.get("multiwfn_command")));
                    wsField.setText(Json.str(cfg.get("workspace")));
                    sshHost.setText(Json.str(cfg.get("ssh_host")));
                    sshUser.setText(Json.str(cfg.get("ssh_username")));
                    sshKey.setText(Json.str(cfg.get("ssh_key")));
                    sshDir.setText(Json.str(cfg.get("ssh_work_dir")));
                    String q = Json.str(cfg.get("ssh_queue"));
                    if (!q.isEmpty()) sshQueue.setSelectedItem(q);
                    String def = Json.str(cfg.get("default_backend"));
                    for (int i = 0; i < BACKEND_KEYS.length; i++)
                        if (BACKEND_KEYS[i].equals(def)) {
                            prefBackendIdx = i;
                            backendCombo.setSelectedIndex(i);
                        }
                    String p = Json.str(res.get("config_path"));
                    cfgPathLabel.setText(Langs.t("set.cfgPath", p));
                    List<Object> live = Json.arr(res.get("backends"));
                    if (!live.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < live.size(); i++) {
                            if (i > 0) sb.append(" / ");
                            sb.append(live.get(i));
                        }
                        dftStatus.setText(Langs.t("set.liveBackends", sb.toString(), def));
                        dftStatus.setForeground(Ui.MUTED);
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    /** 保存并热应用 DFT 配置 */
    private void saveDft() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("g16_command", g16Field.getText().trim());
        body.put("orca_command", orcaField.getText().trim());
        body.put("multiwfn_command", mwField.getText().trim());
        body.put("workspace", wsField.getText().trim());
        body.put("default_backend", BACKEND_KEYS[backendCombo.getSelectedIndex()]);
        body.put("ssh_host", sshHost.getText().trim());
        body.put("ssh_username", sshUser.getText().trim());
        body.put("ssh_password", new String(sshPass.getPassword()));
        body.put("ssh_key", sshKey.getText().trim());
        body.put("ssh_work_dir", sshDir.getText().trim());
        body.put("ssh_queue", (String) sshQueue.getSelectedItem());
        body.put("ssh_queue_name", sshQueueName.getText().trim());
        body.put("ssh_g16_root", sshG16Root.getText().trim());
        body.put("ssh_g16_scratch", sshG16Scratch.getText().trim());
        prefBackendIdx = backendCombo.getSelectedIndex();

        dftSaveBtn.setEnabled(false);
        setDftStatus(true, Langs.t("set.savingDft"));
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.post("/api/dft_config", body);
            }

            @Override
            protected void done() {
                dftSaveBtn.setEnabled(true);
                try {
                    Map<String, Object> res = Json.obj(get());
                    List<Object> backends = Json.arr(res.get("backends"));
                    String def = Json.str(res.get("default_backend"));
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < backends.size(); i++) {
                        if (i > 0) sb.append(" / ");
                        sb.append(backends.get(i));
                    }
                    String msg = Langs.t("set.savedDft", sb.toString(), def);
                    if (!Json.bool(res.get("default_applied")))
                        msg += Langs.t("set.savedFallback");
                    setDftStatus(true, msg);
                } catch (Exception e) {
                    setDftStatus(false, Langs.t("set.saveDftFail", e.getMessage()));
                }
            }
        }.execute();
    }

    /** 测试连通性：g16 / orca / multiwfn / workspace / ssh */
    private void testDft(String target) {
        setDftStatus(true, Langs.t("set.testing", target));
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("target", target);
                return api.post("/api/dft_test", body);
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> res = Json.obj(get());
                    boolean ok = Json.bool(res.get("ok"));
                    setDftStatus(ok, (ok ? "✔ " : "✘ ") + Json.str(res.get("detail")));
                } catch (Exception e) {
                    setDftStatus(false, Langs.t("set.testFail", e.getMessage()));
                }
            }
        }.execute();
    }

    private void setDftStatus(boolean ok, String text) {
        dftStatus.setText(text);
        dftStatus.setForeground(ok ? Ui.OK : Ui.BAD);
    }

    // ---------- 大模型分层配置 ----------

    /** 从服务层加载已保存的模型配置并填充表单 */
    private void loadModel() {
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.get("/api/model_config");
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> res = Json.obj(get());
                    Map<String, Object> cfg = Json.obj(res.get("config"));
                    String bk = Json.str(cfg.get("model_backend"));
                    for (int i = 0; i < MODEL_BACKEND_KEYS.length; i++)
                        if (MODEL_BACKEND_KEYS[i].equals(bk)) {
                            prefModelIdx = i;
                            modelBackendCombo.setSelectedIndex(i);
                        }
                    localModelField.setText(Json.str(cfg.get("local_model")));
                    cloudUrlField.setText(Json.str(cfg.get("cloud_base_url")));
                    String key = Json.str(cfg.get("cloud_api_key"));
                    cloudKeyField.setText(key);
                    cloudModelField.setText(Json.str(cfg.get("cloud_model")));
                    cloudConsent.setSelected(Json.bool(cfg.get("cloud_privacy_consent")));
                    cloudTsAuto.setSelected(Json.bool(cfg.get("cloud_ts_auto")));
                    String p = Json.str(res.get("config_path"));
                    modelCfgPathLabel.setText(Langs.t("set.cfgPath", p));
                    setModelStatus(true, Langs.t("set.currentBackend", bk,
                            bk.equals("local") ? Langs.t("set.dataLocal") : Langs.t("set.dataCloud")));
                    refreshLocalModels();
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    /** 刷新并展示本地 Ollama 已下载的模型列表 */
    private void refreshLocalModels() {
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.get("/api/models");
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> res = Json.obj(get());
                    List<Object> names = Json.arr(res.get("local_models"));
                    if (names.isEmpty()) {
                        localModelsLabel.setText(Langs.t("set.localNoModels"));
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < names.size(); i++) {
                            if (i > 0) sb.append(" / ");
                            sb.append(names.get(i));
                        }
                        localModelsLabel.setText(Langs.t("set.downloaded", sb.toString()));
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    /** 保存并热应用模型配置 */
    private void saveModel() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model_backend", MODEL_BACKEND_KEYS[modelBackendCombo.getSelectedIndex()]);
        body.put("local_model", localModelField.getText().trim());
        body.put("cloud_base_url", cloudUrlField.getText().trim());
        body.put("cloud_api_key", new String(cloudKeyField.getPassword()));
        body.put("cloud_model", cloudModelField.getText().trim());
        body.put("cloud_privacy_consent", cloudConsent.isSelected());
        body.put("cloud_ts_auto", cloudTsAuto.isSelected());
        prefModelIdx = modelBackendCombo.getSelectedIndex();

        modelSaveBtn.setEnabled(false);
        setModelStatus(true, Langs.t("set.savingModel"));
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.post("/api/model_config", body);
            }

            @Override
            protected void done() {
                modelSaveBtn.setEnabled(true);
                try {
                    Map<String, Object> res = Json.obj(get());
                    Map<String, Object> cfg = Json.obj(res.get("config"));
                    String bk = Json.str(cfg.get("model_backend"));
                    setModelStatus(true, Langs.t("set.savedModel", bk,
                            bk.equals("local") ? Langs.t("set.dataLocal") : Langs.t("set.dataCloud")));
                    refreshLocalModels();
                } catch (Exception e) {
                    setModelStatus(false, Langs.t("set.saveModelFail", e.getMessage()));
                }
            }
        }.execute();
    }

    private void setModelStatus(boolean ok, String text) {
        modelStatus.setText(text);
        modelStatus.setForeground(ok ? Ui.OK : Ui.BAD);
    }
}