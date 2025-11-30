package com.pb.synth.tradecapture.controller;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller.
 */
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final DataSource dataSource;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", ZonedDateTime.now());
        response.put("service", "pb-synth-tradecapture-svc");
        
        // Add connection pool metrics if HikariCP is available
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
            
            Map<String, Object> poolMetrics = new HashMap<>();
            poolMetrics.put("active", pool.getActiveConnections());
            poolMetrics.put("idle", pool.getIdleConnections());
            poolMetrics.put("total", pool.getTotalConnections());
            poolMetrics.put("threadsAwaitingConnection", pool.getThreadsAwaitingConnection());
            poolMetrics.put("maximumPoolSize", hikariDataSource.getMaximumPoolSize());
            poolMetrics.put("minimumIdle", hikariDataSource.getMinimumIdle());
            
            response.put("connectionPool", poolMetrics);
        }
        
        return ResponseEntity.ok(response);
    }
}

