package com.example.trackanalysis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("no-persistence")
class TrackAnalysisApplicationTest {

  @Test
  void contextLoads() {}
}
