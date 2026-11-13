package com.itasm.dto.report;

import java.util.List;

public record RiskSummaryReport(
        long highRiskCount,
        long criticalRiskCount,
        List<HighRiskAssetSummary> topHighRiskAssets
) {
}
