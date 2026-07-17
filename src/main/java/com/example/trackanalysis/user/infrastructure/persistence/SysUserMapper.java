package com.example.trackanalysis.user.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface SysUserMapper extends BaseMapper<SysUserDO> {

  @Select(
      """
      SELECT id, username, password_hash, status, auth_version, version, deleted, created_at, updated_at
      FROM sys_user
      WHERE username = #{username} AND deleted = 0
      LIMIT 1
      """)
  SysUserDO selectActiveByUsername(@Param("username") String username);

  @Select(
      """
      SELECT id, username, password_hash, status, auth_version, version, deleted, created_at, updated_at
      FROM sys_user
      WHERE id = #{id} AND deleted = 0
      LIMIT 1
      """)
  SysUserDO selectActiveById(@Param("id") long id);

  @Update(
      """
      UPDATE sys_user
      SET auth_version = auth_version + 1,
          version = version + 1,
          updated_at = #{updatedAt}
      WHERE id = #{id}
        AND auth_version = #{authVersion}
        AND status = 'ACTIVE'
        AND deleted = 0
      """)
  int incrementAuthVersion(
      @Param("id") long id,
      @Param("authVersion") int authVersion,
      @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
