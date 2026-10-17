package com.taskflow.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerRaceResponse {
    private UUID jobId;
    private int totalContenders;
    private int successfulClaims;
    private int rejectedCollisions;
    private String winnerWorkerId;
    private long elapsedNanos;
    private List<String> eventLog;
}
