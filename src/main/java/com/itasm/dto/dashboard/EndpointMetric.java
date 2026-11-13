package com.itasm.dto.dashboard;

public record EndpointMetric(
        String method,
        String endpoint,
        long requests,
        double avgResponseTimeMs,
        long maxResponseTimeMs,
        long errors
) {
}
