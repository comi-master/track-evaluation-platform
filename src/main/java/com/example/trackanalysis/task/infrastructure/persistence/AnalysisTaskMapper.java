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
  @Select(
      """
      SELECT t.* FROM analysis_task t JOIN track_file tf ON tf.id=t.track_file_id
      JOIN dataset d ON d.id=tf.dataset_id AND d.deleted=0
      WHERE t.id=#{taskId} AND (#{admin}=TRUE OR d.user_id=#{actorId}) LIMIT 1
      """)
  AnalysisTaskDO selectVisibleById(
      @Param("taskId") long taskId, @Param("actorId") long actorId, @Param("admin") boolean admin);

  @Insert(
      """
      INSERT INTO analysis_task
      (track_file_id,abnormal_threshold,status,attempt_count,max_attempts,version,created_at,updated_at)
      SELECT #{task.trackFileId},#{task.abnormalThreshold},'PENDING',0,#{task.maxAttempts},0,
             #{task.createdAt},#{task.updatedAt}
      FROM track_file tf JOIN dataset d ON d.id=tf.dataset_id AND d.deleted=0
      WHERE tf.id=#{task.trackFileId} AND d.user_id=#{userId} AND d.delete_status='ACTIVE'
        AND tf.parse_status='PARSED'
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

  @Select(
      """
      <script>
      SELECT t.* FROM analysis_task t JOIN track_file tf ON tf.id=t.track_file_id
      JOIN dataset d ON d.id=tf.dataset_id AND d.deleted=0
      WHERE (#{admin}=TRUE OR d.user_id=#{actorId})
      <if test="fileId != null">AND t.track_file_id=#{fileId}</if>
      <if test="ownerId != null and admin">AND d.user_id=#{ownerId}</if>
      <if test="status != null">AND t.status=#{status}</if>
      ORDER BY t.created_at DESC,t.id DESC
      </script>
      """)
  IPage<AnalysisTaskDO> selectVisiblePage(
      Page<AnalysisTaskDO> page,
      @Param("actorId") long actorId,
      @Param("admin") boolean admin,
      @Param("fileId") Long fileId,
      @Param("ownerId") Long ownerId,
      @Param("status") AnalysisTaskStatus status);

  @Update(
      """
      UPDATE analysis_task SET status='RUNNING',attempt_count=attempt_count+1,
      lease_owner=#{owner},lease_token=#{token},
      lease_expires_at=TIMESTAMPADD(MICROSECOND,#{leaseDurationMilliseconds} * 1000,UTC_TIMESTAMP(6)),
      heartbeat_at=UTC_TIMESTAMP(6),
      started_at=#{now},finished_at=NULL,error_message=NULL,version=version+1,updated_at=#{now}
      WHERE id=#{taskId} AND status='PENDING' AND attempt_count < max_attempts
      """)
  int claim(
      @Param("taskId") long taskId,
      @Param("owner") String owner,
      @Param("token") String token,
      @Param("now") LocalDateTime now,
      @Param("leaseDurationMilliseconds") long leaseDurationMilliseconds);

  @Update(
      """
      UPDATE analysis_task SET heartbeat_at=UTC_TIMESTAMP(6),
      lease_expires_at=TIMESTAMPADD(MICROSECOND,#{leaseDurationMilliseconds} * 1000,UTC_TIMESTAMP(6)),
      updated_at=#{now}
      WHERE id=#{taskId} AND status='RUNNING' AND lease_token=#{token}
      """)
  int renewLease(
      @Param("taskId") long taskId,
      @Param("token") String token,
      @Param("now") LocalDateTime now,
      @Param("leaseDurationMilliseconds") long leaseDurationMilliseconds);

  @Update(
      """
      UPDATE analysis_task SET status='PENDING',started_at=NULL,error_message='Recovered expired lease',
      lease_owner=NULL,lease_token=NULL,lease_expires_at=NULL,heartbeat_at=NULL,
      version=version+1,updated_at=#{now}
      WHERE id=#{taskId} AND status='RUNNING' AND lease_expires_at < UTC_TIMESTAMP(6)
        AND attempt_count < max_attempts
      """)
  int recoverExpiredRunning(@Param("taskId") long taskId, @Param("now") LocalDateTime now);

  @Update(
      """
      UPDATE analysis_task SET status='FAILED',error_message='Interrupted execution exhausted retries',
      lease_owner=NULL,lease_token=NULL,lease_expires_at=NULL,heartbeat_at=NULL,
      finished_at=#{now},version=version+1,updated_at=#{now}
      WHERE id=#{taskId} AND status='RUNNING' AND lease_expires_at < UTC_TIMESTAMP(6)
        AND attempt_count >= max_attempts
      """)
  int failExpiredExhausted(@Param("taskId") long taskId, @Param("now") LocalDateTime now);

  @Update(
      """
      UPDATE analysis_task SET status='SUCCESS',analysis_result_id=#{resultId},finished_at=#{now},
      error_message=NULL,lease_owner=NULL,lease_token=NULL,lease_expires_at=NULL,heartbeat_at=NULL,
      version=version+1,updated_at=#{now}
      WHERE id=#{taskId} AND status='RUNNING' AND lease_token=#{token}
        AND lease_expires_at >= UTC_TIMESTAMP(6)
      """)
  int markSuccess(
      @Param("taskId") long taskId,
      @Param("resultId") long resultId,
      @Param("token") String token,
      @Param("now") LocalDateTime now);

  @Update(
      """
      UPDATE analysis_task SET status='PENDING',started_at=NULL,error_message=#{error},
      lease_owner=NULL,lease_token=NULL,lease_expires_at=NULL,heartbeat_at=NULL,
      version=version+1,updated_at=#{now} WHERE id=#{taskId} AND status='RUNNING'
      AND lease_token=#{token} AND attempt_count < max_attempts
      """)
  int scheduleRetry(
      @Param("taskId") long taskId,
      @Param("token") String token,
      @Param("error") String error,
      @Param("now") LocalDateTime now);

  @Update(
      """
      UPDATE analysis_task SET status='FAILED',analysis_result_id=NULL,error_message=#{error},
      lease_owner=NULL,lease_token=NULL,lease_expires_at=NULL,heartbeat_at=NULL,
      finished_at=#{now},version=version+1,updated_at=#{now}
      WHERE id=#{taskId} AND status='RUNNING' AND lease_token=#{token}
      """)
  int markFailedOwned(
      @Param("taskId") long taskId,
      @Param("token") String token,
      @Param("error") String error,
      @Param("now") LocalDateTime now);

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

  @Update(
      """
      UPDATE analysis_task t JOIN track_file tf ON tf.id=t.track_file_id
      JOIN dataset d ON d.id=tf.dataset_id AND d.deleted=0
      SET t.status='CANCELLED',t.finished_at=#{now},t.error_message=NULL,
          t.version=t.version+1,t.updated_at=#{now}
      WHERE t.id=#{taskId} AND d.user_id=#{userId} AND t.status='PENDING'
      """)
  int cancelPendingOwned(
      @Param("taskId") long taskId, @Param("userId") long userId, @Param("now") LocalDateTime now);
}
