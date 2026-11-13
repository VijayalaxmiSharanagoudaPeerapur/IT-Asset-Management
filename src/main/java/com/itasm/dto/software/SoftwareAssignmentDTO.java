package com.itasm.dto.software;

import java.time.Instant;

public record SoftwareAssignmentDTO(
        Long id,
        Long licenseId,
        String licenseName,
        Long employeeId,
        String employeeName,
        Instant assignedAt
) {}
