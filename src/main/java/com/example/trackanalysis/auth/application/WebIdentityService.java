package com.example.trackanalysis.auth.application;

import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebIdentityService {

  private final SysUserMapper userMapper;

  public WebIdentityService(SysUserMapper userMapper) {
    this.userMapper = userMapper;
  }

  @Transactional(readOnly = true)
  public WebIdentity requireActive(String username) {
    SysUserDO user = userMapper.selectActiveByUsername(username);
    if (user == null || !"ACTIVE".equals(user.getStatus())) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "The account is no longer active");
    }
    List<String> roles = userMapper.selectRoleCodes(user.getId());
    return new WebIdentity(
        user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), roles);
  }

  public record WebIdentity(
      long id, String username, String displayName, String email, List<String> roles) {
    public String primaryRole() {
      return roles == null || roles.isEmpty() ? "RESEARCHER" : roles.get(0);
    }
  }
}
