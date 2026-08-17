package com.example.trackanalysis.auth.security;

import com.example.trackanalysis.user.domain.UserStatus;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

  private final SysUserMapper userMapper;

  public DatabaseUserDetailsService(SysUserMapper userMapper) {
    this.userMapper = userMapper;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    SysUserDO user = userMapper.selectActiveByUsername(username == null ? "" : username.trim());
    if (user == null) {
      throw new UsernameNotFoundException("Invalid username or password");
    }
    List<SimpleGrantedAuthority> authorities =
        userMapper.selectRoleCodes(user.getId()).stream()
            .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
            .toList();
    return User.withUsername(user.getUsername())
        .password(user.getPasswordHash())
        .disabled(!UserStatus.ACTIVE.name().equals(user.getStatus()))
        .authorities(authorities)
        .build();
  }
}
