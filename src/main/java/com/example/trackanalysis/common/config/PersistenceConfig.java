package com.example.trackanalysis.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!no-persistence")
@MapperScan({
  "com.example.trackanalysis.user.infrastructure.persistence",
  "com.example.trackanalysis.dataset.infrastructure.persistence",
  "com.example.trackanalysis.track.infrastructure.persistence",
  "com.example.trackanalysis.analysis.infrastructure.persistence",
  "com.example.trackanalysis.task.infrastructure.persistence",
  "com.example.trackanalysis.report.infrastructure.persistence",
  "com.example.trackanalysis.audit.infrastructure.persistence",
  "com.example.trackanalysis.benchmark.infrastructure.persistence",
  "com.example.trackanalysis.evaluation.infrastructure.persistence",
  "com.example.trackanalysis.outbox",
  "com.example.trackanalysis.web.infrastructure"
})
public class PersistenceConfig {

  @Bean
  MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
    return interceptor;
  }
}
