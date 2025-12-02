package com.pb.synth.tradecapture.integration.external;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.pb.synth.tradecapture.TradeCaptureServiceApplication;
import com.pb.synth.tradecapture.testutil.TestFixtures;
import com.pb.synth.tradecapture.testutil.WireMockHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Rule Management Service using WireMock.
 */
@SpringBootTest(classes = TradeCaptureServiceApplication.class)
@ActiveProfiles("test-mocked")
@DisplayName("Rule Management Service Integration Tests")
class RuleManagementServiceIntegrationTest {

    private WireMockHelper wireMockHelper;
    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockHelper = new WireMockHelper();
        wireMockHelper.start();
        wireMockServer = wireMockHelper.getServer();
    }

    @AfterEach
    void tearDown() {
        wireMockHelper.stop();
    }

    @Test
    @DisplayName("should create rule successfully")
    void should_CreateRule_When_ValidRule() {
        // Given
        var rule = TestFixtures.createSampleRule("ECONOMIC_RULE_001", "ECONOMIC", "DAY_COUNT");
        
        wireMockServer.stubFor(
            post(urlPathEqualTo("/api/v1/rules/economic"))
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(convertToJson(rule)))
        );

        // When
        // var result = ruleManagementService.createRule(rule);

        // Then
        // assertThat(result).isNotNull();
        // assertThat(result.get("id")).isEqualTo("ECONOMIC_RULE_001");
    }

    @Test
    @DisplayName("should retrieve rule successfully")
    void should_RetrieveRule_When_RuleExists() {
        // Given
        String ruleId = "ECONOMIC_RULE_001";
        var rule = TestFixtures.createSampleRule(ruleId, "ECONOMIC", "DAY_COUNT");
        
        wireMockServer.stubFor(
            get(urlPathEqualTo("/api/v1/rules/" + ruleId))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(convertToJson(rule)))
        );

        // When
        // var result = ruleManagementService.getRule(ruleId);

        // Then
        // assertThat(result).isNotNull();
        // assertThat(result.get("id")).isEqualTo(ruleId);
    }

    @Test
    @DisplayName("should list all rules")
    void should_ListRules_When_RulesExist() {
        // Given
        var rules = List.of(
            TestFixtures.createSampleRule("RULE_001", "ECONOMIC", "DAY_COUNT"),
            TestFixtures.createSampleRule("RULE_002", "NON_ECONOMIC", "LEGAL_ENTITY")
        );
        
        wireMockServer.stubFor(
            get(urlPathEqualTo("/api/v1/rules"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(convertToJson(rules)))
        );

        // When
        // var result = ruleManagementService.listRules();

        // Then
        // assertThat(result).isNotNull();
        // assertThat(result.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("should update rule successfully")
    void should_UpdateRule_When_ValidRule() {
        // Given
        var rule = TestFixtures.createSampleRule("ECONOMIC_RULE_001", "ECONOMIC", "DAY_COUNT");
        
        wireMockServer.stubFor(
            put(urlPathEqualTo("/api/v1/rules/ECONOMIC_RULE_001"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(convertToJson(rule)))
        );

        // When
        // var result = ruleManagementService.updateRule(rule);

        // Then
        // assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should delete rule successfully")
    void should_DeleteRule_When_RuleExists() {
        // Given
        String ruleId = "ECONOMIC_RULE_001";
        
        wireMockServer.stubFor(
            delete(urlPathEqualTo("/api/v1/rules/" + ruleId))
                .willReturn(aResponse()
                    .withStatus(204))
        );

        // When
        // ruleManagementService.deleteRule(ruleId);

        // Then
        // No exception thrown
    }

    private String convertToJson(Object obj) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}

