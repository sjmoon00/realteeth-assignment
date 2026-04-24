package com.realteeth.assignment.domain;

import com.realteeth.assignment.domain.entity.Job;
import com.realteeth.assignment.domain.enums.JobStatus;
import com.realteeth.assignment.domain.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;


import java.util.List;
import java.util.Optional;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import com.realteeth.assignment.MySQLTestConfig;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(MySQLTestConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobRepositoryTest {

    @Autowired
    private JobRepository jobRepository;

    @Test
    void findByJobId는_저장된_잡을_반환한다() {
        Job job = jobRepository.save(Job.create("https://example.com/img.jpg", "hash001"));

        Optional<Job> found = jobRepository.findByJobId(job.getJobId());

        assertThat(found).isPresent();
        assertThat(found.get().getJobId()).isEqualTo(job.getJobId());
    }

    @Test
    void findByJobId는_존재하지_않으면_빈_Optional을_반환한다() {
        Optional<Job> found = jobRepository.findByJobId(java.util.UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void findByRequestHash는_같은_해시의_잡_목록을_반환한다() {
        jobRepository.save(Job.create("https://example.com/img.jpg", "hash002"));
        jobRepository.save(Job.create("https://example.com/img.jpg", "hash002"));

        List<Job> found = jobRepository.findByRequestHash("hash002");

        assertThat(found).hasSize(2);
    }

    @Test
    void findByStatus는_해당_상태의_잡만_반환한다() {
        jobRepository.save(Job.create("https://example.com/img1.jpg", "hash003"));
        Job processing = jobRepository.save(Job.create("https://example.com/img2.jpg", "hash004"));
        processing.markProcessing("worker-001");
        jobRepository.save(processing);

        List<Job> pending = jobRepository.findByStatus(JobStatus.PENDING);
        List<Job> processingJobs = jobRepository.findByStatus(JobStatus.PROCESSING);

        assertThat(pending).hasSize(1);
        assertThat(processingJobs).hasSize(1);
    }

    @Test
    void findPendingJobsForUpdate는_PENDING_잡을_limit만큼_반환한다() {
        jobRepository.save(Job.create("https://example.com/img1.jpg", "hash005"));
        jobRepository.save(Job.create("https://example.com/img2.jpg", "hash006"));
        jobRepository.save(Job.create("https://example.com/img3.jpg", "hash007"));

        List<Job> result = jobRepository.findPendingJobsForUpdate(2);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(j -> j.getStatus() == JobStatus.PENDING);
    }

    @Test
    void findPendingJobsForUpdate는_PROCESSING_잡을_포함하지_않는다() {
        Job pending = jobRepository.save(Job.create("https://example.com/img1.jpg", "hash008"));
        Job processing = jobRepository.save(Job.create("https://example.com/img2.jpg", "hash009"));
        processing.markProcessing("worker-002");
        jobRepository.save(processing);

        List<Job> result = jobRepository.findPendingJobsForUpdate(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getJobId()).isEqualTo(pending.getJobId());
    }
}
