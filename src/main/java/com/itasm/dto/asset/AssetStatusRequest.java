package com.itasm.dto.asset;

import com.itasm.domain.AssetStatus;
import jakarta.validation.constraints.NotNull;

public record AssetStatusRequest(@NotNull AssetStatus status, String notes) {
}
