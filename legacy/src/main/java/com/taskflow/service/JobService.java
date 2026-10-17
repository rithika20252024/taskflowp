package com.taskflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.api.dto.CreateJobRequest;
import com.taskflow.api.dto.JobResponse;
import com.taskflow.api.dto.MetricsResponse;
import com.taskflow.api.dto.WorkerRaceResponse;
import com.taskflow.domain.Job;
import com.taskflow.domain.JobAttempt;
import com.taskflow.domain.JobStatus;
import com.taskflow.domain.OutboxEvent;
import com.taskflow.exception.JobNotFoundException;
import com.taskflow.messaging.JobEvent;
import com.taskflow.messaging.TaskFlowProducer;
import com.taskflow.repository.JobAttemptRepository;
import com.taskflow.repository.JobRepository;
import com.taskflow.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final JobAttemptRepository jobAttemptRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JobStateMachine stateMachine;
    private final TaskFlowProducer taskFlowProducer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${taskflow.retry.base-delay-seconds:2}")
    private long baseDelaySeconds;

    @Value("${taskflow.retry.max-delay-seconds:60}")
    private long maxDelaySeconds;

    @Autowired
    public JobService(
            JobRepository jobRepository,
            JobAttemptRepository jobAttemptRepository,
            OutboxEventRepository outboxEventRepository,
            JobStateMachine stateMachine,
            TaskFlowProducer taskFlowProducer) {
        this.jobRepository = jobRepository;
        this.jobAttemptRepository = jobAttemptRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.stateMachine = stateMachine;
        this.taskFlowProducer = taskFlowProducer;
    }

    /**
     * Idempotently submits a job.
     * Uses Transactional Outbox Pattern: persists Job and OutboxEvent atomically in DB.
     */
    @Transactional
    public JobResponse submitJob(CreateJobRequest request, String idempotencyKey) {
        // Step 1: Idempotency Check
        Optional<Job> existing = jobRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotency match found for key: {}. Returning existing job {}", idempotencyKey, existing.get().getId());
            return JobResponse.fromEntity(existing.get(), true);
        }

        // Step 2: Create new Job entity
        Job job = Job.builder()
                .idempotencyKey(idempotencyKey)
                .type(request.getType())
                .payload(request.getPayload())
                .priority(request.getPriority() != null ? request.getPriority() : 5)
                .maxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3)
                .status(JobStatus.QUEUED)
                .retryCount(0)
                .build();

        Job savedJob = jobRepository.save(job);

        // Step 3: Transactional Outbox Pattern — Atomically write OutboxEvent
        try {
            JobEvent event = JobEvent.builder()
                    .jobId(savedJob.getId())
                    .type(savedJob.getType())
                    .payload(savedJob.getPayload())
                    .attempt(0)
                    .priority(savedJob.getPriority())
                    .timestamp(System.currentTimeMillis())
                    .build();

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(savedJob.getId())
                    .eventType("JOB_CREATED")
                    .payload(objectMapper.writeValueAsString(event))
                    .published(false)
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.info("Job created with ID: {} and OutboxEvent queued atomically", savedJob.getId());
        } catch (Exception e) {
            log.error("Failed to serialize outbox event for job {}: {}", savedJob.getId(), e.getMessage());
            throw new RuntimeException("Outbox serialization failure", e);
        }

        return JobResponse.fromEntity(savedJob, false);
    }

    /**
     * Atomically claims a job using JPA Optimistic Locking (@Version).
     * Prevents multiple workers from running the same job concurrently.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean claimJob(UUID jobId, String workerId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (job.getStatus() != JobStatus.QUEUED && job.getStatus() != JobStatus.RETRYING) {
            log.debug("Worker {} cannot claim job {}: current status is {}", workerId, jobId, job.getStatus());
            return false;
        }

        stateMachine.transition(job, JobStatus.RUNNING);
        job.setWorkerId(workerId);
        jobRepository.save(job); // Will increment @Version and fail if concurrently modified

        // Record attempt start
        JobAttempt attempt = JobAttempt.builder()
                .jobId(jobId)
                .workerId(workerId)
                .startedAt(LocalDateTime.now())
                .status(JobStatus.RUNNING)
                .build();
        jobAttemptRepository.save(attempt);

        log.info("Worker [{}] successfully claimed job [{}] (Version: {})", workerId, jobId, job.getVersion());
        return true;
    }

    @Transactional
    public void completeJob(UUID jobId, String workerId, String resultMessage) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        stateMachine.transition(job, JobStatus.COMPLETED);
        jobRepository.save(job);

        // Update latest attempt
        List<JobAttempt> attempts = jobAttemptRepository.findByJobIdOrderByStartedAtDesc(jobId);
        if (!attempts.isEmpty()) {
            JobAttempt attempt = attempts.get(0);
            attempt.setCompletedAt(LocalDateTime.now());
            attempt.setDurationMs(Duration.between(attempt.getStartedAt(), attempt.getCompletedAt()).toMillis());
            attempt.setStatus(JobStatus.COMPLETED);
            jobAttemptRepository.save(attempt);
        }

        log.info("Job [{}] marked COMPLETED by worker [{}]", jobId, workerId);
    }

    @Transactional
    public void failJob(UUID jobId, String workerId, String errorMessage) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));

        // Record attempt failure
        List<JobAttempt> attempts = jobAttemptRepository.findByJobIdOrderByStartedAtDesc(jobId);
        if (!attempts.isEmpty()) {
            JobAttempt attempt = attempts.get(0);
            attempt.setCompletedAt(LocalDateTime.now());
            attempt.setDurationMs(Duration.between(attempt.getStartedAt(), attempt.getCompletedAt()).toMillis());
            attempt.setStatus(JobStatus.FAILED);
            attempt.setErrorMessage(errorMessage);
            jobAttemptRepository.save(attempt);
        }

        job.setErrorMessage(errorMessage);
        int currentRetries = job.getRetryCount() + 1;
        job.setRetryCount(currentRetries);

        JobEvent event = JobEvent.builder()
                .jobId(job.getId())
                .type(job.getType())
                .payload(job.getPayload())
                .attempt(currentRetries)
                .priority(job.getPriority())
                .timestamp(System.currentTimeMillis())
                .build();

        if (currentRetries >= job.getMaxRetries()) {
            // Max retries exhausted -> Move to DLQ
            job.setStatus(JobStatus.DLQ);
            jobRepository.save(job);
            taskFlowProducer.sendToDlq(event, "Max retries (" + job.getMaxRetries() + ") exceeded: " + errorMessage);
            log.warn("Job [{}] permanently FAILED and moved to DLQ after {} attempts", jobId, currentRetries);
        } else {
            // Exponential backoff retry: delay = min(baseDelay * 2^attempt, maxDelay)
            long delaySeconds = Math.min((long) (baseDelaySeconds * Math.pow(2, currentRetries - 1)), maxDelaySeconds);
            job.setStatus(JobStatus.RETRYING);
            jobRepository.save(job);
            taskFlowProducer.sendRetry(event, delaySeconds);
            log.info("Job [{}] scheduled for retry #{} after {} seconds backoff", jobId, currentRetries, delaySeconds);
        }
    }

    @Transactional
    public void cancelJob(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        stateMachine.transition(job, JobStatus.CANCELLED);
        jobRepository.save(job);
        log.info("Job [{}] CANCELLED", jobId);
    }

    @Transactional(readOnly = true)
    public Job getJob(UUID jobId) {
        return jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
    }

    @Transactional(readOnly = true)
    public List<Job> getRecentJobs() {
        return jobRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<Job> searchJobs(JobStatus status, String type, Pageable pageable) {
        String normalizedType = type == null || type.isBlank() ? null : type.trim();
        return jobRepository.searchJobs(status, normalizedType, pageable);
    }

    @Transactional(readOnly = true)
    public List<JobAttempt> getJobAttempts(UUID jobId) {
        return jobAttemptRepository.findByJobIdOrderByStartedAtDesc(jobId);
    }

    @Transactional(readOnly = true)
    public List<JobAttempt> getRecentAttempts() {
        return jobAttemptRepository.findTop50ByOrderByStartedAtDesc();
    }

    @Transactional(readOnly = true)
    public MetricsResponse getMetrics() {
        long queued = jobRepository.countByStatus(JobStatus.QUEUED);
        long running = jobRepository.countByStatus(JobStatus.RUNNING);
        long completed = jobRepository.countByStatus(JobStatus.COMPLETED);
        long failed = jobRepository.countByStatus(JobStatus.FAILED);
        long retrying = jobRepository.countByStatus(JobStatus.RETRYING);
        long dlq = jobRepository.countByStatus(JobStatus.DLQ);
        long cancelled = jobRepository.countByStatus(JobStatus.CANCELLED);
        long total = jobRepository.count();
        long unpublishedOutbox = outboxEventRepository.countByPublishedFalse();

        Double avgDuration = jobRepository.getAverageProcessingDurationMs();
        double avgDurationVal = avgDuration != null ? Math.round(avgDuration * 100.0) / 100.0 : 0.0;

        double successRate = total > 0 ? (double) completed / total : 1.0;
        successRate = Math.round(successRate * 1000.0) / 1000.0;

        return MetricsResponse.builder()
                .queuedJobs(queued)
                .runningJobs(running)
                .completedJobs(completed)
                .failedJobs(failed)
                .retryingJobs(retrying)
                .dlqJobs(dlq)
                .cancelledJobs(cancelled)
                .totalJobs(total)
                .averageProcessingTimeMs(avgDurationVal)
                .successRate(successRate)
                .unpublishedOutboxEvents(unpublishedOutbox)
                .build();
    }

    /**
     * Simulates concurrent multi-worker race condition against a single job.
     * Validates that with 20 concurrent threads trying to claim the job, exactly ONE wins
     * and the others trigger OptimisticLockingFailureException or reject claims.
     */
    public WorkerRaceResponse simulateWorkerRace(UUID jobId, int workerCount) {
        long startTime = System.nanoTime();
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(workerCount);

        AtomicInteger successfulClaims = new AtomicInteger(0);
        AtomicInteger rejectedCollisions = new AtomicInteger(0);
        List<String> winnerHolder = new CopyOnWriteArrayList<>();
        List<String> eventLogs = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(workerCount);

        for (int i = 1; i <= workerCount; i++) {
            final String workerName = "worker-sim-" + i;
            executor.submit(() -> {
                try {
                    startSignal.await(); // Simultaneous thunderous start
                    boolean claimed = claimJob(jobId, workerName);
                    if (claimed) {
                        successfulClaims.incrementAndGet();
                        winnerHolder.add(workerName);
                        eventLogs.add("✅ " + workerName + " SUCCESSFULLY CLAIMED job (Optimistic Lock Acquired)");
                    } else {
                        rejectedCollisions.incrementAndGet();
                        eventLogs.add("⚠️ " + workerName + " REJECTED (Job already claimed/not QUEUED)");
                    }
                } catch (OptimisticLockingFailureException e) {
                    rejectedCollisions.incrementAndGet();
                    eventLogs.add("❌ " + workerName + " COLLISION DETECTED (@Version Conflict - Optimistic Lock Excluded)");
                } catch (Exception e) {
                    rejectedCollisions.incrementAndGet();
                    eventLogs.add("❌ " + workerName + " EXCEPTION: " + e.getMessage());
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startSignal.countDown();
        try {
            doneSignal.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();

        long durationNanos = System.nanoTime() - startTime;
        String winner = winnerHolder.isEmpty() ? "None" : winnerHolder.get(0);

        return WorkerRaceResponse.builder()
                .jobId(jobId)
                .totalContenders(workerCount)
                .successfulClaims(successfulClaims.get())
                .rejectedCollisions(rejectedCollisions.get())
                .winnerWorkerId(winner)
                .elapsedNanos(durationNanos)
                .eventLog(eventLogs)
                .build();
    }
}
