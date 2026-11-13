package com.itasm.dto.report;

public record HighRiskAssetSummary(
        Long id,
        String assetTag,
        String model,
        String status,
        int riskScore,
        String assignedEmployee
) {
}
