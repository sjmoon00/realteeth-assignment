package com.realteeth.assignment.service;

import com.realteeth.assignment.domain.entity.Job;
import com.realteeth.assignment.domain.enums.JobStatus;
import com.realteeth.assignment.domain.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JobRecoveryServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobRecoveryService jobRecoveryService;

    private Job createProcessingJobWithId(Long id) {
        Job job = Job.create("https://example.com/image.jpg", "hash-" + id);
        ReflectionTestUtils.setField(job, "id", id);
        job.markProcessing("worker-" + id);
        return job;
    }

    @Test
    void PROCESSING_잡이_여러_건_있으면_모두_PENDING으로_복구한다() {
        Job job1 = createProcessingJobWithId(1L);
        Job job2 = createProcessingJobWithId(2L);
        given(jobRepository.findByStatus(JobStatus.PROCESSING)).willReturn(List.of(job1, job2));

        jobRecoveryService.recover();

        assertThat(job1.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job1.getWorkerJobId()).isNull();
        assertThat(job2.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job2.getWorkerJobId()).isNull();
    }

    @Test
    void PROCESSING_잡이_없으면_예외_없이_정상_완료한다() {
        given(jobRepository.findByStatus(JobStatus.PROCESSING)).willReturn(List.of());

        jobRecoveryService.recover();
    }

    @Test
    void PROCESSING_잡이_1건_있으면_정상_복구한다() {
        Job job = createProcessingJobWithId(1L);
        given(jobRepository.findByStatus(JobStatus.PROCESSING)).willReturn(List.of(job));

        jobRecoveryService.recover();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getWorkerJobId()).isNull();
    }
}
