-- Initial database schema for pb-synth-tradecapture-svc
-- MS SQL Server with table partitioning and archive support

-- Create partition function for date-based partitioning (by created_at)
CREATE PARTITION FUNCTION PF_TradeCapture_DateRange (DATETIME2)
AS RANGE RIGHT FOR VALUES 
    ('2024-01-01', '2024-07-01', '2025-01-01', '2025-07-01', '2026-01-01');

-- Create partition scheme
CREATE PARTITION SCHEME PS_TradeCapture_DateRange
AS PARTITION PF_TradeCapture_DateRange
ALL TO ([PRIMARY]);

-- SwapBlotter table with partitioning and archive support
CREATE TABLE swap_blotter (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    trade_id VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255) NOT NULL,
    swap_blotter_json NVARCHAR(MAX),
    version BIGINT DEFAULT 0,
    archive_flag BIT DEFAULT 0 NOT NULL,
    created_at DATETIME2 DEFAULT GETUTCDATE(),
    updated_at DATETIME2 DEFAULT GETUTCDATE()
) ON PS_TradeCapture_DateRange(created_at);

-- Create unique constraint on trade_id (non-partitioned index)
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

-- Partition State table with partitioning
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
) ON PS_TradeCapture_DateRange(created_at);

-- Create unique constraint on partition_key (non-partitioned index)
CREATE UNIQUE NONCLUSTERED INDEX UQ_partition_state_partition_key 
ON partition_state(partition_key) 
WHERE archive_flag = 0;

-- Create index for archive queries
CREATE NONCLUSTERED INDEX idx_partition_state_archive_flag 
ON partition_state(archive_flag, created_at)
WHERE archive_flag = 1;

-- Idempotency Record table with partitioning
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
) ON PS_TradeCapture_DateRange(created_at);

-- Create unique constraint on idempotency_key (non-partitioned index)
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

-- Stored procedure for archiving records
CREATE PROCEDURE sp_ArchiveSwapBlotter
    @TradeId VARCHAR(255),
    @ArchiveDate DATETIME2 = NULL
AS
BEGIN
    SET NOCOUNT ON;
    
    IF @ArchiveDate IS NULL
        SET @ArchiveDate = GETUTCDATE();
    
    UPDATE swap_blotter
    SET archive_flag = 1,
        updated_at = @ArchiveDate
    WHERE trade_id = @TradeId
      AND archive_flag = 0;
    
    -- Also archive related partition state if exists
    UPDATE partition_state
    SET archive_flag = 1,
        updated_at = @ArchiveDate
    WHERE partition_key IN (
        SELECT partition_key 
        FROM swap_blotter 
        WHERE trade_id = @TradeId
    )
    AND archive_flag = 0;
    
    -- Archive idempotency record
    UPDATE idempotency_record
    SET archive_flag = 1,
        completed_at = @ArchiveDate
    WHERE trade_id = @TradeId
      AND archive_flag = 0;
END;
GO

-- Stored procedure for archiving by date range
CREATE PROCEDURE sp_ArchiveByDateRange
    @StartDate DATETIME2,
    @EndDate DATETIME2,
    @ArchiveDate DATETIME2 = NULL
AS
BEGIN
    SET NOCOUNT ON;
    
    IF @ArchiveDate IS NULL
        SET @ArchiveDate = GETUTCDATE();
    
    -- Archive swap_blotter records
    UPDATE swap_blotter
    SET archive_flag = 1,
        updated_at = @ArchiveDate
    WHERE created_at >= @StartDate
      AND created_at < @EndDate
      AND archive_flag = 0;
    
    -- Archive partition_state records
    UPDATE partition_state
    SET archive_flag = 1,
        updated_at = @ArchiveDate
    WHERE created_at >= @StartDate
      AND created_at < @EndDate
      AND archive_flag = 0;
    
    -- Archive idempotency_record records
    UPDATE idempotency_record
    SET archive_flag = 1,
        completed_at = @ArchiveDate
    WHERE created_at >= @StartDate
      AND created_at < @EndDate
      AND archive_flag = 0;
END;
GO

-- Stored procedure for archiving expired idempotency records
CREATE PROCEDURE sp_ArchiveExpiredIdempotencyRecords
    @ArchiveDate DATETIME2 = NULL
AS
BEGIN
    SET NOCOUNT ON;
    
    IF @ArchiveDate IS NULL
        SET @ArchiveDate = GETUTCDATE();
    
    UPDATE idempotency_record
    SET archive_flag = 1,
        completed_at = @ArchiveDate
    WHERE expires_at < GETUTCDATE()
      AND archive_flag = 0;
END;
GO

-- Function to get partition number for a date
CREATE FUNCTION fn_GetPartitionNumber(@Date DATETIME2)
RETURNS INT
AS
BEGIN
    DECLARE @PartitionNumber INT;
    
    SELECT @PartitionNumber = $PARTITION.PF_TradeCapture_DateRange(@Date);
    
    RETURN @PartitionNumber;
END;
GO
