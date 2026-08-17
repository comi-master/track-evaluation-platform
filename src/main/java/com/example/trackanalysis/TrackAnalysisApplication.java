package com.example.trackanalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TrackAnalysisApplication {

  public static void main(String[] args) {
    SpringApplication.run(TrackAnalysisApplication.class, args);
  }
}
