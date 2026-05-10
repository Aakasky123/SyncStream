package com.syncstream.observability;

import java.io.IOException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiLatencyFilter extends OncePerRequestFilter {
    private final MeterRegistry registry;

    public ApiLatencyFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }
        Timer.Sample sample = Timer.start(registry);
        try {
            filterChain.doFilter(request, response);
        } finally {
            sample.stop(Timer.builder("syncstream_api_request_latency_ms")
                    .tag("status", Integer.toString(response.getStatus()))
                    .tag("method", request.getMethod())
                    .register(registry));
        }
    }
}
