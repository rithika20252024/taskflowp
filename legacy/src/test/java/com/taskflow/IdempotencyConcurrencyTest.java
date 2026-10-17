package com.taskflow;

import com.taskflow.api.dto.CreateJobRequest;
import com.taskflow.api.dto.JobResponse;
import com.taskflow.domain.Job;
import com.taskflow.repository.JobRepository;
import com.taskflow.service.JobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("Idempotency 100-Thread Concurrency Stress Test")
class IdempotencyConcurrencyTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("100 concurrent requests with the SAME Idempotency-Key must produce exactly 1 database job")
    void test100ConcurrentIdempotentSubmissions() throws InterruptedException {
        String sharedKey = "idemp-stress-" + UUID.randomUUID();
        int threadCount = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger cachedResponses = new AtomicInteger(0);
        UUID[] capturedJobId = new UUID[1];

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // All 100 threads fire simultaneously
                    CreateJobRequest req = CreateJobRequest.builder()
                            .type("PAYMENT_REPORT")
                            .payload("{\"quarter\": \"Q4\", \"year\": 2026}")
                            .priority(7)
                            .maxRetries(3)
                            .build();

                    JobResponse response = jobService.submitJob(req, sharedKey);
                    successfulRequests.incrementAndGet();

                    if (response.isCachedIdempotentResponse()) {
                        cachedResponses.incrementAndGet();
                    } else {
                        synchronized (capturedJobId) {
                            capturedJobId[0] = response.getJobId();
                        }
                    }
                } catch (Exception e) {
                    // In high concurrency DB race, some threads might catch unique constraint
                    // and subsequent retry returns the cached record
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Release the latch to start all threads simultaneously
        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Verification: Exactly 1 job exists in the database for this idempotency key
        Job storedJob = jobRepository.findByIdempotencyKey(sharedKey).orElse(null);
        assertNotNull(storedJob, "Job must exist in repository");
        assertEquals("PAYMENT_REPORT", storedJob.getType());
        
        long countWithKey = jobRepository.findAll().stream()
                .filter(j -> sharedKey.equals(j.getIdempotencyKey()))
                .count();

        assertEquals(1, countWithKey, "Database must strictly contain exactly 1 job record despite 100 concurrent submissions");
    }
}
