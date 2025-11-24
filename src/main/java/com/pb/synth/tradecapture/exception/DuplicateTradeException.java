package com.pb.synth.tradecapture.exception;

/**
 * Exception thrown when a duplicate trade is detected.
 */
public class DuplicateTradeException extends RuntimeException {
    
    public DuplicateTradeException(String message) {
        super(message);
    }
    
    public DuplicateTradeException(String message, Throwable cause) {
        super(message, cause);
    }
}

