# Priority 4: Sequence Number Calculation - Incremental Sequence Numbers

## Overview

Sequence numbers are **incremental** (1, 2, 3, 4, ...) and assigned by the upstream trade capture system. The system uses **bookingTimestamp** (when the trade was booked in the upstream system) for out-of-order detection and time window checks.

---

## Sequence Number Format

### Incremental Sequence Numbers

Sequence numbers are **incremental integers** (1, 2, 3, 4, ...) assigned by the upstream trade capture system when trades are booked. They are **not** derived from timestamps.

### Example

**Trade 1**:
- sequenceNumber: `1`
- bookingTimestamp: `2025-11-29T10:30:45.123Z` (when booked in upstream system)

**Trade 2**:
- sequenceNumber: `2`
- bookingTimestamp: `2025-11-29T10:30:46.124Z`

**Trade 3**:
- sequenceNumber: `3`
- bookingTimestamp: `2025-11-29T10:30:47.125Z`

---

## Implementation Details

### TradeCaptureRequest.getSequenceNumber()

Sequence numbers are **incremental integers** provided by the upstream system:

```java
public Long getSequenceNumber() {
    return sequenceNumber; // Incremental: 1, 2, 3, 4, ...
}
```

### TradeCaptureRequest.getBookingTimestamp()

The booking timestamp is used for out-of-order detection and time window checks:

```java
public ZonedDateTime getBookingTimestamp() {
    if (bookingTimestamp != null) {
        return bookingTimestamp; // When trade was booked in upstream system
    }
    return tradeTimestamp; // Fallback to trade execution timestamp
}
```

### Benefits

1. **Simple Incremental Sequence**: No complex timestamp calculations
2. **No False Gaps**: Trades don't need to occur every millisecond
3. **Booking Timestamp**: Uses actual booking time for out-of-order detection
4. **Flexible**: Can handle trades booked at any time, not just sequential milliseconds

---

## Usage

### With tradeTimestamp (Recommended)

```json
{
  "tradeId": "TRADE-001",
  "accountId": "ACC-001",
  "bookId": "BOOK-001",
  "securityId": "US0378331005",
  "tradeDate": "2025-11-29",
  "tradeTimestamp": "2025-11-29T10:30:45.123Z",
  "source": "AUTOMATED",
  "tradeLots": [...],
  "counterpartyIds": ["CP-001"]
}
```

**Sequence number**: Automatically computed from `tradeDate + tradeTimestamp`

### With Explicit Sequence Number

```json
{
  "tradeId": "TRADE-001",
  "sequenceNumber": 176662185123,
  ...
}
```

**Sequence number**: Uses provided value (for testing or special cases)

### Without Sequence Number (Backward Compatible)

```json
{
  "tradeId": "TRADE-001",
  "tradeDate": "2025-11-29",
  ...
}
```

**Sequence number**: Computed from `tradeDate + current time` (fallback)

---

## Protobuf Message

The protobuf message includes both `trade_date` and `timestamp`:

```protobuf
message TradeCaptureMessage {
  string trade_date = 8;      // ISO 8601: YYYY-MM-DD
  string timestamp = 9;        // ISO 8601: YYYY-MM-DDTHH:mm:ss.SSSZ
  int64 sequence_number = 6;   // Optional - computed if not provided
}
```

**MessageConverter** extracts both fields and computes sequence number if not provided.

---

## Validation Logic

### Time-Based Sliding Window Approach

The system uses a **time-based sliding window** to ensure trades are processed in chronological order (by tradeDate + tradeTimestamp) while handling out-of-order arrivals:

1. **Time Window Check**: 
   - If trade is **within time window** (e.g., last 7 days): Buffer and process in order
   - If trade is **outside time window** (older than 7 days): Process immediately (too old to wait for)

2. **Within Time Window - In Order**: 
   - Sequence matches expected (highest processed + 1): Process immediately

3. **Within Time Window - Out of Order (Earlier)**: 
   - Sequence < expected: Buffer it, waiting for even earlier trades to arrive
   - Example: Expected 100, received 98 → Buffer 98, wait for 99

4. **Within Time Window - Out of Order (Later/Gap)**: 
   - Sequence > expected: Buffer it, waiting for gaps to fill
   - Example: Expected 100, received 102 → Buffer 102, wait for 101
   - If gap > buffer window size: Reject and send to DLQ

5. **Outside Time Window**: 
   - Process immediately (can't wait for older trades that may never arrive)
   - Example: Trade from 1 year ago → Process immediately

### Example Flow

**Scenario 1: Trades within time window (last 7 days)**

```
Highest Processed: 176662185123 (Trade 1 at 10:30:45.123)
Expected: 176662185124
Received: 176662185124 (Trade 2 at 10:30:45.124)
  └─> Process immediately (in order)

Highest Processed: 176662185124
Expected: 176662185125
Received: 176662185125 (Trade 3 at 10:30:45.125)
  └─> Process immediately (in order)

Highest Processed: 176662185125
Expected: 176662185126
Received: 176662185128 (Trade 4 at 10:30:45.128, gap of 2ms)
  └─> Buffer (waiting for 126, 127)

Highest Processed: 176662185125
Expected: 176662185126
Received: 176662185126 (Trade 5 at 10:30:45.126)
  └─> Process immediately, then process buffered 128
```

**Scenario 2: Trades outside time window (older than 7 days)**

```
Highest Processed: 176662185123 (Trade from today)
Expected: 176662185124
Received: 176653785120 (Trade from 1 year ago, outside 7-day window)
  └─> Process immediately (too old to wait for)
  └─> Update highest processed to max(123, 120) = 123 (no change)
```

**Scenario 3: Out-of-order within window**

```
Highest Processed: 176662185123
Expected: 176662185124
Received: 176662185120 (Trade 0 at 10:30:45.120, earlier sequence)
  └─> Buffer (waiting for even earlier trades if any)
  └─> When 124 arrives, process 120, then 124
```

---

## Configuration

Time-based sliding window configuration:

```yaml
sequence:
  validation:
    enabled: true                  # Master switch: enable/disable sequence number validation
  buffer:
    enabled: true
    window-size: 1000              # Maximum messages to buffer per partition
    timeout-seconds: 300           # 5 minutes timeout for waiting for missing sequences
    time-window-days: 7            # Time-based sliding window: only buffer trades within last 7 days
```

### Enabling/Disabling Sequence Validation

**To disable sequence validation** (process all trades immediately, no buffering):
```yaml
sequence:
  validation:
    enabled: false
```

**To enable sequence validation** (default):
```yaml
sequence:
  validation:
    enabled: true
```

**Environment variable:**
```bash
export SEQUENCE_VALIDATION_ENABLED=false  # Disable sequence validation
export SEQUENCE_VALIDATION_ENABLED=true   # Enable sequence validation (default)
```

### Configuration Parameters

- **time-window-days**: Time-based sliding window (default: 7 days)
  - Trades **within** this window: Buffered and processed in chronological order
  - Trades **outside** this window: Processed immediately (too old to wait for)
  - Example: If a trade is 1 year old, it's processed immediately even if out of order

- **window-size**: Maximum number of messages to buffer per partition (default: 1000)
  - If gap exceeds this size, trade is rejected and sent to DLQ

- **timeout-seconds**: Maximum time to wait for missing sequences (default: 300 seconds = 5 minutes)
  - After timeout, buffered messages are sent to DLQ

---

## Testing

### Test Sequence Number Calculation

```bash
# Trade with timestamp (sequence auto-calculated)
curl -X POST http://localhost:8080/api/v1/trades/capture \
  -H "Content-Type: application/json" \
  -d '{
    "tradeId": "TEST-001",
    "tradeDate": "2025-11-29",
    "tradeTimestamp": "2025-11-29T10:30:45.123Z",
    ...
  }'
```

### Test In-Order Processing

```bash
# Trade 1 (earlier timestamp)
curl -X POST ... -d '{"tradeTimestamp": "2025-11-29T10:30:45.123Z", ...}'

# Trade 2 (later timestamp) - should process after Trade 1
curl -X POST ... -d '{"tradeTimestamp": "2025-11-29T10:30:45.124Z", ...}'
```

---

## Summary

✅ **Sequence numbers are now timestamp-based** (tradeDate + tradeTimestamp)
✅ **Automatic calculation** if not explicitly provided
✅ **Natural ordering** based on trade execution time
✅ **Backward compatible** with explicit sequence numbers
✅ **Cross-day support** for multi-day processing

