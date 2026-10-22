package com.relex.wuzi.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单号生成：前缀 + 日期 + 时间片 + 进程内序号 + 随机后缀，多实例/重启不撞号。
 */
public final class BillNoUtil {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private BillNoUtil() {
    }

    public static String next(String prefix) {
        long ts = System.currentTimeMillis();
        long seq = SEQ.incrementAndGet() % 1000;
        int rnd = ThreadLocalRandom.current().nextInt(0, 46656);
        return prefix + LocalDate.now().format(DAY) + "-"
                + Long.toString(ts % 46656, 36).toUpperCase()
                + Long.toString(seq, 36).toUpperCase()
                + Integer.toString(rnd, 36).toUpperCase();
    }
}
