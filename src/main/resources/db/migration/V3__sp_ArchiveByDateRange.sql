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

