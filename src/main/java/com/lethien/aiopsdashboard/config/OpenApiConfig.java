package com.lethien.aiopsdashboard.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String  BEARER_SCHEME = "bearerAuth";

    @Value("${spring.application.name}")
    private String appName;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(serverList())
                // Khai báo security scheme JWT Bearer 1 lần ở đây
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, jwtSecurityScheme()))
                // Áp dụng security mặc định cho toàn bộ API
                // Endpoint public sẽ override lại bằng @SecurityRequirements({})
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    // ── Thông tin chung ────────────────────────────────────────────────────
    private Info apiInfo() {
        return new Info()
                .title("AI Dashboard API")
                .description(
                        """
                        REST API cho hệ thống quản lý AI providers.
                        
                        **Cách dùng:**
                        1. Gọi `POST /api/auth/login` để lấy JWT token
                        2. Nhấn nút **Authorize** ở trên, nhập token vào ô `bearerAuth`
                        3. Tất cả request tiếp theo sẽ tự động đính kèm token
                        """
                )
                .version("1.0.0")
                .contact(new Contact()
                        .name("AI Dashboard Team")
                        .email("admin@localhost"));
    }

    // ── Danh sách server (hiện trong dropdown Swagger UI) ─────────────────
    private List<Server> serverList() {
        return List.of(
                new Server().url("http://localhost:8080").description("Local Dev"),
                new Server().url("http://staging:8080").description("Staging")
        );
    }

    // ── JWT Bearer scheme ──────────────────────────────────────────────────
    private SecurityScheme jwtSecurityScheme() {
        return new SecurityScheme()
                .name(BEARER_SCHEME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Nhập JWT token (không cần prefix 'Bearer ')");
    }
}
