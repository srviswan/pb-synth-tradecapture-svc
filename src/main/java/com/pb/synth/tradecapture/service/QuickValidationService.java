package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for quick validation of trade requests.
 * Performs syntax and format validation only (no enrichment, rules, or database checks).
 * Used for immediate feedback in manual entry and file upload scenarios.
 */
@Service
@Slf4j
public class QuickValidationService {
    
    /**
     * Validation result containing validation status and errors.
     */
    public static class ValidationResult {
        private final boolean passed;
        private final List<ValidationError> errors;
        
        public ValidationResult(boolean passed, List<ValidationError> errors) {
            this.passed = passed;
            this.errors = errors != null ? errors : new ArrayList<>();
        }
        
        public boolean isPassed() {
            return passed;
        }
        
        public List<ValidationError> getErrors() {
            return errors;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, new ArrayList<>());
        }
        
        public static ValidationResult failure(List<ValidationError> errors) {
            return new ValidationResult(false, errors);
        }
    }
    
    /**
     * Validation error with field and message.
     */
    public static class ValidationError {
        private final String field;
        private final String message;
        
        public ValidationError(String field, String message) {
            this.field = field;
            this.message = message;
        }
        
        public String getField() {
            return field;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    /**
     * Perform quick validation on a trade request.
     * Only checks syntax, required fields, and basic format.
     * Does NOT perform enrichment, rules evaluation, or database checks.
     * 
     * @param request The trade capture request
     * @return Validation result
     */
    public ValidationResult validateQuick(TradeCaptureRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Required fields validation
        if (request.getTradeId() == null || request.getTradeId().trim().isEmpty()) {
            errors.add(new ValidationError("tradeId", "Trade ID is required"));
        }
        
        if (request.getAccountId() == null || request.getAccountId().trim().isEmpty()) {
            errors.add(new ValidationError("accountId", "Account ID is required"));
        }
        
        if (request.getBookId() == null || request.getBookId().trim().isEmpty()) {
            errors.add(new ValidationError("bookId", "Book ID is required"));
        }
        
        if (request.getSecurityId() == null || request.getSecurityId().trim().isEmpty()) {
            errors.add(new ValidationError("securityId", "Security ID is required"));
        }
        
        // Trade date validation
        if (request.getTradeDate() == null) {
            errors.add(new ValidationError("tradeDate", "Trade date is required"));
        } else {
            // Check if trade date is not in the future
            if (request.getTradeDate().isAfter(LocalDate.now())) {
                errors.add(new ValidationError("tradeDate", "Trade date cannot be in the future"));
            }
        }
        
        // Trade lots validation
        if (request.getTradeLots() == null || request.getTradeLots().isEmpty()) {
            errors.add(new ValidationError("tradeLots", "At least one trade lot is required"));
        } else {
            // Validate each trade lot has required fields
            for (int i = 0; i < request.getTradeLots().size(); i++) {
                var lot = request.getTradeLots().get(i);
                if (lot.getPriceQuantity() == null || lot.getPriceQuantity().isEmpty()) {
                    errors.add(new ValidationError("tradeLots[" + i + "].priceQuantity", 
                        "Trade lot must have at least one price/quantity"));
                }
            }
        }
        
        // Counterparty validation
        if (request.getCounterpartyIds() == null || request.getCounterpartyIds().isEmpty()) {
            errors.add(new ValidationError("counterpartyIds", "At least one counterparty is required"));
        }
        
        // Format validation
        if (request.getTradeId() != null && request.getTradeId().length() > 100) {
            errors.add(new ValidationError("tradeId", "Trade ID must be 100 characters or less"));
        }
        
        if (request.getAccountId() != null && request.getAccountId().length() > 50) {
            errors.add(new ValidationError("accountId", "Account ID must be 50 characters or less"));
        }
        
        if (request.getBookId() != null && request.getBookId().length() > 50) {
            errors.add(new ValidationError("bookId", "Book ID must be 50 characters or less"));
        }
        
        if (request.getSecurityId() != null && request.getSecurityId().length() > 50) {
            errors.add(new ValidationError("securityId", "Security ID must be 50 characters or less"));
        }
        
        return errors.isEmpty() 
            ? ValidationResult.success() 
            : ValidationResult.failure(errors);
    }
}


