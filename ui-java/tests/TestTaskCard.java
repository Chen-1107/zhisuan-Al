import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import java.util.*;

/** 复现 ChatPage 任务状态行原地更新失效问题 */
public class TestTaskCard {
    static JTextPane pane = new JTextPane();
    static Map<String, Element> taskElems = new LinkedHashMap<>();
    static String taskId = "zs-task-1700000000000-10";

    static final String CSS = "<style>"
            + "body { font-family: 微软雅黑; font-size: 13px; color: #0f172a; margin: 4px 8px; }"
            + "p { margin: 4px 0 8px 0; }"
            + "code { font-family: Consolas, Dialog; color: #be185d; }"
            + "table { border: 1px solid #cbd5e1; margin: 4px 0 8px 0; }"
            + "th { background-color: #f1f5f9; padding: 4px 8px; border: 1px solid #cbd5e1; font-weight: bold; }"
            + "td { padding: 3px 8px; border: 1px solid #e2e8f0; }"
            + "</style>";

    static Element bodyElement() {
        HTMLDocument doc = (HTMLDocument) pane.getDocument();
        Element root = doc.getDefaultRootElement();
        for (int i = 0; i < root.getElementCount(); i++) {
            Element el = root.getElement(i);
            if ("body".equals(el.getName())) return el;
        }
        return root;
    }

    static void appendHtml(String html) {
        try {
            HTMLDocument doc = (HTMLDocument) pane.getDocument();
            doc.insertBeforeEnd(bodyElement(), html);
            pane.setCaretPosition(doc.getLength());
        } catch (Exception e) {
            System.out.println("  !! appendHtml failed: " + e);
        }
    }

    static void diag(String tag, Element el) {
        HTMLDocument doc = (HTMLDocument) pane.getDocument();
        int docLen = doc.getLength();
        String attached = "?";
        try {
            attached = (el.getStartOffset() < docLen && el.getEndOffset() <= docLen) ? "attached" : "DETACHED";
        } catch (Exception e) { attached = "ERR:" + e.getClass().getSimpleName(); }
        System.out.printf("  [%s] el=%s parent=%s offsets=[%d,%d] docLen=%d -> %s%n",
                tag, el.getName(), el.getParentElement() == null ? "null" : el.getParentElement().getName(),
                el.getStartOffset(), el.getEndOffset(), docLen, attached);
    }

    static void updateTaskCard(String status, String detail) {
        String color = "running".equals(status) ? "#d97706" : "#16a34a";
        String inner = "⟳ 任务 <b>" + taskId + "</b> [" + status + "] " + detail;
        HTMLDocument doc = (HTMLDocument) pane.getDocument();
        Element el = taskElems.get(taskId);
        if (el != null && el.getParentElement() != null) {
            try {
                doc.setInnerHTML(el, inner);
                pane.setCaretPosition(doc.getLength());
                return;
            } catch (Exception e) {
                System.out.println("  !! setInnerHTML failed: " + e);
            }
        }
        appendHtml("<div style='color:" + color + ";font-size:12px;margin:2px 0 2px 4px'>" + inner + "</div>");
        Element body = bodyElement();
        taskElems.put(taskId, body.getElement(body.getElementCount() - 1));
    }

    static void appendTaskCard() {
        appendHtml("<table cellpadding='5' cellspacing='0' bgcolor='#fefce8' "
                + "style='margin:4px 0 6px 0;border:1px solid #fde68a'>"
                + "<tr><td>⏳</td><td><b>后台任务已提交</b>"
                + "<br><font size='2' color='#64748b'>" + taskId + "</font></td></tr></table>");
        updateTaskCard("running", "后台任务运行中…");
    }

    static String visibleText() {
        try {
            return pane.getDocument().getText(0, pane.getDocument().getLength())
                    .replaceAll("\\s+", " ");
        } catch (Exception e) { return "ERR"; }
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame("test");
            f.add(new JScrollPane(pane));
            f.setSize(900, 600);
            f.setLocationRelativeTo(null);
            f.setVisible(true);

            pane.setContentType("text/html");
            appendHtml(CSS);

            // 模拟真实对话序列
            appendHtml("<div style='color:#2563eb;font-weight:bold'>你：</div><div style='margin:0 0 8px 0'>计算阿司匹林单点能</div>");
            appendHtml("<div style='color:#7c3aed;font-weight:bold;margin-top:4px'>🔧 调用工具 submit_dft_job</div>");
            appendHtml("<div style='font-family:Consolas;font-size:12px;color:#475569;margin:2px 0 2px 4px'>参数: {\"smiles\": \"CC(=O)OC1=CC=CC=C1C(=O)O\", \"backend\": \"local_orca\", \"job_type\": \"sp\"}</div>");
            appendHtml("<div style='font-family:Consolas;font-size:12px;color:#475569;margin:2px 0 4px 4px'>结果: 任务已提交（task_id: " + taskId + "）。计算类型：单点能（后端 local_orca）</div>");
            appendTaskCard();
            System.out.println("== after appendTaskCard ==");
            diag("initial", taskElems.get(taskId));

            // 服务端第3轮 status 事件（在任务卡片之后追加）
            appendHtml("<div style='color:#64748b;font-style:italic'>⟳ 正在思考…（第 3 轮）</div>");
            System.out.println("== after appendSystem(正在思考) ==");
            diag("post-sysmsg", taskElems.get(taskId));

            // 模拟 20 次轮询
            for (int i = 1; i <= 20; i++) {
                updateTaskCard("running", "运行中（已 " + i * 3 + " 秒）");
                if (i <= 6 || i == 20) {
                    System.out.println("== poll #" + i + " ==");
                    diag("poll" + i, taskElems.get(taskId));
                }
            }

            // 完成
            updateTaskCard("done", "单点能计算完成（后端 local_orca）: 能量 -648.328306 Hartree");
            System.out.println("== after done ==");
            diag("done", taskElems.get(taskId));

            String txt = visibleText();
            System.out.println("\n== FINAL visible text ==");
            System.out.println(txt);
            System.out.println("\ncontains '60 秒' (last poll): " + txt.contains("60 秒"));
            System.out.println("contains '6 秒': " + txt.contains("6 秒"));
            System.out.println("contains 'done': " + txt.contains("done"));
            System.exit(0);
        });
    }
}
