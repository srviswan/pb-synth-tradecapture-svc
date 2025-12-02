package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request model for trade capture.
 * Represents incoming trade lots from upstream systems.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeCaptureRequest {

    @NotBlank(message = "tradeId is required")
    @JsonProperty("tradeId")
    private String tradeId;

    @JsonProperty("idempotencyKey")
    private String idempotencyKey;

    @NotBlank(message = "accountId is required")
    @JsonProperty("accountId")
    private String accountId;

    @NotBlank(message = "bookId is required")
    @JsonProperty("bookId")
    private String bookId;

    @NotBlank(message = "securityId is required")
    @JsonProperty("securityId")
    private String securityId;

    @NotNull(message = "source is required")
    @JsonProperty("source")
    private TradeSource source;

    @NotNull(message = "tradeLots is required")
    @JsonProperty("tradeLots")
    private List<TradeLot> tradeLots;

    @NotNull(message = "tradeDate is required")
    @JsonProperty("tradeDate")
    private LocalDate tradeDate;

    @JsonProperty("tradeTimestamp")
    private ZonedDateTime tradeTimestamp; // Optional - timestamp when trade was executed

    @JsonProperty("bookingTimestamp")
    private ZonedDateTime bookingTimestamp; // Timestamp when trade was booked in upstream system (for out-of-order detection)

    @NotNull(message = "counterpartyIds is required")
    @JsonProperty("counterpartyIds")
    private List<String> counterpartyIds;

    @JsonProperty("sequenceNumber")
    private Long sequenceNumber; // Incremental sequence number (1, 2, 3, ...) assigned by upstream system

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    // Manual entry specific fields
    @JsonProperty("enteredBy")
    private String enteredBy;

    @JsonProperty("entryTimestamp")
    private LocalDate entryTimestamp;

    /**
     * Computed partition key: {accountId}_{bookId}_{securityId}
     */
    @JsonIgnore
    public String getPartitionKey() {
        return String.format("%s_%s_%s", accountId, bookId, securityId);
    }

    /**
     * Get idempotency key (idempotencyKey if provided, otherwise tradeId)
     */
    public String getIdempotencyKey() {
        return idempotencyKey != null ? idempotencyKey : tradeId;
    }

    /**
     * Get sequence number (incremental, assigned by upstream system).
     * Sequence numbers are incremental (1, 2, 3, 4, ...) and should be provided by the upstream system.
     * If not provided, returns null (sequence validation will be skipped).
     */
    public Long getSequenceNumber() {
        return sequenceNumber;
    }
    
    /**
     * Get booking timestamp (when trade was booked in upstream system).
     * Used for out-of-order detection and time window checks.
     * Falls back to tradeTimestamp if bookingTimestamp is not provided.
     */
    public ZonedDateTime getBookingTimestamp() {
        if (bookingTimestamp != null) {
            return bookingTimestamp;
        }
        // Fallback to tradeTimestamp if bookingTimestamp not provided
        return tradeTimestamp;
    }
}

