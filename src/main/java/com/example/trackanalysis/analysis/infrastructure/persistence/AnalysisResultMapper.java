package com.example.trackanalysis.analysis.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AnalysisResultMapper extends BaseMapper<AnalysisResultDO> {
  @Select(
      "SELECT ar.* FROM analysis_result ar JOIN track_file tf ON tf.id=ar.track_file_id JOIN"
          + " dataset d ON d.id=tf.dataset_id AND d.deleted=0 WHERE ar.track_file_id=#{fileId} AND"
          + " d.user_id=#{userId} ORDER BY ar.created_at DESC,ar.id DESC LIMIT 1")
  AnalysisResultDO selectLatestOwned(@Param("fileId") long fileId, @Param("userId") long userId);

  @Select(
      "SELECT ar.* FROM analysis_result ar JOIN track_file tf ON tf.id=ar.track_file_id JOIN"
          + " dataset d ON d.id=tf.dataset_id AND d.deleted=0 WHERE ar.track_file_id=#{fileId} AND"
          + " d.user_id=#{userId} ORDER BY ar.created_at DESC,ar.id DESC")
  IPage<AnalysisResultDO> selectOwnedPage(
      Page<AnalysisResultDO> page, @Param("fileId") long fileId, @Param("userId") long userId);

  @Select(
      """
      SELECT ar.* FROM analysis_result ar
      JOIN track_file tf ON tf.id = ar.track_file_id
      JOIN dataset d ON d.id = tf.dataset_id AND d.deleted = 0
      WHERE d.id = #{datasetId} AND d.user_id = #{userId}
        AND NOT EXISTS (SELECT 1 FROM analysis_result newer WHERE newer.track_file_id = ar.track_file_id
          AND (newer.created_at > ar.created_at OR (newer.created_at = ar.created_at AND newer.id > ar.id)))
      ORDER BY tf.created_at ASC, tf.id ASC
      """)
  List<AnalysisResultDO> selectLatestByOwnedDataset(
      @Param("datasetId") long datasetId, @Param("userId") long userId);

  @Select(
      "SELECT ar.* FROM analysis_result ar JOIN track_file tf ON tf.id = ar.track_file_id JOIN"
          + " dataset d ON d.id = tf.dataset_id AND d.deleted = 0 WHERE ar.id = #{analysisId} AND"
          + " d.user_id = #{userId} LIMIT 1")
  AnalysisResultDO selectOwnedById(
      @Param("analysisId") long analysisId, @Param("userId") long userId);
}
