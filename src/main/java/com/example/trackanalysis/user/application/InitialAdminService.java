package com.example.trackanalysis.user.application;

import com.example.trackanalysis.auth.config.WebAuthProperties;
import com.example.trackanalysis.user.domain.UserStatus;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InitialAdminService implements ApplicationRunner {

  private final SysUserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final WebAuthProperties properties;

  public InitialAdminService(
      SysUserMapper userMapper, PasswordEncoder passwordEncoder, WebAuthProperties properties) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
  }

  @Transactional
  public void createIfNecessary() {
    if (userMapper.countActiveByRole("ADMIN") > 0) {
      return;
    }
    String username = properties.adminUsername() == null ? "" : properties.adminUsername().trim();
    String password = properties.adminPassword() == null ? "" : properties.adminPassword();
    if (username.isEmpty() || password.length() < 12) {
      return;
    }
    SysUserDO existing = userMapper.selectActiveByUsername(UsernamePolicy.normalize(username));
    if (existing == null) {
      existing = new SysUserDO();
      existing.setUsername(UsernamePolicy.normalize(username));
      existing.setPasswordHash(passwordEncoder.encode(password));
      existing.setStatus(UserStatus.ACTIVE.name());
      userMapper.insert(existing);
    }
    userMapper.assignRole(existing.getId(), "ADMIN");
  }

  @Override
  public void run(ApplicationArguments args) {
    createIfNecessary();
  }
}
