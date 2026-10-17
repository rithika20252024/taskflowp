package com.taskflow.service;

import com.taskflow.domain.Job;
import com.taskflow.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class IdempotencyService {

    private final JobRepository jobRepository;

    @Autowired
    public IdempotencyService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Job> getJobByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return jobRepository.findByIdempotencyKey(idempotencyKey);
    }
}
