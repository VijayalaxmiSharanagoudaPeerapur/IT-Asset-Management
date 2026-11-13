package com.itasm.dto.notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String message,
        Long ticketId,
        boolean read,
        Instant createdAt
) {
}
