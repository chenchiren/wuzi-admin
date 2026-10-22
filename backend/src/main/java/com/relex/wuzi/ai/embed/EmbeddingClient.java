package com.relex.wuzi.ai.embed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relex.wuzi.ai.llm.LlmProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding 客户端：Ollama OpenAI 兼容端点 /v1/embeddings（bge-m3）。
 * <p>强制 HTTP/1.1（与 LlmClient 同款教训：H2 升级会丢 body）；
 * 服务不可用时抛 {@link EmbeddingException}，调用方静默跳过向量级——
 * 向量是增值层，别名/LIKE 永不受影响。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    private final LlmProperties props;
    private final ObjectMapper om;
    private HttpClient httpClient;

    @PostConstruct
    void init() {
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static class EmbeddingException extends RuntimeException {
        public EmbeddingException(String message) { super(message); }
    }

    /** 单条向量 */
    public float[] embed(String text) {
        List<float[]> r = embedBatch(List.of(text));
        return r.get(0);
    }

    /** 批量向量（input 数组，一次请求） */
    public List<float[]> embedBatch(List<String> texts) {
        var cfg = props.getEmbedding();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", cfg.getModel());
            body.put("input", texts);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getBaseUrl() + "/embeddings"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new EmbeddingException("embedding HTTP " + resp.statusCode());
            }
            JsonNode data = om.readTree(resp.body()).path("data");
            List<float[]> out = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode arr = item.path("embedding");
                float[] v = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    v[i] = (float) arr.get(i).asDouble();
                }
                out.add(v);
            }
            if (out.size() != texts.size()) {
                throw new EmbeddingException("embedding 数量不匹配: " + out.size() + "/" + texts.size());
            }
            return out;
        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingException("embedding 调用失败: " + e.getMessage());
        }
    }

    /** 双余弦归一化后的点积（bge-m3 输出已归一化，点积即余弦） */
    public static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length && i < b.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-9);
    }
}
