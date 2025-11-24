package com.pb.synth.tradecapture.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Client for AccountService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.account.url}")
    private String baseUrl;

    @Value("${services.account.timeout:5000}")
    private int timeout;

    /**
     * Lookup account and book.
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> lookupAccount(String accountId, String bookId) {
        try {
            String url = baseUrl + "/api/v1/accounts/" + accountId + "/books/" + bookId;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.error("Error looking up account: {} / {}", accountId, bookId, e);
            return Optional.empty();
        }
    }
}

