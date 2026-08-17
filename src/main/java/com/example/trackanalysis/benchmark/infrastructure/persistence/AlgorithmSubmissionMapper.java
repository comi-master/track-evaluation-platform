package com.example.trackanalysis.benchmark.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface AlgorithmSubmissionMapper extends BaseMapper<AlgorithmSubmissionDO> {
  @Insert("INSERT IGNORE INTO algorithm_submission (project_id, benchmark_version_id, protocol_id, output_track_file_id, algorithm_version, git_commit, submission_key, status, description, created_at, updated_at) VALUES (#{projectId},#{benchmarkVersionId},#{protocolId},#{outputTrackFileId},#{algorithmVersion},#{gitCommit},#{submissionKey},#{status},#{description},CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertIfAbsent(AlgorithmSubmissionDO submission);
  @Select("SELECT * FROM algorithm_submission WHERE id=#{id} LIMIT 1")
  AlgorithmSubmissionDO selectBySubmissionId(@Param("id") long id);

  @Select("SELECT * FROM algorithm_submission WHERE project_id=#{projectId} AND submission_key=#{submissionKey} LIMIT 1")
  AlgorithmSubmissionDO selectByProjectAndKey(@Param("projectId") long projectId, @Param("submissionKey") String submissionKey);
}
