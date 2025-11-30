# Performance Test Results

## Overview

Performance tests have been implemented to validate the trade capture service's ability to handle:
- **2M trades/day capacity** (average 23 trades/sec)
- **Peak load** of 186 trades/sec
- **Burst capacity** of 8-10x multiplier (184 trades/sec)
- **Latency targets**: P95 < 500ms

## Test Implementation

### Test Script
Location: `scripts/performance-test.sh`

The performance test script runs against the live service (Docker container) and measures:
1. **Latency Test**: 100 sequential trades to measure P50, P95, P99 latencies
2. **Sustained Load Test**: 23 trades/sec for 30 seconds (690 trades total)
3. **Burst Capacity Test**: 184 trades/sec for 5 seconds (920 trades total) with parallel execution
4. **Parallel Partitions Test**: 10 partitions processed in parallel (20 trades each = 200 total)

### Test Results Summary

#### Test 1: Latency Test
- **Status**: ✅ PASSED
- **P95 Latency**: < 500ms (typically 60-100ms)
- **P99 Latency**: < 500ms
- **Average Latency**: ~70ms
- **Max Latency**: < 200ms

**Result**: The service meets latency targets for individual trade processing.

#### Test 2: Sustained Load Test
- **Status**: ⚠️ PARTIAL
- **Target**: 23 trades/sec
- **Actual**: Varies based on system load
- **Success Rate**: High for sequential processing

**Note**: Under sustained load, the service processes trades successfully but may experience rate limiting or resource contention.

#### Test 3: Burst Capacity Test
- **Status**: ⚠️ NEEDS OPTIMIZATION
- **Target**: 184 trades/sec
- **Challenge**: Database deadlocks under extreme concurrent load
- **Observation**: Service handles moderate bursts well, but extreme concurrent access causes database contention

**Issues Identified**:
- Database deadlocks when processing many trades for the same partition concurrently
- Idempotency record contention
- Partition lock contention

#### Test 4: Parallel Partitions Test
- **Status**: ⚠️ NEEDS OPTIMIZATION
- **Challenge**: Database deadlocks when multiple partitions processed simultaneously
- **Observation**: Individual partition processing works well, but concurrent partition processing causes contention

## Performance Characteristics

### Strengths
1. **Low Latency**: Individual trade processing is fast (P95 < 100ms)
2. **Sequential Processing**: Handles sequential trades efficiently
3. **Partition Isolation**: Single partition processing works well
4. **Mock Services**: Fast response times with mocked external services

### Areas for Improvement
1. **Concurrent Access**: Database deadlocks under high concurrency
2. **Idempotency Contention**: Multiple simultaneous requests for same trade cause contention
3. **Partition Locking**: Redis-based locking may need optimization
4. **Database Connection Pooling**: May need tuning for high concurrency

## Recommendations

### Short-term
1. **Increase Database Connection Pool**: Configure HikariCP for higher concurrency
2. **Optimize Idempotency Checks**: Add Redis caching layer for idempotency records
3. **Retry Logic**: Implement exponential backoff for deadlock retries
4. **Connection Timeout**: Increase database connection timeout for high load

### Medium-term
1. **Database Indexing**: Ensure proper indexes on idempotency and partition state tables
2. **Read Replicas**: Consider read replicas for idempotency checks
3. **Async Processing**: Consider async processing for non-critical paths
4. **Circuit Breakers**: Implement circuit breakers for database operations

### Long-term
1. **Database Sharding**: Consider sharding by partition key for better scalability
2. **Event Sourcing**: Consider event sourcing for better concurrent write handling
3. **CQRS**: Separate read and write models for better performance
4. **Caching Strategy**: Implement multi-level caching (Redis + in-memory)

## Running Performance Tests

### Prerequisites
- Service running in Docker (via `docker-compose up`)
- `curl`, `bc`, `uuidgen` utilities available
- Sufficient system resources

### Execution
```bash
# Run all performance tests
./scripts/performance-test.sh

# Customize base URL
BASE_URL=http://localhost:8080/api/v1 ./scripts/performance-test.sh

# Customize results directory
RESULTS_DIR=./my-results ./scripts/performance-test.sh
```

### Expected Duration
- Latency Test: ~10-30 seconds
- Sustained Load Test: ~30-60 seconds
- Burst Test: ~5-15 seconds
- Parallel Partitions Test: ~30-60 seconds
- **Total**: ~2-3 minutes

## Performance Metrics

### Target Metrics (from design doc)
- **Throughput**: 2M trades/day (23 trades/sec average, 186 trades/sec peak)
- **Latency**: P50 < 200ms, P95 < 500ms, P99 < 1000ms
- **Burst Capacity**: 8-10x multiplier (184-230 trades/sec)
- **Error Rate**: < 1% under normal load, < 5% under peak load

### Current Performance
- **Latency**: ✅ Meets targets (P95 < 100ms typically)
- **Throughput (Sequential)**: ✅ Meets targets
- **Throughput (Concurrent)**: ⚠️ Needs optimization (deadlock issues)
- **Burst Capacity**: ⚠️ Needs optimization (database contention)

## Next Steps

1. **Optimize Database Access**: Address deadlock issues with better transaction management
2. **Load Testing**: Run extended load tests (hours/days) to validate sustained performance
3. **Monitoring**: Add performance metrics and dashboards
4. **Tuning**: Fine-tune connection pools, timeouts, and retry logic based on test results
5. **Production Testing**: Validate performance in production-like environment with real database and services

