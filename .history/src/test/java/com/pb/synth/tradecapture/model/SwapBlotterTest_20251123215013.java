    package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.synth.tradecapture.testutil.SwapBlotterBuilder;
import com.pb.synth.tradecapture.testutil.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SwapBlotter data model.
 */
@DisplayName("SwapBlotter Model Tests")
class SwapBlotterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {
        
        @Test
        @DisplayName("should construct SwapBlotter with all required fields")
        void should_Construct_When_AllFieldsProvided() {
            // Given
            var blotter = new SwapBlotterBuilder()
                .withDefaultApprovedTrade()
                .build();

            // Then
            assertThat(blotter.get("tradeId")).isNotNull();
            assertThat(blotter.get("partitionKey")).isNotNull();
            assertThat(blotter.get("enrichmentStatus")).isNotNull();
            assertThat(blotter.get("workflowStatus")).isNotNull();
        }

        @Test
        @DisplayName("should include contract with economic terms")
        void should_IncludeContract_When_Constructed() {
            // Given
            var contract = Map.of(
                "identifier", TestFixtures.createSampleSecurity(),
                "economicTerms", TestFixtures.createSampleEconomicTerms()
            );
            
            var blotter = new SwapBlotterBuilder()
                .withDefaultApprovedTrade()
                .withContract(contract)
                .build();

            // Then
            assertThat(blotter.get("contract")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Serialization/Deserialization")
    class SerializationTests {
        
        @Test
        @DisplayName("should serialize to JSON")
        void should_Serialize_When_ValidBlotter() throws Exception {
            // Given
            var blotter = new SwapBlotterBuilder()
                .withDefaultApprovedTrade()
                .build();

            // When
            String json = objectMapper.writeValueAsString(blotter);

            // Then
            assertThat(json).isNotNull();
            assertThat(json).contains("tradeId");
            assertThat(json).contains("workflowStatus");
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void should_Deserialize_When_ValidJson() throws Exception {
            // Given
            String json = """
                {
                    "tradeId": "TRADE-2024-001",
                    "partitionKey": "ACC-001_BOOK-001_US0378331005",
                    "enrichmentStatus": "COMPLETE",
                    "workflowStatus": "APPROVED"
                }
                """;

            // When
            var blotter = objectMapper.readValue(json, Map.class);

            // Then
            assertThat(blotter.get("tradeId")).isEqualTo("TRADE-2024-001");
            assertThat(blotter.get("workflowStatus")).isEqualTo("APPROVED");
        }
    }

    @Nested
    @DisplayName("Workflow Status")
    class WorkflowStatusTests {
        
        @Test
        @DisplayName("should set workflow status to APPROVED")
        void should_SetApproved_When_Approved() {
            // Given
            var blotter = new SwapBlotterBuilder()
                .withDefaultApprovedTrade()
                .build();

            // Then
            assertThat(blotter.get("workflowStatus")).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("should set workflow status to PENDING_APPROVAL")
        void should_SetPendingApproval_When_Pending() {
            // Given
            var blotter = new SwapBlotterBuilder()
                .withDefaultPendingApprovalTrade()
                .build();

            // Then
            assertThat(blotter.get("workflowStatus")).isEqualTo("PENDING_APPROVAL");
        }
    }

    @Nested
    @DisplayName("Enrichment Status")
    class EnrichmentStatusTests {
        
        @Test
        @DisplayName("should set enrichment status to COMPLETE")
        void should_SetComplete_When_EnrichmentComplete() {
            // Given
            var blotter = new SwapBlotterBuilder()
                .withEnrichmentStatus("COMPLETE")
                .build();

            // Then
            assertThat(blotter.get("enrichmentStatus")).isEqualTo("COMPLETE");
        }

        @Test
        @DisplayName("should set enrichment status to PARTIAL")
        void should_SetPartial_When_EnrichmentPartial() {
            // Given
            var blotter = new SwapBlotterBuilder()
                .withEnrichmentStatus("PARTIAL")
                .build();

            // Then
            assertThat(blotter.get("enrichmentStatus")).isEqualTo("PARTIAL");
        }
    }
}

