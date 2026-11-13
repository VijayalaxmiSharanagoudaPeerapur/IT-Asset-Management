package com.itasm.dto.report;

import java.util.List;

public record SoftwareSummaryReport(
        long totalLicenses,
        long totalSeats,
        long allocatedSeats,
        long availableSeats,
        double overallUtilizationRate,
        List<SoftwareLicenseUtilization> topLicenses
) {
}
