package com.example.trackanalysis.benchmark.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface EvaluationProtocolMapper extends BaseMapper<EvaluationProtocolDO> {
  @Select("SELECT * FROM evaluation_protocol WHERE status='PUBLISHED' AND visibility='PUBLIC' ORDER BY name, version_no DESC")
  List<EvaluationProtocolDO> selectPublished();
  @Select("SELECT * FROM evaluation_protocol WHERE created_by=#{userId} ORDER BY id DESC LIMIT 100")
  List<EvaluationProtocolDO> selectByCreator(long userId);
}
