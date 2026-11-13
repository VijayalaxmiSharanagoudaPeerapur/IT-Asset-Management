package com.itasm.dto.request;

import com.itasm.domain.RequestStatus;
import jakarta.validation.constraints.NotNull;

public record ResolveAssetRequestDTO(
        @NotNull RequestStatus status,
        String adminNotes
) {}
