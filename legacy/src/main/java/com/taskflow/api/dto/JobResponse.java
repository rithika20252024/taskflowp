package com.taskflow.api.dto;

import com.taskflow.domain.Job;
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
public class JobResponse {
    private UUID jobId;
    private String idempotencyKey;
    private String type;
    private String payload;
    private Integer priority;
    private JobStatus status;
    private String workerId;
    private Integer retryCount;
    private Integer maxRetries;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;
    private boolean isCachedIdempotentResponse;

    public static JobResponse fromEntity(Job job, boolean isCached) {
        return JobResponse.builder()
                .jobId(job.getId())
                .idempotencyKey(job.getIdempotencyKey())
                .type(job.getType())
                .payload(job.getPayload())
                .priority(job.getPriority())
                .status(job.getStatus())
                .workerId(job.getWorkerId())
                .retryCount(job.getRetryCount())
                .maxRetries(job.getMaxRetries())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .version(job.getVersion())
                .isCachedIdempotentResponse(isCached)
                .build();
    }
}
