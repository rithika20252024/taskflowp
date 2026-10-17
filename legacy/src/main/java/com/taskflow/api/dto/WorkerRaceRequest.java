package com.taskflow.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerRaceRequest {
    private UUID jobId;
    @Builder.Default
    private int workerCount = 20;
}
