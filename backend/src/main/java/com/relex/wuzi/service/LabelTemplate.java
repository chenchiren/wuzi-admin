package com.relex.wuzi.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 标签模板配置：优先绑定 wuzi.label.*（application.yml），未配置时使用内存默认 50×30mm。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wuzi.label")
public class LabelTemplate {

    /** 模板编码 */
    private String templateCode = "DEFAULT_50X30";
    /** 标签宽 mm */
    private double widthMm = 50;
    /** 标签高 mm */
    private double heightMm = 30;
    /** 打印分辨率 dpi */
    private int dpi = 203;
    /** 标签标题 */
    private String title = "物资标签";
    /** 二维码模块大小（ZPL ^BQN 模块倍数，2-10） */
    private int qrMagnification = 5;

    /** 标签宽（点） */
    public int widthDots() {
        return mmToDots(widthMm);
    }

    /** 标签高（点） */
    public int heightDots() {
        return mmToDots(heightMm);
    }

    public int mmToDots(double mm) {
        return (int) Math.round(mm / 25.4 * dpi);
    }
}
