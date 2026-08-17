package com.example.trackanalysis.messaging;

import com.example.trackanalysis.task.application.AnalysisTaskProperties;
import java.util.Map;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AnalysisTaskProperties.class)
public class RabbitTopologyConfig {
  public static final String EXCHANGE = "track.analysis.exchange",
      QUEUE = "track.analysis.queue",
      ROUTING = "track.analysis.execute";
  public static final String RETRY_EXCHANGE = "track.analysis.retry.exchange",
      RETRY_QUEUE = "track.analysis.retry.queue";
  public static final String DEAD_EXCHANGE = "track.analysis.dead.exchange",
      DEAD_QUEUE = "track.analysis.dead.queue",
      DEAD_ROUTING = "track.analysis.dead";

  @Bean
  DirectExchange analysisExchange() {
    return new DirectExchange(EXCHANGE, true, false);
  }

  @Bean
  DirectExchange retryExchange() {
    return new DirectExchange(RETRY_EXCHANGE, true, false);
  }

  @Bean
  DirectExchange deadExchange() {
    return new DirectExchange(DEAD_EXCHANGE, true, false);
  }

  @Bean
  Queue analysisQueue() {
    return new Queue(
        QUEUE,
        true,
        false,
        false,
        Map.of("x-dead-letter-exchange", DEAD_EXCHANGE, "x-dead-letter-routing-key", DEAD_ROUTING));
  }

  @Bean
  Queue retryQueue(AnalysisTaskProperties p) {
    return new Queue(
        RETRY_QUEUE,
        true,
        false,
        false,
        Map.of(
            "x-message-ttl",
            p.retryDelayMilliseconds(),
            "x-dead-letter-exchange",
            EXCHANGE,
            "x-dead-letter-routing-key",
            ROUTING));
  }

  @Bean
  Queue deadQueue() {
    return QueueBuilder.durable(DEAD_QUEUE).build();
  }

  @Bean
  Binding analysisBinding() {
    return new Binding(QUEUE, Binding.DestinationType.QUEUE, EXCHANGE, ROUTING, null);
  }

  @Bean
  Binding retryBinding() {
    return new Binding(RETRY_QUEUE, Binding.DestinationType.QUEUE, RETRY_EXCHANGE, ROUTING, null);
  }

  @Bean
  Binding deadBinding() {
    return new Binding(
        DEAD_QUEUE, Binding.DestinationType.QUEUE, DEAD_EXCHANGE, DEAD_ROUTING, null);
  }

  @Bean
  Jackson2JsonMessageConverter rabbitJsonConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  SimpleRabbitListenerContainerFactory manualAckFactory(
      ConnectionFactory connectionFactory,
      Jackson2JsonMessageConverter converter,
      @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup,
      @Value("${spring.rabbitmq.listener.simple.concurrency:2}") int concurrency,
      @Value("${spring.rabbitmq.listener.simple.max-concurrency:8}") int maxConcurrency,
      @Value("${spring.rabbitmq.listener.simple.prefetch:1}") int prefetch) {
    var factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(converter);
    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    factory.setDefaultRequeueRejected(false);
    factory.setAutoStartup(autoStartup);
    factory.setConcurrentConsumers(Math.max(1, concurrency));
    factory.setMaxConcurrentConsumers(Math.max(Math.max(1, concurrency), maxConcurrency));
    factory.setPrefetchCount(Math.max(1, prefetch));
    return factory;
  }
}
