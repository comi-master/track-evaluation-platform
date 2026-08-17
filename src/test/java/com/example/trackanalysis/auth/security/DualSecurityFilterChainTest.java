package com.example.trackanalysis.auth.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.trackanalysis.analysis.infrastructure.persistence.AbnormalIntervalMapper;
import com.example.trackanalysis.analysis.infrastructure.persistence.AnalysisResultMapper;
import com.example.trackanalysis.audit.infrastructure.persistence.AuditLogMapper;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.outbox.ReliableOutboxMapper;
import com.example.trackanalysis.report.infrastructure.persistence.AnalysisReportMapper;
import com.example.trackanalysis.task.infrastructure.persistence.AnalysisTaskMapper;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileMapper;
import com.example.trackanalysis.track.infrastructure.persistence.TrackPointMapper;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "spring.session.store-type=none")
@AutoConfigureMockMvc
@ImportAutoConfiguration(exclude = SessionAutoConfiguration.class)
@ActiveProfiles("no-persistence")
class DualSecurityFilterChainTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private SysUserMapper userMapper;
  @MockitoBean private AuditLogMapper auditLogMapper;
  @MockitoBean private DatasetMapper datasetMapper;
  @MockitoBean private TrackFileMapper trackFileMapper;
  @MockitoBean private TrackPointMapper trackPointMapper;
  @MockitoBean private AnalysisResultMapper analysisResultMapper;
  @MockitoBean private AbnormalIntervalMapper abnormalIntervalMapper;
  @MockitoBean private AnalysisTaskMapper analysisTaskMapper;
  @MockitoBean private AnalysisReportMapper analysisReportMapper;
  @MockitoBean private TransactionTemplate transactionTemplate;
  @MockitoBean private ReliableOutboxMapper reliableOutboxMapper;

  @Test
  void protectedApiWithoutJwtReturnsJsonUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void webSecurityContextCannotReplaceApiJwt() throws Exception {
    MockHttpSession session = new MockHttpSession();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated("web-user", null, java.util.List.of()));
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

    mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void loginIsPublicAndJwtFilterIgnoresIt() throws Exception {
    mockMvc
        .perform(get("/login").header("Authorization", "Bearer deliberately-invalid"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("航迹仿真评测平台")))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.containsString("注册新用户")));
  }

  @Test
  void anonymousWebRequestsRedirectToLogin() throws Exception {
    mockMvc
        .perform(get("/app/dashboard"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/login")));
    mockMvc
        .perform(get("/admin/users"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/login")));
  }

  @Test
  @WithMockUser(username = "researcher", roles = "RESEARCHER")
  void researcherCannotAccessAdminPaths() throws Exception {
    mockMvc.perform(get("/admin/users")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "web-user")
  void webPostRequiresCsrf() throws Exception {
    mockMvc.perform(post("/logout")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "web-user")
  void logoutWithCsrfInvalidatesWebAuthentication() throws Exception {
    mockMvc
        .perform(post("/logout").with(csrf()))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/login?logout"));
  }
}
