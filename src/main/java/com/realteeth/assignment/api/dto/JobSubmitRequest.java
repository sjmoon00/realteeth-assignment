package com.realteeth.assignment.api.dto;

import jakarta.validation.constraints.NotBlank;

public record JobSubmitRequest(@NotBlank String imageUrl) {}
