package com.playona.api.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

  @Value("${jwt.secret}")
  private String secretKey;

  @Value("${jwt.access-expiration}")
  private long accessExpiration;

  @Value("${jwt.refresh-expiration}")
  private long refreshExpiration;

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
  }

  // Access Token 생성
// Access Token 생성 (userId → userUuid로 변경)
  public String generateToken(String userUuid) {
    return Jwts.builder()
        .subject(userUuid)
        .claim("type", "access")
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + accessExpiration))
        .signWith(getSigningKey())
        .compact();
  }

  // 토큰에서 userUuid 추출 (기존 getUserId 대체)
  public String getUserUuid(String token) {
    return getClaims(token).getSubject();
  }

  // Refresh Token 생성
  public String generateRefreshToken(String userUuid) {
    return Jwts.builder()
        .subject(userUuid)
        .claim("type", "refresh")
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
        .signWith(getSigningKey())
        .compact();
  }
  public long getRefreshExpiration() {
    return refreshExpiration;
  }

  // Access Token 전용 유효성 검증 (type=access 확인)
  public boolean validateToken(String token) {
    try {
      Claims claims = getClaims(token);
      return "access".equals(claims.get("type", String.class));
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  // Refresh Token 전용 유효성 검증 (type=refresh 확인)
  public boolean validateRefreshToken(String token) {
    try {
      Claims claims = getClaims(token);
      return "refresh".equals(claims.get("type", String.class));
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  private Claims getClaims(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
