# Deadlock Retry Fix Implementation

## Problem

The original deadlock retry mechanism was failing because:
1. When a deadlock occurs, Spring marks the transaction for rollback
2. The retry aspect tried to retry within the same transaction context
3. Result: `UnexpectedRollbackException` - transaction already marked rollback-only

## Solution

### Implementation Approach

**File**: `src/main/java/com/pb/synth/tradecapture/aspect/DeadlockRetryAspect.java`

**Key Changes**:
1. **First Attempt**: Proceed normally with existing transaction
2. **Deadlock Detection**: Catch deadlock exceptions on first attempt
3. **Retry with New Transactions**: Use `REQUIRES_NEW` propagation for each retry attempt
4. **Transaction Management**: Use `TransactionTemplate` to create fresh transactions for retries

### Technical Details

```java
// First attempt - proceed normally
try {
    return joinPoint.proceed();
} catch (Throwable firstException) {
    if (!isDeadlockException(firstException)) {
        throw firstException; // Not a deadlock, propagate
    }
    
    // Deadlock detected - retry with new transactions
    return retryTemplate.execute(context -> {
        // Create new transaction for retry (REQUIRES_NEW)
        DefaultTransactionDefinition txDef = new DefaultTransactionDefinition();
        txDef.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        txDef.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager, txDef);
        
        return txTemplate.execute(status -> {
            return joinPoint.proceed(); // Retry in new transaction
        });
    });
}
```

### Why This Works

1. **First Attempt**: Uses the original `@Transactional` transaction
2. **On Deadlock**: Catches the exception before transaction is fully rolled back
3. **Retry Attempts**: Each retry gets a completely new transaction (`REQUIRES_NEW`)
4. **No Rollback-Only Issues**: New transactions are not affected by previous rollback

## Database Index Optimization

**File**: `src/main/resources/db/migration/V6__optimize_indexes_for_concurrency.sql`

Added composite indexes to reduce lock contention:
1. `idx_idempotency_record_partition_status` - Faster idempotency checks for same partition
2. `idx_partition_state_partition_key_version` - Faster partition state lookups
3. `idx_swap_blotter_partition_archive` - Faster partition-based queries

## Configuration

Deadlock retry is configured via `application.yml`:

```yaml
deadlock-retry:
  enabled: true
  max-attempts: 3
  initial-delay-ms: 50
  max-delay-ms: 500
  multiplier: 2.0
```

## Expected Improvements

1. **Deadlock Recovery**: Automatic retry with new transactions
2. **Reduced Failures**: Should see significant improvement in parallel partition test
3. **Better Throughput**: Less contention from failed transactions
4. **Index Optimization**: Faster lookups reduce lock hold time

## Testing

After deployment, re-run the performance tests:
```bash
./scripts/performance-test.sh
```

Expected improvements:
- **Parallel Partitions Test**: Success rate should improve from 2.5% to >90%
- **Sustained Load Test**: Throughput should improve
- **Burst Capacity Test**: Better handling of concurrent requests

## Monitoring

Watch for these log messages:
- `Deadlock detected on first attempt for method...` - Deadlock occurred
- `Deadlock retry attempt X for method...` - Retry in progress
- `Deadlock still occurring on retry attempt X...` - Multiple deadlocks (may need tuning)

## Rollback Plan

If issues occur:
1. Disable deadlock retry: `DEADLOCK_RETRY_ENABLED=false`
2. Restart service: `docker-compose restart trade-capture-service`

