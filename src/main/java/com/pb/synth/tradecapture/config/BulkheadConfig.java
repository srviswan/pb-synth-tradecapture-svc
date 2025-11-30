package com.pb.synth.tradecapture.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for Bulkhead Pattern (Priority 5.2).
 * 
 * Creates separate thread pools for:
 * - Partition processing (isolated by partition groups)
 * - External service calls (enrichment, approval workflow)
 * 
 * This prevents cascading failures and provides better resource isolation.
 */
@Configuration
@Slf4j
public class BulkheadConfig {
    
    @Value("${bulkhead.enabled:true}")
    private boolean bulkheadEnabled;
    
    @Value("${bulkhead.partition-groups:10}")
    private int partitionGroups; // Number of partition groups (each gets its own thread pool)
    
    @Value("${bulkhead.partition-group.core-size:5}")
    private int partitionGroupCoreSize;
    
    @Value("${bulkhead.partition-group.max-size:10}")
    private int partitionGroupMaxSize;
    
    @Value("${bulkhead.partition-group.queue-capacity:100}")
    private int partitionGroupQueueCapacity;
    
    @Value("${bulkhead.external-services.core-size:10}")
    private int externalServicesCoreSize;
    
    @Value("${bulkhead.external-services.max-size:20}")
    private int externalServicesMaxSize;
    
    @Value("${bulkhead.external-services.queue-capacity:200}")
    private int externalServicesQueueCapacity;
    
    /**
     * Get executor for a specific partition group.
     * Partitions are distributed across groups using hash-based partitioning.
     * 
     * @param partitionKey The partition key
     * @return Executor for the partition group
     */
    public Executor getPartitionGroupExecutor(String partitionKey) {
        if (!bulkheadEnabled) {
            // Return default executor if bulkhead disabled
            return defaultPartitionExecutor();
        }
        
        // Hash partition key to determine which group it belongs to
        int groupIndex = Math.abs(partitionKey.hashCode()) % partitionGroups;
        return getOrCreatePartitionGroupExecutor(groupIndex);
    }
    
    /**
     * Default executor for partition processing (when bulkhead disabled).
     */
    @Bean(name = "defaultPartitionExecutor")
    public Executor defaultPartitionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(partitionGroupCoreSize);
        executor.setMaxPoolSize(partitionGroupMaxSize);
        executor.setQueueCapacity(partitionGroupQueueCapacity);
        executor.setThreadNamePrefix("partition-default-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("Created default partition executor: core={}, max={}, queue={}", 
            partitionGroupCoreSize, partitionGroupMaxSize, partitionGroupQueueCapacity);
        return executor;
    }
    
    /**
     * Executor for external service calls (enrichment, approval workflow).
     * Isolated from partition processing to prevent cascading failures.
     */
    @Bean(name = "externalServicesExecutor")
    public Executor externalServicesExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(externalServicesCoreSize);
        executor.setMaxPoolSize(externalServicesMaxSize);
        executor.setQueueCapacity(externalServicesQueueCapacity);
        executor.setThreadNamePrefix("external-services-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("Created external services executor: core={}, max={}, queue={}", 
            externalServicesCoreSize, externalServicesMaxSize, externalServicesQueueCapacity);
        return executor;
    }
    
    /**
     * Get or create executor for a specific partition group.
     * In a real implementation, this would cache executors per group.
     * For simplicity, we create a new executor per group (can be optimized with caching).
     */
    private Executor getOrCreatePartitionGroupExecutor(int groupIndex) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(partitionGroupCoreSize);
        executor.setMaxPoolSize(partitionGroupMaxSize);
        executor.setQueueCapacity(partitionGroupQueueCapacity);
        executor.setThreadNamePrefix("partition-group-" + groupIndex + "-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.debug("Created partition group executor for group {}: core={}, max={}, queue={}", 
            groupIndex, partitionGroupCoreSize, partitionGroupMaxSize, partitionGroupQueueCapacity);
        return executor;
    }
}

