package com.example.trackanalysis.task.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.trackanalysis.task.domain.AnalysisTaskStatus;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AnalysisTaskMapper extends BaseMapper<AnalysisTaskDO> {
  @Insert(
      """
      INSERT INTO analysis_task
      (track_file_id,abnormal_threshold,status,attempt_count,max_attempts,version,created_at,updated_at)
      SELECT #{task.trackFileId},#{task.abnormalThreshold},'PENDING',0,#{task.maxAttempts},0,
             #{task.createdAt},#{task.updatedAt}
      FROM track_file tf JOIN dataset d ON d.id=tf.dataset_id AND d.deleted=0
      WHERE tf.id=#{task.trackFileId} AND d.user_id=#{userId} AND tf.parse_status='PARSED'
      """)
  @Options(useGeneratedKeys = true, keyProperty = "task.id")
  int insertOwnedPending(@Param("task") AnalysisTaskDO task, @Param("userId") long userId);

  @Select(
      """
      SELECT t.* FROM analysis_task t
      JOIN track_file tf ON tf.id=t.track_file_id
      JOIN dataset d ON d.id=tf.dataset_id AND d.deleted=0
      WHERE t.id=#{taskId} AND d.user_id=#{userId} LIMIT 1
      """)
  AnalysisTaskDO selectOwnedById(@Param("taskId") long taskId, @Param("userId") long userId);

  @Select(
      """
      <script>
      SELECT t.* FROM analysis_task t
      JOIN track_file tf ON tf.id=t.track_file_id
      JOIN dataset d ON d.id=tf.dataset_id AND d.deleted=0
      WHERE t.track_file_id=#{fileId} AND d.user_id=#{userId}
      <if test="status != null">AND t.status=#{status}</if>
      ORDER BY t.created_at DESC,t.id DESC
      </script>
      """)
  IPage<AnalysisTaskDO> selectOwnedPage(
      Page<AnalysisTaskDO> page,
      @Param("fileId") long fileId,
      @Param("userId") long userId,
      @Param("status") AnalysisTaskStatus status);

  @Update(
      """
      UPDATE analysis_task SET status='RUNNING',attempt_count=attempt_count+1,
      started_at=#{now},finished_at=NULL,error_message=NULL,version=version+1,updated_at=#{now}
      WHERE id=#{taskId} AND status='PENDING' AND attempt_count < max_attempts
      """)
  int claim(@Param("taskId") long taskId, @Param("now") LocalDateTime now);

  @Update(
      """
      UPDATE analysis_task SET status='SUCCESS',analysis_result_id=#{resultId},finished_at=#{now},
      error_message=NULL,version=version+1,updated_at=#{now}
      WHERE id=#{taskId} AND status='RUNNING'
      """)
  int markSuccess(
      @Param("taskId") long taskId,
      @Param("resultId") long resultId,
      @Param("now") LocalDateTime now);

  @Update(
      """
      UPDATE analysis_task SET status='PENDING',started_at=NULL,error_message=#{error},
      version=version+1,updated_at=#{now} WHERE id=#{taskId} AND status='RUNNING'
      AND attempt_count < max_attempts
      """)
  int scheduleRetry(
      @Param("taskId") long taskId, @Param("error") String error, @Param("now") LocalDateTime now);

  @Update(
      """
      UPDATE analysis_task SET status='FAILED',analysis_result_id=NULL,error_message=#{error},
      finished_at=#{now},version=version+1,updated_at=#{now}
      WHERE id=#{taskId} AND status IN ('PENDING','RUNNING')
      """)
  int markFailed(
      @Param("taskId") long taskId, @Param("error") String error, @Param("now") LocalDateTime now);

  @Update(
      """
      UPDATE analysis_task t JOIN track_file tf ON tf.id=t.track_file_id
      JOIN dataset d ON d.id=tf.dataset_id AND d.deleted=0
      SET t.status='PENDING',t.attempt_count=0,t.analysis_result_id=NULL,t.error_message=NULL,
      t.started_at=NULL,t.finished_at=NULL,t.version=t.version+1,t.updated_at=#{now}
      WHERE t.id=#{taskId} AND d.user_id=#{userId} AND t.status='FAILED'
      """)
  int resetFailedOwned(
      @Param("taskId") long taskId, @Param("userId") long userId, @Param("now") LocalDateTime now);
}
