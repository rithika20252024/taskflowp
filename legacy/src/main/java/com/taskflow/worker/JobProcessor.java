package com.taskflow.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.messaging.JobEvent;
import com.taskflow.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Worker processor that executes jobs.
 * Enforces optimistic claim prior to running to prevent duplicate multi-worker execution.
 */
@Component
public class JobProcessor {

    private static final Logger log = LoggerFactory.getLogger(JobProcessor.class);

    private final JobService jobService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${taskflow.worker.id:worker-default-1}")
    private String workerId;

    @Autowired
    public JobProcessor(JobService jobService) {
        this.jobService = jobService;
    }

    public void processJob(JobEvent event) {
        UUID jobId = event.getJobId();
        String currentWorker = workerId + "-" + Thread.currentThread().getName();

        log.info("Worker [{}] attempting to claim job [{}] (Type: {})", currentWorker, jobId, event.getType());

        boolean claimed = false;
        try {
            claimed = jobService.claimJob(jobId, currentWorker);
        } catch (Exception e) {
            log.warn("Worker [{}] failed to claim job [{}]: {}", currentWorker, jobId, e.getMessage());
            return;
        }

        if (!claimed) {
            log.info("Worker [{}] skipped job [{}] - already claimed or completed by another worker", currentWorker, jobId);
            return;
        }

        log.info("Worker [{}] executing job [{}]...", currentWorker, jobId);

        try {
            // Check for simulated failure trigger in payload
            boolean shouldFail = false;
            String failureReason = "Simulated downstream service failure";
            if (event.getPayload() != null) {
                try {
                    JsonNode node = objectMapper.readTree(event.getPayload());
                    if (node.has("simulate_failure") && node.get("simulate_failure").asBoolean()) {
                        shouldFail = true;
                        if (node.has("failure_reason")) {
                            failureReason = node.get("failure_reason").asText();
                        }
                    }
                } catch (Exception ignored) {
                    if (event.getPayload().contains("simulate_failure")) {
                        shouldFail = true;
                    }
                }
            }

            // Simulate realistic task execution duration
            Thread.sleep(120);

            if (shouldFail) {
                throw new RuntimeException(failureReason);
            }

            // Normal completion
            String result = "Executed successfully by " + currentWorker;
            jobService.completeJob(jobId, currentWorker, result);
            log.info("Worker [{}] finished job [{}]", currentWorker, jobId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jobService.failJob(jobId, currentWorker, "Worker thread interrupted");
        } catch (Exception e) {
            log.error("Worker [{}] encountered error processing job [{}]: {}", currentWorker, jobId, e.getMessage());
            jobService.failJob(jobId, currentWorker, e.getMessage());
        }
    }
}
