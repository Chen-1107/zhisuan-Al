package com.zhisuan.workflow.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小 JSON 解析器（无第三方依赖）：对象→LinkedHashMap、数组→ArrayList、
 * 字符串→String、数字→Double、布尔→Boolean。仅需解析 /api/workflow/status 的扁平结构。
 */
public final class MiniJson {

    private final String src;
    private int pos;

    private MiniJson(String src) {
        this.src = src;
    }

    public static Object parse(String text) {
        MiniJson p = new MiniJson(text.trim());
        Object v = p.value();
        return v;
    }

    private char peek() {
        skipWs();
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private void skipWs() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private Object value() {
        skipWs();
        if (pos >= src.length()) {
            return null;
        }
        char c = src.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return number();
        }
    }

    private void expect(String word) {
        skipWs();
        if (!src.startsWith(word, pos)) {
            throw new IllegalStateException("bad literal at " + pos);
        }
        pos += word.length();
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWs();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWs();
            String key = string();
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ':') {
                pos++;
            }
            map.put(key, value());
            skipWs();
            char c = pos < src.length() ? src.charAt(pos) : '\0';
            if (c == ',') {
                pos++;
            } else if (c == '}') {
                pos++;
                break;
            } else {
                break;
            }
        }
        return map;
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWs();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(value());
            skipWs();
            char c = pos < src.length() ? src.charAt(pos) : '\0';
            if (c == ',') {
                pos++;
            } else if (c == ']') {
                pos++;
                break;
            } else {
                break;
            }
        }
        return list;
    }

    private String string() {
        skipWs();
        if (pos >= src.length() || src.charAt(pos) != '"') {
            return "";
        }
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '"') {
                pos++;
                break;
            }
            if (c == '\\' && pos + 1 < src.length()) {
                char n = src.charAt(pos + 1);
                switch (n) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (pos + 5 < src.length()) {
                            sb.append((char) Integer.parseInt(src.substring(pos + 2, pos + 6), 16));
                            pos += 4;
                        }
                        break;
                    default:
                        sb.append(n);
                }
                pos += 2;
            } else {
                sb.append(c);
                pos++;
            }
        }
        return sb.toString();
    }

    private Object number() {
        skipWs();
        int begin = pos;
        boolean isDouble = false;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E') {
                isDouble = true;
                pos++;
            } else {
                break;
            }
        }
        String num = src.substring(begin, pos).trim();
        try {
            if (isDouble || num.contains(".") || num.contains("e") || num.contains("E")) {
                return Double.parseDouble(num);
            }
            return Long.parseLong(num);
        } catch (NumberFormatException e) {
            return num;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object v) {
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object v) {
        return (List<Object>) v;
    }

    public static String asString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}