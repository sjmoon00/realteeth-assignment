package com.realteeth.assignment.service;

import com.realteeth.assignment.domain.entity.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobDispatcher {

    private final JobDispatchProcessor jobDispatchProcessor;

    @Value("${dispatch.batch-size:10}")
    private int batchSize;

    @Scheduled(fixedDelay = 3000)
    public void dispatch() {
        List<Job> pendingJobs = jobDispatchProcessor.fetchPendingJobs(batchSize);
        for (Job job : pendingJobs) {
            try {
                jobDispatchProcessor.dispatchOne(job.getId());
            } catch (Exception e) {
                log.error("잡 디스패치 중 예외 발생 (jobId={}): {}", job.getJobId(), e.getMessage(), e);
            }
        }
    }
}
