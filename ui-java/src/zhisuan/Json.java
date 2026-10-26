package zhisuan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 极简 JSON 解析与序列化（零依赖，供 GUI 与 Python 服务层通信） */
public final class Json {
    private Json() {}

    public static Object parse(String s) {
        return new Parser(s).parseValue();
    }

    private static final class Parser {
        private final String s;
        private int i = 0;

        Parser(String s) { this.s = s == null ? "" : s; }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) return null;
            char c = s.charAt(i);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                default:
                    if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
                    if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
                    if (s.startsWith("null", i)) { i += 4; return null; }
                    return parseNumber();
            }
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // '{'
            skipWs();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (i < s.length()) {
                skipWs();
                String key = parseString();
                skipWs();
                if (i < s.length() && s.charAt(i) == ':') i++;
                m.put(key, parseValue());
                skipWs();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == '}') i++;
                break;
            }
            return m;
        }

        private List<Object> parseArray() {
            List<Object> l = new ArrayList<>();
            i++; // '['
            skipWs();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            while (i < s.length()) {
                l.add(parseValue());
                skipWs();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == ']') i++;
                break;
            }
            return l;
        }

        private String parseString() {
            StringBuilder sb = new StringBuilder();
            if (i < s.length() && s.charAt(i) == '"') i++;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c != '\\') { sb.append(c); continue; }
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (i + 4 <= s.length()) {
                            try { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); }
                            catch (NumberFormatException ignored) { }
                            i += 4;
                        }
                        break;
                    default: sb.append(e);
                }
            }
            return sb.toString();
        }

        private Double parseNumber() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') i++;
                else break;
            }
            try { return Double.parseDouble(s.substring(start, i)); }
            catch (Exception e) { return 0.0; }
        }
    }

    public static String stringify(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof String || o instanceof Character) { writeString(sb, o.toString()); return; }
        if (o instanceof Boolean || o instanceof Number) { sb.append(o); return; }
        if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
            return;
        }
        if (o instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object v : (Iterable<?>) o) {
                if (!first) sb.append(',');
                first = false;
                write(sb, v);
            }
            sb.append(']');
            return;
        }
        writeString(sb, String.valueOf(o));
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ---------- 便捷取值 ----------
    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    public static String str(Object o) { return o == null ? "" : o.toString(); }

    public static String str(Object o, String dflt) { return o == null ? dflt : o.toString(); }

    public static double num(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : 0.0; }

    public static int Int(Object o) { return o instanceof Number ? ((Number) o).intValue() : 0; }

    public static boolean bool(Object o) { return Boolean.TRUE.equals(o); }
}
