package com.taskflow.api;

import com.taskflow.api.dto.*;
import com.taskflow.domain.Job;
import com.taskflow.domain.JobAttempt;
import com.taskflow.domain.JobStatus;
import com.taskflow.service.JobService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/jobs")
@CrossOrigin(origins = "*")
public class JobController {

    private static final Set<String> JOB_SORT_FIELDS = Set.of("createdAt", "updatedAt", "priority", "status", "type");

    private final JobService jobService;

    @Autowired
    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Creates a new job with idempotency guarantee.
     * Clients supply an Idempotency-Key header. Duplicate calls return the original job response.
     */
    @PostMapping
    public ResponseEntity<JobResponse> createJob(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateJobRequest request) {

        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank()) 
                ? idempotencyKey 
                : UUID.randomUUID().toString();

        JobResponse response = jobService.submitJob(request, effectiveKey);
        HttpStatus status = response.isCachedIdempotentResponse() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID jobId) {
        Job job = jobService.getJob(jobId);
        return ResponseEntity.ok(JobResponse.fromEntity(job, false));
    }

    @GetMapping
    public ResponseEntity<Page<JobResponse>> listJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be between 1 and 100");
        }
        if (!JOB_SORT_FIELDS.contains(sortBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sortBy must be one of: " + String.join(", ", JOB_SORT_FIELDS));
        }
        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "direction must be ASC or DESC");
        }
        Page<JobResponse> jobs = jobService.searchJobs(status, type,
                        PageRequest.of(page, size, Sort.by(sortDirection, sortBy).and(Sort.by("id"))))
                .map(job -> JobResponse.fromEntity(job, false));
        return ResponseEntity.ok(jobs);
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<JobResponse> cancelJob(@PathVariable UUID jobId) {
        jobService.cancelJob(jobId);
        Job job = jobService.getJob(jobId);
        return ResponseEntity.ok(JobResponse.fromEntity(job, false));
    }

    @GetMapping("/{jobId}/attempts")
    public ResponseEntity<List<JobAttemptResponse>> getJobAttempts(@PathVariable UUID jobId) {
        List<JobAttemptResponse> attempts = jobService.getJobAttempts(jobId).stream()
                .map(JobAttemptResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(attempts);
    }

    @GetMapping("/attempts")
    public ResponseEntity<List<JobAttemptResponse>> listRecentAttempts() {
        List<JobAttemptResponse> attempts = jobService.getRecentAttempts().stream()
                .map(JobAttemptResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(attempts);
    }

    /**
     * Concurrency test endpoint: launches N competing worker threads against the given job
     * to empirically demonstrate JPA optimistic locking and race-condition prevention.
     */
    @PostMapping("/{jobId}/simulate-race")
    public ResponseEntity<WorkerRaceResponse> simulateWorkerRace(
            @PathVariable UUID jobId,
            @RequestParam(defaultValue = "20") int workerCount) {

        WorkerRaceResponse response = jobService.simulateWorkerRace(jobId, workerCount);
        return ResponseEntity.ok(response);
    }
}
