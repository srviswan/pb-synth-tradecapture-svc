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
            
            // Extract trade timestamp from protobuf message
            if (!protoMessage.getTimestamp().isEmpty()) {
                try {
                    ZonedDateTime timestamp = ZonedDateTime.parse(protoMessage.getTimestamp(), TIMESTAMP_FORMATTER);
                    builder.tradeTimestamp(timestamp);
                } catch (Exception e) {
                    log.warn("Error parsing timestamp from protobuf: {}", protoMessage.getTimestamp(), e);
                    // Fallback to current time
                    builder.tradeTimestamp(ZonedDateTime.now());
                }
            } else {
                // If no timestamp in message, use current time
                builder.tradeTimestamp(ZonedDateTime.now());
            }
            
            // Extract booking timestamp from protobuf message (for out-of-order detection)
            if (!protoMessage.getBookingTimestamp().isEmpty()) {
                try {
                    ZonedDateTime bookingTimestamp = ZonedDateTime.parse(protoMessage.getBookingTimestamp(), TIMESTAMP_FORMATTER);
                    builder.bookingTimestamp(bookingTimestamp);
                } catch (Exception e) {
                    log.warn("Error parsing booking timestamp from protobuf: {}", protoMessage.getBookingTimestamp(), e);
                    // Fallback: bookingTimestamp will be null, getBookingTimestamp() will use tradeTimestamp
                }
            }
            // If booking timestamp not provided, getBookingTimestamp() will fall back to tradeTimestamp
            
            // Extract sequence number from protobuf message (incremental, assigned by upstream system)
            if (protoMessage.getSequenceNumber() > 0) {
                builder.sequenceNumber(protoMessage.getSequenceNumber());
            }
            
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
            
            // Set sequence number if provided
            if (request.getSequenceNumber() != null) {
                builder.setSequenceNumber(request.getSequenceNumber());
            }
            
            // Set booking timestamp if provided
            if (request.getBookingTimestamp() != null) {
                builder.setBookingTimestamp(request.getBookingTimestamp().format(TIMESTAMP_FORMATTER));
            }
            
            // Set idempotency key
            String idempotencyKey = request.getIdempotencyKey() != null 
                ? request.getIdempotencyKey() 
                : request.getTradeId();
            builder.setIdempotencyKey(idempotencyKey);
            
            // Convert trade lots
            if (request.getTradeLots() != null) {
                for (TradeLot tradeLot : request.getTradeLots()) {
                    TradeCaptureProto.TradeLot.Builder lotBuilder = TradeCaptureProto.TradeLot.newBuilder();
                    
                    // Convert lot identifiers
                    if (tradeLot.getLotIdentifier() != null) {
                        for (com.pb.synth.tradecapture.model.Identifier identifier : tradeLot.getLotIdentifier()) {
                            TradeCaptureProto.Identifier protoId = TradeCaptureProto.Identifier.newBuilder()
                                .setIdentifier(identifier.getIdentifier() != null ? identifier.getIdentifier() : "")
                                .setIdentifierType(identifier.getIdentifierType() != null ? identifier.getIdentifierType() : "")
                                .build();
                            lotBuilder.addLotIdentifier(protoId);
                        }
                    }
                    
                    // Convert price quantity
                    if (tradeLot.getPriceQuantity() != null) {
                        for (com.pb.synth.tradecapture.model.PriceQuantity pq : tradeLot.getPriceQuantity()) {
                            TradeCaptureProto.PriceQuantity.Builder pqBuilder = TradeCaptureProto.PriceQuantity.newBuilder();
                            
                            // Convert quantities
                            if (pq.getQuantity() != null) {
                                for (com.pb.synth.tradecapture.model.Quantity qty : pq.getQuantity()) {
                                    TradeCaptureProto.Quantity.Builder qtyBuilder = TradeCaptureProto.Quantity.newBuilder()
                                        .setValue(qty.getValue() != null ? qty.getValue() : 0.0);
                                    
                                    // Convert unit
                                    if (qty.getUnit() != null) {
                                        TradeCaptureProto.Unit.Builder unitBuilder = TradeCaptureProto.Unit.newBuilder();
                                        if (qty.getUnit().getCurrency() != null) {
                                            unitBuilder.setCurrency(qty.getUnit().getCurrency());
                                        }
                                        if (qty.getUnit().getFinancialUnit() != null) {
                                            unitBuilder.setFinancialUnit(qty.getUnit().getFinancialUnit());
                                        }
                                        qtyBuilder.setUnit(unitBuilder.build());
                                    }
                                    pqBuilder.addQuantity(qtyBuilder.build());
                                }
                            }
                            
                            // Convert prices
                            if (pq.getPrice() != null) {
                                for (com.pb.synth.tradecapture.model.Price price : pq.getPrice()) {
                                    TradeCaptureProto.Price.Builder priceBuilder = TradeCaptureProto.Price.newBuilder()
                                        .setValue(price.getValue() != null ? price.getValue() : 0.0);
                                    
                                    // Convert unit
                                    if (price.getUnit() != null) {
                                        TradeCaptureProto.Unit.Builder unitBuilder = TradeCaptureProto.Unit.newBuilder();
                                        if (price.getUnit().getCurrency() != null) {
                                            unitBuilder.setCurrency(price.getUnit().getCurrency());
                                        }
                                        if (price.getUnit().getFinancialUnit() != null) {
                                            unitBuilder.setFinancialUnit(price.getUnit().getFinancialUnit());
                                        }
                                        priceBuilder.setUnit(unitBuilder.build());
                                    }
                                    pqBuilder.addPrice(priceBuilder.build());
                                }
                            }
                            
                            lotBuilder.addPriceQuantity(pqBuilder.build());
                        }
                    }
                    
                    builder.addTradeLots(lotBuilder.build());
                }
            }
            
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
        if (protoLots == null || protoLots.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<TradeLot> tradeLots = new ArrayList<>();
        for (TradeCaptureProto.TradeLot protoLot : protoLots) {
            TradeLot.TradeLotBuilder lotBuilder = TradeLot.builder();
            
            // Convert lot identifiers
            List<com.pb.synth.tradecapture.model.Identifier> identifiers = new ArrayList<>();
            for (TradeCaptureProto.Identifier protoId : protoLot.getLotIdentifierList()) {
                identifiers.add(com.pb.synth.tradecapture.model.Identifier.builder()
                    .identifier(protoId.getIdentifier())
                    .identifierType(protoId.getIdentifierType())
                    .build());
            }
            lotBuilder.lotIdentifier(identifiers);
            
            // Convert price quantity
            List<com.pb.synth.tradecapture.model.PriceQuantity> priceQuantities = new ArrayList<>();
            for (TradeCaptureProto.PriceQuantity protoPq : protoLot.getPriceQuantityList()) {
                com.pb.synth.tradecapture.model.PriceQuantity.PriceQuantityBuilder pqBuilder = 
                    com.pb.synth.tradecapture.model.PriceQuantity.builder();
                
                // Convert quantities
                List<com.pb.synth.tradecapture.model.Quantity> quantities = new ArrayList<>();
                for (TradeCaptureProto.Quantity protoQty : protoPq.getQuantityList()) {
                    com.pb.synth.tradecapture.model.Quantity.QuantityBuilder qtyBuilder = 
                        com.pb.synth.tradecapture.model.Quantity.builder()
                            .value(protoQty.getValue());
                    
                    // Convert unit
                    if (protoQty.hasUnit()) {
                        TradeCaptureProto.Unit protoUnit = protoQty.getUnit();
                        com.pb.synth.tradecapture.model.Unit unit = com.pb.synth.tradecapture.model.Unit.builder()
                            .currency(protoUnit.getCurrency().isEmpty() ? null : protoUnit.getCurrency())
                            .financialUnit(protoUnit.getFinancialUnit().isEmpty() ? null : protoUnit.getFinancialUnit())
                            .build();
                        qtyBuilder.unit(unit);
                    }
                    quantities.add(qtyBuilder.build());
                }
                pqBuilder.quantity(quantities);
                
                // Convert prices
                List<com.pb.synth.tradecapture.model.Price> prices = new ArrayList<>();
                for (TradeCaptureProto.Price protoPrice : protoPq.getPriceList()) {
                    com.pb.synth.tradecapture.model.Price.PriceBuilder priceBuilder = 
                        com.pb.synth.tradecapture.model.Price.builder()
                            .value(protoPrice.getValue());
                    
                    // Convert unit
                    if (protoPrice.hasUnit()) {
                        TradeCaptureProto.Unit protoUnit = protoPrice.getUnit();
                        com.pb.synth.tradecapture.model.Unit unit = com.pb.synth.tradecapture.model.Unit.builder()
                            .currency(protoUnit.getCurrency().isEmpty() ? null : protoUnit.getCurrency())
                            .financialUnit(protoUnit.getFinancialUnit().isEmpty() ? null : protoUnit.getFinancialUnit())
                            .build();
                        priceBuilder.unit(unit);
                    }
                    prices.add(priceBuilder.build());
                }
                pqBuilder.price(prices);
                
                priceQuantities.add(pqBuilder.build());
            }
            lotBuilder.priceQuantity(priceQuantities);
            
            tradeLots.add(lotBuilder.build());
        }
        
        return tradeLots;
    }
    
    private Map<String, Object> convertMetadata(Map<String, String> protoMetadata) {
        Map<String, Object> metadata = new HashMap<>();
        protoMetadata.forEach((key, value) -> metadata.put(key, value));
        return metadata;
    }
    
    /**
     * Convert SwapBlotter to protobuf SwapBlotterMessage.
     * This is a placeholder - full implementation would convert all SwapBlotter fields.
     */
    public TradeCaptureProto.SwapBlotterMessage toSwapBlotterMessage(com.pb.synth.tradecapture.model.SwapBlotter swapBlotter) {
        if (swapBlotter == null) {
            throw new IllegalArgumentException("SwapBlotter cannot be null");
        }
        
        try {
            TradeCaptureProto.SwapBlotterMessage.Builder builder = 
                TradeCaptureProto.SwapBlotterMessage.newBuilder()
                    .setTradeId(swapBlotter.getTradeId())
                    .setPartitionKey(swapBlotter.getPartitionKey());
            
            // TODO: Convert all SwapBlotter fields to protobuf
            // This is a simplified version - full implementation would include:
            // - Contract details
            // - Economic terms
            // - Performance payout
            // - Interest payout
            // - Processing metadata
            // - State information
            // - Timestamp (if field exists in proto)
            
            return builder.build();
        } catch (Exception e) {
            log.error("Error converting SwapBlotter to protobuf: {}", swapBlotter.getTradeId(), e);
            throw new RuntimeException("Failed to convert SwapBlotter to protobuf message", e);
        }
    }
}

