package com.taskflow.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEvent implements Serializable {
    private UUID jobId;
    private String type;
    private String payload;
    private int attempt;
    private int priority;
    private long timestamp;
}
