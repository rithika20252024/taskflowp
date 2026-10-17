package com.taskflow.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Explicit State Machine for TaskFlow Jobs.
 * Prevents invalid state transitions (e.g., COMPLETED -> RUNNING is strictly forbidden).
 */
public enum JobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    RETRYING,
    CANCELLED,
    DLQ;

    private Set<JobStatus> validTransitions;

    static {
        QUEUED.validTransitions = EnumSet.of(RUNNING, CANCELLED);
        RUNNING.validTransitions = EnumSet.of(COMPLETED, FAILED, CANCELLED);
        FAILED.validTransitions = EnumSet.of(RETRYING, DLQ);
        RETRYING.validTransitions = EnumSet.of(QUEUED, CANCELLED);
        // Terminal states cannot transition to anything
        COMPLETED.validTransitions = Collections.emptySet();
        CANCELLED.validTransitions = Collections.emptySet();
        DLQ.validTransitions = EnumSet.of(QUEUED); // Manual replay allowed from DLQ
    }

    public boolean canTransitionTo(JobStatus next) {
        return validTransitions != null && validTransitions.contains(next);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
