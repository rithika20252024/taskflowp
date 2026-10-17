package com.taskflow.repository;

import com.taskflow.domain.JobAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobAttemptRepository extends JpaRepository<JobAttempt, UUID> {
    List<JobAttempt> findByJobIdOrderByStartedAtDesc(UUID jobId);
    List<JobAttempt> findTop50ByOrderByStartedAtDesc();
}
