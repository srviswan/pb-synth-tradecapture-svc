package com.pb.synth.tradecapture.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.synth.tradecapture.model.SwapBlotter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Webhook publisher for SwapBlotter.
 * Publishes SwapBlotter to configured webhook URLs.
 */
@Component
@ConditionalOnProperty(name = "publishing.webhook.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class WebhookSwapBlotterPublisher implements SwapBlotterPublisher {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${publishing.webhook.urls:#{T(java.util.Collections).emptyList()}}")
    private List<String> webhookUrls;

    @Override
    public void publish(SwapBlotter swapBlotter) {
        if (webhookUrls == null || webhookUrls.isEmpty()) {
            return;
        }

        for (String webhookUrl : webhookUrls) {
            try {
                String json = objectMapper.writeValueAsString(swapBlotter);
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(json, headers);
                
                restTemplate.postForEntity(webhookUrl, entity, String.class);
                log.info("Published SwapBlotter to webhook {}: {}", webhookUrl, swapBlotter.getTradeId());
            } catch (Exception e) {
                log.error("Error publishing SwapBlotter to webhook {}: {}", webhookUrl, e.getMessage());
                // Continue with other webhooks even if one fails
            }
        }
    }
}

