package zhisuan;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

/**
 * 零依赖 Markdown → HTML 转换器（供对话页渲染模型输出）。
 * 支持：标题/粗体/斜体/删除线/行内代码/代码块/引用/有序无序列表/表格/分隔线/
 * 链接/本地图片（![alt](path) → &lt;img&gt;，自动转 file:/// URL）。
 */
public final class Markdown {
    private Markdown() {}

    private static final Pattern RE_CODE_BLOCK = Pattern.compile("```[ \\t]*(\\w*)[ \\t]*\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern RE_DISPLAY = Pattern.compile("\\$\\$(.+?)\\$\\$", Pattern.DOTALL);
    private static final Pattern RE_INLINE = Pattern.compile("\\$([^$\\n]+)\\$");
    private static final Color TEX_FG = new Color(0xDBE7FF);
    private static final Color TEX_BG = new Color(0, 0, 0, 0);

    /** 渲染 markdown 片段为 HTML 片段（不含 &lt;html&gt; 骨架）。任何异常降级为纯文本，绝不向调用方抛出 */
    public static String render(String md) {
        if (md == null) return "";
        try {
            return renderImpl(md);
        } catch (Throwable t) {
            System.err.println("[Markdown] render 失败，降级为纯文本: " + t);
            try {
                return "<p>" + escape(md).replace("\n", "<br>") + "</p>";
            } catch (Throwable t2) {
                return "";
            }
        }
    }

    private static String renderImpl(String md) {
        // 1. 抽取代码块 → 占位符（避免内部被行内规则污染）
        List<String> codeBlocks = new ArrayList<>();
        Matcher mc = RE_CODE_BLOCK.matcher(md);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (mc.find()) {
            sb.append(md, last, mc.start());
            String lang = mc.group(1).isEmpty() ? "" : mc.group(1);
            codeBlocks.add("<div class='codeblock'><div class='codelang'>"
                    + (lang.isEmpty() ? "代码" : lang) + "</div><pre>" + escape(mc.group(2))
                    + "</pre></div>");
            sb.append("\u0000CB").append(codeBlocks.size() - 1).append("\u0000");
            last = mc.end();
        }
        sb.append(md.substring(last));
        String text = sb.toString();

        // 2. 逐行块级处理
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        List<String> ul = new ArrayList<>();
        List<String> ol = new ArrayList<>();
        List<String> tableRows = new ArrayList<>();
        List<String> quote = new ArrayList<>();
        List<String> paraLines = new ArrayList<>();

        for (int i = 0; i <= lines.length; i++) {
            String line = i < lines.length ? lines[i] : "";
            String t = line.trim();

            boolean isTable = t.startsWith("|") && t.endsWith("|") && t.length() > 2;
            boolean isQuote = t.startsWith(">");
            boolean isUl = t.matches("[-*+]\\s+.+");
            boolean isOl = t.matches("\\d+[.)]\\s+.+");
            boolean isHr = t.matches("(-{3,}|\\*{3,}|_{3,})");
            boolean isHeading = t.matches("#{1,6}\\s+.*");
            boolean isPlaceholder = t.matches("\u0000CB\\d+\u0000");

            if (t.isEmpty() || isTable != tableActive(tableRows) || isQuote != quoteActive(quote)
                    || isUl != listActive(ul) || isOl != listActive(ol)) {
                flush(paraLines, ul, ol, tableRows, quote, out);
            }

            if (i >= lines.length) break;

            if (t.isEmpty()) {
                continue;
            } else if (isPlaceholder) {
                out.append(restoreCodeBlocks(t, codeBlocks));
            } else if (isHr) {
                out.append("<hr>");
            } else if (isHeading) {
                int level = 0;
                while (level < t.length() && t.charAt(level) == '#') level++;
                level = Math.min(level, 4);
                String content = t.substring(Math.min(level + 1, t.length())).trim();
                out.append("<div class='h").append(level).append("'>")
                        .append(inline(content)).append("</div>");
            } else if (isQuote) {
                quote.add(inline(t.replaceFirst("^>\\s?", "")));
            } else if (isUl) {
                ul.add(inline(t.replaceFirst("^[-*+]\\s+", "")));
            } else if (isOl) {
                ol.add(inline(t.replaceFirst("^\\d+[.)]\\s+", "")));
            } else if (isTable) {
                tableRows.add(t);
            } else {
                paraLines.add(line);
            }
        }
        return out.toString();
    }

    private static boolean tableActive(List<String> rows) { return !rows.isEmpty(); }
    private static boolean quoteActive(List<String> q) { return !q.isEmpty(); }
    private static boolean listActive(List<String> l) { return !l.isEmpty(); }

    private static void flush(List<String> paraLines, List<String> ul, List<String> ol,
                              List<String> tableRows, List<String> quote, StringBuilder out) {
        if (!paraLines.isEmpty()) {
            StringBuilder p = new StringBuilder("<p>");
            for (int i = 0; i < paraLines.size(); i++) {
                if (i > 0) p.append("<br>");
                p.append(inline(paraLines.get(i)));
            }
            p.append("</p>");
            out.append(p);
            paraLines.clear();
        }
        if (!ul.isEmpty()) {
            out.append("<ul>");
            for (String li : ul) out.append("<li>").append(li).append("</li>");
            out.append("</ul>");
            ul.clear();
        }
        if (!ol.isEmpty()) {
            out.append("<ol>");
            for (String li : ol) out.append("<li>").append(li).append("</li>");
            out.append("</ol>");
            ol.clear();
        }
        if (!tableRows.isEmpty()) {
            out.append("<table>");
            for (int i = 0; i < tableRows.size(); i++) {
                String row = tableRows.get(i);
                boolean header = (i == 1 && row.matches("\\|?\\s*:?-{2,}.*")); // 分隔行跳过
                if (header) continue;
                String tag = (i == 0) ? "th" : "td";
                out.append("<tr>");
                for (String cell : row.replaceFirst("^\\|", "").replaceFirst("\\|$", "").split("\\|")) {
                    out.append("<").append(tag).append(">").append(inline(cell.trim())).append("</").append(tag).append(">");
                }
                out.append("</tr>");
            }
            out.append("</table>");
            tableRows.clear();
        }
        if (!quote.isEmpty()) {
            out.append("<blockquote>");
            for (String q : quote) out.append("<p>").append(q).append("</p>");
            out.append("</blockquote>");
            quote.clear();
        }
    }

    private static String restoreCodeBlocks(String placeholder, List<String> codeBlocks) {
        Matcher m = Pattern.compile("\u0000CB(\\d+)\u0000").matcher(placeholder);
        StringBuilder sb = new StringBuilder();
        while (m.find()) sb.append(codeBlocks.get(Integer.parseInt(m.group(1))));
        return sb.toString();
    }

    // ---------- 行内元素 ----------

    private static String inline(String s) {
        List<String> imgs = new ArrayList<>();
        String r = latexMark(s, imgs);
        r = escape(r);
        r = restoreImgs(r, imgs);
        // 行内代码（先保护）
        r = r.replaceAll("`([^`\\n]+)`", "<code>$1</code>");
        // 图片：![alt](本地路径或URL)
        Matcher img = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)").matcher(r);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (img.find()) {
            sb.append(r, last, img.start());
            sb.append("<img src='").append(fileUrl(img.group(2))).append("' alt='")
                    .append(img.group(1)).append("' width='380'>");
            last = img.end();
        }
        sb.append(r.substring(last));
        r = sb.toString();
        // 链接
        r = r.replaceAll("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)",
                "<a href='$2'>$1</a>");
        // 粗体 / 斜体 / 删除线
        r = r.replaceAll("\\*\\*([^*\\n]+)\\*\\*", "<b>$1</b>");
        r = r.replaceAll("__([^_\\n]+)__", "<b>$1</b>");
        r = r.replaceAll("(?<![\\w*])\\*([^*\\n]+)\\*(?![\\w*])", "<i>$1</i>");
        r = r.replaceAll("~~([^~\\n]+)~~", "<s>$1</s>");
        return r;
    }

    /** 将 $..$/$$..$$ 公式替换为占位符（img 标签存列表，待 escape 后还原），失败回退原样 */
    private static String latexMark(String s, List<String> imgs) {
        String r = s;
        Matcher md = RE_DISPLAY.matcher(r);
        StringBuffer sb = new StringBuffer();
        while (md.find()) {
            String img = latexToImg(md.group(1), true);
            if (img == null) {
                md.appendReplacement(sb, Matcher.quoteReplacement(md.group()));
            } else {
                imgs.add(img);
                md.appendReplacement(sb, Matcher.quoteReplacement("\u0001JLM" + (imgs.size() - 1) + "\u0001"));
            }
        }
        md.appendTail(sb);
        r = sb.toString();
        Matcher mi = RE_INLINE.matcher(r);
        sb = new StringBuffer();
        while (mi.find()) {
            String img = latexToImg(mi.group(1), false);
            if (img == null) {
                mi.appendReplacement(sb, Matcher.quoteReplacement(mi.group()));
            } else {
                imgs.add(img);
                mi.appendReplacement(sb, Matcher.quoteReplacement("\u0001JLM" + (imgs.size() - 1) + "\u0001"));
            }
        }
        mi.appendTail(sb);
        return sb.toString();
    }

    private static String restoreImgs(String r, List<String> imgs) {
        Matcher m = Pattern.compile("\u0001JLM(\\d+)\u0001").matcher(r);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(imgs.get(Integer.parseInt(m.group(1)))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String latexToImg(String latex, boolean display) {
        try {
            String latexF = latex.trim();
            String key = "jlm_" + md5(latexF + "|" + display) + ".png";
            File dir = new File(System.getProperty("java.io.tmpdir"), "zhisuan_tex");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File f = new File(dir, key);
            if (!f.exists()) {
                int style = display ? TeXConstants.STYLE_DISPLAY : TeXConstants.STYLE_TEXT;
                TeXIcon icon = new TeXFormula(latexF).createTeXIcon(style, display ? 20 : 15);
                BufferedImage img = new BufferedImage(
                        Math.max(1, icon.getIconWidth()), Math.max(1, icon.getIconHeight()),
                        BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2 = img.createGraphics();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                icon.paintIcon(null, g2, 0, 0);
                g2.dispose();
                recolor(img);
                ImageIO.write(img, "png", f);
            }
            return "<img src='" + fileUrl(f.getAbsolutePath()) + "'"
                    + (display ? " style='margin:4px 0'" : " style='vertical-align:middle'") + ">";
        } catch (Throwable t) {
            return null;
        }
    }

    private static String md5(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** 将公式图（TeXIcon 固定黑色文字）整体染成正文浅色，保留透明底与抗锯齿 alpha */
    private static void recolor(BufferedImage img) {
        int fr = 0xDB, fg2 = 0xE7, fb = 0xFF;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int a = (img.getRGB(x, y) >>> 24) & 0xff;
                if (a > 0) img.setRGB(x, y, (a << 24) | (fr << 16) | (fg2 << 8) | fb);
            }
        }
    }

    /** Windows 相对/绝对路径 → file:/// URL（正斜杠化） */
    public static String fileUrl(String path) {
        String p = path.replace("\\", "/");
        if (p.matches("^[A-Za-z]:/.*")) return "file:///" + p;
        if (p.startsWith("/")) return "file://" + p;
        return p;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
