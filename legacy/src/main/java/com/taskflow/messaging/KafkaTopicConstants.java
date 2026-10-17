package com.taskflow.messaging;

public final class KafkaTopicConstants {
    public static final String JOBS_TOPIC = "taskflow.jobs";
    public static final String RETRY_TOPIC = "taskflow.retry";
    public static final String DLQ_TOPIC = "taskflow.dlq";

    private KafkaTopicConstants() {}
}
