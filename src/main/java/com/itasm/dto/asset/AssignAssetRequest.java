package com.itasm.dto.asset;

import jakarta.validation.constraints.NotNull;

public record AssignAssetRequest(@NotNull Long employeeId, String notes) {
}
