package com.pb.synth.tradecapture.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for parallel processing of partitions.
 */
@Configuration
@EnableAsync
public class ParallelProcessingConfig {
    
    @Value("${processing.parallelism.core-pool-size:10}")
    private int corePoolSize;
    
    @Value("${processing.parallelism.max-pool-size:20}")
    private int maxPoolSize;
    
    @Value("${processing.parallelism.queue-capacity:100}")
    private int queueCapacity;
    
    @Value("${processing.parallelism.thread-name-prefix:partition-processor-}")
    private String threadNamePrefix;
    
    /**
     * Thread pool executor for parallel partition processing.
     */
    @Bean(name = "partitionProcessingExecutor")
    public Executor partitionProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
    
    /**
     * Thread pool executor for enrichment operations.
     */
    @Bean(name = "enrichmentExecutor")
    public Executor enrichmentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("enrichment-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

