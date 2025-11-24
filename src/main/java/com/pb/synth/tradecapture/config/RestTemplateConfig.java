package com.pb.synth.tradecapture.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for RestTemplate with timeout enforcement.
 */
@Configuration
public class RestTemplateConfig {
    
    @Value("${services.security-master.timeout:5000}")
    private int defaultTimeout;
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(defaultTimeout);
        factory.setReadTimeout(defaultTimeout * 2); // Read timeout is typically 2x connect timeout
        
        return builder
            .requestFactory(() -> factory)
            .setConnectTimeout(Duration.ofMillis(defaultTimeout))
            .setReadTimeout(Duration.ofMillis(defaultTimeout * 2))
            .build();
    }
}

