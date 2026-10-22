package com.relex.wuzi.web.mobile;

import com.relex.wuzi.common.R;
import com.relex.wuzi.service.WeComAuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业微信 H5 免登：POST /api/m/auth/wechat-work/login
 * <p>
 * mock：code=demo 直接发 token。真实模式读 wuzi.wecom.*（见 frontend/mobile/README.md）。
 */
@RestController
@RequestMapping("/api/m/auth/wechat-work")
@RequiredArgsConstructor
public class WeComAuthController {

    private final WeComAuthService weComAuthService;

    @Data
    public static class LoginReq {
        /** 企微 OAuth code；mock 模式用 demo */
        private String code;
        /** 兼容前端 authCode 字段 */
        private String authCode;
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginReq req) {
        String code = req.getCode() != null && !req.getCode().isBlank() ? req.getCode() : req.getAuthCode();
        WeComAuthService.AuthResult result = weComAuthService.login(code);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", result.getToken());
        data.put("userId", result.getUserId());
        data.put("username", result.getUsername());
        data.put("realName", result.getRealName());
        data.put("roles", result.getRoles());
        data.put("deptId", result.getDeptId());
        data.put("source", result.getSource());
        data.put("mock", result.isMock());
        data.put("wecomUserId", result.getWecomUserId());
        data.put("mode", result.isMock() ? "mock" : "real");
        return R.ok(data);
    }

    /** 供前端探测是否 mock，便于隐藏/提示扫码入口 */
    @PostMapping("/status")
    public R<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "WECom");
        data.put("mock", weComAuthService.isMockMode());
        data.put("mockCode", weComAuthService.isMockMode() ? WeComAuthService.MOCK_CODE : null);
        return R.ok(data);
    }
}
