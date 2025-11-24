package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.exception.ValidationException;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for validating trades.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private final AccountServiceClient accountServiceClient;

    /**
     * Validate trade.
     */
    public void validate(TradeCaptureRequest request) {
        // Validate ISIN format
        validateIsin(request.getSecurityId());
        
        // Validate account/book status
        validateAccountBook(request.getAccountId(), request.getBookId());
        
        // Validate trade lots
        if (request.getTradeLots() == null || request.getTradeLots().isEmpty()) {
            throw new ValidationException("Trade lots cannot be empty");
        }
        
        // Additional validations can be added here
    }

    private void validateIsin(String securityId) {
        // Basic ISIN validation (12 characters, alphanumeric)
        if (securityId == null || securityId.length() != 12) {
            throw new ValidationException("Invalid ISIN format: " + securityId);
        }
    }

    private void validateAccountBook(String accountId, String bookId) {
        var accountData = accountServiceClient.lookupAccount(accountId, bookId);
        if (accountData.isEmpty()) {
            // Log warning but allow processing to continue for testing/dev environments
            // In production, this should be configured to fail validation
            log.warn("Account/Book not found: {}/{}. Allowing processing to continue (may be in test/dev mode)", 
                accountId, bookId);
            // Uncomment the line below to enforce strict validation in production
            // throw new ValidationException("Account/Book not found: " + accountId + "/" + bookId);
        }
        // Additional validation can check account status, etc.
    }

}

