package com.pb.synth.tradecapture.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to add correlation ID to all requests for distributed tracing.
 * Correlation ID is added to MDC (Mapped Diagnostic Context) for logging
 * and to response headers for client tracking.
 */
@Component
@Order(1)
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String TRADE_ID_MDC_KEY = "tradeId";
    private static final String PARTITION_KEY_MDC_KEY = "partitionKey";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // Get correlation ID from request header or generate new one
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            
            // Add to MDC for logging
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            
            // Add to response header
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            // Extract trade ID and partition key from request if available
            String tradeId = extractTradeId(request);
            if (tradeId != null) {
                MDC.put(TRADE_ID_MDC_KEY, tradeId);
            }
            
            String partitionKey = extractPartitionKey(request);
            if (partitionKey != null) {
                MDC.put(PARTITION_KEY_MDC_KEY, partitionKey);
            }
            
            filterChain.doFilter(request, response);
            
        } finally {
            // Clean up MDC after request
            MDC.clear();
        }
    }

    private String extractTradeId(HttpServletRequest request) {
        // Try to extract from path variable (e.g., /api/v1/trades/capture/{tradeId})
        String path = request.getRequestURI();
        if (path.contains("/capture/")) {
            String[] parts = path.split("/capture/");
            if (parts.length > 1) {
                String tradeId = parts[1];
                // Remove query parameters if any
                if (tradeId.contains("?")) {
                    tradeId = tradeId.substring(0, tradeId.indexOf("?"));
                }
                return tradeId;
            }
        }
        return null;
    }

    private String extractPartitionKey(HttpServletRequest request) {
        // Try to extract from request header or query parameter
        String partitionKey = request.getHeader("X-Partition-Key");
        if (partitionKey == null || partitionKey.isBlank()) {
            partitionKey = request.getParameter("partitionKey");
        }
        return partitionKey;
    }
}

