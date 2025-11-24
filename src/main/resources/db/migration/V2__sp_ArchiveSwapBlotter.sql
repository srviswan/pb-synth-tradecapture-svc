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

