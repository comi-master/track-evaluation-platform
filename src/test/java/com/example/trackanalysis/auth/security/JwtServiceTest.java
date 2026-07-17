package com.example.trackanalysis.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
  private static final Instant NOW = Instant.parse("2026-07-16T08:00:00Z");

  @Test
  void issuesAndParsesAllRequiredClaims() {
    JwtService service = service(SECRET, "test-issuer", NOW, 120);

    IssuedToken token = service.issue(7L, "researcher01", 3);
    ParsedJwt parsed = service.parse(token.value());

    assertThat(parsed.userId()).isEqualTo(7L);
    assertThat(parsed.username()).isEqualTo("researcher01");
    assertThat(parsed.authVersion()).isEqualTo(3);
    assertThat(parsed.jwtId()).isNotBlank();
    assertThat(token.expiresInSeconds()).isEqualTo(7200);
  }

  @Test
  void rejectsAnExpiredToken() {
    String token = service(SECRET, "test-issuer", NOW, 1).issue(1L, "user001", 0).value();

    assertThatThrownBy(() -> service(SECRET, "test-issuer", NOW.plusSeconds(61), 1).parse(token))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsWrongSignatureAndIssuer() {
    String token = service(SECRET, "test-issuer", NOW, 120).issue(1L, "user001", 0).value();

    assertThatThrownBy(
            () ->
                service("YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=", "test-issuer", NOW, 120)
                    .parse(token))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> service(SECRET, "other-issuer", NOW, 120).parse(token))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsASecretShorterThan256Bits() {
    assertThatThrownBy(() -> service("c2hvcnQ=", "test-issuer", NOW, 120))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("256 bits");
  }

  @Test
  void rejectsTokensMissingIssuedAtOrExpiration() {
    var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    String missingExpiration =
        Jwts.builder()
            .subject("1")
            .claim("username", "user001")
            .claim("authVersion", 0)
            .id("jti-1")
            .issuedAt(Date.from(NOW))
            .issuer("test-issuer")
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    String missingIssuedAt =
        Jwts.builder()
            .subject("1")
            .claim("username", "user001")
            .claim("authVersion", 0)
            .id("jti-2")
            .expiration(Date.from(NOW.plusSeconds(60)))
            .issuer("test-issuer")
            .signWith(key, Jwts.SIG.HS256)
            .compact();

    JwtService service = service(SECRET, "test-issuer", NOW, 120);
    assertThatThrownBy(() -> service.parse(missingExpiration)).isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> service.parse(missingIssuedAt)).isInstanceOf(JwtException.class);
  }

  private JwtService service(String secret, String issuer, Instant now, long ttlMinutes) {
    return new JwtService(
        new JwtProperties(secret, issuer, ttlMinutes), Clock.fixed(now, ZoneOffset.UTC));
  }
}
