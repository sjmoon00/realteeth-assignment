package com.realteeth.assignment.service;

import com.realteeth.assignment.domain.entity.Job;
import com.realteeth.assignment.domain.enums.JobStatus;
import com.realteeth.assignment.domain.repository.JobRepository;
import com.realteeth.assignment.exception.ErrorCode;
import com.realteeth.assignment.exception.JobException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobService {

    private final JobRepository jobRepository;

    @Transactional
    public JobSubmitResult submitJob(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new JobException(ErrorCode.INVALID_JOB_REQUEST);
        }
        String hash = calculateHash(imageUrl);

        List<Job> existing = jobRepository.findByRequestHash(hash);
        Optional<Job> activeJob = existing.stream()
                .filter(j -> j.getStatus() != JobStatus.FAILED)
                .findFirst();
        if (activeJob.isPresent()) {
            return new JobSubmitResult(activeJob.get(), false);
        }

        Job job = jobRepository.save(Job.create(imageUrl, hash));
        return new JobSubmitResult(job, true);
    }

    public Job getJob(UUID jobId) {
        return jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new JobException(ErrorCode.JOB_NOT_FOUND));
    }

    public Page<Job> getJobs(JobStatus status, Pageable pageable) {
        if (status != null) {
            return jobRepository.findByStatus(status, pageable);
        }
        return jobRepository.findAll(pageable);
    }

    private String calculateHash(String imageUrl) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(imageUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }
}
