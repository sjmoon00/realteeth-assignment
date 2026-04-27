package com.realteeth.assignment.service;

import com.realteeth.assignment.domain.entity.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobPollerTest {

    @Mock
    private JobPollProcessor jobPollProcessor;

    @InjectMocks
    private JobPoller jobPoller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jobPoller, "batchSize", 10);
    }

    private Job createJobWithId(Long id) {
        Job job = Job.create("https://example.com/image.jpg", "hash");
        ReflectionTestUtils.setField(job, "id", id);
        job.markProcessing("worker-" + id);
        return job;
    }

    @Test
    void PROCESSING_잡이_있으면_각각_pollOne을_호출한다() {
        Job job1 = createJobWithId(1L);
        Job job2 = createJobWithId(2L);
        given(jobPollProcessor.fetchProcessingJobs(10)).willReturn(List.of(job1, job2));

        jobPoller.poll();

        verify(jobPollProcessor).pollOne(1L);
        verify(jobPollProcessor).pollOne(2L);
    }

    @Test
    void PROCESSING_잡이_없으면_pollOne을_호출하지_않는다() {
        given(jobPollProcessor.fetchProcessingJobs(anyInt())).willReturn(List.of());

        jobPoller.poll();

        verify(jobPollProcessor, never()).pollOne(any());
    }

    @Test
    void pollOne에서_예외가_발생해도_다음_잡_처리를_계속한다() {
        Job job1 = createJobWithId(1L);
        Job job2 = createJobWithId(2L);
        given(jobPollProcessor.fetchProcessingJobs(10)).willReturn(List.of(job1, job2));
        doThrow(new RuntimeException("폴링 실패")).when(jobPollProcessor).pollOne(1L);

        jobPoller.poll();

        verify(jobPollProcessor).pollOne(1L);
        verify(jobPollProcessor).pollOne(2L);
    }
}
