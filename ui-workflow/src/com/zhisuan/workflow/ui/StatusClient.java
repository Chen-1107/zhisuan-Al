package com.zhisuan.workflow.ui;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轮询 agent_server.py 的 GET /api/workflow/status，返回值解析为 节点id → NodeRemoteState。
 * 网络调用应在线程中执行（勿阻塞 EDT）。
 */
public final class StatusClient {

    private final String baseUrl;
    private final int timeoutMs;

    public StatusClient(String baseUrl, int timeoutMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeoutMs = timeoutMs;
    }

    public String getEndpoint() {
        return baseUrl + "/api/workflow/status";
    }

    /** 阻塞拉取并解析；失败返回空 Map。 */
    public Map<String, NodeRemoteState> poll() {
        String json = httpGet(getEndpoint(), timeoutMs);
        if (json == null || json.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Map<String, NodeRemoteState> out = new LinkedHashMap<>();
        try {
            Object root = MiniJson.parse(json);
            Object nodesObj = MiniJson.asObject(root).get("nodes");
            if (!(nodesObj instanceof Map)) {
                return out;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> nodes = (Map<String, Object>) nodesObj;
            for (Map.Entry<String, Object> en : nodes.entrySet()) {
                if (!(en.getValue() instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> v = (Map<String, Object>) en.getValue();
                String status = MiniJson.asString(v.get("status"));
                String message = MiniJson.asString(v.get("message"));
                String energy = MiniJson.asString(v.get("energy"));
                List<String> tail = new ArrayList<>();
                Object lt = v.get("log_tail");
                if (lt instanceof List) {
                    for (Object o : (List<?>) lt) {
                        if (o != null) {
                            tail.add(String.valueOf(o));
                        }
                    }
                }
                String program = "", inputFormat = "", analysis = "";
                Object det = v.get("detail");
                if (det instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> d = (Map<String, Object>) det;
                    program = MiniJson.asString(d.get("program"));
                    inputFormat = MiniJson.asString(d.get("input_format"));
                    analysis = MiniJson.asString(d.get("analysis"));
                }
                out.put(en.getKey(), new NodeRemoteState(
                        NodeRemoteState.parseStatus(status), message, energy, tail,
                        program, inputFormat, analysis));
            }
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
        return out;
    }

    /** 简单 GET 返回响应文本；失败返回 null。（供任务/结构子客户端复用） */
    static String httpGet(String urlStr, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300
                    ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}