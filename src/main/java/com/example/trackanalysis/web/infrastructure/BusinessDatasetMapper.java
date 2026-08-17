package com.example.trackanalysis.web.infrastructure;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.trackanalysis.track.domain.TrackSource;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BusinessDatasetMapper {
  @Select(
      """
      <script>
      SELECT d.id, d.user_id owner_id, u.username owner_username, d.name, d.description,d.delete_status,
             tf.id file_id, tf.original_name, tf.sha256, tf.file_size, tf.track_source,
             tf.parse_status, tf.point_count, d.created_at
      FROM dataset d
      JOIN sys_user u ON u.id=d.user_id
      LEFT JOIN track_file tf ON tf.id=(SELECT x.id FROM track_file x WHERE x.dataset_id=d.id ORDER BY x.created_at DESC,x.id DESC LIMIT 1)
      WHERE d.deleted=0
      <if test="!admin">AND d.user_id=#{actorId}</if>
      <if test="keyword != null">AND d.name LIKE CONCAT('%',#{keyword},'%') ESCAPE '!'</if>
      <if test="source != null">AND tf.track_source=#{source}</if>
      <if test="ownerId != null and admin">AND d.user_id=#{ownerId}</if>
      <if test="createdFrom != null">AND d.created_at &gt;= #{createdFrom}</if>
      <if test="createdTo != null">AND d.created_at &lt; #{createdTo}</if>
      ORDER BY d.created_at DESC,d.id DESC
      </script>
      """)
  IPage<BusinessDatasetRow> selectPage(
      Page<BusinessDatasetRow> page,
      @Param("actorId") long actorId,
      @Param("admin") boolean admin,
      @Param("keyword") String keyword,
      @Param("source") TrackSource source,
      @Param("ownerId") Long ownerId,
      @Param("createdFrom") LocalDateTime createdFrom,
      @Param("createdTo") LocalDateTime createdTo);

  @Select(
      """
      SELECT d.id, d.user_id owner_id, u.username owner_username, d.name, d.description,d.delete_status,
             tf.id file_id, tf.original_name, tf.sha256, tf.file_size, tf.track_source,
             tf.parse_status, tf.point_count, d.created_at
      FROM dataset d JOIN sys_user u ON u.id=d.user_id
      LEFT JOIN track_file tf ON tf.id=(SELECT x.id FROM track_file x WHERE x.dataset_id=d.id ORDER BY x.created_at DESC,x.id DESC LIMIT 1)
      WHERE d.id=#{id} AND d.deleted=0 AND (#{admin}=TRUE OR d.user_id=#{actorId}) LIMIT 1
      """)
  BusinessDatasetRow selectVisible(
      @Param("id") long id, @Param("actorId") long actorId, @Param("admin") boolean admin);
}
