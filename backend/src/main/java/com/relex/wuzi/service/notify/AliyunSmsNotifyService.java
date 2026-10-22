package com.relex.wuzi.service.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 短信阿里云桩：wuzi.sms.provider=aliyun 时启用。
 * 仅预留 dysmsapi 调用骨架；未配置 enabled=true 时降级日志，失败不阻断业务。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "wuzi.sms.provider", havingValue = "aliyun")
public class AliyunSmsNotifyService implements SmsNotifyService {

    @Value("${wuzi.sms.enabled:false}")
    private boolean enabled;
    @Value("${wuzi.sms.access-key:}")
    private String accessKey;
    @Value("${wuzi.sms.secret-key:}")
    private String secretKey;
    @Value("${wuzi.sms.sign-name:}")
    private String signName;
    @Value("${wuzi.sms.template-code:}")
    private String templateCode;
    @Value("${wuzi.sms.api-url:https://dysmsapi.aliyuncs.com/}")
    private String apiUrl;

    @Override
    public boolean send(String phone, String content) {
        if (phone == null || phone.isBlank()) {
            log.debug("[sms:aliyun] 手机号为空，跳过");
            return false;
        }
        if (!enabled || isBlank(accessKey) || isBlank(secretKey)) {
            log.info("[sms:aliyun:disabled] phone={} content={} (需配置 wuzi.sms.enabled=true 与密钥)", phone, content);
            return false;
        }
        // 预留：正式接入时按 dysmsapi 签名并调用 SendSms
        log.info("[sms:aliyun:stub] phone={} sign={} template={} content={} (调用占位，未真正发送)",
                phone, signName, templateCode, content);
        return true;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
