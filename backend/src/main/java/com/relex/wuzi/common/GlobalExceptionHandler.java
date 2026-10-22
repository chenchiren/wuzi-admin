package com.relex.wuzi.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        String raw = e.getMessage() == null ? "" : e.getMessage();
        if (raw.contains("uk_sku") || raw.contains("sku_uk")) {
            return R.fail("该物资已存在品码，无需重复生成");
        }
        if (raw.contains("uk_batch") || raw.contains("batch_uk")) {
            return R.fail("该批次已存在批码，无需重复生成");
        }
        if (raw.contains("uk_serial") || raw.contains("serial_uk")) {
            return R.fail("序列号已存在，请更换序列号");
        }
        if (raw.contains("uk_qr_code")) {
            return R.fail("二维码编码冲突，请重试");
        }
        if (raw.contains("uk_trace_token")) {
            return R.fail("溯源 Token 冲突，请重试");
        }
        if (raw.contains("uk_sup_code") || raw.contains("supplier_code")) {
            return R.fail("供应商编码已存在，请更换编码");
        }
        if (raw.contains("uk_unit_code") || raw.contains("unit_code")) {
            return R.fail("计量单位编码已存在，请更换编码");
        }
        if (raw.contains("uk_project_code") || raw.contains("project_code")) {
            return R.fail("项目编码已存在，请更换编码");
        }
        return R.fail("数据已存在，请勿重复提交");
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof DuplicateKeyException || isDuplicateMessage(root.getMessage())) {
            return R.fail("数据已存在，请勿重复提交");
        }
        String msg = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage())
                + " | root=" + root.getClass().getSimpleName() + ": " + (root.getMessage() == null ? "" : root.getMessage());
        return R.fail("系统异常: " + msg);
    }

    private boolean isDuplicateMessage(String msg) {
        return msg != null && (msg.contains("Duplicate entry") || msg.contains("uk_sku") || msg.contains("uk_batch"));
    }
}
