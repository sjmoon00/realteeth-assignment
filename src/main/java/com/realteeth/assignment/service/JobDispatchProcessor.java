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
public class JobDispatchProcessor {

    private final JobRepository jobRepository;
    private final MockWorkerClient mockWorkerClient;

    @Transactional
    public List<Job> fetchPendingJobs(int limit) {
        return jobRepository.findPendingJobsForUpdate(limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchOne(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobException(ErrorCode.JOB_NOT_FOUND));

        if (job.getStatus() != JobStatus.PENDING) {
            log.info("잡이 이미 PENDING이 아님, 스킵 (id={}, status={})", id, job.getStatus());
            return;
        }

        try {
            MockWorkerClient.ProcessStartResponse response = mockWorkerClient.submitJob(job.getImageUrl());
            job.markProcessing(response.jobId());
            log.info("잡 디스패치 성공 (id={}, jobId={}, workerJobId={})", id, job.getJobId(), response.jobId());
        } catch (MockWorkerException e) {
            job.markFailed(e.getMessage());
            log.warn("잡 디스패치 실패 (id={}, jobId={}): {}", id, job.getJobId(), e.getMessage());
        }
    }
}
