package com.realteeth.assignment.exception;

import lombok.Getter;

import java.util.Objects;

@Getter
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BaseException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").getMessage());
        this.errorCode = errorCode;
    }

    protected BaseException(ErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").getMessage(), cause);
        this.errorCode = errorCode;
    }
}
