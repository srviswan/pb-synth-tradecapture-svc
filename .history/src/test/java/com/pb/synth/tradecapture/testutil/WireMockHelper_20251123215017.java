package com.pb.synth.tradecapture.testutil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Helper class for setting up WireMock in tests.
 */
public class WireMockHelper {
    
    private WireMockServer wireMockServer;
    
    public WireMockHelper() {
        this.wireMockServer = new WireMockServer(
            WireMockConfiguration.options().dynamicPort()
        );
    }
    
    public void start() {
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }
    
    public void stop() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }
    
    public int getPort() {
        return wireMockServer.port();
    }
    
    public WireMockServer getServer() {
        return wireMockServer;
    }
    
    /**
     * Sets up a mock response for SecurityMasterService lookup.
     */
    public void mockSecurityLookup(String securityId, Map<String, Object> securityData) {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/api/v1/securities/" + securityId))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(convertToJson(securityData)))
        );
    }
    
    /**
     * Sets up a mock response for SecurityMasterService when security not found.
     */
    public void mockSecurityNotFound(String securityId) {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/api/v1/securities/" + securityId))
                .willReturn(aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":\"Security not found\"}"))
        );
    }
    
    /**
     * Sets up a mock response for AccountService lookup.
     */
    public void mockAccountLookup(String accountId, String bookId, Map<String, Object> accountData) {
        wireMockServer.stubFor(
            get(urlPathMatching("/api/v1/accounts/" + accountId + "/books/" + bookId))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(convertToJson(accountData)))
        );
    }
    
    /**
     * Sets up a mock response for AccountService when account not found.
     */
    public void mockAccountNotFound(String accountId, String bookId) {
        wireMockServer.stubFor(
            get(urlPathMatching("/api/v1/accounts/" + accountId + "/books/" + bookId))
                .willReturn(aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":\"Account not found\"}"))
        );
    }
    
    /**
     * Sets up a mock response for Rule Management Service - create rule.
     */
    public void mockRuleCreation(Map<String, Object> rule) {
        wireMockServer.stubFor(
            post(urlPathEqualTo("/api/v1/rules/economic"))
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(convertToJson(rule)))
        );
    }
    
    /**
     * Sets up a mock response for Rule Management Service - get rule.
     */
    public void mockRuleRetrieval(String ruleId, Map<String, Object> rule) {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/api/v1/rules/" + ruleId))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(convertToJson(rule)))
        );
    }
    
    /**
     * Sets up a mock response for Rule Management Service - list rules.
     */
    public void mockRuleList(List<Map<String, Object>> rules) {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/api/v1/rules"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(convertToJson(rules)))
        );
    }
    
    /**
     * Sets up a mock response for Approval Workflow Service.
     */
    public void mockApprovalSubmission(String tradeId, Map<String, Object> approvalResponse) {
        wireMockServer.stubFor(
            post(urlPathEqualTo("/api/v1/approvals"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(convertToJson(approvalResponse)))
        );
    }
    
    /**
     * Sets up a mock timeout response.
     */
    public void mockTimeout(String path) {
        wireMockServer.stubFor(
            get(urlPathMatching(path))
                .willReturn(aResponse()
                    .withFixedDelay(10000)
                    .withStatus(200))
        );
    }
    
    /**
     * Sets up a mock error response.
     */
    public void mockError(String path, int statusCode) {
        wireMockServer.stubFor(
            get(urlPathMatching(path))
                .willReturn(aResponse()
                    .withStatus(statusCode)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":\"Service error\"}"))
        );
    }
    
    private String convertToJson(Object obj) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}

