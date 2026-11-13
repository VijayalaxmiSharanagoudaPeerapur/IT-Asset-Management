package com.itasm.config;

import com.itasm.service.PerformanceMetricsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
@RequiredArgsConstructor
public class PerformanceMetricsFilter extends OncePerRequestFilter {

    private final PerformanceMetricsService performanceMetricsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        // Only track API requests, skip static assets to keep metrics cleanly focused on API performance
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationNanos = System.nanoTime() - startNanos;
            performanceMetricsService.recordRequest(request.getMethod(), path, response.getStatus(), durationNanos);
        }
    }
}
