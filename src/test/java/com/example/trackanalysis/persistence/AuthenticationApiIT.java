package com.example.trackanalysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthenticationApiIT extends MySqlIntegrationTestSupport {

  private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private SqlSession sqlSession;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void registrationNormalizesUsernameStoresBcryptAndRejectsDuplicates() throws Exception {
    String requestId = "register-request-12345678";
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .header("X-Request-Id", requestId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"username":"  Researcher01  ","password":"secure-password"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-Request-Id", requestId))
            .andExpect(jsonPath("$.requestId").value(requestId))
            .andExpect(jsonPath("$.data.username").value("researcher01"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("password")
        .doesNotContain("authVersion")
        .doesNotContain("deleted");
    String storedHash =
        jdbcTemplate.queryForObject(
            "SELECT password_hash FROM sys_user WHERE username = ?", String.class, "researcher01");
    assertThat(storedHash).startsWith("$2").doesNotContain("secure-password");
    assertThat(passwordEncoder.matches("secure-password", storedHash)).isTrue();

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"RESEARCHER01","password":"another-password"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void loginUsesTheSamePublicFailureForUnknownUserAndWrongPasswordAndRejectsDisabledUser()
      throws Exception {
    register("known-user", "correct-password");

    String wrongMessage = loginFailureMessage("known-user", "wrong-password");
    String missingMessage = loginFailureMessage("missing-user", "wrong-password");
    assertThat(wrongMessage).isEqualTo(missingMessage);

    jdbcTemplate.update("UPDATE sys_user SET status = 'DISABLED' WHERE username = ?", "known-user");
    sqlSession.clearCache();
    mockMvc
        .perform(loginRequest("known-user", "correct-password"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void validJwtAccessesProtectedApiWhileMalformedAndExpiredJwtReturn401() throws Exception {
    register("token-user", "correct-password");
    String token = login("token-user", "correct-password");

    mockMvc
        .perform(
            get("/api/v1/auth/me")
                .header("Authorization", bearer(token))
                .header("X-Request-Id", "token-request-12345678"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "token-request-12345678"))
        .andExpect(jsonPath("$.requestId").value("token-request-12345678"))
        .andExpect(jsonPath("$.data.username").value("token-user"));

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer malformed.token.value"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", bearer(expiredToken("token-user"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void logoutIncrementsAuthVersionAndInvalidatesTheOldToken() throws Exception {
    register("logout-user", "correct-password");
    String token = login("logout-user", "correct-password");
    Integer before = authVersion("logout-user");

    mockMvc
        .perform(post("/api/v1/auth/logout").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUCCESS"));

    assertThat(authVersion("logout-user")).isEqualTo(before + 1);
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void openApiDocumentsAuthenticationAndDatasetEndpointsWithoutAuthentication() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/datasets']").exists())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists());
  }

  private void register(String username, String password) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"%s","password":"%s"}
                    """
                        .formatted(username, password)))
        .andExpect(status().isCreated());
  }

  private String login(String username, String password) throws Exception {
    MvcResult result =
        mockMvc.perform(loginRequest(username, password)).andExpect(status().isOk()).andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
      String username, String password) {
    return post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"username":"%s","password":"%s"}
            """
                .formatted(username, password));
  }

  private String loginFailureMessage(String username, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(loginRequest(username, password))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.message");
  }

  private Integer authVersion(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT auth_version FROM sys_user WHERE username = ?", Integer.class, username);
  }

  private String expiredToken(String username) {
    Long userId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM sys_user WHERE username = ?", Long.class, username);
    Instant now = Instant.now();
    SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
    return Jwts.builder()
        .subject(Long.toString(userId))
        .claim("username", username)
        .claim("authVersion", 0)
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now.minusSeconds(120)))
        .expiration(Date.from(now.minusSeconds(60)))
        .issuer("track-analysis-test")
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
