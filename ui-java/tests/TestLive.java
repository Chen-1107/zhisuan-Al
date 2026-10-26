package zhisuan;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 端到端验证（真实 agent_server:8899 + 真实 ChatPage 代码，反射驱动）：
 * 1. POST /api/task/submit 提交真实 DFT 任务（CCO 单点能，local_orca）
 * 2. registerTaskMonitor 注册轮询（真实 Timer + SwingWorker 链路）
 * 3. 任务完成 → updateTaskCard 原位更新状态行 → resumeConversation 自动恢复对话（真实 SSE/LLM）
 * 4. 断言：状态行唯一、[done] 显示、无 running 残留、自动恢复触发、View 树完好
 */
public class TestLive {
    static ChatPage page;
    static JTextPane pane;

    static int occurrences(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }

    static String docText() {
        try {
            return pane.getDocument().getText(0, pane.getDocument().getLength());
        } catch (Exception e) {
            return "ERR";
        }
    }

    public static void main(String[] args) throws Exception {
        ApiClient api = new ApiClient("http://127.0.0.1:8899");
        Map<String, Object> body = Map.of("tool", "dft_single_point",
                "params", Map.of("smiles", "CCO", "backend", "local_orca"),
                "label", "e2e-live-test");
        Object res = api.post("/api/task/submit", body);
        String taskId = Json.str(Json.obj(res).get("task_id"));
        System.out.println("提交任务: " + taskId);
        if (taskId.isEmpty()) {
            System.out.println("!! 提交失败: " + res);
            System.exit(1);
        }

        SwingUtilities.invokeAndWait(() -> {
            try {
                page = new ChatPage(api);
                JFrame f = new JFrame("live-e2e");
                f.add(new JScrollPane(page));
                f.setSize(900, 600);
                f.setLocationRelativeTo(null);
                f.setVisible(true);
                Field pf = ChatPage.class.getDeclaredField("pane");
                pf.setAccessible(true);
                pane = (JTextPane) pf.get(page);
                Method reg = ChatPage.class.getDeclaredMethod("registerTaskMonitor", String.class);
                reg.setAccessible(true);
                reg.invoke(page, "任务已提交（task_id: " + taskId + "）。计算类型：单点能（后端 local_orca）");
            } catch (Throwable t) {
                System.out.println("!! 初始化失败: " + t);
                System.exit(1);
            }
        });

        // 观察直到 [done] 出现且自动恢复触发（最多 180 秒）
        boolean done = false, resumed = false;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 180_000) {
            Thread.sleep(2000);
            final String txt = docText();
            done = txt.contains("[done]");
            resumed = txt.contains("自动恢复对话");
            System.out.printf("[t=%3ds] done=%s resumed=%s len=%d%n",
                    (System.currentTimeMillis() - start) / 1000, done, resumed, txt.length());
            if (done && resumed) break;
        }

        // 等待模型流式回复结束（文档长度稳定）
        int stable = 0, lastLen = docText().length();
        while (stable < 3 && System.currentTimeMillis() - start < 240_000) {
            Thread.sleep(2000);
            int len = docText().length();
            stable = len == lastLen ? stable + 1 : 0;
            lastLen = len;
        }

        String txt = docText();
        int statusCount = occurrences(txt, "任务 " + taskId + " [");
        int doneCount = occurrences(txt, "[done]");
        int runningCount = occurrences(txt, "[running]");

        System.out.println("\n===== 验证结果 =====");
        System.out.println("状态行唯一（原位更新）: " + (statusCount == 1) + " (" + statusCount + ")");
        System.out.println("[done] 显示一次: " + (doneCount == 1));
        System.out.println("[running] 无残留: " + (runningCount == 0));
        System.out.println("自动恢复对话触发: " + resumed);
        System.out.println("View 树完好（文档持续追加）: " + (txt.length() > 300));

        boolean ok = statusCount == 1 && doneCount == 1 && runningCount == 0 && resumed && txt.length() > 300;
        System.out.println("总体: " + (ok ? "PASS" : "FAIL"));
        System.exit(ok ? 0 : 1);
    }
}
