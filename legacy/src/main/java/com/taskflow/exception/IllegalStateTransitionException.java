package com.taskflow.exception;

import com.taskflow.domain.JobStatus;

public class IllegalStateTransitionException extends RuntimeException {
    public IllegalStateTransitionException(JobStatus from, JobStatus to) {
        super(String.format("Illegal state transition from %s to %s", from, to));
    }
}
