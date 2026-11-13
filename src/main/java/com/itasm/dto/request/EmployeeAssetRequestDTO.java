package com.itasm.dto.request;

import com.itasm.domain.RequestStatus;
import java.time.Instant;

public record EmployeeAssetRequestDTO(
        Long id,
        Long employeeId,
        String employeeName,
        String requestedItem,
        String reason,
        RequestStatus status,
        String adminNotes,
        Instant createdAt,
        Instant resolvedAt
) {}
