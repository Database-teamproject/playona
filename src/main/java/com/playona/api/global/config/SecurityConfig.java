package com.playona.api.global.config;

import com.playona.api.global.security.JwtAuthenticationFilter;
import com.playona.api.global.security.JwtProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtProvider jwtProvider;

  // ← 여기에 직접 정의
  private static final List<String> WHITELIST = List.of(
      "/api/auth",
      "/swagger-ui",
      "/v3/api-docs",
      "/api-docs",
      "/api/platforms",
      "/error"
  );

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth ->
            auth.anyRequest().permitAll())
        .addFilterBefore(
            new JwtAuthenticationFilter(WHITELIST, jwtProvider),  // ← WHITELIST 전달
            UsernamePasswordAuthenticationFilter.class
        );

    return http.build();
  }
}