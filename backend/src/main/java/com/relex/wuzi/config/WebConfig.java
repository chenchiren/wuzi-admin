package com.relex.wuzi.config;

import com.relex.wuzi.security.JwtAuthFilter;
import com.relex.wuzi.security.RoleAccessFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RoleAccessFilter roleAccessFilter;

    @Value("${wuzi.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilter() {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>(jwtAuthFilter);
        bean.addUrlPatterns("/api/*");
        bean.setOrder(2);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<RoleAccessFilter> roleFilter() {
        FilterRegistrationBean<RoleAccessFilter> bean = new FilterRegistrationBean<>(roleAccessFilter);
        bean.addUrlPatterns("/api/*");
        bean.setOrder(3);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = List.of((allowedOrigins == null ? "" : allowedOrigins).split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !"*".equals(s))
                .toList();
        if (origins.isEmpty()) {
            // 安全默认：仅本地前端；生产通过 WUZI_CORS_ORIGINS 显式配置
            origins = List.of("http://localhost:5173", "http://localhost:5174", "http://127.0.0.1:5173");
        }
        config.setAllowedOriginPatterns(origins);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        // 凭证模式禁止 * 源（上一步已过滤）
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
