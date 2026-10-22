package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.domain.SysOperationLog;
import com.relex.wuzi.mapper.SysOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OperationLogService {

    private static final Pattern SENSITIVE = Pattern.compile(
            "(\"?(?:password|oldPassword|newPassword|token)\"?\\s*[:=]\\s*\")[^\"]*(\")",
            Pattern.CASE_INSENSITIVE);

    private final SysOperationLogMapper logMapper;

    public void record(HttpServletRequest request, Long operatorId, String operatorName,
                       String module, String action, String params, String result) {
        SysOperationLog log = new SysOperationLog();
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setModule(module);
        log.setAction(action);
        log.setRequestUri(request != null ? request.getRequestURI() : null);
        log.setParams(mask(params));
        log.setResult(result);
        log.setIp(resolveIp(request));
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    public void record(Long operatorId, String operatorName, String module, String action,
                       String requestUri, String params, String result, String ip) {
        SysOperationLog log = new SysOperationLog();
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setModule(module);
        log.setAction(action);
        log.setRequestUri(requestUri);
        log.setParams(mask(params));
        log.setResult(result);
        log.setIp(ip);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    /**
     * AOP 安全记录：日志失败不影响业务主流程。
     */
    public void recordSafe(HttpServletRequest request, Long operatorId, String operatorName,
                           String module, String action, String params, String result) {
        try {
            record(request, operatorId, operatorName, module, action, params, result);
        } catch (Exception ignore) {
            // 落库失败仅吞掉，避免操作日志阻断业务
        }
    }

    public PageResult<SysOperationLog> page(long page, long size, Long operatorId,
                                            String module, String action, String keyword) {
        LambdaQueryWrapper<SysOperationLog> qw = new LambdaQueryWrapper<SysOperationLog>()
                .eq(operatorId != null, SysOperationLog::getOperatorId, operatorId)
                .eq(module != null && !module.isBlank(), SysOperationLog::getModule, module)
                .eq(action != null && !action.isBlank(), SysOperationLog::getAction, action)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysOperationLog::getOperatorName, keyword)
                        .or().like(SysOperationLog::getRequestUri, keyword)
                        .or().like(SysOperationLog::getParams, keyword))
                .orderByDesc(SysOperationLog::getId);
        Page<SysOperationLog> p = logMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    private String mask(String params) {
        if (params == null || params.isBlank()) {
            return params;
        }
        String masked = SENSITIVE.matcher(params).replaceAll("$1******$2");
        return masked.length() > 4000 ? masked.substring(0, 4000) : masked;
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
