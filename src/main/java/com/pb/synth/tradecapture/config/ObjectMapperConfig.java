package com.pb.synth.tradecapture.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pb.synth.tradecapture.model.Unit;
import com.pb.synth.tradecapture.model.UnitDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for ObjectMapper.
 */
@Configuration
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Register custom deserializer for Unit to support both string and object formats
        SimpleModule unitModule = new SimpleModule();
        unitModule.addDeserializer(Unit.class, new UnitDeserializer());
        mapper.registerModule(unitModule);
        
        return mapper;
    }
}

