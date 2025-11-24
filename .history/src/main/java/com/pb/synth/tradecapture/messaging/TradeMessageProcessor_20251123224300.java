package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.model.TradeCaptureResponse;
import com.pb.synth.tradecapture.service.TradeCaptureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Processes incoming trade messages from queues.
 * This component is called by message consumers (Kafka, Solace, etc.)
 * to process trade capture requests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeMessageProcessor {
    
    private final TradeCaptureService tradeCaptureService;
    
    /**
     * Process a trade capture request from a message queue.
     * 
     * @param request The trade capture request
     * @return The processing response
     */
    public TradeCaptureResponse processMessage(TradeCaptureRequest request) {
        log.info("Processing trade message: tradeId={}, partitionKey={}", 
            request.getTradeId(), request.getPartitionKey());
        
        try {
            TradeCaptureResponse response = tradeCaptureService.processTrade(request);
            log.info("Successfully processed trade: tradeId={}, status={}", 
                request.getTradeId(), response.getStatus());
            return response;
        } catch (Exception e) {
            log.error("Error processing trade message: tradeId={}", 
                request.getTradeId(), e);
            throw new RuntimeException("Failed to process trade message", e);
        }
    }
}

