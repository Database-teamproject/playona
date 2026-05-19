package com.playona.api.global.config;

import com.playona.api.global.security.JwtAuthenticationFilter;
import com.playona.api.global.security.JwtProvider;
import com.playona.api.global.security.OAuth2SuccessHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;


@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtProvider jwtProvider;
  private final OAuth2SuccessHandler oAuth2SuccessHandler;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            // 인증 관련 (로그인, 토큰 갱신, 로그아웃)
            .requestMatchers("/api/auth/**").permitAll()
            // OAuth2 / 소셜 로그인
            .requestMatchers("/login/**", "/oauth2/**").permitAll()
            // 링크 조회 (단건, 플랫폼 목록, 리다이렉트, 링크 생성)는 비로그인 허용
            .requestMatchers(HttpMethod.POST, "/api/links").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/links/{shortCode}").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/links/{shortCode}/platforms").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/links/{shortCode}/redirect").permitAll()
            // 플랫폼 목록 조회
            .requestMatchers(HttpMethod.GET, "/api/platforms/**").permitAll()
            // 트랙 단건 조회는 공개, resolve(POST)는 인증 필요
            .requestMatchers(HttpMethod.GET, "/api/tracks/{trackId}").permitAll()
            // Swagger / API 문서
            .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                "/v3/api-docs/**", "/api-docs/**", "/openapi.yaml").permitAll()
            // 에러 페이지
            .requestMatchers("/error").permitAll()
            // 나머지는 모두 인증 필요
            .anyRequest().authenticated()
        )
        .exceptionHandling(ex -> ex
            // 인증되지 않은 요청 → 401 JSON 응답 (ObjectMapper 없이 직접 작성)
            .authenticationEntryPoint((request, response, authException) -> {
              response.setStatus(401);
              response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
              response.getWriter().write(
                  "{\"success\":false,\"data\":null,\"message\":\"인증이 필요합니다.\",\"result\":\"FAIL\"}"
              );
            })
        )
        .oauth2Login(oauth2 -> oauth2
            .successHandler(oAuth2SuccessHandler)
        )
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtProvider),
            UsernamePasswordAuthenticationFilter.class
        );

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(
        "http://localhost:3000",
        "https://playona-five.vercel.app",
        "http://localhost:8080",
        "http://localhost:5173",
        "http://43.201.139.24:8080"
    ));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
