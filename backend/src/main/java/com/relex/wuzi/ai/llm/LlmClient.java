package com.relex.wuzi.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * OpenAI 兼容流式客户端（/chat/completions, stream=true）。
 * <p>实现要点：
 * <ul>
 *   <li>JDK HttpClient + InputStream 逐行读 SSE 帧，零新依赖；</li>
 *   <li>tool_calls 参数按 index 分片累积（跨 chunk 拼接）后整体返回；</li>
 *   <li>qwen3.5 是思考型模型，请求体固定带 enable_thinking:false，
 *       否则 max_tokens 全花在 reasoning 上、content 为空（已实测验证）；</li>
 *   <li>任何失败抛 {@link LlmException}，由上层熔断并降级。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private final LlmProperties props;
    private final ObjectMapper om;
    private HttpClient httpClient;

    @PostConstruct
    void init() {
        httpClient = HttpClient.newBuilder()
                // 强制 HTTP/1.1：默认的 HTTP/2 升级会让 vLLM(uvicorn/h11) 收到空 body（400
                // 'body: Field required'），curl/Python 均无此问题，属 Java HttpClient 特有兼容坑
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(Math.max(5, props.getTimeoutSeconds())))
                .build();
    }

    /** 一次流式调用的产出：工具调用列表 + token 统计（统计可能为 null） */
    public record StreamResult(List<LlmToolCall> toolCalls, Integer promptTokens, Integer completionTokens) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    /**
     * 流式对话。onDelta 接收正文增量；若模型请求工具调用，返回非空 toolCalls。
     */
    public StreamResult streamChat(List<Map<String, Object>> messages,
                                   List<Map<String, Object>> tools,
                                   Consumer<String> onDelta) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", props.getModel());
            body.put("messages", messages);
            if (tools != null && !tools.isEmpty()) {
                body.put("tools", tools);
                body.put("tool_choice", "auto");
            }
            body.put("stream", true);
            body.put("max_tokens", props.getMaxTokens());
            body.put("chat_template_kwargs", Map.of("enable_thinking", false));
            body.put("stream_options", Map.of("include_usage", true));

            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(props.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body), StandardCharsets.UTF_8));
            if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
                rb.header("Authorization", "Bearer " + props.getApiKey());
            }

            HttpResponse<InputStream> resp = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 300) {
                String detail = readQuietly(resp.body());
                log.warn("LLM HTTP {}: {}", resp.statusCode(), abbreviate(detail));
                throw new LlmException("LLM HTTP " + resp.statusCode());
            }
            return parseStream(resp.body(), onDelta);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    private StreamResult parseStream(InputStream in, Consumer<String> onDelta) throws IOException {
        Map<Integer, String> ids = new HashMap<>();
        Map<Integer, String> names = new HashMap<>();
        Map<Integer, StringBuilder> args = new HashMap<>();
        Integer promptTokens = null;
        Integer completionTokens = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if ("[DONE]".equals(payload)) {
                    break;
                }
                JsonNode node = om.readTree(payload);
                JsonNode usage = node.get("usage");
                if (usage != null && usage.isObject()) {
                    int pt = usage.path("prompt_tokens").asInt(0);
                    int ct = usage.path("completion_tokens").asInt(0);
                    if (pt > 0) {
                        promptTokens = pt;
                    }
                    if (ct > 0) {
                        completionTokens = ct;
                    }
                }
                JsonNode choices = node.path("choices");
                if (!choices.isArray() || choices.isEmpty()) {
                    continue;
                }
                JsonNode delta = choices.get(0).path("delta");
                String content = delta.path("content").asText(null);
                if (content != null && !content.isEmpty()) {
                    onDelta.accept(content);
                }
                JsonNode tcs = delta.path("tool_calls");
                if (tcs.isArray()) {
                    for (JsonNode tc : tcs) {
                        int idx = tc.path("index").asInt(0);
                        String id = tc.path("id").asText(null);
                        if (id != null && !id.isEmpty()) {
                            ids.put(idx, id);
                        }
                        JsonNode fn = tc.path("function");
                        String name = fn.path("name").asText(null);
                        if (name != null && !name.isEmpty()) {
                            names.put(idx, name);
                        }
                        String arg = fn.path("arguments").asText(null);
                        if (arg != null) {
                            args.computeIfAbsent(idx, k -> new StringBuilder()).append(arg);
                        }
                    }
                }
            }
        }

        List<LlmToolCall> calls = new ArrayList<>();
        for (Integer idx : new TreeMap<>(names).keySet()) {
            calls.add(new LlmToolCall(
                    ids.getOrDefault(idx, "call_" + idx),
                    names.get(idx),
                    args.getOrDefault(idx, new StringBuilder()).toString()));
        }
        return new StreamResult(calls, promptTokens, completionTokens);
    }

    private static String readQuietly(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
