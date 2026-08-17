package com.example.trackanalysis.outbox;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

public interface ReliableOutboxMapper {
  @Insert(
      """
      INSERT IGNORE INTO reliable_outbox
      (event_key,event_type,aggregate_type,aggregate_id,payload_json,status,available_at,created_at)
      VALUES(#{key},#{type},#{aggregateType},#{aggregateId},CAST(#{payload} AS JSON),'PENDING',UTC_TIMESTAMP(6),#{now})
      """)
  int insert(
      @Param("key") String key,
      @Param("type") String type,
      @Param("aggregateType") String aggregateType,
      @Param("aggregateId") long aggregateId,
      @Param("payload") String payload,
      @Param("now") LocalDateTime now);

  @Select(
      """
      SELECT id,aggregate_id,payload_json,attempt_count,claim_token FROM reliable_outbox
      WHERE event_type=#{type} AND status='PENDING' AND available_at <= UTC_TIMESTAMP(6)
      ORDER BY id LIMIT 1
      """)
  ReliableOutboxDO next(@Param("type") String type, @Param("now") LocalDateTime now);

  @Update(
      """
      UPDATE reliable_outbox SET status='PROCESSING',claimed_at=UTC_TIMESTAMP(6),claim_token=#{token},
      attempt_count=attempt_count+1
      WHERE id=#{id} AND status='PENDING' AND available_at <= UTC_TIMESTAMP(6)
      """)
  int claim(@Param("id") long id, @Param("token") String token, @Param("now") LocalDateTime now);

  @Update(
      "UPDATE reliable_outbox SET"
          + " status='PROCESSED',processed_at=#{now},last_error=NULL,claim_token=NULL WHERE"
          + " id=#{id} AND status='PROCESSING' AND claim_token=#{token}")
  int complete(@Param("id") long id, @Param("token") String token, @Param("now") LocalDateTime now);

  @Update(
      """
      UPDATE reliable_outbox SET status='PENDING',
      available_at=TIMESTAMPADD(SECOND,#{delaySeconds},UTC_TIMESTAMP(6)),claimed_at=NULL,
      claim_token=NULL,last_error=#{error}
      WHERE id=#{id} AND status='PROCESSING' AND claim_token=#{token}
      """)
  int retry(
      @Param("id") long id,
      @Param("token") String token,
      @Param("delaySeconds") long delaySeconds,
      @Param("error") String error);

  @Update(
      """
      UPDATE reliable_outbox SET status='PENDING',claimed_at=NULL,claim_token=NULL,
      available_at=UTC_TIMESTAMP(6)
      WHERE status='PROCESSING'
        AND claimed_at < TIMESTAMPADD(MINUTE,-5,UTC_TIMESTAMP(6))
      """)
  int recover(@Param("staleBefore") LocalDateTime staleBefore, @Param("now") LocalDateTime now);
}
