package com.taskflow;

import com.taskflow.domain.Job;
import com.taskflow.domain.JobStatus;
import com.taskflow.exception.IllegalStateTransitionException;
import com.taskflow.service.JobStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Job State Machine Invariant Tests")
class JobStateMachineTest {

    private JobStateMachine stateMachine;
    private Job job;

    @BeforeEach
    void setUp() {
        stateMachine = new JobStateMachine();
        job = Job.builder()
                .status(JobStatus.QUEUED)
                .build();
    }

    @Test
    @DisplayName("Should allow valid transition from QUEUED to RUNNING")
    void testQueuedToRunning() {
        assertTrue(stateMachine.canTransition(JobStatus.QUEUED, JobStatus.RUNNING));
        stateMachine.transition(job, JobStatus.RUNNING);
        assertEquals(JobStatus.RUNNING, job.getStatus());
    }

    @Test
    @DisplayName("Should allow valid transition from RUNNING to COMPLETED")
    void testRunningToCompleted() {
        job.setStatus(JobStatus.RUNNING);
        assertTrue(stateMachine.canTransition(JobStatus.RUNNING, JobStatus.COMPLETED));
        stateMachine.transition(job, JobStatus.COMPLETED);
        assertEquals(JobStatus.COMPLETED, job.getStatus());
    }

    @Test
    @DisplayName("Should forbid illegal transition from COMPLETED to RUNNING")
    void testCompletedToRunningForbidden() {
        job.setStatus(JobStatus.COMPLETED);
        assertFalse(stateMachine.canTransition(JobStatus.COMPLETED, JobStatus.RUNNING));
        assertThrows(IllegalStateTransitionException.class, () -> 
            stateMachine.transition(job, JobStatus.RUNNING)
        );
    }

    @Test
    @DisplayName("Should forbid illegal transition from CANCELLED to RUNNING")
    void testCancelledToRunningForbidden() {
        job.setStatus(JobStatus.CANCELLED);
        assertFalse(stateMachine.canTransition(JobStatus.CANCELLED, JobStatus.RUNNING));
        assertThrows(IllegalStateTransitionException.class, () -> 
            stateMachine.transition(job, JobStatus.RUNNING)
        );
    }

    @Test
    @DisplayName("Should allow FAILED to transition to RETRYING and DLQ")
    void testFailedTransitions() {
        assertTrue(stateMachine.canTransition(JobStatus.FAILED, JobStatus.RETRYING));
        assertTrue(stateMachine.canTransition(JobStatus.FAILED, JobStatus.DLQ));
    }
}
