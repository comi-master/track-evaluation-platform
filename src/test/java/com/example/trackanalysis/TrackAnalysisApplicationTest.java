package com.example.trackanalysis;

import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileMapper;
import com.example.trackanalysis.track.infrastructure.persistence.TrackPointMapper;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("no-persistence")
class TrackAnalysisApplicationTest {

  @MockitoBean private SysUserMapper userMapper;
  @MockitoBean private DatasetMapper datasetMapper;
  @MockitoBean private TrackFileMapper trackFileMapper;
  @MockitoBean private TrackPointMapper trackPointMapper;
  @MockitoBean private TransactionTemplate transactionTemplate;

  @Test
  void contextLoads() {}
}
