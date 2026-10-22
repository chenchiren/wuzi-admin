package com.relex.wuzi.service.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 短信 HTTP 桩：阿里云 dysmsapi 或通用 HTTP 网关（wuzi.sms.provider=http|aliyun）。
 * 未配置 enabled=true 或缺少必要参数时降级为日志；发送失败只记日志不抛出。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "wuzi.sms.provider", havingValue = "http")
public class HttpSmsNotifyService implements SmsNotifyService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 总开关，false 时只记日志 */
    @Value("${wuzi.sms.enabled:false}")
    private boolean enabled;
    /** 通用网关地址；provider=aliyun 时走阿里云 dysmsapi */
    @Value("${wuzi.sms.api-url:}")
    private String apiUrl;
    @Value("${wuzi.sms.access-key:}")
    private String accessKey;
    @Value("${wuzi.sms.secret-key:}")
    private String secretKey;
    @Value("${wuzi.sms.sign-name:}")
    private String signName;
    @Value("${wuzi.sms.template-code:}")
    private String templateCode;

    @Override
    public boolean send(String phone, String content) {
        if (phone == null || phone.isBlank()) {
            log.debug("[sms] 手机号为空，跳过");
            return false;
        }
        if (!enabled || apiUrl == null || apiUrl.isBlank()) {
            log.info("[sms:http:disabled] phone={} content={}", phone, content);
            return false;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            long ts = System.currentTimeMillis();
            body.put("phone", phone);
            body.put("content", content);
            body.put("signName", signName);
            body.put("templateCode", templateCode);
            body.put("accessKey", accessKey);
            body.put("timestamp", ts);
            // HMAC-SHA1 签名：HMAC(secret, accessKey + phone + content + timestamp)，Base64 编码
            if (secretKey != null && !secretKey.isBlank()) {
                body.put("signature", hmacSha1(secretKey,
                        accessKey + "|" + phone + "|" + content + "|" + ts));
            }
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.info("[sms:http] 发送成功 phone={} status={}", phone, resp.statusCode());
                return true;
            }
            log.warn("[sms:http] 发送失败 phone={} status={} body={}", phone, resp.statusCode(), resp.body());
            return false;
        } catch (Exception e) {
            log.warn("[sms:http] 发送异常 phone={} err={}", phone, e.getMessage());
            return false;
        }
    }

    private static String hmacSha1(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
