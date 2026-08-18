package com.example.trackanalysis.benchmark.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BenchmarkVersionMapper extends BaseMapper<BenchmarkVersionDO> {
  @Select(
      "SELECT * FROM benchmark_version WHERE benchmark_id=#{benchmarkId} AND status='PUBLISHED'"
          + " ORDER BY version_no DESC")
  List<BenchmarkVersionDO> selectPublishedByBenchmark(long benchmarkId);

  @Select("SELECT * FROM benchmark_version WHERE created_by=#{userId} ORDER BY id DESC LIMIT 100")
  List<BenchmarkVersionDO> selectByCreator(long userId);
}
