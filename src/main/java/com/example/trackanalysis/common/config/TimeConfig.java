package com.example.trackanalysis.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

  @Bean
  Clock utcClock() {
    return Clock.systemUTC();
  }
}
