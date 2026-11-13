package com.itasm.dto.ticket;

import com.itasm.domain.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record TicketStatusRequest(@NotNull TicketStatus status, String resolutionNotes) {
}
