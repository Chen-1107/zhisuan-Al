package zhisuan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** agent_server.py 的 HTTP + SSE 客户端（零依赖，HttpURLConnection 实现） */
public class ApiClient {

    public interface SseListener {
        /** 收到一条服务端事件（type 已从 payload 中剥离） */
        void onEvent(String type, Map<String, Object> data);

        /** 流结束（服务端关闭连接） */
        void onDone();

        /** 传输/服务端错误 */
        void onError(String message);
    }

    private volatile String baseUrl;

    public ApiClient(String baseUrl) { this.baseUrl = baseUrl; }

    public String getBase() { return baseUrl; }

    public void setBase(String url) {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) return;
        if (!u.startsWith("http")) u = "http://" + u;
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        this.baseUrl = u;
    }

    /** 同步 GET，返回解析后的 JSON 对象；非 2xx 抛 IOException（含服务端 error 信息） */
    public Object get(String path) throws IOException {
        return request("GET", path, null);
    }

    /** 同步 GET 二进制（如后端渲染的 PNG 结构图），返回原始字节 */
    public byte[] getBinary(String path) throws IOException {
        HttpURLConnection conn = open("GET", path);
        conn.setReadTimeout(120_000);
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String err = readAll(conn.getErrorStream());
                throw new IOException("HTTP " + code + ": " + extractError(err));
            }
            try (InputStream in = conn.getInputStream();
                 java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
                return bos.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }

    /** 同步 POST（JSON body），返回解析后的 JSON 对象 */
    public Object post(String path, Object body) throws IOException {
        return request("POST", path, body);
    }

    /** 异步 POST 并以 SSE 方式消费响应（在后台线程回调） */
    public void postSse(String path, Object body, SseListener listener) {
        Thread t = new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = open("POST", path);
                conn.setReadTimeout(600_000); // LLM 推理可能较慢
                writeBody(conn, Json.stringify(body));
                int code = conn.getResponseCode();
                if (code >= 400) {
                    String err = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
                    listener.onError("HTTP " + code + ": " + extractError(err));
                    return;
                }
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.isEmpty() || !line.startsWith("data:")) continue;
                        String payload = line.substring(5).trim();
                        if (payload.isEmpty()) continue;
                        Map<String, Object> m = Json.obj(Json.parse(payload));
                        String type = Json.str(m.remove("type"));
                        listener.onEvent(type, m);
                    }
                }
                listener.onDone();
            } catch (Exception e) {
                listener.onError(e.getMessage() == null ? e.toString() : e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "sse-" + path);
        t.setDaemon(true);
        t.start();
    }

    // ---------- 内部 ----------

    private Object request(String method, String path, Object body) throws IOException {
        HttpURLConnection conn = open(method, path);
        conn.setReadTimeout(120_000);
        try {
            if (body != null) writeBody(conn, Json.stringify(body));
            int code = conn.getResponseCode();
            String text = readAll(code < 400 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + extractError(text));
            return Json.parse(text);
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection open(String method, String path) throws IOException {
        URL url = new URL(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    private void writeBody(HttpURLConnection conn, String json) throws IOException {
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
    }

    private String readAll(InputStream in) {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private String extractError(String body) {
        if (body == null || body.isEmpty()) return "";
        try {
            Object e = Json.obj(Json.parse(body)).get("error");
            return e == null ? body : e.toString();
        } catch (Exception ex) {
            return body;
        }
    }
}
