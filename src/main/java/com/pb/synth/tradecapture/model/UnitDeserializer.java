package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Custom deserializer for Unit to support both string and object formats.
 * 
 * String format: "SHARES" or "USD" (for convenience)
 * Object format: {"financialUnit": "Shares"} or {"currency": "USD"}
 */
public class UnitDeserializer extends JsonDeserializer<Unit> {
    
    @Override
    public Unit deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        
        if (node.isTextual()) {
            // String format: "SHARES", "USD", etc.
            String value = node.asText().toUpperCase();
            
            // Common currency codes
            if (value.equals("USD") || value.equals("EUR") || value.equals("GBP") || 
                value.equals("JPY") || value.equals("CAD") || value.equals("AUD") ||
                value.length() == 3 && value.matches("[A-Z]{3}")) {
                // Assume it's a currency code
                return Unit.builder()
                    .currency(value)
                    .build();
            } else {
                // Assume it's a financial unit
                return Unit.builder()
                    .financialUnit(value)
                    .build();
            }
        } else if (node.isObject()) {
            // Object format: {"currency": "USD"} or {"financialUnit": "Shares"}
            String currency = node.has("currency") ? node.get("currency").asText() : null;
            String financialUnit = node.has("financialUnit") ? node.get("financialUnit").asText() : null;
            
            return Unit.builder()
                .currency(currency)
                .financialUnit(financialUnit)
                .build();
        }
        
        throw new IOException("Unit must be either a string or an object");
    }
}


