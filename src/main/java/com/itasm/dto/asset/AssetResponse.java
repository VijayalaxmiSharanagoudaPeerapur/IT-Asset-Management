package com.itasm.dto.asset;

import com.itasm.domain.AssetStatus;

import java.time.Instant;
import java.time.LocalDate;

public record AssetResponse(
        Long id,
        String assetTag,
        String serialNumber,
        String type,
        String brand,
        String model,
        LocalDate purchaseDate,
        LocalDate warrantyExpiry,
        java.math.BigDecimal price,
        AssetStatus status,
        Long assignedEmployeeId,
        String assignedEmployeeName,
        String notes,
        Instant createdAt,
        int riskScore
) {
}
