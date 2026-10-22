package com.relex.wuzi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring @Scheduled 定时任务支持。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
