package com.pb.synth.tradecapture.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenTelemetry distributed tracing.
 * 
 * OpenTelemetry is configured via Spring Boot Actuator's tracing support.
 * Configuration is done through application.yml properties:
 * - management.tracing.sampling.probability
 * - otel.service.name
 * - otel.exporter.otlp.endpoint
 * 
 * Micrometer Tracing Bridge for OpenTelemetry is included as a dependency
 * and will automatically configure tracing when enabled.
 */
@Configuration
@ConditionalOnProperty(name = "management.tracing.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OpenTelemetryConfig {

    // OpenTelemetry is auto-configured by Spring Boot Actuator
    // when micrometer-tracing-bridge-otel is on the classpath.
    // This class serves as documentation and can be extended
    // if custom configuration is needed.
    
    public OpenTelemetryConfig() {
        log.info("OpenTelemetry tracing enabled via Micrometer Tracing Bridge");
    }
}

