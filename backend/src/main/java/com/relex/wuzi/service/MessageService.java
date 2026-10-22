package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.domain.SysConfig;
import com.relex.wuzi.domain.SysMessage;
import com.relex.wuzi.mapper.SysConfigMapper;
import com.relex.wuzi.mapper.SysMessageMapper;
import com.relex.wuzi.service.notify.NotifyChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 消息中心：站内信发送 / 列表 / 已读。
 * 站内信落库后按 sys_config（notify.wecom / notify.mail / notify.sms，值 1/true 开）
 * 或配置 wuzi.notify.*-enabled 决定是否外推企微/钉钉/邮件/短信。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    public static final String BIZ_APPROVAL = "APPROVAL";

    /** sys_config 外发开关键 */
    public static final String CFG_NOTIFY_WECOM = "notify.wecom";
    public static final String CFG_NOTIFY_DINGTALK = "notify.dingtalk";
    public static final String CFG_NOTIFY_MAIL = "notify.mail";
    public static final String CFG_NOTIFY_SMS = "notify.sms";

    private final SysMessageMapper messageMapper;
    private final SysConfigMapper configMapper;
    private final NotifyChannelService notifyChannelService;

    /** sys_config 未配置对应键时，回退到 wuzi.notify.*-enabled */
    @Value("${wuzi.notify.wecom-enabled:false}")
    private boolean wecomEnabledDefault;
    @Value("${wuzi.notify.dingtalk-enabled:false}")
    private boolean dingtalkEnabledDefault;
    @Value("${wuzi.notify.email-enabled:false}")
    private boolean emailEnabledDefault;
    @Value("${wuzi.notify.sms-enabled:false}")
    private boolean smsEnabledDefault;

    /** 内部发送单条消息 */
    public SysMessage send(Long receiverId, String title, String content, String bizType, Long bizId) {
        SysMessage msg = new SysMessage();
        msg.setReceiverId(receiverId);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setBizType(bizType);
        msg.setBizId(bizId);
        msg.setIsRead(0);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        pushExternal(receiverId, title, content);
        return msg;
    }

    /** 内部批量发送（每个接收人一条） */
    public void sendToUsers(Collection<Long> receiverIds, String title, String content, String bizType, Long bizId) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        for (Long rid : receiverIds) {
            if (rid != null) {
                send(rid, title, content, bizType, bizId);
            }
        }
    }

    public PageResult<SysMessage> page(Long receiverId, long page, long size, Boolean isRead) {
        LambdaQueryWrapper<SysMessage> qw = new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getReceiverId, receiverId)
                .eq(isRead != null, SysMessage::getIsRead, isRead == null ? 0 : (isRead ? 1 : 0))
                .orderByDesc(SysMessage::getId);
        Page<SysMessage> p = messageMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    public long unreadCount(Long receiverId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getReceiverId, receiverId)
                .eq(SysMessage::getIsRead, 0));
    }

    /** 标记单条已读 */
    public void markRead(Long id, Long receiverId) {
        SysMessage msg = messageMapper.selectById(id);
        if (msg == null || !receiverId.equals(msg.getReceiverId())) {
            return;
        }
        msg.setIsRead(1);
        messageMapper.updateById(msg);
    }

    /** 全部已读 */
    public void markAllRead(Long receiverId) {
        messageMapper.update(null, new LambdaUpdateWrapper<SysMessage>()
                .eq(SysMessage::getReceiverId, receiverId)
                .eq(SysMessage::getIsRead, 0)
                .set(SysMessage::getIsRead, 1));
    }

    public List<SysMessage> listByBiz(String bizType, Long bizId) {
        return messageMapper.selectList(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getBizType, bizType)
                .eq(SysMessage::getBizId, bizId)
                .orderByAsc(SysMessage::getId));
    }

    /**
     * 站内信落库后按 sys_config 决定是否外推。
     * 外推失败只记日志，不影响站内信与业务主流程。
     */
    private void pushExternal(Long receiverId, String title, String content) {
        try {
            List<String> channels = enabledChannels();
            if (channels.isEmpty()) {
                return;
            }
            notifyChannelService.push(receiverId, title, content, channels);
        } catch (Exception e) {
            log.warn("[msg] 外推通道失败（忽略）receiverId={} err={}", receiverId, e.getMessage());
        }
    }

    /** sys_config 键优先；键不存在时回退 wuzi.notify.*-enabled 配置 */
    private List<String> enabledChannels() {
        List<String> channels = new ArrayList<>();
        if (isOn(CFG_NOTIFY_WECOM, wecomEnabledDefault)) {
            channels.add(NotifyChannelService.CHANNEL_WECOM);
        }
        if (isOn(CFG_NOTIFY_DINGTALK, dingtalkEnabledDefault)) {
            channels.add(NotifyChannelService.CHANNEL_DINGTALK);
        }
        if (isOn(CFG_NOTIFY_MAIL, emailEnabledDefault)) {
            channels.add(NotifyChannelService.CHANNEL_EMAIL);
        }
        if (isOn(CFG_NOTIFY_SMS, smsEnabledDefault)) {
            channels.add(NotifyChannelService.CHANNEL_SMS);
        }
        return channels;
    }

    /** sys_config 显式配置优先，否则用配置默认值 */
    private boolean isOn(String key, boolean fallback) {
        Boolean cfg = readCfg(key);
        return cfg != null ? cfg : fallback;
    }

    /** 读 sys_config；值 1/true/on/yes 开，0/false/off/no 关；键不存在返回 null */
    private Boolean readCfg(String key) {
        try {
            SysConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, key)
                    .last("limit 1"));
            if (cfg == null || cfg.getConfigValue() == null) {
                return null;
            }
            String v = cfg.getConfigValue().trim();
            if (v.isEmpty()) {
                return null;
            }
            return "1".equals(v) || "true".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
        } catch (Exception e) {
            log.debug("[msg] 读取 sys_config {} 失败: {}", key, e.getMessage());
            return null;
        }
    }
}
