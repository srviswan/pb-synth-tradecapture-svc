# Log Errors Analysis - E2E Test Run

## Summary
After running the E2E test script with mocked enrichment services, the following errors were identified in the service logs.

---

## Critical Errors to Fix

### 1. ❌ NullPointerException in WebhookService
**Location:** `src/main/java/com/pb/synth/tradecapture/service/WebhookService.java:71-74`

**Error:**
```
NullPointerException: null
at java.util.Objects.requireNonNull(Unknown Source)
at java.util.ImmutableCollections$MapN.<init>(Unknown Source)
at java.util.Map.of(Unknown Source)
at c.p.s.t.s.WebhookService.sendWebhook(WebhookService.java:71)
```

**Root Cause:**
The `Map.of()` method throws `NullPointerException` if any of the values are null. When building the error payload, if `jobStatus.getError().getCode()` or `getMessage()` returns null, it causes an NPE.

**Fix:**
Add null checks before creating the Map, or use `HashMap` instead of `Map.of()`:

```java
if (jobStatus.getError() != null) {
    Map<String, String> errorMap = new HashMap<>();
    errorMap.put("code", jobStatus.getError().getCode() != null ? jobStatus.getError().getCode() : "UNKNOWN");
    errorMap.put("message", jobStatus.getError().getMessage() != null ? jobStatus.getError().getMessage() : "No error message");
    payload.put("error", errorMap);
}
```

---

### 2. ⚠️ ValidationException: Trade lots cannot be empty
**Location:** Multiple trades failing during async processing via Kafka

**Error:**
```
ValidationException: Trade lots cannot be empty
at c.p.s.t.s.ValidationService.validate(ValidationService.java:31)
at c.p.s.t.s.TradeCaptureService.processTrade(TradeCaptureService.java:174)
```

**Root Cause:**
The E2E test script includes trade lots in the JSON requests, but when messages are processed through Kafka, the `tradeLots` field appears to be empty or null. This could be due to:
1. Deserialization issues when converting from JSON to TradeCaptureRequest
2. Trade lots being lost during message serialization/deserialization
3. The Kafka message converter not properly handling trade lots

**Investigation Needed:**
- Check how TradeCaptureRequest is serialized/deserialized for Kafka messages
- Verify the TradeLot model is correctly annotated for Jackson
- Check if trade lots are properly included in the Kafka message payload

**Potential Fix:**
Ensure the Kafka message converter properly handles nested trade lots when converting between JSON and TradeCaptureRequest.

---

## Expected Errors (Non-Issues)

### 3. ✅ Duplicate Idempotency Key Violation
**Error:**
```
SQLServerException: Violation of UNIQUE KEY constraint 'UK_ixtia2gi4nwc73k5jwss4vflk'. 
Cannot insert duplicate key in object 'dbo.idempotency_record'. 
The duplicate key value is (E2E-TRADE-1764711079-AUTO-9b1904e2c976a73e).
```

**Status:** ✅ **Expected Behavior**
This error is expected during the duplicate trade detection test. The system correctly identifies duplicate trades based on idempotency keys and prevents duplicate processing. This is working as designed.

---

### 4. ✅ Webhook 403 Forbidden
**Error:**
```
HttpClientErrorException$Forbidden: 403 Forbidden
"Access Denied" - You don't have permission to access "http://example.com/callback"
```

**Status:** ✅ **Expected Behavior**
The test script uses `http://example.com/callback` as a placeholder callback URL. This URL is not a real endpoint, so the 403 errors are expected. The webhook retry mechanism is working correctly, attempting 3 retries before giving up.

**Recommendation:**
For testing purposes, consider using a mock webhook service (e.g., webhook.site or a local mock server) to verify webhook delivery without errors.

---

### 5. ✅ Rules Endpoint 404
**Error:**
```
NoResourceFoundException: No static resource api/rules.
```

**Status:** ✅ **Expected Behavior**
The `/api/v1/rules` endpoint doesn't exist yet. The E2E test script correctly handles this as a non-critical test.

---

### 6. ✅ Trade Not Found (404)
**Error:**
```
WARN: SwapBlotter not found for trade ID: E2E-TRADE-1764711079-AUTO
```

**Status:** ✅ **Expected Behavior**
This occurs because the trade processing failed (due to validation errors above), so the trade was never persisted. When the test tries to retrieve it, it correctly returns 404.

---

## Recommendations

### Immediate Actions:
1. **Fix NullPointerException in WebhookService** - This is causing errors in the webhook delivery logic
2. **Investigate Trade Lots Deserialization** - Determine why trade lots are empty when processing via Kafka

### Testing Improvements:
1. Use a real mock webhook service for E2E tests (e.g., webhook.site or WireMock)
2. Add validation to ensure trade lots are preserved through the Kafka message pipeline
3. Add more detailed logging around trade lot deserialization

### Monitoring:
1. Add alerts for NullPointerException in WebhookService
2. Monitor validation failure rates to catch data issues early
3. Track webhook delivery success/failure rates

---

## Error Statistics

From the log analysis:
- **Critical Errors:** 2 (NPE in WebhookService, Trade Lots Validation)
- **Expected Errors:** 4 (Duplicate keys, Webhook 403s, Missing endpoints)
- **Total Errors Logged:** ~20+ (many are retries of the same errors)

---

## Next Steps

1. Fix the NullPointerException in WebhookService
2. Debug why trade lots are empty during Kafka processing
3. Update E2E tests to use proper mock webhook endpoints
4. Re-run E2E tests after fixes

