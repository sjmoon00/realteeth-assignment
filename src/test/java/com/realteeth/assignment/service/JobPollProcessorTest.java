package com.realteeth.assignment.service;

import com.realteeth.assignment.domain.entity.Job;
import com.realteeth.assignment.domain.enums.JobStatus;
import com.realteeth.assignment.domain.repository.JobRepository;
import com.realteeth.assignment.exception.ErrorCode;
import com.realteeth.assignment.exception.JobException;
import com.realteeth.assignment.exception.MockWorkerException;
import com.realteeth.assignment.worker.MockWorkerClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobPollProcessorTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private MockWorkerClient mockWorkerClient;

    @InjectMocks
    private JobPollProcessor jobPollProcessor;

    private static final String IMAGE_URL = "https://example.com/image.jpg";
    private static final String WORKER_JOB_ID = "worker-001";

    private Job createProcessingJobWithId(Long id) {
        Job job = Job.create(IMAGE_URL, "hash");
        ReflectionTestUtils.setField(job, "id", id);
        job.markProcessing(WORKER_JOB_ID);
        return job;
    }

    @Test
    void fetchProcessingJobs는_리포지토리에_limit을_전달하고_결과를_반환한다() {
        Job job = createProcessingJobWithId(1L);
        given(jobRepository.findProcessingJobsForUpdate(5)).willReturn(List.of(job));

        List<Job> result = jobPollProcessor.fetchProcessingJobs(5);

        assertThat(result).containsExactly(job);
        verify(jobRepository).findProcessingJobsForUpdate(5);
    }

    @Test
    void Worker가_COMPLETED를_반환하면_잡을_COMPLETED로_전이하고_result를_설정한다() {
        Job job = createProcessingJobWithId(1L);
        given(jobRepository.findById(1L)).willReturn(Optional.of(job));
        given(mockWorkerClient.getJobStatus(WORKER_JOB_ID))
                .willReturn(new MockWorkerClient.ProcessStatusResponse(WORKER_JOB_ID, "COMPLETED", "{\"score\":0.9}"));

        jobPollProcessor.pollOne(1L);

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getResult()).isEqualTo("{\"score\":0.9}");
    }

    @Test
    void Worker가_FAILED를_반환하면_잡을_FAILED로_전이하고_errorMessage를_설정한다() {
        Job job = createProcessingJobWithId(1L);
        given(jobRepository.findById(1L)).willReturn(Optional.of(job));
        given(mockWorkerClient.getJobStatus(WORKER_JOB_ID))
                .willReturn(new MockWorkerClient.ProcessStatusResponse(WORKER_JOB_ID, "FAILED", null));

        jobPollProcessor.pollOne(1L);

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isNotNull();
    }

    @Test
    void Worker가_PROCESSING을_반환하면_잡_상태를_변경하지_않는다() {
        Job job = createProcessingJobWithId(1L);
        given(jobRepository.findById(1L)).willReturn(Optional.of(job));
        given(mockWorkerClient.getJobStatus(WORKER_JOB_ID))
                .willReturn(new MockWorkerClient.ProcessStatusResponse(WORKER_JOB_ID, "PROCESSING", null));

        jobPollProcessor.pollOne(1L);

        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);
    }

    @Test
    void MockWorkerException_발생_시_잡_상태를_PROCESSING으로_유지한다() {
        Job job = createProcessingJobWithId(1L);
        given(jobRepository.findById(1L)).willReturn(Optional.of(job));
        given(mockWorkerClient.getJobStatus(WORKER_JOB_ID))
                .willThrow(new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR));

        jobPollProcessor.pollOne(1L);

        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);
    }

    @Test
    void 잡이_존재하지_않으면_JOB_NOT_FOUND_예외를_던진다() {
        given(jobRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> jobPollProcessor.pollOne(99L))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).getErrorCode())
                        .isEqualTo(ErrorCode.JOB_NOT_FOUND));
    }

    @Test
    void 잡이_이미_COMPLETED_상태이면_getJobStatus를_호출하지_않는다() {
        Job job = createProcessingJobWithId(1L);
        job.markCompleted("{\"score\":0.9}");
        given(jobRepository.findById(1L)).willReturn(Optional.of(job));

        jobPollProcessor.pollOne(1L);

        verify(mockWorkerClient, never()).getJobStatus(any());
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void 잡이_이미_FAILED_상태이면_getJobStatus를_호출하지_않는다() {
        Job job = Job.create(IMAGE_URL, "hash");
        ReflectionTestUtils.setField(job, "id", 1L);
        job.markFailed("이전 실패");
        given(jobRepository.findById(1L)).willReturn(Optional.of(job));

        jobPollProcessor.pollOne(1L);

        verify(mockWorkerClient, never()).getJobStatus(any());
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
    }
}
