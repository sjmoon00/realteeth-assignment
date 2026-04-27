package com.realteeth.assignment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "이미지 처리 요청")
public record JobSubmitRequest(
        @Schema(description = "처리할 이미지 URL", example = "https://example.com/image.jpg")
        @NotBlank String imageUrl
) {}
