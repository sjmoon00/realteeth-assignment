package com.realteeth.assignment.domain.entity;

import com.realteeth.assignment.domain.enums.JobStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "job_id", nullable = false, unique = true, updatable = false, columnDefinition = "VARCHAR(36)")
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "worker_job_id")
    private String workerJobId;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Job create(String imageUrl, String requestHash) {
        Job job = new Job();
        job.jobId = UUID.randomUUID();
        job.status = JobStatus.PENDING;
        job.imageUrl = imageUrl;
        job.requestHash = requestHash;
        return job;
    }

    public void markProcessing(String workerJobId) {
        this.status = this.status.transitionTo(JobStatus.PROCESSING);
        this.workerJobId = workerJobId;
    }

    public void markCompleted(String result) {
        this.status = this.status.transitionTo(JobStatus.COMPLETED);
        this.result = result;
    }

    public void markFailed(String errorMessage) {
        this.status = this.status.transitionTo(JobStatus.FAILED);
        this.errorMessage = errorMessage;
    }
}
