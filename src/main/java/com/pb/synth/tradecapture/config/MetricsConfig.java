package com.pb.synth.tradecapture.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Configuration for custom metrics and monitoring.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class MetricsConfig {

    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;

    // Custom counters
    private Counter tradesProcessedCounter;
    private Counter tradesSuccessfulCounter;
    private Counter tradesFailedCounter;
    private Counter tradesDuplicateCounter;
    private Counter enrichmentSuccessCounter;
    private Counter enrichmentPartialCounter;
    private Counter enrichmentFailedCounter;
    private Counter rulesAppliedCounter;
    private Counter deadlockRetryCounter;
    private Counter deadlockRetrySuccessCounter;
    private Counter deadlockRetryExhaustedCounter;
    private Counter idempotencyCacheHitCounter;
    private Counter idempotencyCacheMissCounter;
    private Counter partitionLockAcquiredCounter;
    private Counter partitionLockTimeoutCounter;
    
    // Solace router counters
    private Counter routerMessagesRoutedCounter;
    private Counter routerRoutingFailuresCounter;
    private Counter routerPartitionsCreatedCounter;

    // Custom timers
    private Timer tradeProcessingTimer;
    private Timer enrichmentTimer;
    private Timer rulesApplicationTimer;
    private Timer partitionLockAcquisitionTimer;
    private Timer idempotencyCheckTimer;
    private Timer databaseQueryTimer;
    private Timer routerRoutingTimer;

    // Gauges for connection pool
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger idleConnections = new AtomicInteger(0);

    @PostConstruct
    public void initializeMetrics() {
        // Trade processing counters
        tradesProcessedCounter = Counter.builder("trades.processed")
                .description("Total number of trades processed")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        tradesSuccessfulCounter = Counter.builder("trades.successful")
                .description("Number of successfully processed trades")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        tradesFailedCounter = Counter.builder("trades.failed")
                .description("Number of failed trades")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        tradesDuplicateCounter = Counter.builder("trades.duplicate")
                .description("Number of duplicate trades detected")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        // Enrichment counters
        enrichmentSuccessCounter = Counter.builder("enrichment.success")
                .description("Number of successful enrichments")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        enrichmentPartialCounter = Counter.builder("enrichment.partial")
                .description("Number of partial enrichments")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        enrichmentFailedCounter = Counter.builder("enrichment.failed")
                .description("Number of failed enrichments")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        // Rules counter
        rulesAppliedCounter = Counter.builder("rules.applied")
                .description("Number of rules applied")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        // Deadlock retry counters
        deadlockRetryCounter = Counter.builder("deadlock.retry")
                .description("Number of deadlock retries")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        deadlockRetrySuccessCounter = Counter.builder("deadlock.retry.success")
                .description("Number of successful deadlock retries")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        deadlockRetryExhaustedCounter = Counter.builder("deadlock.retry.exhausted")
                .description("Number of exhausted deadlock retries")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        // Idempotency counters
        idempotencyCacheHitCounter = Counter.builder("idempotency.cache.hit")
                .description("Number of idempotency cache hits")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        idempotencyCacheMissCounter = Counter.builder("idempotency.cache.miss")
                .description("Number of idempotency cache misses")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        // Partition locking counters
        partitionLockAcquiredCounter = Counter.builder("partition.lock.acquired")
                .description("Number of partition locks acquired")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        partitionLockTimeoutCounter = Counter.builder("partition.lock.timeout")
                .description("Number of partition lock timeouts")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        // Solace router counters
        routerMessagesRoutedCounter = Counter.builder("solace.router.messages.routed")
                .description("Number of messages routed to partition topics")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        routerRoutingFailuresCounter = Counter.builder("solace.router.routing.failures")
                .description("Number of routing failures")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        routerPartitionsCreatedCounter = Counter.builder("solace.router.partitions.created")
                .description("Number of partition topics created")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        // Timers
        tradeProcessingTimer = Timer.builder("trades.processing.time")
                .description("Time taken to process a trade")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        enrichmentTimer = Timer.builder("enrichment.time")
                .description("Time taken for enrichment")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        rulesApplicationTimer = Timer.builder("rules.application.time")
                .description("Time taken to apply rules")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        partitionLockAcquisitionTimer = Timer.builder("partition.lock.acquisition.time")
                .description("Time taken to acquire partition lock")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        idempotencyCheckTimer = Timer.builder("idempotency.check.time")
                .description("Time taken for idempotency check")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        databaseQueryTimer = Timer.builder("database.query.time")
                .description("Time taken for database queries")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        routerRoutingTimer = Timer.builder("solace.router.routing.time")
                .description("Time taken to route message to partition topic")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        // Connection pool gauges
        Gauge.builder("database.connections.active", activeConnections, AtomicInteger::get)
                .description("Number of active database connections")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        Gauge.builder("database.connections.idle", idleConnections, AtomicInteger::get)
                .description("Number of idle database connections")
                .tag("service", "trade-capture")
                .register(meterRegistry);

        log.info("Custom metrics initialized");
    }

    // Getters for metrics (to be used by services)
    public Counter getTradesProcessedCounter() {
        return tradesProcessedCounter;
    }

    public Counter getTradesSuccessfulCounter() {
        return tradesSuccessfulCounter;
    }

    public Counter getTradesFailedCounter() {
        return tradesFailedCounter;
    }

    public Counter getTradesDuplicateCounter() {
        return tradesDuplicateCounter;
    }

    public Counter getEnrichmentSuccessCounter() {
        return enrichmentSuccessCounter;
    }

    public Counter getEnrichmentPartialCounter() {
        return enrichmentPartialCounter;
    }

    public Counter getEnrichmentFailedCounter() {
        return enrichmentFailedCounter;
    }

    public Counter getRulesAppliedCounter() {
        return rulesAppliedCounter;
    }

    public Counter getDeadlockRetryCounter() {
        return deadlockRetryCounter;
    }

    public Counter getDeadlockRetrySuccessCounter() {
        return deadlockRetrySuccessCounter;
    }

    public Counter getDeadlockRetryExhaustedCounter() {
        return deadlockRetryExhaustedCounter;
    }

    public Counter getIdempotencyCacheHitCounter() {
        return idempotencyCacheHitCounter;
    }

    public Counter getIdempotencyCacheMissCounter() {
        return idempotencyCacheMissCounter;
    }

    public Counter getPartitionLockAcquiredCounter() {
        return partitionLockAcquiredCounter;
    }

    public Counter getPartitionLockTimeoutCounter() {
        return partitionLockTimeoutCounter;
    }

    public Timer getTradeProcessingTimer() {
        return tradeProcessingTimer;
    }

    public Timer getEnrichmentTimer() {
        return enrichmentTimer;
    }

    public Timer getRulesApplicationTimer() {
        return rulesApplicationTimer;
    }

    public Timer getPartitionLockAcquisitionTimer() {
        return partitionLockAcquisitionTimer;
    }

    public Timer getIdempotencyCheckTimer() {
        return idempotencyCheckTimer;
    }

    public Timer getDatabaseQueryTimer() {
        return databaseQueryTimer;
    }

    public Counter getRouterMessagesRoutedCounter() {
        return routerMessagesRoutedCounter;
    }

    public Counter getRouterRoutingFailuresCounter() {
        return routerRoutingFailuresCounter;
    }

    public Counter getRouterPartitionsCreatedCounter() {
        return routerPartitionsCreatedCounter;
    }

    public Timer getRouterRoutingTimer() {
        return routerRoutingTimer;
    }

    /**
     * Update connection pool metrics periodically.
     */
    @Scheduled(fixedRate = 5000) // Every 5 seconds
    public void updateConnectionPoolMetrics() {
        try {
            if (dataSource instanceof com.zaxxer.hikari.HikariDataSource) {
                com.zaxxer.hikari.HikariDataSource hikariDataSource = 
                    (com.zaxxer.hikari.HikariDataSource) dataSource;
                com.zaxxer.hikari.HikariPoolMXBean poolBean = 
                    hikariDataSource.getHikariPoolMXBean();
                
                if (poolBean != null) {
                    activeConnections.set(poolBean.getActiveConnections());
                    idleConnections.set(poolBean.getIdleConnections());
                }
            }
        } catch (Exception e) {
            log.debug("Error updating connection pool metrics", e);
        }
    }
}

