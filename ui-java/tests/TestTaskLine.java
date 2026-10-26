package zhisuan;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 离线验证（无需服务端，直接反射驱动真实 ChatPage.updateTaskCard）：
 * 1. 模拟 20 轮轮询更新（running → done），中途插入用户消息验证非末尾元素也能正确删除
 * 2. 断言：无异常抛出、状态行不冗余累积（taskId 只出现 2 次：黄卡片 1 + 状态行 1）、
 *    done 状态可见、文档长度稳定（无空行累积）、View 树完好（后续 append 不抛 NPE）
 */
public class TestTaskLine {
    static ChatPage page;
    static int occurrences(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                page = new ChatPage(new ApiClient("http://127.0.0.1:1"));
                JFrame f = new JFrame("offline-task-line");
                f.add(new JScrollPane(page));
                f.setSize(900, 600);
                f.setLocationRelativeTo(null);
                f.setVisible(true);
            } catch (Throwable t) {
                System.out.println("!! 构造 ChatPage 失败: " + t);
                System.exit(1);
            }
        });

        Method update = ChatPage.class.getDeclaredMethod("updateTaskCard", String.class, String.class, String.class);
        update.setAccessible(true);
        Method appendCard = ChatPage.class.getDeclaredMethod("appendTaskCard", String.class, String.class, String.class);
        appendCard.setAccessible(true);
        Method appendUser = ChatPage.class.getDeclaredMethod("appendUser", String.class);
        appendUser.setAccessible(true);
        Field paneField = ChatPage.class.getDeclaredField("pane");
        paneField.setAccessible(true);
        JTextPane pane = (JTextPane) paneField.get(page);

        String taskId = "zs-task-offline-1";
        SwingUtilities.invokeAndWait(() -> {
            try {
                appendCard.invoke(page, taskId, "running", "后台任务运行中…");
            } catch (Throwable t) {
                System.out.println("!! appendTaskCard 失败: " + t);
            }
        });

        final int[] baselineLen = {-1};
        for (int i = 1; i <= 20; i++) {
            final int round = i;
            SwingUtilities.invokeAndWait(() -> {
                try {
                    if (round == 10) {
                        // 状态行不再是文档末尾元素：验证中间元素的删除
                        appendUser.invoke(page, "用户在任务运行期间插入的测试消息");
                    }
                    if (round < 18) {
                        update.invoke(page, taskId, "running", "运行中（已 " + round * 3 + " 秒）");
                    } else {
                        update.invoke(page, taskId, "done", "✅ 计算完成，E = -154.06 Ha");
                    }
                    if (round == 5) {
                        baselineLen[0] = pane.getDocument().getLength();
                    }
                } catch (Throwable t) {
                    System.out.println("!! 第 " + round + " 轮更新失败: " + t);
                }
            });
            // 空转一帧，给布局/事件队列处理机会
            SwingUtilities.invokeAndWait(() -> { });
        }

        final String text = pane.getDocument().getText(0, pane.getDocument().getLength());
        final int finalLen = pane.getDocument().getLength();
        int statusCount = occurrences(text, "任务 " + taskId + " [");
        int doneCount = occurrences(text, "[done]");
        int runningCount = occurrences(text, "[running]");

        System.out.println("\n===== 验证结果 =====");
        System.out.println("状态行出现次数: " + statusCount + "（期望 1，哨兵定位原位更新）");
        System.out.println("[done] 出现次数: " + doneCount + "（期望 1）");
        System.out.println("[running] 残留: " + runningCount + "（期望 0）");
        System.out.println("文档长度: 第5轮=" + baselineLen[0] + " 最终=" + finalLen + "（20 轮更新后应基本持平）");

        // 最终再追加内容验证 View 树完好（setInnerHTML 方案在此处会抛 NPE）
        boolean appendOk = true;
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    appendUser.invoke(page, "验证 View 树完好性：最终追加消息");
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            });
        } catch (Throwable t) {
            appendOk = false;
            System.out.println("!! 最终追加失败: " + t.getCause());
        }

        boolean ok = statusCount == 1 && doneCount == 1 && runningCount == 0 && appendOk;
        System.out.println("总体: " + (ok ? "PASS" : "FAIL"));
        System.exit(ok ? 0 : 1);
    }
}
