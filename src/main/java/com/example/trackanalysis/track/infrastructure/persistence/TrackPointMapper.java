package com.example.trackanalysis.track.infrastructure.persistence;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TrackPointMapper {

  int batchInsert(@Param("points") List<TrackPointDO> points);

  @Delete("DELETE FROM track_point WHERE track_file_id = #{fileId}")
  int deleteByFileId(@Param("fileId") long fileId);

  @Select(
      """
      SELECT tp.id, tp.track_file_id, tp.sequence_no, tp.time_value,
             tp.true_x, tp.true_y, tp.true_z, tp.track_x, tp.track_y, tp.track_z, tp.created_at
      FROM track_point tp
      JOIN track_file tf ON tf.id = tp.track_file_id
      JOIN dataset d ON d.id = tf.dataset_id AND d.deleted = 0
      WHERE tp.track_file_id = #{fileId} AND d.user_id = #{userId}
      ORDER BY tp.sequence_no ASC
      """)
  IPage<TrackPointDO> selectOwnedPage(
      Page<TrackPointDO> page, @Param("fileId") long fileId, @Param("userId") long userId);

  @Select(
      """
      SELECT sequence_no, time_value, true_x, true_y, true_z,
             track_x, track_y, track_z
      FROM track_point WHERE track_file_id = #{fileId} AND sequence_no > #{afterSequence}
      ORDER BY sequence_no ASC LIMIT #{limit}
      """)
  List<TrackPointDO> selectAfterSequence(
      @Param("fileId") long fileId,
      @Param("afterSequence") long afterSequence,
      @Param("limit") int limit);
}
