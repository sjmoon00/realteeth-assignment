package com.realteeth.assignment.exception;

public class MockWorkerException extends BaseException {

    public MockWorkerException(ErrorCode errorCode) {
        super(errorCode);
    }

    public MockWorkerException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}