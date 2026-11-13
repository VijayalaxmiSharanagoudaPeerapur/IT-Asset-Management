package com.itasm.dto.report;

public record SoftwareLicenseUtilization(
        String name,
        int totalSeats,
        int allocatedSeats,
        int availableSeats,
        double utilizationPercent,
        String expirationDate
) {
}
