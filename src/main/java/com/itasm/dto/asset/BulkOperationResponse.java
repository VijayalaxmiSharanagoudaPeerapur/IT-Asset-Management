package com.itasm.dto.asset;

import java.util.List;

public record BulkOperationResponse(
    int totalRequested,
    int successCount,
    int failureCount,
    boolean success,
    String message,
    List<AssetResponse> updatedAssets,
    List<String> errors
) {
    public static BulkOperationResponse ok(String message, List<AssetResponse> assets) {
        int count = assets != null ? assets.size() : 0;
        return new BulkOperationResponse(count, count, 0, true, message, assets != null ? assets : List.of(), List.of());
    }

    public static BulkOperationResponse failed(int total, String message, List<String> errors) {
        int errCount = errors != null ? errors.size() : 0;
        return new BulkOperationResponse(total, 0, errCount, false, message, List.of(), errors != null ? errors : List.of());
    }
}
