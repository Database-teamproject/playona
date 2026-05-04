package com.playona.api.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final List<String> whitelist;
  private final JwtProvider jwtProvider;

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException {

    String requestURI = request.getRequestURI();

    // 토큰이 있으면 항상 처리 (화이트리스트여도)
    String token = resolveToken(request);
    if (token != null && jwtProvider.validateToken(token)) {
      String userUuid = jwtProvider.getUserUuid(token);
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(
              userUuid, null, Collections.emptyList());
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // 화이트리스트 경로는 인증 없어도 통과
    if (isWhitelisted(requestURI)) {
      try {
        filterChain.doFilter(request, response);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return;
    }

    try {
      filterChain.doFilter(request, response);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String resolveToken(HttpServletRequest request) {
    String bearer = request.getHeader("Authorization");
    if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
      return bearer.substring(7);
    }
    return null;
  }

  private boolean isWhitelisted(String uri) {
    return whitelist.stream().anyMatch(uri::startsWith);
  }
}
