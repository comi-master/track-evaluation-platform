package com.example.trackanalysis.evaluation.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QualityGateMapper extends BaseMapper<QualityGateDO> {
  @Select("SELECT * FROM quality_gate WHERE evaluation_run_id=#{runId} ORDER BY id")
  List<QualityGateDO> selectByRunId(long runId);
}
