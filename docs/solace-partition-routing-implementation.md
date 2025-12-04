# Solace Partition-Based Topic Routing Implementation

## Overview

This document describes the implementation of partition-based topic routing for Solace PubSub+ messaging. The implementation routes messages from a single upstream topic to partition-specific topics, enabling automatic ordering per partition.

## Architecture

```
Upstream System
    │
    │ (all messages to single topic)
    ▼
┌─────────────────────────────┐
│ trade/capture/input         │  (Single topic from upstream)
└────────────┬────────────────┘
             │
             │ Consume
             ▼
┌─────────────────────────────┐
│ SolaceMessageRouter        │  (New component)
│ - Consumes from input topic │
│ - Extracts partition key    │
│ - Republishes to partition  │
│   specific topics           │
└────────────┬────────────────┘
             │
             │ Republish to partition topics
             ▼
┌─────────────────────────────┐
│ trade/capture/input/        │
│   {partitionKey}            │  (Partition-specific topics)
│                             │
│ Examples:                   │
│ - trade/capture/input/       │
│   ACC-001_BOOK-001_SEC-001  │
│ - trade/capture/input/       │
│   ACC-002_BOOK-002_SEC-002  │
└────────────┬────────────────┘
             │
             │ Consume (ordered per partition)
             ▼
┌─────────────────────────────┐
│ SolaceTradeMessageConsumer │  (Updated)
│ - Subscribes to partition   │
│   specific topics           │
│ - Processes in order        │
└─────────────────────────────┘
```

## Implementation Details

### 1. Solace Docker Service

**File**: `docker-compose.yml`, `docker-compose.scale.yml`

**Added**:
- Solace PubSub+ Standard Docker image
- Health check configuration
- Network and volume configuration
- Environment variables for connection

**Configuration**:
```yaml
solace:
  image: solace/solace-pubsub-standard:latest
  environment:
    - username_admin_globalaccesslevel=admin
    - username_admin_password=admin
  ports:
    - "8080:8080"    # SEMP (Management API)
    - "55555:55555"  # SMF (Message Format)
  healthcheck:
    test: ["CMD-SHELL", "curl -f http://localhost:8080/SEMP || exit 1"]
    start_period: 120s  # Solace takes time to start
```

### 2. SolaceMessageRouter Component

**File**: `src/main/java/com/pb/synth/tradecapture/messaging/SolaceMessageRouter.java`

**Purpose**: Consume from single input topic and route to partition-specific topics

**Key Features**:
- Consumes from `trade/capture/input` (single topic)
- Extracts partition key from protobuf message
- Republishes to `trade/capture/input/{partitionKey}`
- Handles errors and DLQ publishing
- Metrics integration for routing performance
- Auto-starts when Solace is enabled

**Methods**:
- `start()` - Start router consumer
- `stop()` - Stop router
- `routeMessage(byte[] messageBytes)` - Extract partition key and republish
- `getPartitionTopic(String partitionKey)` - Build partition-specific topic name
- `sanitizeTopicName(String partitionKey)` - Sanitize partition key for topic names
- `publishToDlq(byte[] messageBytes, Exception error)` - Publish failed messages to DLQ
- `getStats()` - Get router statistics

### 3. Updated SolaceTradeMessageConsumer

**File**: `src/main/java/com/pb/synth/tradecapture/messaging/SolaceTradeMessageConsumer.java`

**Changes**:
- Subscribe to partition-specific topics using wildcard pattern: `trade/capture/input/>`
- Extract partition key from topic name for logging/metrics
- Process messages in order per partition (automatic via Solace topics)
- Removed direct consumption from single queue (router handles that)

**Configuration**:
- `messaging.solace.consumer.topic-pattern`: Wildcard pattern for subscription
- `messaging.solace.consumer.consumer-threads`: Number of consumer threads

### 4. Updated SolaceSwapBlotterPublisher

**File**: `src/main/java/com/pb/synth/tradecapture/messaging/SolaceSwapBlotterPublisher.java`

**Changes**:
- Publish to partition-specific output topics: `trade/capture/blotter/{partitionKey}`
- Maintain partition key in output for downstream ordering
- Topic name sanitization for special characters

**Configuration**:
- `messaging.solace.topics.output-pattern`: Output topic pattern with partition key placeholder

### 5. Configuration Updates

**File**: `src/main/resources/application.yml`

**Added Configuration**:
```yaml
messaging:
  solace:
    topics:
      input: trade/capture/input  # Single topic from upstream
      partition-pattern: trade/capture/input/{partitionKey}
      output-pattern: trade/capture/blotter/{partitionKey}
      router-dlq: trade/capture/router/dlq
      dlq: trade/capture/dlq
    router:
      enabled: true
      consumer-threads: 3
      max-retries: 3
    consumer:
      topic-pattern: trade/capture/input/>  # Wildcard for all partition topics
      consumer-threads: 3
```

### 6. Metrics Integration

**File**: `src/main/java/com/pb/synth/tradecapture/config/MetricsConfig.java`

**Added Metrics**:
- `solace.router.messages.routed` - Counter for messages routed
- `solace.router.routing.failures` - Counter for routing failures
- `solace.router.partitions.created` - Counter for partition topics created
- `solace.router.routing.time` - Timer for routing latency

### 7. Error Handling & DLQ

**Router Error Handling**:
- Failed routing → DLQ: `trade/capture/router/dlq`
- Invalid partition key → DLQ
- Serialization errors → DLQ
- Error metadata added to DLQ messages

**Consumer Error Handling**:
- Processing failures → DLQ: `trade/capture/dlq`
- Maintains existing DLQ logic

## Topic Naming Strategy

**Partition Key Format**: `ACC-001_BOOK-001_SEC-001`

**Topic Naming**:
- Input (upstream): `trade/capture/input`
- Partition-specific: `trade/capture/input/ACC-001_BOOK-001_SEC-001`
- Output: `trade/capture/blotter/ACC-001_BOOK-001_SEC-001`
- Router DLQ: `trade/capture/router/dlq`
- Processing DLQ: `trade/capture/dlq`

**Topic Name Sanitization**:
- Replaces invalid characters with underscore
- Keeps: alphanumeric, underscore, hyphen, forward slash
- Example: `ACC-001_BOOK-001_SEC-001` → `ACC-001_BOOK-001_SEC-001` (no change)

## Subscription Strategy

**Wildcard Subscription** (Implemented):
- Consumer subscribes to: `trade/capture/input/>`
- Automatically receives messages from all partition-specific topics
- Simple and scalable

## Benefits

1. **Automatic Ordering**: Solace topics provide ordering per partition automatically
2. **Reduced Locking**: No need for partition lock for message ordering (still needed for DB consistency)
3. **Parallel Processing**: Different partitions processed in parallel
4. **Scalability**: Easy to scale by adding more consumers
5. **Isolation**: Each partition isolated in its own topic
6. **Observability**: Can monitor per-partition topics

## Usage

### Enable Solace with Partition Routing

```yaml
messaging:
  provider: solace
  solace:
    enabled: true
    router:
      enabled: true
    consumer:
      topic-pattern: trade/capture/input/>
```

### Environment Variables

```bash
SOLACE_ENABLED=true
SOLACE_HOST=solace
SOLACE_PORT=55555
SOLACE_VPN=default
SOLACE_USERNAME=admin
SOLACE_PASSWORD=admin
SOLACE_ROUTER_ENABLED=true
```

### Docker Compose

```bash
# Start all services including Solace
docker-compose up -d

# Check Solace status
docker-compose logs solace

# Access Solace management (if available)
# http://localhost:8080
```

## Testing

### Unit Tests

**File**: `src/test/java/com/pb/synth/tradecapture/integration/messaging/SolaceIntegrationTest.java`

**Test Cases**:
- Router routes messages by partition key
- Router handles missing partition key
- Router sanitizes partition key for topic names
- Consumer subscribes to partition-specific topics

### Integration Tests

- End-to-end routing and consumption
- Multiple partition keys
- Error handling and DLQ publishing

## Monitoring

### Metrics Available

- `solace.router.messages.routed` - Total messages routed
- `solace.router.routing.failures` - Routing failures
- `solace.router.partitions.created` - Partition topics created
- `solace.router.routing.time` - Routing latency

### Router Statistics

Access router stats via `SolaceMessageRouter.getStats()`:
- Messages routed
- Routing failures
- Partitions created
- Running status

## Next Steps

1. **Complete Solace API Integration**: Replace boilerplate with actual Solace JMS API
2. **Add Integration Tests**: Test with real Solace instance or test container
3. **Performance Testing**: Measure router throughput and latency
4. **Monitoring Dashboards**: Create Grafana dashboards for router metrics
5. **Documentation**: Update operational runbooks

## Notes

- Router auto-starts when Solace is enabled
- Router is optional (can be disabled via `messaging.solace.router.enabled=false`)
- Consumer can fall back to single queue if router is disabled
- All components are backward compatible with existing configuration

