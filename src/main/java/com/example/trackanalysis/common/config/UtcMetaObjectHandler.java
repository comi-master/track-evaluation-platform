package com.example.trackanalysis.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.Clock;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!no-persistence")
public class UtcMetaObjectHandler implements MetaObjectHandler {

  private final Clock clock;

  public UtcMetaObjectHandler(Clock clock) {
    this.clock = clock;
  }

  @Override
  public void insertFill(MetaObject metaObject) {
    LocalDateTime now = LocalDateTime.now(clock);
    strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
    strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
  }

  @Override
  public void updateFill(MetaObject metaObject) {
    setFieldValByName("updatedAt", LocalDateTime.now(clock), metaObject);
  }
}
