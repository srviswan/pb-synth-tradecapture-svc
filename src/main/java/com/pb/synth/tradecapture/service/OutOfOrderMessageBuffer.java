package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.messaging.DLQPublisher;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for buffering out-of-order messages and processing them in order.
 * Implements Priority 4.2: Out-of-Order Message Handling
 */
@Service
@Slf4j
public class OutOfOrderMessageBuffer {

    private final DLQPublisher dlqPublisher;
    private final ApplicationContext applicationContext;
    
    public OutOfOrderMessageBuffer(DLQPublisher dlqPublisher, ApplicationContext applicationContext) {
        this.dlqPublisher = dlqPublisher;
        this.applicationContext = applicationContext;
    }
    
    /**
     * Get TradeCaptureService lazily (to break circular dependency).
     */
    private TradeCaptureService getTradeCaptureService() {
        return applicationContext.getBean(TradeCaptureService.class);
    }

    @Value("${sequence.validation.enabled:true}")
    private boolean sequenceValidationEnabled; // Master switch for sequence validation

    @Value("${sequence.buffer.enabled:true}")
    private boolean bufferEnabled;

    @Value("${sequence.buffer.window-size:1000}")
    private int bufferWindowSize; // Maximum number of messages to buffer per partition

    @Value("${sequence.buffer.timeout-seconds:300}")
    private long bufferTimeoutSeconds; // Timeout for waiting for missing sequence numbers

    @Value("${sequence.buffer.time-window-days:7}")
    private long timeWindowDays; // Time-based sliding window: only buffer trades within last N days

    // Buffer: partitionKey -> (sequenceNumber -> BufferedMessage)
    private final Map<String, Map<Long, BufferedMessage>> buffer = new ConcurrentHashMap<>();
    
    // Track highest processed sequence number per partition (not last + 1, but highest seen)
    private final Map<String, Long> highestProcessedSequence = new ConcurrentHashMap<>();
    
    // Track oldest buffered message timestamp per partition (for timeout)
    private final Map<String, Instant> oldestMessageTime = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Initialize scheduler for timeout checks
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::checkTimeouts, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Process message with sequence number validation and buffering.
     * Uses time-based sliding window: only buffer trades within the window (e.g., last 7 days).
     * Trades outside the window are processed immediately (too old to wait for).
     * 
     * @param request The trade capture request
     * @return Processing result
     */
    public BufferResult processWithSequenceValidation(TradeCaptureRequest request) {
        // If sequence validation is disabled, process directly
        if (!sequenceValidationEnabled) {
            log.debug("Sequence validation disabled - processing trade directly: tradeId={}", request.getTradeId());
            return BufferResult.builder()
                .shouldProcess(true)
                .request(request)
                .reason("VALIDATION_DISABLED")
                .build();
        }
        
        if (!bufferEnabled || request.getSequenceNumber() == null) {
            // If buffering disabled or no sequence number, process directly
            return BufferResult.builder()
                .shouldProcess(true)
                .request(request)
                .build();
        }

        String partitionKey = request.getPartitionKey();
        long sequenceNumber = request.getSequenceNumber();
        long highestProcessed = getHighestProcessedSequence(partitionKey);
        long expected = highestProcessed + 1; // Next expected sequence

        // Check if trade is within time window
        boolean withinTimeWindow = isWithinTimeWindow(request);
        
        if (!withinTimeWindow) {
            // Trade is outside time window (too old) - process immediately
            // Can't wait for older trades that may never arrive
            log.info("Processing trade outside time window immediately: partition={}, sequence={}, highestProcessed={}", 
                partitionKey, sequenceNumber, highestProcessed);
            updateHighestProcessedSequence(partitionKey, Math.max(highestProcessed, sequenceNumber));
            return BufferResult.builder()
                .shouldProcess(true)
                .request(request)
                .reason("OUTSIDE_TIME_WINDOW")
                .expectedSequence(expected)
                .receivedSequence(sequenceNumber)
                .build();
        }

        // Trade is within time window - check if it's in order
        if (sequenceNumber == expected) {
            // In order - process immediately
            log.debug("Message in order: partition={}, sequence={}", partitionKey, sequenceNumber);
            updateHighestProcessedSequence(partitionKey, sequenceNumber);
            processBufferedMessages(partitionKey);
            return BufferResult.builder()
                .shouldProcess(true)
                .request(request)
                .build();
        } else if (sequenceNumber < expected) {
            // Out of order (earlier sequence) - buffer it, waiting for even earlier trades
            long gap = expected - sequenceNumber;
            log.info("Buffering out-of-order message (earlier): partition={}, expected={}, received={}, gap={}", 
                partitionKey, expected, sequenceNumber, gap);
            bufferMessage(partitionKey, sequenceNumber, request);
            return BufferResult.builder()
                .shouldProcess(false)
                .request(request)
                .reason("BUFFERED_EARLIER")
                .expectedSequence(expected)
                .receivedSequence(sequenceNumber)
                .gap(-gap)
                .build();
        } else {
            // Gap detected (later sequence) - buffer it, waiting for gaps to fill
            long gap = sequenceNumber - expected;
            
            if (gap > bufferWindowSize) {
                // Gap too large - reject and send to DLQ
                log.error("Sequence gap too large: partition={}, expected={}, received={}, gap={}, max={}", 
                    partitionKey, expected, sequenceNumber, gap, bufferWindowSize);
                dlqPublisher.publishToDLQ(request, new RuntimeException(
                    String.format("Sequence gap too large: expected %d, received %d, gap %d", 
                        expected, sequenceNumber, gap)));
                return BufferResult.builder()
                    .shouldProcess(false)
                    .request(request)
                    .reason("GAP_TOO_LARGE")
                    .expectedSequence(expected)
                    .receivedSequence(sequenceNumber)
                    .gap(gap)
                    .build();
            }

            // Buffer the message, waiting for missing sequences
            log.info("Buffering out-of-order message (gap): partition={}, expected={}, received={}, gap={}", 
                partitionKey, expected, sequenceNumber, gap);
            bufferMessage(partitionKey, sequenceNumber, request);
            return BufferResult.builder()
                .shouldProcess(false)
                .request(request)
                .reason("BUFFERED")
                .expectedSequence(expected)
                .receivedSequence(sequenceNumber)
                .gap(gap)
                .build();
        }
    }
    
    /**
     * Check if trade is within the time-based sliding window based on bookingTimestamp.
     * Only buffer trades within the window (e.g., last 7 days).
     * Trades outside the window are processed immediately.
     */
    private boolean isWithinTimeWindow(TradeCaptureRequest request) {
        ZonedDateTime bookingTime = request.getBookingTimestamp();
        if (bookingTime == null) {
            // No booking timestamp - assume within window (process normally)
            return true;
        }
        
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        java.time.Duration age = java.time.Duration.between(bookingTime, now);
        
        if (age.isNegative()) {
            // Trade is in the future - within window
            return true;
        }
        
        long ageDays = age.toDays();
        return ageDays <= timeWindowDays;
    }

    /**
     * Buffer a message for later processing.
     */
    private void bufferMessage(String partitionKey, long sequenceNumber, TradeCaptureRequest request) {
        buffer.computeIfAbsent(partitionKey, k -> new ConcurrentHashMap<>())
            .put(sequenceNumber, new BufferedMessage(request, Instant.now()));
        
        // Track oldest message time for timeout
        oldestMessageTime.compute(partitionKey, (k, v) -> {
            Instant now = Instant.now();
            return (v == null || now.isBefore(v)) ? now : v;
        });
    }

    /**
     * Process buffered messages in chronological order (by sequence number).
     * Processes all consecutive messages starting from the expected sequence.
     */
    private void processBufferedMessages(String partitionKey) {
        Map<Long, BufferedMessage> partitionBuffer = buffer.get(partitionKey);
        if (partitionBuffer == null || partitionBuffer.isEmpty()) {
            return;
        }

        long highestProcessed = getHighestProcessedSequence(partitionKey);
        long expected = highestProcessed + 1;
        List<Long> sequencesToProcess = new ArrayList<>();

        // Find consecutive messages starting from expected sequence
        while (partitionBuffer.containsKey(expected)) {
            sequencesToProcess.add(expected);
            expected++;
        }

        // Process messages in chronological order
        for (Long seq : sequencesToProcess) {
            BufferedMessage buffered = partitionBuffer.remove(seq);
            if (buffered != null) {
                log.info("Processing buffered message: partition={}, sequence={}", partitionKey, seq);
                try {
                    getTradeCaptureService().processTrade(buffered.request);
                    updateHighestProcessedSequence(partitionKey, seq);
                } catch (Exception e) {
                    log.error("Error processing buffered message: partition={}, sequence={}", 
                        partitionKey, seq, e);
                    dlqPublisher.publishToDLQ(buffered.request, e);
                }
            }
        }

        // Clean up if buffer is empty
        if (partitionBuffer.isEmpty()) {
            buffer.remove(partitionKey);
            oldestMessageTime.remove(partitionKey);
        }
    }

    /**
     * Get highest processed sequence number for partition.
     * This is the highest sequence number we've successfully processed, not last + 1.
     */
    private long getHighestProcessedSequence(String partitionKey) {
        return highestProcessedSequence.getOrDefault(partitionKey, 0L);
    }

    /**
     * Update highest processed sequence number after processing.
     * We track the highest sequence processed, not just last + 1.
     */
    private void updateHighestProcessedSequence(String partitionKey, long sequenceNumber) {
        highestProcessedSequence.merge(partitionKey, sequenceNumber, Math::max);
    }

    /**
     * Check for timeout messages and send to DLQ.
     */
    private void checkTimeouts() {
        Instant now = Instant.now();
        Duration timeout = Duration.ofSeconds(bufferTimeoutSeconds);

        List<String> partitionsToCleanup = new ArrayList<>();

        for (Map.Entry<String, Instant> entry : oldestMessageTime.entrySet()) {
            String partitionKey = entry.getKey();
            Instant oldestTime = entry.getValue();

            if (now.minus(timeout).isAfter(oldestTime)) {
                log.warn("Buffer timeout for partition: {}, oldest message age: {}s", 
                    partitionKey, Duration.between(oldestTime, now).getSeconds());
                
                // Send all buffered messages to DLQ
                Map<Long, BufferedMessage> partitionBuffer = buffer.get(partitionKey);
                if (partitionBuffer != null) {
                    partitionBuffer.values().forEach(buffered -> {
                        dlqPublisher.publishToDLQ(buffered.request, new RuntimeException(
                            "Message buffer timeout - missing sequence numbers"));
                    });
                    partitionBuffer.clear();
                }
                
                partitionsToCleanup.add(partitionKey);
            }
        }

        // Clean up
        partitionsToCleanup.forEach(partitionKey -> {
            buffer.remove(partitionKey);
            oldestMessageTime.remove(partitionKey);
        });
    }

    /**
     * Get buffer status for monitoring.
     */
    public BufferStatus getBufferStatus() {
        int totalBuffered = buffer.values().stream()
            .mapToInt(Map::size)
            .sum();
        
        return BufferStatus.builder()
            .enabled(bufferEnabled)
            .totalPartitions(buffer.size())
            .totalBufferedMessages(totalBuffered)
            .bufferWindowSize(bufferWindowSize)
            .bufferTimeoutSeconds(bufferTimeoutSeconds)
            .build();
    }

    /**
     * Clear buffer for a partition (for testing/admin).
     */
    public void clearBuffer(String partitionKey) {
        buffer.remove(partitionKey);
        oldestMessageTime.remove(partitionKey);
        highestProcessedSequence.remove(partitionKey);
        log.info("Cleared buffer for partition: {}", partitionKey);
    }

    @lombok.Data
    @lombok.Builder
    public static class BufferResult {
        private boolean shouldProcess;
        private TradeCaptureRequest request;
        private String reason; // BUFFERED, OUT_OF_ORDER_TOO_OLD, GAP_TOO_LARGE
        private Long expectedSequence;
        private Long receivedSequence;
        private Long gap;
    }

    @lombok.Data
    @lombok.Builder
    public static class BufferStatus {
        private boolean enabled;
        private int totalPartitions;
        private int totalBufferedMessages;
        private int bufferWindowSize;
        private long bufferTimeoutSeconds;
    }

    private static class BufferedMessage {
        final TradeCaptureRequest request;
        final Instant bufferedAt;

        BufferedMessage(TradeCaptureRequest request, Instant bufferedAt) {
            this.request = request;
            this.bufferedAt = bufferedAt;
        }
    }
}

