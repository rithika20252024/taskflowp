package com.taskflow.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsResponse {
    private long queuedJobs;
    private long runningJobs;
    private long completedJobs;
    private long failedJobs;
    private long retryingJobs;
    private long dlqJobs;
    private long cancelledJobs;
    private long totalJobs;
    private double averageProcessingTimeMs;
    private double successRate;
    private long unpublishedOutboxEvents;
}
