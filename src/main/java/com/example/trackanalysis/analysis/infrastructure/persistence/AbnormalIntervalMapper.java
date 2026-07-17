package com.example.trackanalysis.analysis.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AbnormalIntervalMapper {
  int batchInsert(@Param("intervals") List<AbnormalIntervalDO> intervals);

  @Select(
      "SELECT ai.* FROM abnormal_interval ai JOIN analysis_result ar ON ar.id=ai.analysis_result_id"
          + " JOIN track_file tf ON tf.id=ar.track_file_id JOIN dataset d ON d.id=tf.dataset_id AND"
          + " d.deleted=0 WHERE ai.analysis_result_id=#{analysisId} AND d.user_id=#{userId} ORDER"
          + " BY ai.interval_no ASC")
  List<AbnormalIntervalDO> selectOwnedByResult(
      @Param("analysisId") long analysisId, @Param("userId") long userId);
}
