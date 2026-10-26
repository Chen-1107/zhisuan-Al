import zhisuan.Markdown;

/** 用真实任务数据 + 边缘输入测试 Markdown.render 的健壮性 */
public class TestMarkdown {
    static int pass = 0, fail = 0;

    static void t(String name, String md) {
        try {
            String html = Markdown.render(md);
            System.out.println("[OK ] " + name + " -> " + brief(html));
            pass++;
        } catch (Throwable e) {
            System.out.println("[THREW] " + name + " -> " + e.getClass().getName() + ": "
                    + e.getMessage());
            e.printStackTrace(System.out);
            fail++;
        }
    }

    static String brief(String s) {
        String r = s.replaceAll("\\s+", " ");
        return r.length() > 120 ? r.substring(0, 120) + "…" : r;
    }

    public static void main(String[] args) {
        // 真实任务 10 的 result
        String real10 = "单点能计算完成（后端 local_orca）:\nSMILES: CC(=O)OC1=CC=CC=C1C(=O)O\n"
                + "能量: -406831.846852 kcal/mol （-648.328306 Hartree）\n输出文件:\n"
                + "- D:\\zhisuan\\workspace\\orca_sample\\output.out";
        t("real-task-10", real10);

        // 真实任务 7 的 result（Multiwfn 键级，Python dict 风格）
        String real7 = "{'type': 'mayer_bond_order', 'bond_orders': {'1-2': 1.0113159, '1-4': 0.95177017}}";
        t("real-task-7", real7);

        // 运行中状态 detail
        t("running-detail", "运行中（已 52 秒）");

        // 边缘输入
        t("null-check", null);
        t("empty", "");
        t("unclosed-codeblock", "```java\nint x = 1;");
        t("many-newlines", "a\n\n\n\n\nb");
        t("table", "|a|b|\n|-|-|\n|1|2|");
        t("stars", "**** * *** ***** *");
        t("backticks", "`a` `` ` `x`");
        t("path-in-md-link", "文件见 [输出](c:\\Users\\test\\out.log)");
        t("img", "![结构图](C:\\Users\\x\\结构.png)");
        t("unterminated-link", "[点击 (c:\\x");
        t("unterminated-img", "![alt (c:\\x");
        t("nested-brackets", "[[a]]((b))");
        t("placeholder-char", "\u0000CB0\u0000");
        t("tilde", "~~a~~ ~ ~b~");
        t("underscore-bold", "a__b__c _d_");
        t("heading6", "###### 六级标题");
        t("heading-no-space", "######无空格标题");
        t("hr", "---");
        t("ol", "1. 第一\n2) 第二");
        t("quote", "> 引用1\n> 引用2");
        t("mixed-list", "- a\n* b\n+ c");
        t("long-line", "x".repeat(20000));
        t("long-line-with-stars", ("*a* ".repeat(5000)));
        t("cjk-mixed", "中文**加粗**与 `code` 混合\n- 列表项 🧪");
        t("xml-ish", "<script>alert(1)</script> & <b>原样</b>");
        t("json-params", "{\"smiles\": \"CC(=O)OC1=CC=CC=C1C(=O)O\", \"backend\": \"local_orca\"}");

        System.out.println("\npass=" + pass + " fail=" + fail);
        System.exit(fail > 0 ? 1 : 0);
    }
}
