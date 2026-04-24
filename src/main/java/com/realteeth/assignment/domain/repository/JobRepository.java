package com.realteeth.assignment.domain.repository;

import com.realteeth.assignment.domain.entity.Job;
import com.realteeth.assignment.domain.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findByJobId(UUID jobId);

    List<Job> findByRequestHash(String requestHash);

    List<Job> findByStatus(JobStatus status);

    @Query(value = """
            SELECT * FROM jobs
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Job> findPendingJobsForUpdate(@Param("limit") int limit);
}
