package com.relex.wuzi.service.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 短信默认实现：仅记录日志（Noop）。
 * wuzi.sms.provider=noop|空 时启用本实现。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "wuzi.sms.provider", havingValue = "noop", matchIfMissing = true)
public class LoggingSmsNotifyService implements SmsNotifyService {

    @Override
    public boolean send(String phone, String content) {
        if (phone == null || phone.isBlank()) {
            log.debug("[sms] 手机号为空，跳过 content={}", content);
            return false;
        }
        log.info("[sms:noop] phone={} content={}", phone, content);
        return true;
    }
}
