package com.itasm.dto.auth;

import java.time.Instant;

public record SecurityEventResponse(
        Long id,
        Instant timestamp,
        String eventType,
        String username,
        String ipAddress,
        String details
) {
}
