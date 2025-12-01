# Kafka Issue with Multiple Instances

## Problem

When running 3 service instances, Kafka consumers were throwing `InconsistentGroupProtocolException`:

```
InconsistentGroupProtocolException: The group member's supported protocols are incompatible 
with those of existing members or first group member tried to join with empty protocol type 
or empty protocol list.
```

## Root Cause

1. **Topic had only 1 partition**: The `trade-capture-input` topic was created with 1 partition
2. **Each instance has concurrency=3**: Each service instance creates 3 consumer threads
3. **Total consumers = 9**: With 3 instances × 3 threads = 9 consumer threads
4. **Kafka limitation**: Kafka doesn't allow multiple consumers from the same consumer group to consume from the same partition
5. **Protocol conflict**: When 9 consumers try to join a consumer group with only 1 partition, Kafka can't properly assign partitions, causing protocol conflicts

## Solution

### Fix 1: Removed Duplicate Partition Assignment Strategy
- **Issue**: The `@KafkaListener` annotation had a `properties` field trying to set the partition assignment strategy, which was redundant and potentially conflicting
- **Fix**: Removed the `properties` field from `@KafkaListener` since the partition assignment strategy is already configured in `KafkaConfig.java`
- **File**: `src/main/java/com/pb/synth/tradecapture/messaging/KafkaTradeMessageConsumer.java`

### Fix 2: Increased Topic Partitions
- **Issue**: Topic had only 1 partition, limiting concurrent consumers
- **Fix**: Increased `trade-capture-input` topic partitions to 10
- **Command**: `kafka-topics --alter --topic trade-capture-input --partitions 10`
- **Result**: Now supports up to 10 concurrent consumers (3 instances × 3 threads = 9 consumers, which fits within 10 partitions)

## Configuration for 3 Instances

**Recommended Setup:**
- **Topic Partitions**: 10 (or more for future scaling)
- **Concurrency per Instance**: 3 threads
- **Total Consumers**: 9 (3 instances × 3 threads)
- **Partition Assignment Strategy**: `StickyAssignor` (for partition affinity)

This configuration allows:
- Each instance to process multiple partitions in parallel
- Proper load distribution across instances
- Partition affinity (same partition key messages stay on same consumer instance)

## Verification

After applying fixes:
1. Rebuild the service: `docker-compose build trade-capture-service`
2. Restart scaled deployment: `docker-compose -f docker-compose.scale.yml up -d`
3. Check consumer group status: Consumers should successfully join the group and partitions should be distributed across instances
4. Verify no more `InconsistentGroupProtocolException` errors in logs

## Future Considerations

- **Auto-create topics with correct partition count**: Consider configuring Kafka to auto-create topics with a default partition count (e.g., 10) for new topics
- **Dynamic partition scaling**: For production, consider using Kafka's partition reassignment tools if you need to scale partitions dynamically
- **Monitor consumer lag**: Use Kafka consumer group tools to monitor lag and ensure proper load distribution

