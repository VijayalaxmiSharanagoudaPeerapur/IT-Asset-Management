package com.itasm.dto.asset;

import java.util.List;

public record BulkExportRequest(
    List<Long> assetIds
) {
}
