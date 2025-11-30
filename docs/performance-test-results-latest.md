# Performance Test Results - Latest Run

**Date**: 2025-11-25  
**Test Run**: Full performance test suite

## Test Results Summary

### ✅ Test 1: Latency Test - PASSED
- **Trades**: 100
- **Success Rate**: 100% (100/100)
- **Latency Metrics**:
  - P50: 32ms
  - P95: 39ms ✅ (Target: <500ms)
  - P99: 41ms
  - Average: 36ms
  - Max: 428ms

**Analysis**: Excellent latency performance. All trades completed successfully with very low latency.

---

### ❌ Test 2: Sustained Load Test - FAILED
- **Target**: 23 trades/sec for 30 seconds (690 trades)
- **Actual**: 10.29 trades/sec
- **Success Rate**: 100% (690/690)
- **Performance**: 44.7% of target throughput

**Analysis**: 
- All trades succeeded, but throughput is below target
- Service is processing sequentially rather than in parallel
- Likely bottleneck: partition locking or database connection pool

---

### ❌ Test 3: Burst Capacity Test - FAILED
- **Target**: 184 trades/sec for 5 seconds (920 trades)
- **Actual**: 20.90 trades/sec
- **Success Rate**: 100% (920/920)
- **Performance**: 11.4% of target throughput

**Analysis**:
- All trades succeeded, but throughput is significantly below target
- Service cannot handle burst load effectively
- Same bottleneck as sustained load test

---

### ❌ Test 4: Parallel Partitions Test - FAILED
- **Target**: 10 partitions × 20 trades = 200 trades
- **Actual**: 5 successful, 195 failed
- **Success Rate**: 2.5% (5/200)
- **Error**: HTTP 500 errors

**Root Cause Analysis**:
1. **Database Deadlocks**: SQL Server error 1205 (deadlock victim)
2. **Transaction Rollback Issues**: `UnexpectedRollbackException` - transaction marked as rollback-only
3. **Deadlock Retry Not Working**: Retry aspect detects deadlocks but cannot retry because transaction is already rolled back

**Error Pattern**:
```
SQL Error: 1205, SQLState: 40001
Transaction (Process ID X) was deadlocked on lock resources with another process and has been chosen as the deadlock victim. Rerun the transaction.

org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
```

---

## Key Findings

### ✅ What's Working
1. **Latency**: Excellent - P95 latency of 39ms (well under 500ms target)
2. **Sequential Processing**: Works perfectly for single-threaded scenarios
3. **Deadlock Detection**: Deadlock retry aspect is detecting deadlocks correctly
4. **Redis Caching**: Idempotency caching is working (no cache-related errors)

### ❌ What's Not Working
1. **Deadlock Retry**: Aspect detects deadlocks but cannot retry due to transaction rollback
2. **Concurrent Processing**: High failure rate (97.5%) under concurrent load
3. **Throughput**: Only achieving 10-20 trades/sec vs 23-184 trades/sec targets
4. **Transaction Management**: Deadlock causes transaction to be marked rollback-only, preventing retry

---

## Root Cause

The deadlock retry mechanism has a fundamental flaw:

1. **Deadlock occurs** → SQL Server returns error 1205
2. **Spring marks transaction for rollback** → Transaction becomes rollback-only
3. **DeadlockRetryAspect tries to retry** → But transaction is already marked for rollback
4. **Retry fails** → `UnexpectedRollbackException` is thrown
5. **Request fails** → HTTP 500 error returned

The aspect is retrying **within** a transaction that's already been marked for rollback, rather than retrying the **entire transaction**.

---

## Recommendations

### Immediate Fixes (Priority 1)
1. **Fix Deadlock Retry Mechanism**:
   - Retry must happen at the transaction boundary, not within a rolled-back transaction
   - Consider using `@Retryable` annotation with proper transaction propagation
   - Or implement retry at the service method level with `REQUIRES_NEW` propagation

2. **Optimize Database Locking**:
   - Reduce transaction scope
   - Use optimistic locking where possible
   - Consider read replicas for idempotency checks

### Medium-term Improvements (Priority 2)
1. **Increase Connection Pool**: Already done (20 connections), but may need more for high concurrency
2. **Partition Locking Optimization**: Current Redis locking may be too coarse-grained
3. **Database Indexes**: Ensure proper indexes on partition_key, trade_id, idempotency_key

### Long-term Solutions (Priority 3)
1. **Event Sourcing**: Separate writes from reads
2. **Database Sharding**: Shard by partition key
3. **Read Replicas**: Use read replicas for idempotency checks

---

## Performance Metrics Comparison

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| P95 Latency | <500ms | 39ms | ✅ PASS |
| Sustained Throughput | 23 trades/sec | 10.29 trades/sec | ❌ FAIL (44.7%) |
| Burst Throughput | 184 trades/sec | 20.90 trades/sec | ❌ FAIL (11.4%) |
| Parallel Success Rate | >90% | 2.5% | ❌ FAIL |

---

## Next Steps

1. **Fix deadlock retry mechanism** - This is blocking concurrent processing
2. **Re-run parallel partitions test** - Should see significant improvement
3. **Tune connection pool and locking** - Based on actual deadlock patterns
4. **Monitor database performance** - Check for lock contention patterns

---

## Test Environment

- **Service**: pb-synth-tradecapture-svc (Docker)
- **Database**: MS SQL Server 2022 (Docker)
- **Redis**: Redis 7-alpine (Docker)
- **Kafka**: Apache Kafka (Docker)
- **Connection Pool**: 20 connections (HikariCP)
- **Deadlock Retry**: Enabled (3 attempts, exponential backoff)

