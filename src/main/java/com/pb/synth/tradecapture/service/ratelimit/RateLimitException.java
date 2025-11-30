package com.pb.synth.tradecapture.service.ratelimit;

/**
 * Exception thrown when rate limit is exceeded.
 */
public class RateLimitException extends RuntimeException {
    
    private final String partitionKey;
    private final String rateLimitType; // "GLOBAL" or "PARTITION"
    
    public RateLimitException(String partitionKey, String rateLimitType, String message) {
        super(message);
        this.partitionKey = partitionKey;
        this.rateLimitType = rateLimitType;
    }
    
    public String getPartitionKey() {
        return partitionKey;
    }
    
    public String getRateLimitType() {
        return rateLimitType;
    }
}

