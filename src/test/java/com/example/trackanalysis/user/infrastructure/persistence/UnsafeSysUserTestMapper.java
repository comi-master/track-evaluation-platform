package com.example.trackanalysis.user.infrastructure.persistence;

import org.apache.ibatis.annotations.Update;

public interface UnsafeSysUserTestMapper {

  @Update("UPDATE sys_user SET status = 'DISABLED'")
  int updateEveryUserWithoutPredicate();
}
