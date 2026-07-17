package com.example.trackanalysis;

import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("no-persistence")
class TrackAnalysisApplicationTest {

  @MockitoBean private SysUserMapper userMapper;
  @MockitoBean private DatasetMapper datasetMapper;

  @Test
  void contextLoads() {}
}
