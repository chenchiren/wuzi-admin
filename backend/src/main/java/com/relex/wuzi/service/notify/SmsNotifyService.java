package com.relex.wuzi.service.notify;

/**
 * 短信通知通道。默认 Noop/日志实现，可选阿里云/通用 HTTP 桩。
 * 约定：失败只记日志，不阻断业务。
 */
public interface SmsNotifyService {

    /**
     * 发送短信。
     *
     * @param phone   目标手机号
     * @param content 短信正文
     * @return true 表示已尝试发送（含 mock/日志），false 表示未启用或跳过
     */
    boolean send(String phone, String content);
}
