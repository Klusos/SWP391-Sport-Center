/*
  DRAFT ONLY — rollback proposal for the explicit candidate list in
  demo-membership-duration-normalization-apply.sql. Default @execute = 0.
  Requires a successful approved apply and exact post-apply values. A verified
  full-database backup remains the primary recovery method.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @expectedServer sysname = N'LAPTOP-416VBDLK';
DECLARE @expectedDatabase sysname = N'SportCenterDemo';
DECLARE @execute bit = 0;
DECLARE @ownerApprovedRollback bit = 0;
DECLARE @approvedBy nvarchar(128) = NULL;

IF CONVERT(sysname, SERVERPROPERTY('ServerName')) <> @expectedServer
   OR DB_NAME() <> @expectedDatabase
    THROW 50506, 'Target identity differs from the reviewed local SportCenterDemo instance. Stop.', 1;

DECLARE @targets table (
    membership_id int NOT NULL PRIMARY KEY,
    expected_start_date date NOT NULL,
    expected_current_end_date date NOT NULL,
    restore_end_date date NOT NULL
);
INSERT @targets (membership_id, expected_start_date, expected_current_end_date, restore_end_date)
VALUES (2, '2026-11-25', '2026-12-25', '2027-05-25'),
       (3, '2027-05-25', '2027-06-25', '2027-11-25');

SELECT t.membership_id,
       m.member_id,
       m.start_date,
       m.end_date AS current_exclusive_end_date,
       t.restore_end_date AS rollback_exclusive_end_date,
       m.status,
       m.purchase_payment_id
FROM @targets AS t
LEFT JOIN dbo.membership AS m ON m.id = t.membership_id
ORDER BY t.membership_id;

IF @execute = 0
BEGIN
    PRINT 'ROLLBACK PREVIEW ONLY: no rows were changed.';
    RETURN;
END;

IF @ownerApprovedRollback <> 1 OR NULLIF(LTRIM(RTRIM(@approvedBy)), N'') IS NULL
    THROW 50507, 'Rollback requires explicit owner approval and approver identity.', 1;

IF EXISTS (
    SELECT 1
    FROM @targets AS t
    LEFT JOIN dbo.membership AS m ON m.id = t.membership_id
    WHERE m.id IS NULL
       OR m.start_date <> t.expected_start_date
       OR m.end_date <> t.expected_current_end_date
       OR m.status <> 'CANCELLED'
       OR m.purchase_payment_id IS NOT NULL
)
    THROW 50508, 'Current rows differ from the reviewed post-apply values. Stop; do not overwrite later changes.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @restored table (
        membership_id int NOT NULL,
        old_end_date date NOT NULL,
        restored_end_date date NOT NULL
    );

    UPDATE m
       SET end_date = t.restore_end_date
    OUTPUT inserted.id, deleted.end_date, inserted.end_date
      INTO @restored (membership_id, old_end_date, restored_end_date)
    FROM dbo.membership AS m
    JOIN @targets AS t ON t.membership_id = m.id
    WHERE m.start_date = t.expected_start_date
      AND m.end_date = t.expected_current_end_date
      AND m.status = 'CANCELLED'
      AND m.purchase_payment_id IS NULL;

    IF (SELECT COUNT(*) FROM @restored) <> (SELECT COUNT(*) FROM @targets)
        THROW 50509, 'Rollback row count did not match the explicit target list.', 1;

    SELECT membership_id, old_end_date, restored_end_date FROM @restored ORDER BY membership_id;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
