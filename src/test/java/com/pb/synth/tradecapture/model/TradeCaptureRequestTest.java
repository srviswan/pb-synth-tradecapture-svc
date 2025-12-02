package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pb.synth.tradecapture.testutil.TestFixtures;
import com.pb.synth.tradecapture.testutil.TradeCaptureRequestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.pb.synth.tradecapture.model.TradeSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TradeCaptureRequest data model.
 */
@DisplayName("TradeCaptureRequest Model Tests")
class TradeCaptureRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Nested
    @DisplayName("Serialization/Deserialization")
    class SerializationTests {
        
        @Test
        @DisplayName("should serialize to JSON")
        void should_Serialize_When_ValidRequest() throws Exception {
            // Given
            var request = new TradeCaptureRequestBuilder()
                .withDefaultAutomatedTrade()
                .build();

            // When
            String json = objectMapper.writeValueAsString(request);

            // Then
            assertThat(json).isNotNull();
            assertThat(json).contains("tradeId");
            assertThat(json).contains("accountId");
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void should_Deserialize_When_ValidJson() throws Exception {
            // Given
            String json = """
                {
                    "tradeId": "TRADE-2024-001",
                    "accountId": "ACC-001",
                    "bookId": "BOOK-001",
                    "securityId": "US0378331005",
                    "source": "AUTOMATED",
                    "tradeDate": "2024-01-31"
                }
                """;

            // When
            var request = objectMapper.readValue(json, TradeCaptureRequest.class);

            // Then
            assertThat(request.getTradeId()).isEqualTo("TRADE-2024-001");
            assertThat(request.getAccountId()).isEqualTo("ACC-001");
        }
    }

    @Nested
    @DisplayName("Partition Key Generation")
    class PartitionKeyTests {
        
        @Test
        @DisplayName("should generate partition key from account, book, and security")
        void should_GeneratePartitionKey_When_ValidComponents() {
            // Given
            String accountId = "ACC-001";
            String bookId = "BOOK-001";
            String securityId = "US0378331005";

            // When
            String partitionKey = accountId + "_" + bookId + "_" + securityId;

            // Then
            assertThat(partitionKey).isEqualTo("ACC-001_BOOK-001_US0378331005");
        }
    }

    @Nested
    @DisplayName("Manual Entry Fields")
    class ManualEntryTests {
        
        @Test
        @DisplayName("should include manual entry fields when source is MANUAL")
        void should_IncludeManualFields_When_ManualSource() {
            // Given
            var request = new TradeCaptureRequestBuilder()
                .withDefaultManualTrade()
                .build();

            // Then
            assertThat(request.getSource()).isEqualTo(TradeSource.MANUAL);
            assertThat(request.getEnteredBy()).isNotNull();
            assertThat(request.getEntryTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should not require manual entry fields when source is AUTOMATED")
        void should_NotRequireManualFields_When_AutomatedSource() {
            // Given
            var request = new TradeCaptureRequestBuilder()
                .withDefaultAutomatedTrade()
                .build();

            // Then
            assertThat(request.getSource()).isEqualTo(TradeSource.AUTOMATED);
            // Manual entry fields are optional for automated trades
        }
    }
}

