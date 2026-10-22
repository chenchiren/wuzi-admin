package com.relex.wuzi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger 契约：统一 Bearer JWT 鉴权说明。
 * 访问：/swagger-ui.html · /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    public static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI wuziOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("物资全链路管理系统 API")
                        .description("""
                                统一响应 {code,msg,data}；分页 data={total,records}。
                                认证：Authorization: Bearer <jwt>。
                                接口前缀：PC /api/** · 手机 /api/m/** · 对外溯源 /api/trace/public/**。
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("relex")))
                .servers(List.of(new Server().url("/").description("当前服务")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .name(SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录 /api/auth/login 获取 token")));
    }
}
