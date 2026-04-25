package com.realteeth.assignment.api.controller;

import com.realteeth.assignment.api.dto.JobResponse;
import com.realteeth.assignment.api.dto.JobSubmitRequest;
import com.realteeth.assignment.domain.enums.JobStatus;
import com.realteeth.assignment.service.JobService;
import com.realteeth.assignment.service.JobSubmitResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<JobResponse> submit(@Valid @RequestBody JobSubmitRequest request) {
        JobSubmitResult result = jobService.submitJob(request.imageUrl());
        JobResponse response = JobResponse.from(result.job());
        if (result.created()) {
            return ResponseEntity.status(201).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(JobResponse.from(jobService.getJob(jobId)));
    }

    @GetMapping
    public ResponseEntity<Page<JobResponse>> getJobs(
            @RequestParam(required = false) JobStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<JobResponse> page = jobService.getJobs(status, pageable).map(JobResponse::from);
        return ResponseEntity.ok(page);
    }
}
