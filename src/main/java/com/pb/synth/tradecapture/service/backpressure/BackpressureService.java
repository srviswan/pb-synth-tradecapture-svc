package com.pb.synth.tradecapture.service.backpressure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for monitoring and managing backpressure at both API and messaging levels.
 * 
 * Tracks:
 * - API request queue depth
 * - Messaging consumer lag
 * - Processing capacity
 * - System load indicators
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackpressureService {
    
    @Value("${backpressure.api.enabled:true}")
    private boolean apiBackpressureEnabled;
    
    @Value("${backpressure.api.max-queue-size:1000}")
    private int maxApiQueueSize;
    
    @Value("${backpressure.api.warning-threshold:0.8}")
    private double apiWarningThreshold; // 80% of max queue
    
    @Value("${backpressure.messaging.enabled:true}")
    private boolean messagingBackpressureEnabled;
    
    @Value("${backpressure.messaging.max-lag:10000}")
    private long maxConsumerLag; // Maximum acceptable consumer lag
    
    @Value("${backpressure.messaging.warning-lag:5000}")
    private long warningConsumerLag; // Warning threshold for consumer lag
    
    @Value("${backpressure.messaging.max-processing-queue:500}")
    private int maxProcessingQueueSize;
    
    // API-level metrics
    private final AtomicInteger currentApiQueueSize = new AtomicInteger(0);
    private final AtomicLong totalApiRequests = new AtomicLong(0);
    private final AtomicLong rejectedApiRequests = new AtomicLong(0);
    
    // Messaging-level metrics
    private final AtomicLong currentConsumerLag = new AtomicLong(0);
    private final AtomicInteger currentProcessingQueueSize = new AtomicInteger(0);
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
    
    /**
     * Check if API can accept new requests (backpressure check).
     * 
     * @return true if API can accept requests, false if under backpressure
     */
    public boolean canAcceptApiRequest() {
        if (!apiBackpressureEnabled) {
            return true;
        }
        
        int currentSize = currentApiQueueSize.get();
        if (currentSize >= maxApiQueueSize) {
            rejectedApiRequests.incrementAndGet();
            log.warn("API backpressure: Queue full ({} >= {}), rejecting request", 
                currentSize, maxApiQueueSize);
            return false;
        }
        
        // Check warning threshold
        double utilization = (double) currentSize / maxApiQueueSize;
        if (utilization >= apiWarningThreshold) {
            log.warn("API backpressure warning: Queue utilization at {}% ({} / {})", 
                (int)(utilization * 100), currentSize, maxApiQueueSize);
        }
        
        return true;
    }
    
    /**
     * Increment API queue size (called when request is accepted).
     */
    public void incrementApiQueue() {
        currentApiQueueSize.incrementAndGet();
        totalApiRequests.incrementAndGet();
    }
    
    /**
     * Decrement API queue size (called when request is processed).
     */
    public void decrementApiQueue() {
        currentApiQueueSize.decrementAndGet();
    }
    
    /**
     * Check if messaging consumer can process more messages.
     * 
     * @return true if consumer can process, false if under backpressure
     */
    public boolean canProcessMessage() {
        if (!messagingBackpressureEnabled) {
            return true;
        }
        
        long lag = currentConsumerLag.get();
        int queueSize = currentProcessingQueueSize.get();
        
        // Check consumer lag
        if (lag >= maxConsumerLag) {
            log.warn("Messaging backpressure: Consumer lag too high ({} >= {}), pausing consumption", 
                lag, maxConsumerLag);
            return false;
        }
        
        // Check processing queue
        if (queueSize >= maxProcessingQueueSize) {
            log.warn("Messaging backpressure: Processing queue full ({} >= {}), pausing consumption", 
                queueSize, maxProcessingQueueSize);
            return false;
        }
        
        // Check warning thresholds
        if (lag >= warningConsumerLag) {
            log.warn("Messaging backpressure warning: Consumer lag at {} (warning: {})", 
                lag, warningConsumerLag);
        }
        
        return true;
    }
    
    /**
     * Update consumer lag (called by consumer lag monitor).
     * 
     * @param lag Current consumer lag
     */
    public void updateConsumerLag(long lag) {
        currentConsumerLag.set(lag);
    }
    
    /**
     * Increment processing queue size (called when message starts processing).
     */
    public void incrementProcessingQueue() {
        currentProcessingQueueSize.incrementAndGet();
    }
    
    /**
     * Decrement processing queue size (called when message processing completes).
     */
    public void decrementProcessingQueue() {
        currentProcessingQueueSize.decrementAndGet();
        totalMessagesProcessed.incrementAndGet();
    }
    
    /**
     * Get current API backpressure status.
     */
    public ApiBackpressureStatus getApiStatus() {
        int currentSize = currentApiQueueSize.get();
        double utilization = (double) currentSize / maxApiQueueSize;
        boolean underPressure = currentSize >= maxApiQueueSize;
        boolean warning = utilization >= apiWarningThreshold;
        
        return ApiBackpressureStatus.builder()
            .enabled(apiBackpressureEnabled)
            .currentQueueSize(currentSize)
            .maxQueueSize(maxApiQueueSize)
            .utilization(utilization)
            .underPressure(underPressure)
            .warning(warning)
            .totalRequests(totalApiRequests.get())
            .rejectedRequests(rejectedApiRequests.get())
            .build();
    }
    
    /**
     * Get current messaging backpressure status.
     */
    public MessagingBackpressureStatus getMessagingStatus() {
        long lag = currentConsumerLag.get();
        int queueSize = currentProcessingQueueSize.get();
        boolean underPressure = lag >= maxConsumerLag || queueSize >= maxProcessingQueueSize;
        boolean warning = lag >= warningConsumerLag;
        
        return MessagingBackpressureStatus.builder()
            .enabled(messagingBackpressureEnabled)
            .currentLag(lag)
            .maxLag(maxConsumerLag)
            .warningLag(warningConsumerLag)
            .currentQueueSize(queueSize)
            .maxQueueSize(maxProcessingQueueSize)
            .underPressure(underPressure)
            .warning(warning)
            .totalProcessed(totalMessagesProcessed.get())
            .build();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ApiBackpressureStatus {
        private boolean enabled;
        private int currentQueueSize;
        private int maxQueueSize;
        private double utilization;
        private boolean underPressure;
        private boolean warning;
        private long totalRequests;
        private long rejectedRequests;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MessagingBackpressureStatus {
        private boolean enabled;
        private long currentLag;
        private long maxLag;
        private long warningLag;
        private int currentQueueSize;
        private int maxQueueSize;
        private boolean underPressure;
        private boolean warning;
        private long totalProcessed;
    }
}

