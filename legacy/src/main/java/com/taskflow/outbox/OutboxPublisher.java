package com.taskflow.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.domain.OutboxEvent;
import com.taskflow.messaging.JobEvent;
import com.taskflow.messaging.TaskFlowProducer;
import com.taskflow.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Transactional Outbox Publisher.
 * Solves dual-write problems: Polling publisher reliably pushes unpublished events to the message broker.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final TaskFlowProducer taskFlowProducer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public OutboxPublisher(OutboxEventRepository outboxEventRepository, TaskFlowProducer taskFlowProducer) {
        this.outboxEventRepository = outboxEventRepository;
        this.taskFlowProducer = taskFlowProducer;
    }

    @Scheduled(fixedDelayString = "${taskflow.outbox.publisher-interval-ms:1000}")
    @Transactional
    public void publishUnsentEvents() {
        List<OutboxEvent> unsentEvents = outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        if (unsentEvents.isEmpty()) {
            return;
        }

        log.info("Processing {} unpublished outbox events for message broker distribution", unsentEvents.size());

        for (OutboxEvent event : unsentEvents) {
            try {
                JobEvent jobEvent = objectMapper.readValue(event.getPayload(), JobEvent.class);
                taskFlowProducer.sendJob(jobEvent);
                event.setPublished(true);
                outboxEventRepository.save(event);
                log.debug("Outbox event [{}] published and marked sent", event.getId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event [{}]: {}", event.getId(), e.getMessage());
            }
        }
    }
}
