package com.playona.api.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class SwaggerConfig {
  @Bean
  public OpenAPI openAPI() {
    // API 기본 정보 설정
    Info info = new Info()
        .title("Playona API Document")
        .version("1.0")
        .description(
            "크로스 플랫폼 음악 공유 서비스")
        .contact(new io.swagger.v3.oas.models.info.Contact().email("yunsung65@naver.com"));

    // JWT 인증 방식 설정
    String jwtScheme = "jwtAuth";
    SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtScheme);
    Components components = new Components()
        .addSecuritySchemes(jwtScheme, new SecurityScheme()
            .name("Authorization")
            .type(SecurityScheme.Type.HTTP)
            .in(SecurityScheme.In.HEADER)
            .scheme("Bearer")
            .bearerFormat("JWT"));

    // Swagger UI 설정 및 보안 추가
    return new OpenAPI()
        .addServersItem(new Server().url("http://localhost:8080"))  // 추가적인 서버 URL 설정 가능
        .components(components)
        .info(info)
        .addSecurityItem(securityRequirement);
  }
}
