package com.pb.synth.tradecapture.config;

import com.pb.synth.tradecapture.service.backpressure.BackpressureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for API-level backpressure handling.
 * 
 * Implements:
 * - Bounded thread pool for API request processing
 * - Request queue monitoring
 * - 503 Service Unavailable responses when overloaded
 * - Retry-After header for client backoff
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ApiBackpressureConfig implements WebMvcConfigurer {
    
    private final BackpressureService backpressureService;
    
    @Value("${backpressure.api.enabled:true}")
    private boolean apiBackpressureEnabled;
    
    @Value("${backpressure.api.thread-pool.core-size:20}")
    private int threadPoolCoreSize;
    
    @Value("${backpressure.api.thread-pool.max-size:50}")
    private int threadPoolMaxSize;
    
    @Value("${backpressure.api.thread-pool.queue-capacity:1000}")
    private int threadPoolQueueCapacity;
    
    @Value("${backpressure.api.retry-after-seconds:5}")
    private int retryAfterSeconds;
    
    /**
     * Thread pool executor for API request processing.
     * Bounded queue provides natural backpressure.
     */
    @Bean(name = "apiRequestExecutor")
    public ThreadPoolTaskExecutor apiRequestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolCoreSize);
        executor.setMaxPoolSize(threadPoolMaxSize);
        executor.setQueueCapacity(threadPoolQueueCapacity);
        executor.setThreadNamePrefix("api-request-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        log.info("Created API request executor: core={}, max={}, queue={}", 
            threadPoolCoreSize, threadPoolMaxSize, threadPoolQueueCapacity);
        
        return executor;
    }
    
    /**
     * Interceptor to check backpressure before processing API requests.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (apiBackpressureEnabled) {
            registry.addInterceptor(new HandlerInterceptor() {
                @Override
                public boolean preHandle(HttpServletRequest request, 
                                       HttpServletResponse response, 
                                       Object handler) throws Exception {
                    // Skip backpressure check for health/status endpoints
                    String path = request.getRequestURI();
                    if (path.contains("/health") || path.contains("/status") || 
                        path.contains("/backpressure")) {
                        return true;
                    }
                    
                    // Check if we can accept the request
                    if (!backpressureService.canAcceptApiRequest()) {
                        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                        response.setContentType("application/json");
                        response.getWriter().write(String.format(
                            "{\"error\":{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"Service is currently overloaded. Please retry after %d seconds.\"}}",
                            retryAfterSeconds));
                        return false;
                    }
                    
                    // Increment queue counter
                    backpressureService.incrementApiQueue();
                    return true;
                }
                
                @Override
                public void afterCompletion(HttpServletRequest request, 
                                          HttpServletResponse response, 
                                          Object handler, 
                                          Exception ex) throws Exception {
                    // Decrement queue counter when request completes
                    backpressureService.decrementApiQueue();
                }
            }).addPathPatterns("/api/v1/trades/**");
        }
    }
}

