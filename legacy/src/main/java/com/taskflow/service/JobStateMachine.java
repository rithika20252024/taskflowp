package com.taskflow.service;

import com.taskflow.domain.Job;
import com.taskflow.domain.JobStatus;
import com.taskflow.exception.IllegalStateTransitionException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Validates and applies strict state machine transitions for TaskFlow jobs.
 */
@Component
public class JobStateMachine {

    public boolean canTransition(JobStatus from, JobStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return from.canTransitionTo(to);
    }

    public void validateTransition(JobStatus from, JobStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateTransitionException(from, to);
        }
    }

    public void transition(Job job, JobStatus targetStatus) {
        validateTransition(job.getStatus(), targetStatus);
        job.setStatus(targetStatus);
        job.setUpdatedAt(LocalDateTime.now());
    }
}
