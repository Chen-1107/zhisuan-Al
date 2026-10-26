package zhisuan;

import javax.swing.JList;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

/**
 * 验证入口：启动主窗口 → 依次切到各新页面 → 打印输出为 PNG
 * 供自动化渲染验证使用（不需要人工按键）。
 */
public class UiShot {
    /** 截图输出目录，由 main 设置；未设置时落系统临时目录 */
    private static String outDir = System.getProperty("java.io.tmpdir");

    public static void main(String[] args) throws Exception {
        outDir = args.length > 0 ? args[0] : System.getProperty("java.io.tmpdir");
        String mode = args.length > 1 ? args[1] : "default";
        String port = args.length > 2 ? args[2] : "8899";
        zhisuan.Ui.applyDark(); // 与 Main.main 保持一致，还原真实渲染
        // 帧创建在 EDT；截图循环必须在主线程执行（shoot 内部有 sleep，
        // 若在 EDT 上 sleep 会阻塞事件分发，导致 onShow/异步数据/重绘全部无法执行）
        MainFrame[] ref = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            ref[0] = new MainFrame("http://127.0.0.1:" + port, mode);
            ref[0].setLocation(-2000, 0); // 移出可视区，避免打扰
            ref[0].setVisible(true);
        });
        MainFrame f = ref[0];
        String[] pages;
        if ("audit".equals(mode)) pages = new String[]{"chat", "audit", "settings"};
        else if ("dispatch".equals(mode)) pages = new String[]{"chat", "dispatch", "settings"};
        else pages = new String[]{"chat", "starmap", "spectrum", "jobs", "library", "mol", "memory", "settings"};
        for (String p : pages) {
            shoot(f, outDir + File.separator + "shot_" + mode + "_" + p + ".png", p);
        }
        SwingUtilities.invokeAndWait(f::dispose);
        System.out.println("[UiShot] done -> " + outDir);
        System.exit(0);
    }

    /** 遍历组件树，打印包含 "Canvas" 的组件尺寸，并单独导出 3D 画布截图 */
    static void dumpCanvas(java.awt.Container root, String tag) {
        try {
            for (java.awt.Component c : root.getComponents()) {
                if (c instanceof java.awt.Container) dumpCanvas((java.awt.Container) c, tag);
                if (c.getClass().getSimpleName().contains("Canvas")) {
                    System.out.println("[canvas] " + tag + " -> " + c.getClass().getSimpleName()
                            + " size=" + c.getWidth() + "x" + c.getHeight()
                            + " vis=" + c.isShowing() + " valid=" + c.isValid());
                    if (c.getWidth() > 0 && c.getHeight() > 0) {
                        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                                c.getWidth(), c.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = img.createGraphics();
                        c.printAll(g);
                        g.dispose();
                        javax.imageio.ImageIO.write(img, "png",
                                new File(outDir, "raw_canvas.png"));
                        System.out.println("[canvas] saved raw_canvas " + img.getWidth() + "x" + img.getHeight());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[canvas] dump FAILED: " + e);
        }
    }

    static final class TabsHolder { javax.swing.JTabbedPane js = null; }

    /** 递归查找光谱页里的 JTabbedPane */
    static void findTabs(java.awt.Container c, TabsHolder h, int[] idx) {
        if (h.js != null) return;
        if (c instanceof javax.swing.JTabbedPane) { h.js = (javax.swing.JTabbedPane) c; return; }
        if (idx[0]++ > 2000) return;
        for (var x : c.getComponents()) if (x instanceof java.awt.Container) findTabs((java.awt.Container) x, h, idx);
    }

    /** 反射调用光谱页的 loadDemo()，让能级图/红外谱填上示例数据后再截图 */
    static void loadSample(MainFrame f) {
        try {
            var pagesField = f.getClass().getDeclaredField("pages");
            pagesField.setAccessible(true);
            Map<?, ?> pages = (Map<?, ?>) pagesField.get(f);
            for (Object v : pages.values()) {
                if (v.getClass().getSimpleName().equals("SpectrumPage")) {
                    var m = v.getClass().getDeclaredMethod("loadDemo");
                    m.setAccessible(true);
                    m.invoke(v);
                    Thread.sleep(450);
                    TabsHolder th = new TabsHolder();
                    findTabs((java.awt.Container) v, th, new int[]{0});
                    if (th.js != null) { th.js.setSelectedIndex(1); Thread.sleep(400); }
                    System.out.println("[spectrum] loadDemo invoked, tab=" + (th.js == null ? "?" : th.js.getSelectedIndex()));
                }
            }
        } catch (Exception e) {
            System.out.println("[spectrum] loadDemo FAILED: " + e);
        }
    }

    static void shoot(MainFrame f, String path, String pageKey) {
        try {
            var m = f.getClass().getDeclaredMethod("select", String.class);
            m.setAccessible(true);
            // 必须在 EDT 上切换页面，否则 Swing 渲染不完整（列表/画布可能空白）
            if (SwingUtilities.isEventDispatchThread()) {
                try { m.invoke(f, pageKey); } catch (Exception ex) { ex.printStackTrace(); }
            } else {
                SwingUtilities.invokeAndWait(() -> {
                    try { m.invoke(f, pageKey); } catch (Exception ex) { ex.printStackTrace(); }
                });
            }
            Thread.sleep(2000); // 等待异步数据（列表/分诊）填充（主线程 sleep，EDT 可正常处理事件）
            if (pageKey.equals("mol")) dumpCanvas(f, "mol");
            if (pageKey.equals("spectrum")) loadSample(f);
            // audit 模式：自动选中队列首项以触发分诊，随后等分诊结果渲染
            if ("audit".equals(pageKey)) {
                boolean isAuditMode = false;
                try {
                    var mf = MainFrame.class.getDeclaredField("mode");
                    mf.setAccessible(true);
                    isAuditMode = "audit".equals(mf.get(f));
                } catch (Exception e) { /* ignore */ }
                if (isAuditMode) {
                    SwingUtilities.invokeAndWait(() -> {
                        try {
                            var pf = MainFrame.class.getDeclaredField("pages");
                            pf.setAccessible(true);
                            Map<?, ?> pages = (Map<?, ?>) pf.get(f);
                            Object ap = pages.get("audit");
                            var qf = ap.getClass().getDeclaredField("queue");
                            qf.setAccessible(true);
                            JList<?> queue = (JList<?>) qf.get(ap);
                            if (queue.getModel().getSize() > 0) {
                                queue.setSelectedIndex(0);
                                queue.ensureIndexIsVisible(0);
                            }
                            System.out.println("[audit] queue size=" + queue.getModel().getSize());
                        } catch (Exception e) {
                            System.out.println("[audit] select FAILED: " + e);
                        }
                    });
                    Thread.sleep(22000); // 等待 /api/diagnose（auto 引擎可能走 qwen，需 15-20s）完成并渲染判定详情
                }
            }
            BufferedImage img = new BufferedImage(
                    f.getContentPane().getWidth(), f.getContentPane().getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            f.getContentPane().printAll(g);
            g.dispose();
            javax.imageio.ImageIO.write(img, "png", new File(path));
            System.out.println("[shot] " + pageKey + " " + img.getWidth() + "x" + img.getHeight() + " -> " + path);
        } catch (Exception e) {
            System.out.println("[shot] " + pageKey + " FAILED: " + e);
        }
    }
}