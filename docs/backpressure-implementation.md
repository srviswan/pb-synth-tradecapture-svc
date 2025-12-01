# Backpressure Handling Implementation

## Overview

This document describes the backpressure handling mechanisms implemented at both API and messaging levels to prevent system overload and ensure graceful degradation under high load.

## Architecture

### API-Level Backpressure

**Location**: `src/main/java/com/pb/synth/tradecapture/config/ApiBackpressureConfig.java`

**Mechanisms:**
1. **Bounded Thread Pool**: API requests are processed through a bounded thread pool executor
   - Core size: 20 threads (configurable)
   - Max size: 50 threads (configurable)
   - Queue capacity: 1000 requests (configurable)
   - Rejection policy: `CallerRunsPolicy` (blocks caller when queue is full)

2. **Request Queue Monitoring**: Tracks current queue depth
   - Monitors queue utilization
   - Warns at 80% capacity (configurable)
   - Rejects requests at 100% capacity

3. **HTTP 503 Response**: When overloaded, returns:
   - Status: `503 Service Unavailable`
   - Header: `Retry-After: 5` (configurable seconds)
   - JSON error response with clear message

4. **Interceptor-Based**: Uses Spring MVC interceptor to check backpressure before processing
   - Skips health/status endpoints
   - Automatically increments/decrements queue counters

**Configuration** (`application.yml`):
```yaml
backpressure:
  api:
    enabled: true
    max-queue-size: 1000
    warning-threshold: 0.8  # Warn at 80%
    thread-pool:
      core-size: 20
      max-size: 50
      queue-capacity: 1000
    retry-after-seconds: 5
```

### Messaging-Level Backpressure

**Location**: 
- `src/main/java/com/pb/synth/tradecapture/messaging/ConsumerLagMonitor.java`
- `src/main/java/com/pb/synth/tradecapture/service/backpressure/BackpressureService.java`

**Mechanisms:**
1. **Consumer Lag Monitoring**: Tracks Kafka consumer lag across all partitions
   - Monitors lag every 5 seconds (configurable)
   - Calculates total lag across all partitions
   - Updates `BackpressureService` with current lag

2. **Automatic Pause/Resume**: 
   - **Pause**: When lag exceeds `max-lag` threshold (default: 10,000 messages)
   - **Resume**: When lag drops below `resume-lag` threshold (default: 2,000 messages)
   - Uses Spring Kafka's `MessageListenerContainer.pause()` and `resume()` methods

3. **Processing Queue Monitoring**: Tracks messages currently being processed
   - Monitors in-flight message count
   - Rejects new messages when queue is full (default: 500 messages)
   - Prevents overwhelming the system

4. **Adaptive Polling**: Kafka consumer configuration optimized for backpressure
   - Reduced `max-poll-records` when under pressure
   - Smaller fetch sizes to reduce memory usage
   - Faster processing cycles

**Configuration** (`application.yml`):
```yaml
backpressure:
  messaging:
    enabled: true
    max-lag: 10000  # Pause consumer when lag >= 10,000
    warning-lag: 5000  # Warn when lag >= 5,000
    resume-lag: 2000  # Resume when lag < 2,000
    max-processing-queue: 500  # Max in-flight messages
    lag-check-interval-ms: 5000  # Check lag every 5 seconds
```

## Integration Points

### API Level

1. **TradeCaptureController**: All `/api/v1/trades/**` endpoints are protected
   - Interceptor checks backpressure before processing
   - Returns 503 if overloaded
   - Queue counters updated automatically

2. **BackpressureService**: Central service for tracking backpressure state
   - `canAcceptApiRequest()`: Checks if API can accept new requests
   - `incrementApiQueue()` / `decrementApiQueue()`: Track queue depth
   - `getApiStatus()`: Returns current API backpressure status

### Messaging Level

1. **TradeMessageProcessor**: Checks backpressure before processing messages
   - `canProcessMessage()`: Checks if consumer can process more messages
   - Rejects messages when under backpressure
   - Tracks processing queue depth

2. **ConsumerLagMonitor**: Scheduled task that monitors consumer lag
   - Runs every 5 seconds (configurable)
   - Calculates lag across all partitions
   - Automatically pauses/resumes consumer

3. **KafkaTradeMessageConsumer**: Integrated with lag monitor
   - Wires up listener container for pause/resume operations
   - Consumer automatically pauses when lag is too high

## Monitoring and Management

### REST API Endpoints

1. **GET `/api/v1/backpressure/api/status`**: Get API-level backpressure status
   ```json
   {
     "enabled": true,
     "currentQueueSize": 150,
     "maxQueueSize": 1000,
     "utilization": 0.15,
     "underPressure": false,
     "warning": false,
     "totalRequests": 10000,
     "rejectedRequests": 5
   }
   ```

2. **GET `/api/v1/backpressure/messaging/status`**: Get messaging-level backpressure status
   ```json
   {
     "enabled": true,
     "currentLag": 2500,
     "maxLag": 10000,
     "warningLag": 5000,
     "currentQueueSize": 45,
     "maxQueueSize": 500,
     "underPressure": false,
     "warning": false,
     "totalProcessed": 50000
   }
   ```

3. **GET `/api/v1/backpressure/status`**: Get overall backpressure status
   ```json
   {
     "api": { ... },
     "messaging": { ... },
     "overallUnderPressure": false,
     "overallWarning": false
   }
   ```

## Behavior Under Load

### API-Level Backpressure

**Normal Operation:**
- Requests accepted and queued
- Processed by thread pool
- Queue depth tracked

**Under Pressure (80-100% capacity):**
- Warning logs generated
- Requests still accepted until 100%

**Overloaded (100% capacity):**
- New requests rejected with HTTP 503
- `Retry-After` header indicates when to retry
- Existing requests continue processing

### Messaging-Level Backpressure

**Normal Operation:**
- Messages consumed and processed
- Consumer lag monitored
- Processing queue tracked

**Under Pressure (lag approaching threshold):**
- Warning logs generated
- Consumer continues processing

**Overloaded (lag exceeds threshold):**
- Consumer automatically paused
- No new messages consumed
- Existing messages continue processing
- Consumer resumes when lag decreases

## Configuration Tuning

### For High Throughput (2M trades/day)
```yaml
backpressure:
  api:
    max-queue-size: 2000
    thread-pool:
      core-size: 50
      max-size: 100
      queue-capacity: 2000
  messaging:
    max-lag: 20000
    max-processing-queue: 1000
```

### For Low Latency (Fast Response Times)
```yaml
backpressure:
  api:
    max-queue-size: 500
    thread-pool:
      core-size: 10
      max-size: 20
      queue-capacity: 500
  messaging:
    max-lag: 5000
    max-processing-queue: 200
```

### For Burst Handling (8-10x multiplier)
```yaml
backpressure:
  api:
    max-queue-size: 5000
    thread-pool:
      core-size: 100
      max-size: 200
      queue-capacity: 5000
  messaging:
    max-lag: 50000
    max-processing-queue: 2000
```

## Metrics and Observability

### Key Metrics Tracked

1. **API Level:**
   - Current queue size
   - Queue utilization percentage
   - Total requests accepted
   - Total requests rejected
   - Under pressure flag
   - Warning flag

2. **Messaging Level:**
   - Current consumer lag
   - Processing queue size
   - Total messages processed
   - Consumer paused state
   - Under pressure flag
   - Warning flag

### Integration with Monitoring

- Metrics exposed via REST API endpoints
- Can be integrated with Prometheus/Grafana
- Logs include backpressure warnings and actions
- Health checks can include backpressure status

## Best Practices

1. **Tune Thresholds Based on Capacity**: Set thresholds based on actual processing capacity
2. **Monitor Lag Trends**: Watch for increasing lag trends before thresholds are hit
3. **Scale Horizontally**: When backpressure triggers frequently, consider scaling out
4. **Alert on Persistent Backpressure**: Set up alerts for sustained backpressure conditions
5. **Test Under Load**: Validate backpressure behavior under realistic load scenarios

## Future Enhancements

1. **Adaptive Thresholds**: Automatically adjust thresholds based on system capacity
2. **Priority Queues**: Process high-priority trades even under backpressure
3. **Circuit Breaker Integration**: Integrate with Resilience4j circuit breakers
4. **Metrics Export**: Export backpressure metrics to Prometheus/CloudWatch
5. **Auto-Scaling Triggers**: Use backpressure metrics to trigger auto-scaling

