package com.realteeth.assignment.domain.enums;

import com.realteeth.assignment.exception.ErrorCode;
import com.realteeth.assignment.exception.JobException;

import java.util.Set;

public enum JobStatus {

    PENDING {
        @Override
        public Set<JobStatus> allowedNextStates() {
            return Set.of(PROCESSING, FAILED);
        }
    },
    PROCESSING {
        @Override
        public Set<JobStatus> allowedNextStates() {
            return Set.of(COMPLETED, FAILED);
        }
    },
    COMPLETED {
        @Override
        public Set<JobStatus> allowedNextStates() {
            return Set.of();
        }
    },
    FAILED {
        @Override
        public Set<JobStatus> allowedNextStates() {
            return Set.of();
        }
    };

    public abstract Set<JobStatus> allowedNextStates();

    public JobStatus transitionTo(JobStatus next) {
        if (!allowedNextStates().contains(next)) {
            throw new JobException(ErrorCode.INVALID_STATE_TRANSITION);
        }
        return next;
    }
}
