package com.taskflow.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * High-performance concurrent in-memory event bus.
 * Serves as an immediate fallback / local development bus when external Kafka is offline.
 */
@Component
public class InProcessEventBus {

    private static final Logger log = LoggerFactory.getLogger(InProcessEventBus.class);

    private final BlockingQueue<JobEvent> jobQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(2);
    private Consumer<JobEvent> jobListener;

    public void registerJobListener(Consumer<JobEvent> listener) {
        this.jobListener = listener;
    }

    public void publishJob(JobEvent event) {
        log.info("[InProcessBus] Published job {} to in-process queue", event.getJobId());
        jobQueue.offer(event);
        dispatchNext();
    }

    public void scheduleRetry(JobEvent event, long delaySeconds) {
        log.info("[InProcessBus] Scheduling retry for job {} with exponential backoff: {}s", event.getJobId(), delaySeconds);
        retryScheduler.schedule(() -> {
            jobQueue.offer(event);
            dispatchNext();
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public void publishDlq(JobEvent event, String reason) {
        log.warn("[InProcessBus] Routed job {} to Dead-Letter Queue (DLQ). Reason: {}", event.getJobId(), reason);
    }

    private void dispatchNext() {
        if (jobListener != null) {
            CompletableFuture.runAsync(() -> {
                JobEvent event = jobQueue.poll();
                if (event != null) {
                    try {
                        jobListener.accept(event);
                    } catch (Exception e) {
                        log.error("Error processing event in InProcessBus: {}", e.getMessage(), e);
                    }
                }
            });
        }
    }
}
