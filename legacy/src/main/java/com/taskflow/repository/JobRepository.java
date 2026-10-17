package com.taskflow.repository;

import com.taskflow.domain.Job;
import com.taskflow.domain.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    List<Job> findByStatusOrderByPriorityDescCreatedAtAsc(JobStatus status);

    List<Job> findTop50ByOrderByCreatedAtDesc();

    @Query("SELECT j FROM Job j WHERE (:status IS NULL OR j.status = :status) " +
            "AND (:type IS NULL OR LOWER(j.type) = LOWER(:type))")
    Page<Job> searchJobs(@Param("status") JobStatus status, @Param("type") String type, Pageable pageable);

    long countByStatus(JobStatus status);

    @Query("SELECT AVG(a.durationMs) FROM JobAttempt a WHERE a.status = 'COMPLETED'")
    Double getAverageProcessingDurationMs();
}
