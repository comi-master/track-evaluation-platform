package com.example.trackanalysis.auth.security;

import com.example.trackanalysis.user.domain.UserStatus;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;
  private final SysUserMapper userMapper;
  private final RestAuthenticationEntryPoint authenticationEntryPoint;

  public JwtAuthenticationFilter(
      JwtService jwtService,
      SysUserMapper userMapper,
      RestAuthenticationEntryPoint authenticationEntryPoint) {
    this.jwtService = jwtService;
    this.userMapper = userMapper;
    this.authenticationEntryPoint = authenticationEntryPoint;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    if (authorization == null) {
      filterChain.doFilter(request, response);
      return;
    }
    if (!authorization.startsWith(BEARER_PREFIX)
        || authorization.length() == BEARER_PREFIX.length()) {
      authenticationEntryPoint.commence(
          request, response, new BadCredentialsException("Invalid bearer token"));
      return;
    }

    AuthenticatedUser principal;
    try {
      ParsedJwt parsedJwt = jwtService.parse(authorization.substring(BEARER_PREFIX.length()));
      SysUserDO user = userMapper.selectActiveById(parsedJwt.userId());
      if (user == null
          || !UserStatus.ACTIVE.name().equals(user.getStatus())
          || !parsedJwt.username().equals(user.getUsername())
          || user.getAuthVersion() == null
          || parsedJwt.authVersion() != user.getAuthVersion()) {
        throw new BadCredentialsException("Access token is no longer valid");
      }
      principal = new AuthenticatedUser(user.getId(), user.getUsername(), user.getAuthVersion());
    } catch (JwtException | BadCredentialsException exception) {
      SecurityContextHolder.clearContext();
      authenticationEntryPoint.commence(
          request, response, new BadCredentialsException("Invalid access token", exception));
      return;
    }

    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                userMapper.selectRoleCodes(principal.id()).stream()
                    .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                    .toList()));
    try {
      filterChain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
