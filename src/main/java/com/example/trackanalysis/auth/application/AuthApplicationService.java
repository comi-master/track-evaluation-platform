package com.example.trackanalysis.auth.application;

import com.example.trackanalysis.auth.api.LoginRequest;
import com.example.trackanalysis.auth.api.LoginResponse;
import com.example.trackanalysis.auth.api.RegisterRequest;
import com.example.trackanalysis.auth.api.UserResponse;
import com.example.trackanalysis.auth.security.AuthenticatedUser;
import com.example.trackanalysis.auth.security.IssuedToken;
import com.example.trackanalysis.auth.security.JwtService;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.user.application.UsernamePolicy;
import com.example.trackanalysis.user.domain.UserStatus;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationService {

  private static final String INVALID_CREDENTIALS = "Username or password is incorrect";

  private final SysUserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final Clock clock;
  private final String dummyPasswordHash;

  public AuthApplicationService(
      SysUserMapper userMapper,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      Clock clock) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.clock = clock;
    this.dummyPasswordHash = passwordEncoder.encode("constant-time-login-placeholder");
  }

  @Transactional
  public UserResponse register(RegisterRequest request) {
    String username = UsernamePolicy.normalize(request.username());
    if (userMapper.selectActiveByUsername(username) != null) {
      throw duplicateUsername();
    }

    SysUserDO user = new SysUserDO();
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setStatus(UserStatus.ACTIVE.name());
    try {
      userMapper.insert(user);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.CONFLICT, "Username is already registered", exception);
    }
    return toResponse(user);
  }

  @Transactional(readOnly = true)
  public LoginResponse login(LoginRequest request) {
    String username = UsernamePolicy.normalize(request.username());
    SysUserDO user = userMapper.selectActiveByUsername(username);
    String storedHash = user == null ? dummyPasswordHash : user.getPasswordHash();
    boolean passwordMatches = passwordEncoder.matches(request.password(), storedHash);
    if (user == null || !passwordMatches || !UserStatus.ACTIVE.name().equals(user.getStatus())) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS);
    }

    IssuedToken token = jwtService.issue(user.getId(), user.getUsername(), user.getAuthVersion());
    return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), toResponse(user));
  }

  public UserResponse currentUser(AuthenticatedUser principal) {
    return new UserResponse(principal.id(), principal.username(), UserStatus.ACTIVE.name());
  }

  @Transactional
  public void logout(AuthenticatedUser principal) {
    int changed =
        userMapper.incrementAuthVersion(
            principal.id(), principal.authVersion(), LocalDateTime.now(clock));
    if (changed != 1) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
  }

  private BusinessException duplicateUsername() {
    return new BusinessException(ErrorCode.CONFLICT, "Username is already registered");
  }

  private UserResponse toResponse(SysUserDO user) {
    return new UserResponse(user.getId(), user.getUsername(), user.getStatus());
  }
}
