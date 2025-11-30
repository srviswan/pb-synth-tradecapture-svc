-- Optimize indexes for concurrent access to reduce deadlocks
-- This migration adds composite indexes to reduce lock contention

-- Add composite index on idempotency_record for partition_key + status lookups
-- This helps reduce deadlocks when checking idempotency for trades in the same partition
CREATE NONCLUSTERED INDEX idx_idempotency_record_partition_status 
ON idempotency_record(partition_key, status) 
INCLUDE (idempotency_key, trade_id, expires_at)
WHERE archive_flag = 0;

-- Add index on partition_state for faster lookups during concurrent updates
-- This helps reduce lock contention on partition_state table
CREATE NONCLUSTERED INDEX idx_partition_state_partition_key_version 
ON partition_state(partition_key, version) 
INCLUDE (position_state, last_sequence_number)
WHERE archive_flag = 0;

-- Add composite index on swap_blotter for partition-based queries
-- This helps with queries that filter by partition_key and archive_flag
CREATE NONCLUSTERED INDEX idx_swap_blotter_partition_archive 
ON swap_blotter(partition_key, archive_flag) 
INCLUDE (trade_id, created_at)
WHERE archive_flag = 0;

