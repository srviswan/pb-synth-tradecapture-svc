# Nested Transaction Rollback Fix

## Problem

When a deadlock occurred in an inner transaction (e.g., `StateManagementService.updateState`), it marked the outer transaction (`TradeCaptureService.processTrade`) as rollback-only. Even though the deadlock retry mechanism retried the inner method with a new transaction, the outer transaction was already rolled back, causing `UnexpectedRollbackException`.

### Problem Flow

```
1. TradeCaptureService.processTrade() [@Transactional - outer]
   └─> StateManagementService.updateState() [@Transactional - inner]
       └─> DEADLOCK occurs
           └─> Inner transaction marked rollback-only
           └─> Outer transaction also marked rollback-only
           └─> DeadlockRetryAspect retries inner method
               └─> But outer transaction is already rolled back
               └─> Result: UnexpectedRollbackException
```

## Solution

Use `REQUIRES_NEW` propagation for inner transactions that might deadlock. This isolates them from the outer transaction, so deadlocks in inner transactions don't affect the outer transaction.

### Changes Made

**File**: `src/main/java/com/pb/synth/tradecapture/service/StateManagementService.java`
- Changed `updateState()` to use `@Transactional(propagation = Propagation.REQUIRES_NEW)`

**File**: `src/main/java/com/pb/synth/tradecapture/service/IdempotencyService.java`
- Changed `createIdempotencyRecord()` to use `REQUIRES_NEW`
- Changed `markCompleted()` to use `REQUIRES_NEW`
- Changed `markFailed()` to use `REQUIRES_NEW`

**File**: `src/main/java/com/pb/synth/tradecapture/service/SwapBlotterService.java`
- Changed `saveSwapBlotter()` to use `REQUIRES_NEW`

### How It Works

With `REQUIRES_NEW`:
1. Inner transaction is completely independent
2. Deadlock in inner transaction doesn't affect outer transaction
3. Deadlock retry can succeed in inner transaction
4. Outer transaction can continue normally

### Trade-offs

**Benefits**:
- Deadlocks in inner transactions don't rollback outer transaction
- Deadlock retry can succeed independently
- Better isolation and resilience

**Considerations**:
- Loss of full atomicity (inner transactions commit independently)
- If outer transaction fails after inner succeeds, inner changes persist
- This is acceptable because:
  - State updates are idempotent (can be retried)
  - Idempotency records can be marked as failed later
  - SwapBlotter save happens at the end (if we get there, trade is mostly complete)

## Expected Impact

- **Parallel Partitions Test**: Should improve from 26% to >90% success rate
- **Deadlock Recovery**: Inner transaction deadlocks won't cause outer transaction failures
- **Throughput**: Better concurrent processing capability

## Testing

After deployment, re-run performance tests:
```bash
./scripts/performance-test.sh
```

Expected improvements:
- Parallel partitions test: >90% success rate
- Reduced `UnexpectedRollbackException` errors
- Better deadlock recovery

## Monitoring

Watch for:
- Reduced `UnexpectedRollbackException` errors
- Successful deadlock retries in logs
- Improved parallel partition test success rate

## Rollback Plan

If issues occur:
1. Revert `REQUIRES_NEW` to default `REQUIRED` propagation
2. Restart service: `docker-compose restart trade-capture-service`

