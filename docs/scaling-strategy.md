# Horizontal Scaling Strategy

## Executive Summary

**Recommendation**: ✅ **YES, implement horizontal scaling**

**Rationale**:
- Service is already designed for horizontal scaling (distributed locking, stateless)
- Current throughput (10 trades/sec) is below target (23 trades/sec)
- Scaling provides linear improvement for different partitions
- Cost-effective way to meet throughput targets

---

## Current Architecture Analysis

### ✅ Already Scalable Components

1. **Stateless Service Design**
   - No in-memory state between requests
   - All state in database/Redis
   - Perfect for horizontal scaling

2. **Distributed Locking**
   - Redis-based `PartitionLockService`
   - Ensures cross-instance coordination
   - Prevents concurrent processing of same partition

3. **Partition-Based Processing**
   - Trades partitioned by `{accountId}_{bookId}_{securityId}`
   - Different partitions can process in parallel
   - Same partition processes sequentially (by design)

### ⚠️ Current Bottlenecks

1. **Single Instance Throughput**: ~10 trades/sec
2. **Database Deadlocks**: Under concurrent load
3. **Sequential Partition Processing**: By design (correctness requirement)

---

## Scaling Math

### Current Performance
- **1 Instance**: ~10 trades/sec sustained
- **Target**: 23 trades/sec sustained, 184 trades/sec burst

### With Horizontal Scaling

| Instances | Expected Throughput | Meets Target? |
|-----------|-------------------|--------------|
| 1 | 10 trades/sec | ❌ (43% of sustained) |
| 2 | 20 trades/sec | ❌ (87% of sustained) |
| 3 | 30 trades/sec | ✅ (130% of sustained) |
| 10 | 100 trades/sec | ✅ (435% of sustained, 54% of burst) |
| 20 | 200 trades/sec | ✅ (870% of sustained, 109% of burst) |

**Key Insight**: Scaling helps when you have **many different partitions**. Same partition still processes at ~10 trades/sec (sequential requirement).

---

## Implementation Approach

### Option 1: Load Balancer + Multiple Instances (Recommended)

**Architecture**:
```
                    Load Balancer (NGINX)
                         |
        +----------------+----------------+
        |                |                |
   Instance 1      Instance 2      Instance 3
   (Partition A)  (Partition B)  (Partition C)
   (Partition D)  (Partition E)  (Partition F)
```

**Benefits**:
- Simple to implement
- Automatic failover
- Easy to scale up/down
- Works with existing Redis locking

**Files Created**:
- `docker-compose.scale.yml` - Multi-instance setup
- `nginx.conf` - Load balancer configuration

### Option 2: Kubernetes Deployment (Production)

**Benefits**:
- Auto-scaling based on metrics
- Self-healing
- Rolling updates
- Service discovery

---

## Scaling Considerations

### 1. Database Connection Pool

**Current**: 20 connections per instance
**With N Instances**: 20N total connections

**Recommendation**:
- Monitor database connection usage
- Adjust pool size per instance if needed
- Consider connection pooler for SQL Server

### 2. Redis Connection Pool

**Current**: 20 connections per instance
**With N Instances**: 20N total connections

**Recommendation**:
- Redis can handle many connections
- Monitor Redis connection count
- Consider Redis Cluster for high availability

### 3. Partition Lock Contention

**Impact**:
- More instances = more lock contention for **same partition**
- Different partitions = no contention (scales linearly)
- **Key**: High partition diversity = better scaling

**Mitigation**:
- Optimize lock hold time
- Use exponential backoff (already implemented)
- Consider lock-free algorithms where possible

### 4. Database Deadlocks

**Risk**: More instances = potentially more deadlocks

**Mitigation**:
- Complete deadlock retry fix
- Optimize transaction boundaries
- Use read replicas for idempotency checks
- Consider optimistic locking

---

## Testing Scaling

### Step 1: Test with 2 Instances

```bash
# Use scaled docker-compose
docker-compose -f docker-compose.scale.yml up -d

# Run performance test
./scripts/performance-test.sh
```

**Expected**:
- Throughput: ~20 trades/sec (2x improvement)
- Different partitions process in parallel
- Same partition still sequential

### Step 2: Test with 3 Instances

```bash
# Add instance 3 to docker-compose.scale.yml
docker-compose -f docker-compose.scale.yml up -d --scale trade-capture-service=3

# Run performance test
./scripts/performance-test.sh
```

**Expected**:
- Throughput: ~30 trades/sec (exceeds 23 target)
- Better load distribution
- Improved parallel partition processing

---

## Monitoring for Scaling

### Key Metrics

1. **Throughput per Instance**
   - Should be consistent across instances
   - Indicates good load balancing

2. **Partition Distribution**
   - Number of unique partitions
   - Lock contention per partition
   - Lock wait times

3. **Database Metrics**
   - Connection pool usage (should scale with instances)
   - Deadlock frequency
   - Query performance

4. **Redis Metrics**
   - Connection count
   - Lock acquisition time
   - Cache hit rate

---

## Recommended Scaling Plan

### Phase 1: Fix Current Issues (Before Scaling)
- [x] Deadlock retry mechanism
- [x] Nested transaction isolation
- [ ] Verify >90% success rate
- [ ] Optimize transaction boundaries

### Phase 2: Initial Scaling (2-3 Instances)
- [ ] Set up load balancer
- [ ] Deploy 2-3 instances
- [ ] Test and measure improvement
- [ ] Tune based on results

### Phase 3: Production Scaling (5-10 Instances)
- [ ] Based on actual load patterns
- [ ] Optimize database and Redis
- [ ] Consider read replicas
- [ ] Implement auto-scaling

---

## Quick Start: Test Scaling Locally

```bash
# 1. Build service
docker-compose build trade-capture-service-1

# 2. Start scaled environment
docker-compose -f docker-compose.scale.yml up -d

# 3. Test load balancer
curl http://localhost:8080/api/v1/health

# 4. Run performance test
./scripts/performance-test.sh

# 5. Monitor instances
docker logs -f pb-synth-tradecapture-svc-1
docker logs -f pb-synth-tradecapture-svc-2
docker logs -f pb-synth-tradecapture-svc-3
```

---

## Conclusion

**Horizontal scaling is the right approach** because:
1. Service is already designed for it
2. Current throughput is below target
3. Scaling provides linear improvement
4. Cost-effective solution

**Next Steps**:
1. Complete current fixes (deadlock retry)
2. Test with 2-3 instances
3. Measure improvement
4. Scale based on actual needs

