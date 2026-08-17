package com.example.trackanalysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import com.example.trackanalysis.user.infrastructure.persistence.UnsafeSysUserTestMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SysUserMapperIT extends MySqlIntegrationTestSupport {

  @Autowired private SysUserMapper userMapper;
  @Autowired private UnsafeSysUserTestMapper unsafeMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void insertsSelectsAndAutomaticallyFillsTechnicalFields() {
    SysUserDO user = newUser("mapper-user");

    assertThat(userMapper.insert(user)).isOne();
    assertThat(user.getId()).isPositive();
    assertThat(user.getCreatedAt()).isNotNull();
    assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());

    SysUserDO stored = userMapper.selectById(user.getId());
    assertThat(stored.getUsername()).isEqualTo("mapper-user");
    assertThat(stored.getAuthVersion()).isZero();
    assertThat(stored.getVersion()).isZero();
    assertThat(stored.getDeleted()).isZero();
  }

  @Test
  void logicalDeleteHidesTheRowWithoutPhysicalDeletion() {
    SysUserDO user = newUser("logical-delete-user");
    userMapper.insert(user);

    assertThat(userMapper.deleteById(user.getId())).isOne();
    assertThat(userMapper.selectById(user.getId())).isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT deleted FROM sys_user WHERE id = ?", Integer.class, user.getId()))
        .isOne();
  }

  @Test
  void deletedDisabledUserCanBeExplicitlyRestored() {
    SysUserDO user = newUser("restore-deleted-user");
    user.setStatus("DISABLED");
    userMapper.insert(user);
    userMapper.deleteById(user.getId());

    assertThat(userMapper.selectByUsername(user.getUsername())).isNull();
    assertThat(userMapper.selectByUsernameIncludingDeleted(user.getUsername()).getDeleted())
        .isOne();
    assertThat(userMapper.restoreDeleted(user.getId(), java.time.LocalDateTime.now())).isOne();
    assertThat(userMapper.selectByUsername(user.getUsername()).getStatus()).isEqualTo("DISABLED");
  }

  @Test
  void optimisticLockRejectsAStaleUpdateAndRefreshesUpdatedAt() {
    SysUserDO user = newUser("optimistic-user");
    userMapper.insert(user);
    SysUserDO first = userMapper.selectById(user.getId());
    SysUserDO stale = copyOf(first);

    first.setStatus("DISABLED");
    assertThat(userMapper.updateById(first)).isOne();
    assertThat(first.getVersion()).isOne();
    assertThat(first.getUpdatedAt()).isAfter(user.getUpdatedAt());

    stale.setStatus("DISABLED");
    assertThat(userMapper.updateById(stale)).isZero();
  }

  @Test
  void exactUsernameLookupIncludesDisabledButActiveLookupDoesNot() {
    SysUserDO active = newUser("lookup-active-user");
    SysUserDO disabled = newUser("lookup-disabled-user");
    disabled.setStatus("DISABLED");
    userMapper.insert(active);
    userMapper.insert(disabled);

    assertThat(userMapper.selectByUsername(active.getUsername()).getStatus()).isEqualTo("ACTIVE");
    assertThat(userMapper.selectByUsername(disabled.getUsername()).getStatus())
        .isEqualTo("DISABLED");
    assertThat(userMapper.selectActiveByUsername(disabled.getUsername())).isNull();
    assertThat(userMapper.selectByUsername("lookup-missing-user")).isNull();
  }

  @Test
  void blockAttackRejectsUpdateWithoutPredicate() {
    userMapper.insert(newUser("protected-user"));

    assertThatThrownBy(unsafeMapper::updateEveryUserWithoutPredicate)
        .isInstanceOf(MyBatisSystemException.class)
        .hasRootCauseInstanceOf(MybatisPlusException.class)
        .hasRootCauseMessage("Prohibition of table update operation");
  }

  private SysUserDO newUser(String username) {
    SysUserDO user = new SysUserDO();
    user.setUsername(username);
    user.setPasswordHash("encoded-hash");
    user.setStatus("ACTIVE");
    return user;
  }

  private SysUserDO copyOf(SysUserDO source) {
    SysUserDO copy = new SysUserDO();
    copy.setId(source.getId());
    copy.setUsername(source.getUsername());
    copy.setPasswordHash(source.getPasswordHash());
    copy.setStatus(source.getStatus());
    copy.setAuthVersion(source.getAuthVersion());
    copy.setVersion(source.getVersion());
    copy.setDeleted(source.getDeleted());
    copy.setCreatedAt(source.getCreatedAt());
    copy.setUpdatedAt(source.getUpdatedAt());
    return copy;
  }
}
