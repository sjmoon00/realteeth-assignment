package com.realteeth.assignment.service;

import com.realteeth.assignment.domain.entity.Job;
import com.realteeth.assignment.domain.enums.JobStatus;
import com.realteeth.assignment.domain.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobRecoveryService {

    private final JobRepository jobRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    @Transactional
    public void recover() {
        List<Job> processingJobs = jobRepository.findByStatus(JobStatus.PROCESSING);

        for (Job job : processingJobs) {
            job.resetToPending();
        }

        if (!processingJobs.isEmpty()) {
            List<String> jobIds = processingJobs.stream()
                    .map(job -> job.getJobId().toString())
                    .toList();
            log.info("서버 재시작 복구: PROCESSING 잡 {}건을 PENDING으로 리셋 {}", processingJobs.size(), jobIds);
        }
    }
}
