package com.relex.wuzi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 保证 DataScopeAspect（Advisor）被织入；即使无 AspectJ 也可用 Spring AOP 代理。
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class DataScopeAopConfig {
}
