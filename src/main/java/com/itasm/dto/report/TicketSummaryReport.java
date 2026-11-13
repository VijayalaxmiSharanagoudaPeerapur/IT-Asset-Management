package com.itasm.dto.report;

import java.util.Map;

public record TicketSummaryReport(
        long totalTickets,
        long openTickets,
        long inProgressTickets,
        long resolvedTickets,
        long closedTickets,
        long slaBreachedTickets,
        double slaBreachRatePercent,
        Map<String, Long> ticketsByPriority
) {
}
