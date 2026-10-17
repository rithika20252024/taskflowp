package com.taskflow.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes events to Kafka topics with fallback to InProcessEventBus.
 */
@Component
public class TaskFlowProducer {

    private static final Logger log = LoggerFactory.getLogger(TaskFlowProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final InProcessEventBus inProcessEventBus;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public TaskFlowProducer(
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
            InProcessEventBus inProcessEventBus) {
        this.kafkaTemplate = kafkaTemplate;
        this.inProcessEventBus = inProcessEventBus;
    }

    public void sendJob(JobEvent event) {
        try {
            if (kafkaTemplate != null) {
                String payload = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(KafkaTopicConstants.JOBS_TOPIC, event.getJobId().toString(), payload)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.warn("Kafka send failed, routing to in-process bus: {}", ex.getMessage());
                                inProcessEventBus.publishJob(event);
                            } else {
                                log.info("Published job {} to Kafka topic {}", event.getJobId(), KafkaTopicConstants.JOBS_TOPIC);
                            }
                        });
                return;
            }
        } catch (Exception e) {
            log.warn("Kafka error: {}. Falling back to in-process event bus", e.getMessage());
        }
        inProcessEventBus.publishJob(event);
    }

    public void sendRetry(JobEvent event, long delaySeconds) {
        log.info("Dispatching retry for job {} with delay {}s", event.getJobId(), delaySeconds);
        try {
            if (kafkaTemplate != null) {
                String payload = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(KafkaTopicConstants.RETRY_TOPIC, event.getJobId().toString(), payload);
                return;
            }
        } catch (Exception e) {
            log.warn("Kafka retry send error: {}", e.getMessage());
        }
        inProcessEventBus.scheduleRetry(event, delaySeconds);
    }

    public void sendToDlq(JobEvent event, String reason) {
        log.warn("Sending job {} to DLQ. Reason: {}", event.getJobId(), reason);
        try {
            if (kafkaTemplate != null) {
                String payload = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(KafkaTopicConstants.DLQ_TOPIC, event.getJobId().toString(), payload);
                return;
            }
        } catch (Exception e) {
            log.warn("Kafka DLQ send error: {}", e.getMessage());
        }
        inProcessEventBus.publishDlq(event, reason);
    }
}
