package com.itasm.dto.asset;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkRetireRequest(
    @NotEmpty(message = "At least one asset ID must be selected")
    List<Long> assetIds,

    String reason
) {
}
