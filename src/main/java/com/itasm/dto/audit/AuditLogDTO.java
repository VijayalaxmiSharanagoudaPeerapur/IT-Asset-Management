package com.itasm.dto.audit;

import java.time.Instant;

public record AuditLogDTO(
        Long id,
        String entityType,
        Long entityId,
        String action,
        String oldValue,
        String newValue,
        AuditUserDTO performedBy,
        Instant timestamp
) {}
