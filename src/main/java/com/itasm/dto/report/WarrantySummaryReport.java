package com.itasm.dto.report;

public record WarrantySummaryReport(
        long expiredCount,
        long expiringIn30Days,
        long expiringIn90Days,
        long activeWarrantyCount
) {
}
