package com.pb.synth.tradecapture.testutil;

import com.pb.synth.tradecapture.model.Identifier;
import com.pb.synth.tradecapture.model.Price;
import com.pb.synth.tradecapture.model.PriceQuantity;
import com.pb.synth.tradecapture.model.Quantity;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.model.TradeLot;
import com.pb.synth.tradecapture.model.TradeSource;
import com.pb.synth.tradecapture.model.Unit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for creating TradeCaptureRequest test data.
 */
public class TradeCaptureRequestBuilder {
    private String tradeId;
    private String accountId;
    private String bookId;
    private String securityId;
    private String source;
    private List<TradeLot> tradeLots;
    private LocalDate tradeDate;
    private List<String> counterpartyIds;
    private Map<String, Object> metadata;
    private String enteredBy;
    private LocalDate entryTimestamp;

    public TradeCaptureRequestBuilder() {
        this.tradeLots = new ArrayList<>();
        this.counterpartyIds = new ArrayList<>();
        this.metadata = new HashMap<>();
    }

    public TradeCaptureRequestBuilder withTradeId(String tradeId) {
        this.tradeId = tradeId;
        return this;
    }

    public TradeCaptureRequestBuilder withAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public TradeCaptureRequestBuilder withBookId(String bookId) {
        this.bookId = bookId;
        return this;
    }

    public TradeCaptureRequestBuilder withSecurityId(String securityId) {
        this.securityId = securityId;
        return this;
    }

    public TradeCaptureRequestBuilder withSource(String source) {
        this.source = source;
        return this;
    }

    public TradeCaptureRequestBuilder withTradeLots(List<TradeLot> tradeLots) {
        this.tradeLots = tradeLots;
        return this;
    }

    public TradeCaptureRequestBuilder withTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
        return this;
    }

    public TradeCaptureRequestBuilder withCounterpartyIds(List<String> counterpartyIds) {
        this.counterpartyIds = counterpartyIds;
        return this;
    }

    public TradeCaptureRequestBuilder addCounterpartyId(String counterpartyId) {
        this.counterpartyIds.add(counterpartyId);
        return this;
    }

    public TradeCaptureRequestBuilder withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }

    public TradeCaptureRequestBuilder addMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public TradeCaptureRequestBuilder withEnteredBy(String enteredBy) {
        this.enteredBy = enteredBy;
        return this;
    }

    public TradeCaptureRequestBuilder withEntryTimestamp(LocalDate entryTimestamp) {
        this.entryTimestamp = entryTimestamp;
        return this;
    }

    public TradeCaptureRequestBuilder withDefaultAutomatedTrade() {
        // Create a default trade lot
        TradeLot defaultLot = createDefaultTradeLot();
        
        return this
            .withTradeId("TRADE-2024-001")
            .withAccountId("ACC-001")
            .withBookId("BOOK-001")
            .withSecurityId("US0378331005")
            .withSource("AUTOMATED")
            .withTradeDate(LocalDate.now())
            .addTradeLot(defaultLot)
            .addCounterpartyId("CPTY-001")
            .addCounterpartyId("CPTY-002");
    }

    public TradeCaptureRequestBuilder withDefaultManualTrade() {
        // Create a default trade lot
        TradeLot defaultLot = createDefaultTradeLot();
        
        return this
            .withTradeId("TRADE-2024-002")
            .withAccountId("ACC-001")
            .withBookId("BOOK-001")
            .withSecurityId("US0378331005")
            .withSource("MANUAL")
            .withTradeDate(LocalDate.now())
            .addTradeLot(defaultLot)
            .addCounterpartyId("CPTY-001")
            .addCounterpartyId("CPTY-002")
            .withEnteredBy("USER-001")
            .withEntryTimestamp(LocalDate.now());
    }

    public TradeCaptureRequestBuilder addTradeLot(TradeLot tradeLot) {
        this.tradeLots.add(tradeLot);
        return this;
    }

    public TradeCaptureRequest build() {
        TradeSource tradeSource = source != null ? TradeSource.valueOf(source) : TradeSource.AUTOMATED;
        List<TradeLot> lots = new ArrayList<>();
        for (Object lot : tradeLots) {
            if (lot instanceof TradeLot) {
                lots.add((TradeLot) lot);
            }
        }
        
        return TradeCaptureRequest.builder()
            .tradeId(tradeId)
            .accountId(accountId)
            .bookId(bookId)
            .securityId(securityId)
            .source(tradeSource)
            .tradeLots(lots.isEmpty() ? List.of(createDefaultTradeLot()) : lots)
            .tradeDate(tradeDate != null ? tradeDate : LocalDate.now())
            .counterpartyIds(counterpartyIds.isEmpty() ? List.of("CPTY-001", "CPTY-002") : counterpartyIds)
            .metadata(metadata)
            .enteredBy(enteredBy)
            .entryTimestamp(entryTimestamp)
            .build();
    }
    
    private TradeLot createDefaultTradeLot() {
        // Create identifier
        Identifier identifier = Identifier.builder()
            .identifier("LOT-001")
            .identifierType("INTERNAL")
            .build();
        
        // Create price quantity with quantity and price
        PriceQuantity priceQuantity = PriceQuantity.builder()
            .quantity(List.of(
                Quantity.builder()
                    .value(1000.0)
                    .unit(Unit.builder().financialUnit("SHARES").build())
                    .build()
            ))
            .price(List.of(
                Price.builder()
                    .value(100.50)
                    .unit(Unit.builder().currency("USD").build())
                    .build()
            ))
            .build();
        
        return TradeLot.builder()
            .lotIdentifier(List.of(identifier))
            .priceQuantity(List.of(priceQuantity))
            .build();
    }
}

