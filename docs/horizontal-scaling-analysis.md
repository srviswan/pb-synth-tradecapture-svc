# Horizontal Scaling Analysis

## Current Performance Bottlenecks

### Observed Performance
- **Sustained Load**: 9.20 trades/sec (target: 23 trades/sec) - **40% of target**
- **Burst Capacity**: High failure rate under burst load
- **Parallel Partitions**: 26% success rate (improving but still below target)
- **Latency**: Excellent (P95: 50ms)

### Bottleneck Analysis

1. **Sequential Processing Per Partition** (By Design)
   - Each partition must be processed sequentially to maintain order
   - Partition lock ensures only one trade per partition at a time
   - This is **intentional** for correctness, not a bug

2. **Database Deadlocks** (Being Addressed)
   - High deadlock rate under concurrent load
   - Deadlock retry mechanism improving but not fully resolved
   - Nested transaction issues being fixed

3. **Single Instance Throughput Limit**
   - Current: ~10 trades/sec per instance
   - Target: 23 trades/sec sustained, 184 trades/sec burst
   - Gap: Need 2-3x improvement for sustained, 18x for burst

---

## Would Horizontal Scaling Help?

### ✅ YES - Horizontal Scaling Will Help

**Key Insight**: The service is **already designed for horizontal scaling**:

1. **Partition-Based Architecture**
   - Trades are partitioned by `{accountId}_{bookId}_{securityId}`
   - Different partitions can be processed **in parallel** across instances
   - Same partition must be processed **sequentially** (by design)

2. **Distributed Locking Already Implemented**
   - Redis-based `PartitionLockService` ensures cross-instance coordination
   - Multiple instances can process different partitions simultaneously
   - Lock prevents concurrent processing of same partition

3. **Stateless Service Design**
   - No in-memory state between requests
   - All state in database/Redis
   - Perfect for horizontal scaling

### Scaling Math

**Current State**:
- 1 instance: ~10 trades/sec
- Processing is partition-sequential (correctness requirement)

**With Horizontal Scaling**:
- 2 instances: ~20 trades/sec (different partitions in parallel)
- 3 instances: ~30 trades/sec
- N instances: ~10N trades/sec (for different partitions)

**Limitation**: 
- Same partition still processes at ~10 trades/sec (sequential requirement)
- Scaling helps when you have **many different partitions**

---

## Scaling Strategy

### Phase 1: Fix Current Issues (Before Scaling)

**Priority**: Fix remaining bottlenecks before scaling

1. **Complete Deadlock Retry Fix** ✅ (In Progress)
   - Nested transaction rollback issue
   - Should improve parallel partition success rate

2. **Optimize Database Performance**
   - Connection pool tuning
   - Query optimization
   - Index optimization ✅ (Done)

3. **Reduce Lock Hold Time**
   - Minimize transaction duration
   - Process asynchronously where possible
   - Use read replicas for reads

### Phase 2: Horizontal Scaling (After Fixes)

**When to Scale**:
- After deadlock retry is fully working (>90% success rate)
- When single instance reaches stable ~15-20 trades/sec
- When you have many different partitions (high partition diversity)

**Scaling Approach**:

#### Option A: Load Balancer + Multiple Instances (Recommended)

```
                    Load Balancer
                         |
        +----------------+----------------+
        |                |                |
   Instance 1      Instance 2      Instance 3
   (Partition A)    (Partition B)    (Partition C)
   (Partition D)    (Partition E)    (Partition F)
```

**Benefits**:
- Simple to implement
- Automatic failover
- Easy to scale up/down
- Works with existing Redis locking

**Configuration**:
- Use sticky sessions (optional - not required for stateless service)
- Round-robin or least-connections load balancing
- Health checks for instance health

#### Option B: Message Queue Partitioning (Advanced)

```
   Kafka/Solace (Partitioned)
        |
   +----+----+----+
   |    |    |    |
Inst1 Inst2 Inst3 Inst4
```

**Benefits**:
- Natural partition distribution
- Better throughput for high partition count
- Automatic load distribution

**Requirements**:
- Message queue with partition support
- Consumer group configuration
- Partition assignment strategy

---

## Scaling Considerations

### 1. Database Connection Pool

**Current**: 20 connections per instance
**With N Instances**: 20N total connections

**Recommendation**:
- Monitor database connection usage
- Adjust pool size: `HIKARI_MAX_POOL_SIZE = 20 * expected_instances`
- Consider connection pooler (PgBouncer, etc.) for SQL Server

### 2. Redis Connection Pool

**Current**: 20 connections per instance
**With N Instances**: 20N total connections

**Recommendation**:
- Redis can handle many connections
- Monitor Redis connection count
- Consider Redis Cluster for high availability

### 3. Partition Lock Contention

**Current**: Redis lock per partition
**With N Instances**: Same lock, more contention

**Impact**:
- More instances = more lock contention for same partition
- Different partitions = no contention (scales linearly)
- **Key**: High partition diversity = better scaling

### 4. Database Deadlocks

**Current**: Deadlocks under concurrent load
**With N Instances**: Potentially more deadlocks

**Mitigation**:
- Complete deadlock retry fix
- Optimize transaction boundaries
- Use read replicas for idempotency checks
- Consider optimistic locking for partition state

### 5. Message Queue Throughput

**Current**: Kafka configured for local development
**With N Instances**: Need proper Kafka cluster

**Requirements**:
- Kafka cluster with proper partitioning
- Consumer group configuration
- Partition assignment strategy

---

## Scaling Targets

### Current Capacity
- **1 Instance**: ~10 trades/sec sustained
- **1 Instance**: ~20 trades/sec burst (with failures)

### Target Capacity
- **Sustained**: 23 trades/sec (2M trades/day)
- **Burst**: 184 trades/sec (8-10x multiplier)

### Scaling Plan

| Scenario | Instances Needed | Notes |
|----------|-----------------|-------|
| **Sustained Load** | 3 instances | 3 × 10 = 30 trades/sec (exceeds 23 target) |
| **Burst Load** | 10-15 instances | 10 × 20 = 200 trades/sec (exceeds 184 target) |
| **With Optimizations** | 2-3 instances | If we reach 15 trades/sec per instance |

---

## Implementation Steps

### Step 1: Complete Current Fixes
- [x] Deadlock retry mechanism
- [x] Nested transaction isolation
- [ ] Verify >90% success rate in parallel partitions test
- [ ] Optimize transaction boundaries

### Step 2: Prepare for Scaling
- [ ] Add health check endpoint (✅ Already exists)
- [ ] Configure load balancer
- [ ] Set up monitoring/metrics
- [ ] Document scaling procedures

### Step 3: Test Scaling
- [ ] Test with 2 instances
- [ ] Verify partition locking works across instances
- [ ] Measure throughput improvement
- [ ] Test failover scenarios

### Step 4: Production Deployment
- [ ] Deploy with load balancer
- [ ] Monitor performance
- [ ] Scale based on actual load

---

## Load Balancer Configuration

### Recommended: NGINX or HAProxy

**NGINX Example**:
```nginx
upstream trade_capture_backend {
    least_conn;  # Use least connections
    server instance1:8080 max_fails=3 fail_timeout=30s;
    server instance2:8080 max_fails=3 fail_timeout=30s;
    server instance3:8080 max_fails=3 fail_timeout=30s;
}

server {
    listen 80;
    location / {
        proxy_pass http://trade_capture_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        health_check;
    }
}
```

### Kubernetes (If Using K8s)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: trade-capture-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: trade-capture-service
  template:
    metadata:
      labels:
        app: trade-capture-service
    spec:
      containers:
      - name: trade-capture-service
        image: pb-synth-tradecapture-svc:latest
        ports:
        - containerPort: 8080
        env:
        - name: REDIS_HOST
          value: "redis-service"
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: url
---
apiVersion: v1
kind: Service
metadata:
  name: trade-capture-service
spec:
  type: LoadBalancer
  selector:
    app: trade-capture-service
  ports:
  - port: 80
    targetPort: 8080
```

---

## Monitoring for Scaling

### Key Metrics to Track

1. **Throughput per Instance**
   - Trades/sec per instance
   - Should be consistent across instances

2. **Partition Distribution**
   - Number of unique partitions
   - Partition lock contention
   - Lock wait times

3. **Database Metrics**
   - Connection pool usage
   - Deadlock frequency
   - Query performance

4. **Redis Metrics**
   - Connection count
   - Lock acquisition time
   - Cache hit rate

5. **Instance Health**
   - CPU usage
   - Memory usage
   - Request latency
   - Error rate

---

## Expected Scaling Benefits

### With 3 Instances

**Before Scaling** (1 instance):
- Sustained: 10 trades/sec
- Burst: 20 trades/sec (with failures)

**After Scaling** (3 instances):
- Sustained: ~30 trades/sec (exceeds 23 target) ✅
- Burst: ~60 trades/sec (still below 184 target)
- **Improvement**: 3x throughput for different partitions

### With 10 Instances

**After Scaling** (10 instances):
- Sustained: ~100 trades/sec ✅
- Burst: ~200 trades/sec (exceeds 184 target) ✅
- **Improvement**: 10x throughput for different partitions

---

## Limitations and Considerations

### 1. Partition Sequential Processing

**Constraint**: Same partition must process sequentially
- This is **by design** for correctness
- Horizontal scaling helps when you have **many different partitions**
- If all trades are for same partition, scaling won't help

### 2. Database as Bottleneck

**Risk**: Database may become bottleneck with many instances
- Monitor connection pool usage
- Consider read replicas
- Optimize queries and indexes
- Consider database sharding (long-term)

### 3. Redis as Single Point

**Risk**: Redis becomes single point of failure
- Use Redis Sentinel or Cluster
- Monitor Redis performance
- Consider Redis replication

### 4. Cost Considerations

**Trade-off**: More instances = higher infrastructure cost
- Balance cost vs. performance
- Use auto-scaling based on load
- Consider spot instances for non-critical workloads

---

## Recommendation

### ✅ YES - Implement Horizontal Scaling

**Rationale**:
1. Service is already designed for it (distributed locking, stateless)
2. Current throughput (10 trades/sec) is below target (23 trades/sec)
3. Scaling will provide linear improvement for different partitions
4. Cost-effective way to meet throughput targets

### Implementation Priority

1. **Immediate** (Before Scaling):
   - Complete deadlock retry fix
   - Verify >90% success rate
   - Optimize transaction boundaries

2. **Short-term** (Scale to 2-3 instances):
   - Set up load balancer
   - Deploy 2-3 instances
   - Monitor and tune

3. **Medium-term** (Scale to 5-10 instances):
   - Based on actual load patterns
   - Optimize database and Redis
   - Consider read replicas

4. **Long-term** (Advanced scaling):
   - Database sharding
   - Message queue partitioning
   - Auto-scaling based on metrics

---

## Next Steps

1. **Complete current fixes** (deadlock retry, nested transactions)
2. **Re-run performance tests** to establish baseline
3. **Set up load balancer** (NGINX/HAProxy or K8s)
4. **Deploy 2 instances** and test
5. **Measure improvement** and tune
6. **Scale incrementally** based on actual needs

