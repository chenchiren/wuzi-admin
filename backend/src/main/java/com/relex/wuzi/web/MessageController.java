package com.relex.wuzi.web;

import com.relex.wuzi.annotation.LoginOnly;
import com.relex.wuzi.annotation.RequirePerm;

import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.SysMessage;
import com.relex.wuzi.service.MessageService;
import com.relex.wuzi.web.annotation.OpLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 消息中心：列表 / 单条已读 / 全部已读（发送为内部方法，不对外）。
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** 我的消息列表 */
    @GetMapping
    public R<PageResult<SysMessage>> list(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) Boolean isRead,
                                          @RequestAttribute("userId") Long userId) {
        return R.ok(messageService.page(userId, page, size, isRead));
    }

    /** 未读数 */
    @GetMapping("/unread-count")
    public R<Map<String, Long>> unreadCount(@RequestAttribute("userId") Long userId) {
        return R.ok(Map.of("count", messageService.unreadCount(userId)));
    }

    /** 标记单条已读 */
    @LoginOnly("消息自助已读")
    @PostMapping("/{id}/read")
    @OpLog(module = "消息中心", action = "标记已读")
    public R<Void> read(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        messageService.markRead(id, userId);
        return R.ok();
    }

    /** 全部已读 */
    @LoginOnly("消息自助已读")
    @PostMapping("/read-all")
    @OpLog(module = "消息中心", action = "全部已读")
    public R<Void> readAll(@RequestAttribute("userId") Long userId) {
        messageService.markAllRead(userId);
        return R.ok();
    }

    /** 内部发送（管理端调试用） */
    @Data
    public static class SendReq {
        private Long receiverId;
        private String title;
        private String content;
        private String bizType;
        private Long bizId;
    }

    @RequirePerm("msg:send")
    @PostMapping("/send")
    @OpLog(module = "消息中心", action = "发送消息")
    public R<SysMessage> send(@RequestBody SendReq req) {
        SysMessage msg = messageService.send(req.getReceiverId(), req.getTitle(), req.getContent(),
                req.getBizType(), req.getBizId());
        return R.ok(msg);
    }
}
