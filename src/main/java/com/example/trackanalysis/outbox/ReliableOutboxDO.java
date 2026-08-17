package com.example.trackanalysis.outbox;

public class ReliableOutboxDO {
  private Long id;
  private Long aggregateId;
  private String payloadJson;
  private Integer attemptCount;
  private String claimToken;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }

  public Long getAggregateId() {
    return aggregateId;
  }

  public void setAggregateId(Long value) {
    aggregateId = value;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(String value) {
    payloadJson = value;
  }

  public Integer getAttemptCount() {
    return attemptCount;
  }

  public void setAttemptCount(Integer value) {
    attemptCount = value;
  }

  public String getClaimToken() {
    return claimToken;
  }

  public void setClaimToken(String value) {
    claimToken = value;
  }
}
