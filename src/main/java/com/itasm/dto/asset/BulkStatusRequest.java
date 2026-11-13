package com.itasm.dto.asset;

import com.itasm.domain.AssetStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BulkStatusRequest(
    @NotEmpty(message = "At least one asset ID must be selected")
    List<Long> assetIds,

    @NotNull(message = "Status is required")
    AssetStatus status,

    String notes
) {
}
