package com.relex.wuzi.aspect;

import com.relex.wuzi.service.OperationLogService;
import com.relex.wuzi.web.annotation.OpLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * @OpLog 操作日志切面：记录入参与结果到 sys_operation_log。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OpLogAspect {

    private static final Pattern SENSITIVE = Pattern.compile(
            "(\"?(?:password|oldPassword|newPassword|token)\"?\\s*[:=]\\s*\")[^\"]*(\")",
            Pattern.CASE_INSENSITIVE);

    private final OperationLogService operationLogService;

    @Around("@annotation(com.relex.wuzi.web.annotation.OpLog)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        OpLog opLog = method.getAnnotation(OpLog.class);
        if (opLog == null) {
            return pjp.proceed();
        }

        HttpServletRequest request = currentRequest();
        Long userId = attr(request, "userId");
        String username = attr(request, "username");
        String params = buildParams(pjp.getArgs(), signature.getParameterNames());

        String result;
        try {
            Object ret = pjp.proceed();
            result = "SUCCESS";
            write(request, userId, username, opLog, params, result);
            return ret;
        } catch (Throwable ex) {
            result = "FAIL:" + ex.getMessage();
            write(request, userId, username, opLog, params, result);
            throw ex;
        }
    }

    private void write(HttpServletRequest request, Long userId, String username,
                       OpLog opLog, String params, String result) {
        try {
            operationLogService.recordSafe(request, userId, username,
                    opLog.module(), opLog.action(), params, result);
        } catch (Exception e) {
            log.warn("操作日志写入失败: {}", e.getMessage());
        }
    }

    private String buildParams(Object[] args, String[] names) {
        if (args == null || args.length == 0) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        for (int i = 0; i < args.length; i++) {
            String name = names != null && i < names.length && names[i] != null ? names[i] : ("arg" + i);
            Object val = args[i];
            if (val instanceof HttpServletRequest) {
                continue;
            }
            joiner.add(name + "=" + (val == null ? "null" : val.toString()));
        }
        String raw = joiner.toString();
        return SENSITIVE.matcher(raw).replaceAll("$1******$2");
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T attr(HttpServletRequest request, String key) {
        if (request == null) {
            return null;
        }
        return (T) request.getAttribute(key);
    }
}
