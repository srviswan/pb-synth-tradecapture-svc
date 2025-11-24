-- Initial database schema for pb-synth-tradecapture-svc
-- MS SQL Server with archive support

-- SwapBlotter table with archive support
CREATE TABLE swap_blotter (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    trade_id VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255) NOT NULL,
    swap_blotter_json NVARCHAR(MAX),
    version BIGINT DEFAULT 0,
    archive_flag BIT DEFAULT 0 NOT NULL,
    created_at DATETIME2 DEFAULT GETUTCDATE(),
    updated_at DATETIME2 DEFAULT GETUTCDATE()
);

-- Create unique constraint on trade_id
CREATE UNIQUE NONCLUSTERED INDEX UQ_swap_blotter_trade_id 
ON swap_blotter(trade_id) 
WHERE archive_flag = 0;

-- Create indexes
CREATE NONCLUSTERED INDEX idx_swap_blotter_trade_id 
ON swap_blotter(trade_id) 
INCLUDE (partition_key, archive_flag);

CREATE NONCLUSTERED INDEX idx_swap_blotter_partition_key 
ON swap_blotter(partition_key) 
INCLUDE (trade_id, archive_flag)
WHERE archive_flag = 0;

CREATE NONCLUSTERED INDEX idx_swap_blotter_archive_flag 
ON swap_blotter(archive_flag, created_at)
WHERE archive_flag = 1;

-- Partition State table
CREATE TABLE partition_state (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    partition_key VARCHAR(255) NOT NULL,
    position_state VARCHAR(50),
    last_sequence_number BIGINT DEFAULT 0,
    state_json NVARCHAR(MAX),
    version BIGINT DEFAULT 0,
    archive_flag BIT DEFAULT 0 NOT NULL,
    created_at DATETIME2 DEFAULT GETUTCDATE(),
    updated_at DATETIME2 DEFAULT GETUTCDATE()
);

-- Create unique constraint on partition_key
CREATE UNIQUE NONCLUSTERED INDEX UQ_partition_state_partition_key 
ON partition_state(partition_key) 
WHERE archive_flag = 0;

-- Create index for archive queries
CREATE NONCLUSTERED INDEX idx_partition_state_archive_flag 
ON partition_state(archive_flag, created_at)
WHERE archive_flag = 1;

-- Idempotency Record table
CREATE TABLE idempotency_record (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    trade_id VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    swap_blotter_id VARCHAR(255),
    archive_flag BIT DEFAULT 0 NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    completed_at DATETIME2,
    expires_at DATETIME2 NOT NULL
);

-- Create unique constraint on idempotency_key
CREATE UNIQUE NONCLUSTERED INDEX UQ_idempotency_record_idempotency_key 
ON idempotency_record(idempotency_key) 
WHERE archive_flag = 0;

-- Create indexes
CREATE NONCLUSTERED INDEX idx_idempotency_record_trade_id 
ON idempotency_record(trade_id) 
INCLUDE (idempotency_key, archive_flag)
WHERE archive_flag = 0;

CREATE NONCLUSTERED INDEX idx_idempotency_record_expires_at 
ON idempotency_record(expires_at) 
INCLUDE (archive_flag)
WHERE archive_flag = 0;

CREATE NONCLUSTERED INDEX idx_idempotency_record_archive_flag 
ON idempotency_record(archive_flag, created_at)
WHERE archive_flag = 1;
