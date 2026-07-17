package com.example.trackanalysis.report.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AnalysisReportMapper extends BaseMapper<AnalysisReportDO> {
  @Select(
      """
      SELECT r.* FROM analysis_report r JOIN dataset d ON d.id=r.dataset_id AND d.deleted=0
      WHERE r.id=#{reportId} AND d.user_id=#{userId} LIMIT 1
      """)
  AnalysisReportDO selectOwnedById(@Param("reportId") long reportId, @Param("userId") long userId);

  @Select(
      """
      SELECT r.* FROM analysis_report r JOIN dataset d ON d.id=r.dataset_id AND d.deleted=0
      WHERE r.dataset_id=#{datasetId} AND d.user_id=#{userId}
      ORDER BY r.created_at DESC,r.id DESC
      """)
  IPage<AnalysisReportDO> selectOwnedPage(
      Page<AnalysisReportDO> page,
      @Param("datasetId") long datasetId,
      @Param("userId") long userId);

  @Select(
      """
      SELECT tf.id file_id,tf.original_name,tf.track_source,ar.point_count,ar.abnormal_threshold,
       ar.mean_error,ar.rmse,ar.min_error,ar.max_error,ar.standard_deviation,ar.abnormal_count,
       ar.abnormal_ratio,ar.max_error_time,ar.created_at analyzed_at
      FROM analysis_result ar JOIN track_file tf ON tf.id=ar.track_file_id
      JOIN dataset d ON d.id=tf.dataset_id AND d.deleted=0
      WHERE d.id=#{datasetId} AND d.user_id=#{userId}
       AND NOT EXISTS (SELECT 1 FROM analysis_result n WHERE n.track_file_id=ar.track_file_id
        AND (n.created_at>ar.created_at OR (n.created_at=ar.created_at AND n.id>ar.id)))
      ORDER BY tf.created_at ASC,tf.id ASC
      """)
  List<ReportSourceRow> selectLatestSources(
      @Param("datasetId") long datasetId, @Param("userId") long userId);
}
