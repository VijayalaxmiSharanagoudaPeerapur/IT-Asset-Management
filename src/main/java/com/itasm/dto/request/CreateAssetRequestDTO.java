package com.itasm.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateAssetRequestDTO(
        @NotBlank String requestedItem,
        String reason
) {}
