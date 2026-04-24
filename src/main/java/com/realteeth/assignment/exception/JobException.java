package com.realteeth.assignment.exception;

public class JobException extends BaseException {

    public JobException(ErrorCode errorCode) {
        super(errorCode);
    }

    public JobException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
