package com.example.trackanalysis.benchmark.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlgorithmProjectMapper extends BaseMapper<AlgorithmProjectDO> {
  @Select(
      "SELECT * FROM algorithm_project WHERE id=#{id} AND owner_user_id=#{userId} AND status='ACTIVE' LIMIT 1")
  AlgorithmProjectDO selectOwnedById(@Param("id") long id, @Param("userId") long userId);

  @Select(
      "SELECT * FROM algorithm_project WHERE owner_user_id=#{userId} AND status='ACTIVE' ORDER BY created_at DESC, id DESC LIMIT #{limit}")
  List<AlgorithmProjectDO> selectOwned(@Param("userId") long userId, @Param("limit") int limit);
}
