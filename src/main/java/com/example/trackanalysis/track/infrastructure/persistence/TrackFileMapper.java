package com.example.trackanalysis.track.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.trackanalysis.track.domain.ParseStatus;
import com.example.trackanalysis.track.domain.TrackSource;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface TrackFileMapper extends BaseMapper<TrackFileDO> {

  @Select(
      """
      SELECT d.user_id FROM track_file tf JOIN dataset d ON d.id=tf.dataset_id AND d.deleted=0
      WHERE tf.id=#{fileId} AND d.delete_status='ACTIVE'
        AND (#{admin}=TRUE OR d.user_id=#{actorId}) LIMIT 1
      """)
  Long selectVisibleOwnerId(
      @Param("fileId") long fileId, @Param("actorId") long actorId, @Param("admin") boolean admin);

  @Select(
      """
      SELECT tf.* FROM track_file tf JOIN dataset d ON d.id=tf.dataset_id
      WHERE tf.dataset_id=#{datasetId} AND d.deleted=0 ORDER BY tf.created_at DESC,tf.id DESC
      """)
  java.util.List<TrackFileDO> selectActiveDatasetFiles(@Param("datasetId") long datasetId);

  @Select(
      """
      SELECT COUNT(*) FROM analysis_task t JOIN track_file tf ON tf.id=t.track_file_id
      WHERE tf.dataset_id=#{datasetId} AND t.status IN ('PENDING','RUNNING')
      """)
  int countNonTerminalTasks(@Param("datasetId") long datasetId);

  @Insert(
      """
      INSERT INTO track_file
      (dataset_id, original_name, object_name, sha256, file_size, track_source,
       parse_status, point_count, parse_error, version, created_at, updated_at)
      SELECT #{file.datasetId}, #{file.originalName}, #{file.objectName}, #{file.sha256},
             #{file.fileSize}, #{file.trackSource}, #{file.parseStatus}, #{file.pointCount},
             #{file.parseError,jdbcType=VARCHAR}, #{file.version}, #{file.createdAt}, #{file.updatedAt}
      FROM dataset d
      WHERE d.id = #{file.datasetId} AND d.user_id = #{userId} AND d.deleted = 0
        AND d.delete_status='ACTIVE'
      """)
  @Options(useGeneratedKeys = true, keyProperty = "file.id")
  int insertOwned(@Param("file") TrackFileDO file, @Param("userId") long userId);

  @Select(
      """
      SELECT tf.id, tf.dataset_id, tf.original_name, tf.object_name, tf.sha256, tf.file_size,
             tf.track_source, tf.parse_status, tf.point_count, tf.parse_error, tf.version,
             tf.created_at, tf.updated_at
      FROM track_file tf
      JOIN dataset d ON d.id = tf.dataset_id AND d.deleted = 0
      WHERE tf.id = #{fileId} AND d.user_id = #{userId} AND d.delete_status='ACTIVE'
      LIMIT 1
      """)
  TrackFileDO selectOwnedById(@Param("fileId") long fileId, @Param("userId") long userId);

  @Select(
      """
      <script>
      SELECT tf.id, tf.dataset_id, tf.original_name, tf.object_name, tf.sha256, tf.file_size,
             tf.track_source, tf.parse_status, tf.point_count, tf.parse_error, tf.version,
             tf.created_at, tf.updated_at
      FROM track_file tf
      JOIN dataset d ON d.id = tf.dataset_id AND d.deleted = 0
      WHERE tf.dataset_id = #{datasetId} AND d.user_id = #{userId}
      <if test="trackSource != null">AND tf.track_source = #{trackSource}</if>
      <if test="parseStatus != null">AND tf.parse_status = #{parseStatus}</if>
      ORDER BY tf.created_at DESC, tf.id DESC
      </script>
      """)
  IPage<TrackFileDO> selectOwnedPage(
      Page<TrackFileDO> page,
      @Param("datasetId") long datasetId,
      @Param("userId") long userId,
      @Param("trackSource") TrackSource trackSource,
      @Param("parseStatus") ParseStatus parseStatus);

  @Update(
      """
      UPDATE track_file tf
      JOIN dataset d ON d.id = tf.dataset_id AND d.deleted = 0
      SET tf.parse_status = 'PARSING', tf.parse_error = NULL,
          tf.version = tf.version + 1, tf.updated_at = #{updatedAt}
      WHERE tf.id = #{fileId} AND d.user_id = #{userId}
        AND d.delete_status='ACTIVE'
        AND tf.parse_status IN ('UPLOADED', 'FAILED')
      """)
  int claimForParsing(
      @Param("fileId") long fileId,
      @Param("userId") long userId,
      @Param("updatedAt") LocalDateTime updatedAt);

  @Update(
      """
      UPDATE track_file
      SET parse_status = 'PARSED', point_count = #{pointCount}, parse_error = NULL,
          version = version + 1, updated_at = #{updatedAt}
      WHERE id = #{fileId} AND parse_status = 'PARSING'
      """)
  int markParsed(
      @Param("fileId") long fileId,
      @Param("pointCount") long pointCount,
      @Param("updatedAt") LocalDateTime updatedAt);

  @Update(
      """
      UPDATE track_file
      SET parse_status = 'FAILED', point_count = 0, parse_error = #{error},
          version = version + 1, updated_at = #{updatedAt}
      WHERE id = #{fileId} AND parse_status = 'PARSING'
      """)
  int markFailed(
      @Param("fileId") long fileId,
      @Param("error") String error,
      @Param("updatedAt") LocalDateTime updatedAt);
}
