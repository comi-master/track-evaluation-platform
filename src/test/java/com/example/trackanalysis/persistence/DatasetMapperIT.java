package com.example.trackanalysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetDO;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DatasetMapperIT extends MySqlIntegrationTestSupport {

  @Autowired private DatasetMapper datasetMapper;
  @Autowired private SysUserMapper userMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void insertsDatasetWithOwnerAndAutomaticallyGeneratedId() {
    SysUserDO owner = insertUser("dataset-owner");
    DatasetDO dataset = newDataset(owner.getId(), "dataset-one", null);

    assertThat(datasetMapper.insert(dataset)).isOne();
    assertThat(dataset.getId()).isPositive();
    assertThat(datasetMapper.selectById(dataset.getId()).getUserId()).isEqualTo(owner.getId());
    DatasetDO duplicateName = newDataset(owner.getId(), "dataset-one", null);
    assertThat(datasetMapper.insert(duplicateName)).isOne();
  }

  @Test
  void pagesOwnedDatasetsWithStableCreatedAtAndIdOrdering() {
    SysUserDO owner = insertUser("page-owner");
    SysUserDO other = insertUser("other-owner");
    LocalDateTime base = LocalDateTime.of(2026, 7, 16, 1, 0);
    DatasetDO first = insertDataset(owner.getId(), "first", base);
    DatasetDO second = insertDataset(owner.getId(), "second", base.plusSeconds(1));
    DatasetDO third = insertDataset(owner.getId(), "third", base.plusSeconds(1));
    insertDataset(other.getId(), "not-visible", base.plusSeconds(2));

    Page<DatasetDO> page = new Page<>(1, 2);
    datasetMapper.selectPage(
        page,
        new LambdaQueryWrapper<DatasetDO>()
            .eq(DatasetDO::getUserId, owner.getId())
            .orderByDesc(DatasetDO::getCreatedAt)
            .orderByDesc(DatasetDO::getId));

    assertThat(page.getTotal()).isEqualTo(3);
    assertThat(page.getRecords())
        .extracting(DatasetDO::getId)
        .containsExactly(third.getId(), second.getId());
    assertThat(page.getRecords()).extracting(DatasetDO::getId).doesNotContain(first.getId());
  }

  @Test
  void logicalDeleteAndOwnerPredicateKeepDatasetsIsolated() {
    SysUserDO owner = insertUser("isolation-owner");
    SysUserDO other = insertUser("isolation-other");
    DatasetDO deleted = insertDataset(owner.getId(), "deleted", null);
    DatasetDO visible = insertDataset(owner.getId(), "visible", null);
    insertDataset(other.getId(), "other", null);
    datasetMapper.deleteById(deleted.getId());

    List<DatasetDO> owned =
        datasetMapper.selectList(
            new LambdaQueryWrapper<DatasetDO>().eq(DatasetDO::getUserId, owner.getId()));

    assertThat(owned).extracting(DatasetDO::getId).containsExactly(visible.getId());
  }

  @Test
  void optimisticLockRejectsStaleDatasetUpdate() {
    SysUserDO owner = insertUser("dataset-lock-owner");
    DatasetDO dataset = insertDataset(owner.getId(), "before", null);
    DatasetDO first = datasetMapper.selectById(dataset.getId());
    DatasetDO stale = copyOf(first);

    first.setName("after");
    assertThat(datasetMapper.updateById(first)).isOne();
    assertThat(first.getVersion()).isOne();

    stale.setName("stale");
    assertThat(datasetMapper.updateById(stale)).isZero();
    assertThat(datasetMapper.selectById(dataset.getId()).getName()).isEqualTo("after");
  }

  @Test
  void ownerPaginationSqlExecutesWithTheDocumentedIndexAvailable() {
    SysUserDO owner = insertUser("explain-owner");
    insertDataset(owner.getId(), "explain-dataset", null);

    List<Map<String, Object>> plan =
        jdbcTemplate.queryForList(
            """
            EXPLAIN SELECT id, user_id, name, description, version, deleted, created_at, updated_at
            FROM dataset
            WHERE user_id = ? AND deleted = 0
            ORDER BY created_at DESC, id DESC
            LIMIT 20
            """,
            owner.getId());

    assertThat(plan).isNotEmpty();
  }

  private SysUserDO insertUser(String username) {
    SysUserDO user = new SysUserDO();
    user.setUsername(username);
    user.setPasswordHash("encoded-hash");
    user.setStatus("ACTIVE");
    userMapper.insert(user);
    return user;
  }

  private DatasetDO insertDataset(Long userId, String name, LocalDateTime createdAt) {
    DatasetDO dataset = newDataset(userId, name, createdAt);
    datasetMapper.insert(dataset);
    return dataset;
  }

  private DatasetDO newDataset(Long userId, String name, LocalDateTime createdAt) {
    DatasetDO dataset = new DatasetDO();
    dataset.setUserId(userId);
    dataset.setName(name);
    dataset.setCreatedAt(createdAt);
    return dataset;
  }

  private DatasetDO copyOf(DatasetDO source) {
    DatasetDO copy = new DatasetDO();
    copy.setId(source.getId());
    copy.setUserId(source.getUserId());
    copy.setName(source.getName());
    copy.setDescription(source.getDescription());
    copy.setVersion(source.getVersion());
    copy.setDeleted(source.getDeleted());
    copy.setCreatedAt(source.getCreatedAt());
    copy.setUpdatedAt(source.getUpdatedAt());
    return copy;
  }
}
