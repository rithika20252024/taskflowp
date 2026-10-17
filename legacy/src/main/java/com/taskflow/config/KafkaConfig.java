package com.taskflow.config;

import com.taskflow.messaging.KafkaTopicConstants;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
    public NewTopic jobsTopic() {
        return TopicBuilder.name(KafkaTopicConstants.JOBS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
    public NewTopic retryTopic() {
        return TopicBuilder.name(KafkaTopicConstants.RETRY_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
    public NewTopic dlqTopic() {
        return TopicBuilder.name(KafkaTopicConstants.DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
