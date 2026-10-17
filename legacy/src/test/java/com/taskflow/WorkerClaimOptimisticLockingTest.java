package com.taskflow;

import com.taskflow.api.dto.CreateJobRequest;
import com.taskflow.api.dto.JobResponse;
import com.taskflow.api.dto.WorkerRaceResponse;
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
@DisplayName("Worker Claim Optimistic Locking Concurrency Test")
class WorkerClaimOptimisticLockingTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("20 competing workers hammer 1 job: exactly 1 wins and 19 collide/reject")
    void test20WorkersCompeteForSameJob() {
        // Step 1: Submit a queued job
        String key = "worker-race-" + UUID.randomUUID();
        CreateJobRequest request = CreateJobRequest.builder()
                .type("DATA_SYNC")
                .payload("{\"entity\": \"UserAccounts\"}")
                .priority(10)
                .build();

        JobResponse submitted = jobService.submitJob(request, key);
        UUID jobId = submitted.getJobId();

        // Step 2: Trigger 20 competing workers
        WorkerRaceResponse raceResult = jobService.simulateWorkerRace(jobId, 20);

        // Step 3: Verify invariants
        assertEquals(20, raceResult.getTotalContenders());
        assertEquals(1, raceResult.getSuccessfulClaims(), "Exactly 1 worker must successfully claim the job");
        assertEquals(19, raceResult.getRejectedCollisions(), "All other 19 workers must be rejected via optimistic lock exclusion");
        assertNotEquals("None", raceResult.getWinnerWorkerId(), "A valid winner worker must be assigned");

        // Verify database state
        Job updatedJob = jobRepository.findById(jobId).orElseThrow();
        assertEquals(JobStatus.RUNNING, updatedJob.getStatus());
        assertEquals(raceResult.getWinnerWorkerId(), updatedJob.getWorkerId());
        assertTrue(updatedJob.getVersion() > 0, "Job optimistic lock version must have incremented");
    }
}
