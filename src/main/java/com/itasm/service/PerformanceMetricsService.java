package com.itasm.service;

import com.itasm.dto.dashboard.EndpointMetric;
import com.itasm.dto.dashboard.PerformanceMetricsResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PerformanceMetricsService {

    private final Instant startTime = Instant.now();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalDurationNanos = new AtomicLong(0);

    private final ConcurrentMap<String, EndpointTracker> endpointStats = new ConcurrentHashMap<>();

    public void recordRequest(String method, String endpoint, int statusCode, long durationNanos) {
        totalRequests.incrementAndGet();
        totalDurationNanos.addAndGet(durationNanos);

        if (statusCode >= 400) {
            totalErrors.incrementAndGet();
        }

        String key = method + " " + normalizeEndpoint(endpoint);
        endpointStats.computeIfAbsent(key, k -> new EndpointTracker(method, normalizeEndpoint(endpoint)))
                .record(durationNanos, statusCode >= 400);
    }

    public PerformanceMetricsResponse getPerformanceMetrics() {
        long requests = totalRequests.get();
        long errors = totalErrors.get();
        long nanos = totalDurationNanos.get();

        double avgLatencyMs = requests > 0 ? (nanos / (double) requests / 1_000_000.0) : 0.0;
        avgLatencyMs = Math.round(avgLatencyMs * 100.0) / 100.0;

        double errorRate = requests > 0 ? ((double) errors * 100.0 / requests) : 0.0;
        errorRate = Math.round(errorRate * 100.0) / 100.0;

        Duration uptime = Duration.between(startTime, Instant.now());
        long uptimeSecs = uptime.getSeconds();
        long hours = uptimeSecs / 3600;
        long mins = (uptimeSecs % 3600) / 60;
        long secs = uptimeSecs % 60;
        String formattedUptime = String.format("%dh %02dm %02ds", hours, mins, secs);

        Runtime rt = Runtime.getRuntime();
        long usedMem = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMem = rt.maxMemory() / (1024 * 1024);
        int threads = Thread.activeCount();

        List<EndpointMetric> top = endpointStats.values().stream()
                .map(EndpointTracker::toMetric)
                .sorted(Comparator.comparingLong(EndpointMetric::requests).reversed())
                .limit(10)
                .toList();

        return new PerformanceMetricsResponse(
                requests,
                errors,
                errorRate,
                avgLatencyMs,
                "<200ms", // Clearly documented Target capacity
                "99.9%",  // Clearly documented Target SLA
                uptimeSecs,
                formattedUptime,
                usedMem,
                maxMem,
                threads,
                top
        );
    }

    private String normalizeEndpoint(String path) {
        if (path == null) return "/";
        return path.replaceAll("/\\d+", "/{id}");
    }

    private static class EndpointTracker {
        private final String method;
        private final String endpoint;
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalNanos = new AtomicLong(0);
        private final AtomicLong errors = new AtomicLong(0);
        private final AtomicLong maxNanos = new AtomicLong(0);

        public EndpointTracker(String method, String endpoint) {
            this.method = method;
            this.endpoint = endpoint;
        }

        public void record(long durationNanos, boolean isError) {
            count.incrementAndGet();
            totalNanos.addAndGet(durationNanos);
            if (isError) {
                errors.incrementAndGet();
            }
            maxNanos.accumulateAndGet(durationNanos, Math::max);
        }

        public EndpointMetric toMetric() {
            long c = count.get();
            double avg = c > 0 ? (totalNanos.get() / (double) c / 1_000_000.0) : 0.0;
            avg = Math.round(avg * 100.0) / 100.0;
            long maxMs = maxNanos.get() / 1_000_000;
            return new EndpointMetric(method, endpoint, c, avg, maxMs, errors.get());
        }
    }
}
