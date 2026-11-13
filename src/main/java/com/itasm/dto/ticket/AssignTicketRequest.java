package com.itasm.dto.ticket;

import jakarta.validation.constraints.NotNull;

public record AssignTicketRequest(@NotNull Long employeeId) {
}
