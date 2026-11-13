package com.itasm.dto.report;

import java.math.BigDecimal;
import java.util.Map;

public record AssetSummaryReport(
        long totalAssets,
        long pricedAssetsCount,
        long unpricedAssetsCount,
        boolean valuationAvailable,
        BigDecimal totalValuation,
        BigDecimal averageValue,
        Map<String, Long> assetsByStatus,
        Map<String, Long> assetsByType,
        Map<String, Long> assetsByDepartment
) {
}
