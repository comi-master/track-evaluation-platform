package com.example.trackanalysis.evaluation.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import java.util.List;
import com.example.trackanalysis.evaluation.api.LeaderboardEntry;

@Mapper
public interface EvaluationRunMapper extends BaseMapper<EvaluationRunDO> {
  @Insert("INSERT IGNORE INTO evaluation_run (submission_id,status,version,created_at,updated_at) VALUES (#{submissionId},#{status},#{version},CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertIfAbsent(EvaluationRunDO run);
  @Select("SELECT p.id AS projectId, p.name AS projectName, s.algorithm_version AS algorithmVersion, s.git_commit AS gitCommit, s.id AS submissionId, er.id AS evaluationRunId, er.gate_status AS gateStatus, CAST(JSON_UNQUOTE(JSON_EXTRACT(er.metrics_json, '$.RMSE')) AS DECIMAL(20,8)) AS rmse, DATE_FORMAT(er.finished_at, '%Y-%m-%dT%H:%i:%s') AS createdAt FROM evaluation_run er JOIN algorithm_submission s ON s.id=er.submission_id JOIN algorithm_project p ON p.id=s.project_id WHERE er.status='SUCCESS' AND p.visibility='PUBLIC' AND s.benchmark_version_id=#{benchmarkVersionId} AND s.protocol_id=#{protocolId} ORDER BY (er.gate_status='PASS') DESC, rmse ASC, er.id ASC LIMIT #{limit}")
  List<LeaderboardEntry> selectLeaderboard(@Param("benchmarkVersionId") long benchmarkVersionId, @Param("protocolId") long protocolId, @Param("limit") int limit);

  @Select("SELECT * FROM evaluation_run WHERE id=#{id} LIMIT 1")
  EvaluationRunDO selectByRunId(@Param("id") long id);

  @Select("SELECT * FROM evaluation_run WHERE submission_id=#{submissionId} LIMIT 1")
  EvaluationRunDO selectBySubmissionId(@Param("submissionId") long submissionId);

  @Update("UPDATE evaluation_run SET analysis_task_id=#{taskId}, status=#{status}, updated_at=CURRENT_TIMESTAMP(6) WHERE id=#{runId} AND status='QUEUED'")
  int attachTask(@Param("runId") long runId, @Param("taskId") long taskId, @Param("status") String status);

  @Update("UPDATE evaluation_run SET analysis_result_id=#{resultId}, status=#{status}, gate_status=#{gateStatus}, metrics_json=#{metricsJson}, failure_message=#{failureMessage}, finished_at=CURRENT_TIMESTAMP(6), updated_at=CURRENT_TIMESTAMP(6), version=version+1 WHERE id=#{runId} AND gate_status IS NULL")
  int finish(@Param("runId") long runId, @Param("resultId") Long resultId, @Param("status") String status,
      @Param("gateStatus") String gateStatus, @Param("metricsJson") String metricsJson,
      @Param("failureMessage") String failureMessage);
}
