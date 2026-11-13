package com.itasm.dto.procurement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PurchaseOrderDTO(
        Long id,
        String poNumber,
        VendorDTO vendor,
        Integer quantity,
        BigDecimal totalCost,
        LocalDate orderDate,
        LocalDate expectedDelivery,
        LocalDate receivedDate,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
