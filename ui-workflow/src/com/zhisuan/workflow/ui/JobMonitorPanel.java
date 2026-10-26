package com.zhisuan.workflow.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.zhisuan.workflow.model.NodeStatus;

/**
 * 「任务进程」标签页：轮询 /api/workflow/jobs 展示跨 WSL/容器/本地/集群的任务；
 * 选中任务时拉取 /api/workflow/structure，在右侧球棍渲染其提交时分子结构。
 */
public class JobMonitorPanel extends JPanel {

    private static final int POLL_MS = 3000;

    /** 默认后端地址：与 Main 保持一致（可用 -Dzhisuan.backend 覆盖）。 */
    static final String DEFAULT_BACKEND_URL =
            System.getProperty("zhisuan.backend", "http://127.0.0.1:8765");

    private final String baseUrl;
    private final DefaultListModel<JobItem> model = new DefaultListModel<>();
    private final MolRenderPanel mol = new MolRenderPanel();
    private final JLabel statusLabel = new JLabel("● 正在连接");
    private final JLabel summary = new JLabel("选择任务查看结构");
    private final Timer timer;

    public JobMonitorPanel(String baseUrl, int timeoutMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        setBackground(Theme.PANEL);
        setOpaque(true);
        setLayout(new BorderLayout());

        JLabel title = new JLabel("任务进程",
                SwingConstants.LEFT);
        title.setFont(Theme.uiFontBold(13));
        title.setForeground(Theme.TEXT_PRIMARY);
        statusLabel.setFont(Theme.uiFont(11));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        topBar.setBackground(Theme.PANEL);
        topBar.add(title);
        topBar.add(statusLabel);

        JList<JobItem> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new JobCell());
        list.setBackground(Theme.PANEL);
        list.setBorder(new EmptyBorder(4, 6, 4, 6));
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            JobItem sel = list.getSelectedValue();
            if (sel == null) {
                mol.clear();
                summary.setText("选择任务查看结构");
                return;
            }
            summary.setText(sel.program + " · " + statusText(sel.status));
            loadStructure(sel.id);
        });
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));
        listScroll.setPreferredSize(new Dimension(380, 0));

        summary.setFont(Theme.uiFontBold(12));
        summary.setForeground(Theme.TEXT_PRIMARY);
        summary.setBorder(new EmptyBorder(6, 10, 4, 10));

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(Theme.PANEL);
        right.setOpaque(true);
        right.add(summary, BorderLayout.NORTH);
        right.add(mol, BorderLayout.CENTER);

        add(topBar, BorderLayout.NORTH);
        add(listScroll, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);

        // 轮询：后台线程拉取，EDT 更新列表
        timer = new Timer(POLL_MS, ev -> refreshAsync(timeoutMs));
        timer.setInitialDelay(200);
        timer.start();
    }

    /** 停止定时轮询（窗口关闭时释放定时器）。 */
    public void stop() {
        if (timer != null) {
            timer.stop();
        }
    }

    private void refreshAsync(int timeoutMs) {
        Thread t = new Thread(() -> {
            String json = StatusClient.httpGet(baseUrl + "/api/workflow/jobs", timeoutMs);
            List<JobItem> items = parseJobs(json);
            SwingUtilities.invokeLater(() -> applyItems(items, json != null));
        });
        t.setDaemon(true);
        t.start();
    }

    private void applyItems(List<JobItem> items, boolean connected) {
        statusLabel.setForeground(connected ? Theme.STATUS_COMPLETED : Theme.STATUS_FAILED);
        statusLabel.setText(connected ? "● 已连接 · " + items.size() + " 个任务"
                : "○ 离线 · 等待后端");
        if (!connected) {
            if (model.isEmpty()) {
                mol.setMessage("后端未连接，无法获取任务进程");
            }
            return;
        }
        if (model.size() != items.size()) {
            model.clear();
        }
        for (int i = 0; i < items.size(); i++) {
            if (i < model.size()) {
                model.set(i, items.get(i));
            } else {
                model.addElement(items.get(i));
            }
        }
        while (model.size() > items.size()) {
            model.remove(model.size() - 1);
        }
        repaint();
    }

    private void loadStructure(String taskId) {
        Thread t = new Thread(() -> {
            String json = StatusClient.httpGet(
                    baseUrl + "/api/workflow/structure?task_id=" + encode(taskId), 3000);
            SwingUtilities.invokeLater(() -> applyStructure(json));
        });
        t.setDaemon(true);
        t.start();
    }

    private void applyStructure(String json) {
        if (json == null) {
            mol.setMessage("获取结构失败");
            return;
        }
        try {
            Object root = MiniJson.parse(json);
            Map<String, Object> o = MiniJson.asObject(root);
            Boolean ok = Boolean.TRUE.equals(o.get("ok"));
            if (!ok) {
                mol.setMessage(MiniJson.asString(o.get("error")));
                return;
            }
            Object symObj = o.get("symbols");
            Object crdObj = o.get("coords");
            Object bndObj = o.get("bonds");
            List<String> symbols = new ArrayList<>();
            List<double[]> coords = new ArrayList<>();
            List<int[]> bonds = new ArrayList<>();
            if (symObj instanceof List) {
                for (Object s : (List<?>) symObj) {
                    symbols.add(String.valueOf(s));
                }
            }
            if (crdObj instanceof List) {
                for (Object row : (List<?>) crdObj) {
                    if (!(row instanceof List)) {
                        continue;
                    }
                    double[] p = new double[3];
                    int idx = 0;
                    for (Object v : (List<?>) row) {
                        if (idx < 3 && v instanceof Number) {
                            p[idx++] = ((Number) v).doubleValue();
                        }
                    }
                    coords.add(p);
                }
            }
            if (bndObj instanceof List) {
                for (Object row : (List<?>) bndObj) {
                    if (!(row instanceof List)) {
                        continue;
                    }
                    int[] b = new int[2];
                    int idx = 0;
                    for (Object v : (List<?>) row) {
                        if (idx < 2 && v instanceof Number) {
                            b[idx++] = ((Number) v).intValue();
                        }
                    }
                    bonds.add(b);
                }
            }
            mol.setStructure(symbols, coords, bonds);
        } catch (Exception ex) {
            mol.setMessage("结构解析失败");
        }
    }

    private static List<JobItem> parseJobs(String json) {
        List<JobItem> out = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return out;
        }
        try {
            Object root = MiniJson.parse(json);
            Object jobsObj = MiniJson.asObject(root).get("jobs");
            if (!(jobsObj instanceof List)) {
                return out;
            }
            for (Object j : (List<?>) jobsObj) {
                if (!(j instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) j;
                JobItem it = new JobItem();
                it.id = MiniJson.asString(m.get("id"));
                it.label = MiniJson.asString(m.get("label"));
                it.tool = MiniJson.asString(m.get("tool"));
                it.program = MiniJson.asString(m.get("program"));
                it.status = MiniJson.asString(m.get("status"));
                it.error = MiniJson.asString(m.get("error"));
                out.add(it);
            }
        } catch (Exception ignored) {
            // 解析失败返回已收集部分
        }
        return out;
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    static String statusText(String raw) {
        switch (NodeRemoteState.parseStatus(raw)) {
            case RUNNING: return "运行中";
            case COMPLETED: return "完成";
            case FAILED: return "失败";
            case RERUN: return "中断/需重跑";
            case CANCELLED: return "已取消";
            case WAITING:
            default: return "排队";
        }
    }

    private static final class JobCell extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JobItem j = (JobItem) value;
            String html = "<html><div style='padding:3px'>"
                    + "<b style='color:" + rgb(Theme.TEXT_PRIMARY) + ";font-size:12px'>"
                    + esc(sc(j.program)) + "</b>"
                    + "&nbsp;<span style='color:" + rgb(Theme.TEXT_SECONDARY) + ";font-size:10px'>"
                    + esc(sc(j.id)) + "</span><br>"
                    + "<span style='font-size:10px'>" + esc(sc(statusText(j.status))) + "</span>"
                    + "&nbsp;<span style='color:" + rgb(Theme.TEXT_SECONDARY) + ";font-size:10px'>"
                    + esc(sc(j.label)) + "</span>"
                    + "</div></html>";
            JLabel l = new JLabel(html);
            l.setOpaque(true);
            l.setBackground(isSelected ? Theme.PANEL_RAISED : Theme.PANEL);
            l.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
            return l;
        }
    }

    private static String sc(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String rgb(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static class JobItem {
        String id = "", label = "", tool = "", program = "", status = "", error = "";
    }
}