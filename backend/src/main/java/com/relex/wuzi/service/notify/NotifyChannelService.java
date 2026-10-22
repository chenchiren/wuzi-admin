package com.relex.wuzi.service.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relex.wuzi.domain.SysUser;
import com.relex.wuzi.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 多通道通知：站内信由 MessageService 负责，本服务负责外推通道。
 * 企微应用消息 / 钉钉工作通知 / SMTP 邮件 / 短信。
 * 通道未配置或 mock 时降级为日志，外发失败只记日志，不阻断业务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyChannelService {

    public static final String CHANNEL_INAPP = "INAPP";
    public static final String CHANNEL_WECOM = "WECOM";
    public static final String CHANNEL_DINGTALK = "DINGTALK";
    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_SMS = "SMS";

    private final SysUserMapper userMapper;
    private final MailNotifyService mailNotifyService;
    private final SmsNotifyService smsNotifyService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ---------- 企微应用消息 ----------
    @Value("${wuzi.wecom.corp-id:}")
    private String wecomCorpId;
    @Value("${wuzi.wecom.agent-id:}")
    private String wecomAgentId;
    @Value("${wuzi.wecom.secret:}")
    private String wecomSecret;
    @Value("${wuzi.wecom.mock:true}")
    private boolean wecomMock;
    @Value("${wuzi.notify.wecom-enabled:false}")
    private boolean wecomEnabled;

    /** access_token 缓存（企微 token 有效期 7200s，提前 300s 刷新） */
    private volatile String wecomAccessToken;
    private volatile long wecomTokenExpireAt;

    // ---------- 钉钉工作通知 ----------
    @Value("${wuzi.dingtalk.app-key:}")
    private String dingtalkAppKey;
    @Value("${wuzi.dingtalk.app-secret:}")
    private String dingtalkAppSecret;
    @Value("${wuzi.dingtalk.agent-id:}")
    private String dingtalkAgentId;
    @Value("${wuzi.dingtalk.mock:true}")
    private boolean dingtalkMock;
    @Value("${wuzi.notify.dingtalk-enabled:false}")
    private boolean dingtalkEnabled;

    private volatile String dingtalkAccessToken;
    private volatile long dingtalkTokenExpireAt;

    // ---------- 邮件 / 短信开关 ----------
    @Value("${wuzi.notify.email-enabled:false}")
    private boolean emailEnabled;
    @Value("${wuzi.notify.email-from:noreply@local}")
    private String emailFrom;
    @Value("${wuzi.notify.sms-enabled:false}")
    private boolean smsEnabled;

    /**
     * 向用户外推一条通知（站内信之外）。
     *
     * @param channels 目标通道；空则按配置自动选择已启用通道
     * @return 实际尝试的通道列表
     */
    public List<String> push(Long userId, String title, String content, List<String> channels) {
        List<String> tried = new ArrayList<>();
        List<String> targets = (channels == null || channels.isEmpty())
                ? defaultChannels()
                : channels;
        if (targets.isEmpty()) {
            return tried;
        }
        SysUser user = resolveUser(userId);
        String text = (title == null ? "" : title) + "\n" + (content == null ? "" : content);
        for (String ch : targets) {
            try {
                switch (ch) {
                    case CHANNEL_WECOM -> {
                        tried.add(CHANNEL_WECOM);
                        pushWecom(user, text);
                    }
                    case CHANNEL_DINGTALK -> {
                        tried.add(CHANNEL_DINGTALK);
                        pushDingTalk(user, text);
                    }
                    case CHANNEL_EMAIL -> {
                        tried.add(CHANNEL_EMAIL);
                        pushEmail(user, title, content);
                    }
                    case CHANNEL_SMS -> {
                        tried.add(CHANNEL_SMS);
                        pushSms(user, text);
                    }
                    default -> log.debug("[notify] skip channel {}", ch);
                }
            } catch (Exception e) {
                // 外发失败不阻断主流程
                log.warn("[notify] channel={} user={} 发送失败: {}", ch, userId, e.getMessage());
            }
        }
        return tried;
    }

    // ---------- 各通道实现 ----------

    /** 企微应用消息：text 消息，userId → userid（取 username 映射） */
    private void pushWecom(SysUser user, String text) {
        if (!wecomEnabled) {
            log.info("[notify:wecom:disabled] user={} text={}", user == null ? null : user.getId(), text);
            return;
        }
        if (wecomMock || isBlank(wecomSecret)) {
            log.info("[notify:wecom:mock] user={} text={}", user == null ? null : user.getId(), text);
            return;
        }
        String userId = wecomUserId(user);
        if (isBlank(userId)) {
            log.warn("[notify:wecom] 用户无企微 userid 映射，跳过 userId={}", user == null ? null : user.getId());
            return;
        }
        try {
            String token = getWecomAccessToken();
            ObjectNode body = objectMapper.createObjectNode();
            body.put("touser", userId);
            body.put("msgtype", "text");
            body.put("agentid", Integer.parseInt(wecomAgentId.trim()));
            body.put("text", objectMapper.createObjectNode().put("content", text));
            JsonNode resp = postJson("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token="
                    + enc(token), body);
            int errcode = resp.path("errcode").asInt(0);
            if (errcode != 0) {
                log.warn("[notify:wecom] 发送失败 userId={} errcode={} errmsg={}",
                        userId, errcode, resp.path("errmsg").asText());
            } else {
                log.info("[notify:wecom] 发送成功 userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("[notify:wecom] 发送异常 userId={}: {}", wecomUserId(user), e.getMessage());
        }
    }

    /** 钉钉工作通知：text 消息 */
    private void pushDingTalk(SysUser user, String text) {
        if (!dingtalkEnabled) {
            log.info("[notify:dingtalk:disabled] user={} text={}", user == null ? null : user.getId(), text);
            return;
        }
        if (dingtalkMock || isBlank(dingtalkAppKey) || isBlank(dingtalkAppSecret)) {
            log.info("[notify:dingtalk:mock] user={} text={}", user == null ? null : user.getId(), text);
            return;
        }
        String userId = dingtalkUserId(user);
        if (isBlank(userId)) {
            log.warn("[notify:dingtalk] 用户无钉钉 userid 映射，跳过 userId={}", user == null ? null : user.getId());
            return;
        }
        try {
            String token = getDingTalkAccessToken();
            ObjectNode body = objectMapper.createObjectNode();
            body.put("agent_id", dingtalkAgentId == null ? "" : dingtalkAgentId.trim());
            body.put("userid_list", userId);
            ObjectNode msg = body.putObject("msg");
            msg.put("msgtype", "text");
            msg.putObject("text").put("content", text);
            JsonNode resp = postJson("https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2?access_token="
                    + enc(token), body);
            int errcode = resp.path("errcode").asInt(0);
            if (errcode != 0) {
                log.warn("[notify:dingtalk] 发送失败 userId={} errcode={} errmsg={}",
                        userId, errcode, resp.path("errmsg").asText());
            } else {
                log.info("[notify:dingtalk] 发送成功 userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("[notify:dingtalk] 发送异常 userId={}: {}", dingtalkUserId(user), e.getMessage());
        }
    }

    private void pushEmail(SysUser user, String title, String content) {
        if (!emailEnabled) {
            log.info("[notify:email:disabled] user={} title={}", user == null ? null : user.getId(), title);
            return;
        }
        String to = user == null ? null : user.getEmail();
        if (isBlank(to)) {
            log.warn("[notify:email] 用户无邮箱，跳过 userId={}", user == null ? null : user.getId());
            return;
        }
        boolean ok = mailNotifyService.send(to, title, content);
        log.info("[notify:email] from={} to={} title={} ok={}", emailFrom, to, title, ok);
    }

    private void pushSms(SysUser user, String text) {
        if (!smsEnabled) {
            log.info("[notify:sms:disabled] user={} text={}", user == null ? null : user.getId(), text);
            return;
        }
        String phone = user == null ? null : user.getPhone();
        if (isBlank(phone)) {
            log.warn("[notify:sms] 用户无手机号，跳过 userId={}", user == null ? null : user.getId());
            return;
        }
        boolean ok = smsNotifyService.send(phone, text);
        log.info("[notify:sms] phone={} ok={}", phone, ok);
    }

    // ---------- token 缓存 ----------

    /** 企微 access_token：缓存至过期前 5 分钟 */
    public String getWecomAccessToken() throws Exception {
        long now = System.currentTimeMillis();
        if (wecomAccessToken != null && now < wecomTokenExpireAt) {
            return wecomAccessToken;
        }
        synchronized (this) {
            if (wecomAccessToken != null && System.currentTimeMillis() < wecomTokenExpireAt) {
                return wecomAccessToken;
            }
            String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid="
                    + enc(wecomCorpId) + "&corpsecret=" + enc(wecomSecret);
            JsonNode node = getJson(url);
            int errcode = node.path("errcode").asInt(0);
            if (errcode != 0) {
                throw new IllegalStateException("企微 gettoken 失败: " + node.path("errmsg").asText());
            }
            String token = node.path("access_token").asText();
            long expiresIn = node.path("expires_in").asLong(7200);
            wecomAccessToken = token;
            wecomTokenExpireAt = System.currentTimeMillis() + Math.max(60, expiresIn - 300) * 1000L;
            log.info("[notify:wecom] access_token 已刷新，{}s 后过期", expiresIn);
            return token;
        }
    }

    /** 钉钉 access_token：缓存至过期前 5 分钟 */
    public String getDingTalkAccessToken() throws Exception {
        long now = System.currentTimeMillis();
        if (dingtalkAccessToken != null && now < dingtalkTokenExpireAt) {
            return dingtalkAccessToken;
        }
        synchronized (this) {
            if (dingtalkAccessToken != null && System.currentTimeMillis() < dingtalkTokenExpireAt) {
                return dingtalkAccessToken;
            }
            String url = "https://oapi.dingtalk.com/gettoken?appkey="
                    + enc(dingtalkAppKey) + "&appsecret=" + enc(dingtalkAppSecret);
            JsonNode node = getJson(url);
            int errcode = node.path("errcode").asInt(0);
            if (errcode != 0) {
                throw new IllegalStateException("钉钉 gettoken 失败: " + node.path("errmsg").asText());
            }
            String token = node.path("access_token").asText();
            long expiresIn = node.path("expires_in").asLong(7200);
            dingtalkAccessToken = token;
            dingtalkTokenExpireAt = System.currentTimeMillis() + Math.max(60, expiresIn - 300) * 1000L;
            log.info("[notify:dingtalk] access_token 已刷新，{}s 后过期", expiresIn);
            return token;
        }
    }

    // ---------- 内部工具 ----------

    private List<String> defaultChannels() {
        List<String> list = new ArrayList<>();
        if (wecomEnabled) list.add(CHANNEL_WECOM);
        if (dingtalkEnabled) list.add(CHANNEL_DINGTALK);
        if (emailEnabled) list.add(CHANNEL_EMAIL);
        if (smsEnabled) list.add(CHANNEL_SMS);
        return list;
    }

    private SysUser resolveUser(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return userMapper.selectById(userId);
        } catch (Exception e) {
            log.debug("[notify] 用户查询失败 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /** 企微 userid：与 WeComAuthService 绑定一致，默认用 username */
    private String wecomUserId(SysUser user) {
        return user == null ? null : user.getUsername();
    }

    /** 钉钉 userid：默认用 username（生产可扩展独立映射） */
    private String dingtalkUserId(SysUser user) {
        return user == null ? null : user.getUsername();
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private JsonNode postJson(String url, ObjectNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
