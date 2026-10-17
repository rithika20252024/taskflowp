package com.taskflow.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.worker.JobProcessor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Message consumer for incoming jobs.
 * Subscribes to Kafka topics if present, and registers with InProcessEventBus as fallback.
 */
@Component
public class TaskFlowConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskFlowConsumer.class);

    private final JobProcessor jobProcessor;
    private final InProcessEventBus inProcessEventBus;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public TaskFlowConsumer(JobProcessor jobProcessor, InProcessEventBus inProcessEventBus) {
        this.jobProcessor = jobProcessor;
        this.inProcessEventBus = inProcessEventBus;
    }

    @PostConstruct
    public void init() {
        // Wire in-process event bus directly to worker processor
        inProcessEventBus.registerJobListener(jobProcessor::processJob);
        log.info("TaskFlow worker consumer successfully registered with event dispatchers");
    }

    @KafkaListener(topics = {KafkaTopicConstants.JOBS_TOPIC, KafkaTopicConstants.RETRY_TOPIC},
                   groupId = "taskflow-worker-group",
                   autoStartup = "${spring.kafka.consumer.auto-startup:false}")
    public void onKafkaMessage(String message) {
        try {
            JobEvent event = objectMapper.readValue(message, JobEvent.class);
            log.info("Received job {} from Kafka topic", event.getJobId());
            jobProcessor.processJob(event);
        } catch (Exception e) {
            log.error("Failed to parse and process Kafka job event: {}", e.getMessage(), e);
        }
    }
}
