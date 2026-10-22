package com.relex.wuzi.web.mobile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.SysUser;
import com.relex.wuzi.mapper.SysUserMapper;
import com.relex.wuzi.security.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉 H5 免登：POST /api/m/auth/dingtalk/login
 * <p>
 * mock：code=demo 直接发 token。真实模式读 wuzi.dingtalk.*（见 frontend/mobile/README.md）。
 * 与企微可插拔：两边互不影响，未配置密钥时均为 mock。
 */
@Slf4j
@RestController
@RequestMapping("/api/m/auth/dingtalk")
@RequiredArgsConstructor
public class DingTalkAuthController {

    public static final String MOCK_CODE = "demo";

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${wuzi.dingtalk.corp-id:}")
    private String corpId;
    @Value("${wuzi.dingtalk.agent-id:}")
    private String agentId;
    @Value("${wuzi.dingtalk.app-key:}")
    private String appKey;
    @Value("${wuzi.dingtalk.app-secret:}")
    private String appSecret;
    @Value("${wuzi.dingtalk.mock:true}")
    private boolean mockEnabled;

    @Data
    public static class LoginReq {
        private String code;
        private String authCode;
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginReq req) {
        String code = req.getCode() != null && !req.getCode().isBlank() ? req.getCode() : req.getAuthCode();
        if (code == null || code.isBlank()) {
            throw new BizException("钉钉 code 不能为空");
        }
        code = code.trim();
        boolean mock = isMockMode();
        String dingUserId;
        if (mock) {
            if (!MOCK_CODE.equalsIgnoreCase(code) && !code.toLowerCase().startsWith("mock")) {
                throw new BizException(401, "mock 模式仅接受 code=demo（或 mock*），真实钉钉 code 请配置 wuzi.dingtalk.* 并设 mock=false");
            }
            dingUserId = "mock-admin";
        } else {
            dingUserId = realUserId(code);
        }

        SysUser user;
        if (mock) {
            user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, "admin")
                    .last("LIMIT 1"));
            if (user == null) {
                user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                        .orderByAsc(SysUser::getId)
                        .last("LIMIT 1"));
            }
        } else {
            user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, dingUserId)
                    .last("LIMIT 1"));
        }
        if (user == null) {
            throw new BizException(mock ? "系统无可用用户，mock 免登失败" : "钉钉账号未绑定系统用户: " + dingUserId);
        }
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BizException(401, "用户已停用");
        }

        List<String> roles = userMapper.selectRoleCodes(user.getId());
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), roles);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("roles", roles);
        data.put("deptId", user.getDeptId());
        data.put("source", "DingTalk");
        data.put("mock", mock);
        data.put("dingUserId", dingUserId);
        data.put("mode", mock ? "mock" : "real");
        return R.ok(data);
    }

    @PostMapping("/status")
    public R<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "DingTalk");
        data.put("mock", isMockMode());
        data.put("mockCode", isMockMode() ? MOCK_CODE : null);
        return R.ok(data);
    }

    private boolean isMockMode() {
        return mockEnabled || appSecret == null || appSecret.isBlank();
    }

    /** 真实模式：authCode → userAccessToken → userinfo。 */
    private String realUserId(String authCode) {
        if (appKey == null || appKey.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new BizException("钉钉未配置 app-key/app-secret，无法免登");
        }
        try {
            HttpRequest tokenReq = HttpRequest.newBuilder(URI.create("https://api.dingtalk.com/v1.0/oauth2/userAccessToken"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                            "appKey", appKey,
                            "appSecret", appSecret,
                            "code", authCode,
                            "grantType", "authorization_code"
                    )), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> tokenResp = httpClient.send(tokenReq, HttpResponse.BodyHandlers.ofString());
            JsonNode tokenNode = objectMapper.readTree(tokenResp.body());
            String accessToken = tokenNode.path("accessToken").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new BizException(401, "钉钉 code 无效: " + tokenResp.body());
            }

            HttpRequest userReq = HttpRequest.newBuilder(URI.create("https://api.dingtalk.com/v1.0/contact/users/me"))
                    .header("x-acs-dingtalk-access-token", accessToken)
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> userResp = httpClient.send(userReq, HttpResponse.BodyHandlers.ofString());
            JsonNode userNode = objectMapper.readTree(userResp.body());
            String unionId = userNode.path("unionId").asText(null);
            String nick = userNode.path("nick").asText(null);
            String id = unionId != null && !unionId.isBlank() ? unionId : nick;
            if (id == null || id.isBlank()) {
                throw new BizException(401, "钉钉未返回用户标识");
            }
            return id;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("钉钉免登失败", e);
            throw new BizException("钉钉免登失败: " + e.getMessage());
        }
    }
}
