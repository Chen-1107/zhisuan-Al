package zhisuan;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

/** 打字机效果状态机离线测试（无需服务层）：
 *  1) done 事件先到、打字未完 → 打完后自动收尾（Markdown 渲染 + 解锁）
 *  2) 中间轮 tool_call → removeStreamText 清除打字中的内容与队列
 *  3) 分批插入的转义正确性（& < > 不被分批解析破坏） */
public class TestTypewriter {
    static int pass = 0, fail = 0;

    public static void main(String[] args) throws Exception {
        ChatPage page = new ChatPage(new ApiClient("http://127.0.0.1:1"));
        Class<?> c = ChatPage.class;

        Method queueTyping = c.getDeclaredMethod("queueTyping", String.class);
        queueTyping.setAccessible(true);
        Method finishTurn = c.getDeclaredMethod("finishTurn", boolean.class);
        finishTurn.setAccessible(true);
        Method removeStreamText = c.getDeclaredMethod("removeStreamText");
        removeStreamText.setAccessible(true);
        Method setBusy = c.getDeclaredMethod("setBusy", boolean.class);
        setBusy.setAccessible(true);
        Field fHistory = c.getDeclaredField("history");
        fHistory.setAccessible(true);
        Field fQueue = c.getDeclaredField("typingQueue");
        fQueue.setAccessible(true);
        Field fStreamBuf = c.getDeclaredField("streamBuf");
        fStreamBuf.setAccessible(true);
        Field fBusy = c.getDeclaredField("busy");
        fBusy.setAccessible(true);
        Field fPane = c.getDeclaredField("pane");
        fPane.setAccessible(true);

        String answer = "第一行含 & 符号与 <标签>\n第二行：**加粗** 与 `代码`";

        // ---------- 场景1：done 已到、打字未完 → 打完后自动收尾 ----------
        setBusy.invoke(page, true);
        ((StringBuilder) fStreamBuf.get(page)).append(answer);
        SwingUtilities.invokeAndWait(() -> {
            try {
                queueTyping.invoke(page, answer);
                finishTurn.invoke(page, true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        check("打字未完时挂起收尾（不提前渲染）", ((List<?>) fHistory.get(page)).isEmpty());

        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline && ((List<?>) fHistory.get(page)).isEmpty()) {
            Thread.sleep(30);
        }
        List<?> h = (List<?>) fHistory.get(page);
        check("打完后自动收尾并记录答案", h.size() == 1
                && answer.equals(((Map<?, ?>) h.get(0)).get("content")));
        check("收尾后解锁（busy=false）", !((Boolean) fBusy.get(page)));

        String doc = docText((JTextPane) fPane.get(page));
        check("HTML 转义正确（& 与 <标签> 原样显示）", doc.contains("& 符号与 <标签>"));
        check("Markdown 生效（** 消失且加粗文本在）", !doc.contains("**") && doc.contains("加粗"));

        // ---------- 场景2：中间轮 tool_call → removeStreamText 清场 ----------
        ((StringBuilder) fStreamBuf.get(page)).append("中间轮临时文本");
        SwingUtilities.invokeAndWait(() -> {
            try {
                queueTyping.invoke(page, "中间轮临时文本");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(120); // 打出一部分
        SwingUtilities.invokeAndWait(() -> {
            try {
                removeStreamText.invoke(page);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        String doc2 = docText((JTextPane) fPane.get(page));
        check("中间轮文本已从文档删除", !doc2.contains("中间轮临时文本"));
        check("打字队列已清空", ((ArrayDeque<?>) fQueue.get(page)).isEmpty());

        System.out.println();
        System.out.println(fail == 0 ? "ALL " + pass + " PASS" : "FAIL: " + fail + " / " + (pass + fail));
        System.exit(fail == 0 ? 0 : 1);
    }

    static String docText(JTextPane p) throws Exception {
        final String[] out = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                out[0] = p.getDocument().getText(0, p.getDocument().getLength());
            } catch (Exception e) {
                out[0] = "";
            }
        });
        return out[0];
    }

    static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("[PASS] " + name);
        } else {
            fail++;
            System.out.println("[FAIL] " + name);
        }
    }
}
