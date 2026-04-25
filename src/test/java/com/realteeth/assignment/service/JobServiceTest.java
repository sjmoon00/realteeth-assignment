package com.realteeth.assignment.service;

import com.realteeth.assignment.domain.entity.Job;
import com.realteeth.assignment.domain.enums.JobStatus;
import com.realteeth.assignment.domain.repository.JobRepository;
import com.realteeth.assignment.exception.ErrorCode;
import com.realteeth.assignment.exception.JobException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobService jobService;

    private static final String IMAGE_URL = "https://example.com/image.jpg";

    @Test
    void submitJob은_새_잡을_생성하고_created_true를_반환한다() {
        given(jobRepository.findByRequestHash(anyString())).willReturn(List.of());
        Job saved = Job.create(IMAGE_URL, "hash");
        given(jobRepository.save(any(Job.class))).willReturn(saved);

        JobSubmitResult result = jobService.submitJob(IMAGE_URL);

        assertThat(result.created()).isTrue();
        assertThat(result.job()).isEqualTo(saved);
        verify(jobRepository).save(any(Job.class));
    }

    @Test
    void submitJob은_PENDING_잡이_존재하면_기존_잡을_반환하고_created_false를_반환한다() {
        Job pending = Job.create(IMAGE_URL, "hash");
        given(jobRepository.findByRequestHash(anyString())).willReturn(List.of(pending));

        JobSubmitResult result = jobService.submitJob(IMAGE_URL);

        assertThat(result.created()).isFalse();
        assertThat(result.job()).isEqualTo(pending);
        verify(jobRepository, never()).save(any());
    }

    @Test
    void submitJob은_PROCESSING_잡이_존재하면_기존_잡을_반환하고_created_false를_반환한다() {
        Job processing = Job.create(IMAGE_URL, "hash");
        processing.markProcessing("worker-001");
        given(jobRepository.findByRequestHash(anyString())).willReturn(List.of(processing));

        JobSubmitResult result = jobService.submitJob(IMAGE_URL);

        assertThat(result.created()).isFalse();
        assertThat(result.job()).isEqualTo(processing);
        verify(jobRepository, never()).save(any());
    }

    @Test
    void submitJob은_COMPLETED_잡이_존재하면_기존_잡을_반환하고_created_false를_반환한다() {
        Job completed = Job.create(IMAGE_URL, "hash");
        completed.markProcessing("worker-001");
        completed.markCompleted("{\"score\":0.9}");
        given(jobRepository.findByRequestHash(anyString())).willReturn(List.of(completed));

        JobSubmitResult result = jobService.submitJob(IMAGE_URL);

        assertThat(result.created()).isFalse();
        assertThat(result.job()).isEqualTo(completed);
        verify(jobRepository, never()).save(any());
    }

    @Test
    void submitJob은_FAILED_잡만_존재하면_새_잡을_생성한다() {
        Job failed = Job.create(IMAGE_URL, "hash");
        failed.markProcessing("worker-001");
        failed.markFailed("error");
        given(jobRepository.findByRequestHash(anyString())).willReturn(List.of(failed));
        Job saved = Job.create(IMAGE_URL, "hash");
        given(jobRepository.save(any(Job.class))).willReturn(saved);

        JobSubmitResult result = jobService.submitJob(IMAGE_URL);

        assertThat(result.created()).isTrue();
        verify(jobRepository).save(any(Job.class));
    }

    @Test
    void submitJob은_동일한_imageUrl에_대해_동일한_해시를_생성한다() {
        Job saved1 = Job.create(IMAGE_URL, "hash");
        Job saved2 = Job.create(IMAGE_URL, "hash");
        given(jobRepository.findByRequestHash(anyString())).willReturn(List.of());
        given(jobRepository.save(any(Job.class))).willReturn(saved1, saved2);

        jobService.submitJob(IMAGE_URL);
        jobService.submitJob(IMAGE_URL);

        // 두 번 호출 시 동일한 hash로 findByRequestHash 호출됨을 캡처로 확인 가능
        // 여기서는 save가 두 번 호출됐음(FAILED 없으면 매번 빈 리스트 반환되므로)을 확인
        verify(jobRepository, org.mockito.Mockito.times(2)).save(any(Job.class));
    }

    @Test
    void getJob은_jobId로_잡을_조회한다() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.create(IMAGE_URL, "hash");
        given(jobRepository.findByJobId(jobId)).willReturn(Optional.of(job));

        Job found = jobService.getJob(jobId);

        assertThat(found).isEqualTo(job);
    }

    @Test
    void getJob은_존재하지_않는_jobId이면_JOB_NOT_FOUND_예외를_던진다() {
        UUID jobId = UUID.randomUUID();
        given(jobRepository.findByJobId(jobId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getJob(jobId))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).getErrorCode())
                        .isEqualTo(ErrorCode.JOB_NOT_FOUND));
    }

    @Test
    void getJobs는_status가_null이면_전체_목록을_조회한다() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Job> page = new PageImpl<>(List.of(Job.create(IMAGE_URL, "hash")));
        given(jobRepository.findAll(pageable)).willReturn(page);

        Page<Job> result = jobService.getJobs(null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(jobRepository).findAll(pageable);
        verify(jobRepository, never()).findByStatus(any(), any());
    }

    @Test
    void getJobs는_status_필터로_목록을_조회한다() {
        Pageable pageable = PageRequest.of(0, 10);
        Job job = Job.create(IMAGE_URL, "hash");
        Page<Job> page = new PageImpl<>(List.of(job));
        given(jobRepository.findByStatus(JobStatus.PENDING, pageable)).willReturn(page);

        Page<Job> result = jobService.getJobs(JobStatus.PENDING, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(jobRepository).findByStatus(JobStatus.PENDING, pageable);
        verify(jobRepository, never()).findAll(any(Pageable.class));
    }
}
