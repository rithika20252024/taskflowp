package com.taskflow;

import com.taskflow.api.dto.CreateJobRequest;
import com.taskflow.api.dto.JobResponse;
import com.taskflow.domain.Job;
import com.taskflow.domain.JobStatus;
import com.taskflow.repository.JobRepository;
import com.taskflow.service.JobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("Retry Policy & DLQ Routing Tests")
class RetryExponentialBackoffTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("Job transitions to RETRYING and finally to DLQ when max retries are exhausted")
    void testRetriesAndDlqTransition() {
        String key = "retry-test-" + UUID.randomUUID();
        CreateJobRequest request = CreateJobRequest.builder()
                .type("WEBHOOK_DISPATCH")
                .payload("{\"url\": \"https://api.external.com/hook\"}")
                .maxRetries(3)
                .build();

        JobResponse submitted = jobService.submitJob(request, key);
        UUID jobId = submitted.getJobId();

        // Attempt 1: Claim & Fail
        jobService.claimJob(jobId, "worker-1");
        jobService.failJob(jobId, "worker-1", "Connection timed out");
        Job job1 = jobRepository.findById(jobId).orElseThrow();
        assertEquals(JobStatus.RETRYING, job1.getStatus());
        assertEquals(1, job1.getRetryCount());

        // Attempt 2: Claim & Fail
        jobService.claimJob(jobId, "worker-2");
        jobService.failJob(jobId, "worker-2", "503 Service Unavailable");
        Job job2 = jobRepository.findById(jobId).orElseThrow();
        assertEquals(JobStatus.RETRYING, job2.getStatus());
        assertEquals(2, job2.getRetryCount());

        // Attempt 3: Claim & Fail -> Exceeds maxRetries (3) -> DLQ
        jobService.claimJob(jobId, "worker-3");
        jobService.failJob(jobId, "worker-3", "Host unreachable");
        Job job3 = jobRepository.findById(jobId).orElseThrow();
        assertEquals(JobStatus.DLQ, job3.getStatus(), "Job must be routed to DLQ when max retries are exhausted");
        assertEquals(3, job3.getRetryCount());
    }
}
