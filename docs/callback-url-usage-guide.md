# Callback URL Usage Guide

## Overview

The `callbackUrl` (provided via `X-Callback-Url` header) is used for **webhook notifications** when async trade processing completes. This enables your application to receive real-time notifications instead of polling the job status endpoint.

## How It Works

### Flow Diagram

```
1. Client sends POST /api/v1/trades/capture
   ├─ Headers: X-Callback-Url: https://your-app.com/webhooks/trade-complete
   └─ Body: TradeCaptureRequest
   
2. Service returns 202 Accepted
   ├─ jobId: "abc-123"
   └─ statusUrl: "/api/v1/trades/jobs/abc-123/status"
   
3. Trade is queued for async processing
   └─ callbackUrl stored in message metadata
   
4. Trade processing completes (success or failure)
   └─ WebhookService sends HTTP POST to callbackUrl
      ├─ Payload: JobStatus + TradeResponse
      └─ Retries: 3 attempts with exponential backoff
```

### Current Implementation

1. **Controller Level** (`TradeCaptureController.java`)
   - Accepts `X-Callback-Url` header (required)
   - Passes to `TradePublishingService.publishTrade()`

2. **Publishing Service** (`TradePublishingService.java`)
   - Stores `callbackUrl` in protobuf message metadata
   - Key: `"callback_url"`

3. **Message Processor** (`TradeMessageProcessor.java`)
   - Extracts `callbackUrl` from message metadata
   - Calls `WebhookService.sendWebhook()` on completion/failure

4. **Webhook Service** (`WebhookService.java`)
   - Sends HTTP POST to `callbackUrl`
   - Retries up to 3 times with exponential backoff
   - Timeout: 30 seconds (configurable)

## Best Practices

### 1. **Always Use HTTPS**

```http
✅ Good:
X-Callback-Url: https://api.yourcompany.com/webhooks/trade-complete

❌ Bad:
X-Callback-Url: http://api.yourcompany.com/webhooks/trade-complete
```

**Why:** Prevents man-in-the-middle attacks and ensures data privacy.

### 2. **Use Dedicated Webhook Endpoints**

```http
✅ Good:
X-Callback-Url: https://api.yourcompany.com/webhooks/trade-capture/complete

❌ Bad:
X-Callback-Url: https://api.yourcompany.com/api/trades/capture
```

**Why:** Separates webhook handling from regular API endpoints, allows different security/rate limiting.

### 3. **Implement Idempotency**

Your webhook endpoint should handle duplicate notifications gracefully:

```java
@PostMapping("/webhooks/trade-complete")
public ResponseEntity<?> handleWebhook(@RequestBody WebhookPayload payload) {
    // Check if already processed
    if (isAlreadyProcessed(payload.getJobId())) {
        return ResponseEntity.ok().build(); // Idempotent - already processed
    }
    
    // Process the webhook
    processTradeCompletion(payload);
    
    // Mark as processed
    markAsProcessed(payload.getJobId());
    
    return ResponseEntity.ok().build();
}
```

**Why:** Webhooks may be retried, so your endpoint must be idempotent.

### 4. **Validate Webhook Signatures (Recommended)**

Add signature validation to ensure webhooks are from the trade capture service:

```java
@PostMapping("/webhooks/trade-complete")
public ResponseEntity<?> handleWebhook(
        @RequestBody WebhookPayload payload,
        @RequestHeader("X-Webhook-Signature") String signature) {
    
    // Validate signature
    if (!isValidSignature(payload, signature)) {
        return ResponseEntity.status(401).build();
    }
    
    // Process webhook
    processTradeCompletion(payload);
    
    return ResponseEntity.ok().build();
}
```

**Note:** Currently, the service doesn't send signatures. Consider adding this feature.

### 5. **Return 2xx Status Codes Quickly**

Your webhook endpoint should:
- Return `200 OK` or `202 Accepted` quickly (< 1 second)
- Process the webhook asynchronously if needed

```java
@PostMapping("/webhooks/trade-complete")
public ResponseEntity<?> handleWebhook(@RequestBody WebhookPayload payload) {
    // Queue for async processing
    asyncProcessor.queue(payload);
    
    // Return immediately
    return ResponseEntity.accepted().build();
}
```

**Why:** Prevents webhook timeouts and retries.

### 6. **Handle All Job Statuses**

The webhook is sent for both success and failure:

```java
@PostMapping("/webhooks/trade-complete")
public ResponseEntity<?> handleWebhook(@RequestBody WebhookPayload payload) {
    String status = payload.getStatus(); // COMPLETED, FAILED, CANCELLED
    
    switch (status) {
        case "COMPLETED":
            handleSuccess(payload);
            break;
        case "FAILED":
            handleFailure(payload);
            break;
        case "CANCELLED":
            handleCancellation(payload);
            break;
    }
    
    return ResponseEntity.ok().build();
}
```

### 7. **Log Webhook Receipts**

Always log webhook receipts for debugging and audit:

```java
@PostMapping("/webhooks/trade-complete")
public ResponseEntity<?> handleWebhook(@RequestBody WebhookPayload payload) {
    log.info("Received webhook: jobId={}, status={}, tradeId={}", 
        payload.getJobId(), payload.getStatus(), payload.getTradeId());
    
    // Process webhook
    processTradeCompletion(payload);
    
    return ResponseEntity.ok().build();
}
```

## Webhook Payload Structure

The webhook sends a JSON payload with the following structure:

```json
{
  "jobId": "abc-123-def-456",
  "status": "COMPLETED",
  "progress": 100,
  "message": "Trade processing completed",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:05Z",
  "tradeId": "TRADE-2024-001",
  "tradeStatus": "SUCCESS",
  "swapBlotter": {
    "tradeId": "TRADE-2024-001",
    "partitionKey": "ACC-001_BOOK-001_US0378331005",
    "enrichmentStatus": "COMPLETE",
    "workflowStatus": "APPROVED",
    "state": {
      "positionState": "FORMED"
    }
  },
  "error": null
}
```

### Failure Payload Example

```json
{
  "jobId": "abc-123-def-456",
  "status": "FAILED",
  "progress": 100,
  "message": "Trade processing failed: Validation error",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:05Z",
  "tradeId": "TRADE-2024-001",
  "tradeStatus": "FAILED",
  "swapBlotter": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid ISIN: INVALID-ISIN"
  }
}
```

## Configuration

### Webhook Timeout

Configure in `application.yml`:

```yaml
webhook:
  timeout-ms: 30000  # 30 seconds (default)
  max-retries: 3     # Number of retry attempts (default)
```

### Environment Variables

```bash
export WEBHOOK_TIMEOUT_MS=30000
export WEBHOOK_MAX_RETRIES=3
```

## Retry Behavior

The webhook service retries failed webhooks with exponential backoff:

- **Attempt 1:** Immediate
- **Attempt 2:** After 1 second
- **Attempt 3:** After 2 seconds

If all retries fail, the error is logged but **does not fail the job**. The job status is still updated, and clients can poll the status endpoint.

## Security Considerations

### 1. **Whitelist Allowed Callback URLs**

Consider implementing a whitelist of allowed callback URLs:

```java
@Value("${webhook.allowed-domains}")
private List<String> allowedDomains;

public void validateCallbackUrl(String callbackUrl) {
    URI uri = URI.create(callbackUrl);
    String host = uri.getHost();
    
    if (!allowedDomains.contains(host)) {
        throw new IllegalArgumentException("Callback URL not whitelisted: " + callbackUrl);
    }
}
```

### 2. **Rate Limiting**

Implement rate limiting on your webhook endpoint to prevent abuse:

```java
@RateLimiter(name = "webhook")
@PostMapping("/webhooks/trade-complete")
public ResponseEntity<?> handleWebhook(@RequestBody WebhookPayload payload) {
    // Process webhook
}
```

### 3. **IP Whitelisting**

If possible, whitelist the trade capture service IPs in your firewall/load balancer.

### 4. **TLS Certificate Validation**

Ensure your webhook endpoint uses valid TLS certificates. The service validates certificates when making HTTPS requests.

## Error Handling

### Webhook Endpoint Errors

If your webhook endpoint returns:
- **4xx (Client Error):** Service will retry (may indicate temporary issue)
- **5xx (Server Error):** Service will retry
- **Timeout:** Service will retry

### Service-Side Errors

If the webhook service fails to send:
- Error is logged
- Job status is still updated
- Client can poll status endpoint as fallback

## Example Implementation

### Spring Boot Webhook Endpoint

```java
@RestController
@RequestMapping("/webhooks")
@Slf4j
public class TradeWebhookController {
    
    private final TradeCompletionService tradeCompletionService;
    
    @PostMapping("/trade-complete")
    public ResponseEntity<?> handleTradeComplete(
            @RequestBody WebhookPayload payload) {
        
        log.info("Received trade completion webhook: jobId={}, status={}", 
            payload.getJobId(), payload.getStatus());
        
        try {
            // Check idempotency
            if (tradeCompletionService.isAlreadyProcessed(payload.getJobId())) {
                log.debug("Webhook already processed: jobId={}", payload.getJobId());
                return ResponseEntity.ok().build();
            }
            
            // Process based on status
            if ("COMPLETED".equals(payload.getStatus())) {
                tradeCompletionService.handleSuccess(payload);
            } else if ("FAILED".equals(payload.getStatus())) {
                tradeCompletionService.handleFailure(payload);
            }
            
            // Mark as processed
            tradeCompletionService.markAsProcessed(payload.getJobId());
            
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("Error processing webhook: jobId={}", payload.getJobId(), e);
            // Return 500 to trigger retry
            return ResponseEntity.status(500).build();
        }
    }
}
```

### Webhook Payload Model

```java
@Data
public class WebhookPayload {
    private String jobId;
    private String status; // COMPLETED, FAILED, CANCELLED
    private Integer progress;
    private String message;
    private String createdAt;
    private String updatedAt;
    private String tradeId;
    private String tradeStatus;
    private SwapBlotter swapBlotter;
    private ErrorDetail error;
}
```

## Testing

### Test Webhook Endpoint Locally

Use a tool like [ngrok](https://ngrok.com/) to expose your local webhook endpoint:

```bash
# Start ngrok
ngrok http 8080

# Use the ngrok URL as callback
curl -X POST http://localhost:8080/api/v1/trades/capture \
  -H "X-Callback-Url: https://abc123.ngrok.io/webhooks/trade-complete" \
  -H "Content-Type: application/json" \
  -d '{"tradeId": "TEST-001", ...}'
```

### Mock Webhook Endpoint

Use [webhook.site](https://webhook.site/) for testing:

```bash
# Get a unique webhook URL
# https://webhook.site/unique-id-here

# Use it as callback
curl -X POST http://localhost:8080/api/v1/trades/capture \
  -H "X-Callback-Url: https://webhook.site/unique-id-here" \
  -H "Content-Type: application/json" \
  -d '{"tradeId": "TEST-001", ...}'
```

## Fallback: Polling Status Endpoint

Even with webhooks, clients should implement polling as a fallback:

```java
public CompletableFuture<TradeCaptureResponse> waitForCompletion(
        String jobId, Duration timeout) {
    
    return CompletableFuture.supplyAsync(() -> {
        Instant start = Instant.now();
        
        while (Duration.between(start, Instant.now()).compareTo(timeout) < 0) {
            AsyncJobStatus status = getJobStatus(jobId);
            
            if (status.getStatus() == JobStatus.COMPLETED) {
                return getTradeResponse(status);
            } else if (status.getStatus() == JobStatus.FAILED) {
                throw new TradeProcessingException(status.getError());
            }
            
            // Poll every 2 seconds
            Thread.sleep(2000);
        }
        
        throw new TimeoutException("Job did not complete within timeout");
    });
}
```

## Summary

✅ **Do:**
- Use HTTPS for callback URLs
- Implement idempotency in webhook handlers
- Return 2xx status codes quickly
- Log all webhook receipts
- Handle all job statuses (COMPLETED, FAILED, CANCELLED)
- Implement polling as fallback

❌ **Don't:**
- Use HTTP (only HTTPS)
- Block webhook processing
- Ignore duplicate webhooks
- Fail silently on errors
- Rely solely on webhooks (implement polling fallback)

## Related Documentation

- [API Documentation](./api/trade-capture-service-openapi.yaml)
- [Async Processing Guide](./trade-capture-service_design.md)
- [Job Status Service](../src/main/java/com/pb/synth/tradecapture/service/JobStatusService.java)

