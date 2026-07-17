package com.example.trackanalysis.messaging;

import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class AnalysisTaskPublisher {
  private final RabbitTemplate rabbit;

  public AnalysisTaskPublisher(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
    rabbit.setMandatory(true);
  }

  public void publish(long taskId) {
    send(RabbitTopologyConfig.EXCHANGE, RabbitTopologyConfig.ROUTING, taskId);
  }

  public void publishRetry(long taskId) {
    send(RabbitTopologyConfig.RETRY_EXCHANGE, RabbitTopologyConfig.ROUTING, taskId);
  }

  public void publishInfrastructureRetry(long taskId, int attempt) {
    send(RabbitTopologyConfig.RETRY_EXCHANGE, RabbitTopologyConfig.ROUTING, taskId, attempt);
  }

  public void publishDead(long taskId) {
    send(RabbitTopologyConfig.DEAD_EXCHANGE, RabbitTopologyConfig.DEAD_ROUTING, taskId);
  }

  private void send(String exchange, String routing, long taskId) {
    send(exchange, routing, taskId, null);
  }

  private void send(String exchange, String routing, long taskId, Integer infrastructureAttempt) {
    CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
    try {
      rabbit.convertAndSend(
          exchange,
          routing,
          new AnalysisTaskMessage(taskId),
          message -> {
            if (infrastructureAttempt != null)
              message
                  .getMessageProperties()
                  .setHeader("x-infrastructure-attempt", infrastructureAttempt);
            return message;
          },
          correlation);
      CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
      if (!confirm.isAck() || correlation.getReturned() != null)
        throw new IllegalStateException("Broker did not confirm routable message");
    } catch (Exception exception) {
      throw new BusinessException(
          ErrorCode.INFRASTRUCTURE_ERROR, "Analysis task publication failed", exception);
    }
  }
}
