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
    copy.setVersion(source.getVersion());
    copy.setDeleted(source.getDeleted());
    copy.setCreatedAt(source.getCreatedAt());
    copy.setUpdatedAt(source.getUpdatedAt());
    return copy;
  }
}
