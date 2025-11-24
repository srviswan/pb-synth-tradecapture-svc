package com.pb.synth.tradecapture.testutil;

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
    private List<Object> tradeLots;
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

    public TradeCaptureRequestBuilder withTradeLots(List<Object> tradeLots) {
        this.tradeLots = tradeLots;
        return this;
    }

    public TradeCaptureRequestBuilder addTradeLot(Object tradeLot) {
        this.tradeLots.add(tradeLot);
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
        return this
            .withTradeId("TRADE-2024-001")
            .withAccountId("ACC-001")
            .withBookId("BOOK-001")
            .withSecurityId("US0378331005")
            .withSource("AUTOMATED")
            .withTradeDate(LocalDate.now())
            .addCounterpartyId("CPTY-001")
            .addCounterpartyId("CPTY-002");
    }

    public TradeCaptureRequestBuilder withDefaultManualTrade() {
        return this
            .withTradeId("TRADE-2024-002")
            .withAccountId("ACC-001")
            .withBookId("BOOK-001")
            .withSecurityId("US0378331005")
            .withSource("MANUAL")
            .withTradeDate(LocalDate.now())
            .addCounterpartyId("CPTY-001")
            .addCounterpartyId("CPTY-002")
            .withEnteredBy("USER-001")
            .withEntryTimestamp(LocalDate.now());
    }

    public Map<String, Object> build() {
        Map<String, Object> request = new HashMap<>();
        if (tradeId != null) request.put("tradeId", tradeId);
        if (accountId != null) request.put("accountId", accountId);
        if (bookId != null) request.put("bookId", bookId);
        if (securityId != null) request.put("securityId", securityId);
        if (source != null) request.put("source", source);
        if (!tradeLots.isEmpty()) request.put("tradeLots", tradeLots);
        if (tradeDate != null) request.put("tradeDate", tradeDate.toString());
        if (!counterpartyIds.isEmpty()) request.put("counterpartyIds", counterpartyIds);
        if (!metadata.isEmpty()) request.put("metadata", metadata);
        if (enteredBy != null) request.put("enteredBy", enteredBy);
        if (entryTimestamp != null) request.put("entryTimestamp", entryTimestamp.toString());
        return request;
    }
}

