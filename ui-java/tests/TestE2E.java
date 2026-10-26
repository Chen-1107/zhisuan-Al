import zhisuan.Markdown;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.text.*;
import javax.swing.text.html.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** 端到端复现：Timer + SwingWorker + DoSubmit 批量 done() 投递 + 原地更新 */
public class TestE2E {
    static JTextPane pane = new JTextPane();
    static Map<String, Element> taskElems = new LinkedHashMap<>();
    static Map<String, Timer> taskTimers = new LinkedHashMap<>();
    static String taskId = "zs-task-1700000000000-10";
    static final AtomicInteger polls = new AtomicInteger();
    static final AtomicInteger updates = new AtomicInteger();
    static volatile boolean busy = true; // 模拟 SSE 流未结束
    static String baseUrl;

    static final String CSS = "<style>"
            + "body { font-family: 微软雅黑; font-size: 13px; color: #0f172a; margin: 4px 8px; }"
            + "p { margin: 4px 0 8px 0; }"
            + "code { font-family: Consolas, Dialog; color: #be185d; }"
            + "blockquote { color: #64748b; font-style: italic; margin-left: 12px; border-left: 2px solid #cbd5e1; }"
            + "h1 { font-size: 18px; font-weight: bold; margin: 8px 0 6px 0; }"
            + "h2 { font-size: 16px; font-weight: bold; margin: 8px 0 4px 0; }"
            + "h3 { font-size: 14px; font-weight: bold; margin: 6px 0 4px 0; }"
            + "h4 { font-size: 13px; font-weight: bold; margin: 6px 0 4px 0; }"
            + ".h1 { font-size: 18px; font-weight: bold; margin: 8px 0 6px 0; color: #1e293b; }"
            + ".h2 { font-size: 16px; font-weight: bold; margin: 8px 0 4px 0; color: #1e293b; }"
            + ".h3 { font-size: 14px; font-weight: bold; margin: 6px 0 4px 0; color: #334155; }"
            + ".h4 { font-size: 13px; font-weight: bold; margin: 6px 0 4px 0; color: #334155; }"
            + ".codeblock { background-color: #f8fafc; margin: 6px 0; border: 1px solid #e2e8f0; }"
            + ".codelang { font-family: Consolas, Dialog; font-size: 11px; color: #94a3b8;"
            + " background-color: #f1f5f9; padding: 2px 6px; border-bottom: 1px solid #e2e8f0; }"
            + ".codeblock pre { font-family: Consolas, Dialog; font-size: 12px; margin: 2px; padding: 2px; }"
            + "table { border: 1px solid #cbd5e1; margin: 4px 0 8px 0; }"
            + "th { background-color: #f1f5f9; padding: 4px 8px; border: 1px solid #cbd5e1; font-weight: bold; }"
            + "td { padding: 3px 8px; border: 1px solid #e2e8f0; }"
            + "pre { font-family: Consolas, Dialog; font-size: 12px; margin: 2px; }"
            + "img { margin: 4px 0; }"
            + "a { color: #2563eb; }"
            + "</style>";

    static Element bodyElement() {
        HTMLDocument doc = (HTMLDocument) pane.getDocument();
        Element root = doc.getDefaultRootElement();
        for (int i = 0; i < root.getElementCount(); i++)
            if ("body".equals(root.getElement(i).getName())) return root.getElement(i);
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

    static void appendSystem(String text) {
        appendHtml("<div style='color:#64748b;font-style:italic'>"
                + Markdown.render(text).replace("\n", "<br>") + "</div>");
    }

    static void updateTaskCard(String taskId, String status, String detail) {
        String color = switch (status) {
            case "done" -> "#16a34a";
            case "failed", "interrupted" -> "#dc2626";
            default -> "#d97706";
        };
        String mark = switch (status) {
            case "done" -> "✔";
            case "failed", "interrupted" -> "✘";
            default -> "⟳";
        };
        String inner = mark + " 任务 <b>" + taskId + "</b> [" + status + "] "
                + Markdown.render(detail).replace("\n", " ");
        HTMLDocument doc = (HTMLDocument) pane.getDocument();
        Element el = taskElems.get(taskId);
        if (el != null && el.getParentElement() != null) {
            try {
                doc.setInnerHTML(el, inner);
                pane.setCaretPosition(doc.getLength());
                updates.incrementAndGet();
                return;
            } catch (Exception e) {
                System.out.println("  !! setInnerHTML failed: " + e);
            }
        }
        appendHtml("<div style='color:" + color + ";font-size:12px;margin:2px 0 2px 4px'>" + inner + "</div>");
        Element body = bodyElement();
        taskElems.put(taskId, body.getElement(body.getElementCount() - 1));
        updates.incrementAndGet();
    }

    static void registerTaskMonitor(String toolResult) {
        // 简化：直接注册 taskId
        appendHtml("<table cellpadding='5' cellspacing='0' bgcolor='#fefce8' "
                + "style='margin:4px 0 6px 0;border:1px solid #fde68a'>"
                + "<tr><td>⏳</td><td><b>后台任务已提交</b>"
                + "<br><font size='2' color='#64748b'>" + taskId + "</font></td></tr></table>");
        updateTaskCard(taskId, "running", "后台任务运行中…");
        Timer timer = new Timer(1000, null);
        timer.addActionListener(e -> {
            try {
                pollTask(taskId, timer);
            } catch (Throwable t) {
                System.out.println("!!! LISTENER THREW (timer deaf risk): " + t);
                t.printStackTrace();
                throw t; // 真实代码不捕获 —— 模拟原样
            }
        });
        taskTimers.put(taskId, timer);
        timer.start();
    }

    static void pollTask(String taskId, Timer timer) {
        polls.incrementAndGet();
        new javax.swing.SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + "/status").openConnection();
                c.setConnectTimeout(3000);
                c.setReadTimeout(5000);
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = r.readLine()) != null) sb.append(l);
                    return sb.toString();
                } finally { c.disconnect(); }
            }

            @Override
            protected void done() {
                try {
                    String status = String.valueOf(get()).contains("\"done\"") ? "done" : "running";
                    if ("running".equals(status)) {
                        updateTaskCard(taskId, "running", "运行中（已 " + polls.get() * 1 + " 秒）");
                    } else {
                        timer.stop();
                        taskTimers.remove(taskId);
                        updateTaskCard(taskId, "done", "单点能计算完成: -648.328306 Hartree");
                    }
                } catch (Exception ignored) {
                    System.out.println("  done() swallowed: " + ignored);
                }
            }
        }.execute();
    }

    public static void main(String[] args) throws Exception {
        // 模拟服务端：12 秒后 done
        ServerSocket ss = new ServerSocket(0);
        baseUrl = "http://127.0.0.1:" + ss.getLocalPort();
        Thread srv = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try {
                while (true) {
                    Socket s = ss.accept();
                    try (OutputStream o = s.getOutputStream()) {
                        boolean done = System.currentTimeMillis() - t0 > 12000;
                        o.write(("{\"status\":\"" + (done ? "done" : "running") + "\"}").getBytes("UTF-8"));
                        o.flush();
                    } finally { s.close(); }
                }
            } catch (Exception ignored) {}
        }, "mock-server");
        srv.setDaemon(true);
        srv.start();

        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame("e2e");
            f.add(new JScrollPane(pane));
            f.setSize(900, 700);
            f.setVisible(true);
            pane.setContentType("text/html");
            appendHtml(CSS);
            appendHtml("<div style='color:#2563eb;font-weight:bold'>你：</div><div style='margin:0 0 8px 0'>计算阿司匹林单点能</div>");
            appendHtml("<div style='color:#7c3aed;font-weight:bold;margin-top:4px'>🔧 调用工具 submit_dft_job</div>");
            appendHtml("<div style='font-family:Consolas;font-size:12px;color:#475569;margin:2px 0 2px 4px'>参数: {\"smiles\": \"CC(=O)OC1=CC=CC=C1C(=O)O\", \"backend\": \"local_orca\", \"job_type\": \"sp\"}</div>");
            appendHtml("<div style='font-family:Consolas;font-size:12px;color:#475569;margin:2px 0 4px 4px'>结果: 任务已提交（task_id: " + taskId + "）。计算类型：单点能（后端 local_orca）</div>");
            registerTaskMonitor(taskId);
            appendSystem("⟳ 正在思考…（第 3 轮）");
        });

        // 观察者：每 2 秒输出状态
        for (int i = 0; i < 25; i++) {
            Thread.sleep(2000);
            final int sec = i;
            SwingUtilities.invokeAndWait(() -> {
                String txt;
                try { txt = pane.getDocument().getText(0, pane.getDocument().getLength()).replaceAll("\\s+", " "); }
                catch (Exception e) { txt = "ERR"; }
                System.out.printf("[t=%02ds] polls=%d updates=%d | %s%n",
                        sec * 2, polls.get(), updates.get(),
                        txt.length() > 160 ? txt.substring(txt.length() - 160) : txt);
            });
        }
        System.exit(0);
    }
}
