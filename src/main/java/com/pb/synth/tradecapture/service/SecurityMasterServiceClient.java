package com.pb.synth.tradecapture.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Client for SecurityMasterService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityMasterServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.security-master.url}")
    private String baseUrl;

    @Value("${services.security-master.timeout:5000}")
    private int timeout;

    /**
     * Lookup security by security ID.
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> lookupSecurity(String securityId) {
        try {
            String url = baseUrl + "/api/v1/securities/" + securityId;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.error("Error looking up security: {}", securityId, e);
            return Optional.empty();
        }
    }
}

