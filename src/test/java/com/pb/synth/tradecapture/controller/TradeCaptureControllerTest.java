package com.pb.synth.tradecapture.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.synth.tradecapture.service.TradeCaptureService;
import com.pb.synth.tradecapture.testutil.TradeCaptureRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for TradeCaptureController using MockMvc.
 */
@WebMvcTest(TradeCaptureController.class)
@DisplayName("TradeCaptureController Unit Tests")
class TradeCaptureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradeCaptureService tradeCaptureService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Setup common mocks
    }

    @Nested
    @DisplayName("POST /api/v1/trades/capture")
    class CaptureTradeTests {
        
        @Test
        @DisplayName("should capture trade successfully")
        void should_CaptureTrade_When_ValidRequest() throws Exception {
            // Given
            var request = new TradeCaptureRequestBuilder()
                .withDefaultAutomatedTrade()
                .build();
            
            var response = Map.of(
                "tradeId", "TRADE-2024-001",
                "status", "SUCCESS"
            );
            
            // when(tradeCaptureService.processTrade(any())).thenReturn(response);

            // When/Then
            mockMvc.perform(post("/api/v1/trades/capture")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tradeId").exists());
        }

        @Test
        @DisplayName("should return 400 when request is invalid")
        void should_Return400_When_InvalidRequest() throws Exception {
            // Given
            var invalidRequest = Map.of("tradeId", "INVALID");

            // When/Then
            mockMvc.perform(post("/api/v1/trades/capture")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 500 when service error occurs")
        void should_Return500_When_ServiceError() throws Exception {
            // Given
            var request = new TradeCaptureRequestBuilder()
                .withDefaultAutomatedTrade()
                .build();
            
            // when(tradeCaptureService.processTrade(any()))
            //     .thenThrow(new RuntimeException("Service error"));

            // When/Then
            mockMvc.perform(post("/api/v1/trades/capture")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/trades/capture/async")
    class AsyncCaptureTradeTests {
        
        @Test
        @DisplayName("should accept async trade capture request")
        void should_AcceptAsyncRequest_When_ValidRequest() throws Exception {
            // Given
            var request = new TradeCaptureRequestBuilder()
                .withDefaultAutomatedTrade()
                .build();
            
            var response = Map.of(
                "jobId", "JOB-2024-001",
                "status", "ACCEPTED"
            );
            
            // when(tradeCaptureService.processTradeAsync(any())).thenReturn(response);

            // When/Then
            mockMvc.perform(post("/api/v1/trades/capture/async")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/trades/capture/batch")
    class BatchCaptureTradeTests {
        
        @Test
        @DisplayName("should process batch trade capture")
        void should_ProcessBatch_When_ValidRequest() throws Exception {
            // Given
            var request = Map.of(
                "trades", List.of(
                    new TradeCaptureRequestBuilder().withDefaultAutomatedTrade().build()
                )
            );
            
            var response = Map.of(
                "processed", 1,
                "failed", 0
            );
            
            // when(tradeCaptureService.processBatch(any())).thenReturn(response);

            // When/Then
            mockMvc.perform(post("/api/v1/trades/capture/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").exists());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/trades/manual-entry")
    class ManualEntryTests {
        
        @Test
        @DisplayName("should accept manual trade entry")
        void should_AcceptManualEntry_When_ValidRequest() throws Exception {
            // Given
            var request = new TradeCaptureRequestBuilder()
                .withDefaultManualTrade()
                .build();
            
            var response = Map.of(
                "tradeId", "TRADE-2024-002",
                "status", "PENDING_APPROVAL"
            );
            
            // when(tradeCaptureService.processManualEntry(any())).thenReturn(response);

            // When/Then
            mockMvc.perform(post("/api/v1/trades/manual-entry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        }
    }

    @Nested
    @DisplayName("Request Validation")
    class RequestValidationTests {
        
        @Test
        @DisplayName("should validate required fields")
        void should_ValidateRequiredFields_When_MissingFields() throws Exception {
            // Given
            var invalidRequest = Map.of("tradeId", "TRADE-001");

            // When/Then
            mockMvc.perform(post("/api/v1/trades/capture")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should validate trade source enum")
        void should_ValidateTradeSource_When_InvalidSource() throws Exception {
            // Given
            var request = new TradeCaptureRequestBuilder()
                .withDefaultAutomatedTrade()
                .withSource("INVALID")
                .build();

            // When/Then
            mockMvc.perform(post("/api/v1/trades/capture")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Error Response Format")
    class ErrorResponseTests {
        
        @Test
        @DisplayName("should return standardized error response")
        void should_ReturnStandardError_When_ErrorOccurs() throws Exception {
            // Given
            var request = new TradeCaptureRequestBuilder()
                .withDefaultAutomatedTrade()
                .build();
            
            // when(tradeCaptureService.processTrade(any()))
            //     .thenThrow(new ValidationException("Validation failed"));

            // When/Then
            mockMvc.perform(post("/api/v1/trades/capture")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").exists())
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.error.timestamp").exists());
        }
    }
}

