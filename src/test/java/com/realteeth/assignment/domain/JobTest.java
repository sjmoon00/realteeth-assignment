package com.realteeth.assignment.domain;

import com.realteeth.assignment.domain.entity.Job;
import com.realteeth.assignment.domain.enums.JobStatus;
import com.realteeth.assignment.exception.JobException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobTest {

    @Test
    void create는_PENDING_상태의_잡을_생성한다() {
        Job job = Job.create("https://example.com/image.jpg", "abc123hash");

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getJobId()).isNotNull();
        assertThat(job.getImageUrl()).isEqualTo("https://example.com/image.jpg");
        assertThat(job.getRequestHash()).isEqualTo("abc123hash");
        assertThat(job.getWorkerJobId()).isNull();
        assertThat(job.getResult()).isNull();
        assertThat(job.getErrorMessage()).isNull();
    }

    @Test
    void create는_호출마다_고유한_jobId를_생성한다() {
        Job job1 = Job.create("https://example.com/image.jpg", "hash1");
        Job job2 = Job.create("https://example.com/image.jpg", "hash2");

        assertThat(job1.getJobId()).isNotEqualTo(job2.getJobId());
    }

    @Test
    void markProcessing은_PROCESSING_상태로_전이하고_workerJobId를_설정한다() {
        Job job = Job.create("https://example.com/image.jpg", "abc123hash");

        job.markProcessing("worker-job-001");

        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(job.getWorkerJobId()).isEqualTo("worker-job-001");
    }

    @Test
    void markCompleted는_COMPLETED_상태로_전이하고_result를_설정한다() {
        Job job = Job.create("https://example.com/image.jpg", "abc123hash");
        job.markProcessing("worker-job-001");

        job.markCompleted("{\"score\": 0.95}");

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getResult()).isEqualTo("{\"score\": 0.95}");
    }

    @Test
    void markFailed는_FAILED_상태로_전이하고_errorMessage를_설정한다() {
        Job job = Job.create("https://example.com/image.jpg", "abc123hash");
        job.markProcessing("worker-job-001");

        job.markFailed("처리 중 오류 발생");

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("처리 중 오류 발생");
    }

    @Test
    void PENDING_상태에서_markCompleted_호출_시_예외가_발생한다() {
        Job job = Job.create("https://example.com/image.jpg", "abc123hash");

        assertThatThrownBy(() -> job.markCompleted("result"))
                .isInstanceOf(JobException.class);
    }

    @Test
    void PENDING_상태에서_markFailed는_FAILED로_전이하고_errorMessage를_설정한다() {
        Job job = Job.create("https://example.com/image.jpg", "abc123hash");

        job.markFailed("디스패치 실패");

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("디스패치 실패");
    }

    @Test
    void COMPLETED_상태에서_markProcessing_호출_시_예외가_발생한다() {
        Job job = Job.create("https://example.com/image.jpg", "abc123hash");
        job.markProcessing("worker-job-001");
        job.markCompleted("result");

        assertThatThrownBy(() -> job.markProcessing("worker-job-002"))
                .isInstanceOf(JobException.class);
    }

    @Test
    void FAILED_상태에서_markProcessing_호출_시_예외가_발생한다() {
        Job job = Job.create("https://example.com/image.jpg", "abc123hash");
        job.markProcessing("worker-job-001");
        job.markFailed("error");

        assertThatThrownBy(() -> job.markProcessing("worker-job-002"))
                .isInstanceOf(JobException.class);
    }
}
