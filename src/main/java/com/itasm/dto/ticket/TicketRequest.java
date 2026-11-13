package com.itasm.dto.ticket;

import com.itasm.domain.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TicketRequest(
        @NotBlank String title,
        @NotBlank String description,
        @NotNull TicketPriority priority,
        Long assetId,
        Long reportedByEmployeeId
) {
}
