package com.playona.api.global.config;

import com.playona.api.global.security.JwtAuthenticationFilter;
import com.playona.api.global.security.JwtProvider;
import com.playona.api.global.security.OAuth2SuccessHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

  private static final List<String> WHITELIST = List.of(
      "/api/auth",
      "/api/links",
      "/swagger-ui",
      "/v3/api-docs",
      "/api-docs",
      "/api/platforms",
      "/login",
      "/oauth2",
      "/error"
  );

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth ->
            auth.anyRequest().permitAll())
        .oauth2Login(oauth2 -> oauth2
            .successHandler(oAuth2SuccessHandler)
        )
        .addFilterBefore(
            new JwtAuthenticationFilter(WHITELIST, jwtProvider),
            UsernamePasswordAuthenticationFilter.class
        );

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(
        "http://localhost:3000",
        "https://playona-five.vercel.app/"  // 실제 프론트 도메인으로 변경
    ));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}