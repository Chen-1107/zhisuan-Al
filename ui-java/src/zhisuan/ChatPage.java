package zhisuan;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.html.HTMLDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Desktop;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 助手页：Markdown 富文本渲染 + 附件卡片 + 异步任务监控自动恢复对话。
 *
 * 机制说明（与 agent_server.py 配合）：
 * - 工具结果中的文件路径 → 渲染为附件卡片（点击可用系统程序打开）
 * - 工具结果中的 task_id → 注册轮询定时器监控后台任务；
 *   任务完成后自动以 [系统] 消息恢复对话（携带完整上下文与任务结果）。
 */
public class ChatPage extends Page {
    private final JTextPane pane = new JTextPane();
    private final javax.swing.JTextArea input = new javax.swing.JTextArea(2, 30);
    private final JToggleButton agentMode = new JToggleButton(Langs.t("chat.agent"));
    private final JButton sendBtn = Ui.button(Langs.t("chat.send"));
    private final JButton clearBtn = Ui.secondary(Langs.t("chat.clear"));
    private final JLabel hintLabel = Ui.muted(Langs.t("chat.hint"));
    private JLabel attachHintLabel;
    private JButton attachBtn;
    private final List<Map<String, String>> history = new ArrayList<>();
    private final StringBuilder streamBuf = new StringBuilder();
    private volatile boolean busy = false;

    /** 当前流式回复在文档中的起始偏移（完成后删除纯文本并替换为 Markdown 渲染） */
    private int streamStart = -1;

    // ---------- 打字机效果（服务端 chunk 整段一次性到达，GUI 端定时器逐字上屏） ----------
    /** 待上屏的原子 HTML 片段队列（每片段自身合法：单字符 / 完整转义串 / <br>） */
    private final ArrayDeque<String> typingQueue = new ArrayDeque<>();
    private Timer typerTimer;
    /** done 事件已到：等打字完成后自动收尾（渲染 Markdown + 解锁输入） */
    private boolean finishPending = false;
    private boolean pendingAgent;
    private static final int TYPE_DELAY_MS = 18;
    private static final int TYPE_BATCH = 4;
    /** 活跃任务监控：taskId → 轮询定时器 */
    private final Map<String, Timer> taskTimers = new LinkedHashMap<>();
    /** 任务轮询连续失败计数：taskId → 次数（成功后清零，≥2 次在界面提示） */
    private final Map<String, Integer> taskFails = new LinkedHashMap<>();

    private static final Pattern TASK_ID_RE =
            Pattern.compile("task_id:\\s*(zs-task-[A-Za-z0-9-]+)");
    private static final Pattern FILE_RE = Pattern.compile(
            "[A-Za-z]:\\\\[^\\s\"'，。；）)]+?\\.(xyz|png|jpg|jpeg|gif|html?|log|out|fchk|molden|inp|gjf|csv|json|txt|sdf|mol|bmp|webp)",
            Pattern.CASE_INSENSITIVE);

    private static final String CSS = "<style>"
            + "body { font-family: 微软雅黑; font-size: 13px; color: #dce7ff; background-color:#0d172e; margin: 4px 8px; }"
            + "p { margin: 4px 0 8px 0; }"
            + "code { font-family: Consolas, Dialog; color: #ff8ad8; }"
            + "blockquote { color: #8497bd; font-style: italic; margin-left: 12px; border-left: 2px solid #4a5d7f; }"
            + "h1 { font-size: 18px; font-weight: bold; margin: 8px 0 6px 0; color:#3ce0ff; }"
            + "h2 { font-size: 16px; font-weight: bold; margin: 8px 0 4px 0; color:#3ce0ff; }"
            + "h3 { font-size: 14px; font-weight: bold; margin: 6px 0 4px 0; }"
            + "h4 { font-size: 13px; font-weight: bold; margin: 6px 0 4px 0; }"
            + ".h1 { font-size: 18px; font-weight: bold; margin: 8px 0 6px 0; color: #e2e9ff; }"
            + ".h2 { font-size: 16px; font-weight: bold; margin: 8px 0 4px 0; color: #e2e9ff; }"
            + ".h3 { font-size: 14px; font-weight: bold; margin: 6px 0 4px 0; color: #ccd8f5; }"
            + ".h4 { font-size: 13px; font-weight: bold; margin: 6px 0 4px 0; color: #ccd8f5; }"
            + ".codeblock { background-color: #0a1226; margin: 6px 0; border: 1px solid #2a3b5e; }"
            + ".codelang { font-family: Consolas, Dialog; font-size: 11px; color: #94a3b8;"
            + " background-color: #0f1a33; padding: 2px 6px; border-bottom: 1px solid #2a3b5e; }"
            + ".codeblock pre { font-family: Consolas, Dialog; font-size: 12px; color:#bcd0ff; margin: 2px; padding: 2px; }"
            + "table { border: 1px solid #2a3b5e; margin: 4px 0 8px 0; }"
            + "th { background-color: #14213e; color:#dce7ff; padding: 4px 8px; border: 1px solid #2a3b5e; font-weight: bold; }"
            + "td { padding: 3px 8px; border: 1px solid #2a3b5e; }"
            + "pre { font-family: Consolas, Dialog; font-size: 12px; color:#bcd0ff; margin: 2px; }"
            + "img { margin: 4px 0; }"
            + "a { color: #3ce0ff; }"
            + "</style>";

    ChatPage(ApiClient api) {
        super(api);
        setLayout(new BorderLayout(0, 8));

        // 顶部模式栏
        JPanel top = new JPanel(new GridBagLayout());
        top.setBackground(Ui.CARD);
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.LINE), BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0, 4, 0, 4);
        agentMode.setFont(Ui.FONT_BOLD);
        agentMode.setFocusPainted(false);
        agentMode.setToolTipText(Langs.t("chat.agentModeTip"));
        agentMode.addActionListener(e -> agentMode.setBackground(
                agentMode.isSelected() ? Ui.ACCENT : Ui.CARD));
        agentMode.setForeground(agentMode.isSelected() ? Color.WHITE : Ui.TEXT);
        agentMode.setSelected(true);
        agentMode.setBackground(Ui.ACCENT);
        agentMode.setForeground(Color.WHITE);
        gc.gridx = 0;
        gc.anchor = GridBagConstraints.WEST;
        top.add(agentMode, gc);
        top.add(Box.createHorizontalStrut(8), gc);
        top.add(hintLabel, gc);
        gc.weightx = 1;
        top.add(Box.createHorizontalGlue(), gc);
        gc.weightx = 0;
        top.add(clearBtn, gc);
        add(top, BorderLayout.NORTH);

        // 对话区（HTML 渲染）
        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.setBackground(Ui.PANEL);
        pane.addHyperlinkListener(this::onLink);
        appendHtml(CSS);
        appendSystem(Langs.t("chat.welcome"));
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        add(scroll, BorderLayout.CENTER);

        // 输入区
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setBackground(Ui.CARD);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.LINE), BorderFactory.createEmptyBorder(8, 12, 10, 12)));

        // 上传栏（附件：把本地文件路径插入输入框，随本次消息一起发送）
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        topRow.setOpaque(false);
        attachBtn = Ui.secondary(Langs.t("chat.attach"));
        attachBtn.addActionListener(e -> pickFileToAttach());
        topRow.add(attachBtn);
        attachHintLabel = new JLabel(Langs.t("chat.attachHint"));
        attachHintLabel.setFont(Ui.FONT_SMALL);
        attachHintLabel.setForeground(Ui.MUTED);
        topRow.add(attachHintLabel);
        bottom.add(topRow, BorderLayout.NORTH);

        // 圆角输入容器
        input.setFont(Ui.FONT);
        input.setLineWrap(true);
        input.setOpaque(false); // 透出容器的圆角背景
        input.setBorder(new javax.swing.border.EmptyBorder(6, 8, 6, 8));
        Ui.RoundedPanel inputWrap = new Ui.RoundedPanel(Ui.PANEL2, 10);
        inputWrap.setLayout(new BorderLayout());
        inputWrap.add(input, BorderLayout.CENTER);
        input.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isControlDown()) {
                    e.consume();
                    send();
                }
            }
        });
        bottom.add(inputWrap, BorderLayout.CENTER);
        JPanel btns = new JPanel();
        btns.setOpaque(false);
        btns.setLayout(new javax.swing.BoxLayout(btns, javax.swing.BoxLayout.Y_AXIS));
        btns.add(sendBtn);
        sendBtn.addActionListener(e -> send());
        clearBtn.addActionListener(e -> clear());
        bottom.add(btns, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        Langs.addListener(this::refreshLang);
    }

    private void refreshLang() {
        agentMode.setText(Langs.t("chat.agent"));
        agentMode.setToolTipText(Langs.t("chat.agentModeTip"));
        sendBtn.setText(busy ? Langs.t("chat.busy") : Langs.t("chat.send"));
        clearBtn.setText(Langs.t("chat.clear"));
        hintLabel.setText(Langs.t("chat.hint"));
        if (attachBtn != null) attachBtn.setText(Langs.t("chat.attach"));
        if (attachHintLabel != null) attachHintLabel.setText(Langs.t("chat.attachHint"));
    }

    /** 弹出文件选择器，把选中文件的路径附加到输入框 */
    private void pickFileToAttach() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setDialogTitle(Langs.t("chat.attachTitle"));
        if (fc.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        String cur = input.getText();
        java.util.List<String> lines = new ArrayList<>();
        for (java.io.File f : fc.getSelectedFiles()) {
            String line = "[" + f.getName() + "] " + f.getAbsolutePath();
            if (!lines.contains(line)) lines.add(line);
        }
        String join = String.join("\n", lines);
        String text = cur + (cur.isEmpty() || cur.endsWith("\n") ? "" : "\n") + join + "\n";
        input.setText(text);
        input.setCaretPosition(text.length());
        input.requestFocusInWindow();
    }

    @Override
    public void onShow() {
        refreshLang();
        SwingUtilities.invokeLater(() -> input.requestFocusInWindow());
    }

    // ---------- HTML 文档操作 ----------

    private Element bodyElement() {
        HTMLDocument doc = (HTMLDocument) pane.getDocument();
        Element root = doc.getDefaultRootElement();
        for (int i = 0; i < root.getElementCount(); i++) {
            Element el = root.getElement(i);
            if ("body".equals(el.getName())) return el;
        }
        return root;
    }

    private void appendHtml(String html) {
        try {
            HTMLDocument doc = (HTMLDocument) pane.getDocument();
            doc.insertBeforeEnd(bodyElement(), html);
            pane.setCaretPosition(doc.getLength());
        } catch (Exception ignored) {
        }
    }

    private void appendText(String text, String cssClass) {
        String style = cssClass == null ? "" : "class='" + cssClass + "'";
        appendHtml("<div " + style + ">"
                + Markdown.render(text).replace("\n", "<br>") + "</div>");
    }

    private void appendSystem(String text) {
        appendHtml("<div style='color:#64748b;font-style:italic'>"
                + Markdown.render(text).replace("\n", "<br>") + "</div>");
    }

    private void appendUser(String text) {
        appendHtml("<div style='color:#2563eb;font-weight:bold'>" + Langs.t("chat.you")
                + "</div><div style='margin:0 0 8px 0'>"
                + Markdown.render(text) + "</div>");
    }

    /** 附件卡片：从工具结果文本提取文件路径 */
    private void appendAttachments(String toolResult) {
        Matcher m = FILE_RE.matcher(toolResult);
        List<String> shown = new ArrayList<>();
        while (m.find()) {
            String path = m.group();
            if (shown.contains(path)) continue;
            shown.add(path);
            String name = path.substring(path.lastIndexOf('\\') + 1);
            String ext = name.contains(".")
                    ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
            String icon = switch (ext) {
                case "xyz", "sdf", "mol", "inp", "gjf" -> "🧪";
                case "png", "jpg", "jpeg", "gif", "bmp", "webp" -> "🖼";
                case "log", "out", "fchk", "molden" -> "📊";
                default -> "📄";
            };
            appendHtml("<table cellpadding='5' cellspacing='0' bgcolor='#f0f9ff' "
                    + "style='margin:2px 0 6px 0;border:1px solid #bae6fd'>"
                    + "<tr><td>" + icon + "</td><td><b>" + name + "</b>"
                    + "<br><font size='2' color='#64748b'>" + path + "</font></td>"
                    + "<td><a href='zsfile:" + path + "'>" + Langs.t("chat.open") + "</a> &nbsp;"
                    + "<a href='zsdir:" + path + "'>" + Langs.t("chat.openDir") + "</a></td></tr></table>");
            if (shown.size() >= 6) break;
        }
    }

    /** 链接处理：zsfile/zsdir 打开本地文件，http(s) 用浏览器 */
    private void onLink(HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
        String href = e.getDescription();
        if (href == null || href.isEmpty()) return;
        try {
            if (href.startsWith("zsfile:")) {
                File f = new File(href.substring(7));
                if (f.exists()) Desktop.getDesktop().open(f);
                else appendSystem(Langs.t("chat.fileMissing", f.getAbsolutePath()) + "\n");
            } else if (href.startsWith("zsdir:")) {
                File f = new File(href.substring(6));
                File dir = f.exists() ? (f.isDirectory() ? f : f.getParentFile()) : null;
                if (dir != null && dir.exists()) Desktop.getDesktop().open(dir);
            } else if (href.startsWith("http")) {
                Desktop.getDesktop().browse(new java.net.URI(href));
            } else if (href.startsWith("file:")) {
                Desktop.getDesktop().open(new File(new java.net.URI(href)));
            }
        } catch (Exception ex) {
            appendSystem(Langs.t("chat.openFail", ex.getMessage()) + "\n");
        }
    }

    // ---------- 发送 / 流式 / 完成 ----------

    private void setBusy(boolean b) {
        busy = b;
        SwingUtilities.invokeLater(() -> {
            sendBtn.setEnabled(!b);
            sendBtn.setText(b ? Langs.t("chat.busy") : Langs.t("chat.send"));
        });
    }

    private void send() {
        if (busy) return;
        final String text = input.getText().trim();
        if (text.isEmpty()) return;
        input.setText("");
        history.add(Map.of("role", "user", "content", text));
        appendUser(text);
        dispatch();
    }

    /** 发起一次模型调用（普通 / 智能体模式） */
    private void dispatch() {
        boolean agent = agentMode.isSelected();
        setBusy(true);
        if (agent) appendSystem(Langs.t("chat.agentStart"));
        else appendText(Langs.t("chat.assistant"), null);

        streamBuf.setLength(0);
        streamStart = -1;
        stopTyper();
        List<Map<String, String>> toSend = new ArrayList<>(history);
        if (!agent) {
            boolean hasSystem = history.stream().anyMatch(m -> "system".equals(m.get("role")));
            if (!hasSystem) toSend.add(0, Map.of("role", "system", "content", Langs.sysPrompt()));
        }

        api.postSse(agent ? "/api/agent" : "/api/chat", Map.of("messages", toSend),
                new ApiClient.SseListener() {
                    @Override
                    public void onEvent(String type, Map<String, Object> d) {
                        SwingUtilities.invokeLater(() -> handleEvent(type, d, agent));
                    }

                    @Override
                    public void onDone() {
                        SwingUtilities.invokeLater(() -> finishTurn(agent));
                    }

                    @Override
                    public void onError(String message) {
                        SwingUtilities.invokeLater(() -> {
                            appendText("❌ " + message + "\n\n", null);
                            setBusy(false);
                        });
                    }
                });
    }

    private void handleEvent(String type, Map<String, Object> d, boolean agent) {
        try {
            switch (type == null ? "" : type) {
                case "chunk":
                    String piece = Json.str(d.get("content"));
                    streamBuf.append(piece);
                    queueTyping(piece);
                    break;
                case "status":
                    appendSystem("⟳ " + Json.str(d.get("message")) + "\n");
                    break;
                case "tool_call":
                    removeStreamText(); // 删除中间轮残留的流式纯文本
                    streamBuf.setLength(0); // 中间轮文本不进入最终答案
                    appendHtml("<div style='color:#7c3aed;font-weight:bold;margin-top:4px'>" + Langs.t("chat.tool")
                            + Markdown.render(Json.str(d.get("tool"))) + "</div>");
                    appendHtml("<div style='font-family:Consolas;font-size:12px;color:#475569;"
                            + "margin:2px 0 2px 4px'>" + Langs.t("chat.param") + Markdown.render(Json.stringify(d.get("params"))) + "</div>");
                    break;
                case "tool_result":
                    String result = Json.str(d.get("result"));
                    appendHtml("<div style='font-family:Consolas;font-size:12px;color:#475569;"
                            + "margin:2px 0 4px 4px'>" + Langs.t("chat.result")
                            + Markdown.render(result).replace("\n", "<br>") + "</div>");
                    appendAttachments(result);
                    registerTaskMonitor(result);
                    break;
                case "error":
                    appendText("❌ " + Json.str(d.get("message")) + "\n\n", null);
                    break;
                default:
                    break;
            }
        } catch (Exception ex) {
            // 方案C：单条事件的渲染异常不得中断后续事件流（否则对话锁死在"正在思考"）
            System.err.println("[ChatPage] 事件渲染异常(" + type + "): " + ex);
        }
    }

    // ---------- 打字机效果：逐字上屏 ----------

    /** chunk 入队：拆为原子 HTML 片段（完整转义串/<br> 不可再分割，避免分批解析歧义） */
    private void queueTyping(String piece) {
        if (piece == null || piece.isEmpty()) return;
        for (int i = 0; i < piece.length(); i++) {
            char c = piece.charAt(i);
            switch (c) {
                case '\n' -> typingQueue.add("<br>");
                case '&' -> typingQueue.add("&amp;");
                case '<' -> typingQueue.add("&lt;");
                case '>' -> typingQueue.add("&gt;");
                default -> typingQueue.add(String.valueOf(c));
            }
        }
        if (typerTimer == null) typerTimer = new Timer(TYPE_DELAY_MS, e -> typeTick());
        if (!typerTimer.isRunning()) typerTimer.start();
    }

    /** 定时 tick：批量上屏若干原子片段（模拟逐字打字） */
    private void typeTick() {
        if (typingQueue.isEmpty()) {
            finishTypingPhase();
            return;
        }
        try {
            StringBuilder batch = new StringBuilder();
            for (int i = 0; i < TYPE_BATCH && !typingQueue.isEmpty(); i++) {
                batch.append(typingQueue.poll());
            }
            HTMLDocument doc = (HTMLDocument) pane.getDocument();
            if (streamStart < 0) streamStart = doc.getLength();
            doc.insertBeforeEnd(bodyElement(), batch.toString());
            pane.setCaretPosition(doc.getLength());
        } catch (Exception ex) {
            // 渲染失败：丢弃剩余片段直接收尾，不得让定时器循环挂死
            System.err.println("[ChatPage] 打字机渲染异常: " + ex);
            typingQueue.clear();
            finishTypingPhase();
        }
    }

    /** 打字结束：停表；若 done 已在等待则触发收尾 */
    private void finishTypingPhase() {
        if (typerTimer != null) typerTimer.stop();
        if (finishPending) {
            finishPending = false;
            doFinishTurn(pendingAgent);
        }
    }

    private void stopTyper() {
        typingQueue.clear();
        finishPending = false;
        if (typerTimer != null) typerTimer.stop();
    }

    private void finishTurn(boolean agent) {
        if (!typingQueue.isEmpty()) {
            // 打字未完：先打完再收尾，保证打字机效果完整呈现后替换为 Markdown
            pendingAgent = agent;
            finishPending = true;
            return;
        }
        doFinishTurn(agent);
    }

    /** 收尾：删除流式纯文本 → Markdown 整体渲染 → 解锁。任何异常不得锁死对话（方案C） */
    private void doFinishTurn(boolean agent) {
        try {
            String answer = streamBuf.toString().trim();
            removeStreamText();
            if (!answer.isEmpty()) {
                appendHtml("<div style='margin:0 0 10px 0'>" + Markdown.render(answer) + "</div>");
                history.add(Map.of("role", "assistant", "content", answer));
            } else if (agent) {
                appendHtml("<div style='margin:0 0 10px 0'>&nbsp;</div>");
            }
        } catch (Exception ex) {
            System.err.println("[ChatPage] finishTurn 异常: " + ex);
        } finally {
            setBusy(false);
        }
    }

    /** 删除流式期间追加的纯文本（完成后由 Markdown 渲染替换） */
    private void removeStreamText() {
        stopTyper(); // 停止打字机并丢弃未上屏片段（将被替换或本就不入最终答案）
        try {
            HTMLDocument doc = (HTMLDocument) pane.getDocument();
            if (streamStart >= 0 && doc.getLength() > streamStart) {
                doc.remove(streamStart, doc.getLength() - streamStart);
            }
        } catch (Exception ignored) {
            // 方案A：Swing 对中英混排文本的 BIDI 布局计算在 remove 时可抛
            // IndexOutOfBoundsException，必须整体捕获，否则 finishTurn 中断、输入框锁死
        }
        streamStart = -1;
    }

    // ---------- 异步任务监控：完成后自动恢复对话 ----------

    /** 工具结果含 task_id 时启动轮询定时器 */
    private void registerTaskMonitor(String toolResult) {
        Matcher m = TASK_ID_RE.matcher(toolResult);
        List<String> ids = new ArrayList<>();
        while (m.find()) ids.add(m.group(1));
        for (String taskId : ids) {
            if (taskTimers.containsKey(taskId)) continue;
            appendTaskCard(taskId, "running", Langs.t("chat.taskRunning"));
            Timer timer = new Timer(3000, null);
            // Timer.notify 机制：actionPerformed 抛出未捕获异常时 cancelEvent() 不再执行，
            // notify 卡在 true，coalesce 模式下 Timer 会静默失效 —— 必须兜底捕获
            timer.addActionListener(e -> {
                try {
                    pollTask(taskId, timer);
                } catch (Throwable t) {
                    System.err.println("[ChatPage] 任务轮询异常: " + t);
                }
            });
            taskTimers.put(taskId, timer);
            timer.start();
        }
    }

    /** 任务尾部哨兵的文本标记（渲染为 2px 白色文字，视觉不可见；
     *  作用：状态行始终插在哨兵之前，保证它永远不是 body 最后一个元素 ——
     *  最后一个块元素的 endOffset 含幻影换行（比 getLength() 大 1），基于元素范围的删除会越界失败） */
    private static String tailMark(String taskId) {
        return "zs-tail-" + taskId;
    }

    /** 在 body 直接子元素中按文本标记查找（每次从当前元素树搜索，不缓存元素引用——文档编辑会使引用失效） */
    private Element findBodyChildByMark(String marker) {
        HTMLDocument doc = (HTMLDocument) pane.getDocument();
        Element body = bodyElement();
        for (int i = body.getElementCount() - 1; i >= 0; i--) {
            Element el = body.getElement(i);
            try {
                int s = el.getStartOffset();
                int len = Math.min(el.getEndOffset(), doc.getLength()) - s; // 最后一个元素需钳位（幻影换行）
                if (len > 0 && doc.getText(s, len).contains(marker)) return el;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** 任务状态行更新：删除旧状态行（哨兵前一个兄弟），再在哨兵之前插入新行（保持对话流中的原位）。
     *  任何异常不得外抛（done() 异常会连带丢弃同批 SwingWorker 事件，阻断恢复对话）。 */
    private void updateTaskCard(String taskId, String status, String detail) {
        try {
            String color = switch (status) {
                case "done" -> "#16a34a";
                case "failed", "interrupted", "error" -> "#dc2626";
                default -> "#d97706";
            };
            String mark = switch (status) {
                case "done" -> "✔";
                case "failed", "interrupted", "error" -> "⚠";
                default -> "⟳";
            };
            String inner = mark + " 任务 <b>" + taskId + "</b> [" + status + "] "
                    + Markdown.render(detail).replace("\n", " ");
            String lineDiv = "<div style='color:" + color
                    + ";font-size:12px;margin:2px 0 2px 4px'>" + inner + "</div>";
            HTMLDocument doc = (HTMLDocument) pane.getDocument();
            Element tail = findBodyChildByMark(tailMark(taskId));
            if (tail != null) {
                // 旧状态行 = 哨兵的前一个兄弟（首次更新时是黄色任务卡片，不匹配则跳过删除）
                Element body = bodyElement();
                for (int i = 0; i < body.getElementCount(); i++) {
                    if (body.getElement(i) != tail) continue;
                    if (i > 0) {
                        Element old = body.getElement(i - 1);
                        try {
                            int s = old.getStartOffset();
                            int e = old.getEndOffset();
                            if (e <= doc.getLength() && e > s
                                    && doc.getText(s, e - s).contains("任务 " + taskId + " [")) {
                                doc.remove(s, e - s);
                            }
                        } catch (Exception ex) {
                            System.err.println("[ChatPage] 删除旧状态行失败: " + ex);
                        }
                    }
                    break;
                }
                // 重新定位哨兵（前面的删除可能使其引用失效）
                tail = findBodyChildByMark(tailMark(taskId));
            }
            if (tail != null) {
                doc.insertBeforeStart(tail, lineDiv);
                pane.setCaretPosition(doc.getLength());
            } else {
                appendHtml(lineDiv);
            }
        } catch (Throwable t) {
            System.err.println("[ChatPage] updateTaskCard 异常: " + t);
        }
    }

    private void appendTaskCard(String taskId, String status, String detail) {
        appendHtml("<table cellpadding='5' cellspacing='0' bgcolor='#fefce8' "
                + "style='margin:4px 0 6px 0;border:1px solid #fde68a'>"
                + "<tr><td>⏳</td><td><b>" + Langs.t("chat.taskSubmitted") + "</b>"
                + "<br><font size='2' color='#64748b'>" + taskId + "</font></td></tr></table>");
        // 任务专属哨兵：状态行插在它之前
        appendHtml("<div style='font-size:2px;line-height:2px;color:#ffffff'>"
                + tailMark(taskId) + "</div>");
        updateTaskCard(taskId, status, detail);
    }

    private void pollTask(String taskId, Timer timer) {
        new javax.swing.SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return api.get("/api/task/status?id=" + taskId);
            }

            @Override
            protected void done() {
                Map<String, Object> t;
                try {
                    t = Json.obj(get());
                } catch (Exception ex) {
                    // 网络抖动/服务重启：继续轮询，但让失败可见（不再静默吞掉）
                    int fails = taskFails.merge(taskId, 1, Integer::sum);
                    System.err.println("[ChatPage] 任务状态查询失败(" + fails + ") " + taskId + ": " + ex);
                    if (fails >= 2) {
                        updateTaskCard(taskId, "error", Langs.t("chat.connFail", fails));
                    }
                    return;
                }
                taskFails.remove(taskId);
                String status = Json.str(t.get("status"));
                try {
                    switch (status) {
                        case "running" -> updateTaskCard(taskId, "running",
                                Langs.t("chat.runningWith", elapsed(t)));
                        case "done", "failed", "interrupted" -> {
                            timer.stop();
                            taskTimers.remove(taskId);
                            taskFails.remove(taskId);
                            String result = Json.str(t.get("result"));
                            String err = Json.str(t.get("error"));
                            String detail = status.equals("done")
                                    ? (result.isEmpty() ? Langs.t("chat.noOutput") : result)
                                    : (err.isEmpty() ? status : err);
                            updateTaskCard(taskId, status, detail);
                            if (!busy) {
                                resumeConversation(taskId, status, detail);
                            } else {
                                // 对话占用中：稍后由下一次轮询重试（timer 已停，重启）
                                timer.setInitialDelay(4000);
                                timer.start();
                            }
                        }
                        default -> updateTaskCard(taskId, status, "");
                    }
                } catch (Throwable tr) {
                    // done() 抛出会被 SwingWorker 的批量投递机制连带丢弃同批后续任务，必须兜底
                    System.err.println("[ChatPage] 任务状态处理异常 " + taskId + ": " + tr);
                }
            }
        }.execute();
    }

    private String elapsed(Map<String, Object> t) {
        double submitted = Json.num(t.get("submitted_at"));
        if (submitted <= 0) return "?";
        long sec = (long) (System.currentTimeMillis() / 1000.0 - submitted);
        if (sec < 60) return Langs.t("chat.elapsedSec", sec);
        return Langs.t("chat.elapsedMin", sec / 60, sec % 60);
    }

    /** 任务完成 → 自动恢复对话（携带历史上下文与任务结果） */
    private void resumeConversation(String taskId, String status, String detail) {
        boolean ok = "done".equals(status);
        String sys = (ok ? Langs.t("chat.sysDone", taskId) : Langs.t("chat.sysFail", taskId)) + "\n"
                + detail + "\n"
                + (ok ? Langs.t("chat.rsmOkCtrl") : Langs.t("chat.rsmFailCtrl"));
        history.add(Map.of("role", "user", "content", sys));
        appendHtml("<table cellpadding='5' cellspacing='0' bgcolor='" + (ok ? "#f0fdf4" : "#fef2f2") + "' "
                + "style='margin:6px 0 6px 0;border:1px solid " + (ok ? "#bbf7d0" : "#fecaca") + "'>"
                + "<tr><td>" + (ok ? "✅" : "⚠️") + "</td><td><b>"
                + (ok ? Langs.t("chat.rsmOkTitle") : Langs.t("chat.rsmFailTitle")) + "</b>"
                + "<br><font size='2' color='#64748b'>"
                + (ok ? Langs.t("chat.rsmOkHint") : Langs.t("chat.rsmFailHint"))
                + "</font></td></tr></table>");
        dispatch();
    }

    private void clear() {
        if (busy) return;
        for (Timer t : taskTimers.values()) t.stop();
        taskTimers.clear();
        taskFails.clear();
        history.clear();
        try {
            pane.getDocument().remove(0, pane.getDocument().getLength());
        } catch (BadLocationException ignored) {
        }
        appendHtml(CSS);
        appendSystem(Langs.t("chat.clearDone") + "\n\n");
    }
}
