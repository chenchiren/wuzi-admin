package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.domain.SysUser;
import com.relex.wuzi.mapper.SysUserMapper;
import com.relex.wuzi.security.JwtUtil;
import lombok.Data;
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
import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业微信 H5 免登。
 * <p>
 * mock 模式（默认，或 wuzi.wecom.mock=true）：code=demo 直接签发本地 JWT。
 * 真实模式：读取 wuzi.wecom.corp-id / agent-id / secret，走企微 gettoken + getuserinfo。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeComAuthService {

    public static final String MOCK_CODE = "demo";

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${wuzi.wecom.corp-id:}")
    private String corpId;
    @Value("${wuzi.wecom.agent-id:}")
    private String agentId;
    @Value("${wuzi.wecom.secret:}")
    private String secret;
    /** 未配置密钥时默认 mock；显式 false 才走真实 OAuth。 */
    @Value("${wuzi.wecom.mock:true}")
    private boolean mockEnabled;

    @Data
    public static class AuthResult {
        private String source = "WECom";
        private boolean mock;
        private String token;
        private Long userId;
        private String username;
        private String realName;
        private List<String> roles;
        private Long deptId;
        private String wecomUserId;
    }

    public AuthResult login(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException("企微 code 不能为空");
        }
        if (isMockMode()) {
            return mockLogin(code.trim());
        }
        return realLogin(code.trim());
    }

    public boolean isMockMode() {
        return mockEnabled || secret == null || secret.isBlank();
    }

    private AuthResult mockLogin(String code) {
        if (!MOCK_CODE.equalsIgnoreCase(code) && !code.toLowerCase().startsWith("mock")) {
            throw new BizException(401, "mock 模式仅接受 code=demo（或 mock*），真实企微 code 请配置 wuzi.wecom.* 并设 mock=false");
        }
        SysUser user = resolveDemoUser();
        return buildResult(user, true, "mock-" + user.getUsername());
    }

    private AuthResult realLogin(String code) {
        if (corpId == null || corpId.isBlank() || secret == null || secret.isBlank()) {
            throw new BizException("企业微信未配置 corp-id/secret，无法免登");
        }
        try {
            String accessToken = fetchAccessToken();
            String wecomUserId = fetchUserId(accessToken, code);
            SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, wecomUserId)
                    .last("LIMIT 1"));
            if (user == null) {
                throw new BizException(401, "企微账号未绑定系统用户: " + wecomUserId);
            }
            return buildResult(user, false, wecomUserId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("企微免登失败", e);
            throw new BizException("企微免登失败: " + e.getMessage());
        }
    }

    private String fetchAccessToken() throws Exception {
        String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid="
                + enc(corpId) + "&corpsecret=" + enc(secret);
        JsonNode node = getJson(url);
        int errcode = node.path("errcode").asInt(0);
        if (errcode != 0) {
            throw new BizException("企微 gettoken 失败: " + node.path("errmsg").asText());
        }
        return node.path("access_token").asText();
    }

    private String fetchUserId(String accessToken, String code) throws Exception {
        String url = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token="
                + enc(accessToken) + "&code=" + enc(code);
        JsonNode node = getJson(url);
        int errcode = node.path("errcode").asInt(0);
        if (errcode != 0) {
            throw new BizException(401, "企微 code 无效: " + node.path("errmsg").asText());
        }
        String userId = node.path("userid").asText(null);
        if (userId == null || userId.isBlank()) {
            throw new BizException(401, "企微未返回 userid（可能为外部联系人）");
        }
        return userId;
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private SysUser resolveDemoUser() {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "admin")
                .last("LIMIT 1"));
        if (user == null) {
            user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .orderByAsc(SysUser::getId)
                    .last("LIMIT 1"));
        }
        if (user == null) {
            throw new BizException("系统无可用用户，mock 免登失败");
        }
        return user;
    }

    private AuthResult buildResult(SysUser user, boolean mock, String wecomUserId) {
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BizException(401, "用户已停用");
        }
        List<String> roles = userMapper.selectRoleCodes(user.getId());
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), roles);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        AuthResult out = new AuthResult();
        out.setMock(mock);
        out.setToken(token);
        out.setUserId(user.getId());
        out.setUsername(user.getUsername());
        out.setRealName(user.getRealName());
        out.setRoles(roles);
        out.setDeptId(user.getDeptId());
        out.setWecomUserId(wecomUserId);
        return out;
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}
