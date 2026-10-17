package com.taskflow.api.dto;

import com.taskflow.domain.JobAttempt;
import com.taskflow.domain.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobAttemptResponse {
    private UUID attemptId;
    private UUID jobId;
    private String workerId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
    private JobStatus status;
    private String errorMessage;

    public static JobAttemptResponse fromEntity(JobAttempt attempt) {
        return JobAttemptResponse.builder()
                .attemptId(attempt.getId())
                .jobId(attempt.getJobId())
                .workerId(attempt.getWorkerId())
                .startedAt(attempt.getStartedAt())
                .completedAt(attempt.getCompletedAt())
                .durationMs(attempt.getDurationMs())
                .status(attempt.getStatus())
                .errorMessage(attempt.getErrorMessage())
                .build();
    }
}
