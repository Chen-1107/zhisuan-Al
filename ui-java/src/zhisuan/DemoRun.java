package zhisuan;

import com.zhisuan.workflow.ui.MonitorPanel;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

/**
 * 研究链条静态演示：启动智算铝宾前端 → 停掉工作流轮询（避免后端状态覆盖演示）→
 * 在【工作流】页逐步模拟推进完整研究链
 * （对话输入 → LLM Agent → 分子输入 → 固定-放开法 MDP 子链 → DFT 计算 → 日志分诊 → 判定分支 → 谱分析 → 报告生成），
 * 并补一张 RERUN 回环状态；随后切到对话输入 / 化合物库 / 光谱 / 计算作业等支撑页面各截一张。
 * 全部 PNG 输出到 Demo_p 目录。
 */
public class DemoRun {

    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "Demo_p";
        new File(outDir).mkdirs();
        zhisuan.Ui.applyDark(); // 与 Main.main 保持一致

        MainFrame[] ref = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            ref[0] = new MainFrame("http://127.0.0.1:8899", "default");
            ref[0].setLocation(-2000, 0); // 移出可视区，避免打扰
            ref[0].setVisible(true);
        });
        MainFrame f = ref[0];

        WorkflowPage wf = workflowPage(f);
        select(f, "workflow");
        sleep(3000); // 等首轮轮询连上后端（300ms 初始延迟 + 2s 超时），顶部显示「在线」
        wf.stopPolling(); // 关键：否则后端 /api/workflow/status 每 3s 覆盖演示推进状态
        MonitorPanel mon = wf.monitor();
        mon.resetDemo();  // 统一回到演示初始态：上游子链完成 / 知识注入 / DFT 收敛中
        sleep(800);

        // 1. 全链初始状态
        shot(f, outDir, "01_workflow_initial.png");

        // 2. DFT 完成 → 日志分诊开始
        mon.advance();
        shot(f, outDir, "02_dft_done_diag_start.png");

        // 3. 分诊可信 → 判定分支（日志页签）
        mon.advance();
        clickTab(mon, "日志");
        sleep(500);
        shot(f, outDir, "03_diag_ok_decision.png");

        // 4. OK/可信 → 谱分析（节点状态页签）
        mon.advance();
        clickTab(mon, "节点状态");
        sleep(500);
        shot(f, outDir, "04_decision_multiwfn.png");

        // 5. 谱分析完成 → 报告生成
        mon.advance();
        shot(f, outDir, "05_multiwfn_report.png");

        // 6. 报告完成，链路闭环（执行记录页签）
        mon.advance();
        clickTab(mon, "执行记录");
        sleep(500);
        shot(f, outDir, "06_chain_complete.png");

        // 7. RERUN 回环：判定分支触发 DFT 重跑（日志页签）
        mon.rerun();
        clickTab(mon, "日志");
        sleep(500);
        shot(f, outDir, "07_rerun_loop.png");

        // 支撑页面：对话输入（研究链起点）
        select(f, "chat");
        shot(f, outDir, "08_chat_input.png");

        // 化合物库
        select(f, "library");
        shot(f, outDir, "09_library.png");

        // 光谱分析（Multiwfn 谱学结果）
        select(f, "spectrum");
        sleep(400);
        UiShot.loadSample(f);
        shot(f, outDir, "10_spectrum.png");

        // 计算作业（DFT 集群任务）
        select(f, "jobs");
        sleep(1500);
        shot(f, outDir, "11_jobs.png");

        SwingUtilities.invokeAndWait(f::dispose);
        System.out.println("[DemoRun] done -> " + new File(outDir).getAbsolutePath());
        System.exit(0);
    }

    // ---------------- 工具 ----------------

    @SuppressWarnings("unchecked")
    private static WorkflowPage workflowPage(MainFrame f) throws Exception {
        var pf = MainFrame.class.getDeclaredField("pages");
        pf.setAccessible(true);
        Map<String, Page> pages = (Map<String, Page>) pf.get(f);
        return (WorkflowPage) pages.get("workflow");
    }

    private static void select(MainFrame f, String key) {
        try {
            SwingUtilities.invokeAndWait(() -> f.select(key));
        } catch (Exception e) {
            System.out.println("[demo] select(" + key + ") FAILED: " + e);
        }
    }

    /** 点击监控抽屉顶部标签（执行记录/日志/节点状态/任务进程）。 */
    private static void clickTab(MonitorPanel mon, String text) {
        if (!clickLabel(mon, text)) {
            System.out.println("[demo] tab not found: " + text);
        }
    }

    private static boolean clickLabel(Container c, String text) {
        for (Component x : c.getComponents()) {
            if (x instanceof JLabel && text.equals(((JLabel) x).getText())) {
                JLabel l = (JLabel) x;
                l.dispatchEvent(new MouseEvent(l, MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(), 0,
                        l.getWidth() / 2, l.getHeight() / 2, 1, false, MouseEvent.BUTTON1));
                return true;
            }
            if (x instanceof Container && clickLabel((Container) x, text)) {
                return true;
            }
        }
        return false;
    }

    private static void shot(MainFrame f, String dir, String name) throws Exception {
        sleep(900); // 等 EDT 完成布局/重绘
        BufferedImage img = new BufferedImage(
                f.getContentPane().getWidth(), f.getContentPane().getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        f.getContentPane().printAll(g);
        g.dispose();
        File out = new File(dir, name);
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println("[demo] " + name + " " + img.getWidth() + "x" + img.getHeight()
                + " -> " + out.getAbsolutePath());
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
