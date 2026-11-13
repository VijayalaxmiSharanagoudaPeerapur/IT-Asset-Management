package com.itasm.dto.dashboard;

import java.util.List;

public record PerformanceMetricsResponse(
        long totalRequests,
        long totalErrors,
        double errorRatePercent,
        double measuredAvgResponseTimeMs,
        String targetResponseTime,
        String targetUptime,
        long uptimeSeconds,
        String uptimeFormatted,
        long jvmUsedMemoryMb,
        long jvmMaxMemoryMb,
        int activeThreads,
        List<EndpointMetric> topEndpoints
) {
}
