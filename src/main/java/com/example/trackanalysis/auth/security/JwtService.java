package com.example.trackanalysis.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private static final String USERNAME_CLAIM = "username";
  private static final String AUTH_VERSION_CLAIM = "authVersion";

  private final JwtProperties properties;
  private final Clock clock;
  private final SecretKey signingKey;
  private final JwtParser parser;

  public JwtService(JwtProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    this.signingKey = decodeSigningKey(properties.secret());
    this.parser =
        Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(properties.issuer())
            .clock(() -> Date.from(clock.instant()))
            .build();
  }

  public IssuedToken issue(long userId, String username, int authVersion) {
    Instant issuedAt = clock.instant();
    Duration ttl = Duration.ofMinutes(properties.accessTtlMinutes());
    Instant expiresAt = issuedAt.plus(ttl);
    String token =
        Jwts.builder()
            .subject(Long.toString(userId))
            .claim(USERNAME_CLAIM, username)
            .claim(AUTH_VERSION_CLAIM, authVersion)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .issuer(properties.issuer())
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
    return new IssuedToken(token, ttl.toSeconds());
  }

  public ParsedJwt parse(String token) {
    Claims claims = parser.parseSignedClaims(token).getPayload();
    try {
      long userId = Long.parseLong(claims.getSubject());
      String username = claims.get(USERNAME_CLAIM, String.class);
      Integer authVersion = claims.get(AUTH_VERSION_CLAIM, Integer.class);
      String jwtId = claims.getId();
      Date issuedAt = claims.getIssuedAt();
      Date expiration = claims.getExpiration();
      if (userId <= 0
          || username == null
          || username.isBlank()
          || authVersion == null
          || authVersion < 0
          || jwtId == null
          || jwtId.isBlank()
          || issuedAt == null
          || expiration == null
          || issuedAt.toInstant().isAfter(clock.instant())
          || !expiration.after(issuedAt)) {
        throw new JwtException("Required access-token claims are missing");
      }
      return new ParsedJwt(userId, username, authVersion, jwtId);
    } catch (NumberFormatException exception) {
      throw new JwtException("Invalid subject claim", exception);
    }
  }

  private SecretKey decodeSigningKey(String encodedSecret) {
    try {
      byte[] bytes = Decoders.BASE64.decode(encodedSecret);
      if (bytes.length < 32) {
        throw new IllegalStateException("JWT secret must represent at least 256 bits");
      }
      return Keys.hmacShaKeyFor(bytes);
    } catch (RuntimeException exception) {
      if (exception instanceof IllegalStateException stateException) {
        throw stateException;
      }
      throw new IllegalStateException(
          "JWT secret must be valid Base64 representing at least 256 bits", exception);
    }
  }
}
