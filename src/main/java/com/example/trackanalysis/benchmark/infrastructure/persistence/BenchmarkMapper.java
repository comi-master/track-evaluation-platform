package com.example.trackanalysis.benchmark.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface BenchmarkMapper extends BaseMapper<BenchmarkDO> {
  @Select("SELECT * FROM benchmark WHERE status='PUBLISHED' AND visibility='PUBLIC' ORDER BY id DESC")
  List<BenchmarkDO> selectPublished();
  @Select("SELECT * FROM benchmark WHERE created_by=#{userId} ORDER BY id DESC LIMIT 100")
  List<BenchmarkDO> selectByCreator(long userId);
}
