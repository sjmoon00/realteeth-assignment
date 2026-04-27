package com.realteeth.assignment.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "오류 응답")
public record ErrorResponse(
        @Schema(description = "오류 코드", example = "JOB_NOT_FOUND") String code,
        @Schema(description = "오류 메시지", example = "잡을 찾을 수 없습니다.") String message
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }
}
