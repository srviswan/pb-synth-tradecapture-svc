package com.pb.synth.tradecapture;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application for PB Synthetic Trade Capture Service.
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class TradeCaptureServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeCaptureServiceApplication.class, args);
    }
}

