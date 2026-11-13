package com.itasm.dto.report;

import java.time.Instant;

public record ManagementReportResponse(
        AssetSummaryReport assetSummary,
        TicketSummaryReport ticketSummary,
        SoftwareSummaryReport softwareSummary,
        WarrantySummaryReport warrantySummary,
        RiskSummaryReport riskSummary,
        Instant generatedAt
) {
}
