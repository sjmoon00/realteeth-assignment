package com.realteeth.assignment.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Job
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "잡을 찾을 수 없습니다."),
    INVALID_JOB_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 상태 전이입니다."),

    // MockWorker
    MOCK_WORKER_ERROR(HttpStatus.BAD_GATEWAY, "외부 처리 서비스 오류가 발생했습니다."),

    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatusCode status;
    private final String message;
}
