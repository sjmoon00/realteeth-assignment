package com.realteeth.assignment.service;

import com.realteeth.assignment.domain.entity.Job;
import com.realteeth.assignment.domain.enums.JobStatus;
import com.realteeth.assignment.domain.repository.JobRepository;
import com.realteeth.assignment.exception.ErrorCode;
import com.realteeth.assignment.exception.JobException;
import com.realteeth.assignment.exception.MockWorkerException;
import com.realteeth.assignment.worker.MockWorkerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobPollProcessor {

    private final JobRepository jobRepository;
    private final MockWorkerClient mockWorkerClient;

    @Transactional
    public List<Job> fetchProcessingJobs(int limit) {
        return jobRepository.findProcessingJobsForUpdate(limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pollOne(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobException(ErrorCode.JOB_NOT_FOUND));

        if (job.getStatus() != JobStatus.PROCESSING) {
            log.info("잡이 이미 PROCESSING이 아님, 스킵 (id={}, status={})", id, job.getStatus());
            return;
        }

        try {
            MockWorkerClient.ProcessStatusResponse response = mockWorkerClient.getJobStatus(job.getWorkerJobId());
            switch (response.status()) {
                case "COMPLETED" -> {
                    job.markCompleted(response.result());
                    log.info("잡 COMPLETED 전이 (id={}, jobId={})", id, job.getJobId());
                }
                case "FAILED" -> {
                    String errorMessage = response.result() != null ? response.result() : "Worker processing failed";
                    job.markFailed(errorMessage);
                    log.info("잡 FAILED 전이 (id={}, jobId={}): {}", id, job.getJobId(), errorMessage);
                }
                default -> log.debug("잡 아직 PROCESSING 중 (id={}, jobId={})", id, job.getJobId());
            }
        } catch (MockWorkerException e) {
            log.warn("잡 폴링 실패, 다음 주기에 재시도 (id={}, jobId={}): {}", id, job.getJobId(), e.getMessage());
        }
    }
}
