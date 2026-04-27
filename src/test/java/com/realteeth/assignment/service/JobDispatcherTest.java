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
class JobDispatcherTest {

    @Mock
    private JobDispatchProcessor jobDispatchProcessor;

    @InjectMocks
    private JobDispatcher jobDispatcher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jobDispatcher, "batchSize", 10);
    }

    @Test
    void PENDING_잡이_있으면_각각_dispatchOne을_호출한다() {
        Job job1 = Job.create("https://example.com/a.jpg", "hash1");
        Job job2 = Job.create("https://example.com/b.jpg", "hash2");
        given(jobDispatchProcessor.fetchPendingJobs(10)).willReturn(List.of(job1, job2));

        jobDispatcher.dispatch();

        verify(jobDispatchProcessor).dispatchOne(job1.getId());
        verify(jobDispatchProcessor).dispatchOne(job2.getId());
    }

    @Test
    void PENDING_잡이_없으면_dispatchOne을_호출하지_않는다() {
        given(jobDispatchProcessor.fetchPendingJobs(anyInt())).willReturn(List.of());

        jobDispatcher.dispatch();

        verify(jobDispatchProcessor, never()).dispatchOne(any());
    }

    @Test
    void dispatchOne에서_예외가_발생해도_다음_잡_처리를_계속한다() {
        Job job1 = Job.create("https://example.com/a.jpg", "hash1");
        Job job2 = Job.create("https://example.com/b.jpg", "hash2");
        given(jobDispatchProcessor.fetchPendingJobs(10)).willReturn(List.of(job1, job2));
        doThrow(new RuntimeException("디스패치 실패")).when(jobDispatchProcessor).dispatchOne(job1.getId());

        jobDispatcher.dispatch();

        verify(jobDispatchProcessor).dispatchOne(job1.getId());
        verify(jobDispatchProcessor).dispatchOne(job2.getId());
    }
}
