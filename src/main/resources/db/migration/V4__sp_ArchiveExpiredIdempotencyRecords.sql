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

