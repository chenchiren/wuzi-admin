package com.relex.wuzi.security;

import com.relex.wuzi.common.BizException;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * 密码策略：长度 >= 8 且同时包含字母与数字。
 * 重置/改密/建号统一校验；管理员重置可生成默认密码并提示。
 */
public final class PasswordPolicy {

    /** 默认重置密码前缀 + 随机后缀（满足强度策略） */
    public static final String DEFAULT_RESET_PREFIX = "Wuzi@";

    private static final int MIN_LENGTH = 8;
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] RANDOM_POOL =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();

    private PasswordPolicy() {
    }

    /** 校验密码强度，不合规抛 BizException */
    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new BizException("密码不能为空");
        }
        if (password.length() < MIN_LENGTH) {
            throw new BizException("密码长度不能少于 " + MIN_LENGTH + " 位");
        }
        if (!LETTER.matcher(password).find()) {
            throw new BizException("密码必须包含字母");
        }
        if (!DIGIT.matcher(password).find()) {
            throw new BizException("密码必须包含数字");
        }
    }

    /** 是否合规（不抛异常） */
    public static boolean isValid(String password) {
        try {
            validate(password);
            return true;
        } catch (BizException e) {
            return false;
        }
    }

    /** 生成默认重置密码（含字母+数字，满足策略），配合提示语告知用户 */
    public static String generateDefaultPassword() {
        StringBuilder sb = new StringBuilder(DEFAULT_RESET_PREFIX);
        for (int i = 0; i < 6; i++) {
            sb.append(RANDOM_POOL[RANDOM.nextInt(RANDOM_POOL.length)]);
        }
        return sb.toString();
    }

    /** 重置密码后的默认提示 */
    public static String resetHint() {
        return "密码已重置为默认密码，请提醒用户首次登录后立即修改（密码需 8 位以上且含字母数字）";
    }
}
