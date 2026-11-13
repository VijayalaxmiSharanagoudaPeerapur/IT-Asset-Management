package com.itasm.dto.asset;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BulkAssignRequest(
    @NotEmpty(message = "At least one asset ID must be selected")
    List<Long> assetIds,

    @NotNull(message = "Employee ID is required")
    Long employeeId,

    String notes
) {
}
