package com.realteeth.assignment.api.dto;

import com.realteeth.assignment.domain.entity.Job;
import com.realteeth.assignment.domain.enums.JobStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobResponse(
        UUID jobId,
        JobStatus status,
        String imageUrl,
        String result,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getJobId(),
                job.getStatus(),
                job.getImageUrl(),
                job.getResult(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
