package com.example.trackanalysis.user.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface SysUserMapper extends BaseMapper<SysUserDO> {
  String USER_COLUMNS =
      "id, username, display_name, email, password_hash, status, failed_login_count, "
          + "last_login_at, auth_version, version, deleted, created_at, updated_at";

  @Select(
      "SELECT " + USER_COLUMNS + " FROM sys_user WHERE username=#{username} AND deleted=0 LIMIT 1")
  SysUserDO selectByUsername(@Param("username") String username);

  @Select("SELECT " + USER_COLUMNS + " FROM sys_user WHERE username=#{username} LIMIT 1")
  SysUserDO selectByUsernameIncludingDeleted(@Param("username") String username);

  @Select("SELECT " + USER_COLUMNS + " FROM sys_user WHERE id=#{id} FOR UPDATE")
  SysUserDO selectByIdIncludingDeletedForUpdate(@Param("id") long id);

  @Update(
      "UPDATE sys_user SET"
          + " deleted=0,auth_version=auth_version+1,version=version+1,updated_at=#{now} WHERE"
          + " id=#{id} AND deleted=1 AND status='DISABLED'")
  int restoreDeleted(@Param("id") long id, @Param("now") java.time.LocalDateTime now);

  @Select(
      """
      SELECT CASE WHEN
        EXISTS(SELECT 1 FROM audit_log WHERE resource_type='USER' AND resource_id=CAST(#{id} AS CHAR)
          AND request_id='local-admin-recovery' AND action='USER_RESTORE')
        AND EXISTS(SELECT 1 FROM audit_log WHERE resource_type='USER' AND resource_id=CAST(#{id} AS CHAR)
          AND request_id='local-admin-recovery' AND action='USER_ENABLE')
        AND NOT EXISTS(SELECT 1 FROM audit_log WHERE resource_type='USER' AND resource_id=CAST(#{id} AS CHAR)
          AND request_id='local-admin-recovery' AND action='PASSWORD_RESET')
      THEN 1 ELSE 0 END
      """)
  int countIncompleteLocalRecovery(@Param("id") long id);

  @Select(
      "SELECT "
          + USER_COLUMNS
          + " FROM sys_user WHERE username=#{username} AND deleted=0 AND status='ACTIVE' LIMIT 1")
  SysUserDO selectActiveByUsername(@Param("username") String username);

  @Select(
      """
      SELECT id, username, display_name, email, password_hash, status, failed_login_count,
             last_login_at, auth_version, version, deleted, created_at, updated_at
      FROM sys_user
      WHERE id = #{id} AND deleted = 0
      LIMIT 1
      """)
  SysUserDO selectActiveById(@Param("id") long id);

  @Select(
      """
      SELECT id, username, display_name, email, password_hash, status, failed_login_count,
             last_login_at, auth_version, version, deleted, created_at, updated_at
      FROM sys_user
      WHERE id = #{id} AND deleted = 0
      FOR UPDATE
      """)
  SysUserDO selectActiveByIdForUpdate(@Param("id") long id);

  @Select("SELECT id FROM sys_role WHERE code = #{roleCode} FOR UPDATE")
  Long lockRole(@Param("roleCode") String roleCode);

  @Select(
      """
      SELECT r.code FROM sys_role r
      JOIN sys_user_role ur ON ur.role_id = r.id
      WHERE ur.user_id = #{userId}
      ORDER BY r.code
      """)
  List<String> selectRoleCodes(@Param("userId") long userId);

  @Select(
      """
      SELECT COUNT(*) FROM sys_user u
      JOIN sys_user_role ur ON ur.user_id=u.id
      JOIN sys_role r ON r.id=ur.role_id
      WHERE r.code=#{roleCode} AND u.status='ACTIVE' AND u.deleted=0
      """)
  int countActiveByRole(@Param("roleCode") String roleCode);

  @Insert(
      """
      INSERT INTO sys_user_role(user_id, role_id)
      SELECT #{userId}, id FROM sys_role WHERE code=#{roleCode}
      """)
  int assignRole(@Param("userId") long userId, @Param("roleCode") String roleCode);

  @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
  int deleteRoles(@Param("userId") long userId);

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

  @Update(
      """
      UPDATE sys_user
      SET last_login_at = #{loggedInAt}, failed_login_count = 0,
          version = version + 1, updated_at = #{loggedInAt}
      WHERE id = #{id} AND deleted = 0
      """)
  int recordSuccessfulLogin(
      @Param("id") long id, @Param("loggedInAt") java.time.LocalDateTime loggedInAt);
}
