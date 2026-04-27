package com.realteeth.assignment.api.controller;

import com.realteeth.assignment.api.dto.JobResponse;
import com.realteeth.assignment.api.dto.JobSubmitRequest;
import com.realteeth.assignment.domain.enums.JobStatus;
import com.realteeth.assignment.exception.ErrorResponse;
import com.realteeth.assignment.service.JobService;
import com.realteeth.assignment.service.JobSubmitResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Job", description = "이미지 처리 잡 API")
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @Operation(
            summary = "이미지 처리 요청",
            description = "이미지 URL을 제출하여 처리 잡을 생성합니다. " +
                    "동일 URL의 PENDING/PROCESSING/COMPLETED 잡이 이미 존재하면 기존 잡을 반환합니다(멱등). " +
                    "FAILED 상태인 경우에만 새 잡이 생성됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "새 잡 생성됨"),
            @ApiResponse(responseCode = "200", description = "중복 요청 — 기존 잡 반환"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<JobResponse> submit(@Valid @RequestBody JobSubmitRequest request) {
        JobSubmitResult result = jobService.submitJob(request.imageUrl());
        JobResponse response = JobResponse.from(result.job());
        if (result.created()) {
            return ResponseEntity.status(201).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "잡 단건 조회", description = "잡 ID로 처리 상태 및 결과를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "잡을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJob(
            @Parameter(description = "조회할 잡 ID") @PathVariable UUID jobId
    ) {
        return ResponseEntity.ok(JobResponse.from(jobService.getJob(jobId)));
    }

    @Operation(
            summary = "잡 목록 조회",
            description = "잡 목록을 페이지 단위로 조회합니다. status 파라미터로 특정 상태만 필터링할 수 있습니다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<Page<JobResponse>> getJobs(
            @Parameter(description = "필터링할 잡 상태 (미입력 시 전체 조회)", schema = @Schema(implementation = JobStatus.class))
            @RequestParam(required = false) JobStatus status,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<JobResponse> page = jobService.getJobs(status, pageable).map(JobResponse::from);
        return ResponseEntity.ok(page);
    }
}
