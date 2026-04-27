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
public class JobPoller {

    private final JobPollProcessor jobPollProcessor;

    @Value("${polling.batch-size:10}")
    private int batchSize;

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        List<Job> processingJobs = jobPollProcessor.fetchProcessingJobs(batchSize);
        for (Job job : processingJobs) {
            try {
                jobPollProcessor.pollOne(job.getId());
            } catch (Exception e) {
                log.error("잡 폴링 중 예외 발생 (id={}, jobId={}): {}", job.getId(), job.getJobId(), e.getMessage(), e);
            }
        }
    }
}
