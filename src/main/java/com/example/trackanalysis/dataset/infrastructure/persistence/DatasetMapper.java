package com.example.trackanalysis.dataset.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DatasetMapper extends BaseMapper<DatasetDO> {

  @Select(
      """
      SELECT id, user_id, name, description, version, deleted, created_at, updated_at
      FROM dataset
      WHERE id = #{id} AND user_id = #{userId} AND deleted = 0
      LIMIT 1
      """)
  DatasetDO selectOwnedById(@Param("id") long id, @Param("userId") long userId);

  @Select(
      """
      <script>
      SELECT id, user_id, name, description, version, deleted, created_at, updated_at
      FROM dataset
      WHERE user_id = #{userId} AND deleted = 0
      <if test="keyword != null">
        AND name LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'
      </if>
      ORDER BY created_at DESC, id DESC
      </script>
      """)
  IPage<DatasetDO> selectOwnedPage(
      Page<DatasetDO> page, @Param("userId") long userId, @Param("keyword") String keyword);

  @Update(
      """
      UPDATE dataset
      SET name = #{name},
          description = #{description,jdbcType=VARCHAR},
          version = version + 1,
          updated_at = #{updatedAt}
      WHERE id = #{id}
        AND user_id = #{userId}
        AND version = #{version}
        AND deleted = 0
      """)
  int updateOwned(
      @Param("id") long id,
      @Param("userId") long userId,
      @Param("name") String name,
      @Param("description") String description,
      @Param("version") int version,
      @Param("updatedAt") LocalDateTime updatedAt);

  @Update(
      """
      UPDATE dataset
      SET deleted = 1,
          version = version + 1,
          updated_at = #{updatedAt}
      WHERE id = #{id} AND user_id = #{userId} AND deleted = 0
      """)
  int deleteOwned(
      @Param("id") long id,
      @Param("userId") long userId,
      @Param("updatedAt") LocalDateTime updatedAt);

  @Select(
      """
      SELECT COUNT(*)
      FROM dataset
      WHERE id = #{id} AND user_id = #{userId} AND deleted = 0
      """)
  int countOwnedActive(@Param("id") long id, @Param("userId") long userId);
}
