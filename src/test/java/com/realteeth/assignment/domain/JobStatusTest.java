package com.realteeth.assignment.domain;

import com.realteeth.assignment.domain.enums.JobStatus;
import com.realteeth.assignment.exception.ErrorCode;
import com.realteeth.assignment.exception.JobException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobStatusTest {

    @Test
    void PENDING은_PROCESSING으로_전이된다() {
        assertThat(JobStatus.PENDING.transitionTo(JobStatus.PROCESSING))
                .isEqualTo(JobStatus.PROCESSING);
    }

    @Test
    void PROCESSING은_COMPLETED로_전이된다() {
        assertThat(JobStatus.PROCESSING.transitionTo(JobStatus.COMPLETED))
                .isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void PROCESSING은_FAILED로_전이된다() {
        assertThat(JobStatus.PROCESSING.transitionTo(JobStatus.FAILED))
                .isEqualTo(JobStatus.FAILED);
    }

    @Test
    void PENDING에서_COMPLETED로_전이하면_예외가_발생한다() {
        assertThatThrownBy(() -> JobStatus.PENDING.transitionTo(JobStatus.COMPLETED))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void PENDING에서_FAILED로_전이하면_예외가_발생한다() {
        assertThatThrownBy(() -> JobStatus.PENDING.transitionTo(JobStatus.FAILED))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"PENDING", "PROCESSING", "FAILED"})
    void COMPLETED에서_다른_상태로_전이하면_예외가_발생한다(JobStatus next) {
        assertThatThrownBy(() -> JobStatus.COMPLETED.transitionTo(next))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"PENDING", "PROCESSING", "COMPLETED"})
    void FAILED에서_다른_상태로_전이하면_예외가_발생한다(JobStatus next) {
        assertThatThrownBy(() -> JobStatus.FAILED.transitionTo(next))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }
}
