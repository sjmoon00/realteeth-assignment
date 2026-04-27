package com.realteeth.assignment.api.dto;

import com.realteeth.assignment.domain.entity.Job;
import com.realteeth.assignment.domain.enums.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "잡 처리 결과")
public record JobResponse(
        @Schema(description = "잡 ID") UUID jobId,
        @Schema(description = "처리 상태") JobStatus status,
        @Schema(description = "요청 이미지 URL") String imageUrl,
        @Schema(description = "처리 결과 (COMPLETED 시 채워짐)", nullable = true) String result,
        @Schema(description = "오류 메시지 (FAILED 시 채워짐)", nullable = true) String errorMessage,
        @Schema(description = "잡 생성 시각") LocalDateTime createdAt,
        @Schema(description = "잡 최종 수정 시각") LocalDateTime updatedAt
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
