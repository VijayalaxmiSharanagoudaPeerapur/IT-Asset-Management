package com.itasm.dto.ticket;

import com.itasm.domain.TicketPriority;
import com.itasm.domain.TicketStatus;

import java.time.Instant;

public record TicketResponse(
        Long id,
        String ticketNumber,
        String title,
        String description,
        TicketPriority priority,
        TicketStatus status,
        Long assetId,
        String assetTag,
        Long reportedById,
        String reportedByName,
        Long assignedToId,
        String assignedToName,
        String resolutionNotes,
        Instant createdAt,
        Instant resolvedAt,
        String slaStatus
) {
}
