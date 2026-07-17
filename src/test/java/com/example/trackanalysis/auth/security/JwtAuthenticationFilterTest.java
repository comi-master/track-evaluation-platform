package com.example.trackanalysis.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private JwtService jwtService;
  @Mock private SysUserMapper userMapper;
  @Mock private RestAuthenticationEntryPoint entryPoint;
  @Mock private FilterChain filterChain;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void validTokenSetsAPrincipalForTheChainAndCleansItAfterward() throws Exception {
    when(jwtService.parse("valid-token")).thenReturn(new ParsedJwt(7L, "user007", 2, "jti-123"));
    when(userMapper.selectActiveById(7L)).thenReturn(user(7L, "user007", "ACTIVE", 2));
    MockHttpServletRequest request = request("Bearer valid-token");

    new JwtAuthenticationFilter(jwtService, userMapper, entryPoint)
        .doFilterInternal(
            request,
            new MockHttpServletResponse(),
            (servletRequest, servletResponse) -> {
              Object principal =
                  SecurityContextHolder.getContext().getAuthentication().getPrincipal();
              assertThat(principal).isEqualTo(new AuthenticatedUser(7L, "user007", 2));
            });

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(entryPoint, never())
        .commence(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void invalidAuthVersionReturns401WithoutContinuingTheChain() throws Exception {
    when(jwtService.parse("old-token")).thenReturn(new ParsedJwt(7L, "user007", 1, "jti-123"));
    when(userMapper.selectActiveById(7L)).thenReturn(user(7L, "user007", "ACTIVE", 2));
    MockHttpServletRequest request = request("Bearer old-token");

    new JwtAuthenticationFilter(jwtService, userMapper, entryPoint)
        .doFilterInternal(request, new MockHttpServletResponse(), filterChain);

    verify(entryPoint)
        .commence(
            org.mockito.ArgumentMatchers.eq(request),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void missingAuthorizationHeaderContinuesAnonymously() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    new JwtAuthenticationFilter(jwtService, userMapper, entryPoint)
        .doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }

  @Test
  void downstreamAuthenticationExceptionsAreNotRewrittenAsInvalidJwt() throws Exception {
    when(jwtService.parse("valid-token")).thenReturn(new ParsedJwt(7L, "user007", 2, "jti-123"));
    when(userMapper.selectActiveById(7L)).thenReturn(user(7L, "user007", "ACTIVE", 2));
    MockHttpServletRequest request = request("Bearer valid-token");
    org.mockito.Mockito.doThrow(new BadCredentialsException("downstream"))
        .when(filterChain)
        .doFilter(any(), any());

    assertThatThrownBy(
            () ->
                new JwtAuthenticationFilter(jwtService, userMapper, entryPoint)
                    .doFilterInternal(request, new MockHttpServletResponse(), filterChain))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("downstream");
    verify(entryPoint, never()).commence(any(), any(), any());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private MockHttpServletRequest request(String authorization) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", authorization);
    return request;
  }

  private SysUserDO user(long id, String username, String status, int authVersion) {
    SysUserDO user = new SysUserDO();
    user.setId(id);
    user.setUsername(username);
    user.setStatus(status);
    user.setAuthVersion(authVersion);
    return user;
  }
}
