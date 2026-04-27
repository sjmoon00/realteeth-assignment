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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobDispatchProcessorTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private MockWorkerClient mockWorkerClient;

    @InjectMocks
    private JobDispatchProcessor jobDispatchProcessor;

    private static final String IMAGE_URL = "https://example.com/image.jpg";

    @Test
    void fetchPendingJobs는_리포지토리에_limit을_전달하고_결과를_반환한다() {
        Job job = Job.create(IMAGE_URL, "hash");
        given(jobRepository.findPendingJobsForUpdate(5)).willReturn(List.of(job));

        List<Job> result = jobDispatchProcessor.fetchPendingJobs(5);

        assertThat(result).containsExactly(job);
        verify(jobRepository).findPendingJobsForUpdate(5);
    }

    @Test
    void submitJob_성공_시_잡을_PROCESSING으로_전이하고_workerJobId를_설정한다() {
        Job job = Job.create(IMAGE_URL, "hash");
        given(jobRepository.findById(job.getId())).willReturn(Optional.of(job));
        given(mockWorkerClient.submitJob(IMAGE_URL))
                .willReturn(new MockWorkerClient.ProcessStartResponse("worker-001", "PROCESSING"));

        jobDispatchProcessor.dispatchOne(job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(job.getWorkerJobId()).isEqualTo("worker-001");
    }

    @Test
    void MockWorkerException_발생_시_잡을_FAILED로_전이하고_errorMessage를_설정한다() {
        Job job = Job.create(IMAGE_URL, "hash");
        given(jobRepository.findById(job.getId())).willReturn(Optional.of(job));
        given(mockWorkerClient.submitJob(IMAGE_URL))
                .willThrow(new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR));

        jobDispatchProcessor.dispatchOne(job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isNotNull();
    }

    @Test
    void 잡이_존재하지_않으면_JOB_NOT_FOUND_예외를_던진다() {
        given(jobRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> jobDispatchProcessor.dispatchOne(99L))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).getErrorCode())
                        .isEqualTo(ErrorCode.JOB_NOT_FOUND));
    }

    @Test
    void 잡이_이미_PROCESSING_상태이면_submitJob을_호출하지_않는다() {
        Job job = Job.create(IMAGE_URL, "hash");
        job.markProcessing("worker-existing");
        given(jobRepository.findById(job.getId())).willReturn(Optional.of(job));

        jobDispatchProcessor.dispatchOne(job.getId());

        verify(mockWorkerClient, never()).submitJob(any());
        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);
    }

    @Test
    void 잡이_이미_COMPLETED_상태이면_submitJob을_호출하지_않는다() {
        Job job = Job.create(IMAGE_URL, "hash");
        job.markProcessing("worker-existing");
        job.markCompleted("{\"score\":0.9}");
        given(jobRepository.findById(job.getId())).willReturn(Optional.of(job));

        jobDispatchProcessor.dispatchOne(job.getId());

        verify(mockWorkerClient, never()).submitJob(any());
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }
}
