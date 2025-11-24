package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.model.TradeLot;
import com.pb.synth.tradecapture.model.TradeSource;
import com.pb.synth.tradecapture.proto.TradeCaptureProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between protobuf TradeCaptureMessage and Java TradeCaptureRequest.
 */
@Component
@Slf4j
public class MessageConverter {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_ZONED_DATE_TIME;
    
    /**
     * Convert protobuf TradeCaptureMessage to TradeCaptureRequest.
     */
    public TradeCaptureRequest toTradeCaptureRequest(TradeCaptureProto.TradeCaptureMessage protoMessage) {
        if (protoMessage == null) {
            throw new IllegalArgumentException("TradeCaptureMessage cannot be null");
        }
        
        try {
            TradeCaptureRequest.TradeCaptureRequestBuilder builder = TradeCaptureRequest.builder()
                .tradeId(protoMessage.getTradeId())
                .accountId(protoMessage.getAccountId())
                .bookId(protoMessage.getBookId())
                .securityId(protoMessage.getSecurityId())
                .source(convertTradeSource(protoMessage.getSource()))
                .tradeLots(convertTradeLots(protoMessage.getTradeLotsList()))
                .counterpartyIds(new ArrayList<>(protoMessage.getCounterpartyIdsList()))
                .metadata(convertMetadata(protoMessage.getMetadataMap()));
            
            // Set idempotency key if provided
            if (!protoMessage.getIdempotencyKey().isEmpty()) {
                builder.idempotencyKey(protoMessage.getIdempotencyKey());
            } else {
                builder.idempotencyKey(protoMessage.getTradeId());
            }
            
            // Parse trade date
            if (!protoMessage.getTradeDate().isEmpty()) {
                builder.tradeDate(LocalDate.parse(protoMessage.getTradeDate(), DATE_FORMATTER));
            } else {
                builder.tradeDate(LocalDate.now());
            }
            
            // Manual entry info
            if (protoMessage.hasManualEntryInfo()) {
                TradeCaptureProto.ManualEntryInfo manualInfo = protoMessage.getManualEntryInfo();
                builder.enteredBy(manualInfo.getEnteredBy());
                if (!manualInfo.getEntryTimestamp().isEmpty()) {
                    builder.entryTimestamp(LocalDate.parse(manualInfo.getEntryTimestamp(), DATE_FORMATTER));
                }
            }
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("Error converting protobuf message to TradeCaptureRequest: {}", protoMessage.getTradeId(), e);
            throw new RuntimeException("Failed to convert protobuf message", e);
        }
    }
    
    /**
     * Convert TradeCaptureRequest to protobuf TradeCaptureMessage.
     */
    public TradeCaptureProto.TradeCaptureMessage toProtobufMessage(TradeCaptureRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("TradeCaptureRequest cannot be null");
        }
        
        try {
            TradeCaptureProto.TradeCaptureMessage.Builder builder = TradeCaptureProto.TradeCaptureMessage.newBuilder()
                .setTradeId(request.getTradeId())
                .setAccountId(request.getAccountId())
                .setBookId(request.getBookId())
                .setSecurityId(request.getSecurityId())
                .setPartitionKey(request.getPartitionKey())
                .setSource(convertTradeSource(request.getSource()))
                .setTradeDate(request.getTradeDate().format(DATE_FORMATTER))
                .setTimestamp(ZonedDateTime.now().format(TIMESTAMP_FORMATTER))
                .addAllCounterpartyIds(request.getCounterpartyIds());
            
            // Set idempotency key
            String idempotencyKey = request.getIdempotencyKey() != null 
                ? request.getIdempotencyKey() 
                : request.getTradeId();
            builder.setIdempotencyKey(idempotencyKey);
            
            // Convert trade lots
            request.getTradeLots().forEach(tradeLot -> {
                TradeCaptureProto.TradeLot.Builder lotBuilder = TradeCaptureProto.TradeLot.newBuilder();
                // TODO: Convert TradeLot details to protobuf
                builder.addTradeLots(lotBuilder.build());
            });
            
            // Convert metadata
            if (request.getMetadata() != null) {
                request.getMetadata().forEach((key, value) -> 
                    builder.putMetadata(key, value != null ? value.toString() : ""));
            }
            
            // Manual entry info
            if (request.getEnteredBy() != null) {
                TradeCaptureProto.ManualEntryInfo.Builder manualBuilder = 
                    TradeCaptureProto.ManualEntryInfo.newBuilder()
                        .setEnteredBy(request.getEnteredBy());
                if (request.getEntryTimestamp() != null) {
                    manualBuilder.setEntryTimestamp(request.getEntryTimestamp().format(DATE_FORMATTER));
                }
                builder.setManualEntryInfo(manualBuilder.build());
            }
            
            return builder.build();
        } catch (Exception e) {
            log.error("Error converting TradeCaptureRequest to protobuf: {}", request.getTradeId(), e);
            throw new RuntimeException("Failed to convert to protobuf message", e);
        }
    }
    
    private TradeSource convertTradeSource(TradeCaptureProto.TradeSource protoSource) {
        return switch (protoSource) {
            case AUTOMATED -> TradeSource.AUTOMATED;
            case MANUAL -> TradeSource.MANUAL;
            default -> TradeSource.AUTOMATED;
        };
    }
    
    private TradeCaptureProto.TradeSource convertTradeSource(TradeSource source) {
        return switch (source) {
            case AUTOMATED -> TradeCaptureProto.TradeSource.AUTOMATED;
            case MANUAL -> TradeCaptureProto.TradeSource.MANUAL;
        };
    }
    
    private List<TradeLot> convertTradeLots(List<TradeCaptureProto.TradeLot> protoLots) {
        // TODO: Implement full conversion of TradeLot from protobuf
        // For now, return empty list - this needs to be implemented based on TradeLot structure
        return new ArrayList<>();
    }
    
    private Map<String, Object> convertMetadata(Map<String, String> protoMetadata) {
        Map<String, Object> metadata = new HashMap<>();
        protoMetadata.forEach((key, value) -> metadata.put(key, value));
        return metadata;
    }
}

