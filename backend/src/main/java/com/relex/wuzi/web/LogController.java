package com.relex.wuzi.web;

import com.relex.wuzi.annotation.LoginOnly;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.SysOperationLog;
import com.relex.wuzi.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final OperationLogService operationLogService;

    @GetMapping
    public R<PageResult<SysOperationLog>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) Long operatorId,
                                               @RequestParam(required = false) String module,
                                               @RequestParam(required = false) String action,
                                               @RequestParam(required = false) String keyword) {
        return R.ok(operationLogService.page(page, size, operatorId, module, action, keyword));
    }

    @Data
    public static class WriteLogReq {
        private String module;
        private String action;
        private String params;
        private String result = "SUCCESS";
    }

    @LoginOnly("前端操作日志上报")
    @PostMapping
    public R<Void> write(@RequestBody WriteLogReq req,
                         @RequestAttribute(value = "userId", required = false) Long userId,
                         @RequestAttribute(value = "username", required = false) String username,
                         HttpServletRequest request) {
        operationLogService.record(request, userId, username,
                req.getModule(), req.getAction(), req.getParams(), req.getResult());
        return R.ok();
    }
}
