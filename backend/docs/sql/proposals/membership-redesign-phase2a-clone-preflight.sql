/*
  DRAFT ONLY 鈥?Phase 2C.1 clone-rehearsal baseline and preflight.
  NOT APPROVED TO RUN. This script writes only a sidecar hash manifest into the
  explicitly named restored clone. It does not change business rows.

  Before a separately approved rehearsal, replace BOTH required identity values
  below using identity evidence from the restored clone. Do not guess them.
  The guard always rejects SportCenterDemo, even if the variables are changed.
  Run only after an owner-approved backup/restore and clone confirmation.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @expectedCloneServerProperty sysname = N'LAPTOP-416VBDLK';
DECLARE @expectedCloneDatabase sysname = N'SportCenterDemo_Phase2C4_Rehearsal_20261119';

IF @expectedCloneServerProperty = N'__SET_VERIFIED_CLONE_SERVERPROPERTY_SERVERNAME__'
   OR @expectedCloneDatabase = N'__SET_VERIFIED_CLONE_DATABASE_NAME__'
   OR LEN(LTRIM(RTRIM(@expectedCloneServerProperty))) = 0
   OR LEN(LTRIM(RTRIM(@expectedCloneDatabase))) = 0
    THROW 50701, 'Set both verified clone identity values before rehearsal. No manifest was written.', 1;

IF UPPER(@expectedCloneDatabase) = N'SPORTCENTERDEMO'
    THROW 50702, 'Refusing to run rehearsal preflight on the original SportCenterDemo database.', 1;

IF CONVERT(sysname,SERVERPROPERTY('ServerName')) <> @expectedCloneServerProperty
   OR DB_NAME() <> @expectedCloneDatabase
   OR SCHEMA_NAME() <> N'dbo'
    THROW 50703, 'Clone identity mismatch. No manifest was written.', 1;

IF OBJECT_ID(N'dbo.membership',N'U') IS NULL
   OR OBJECT_ID(N'dbo.payment',N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_upgrade',N'U') IS NULL
   OR OBJECT_ID(N'dbo.membership_cancellation_request',N'U') IS NULL
    THROW 50704, 'A baseline source table is missing. No manifest was written.', 1;

IF OBJECT_ID(N'dbo.phase2a_clone_rehearsal_manifest',N'U') IS NOT NULL
   OR OBJECT_ID(N'dbo.phase2a_clone_rehearsal_manifest_run',N'U') IS NOT NULL
    THROW 50705, 'A clone rehearsal manifest already exists. Archive/review it; do not overwrite it.', 1;

SELECT @@SERVERNAME AS server_name,
       CONVERT(sysname,SERVERPROPERTY('ServerName')) AS serverproperty_server_name,
       CONVERT(sysname,SERVERPROPERTY('InstanceName')) AS instance_name,
       DB_NAME() AS database_name,SCHEMA_NAME() AS default_schema;

-- Confirm the expected source objects and their current column metadata before
-- recording the baseline. Review this output against the approved Phase 1 report.
SELECT t.name AS table_name,c.column_id,c.name AS column_name,ty.name AS data_type,
       c.max_length,c.precision,c.scale,c.is_nullable
FROM sys.tables t
JOIN sys.columns c ON c.object_id=t.object_id
JOIN sys.types ty ON ty.user_type_id=c.user_type_id
WHERE t.schema_id=SCHEMA_ID(N'dbo')
  AND t.name IN (N'membership',N'payment',N'membership_upgrade',N'membership_cancellation_request')
ORDER BY t.name,c.column_id;

-- Fail closed if the restored clone has any missing or unexpected original
-- columns. Phase 1's written report summarizes some tables; the full clone
-- metadata result above must be retained as the rehearsal's schema evidence.
DECLARE @expected_columns TABLE(table_name sysname NOT NULL,column_name sysname NOT NULL,
                               PRIMARY KEY(table_name,column_name));
INSERT @expected_columns(table_name,column_name) VALUES
 (N'membership',N'id'),(N'membership',N'member_id'),(N'membership',N'plan_id'),
 (N'membership',N'auto_renew'),(N'membership',N'start_date'),(N'membership',N'end_date'),
 (N'membership',N'status'),(N'membership',N'purchase_payment_id'),(N'membership',N'cancelled_at'),
 (N'membership',N'cancel_reason'),
 (N'payment',N'id'),(N'payment',N'member_id'),(N'payment',N'amount'),(N'payment',N'method'),
 (N'payment',N'purpose'),(N'payment',N'reference_id'),(N'payment',N'auto_renew'),(N'payment',N'status'),
 (N'payment',N'transaction_code'),(N'payment',N'processed_by'),(N'payment',N'note'),
 (N'payment',N'created_at'),(N'payment',N'paid_at'),(N'payment',N'refunded_at'),(N'payment',N'refunded_by'),
 (N'membership_upgrade',N'id'),(N'membership_upgrade',N'payment_id'),
 (N'membership_upgrade',N'source_membership_id'),(N'membership_upgrade',N'target_plan_id'),
 (N'membership_upgrade',N'remaining_days'),(N'membership_upgrade',N'expires_at'),
 (N'membership_upgrade',N'created_at'),(N'membership_upgrade',N'result_membership_id'),
 (N'membership_cancellation_request',N'id'),(N'membership_cancellation_request',N'membership_id'),
 (N'membership_cancellation_request',N'member_id'),(N'membership_cancellation_request',N'reason'),
 (N'membership_cancellation_request',N'status'),(N'membership_cancellation_request',N'requested_at'),
 (N'membership_cancellation_request',N'decided_by'),(N'membership_cancellation_request',N'decided_at'),
 (N'membership_cancellation_request',N'decision_note');

IF EXISTS (
    SELECT table_name,column_name FROM @expected_columns
    EXCEPT
    SELECT t.name,c.name FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id
    WHERE t.schema_id=SCHEMA_ID(N'dbo')
      AND t.name IN (N'membership',N'payment',N'membership_upgrade',N'membership_cancellation_request')
) OR EXISTS (
    SELECT t.name,c.name FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id
    WHERE t.schema_id=SCHEMA_ID(N'dbo')
      AND t.name IN (N'membership',N'payment',N'membership_upgrade',N'membership_cancellation_request')
    EXCEPT
    SELECT table_name,column_name FROM @expected_columns
)
    THROW 50706, 'Clone table columns differ from the manifest field contract. Review metadata; no manifest was written.', 1;

SELECT entity_name,state,row_count
FROM (
    SELECT 'Membership' AS entity_name,status AS state,COUNT_BIG(*) AS row_count
    FROM dbo.membership GROUP BY status
    UNION ALL
    SELECT 'Payment',CONCAT(purpose,'/',status),COUNT_BIG(*)
    FROM dbo.payment WHERE purpose IN ('MEMBERSHIP','UPGRADE') GROUP BY purpose,status
    UNION ALL
    SELECT 'MembershipUpgrade',CASE WHEN result_membership_id IS NULL THEN 'NO_RESULT' ELSE 'HAS_RESULT' END,COUNT_BIG(*)
    FROM dbo.membership_upgrade GROUP BY CASE WHEN result_membership_id IS NULL THEN 'NO_RESULT' ELSE 'HAS_RESULT' END
    UNION ALL
    SELECT 'MembershipCancellationRequest',status,COUNT_BIG(*)
    FROM dbo.membership_cancellation_request GROUP BY status
) AS baseline_counts
ORDER BY entity_name,state;

BEGIN TRY
    BEGIN TRANSACTION;

    CREATE TABLE dbo.phase2a_clone_rehearsal_manifest (
        entity_name varchar(40) NOT NULL,
        record_id int NOT NULL,
        row_hash binary(32) NOT NULL,
        captured_at datetime2(6) NOT NULL CONSTRAINT df_phase2a_clone_manifest_captured_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT pk_phase2a_clone_rehearsal_manifest PRIMARY KEY (entity_name,record_id),
        CONSTRAINT ck_phase2a_clone_manifest_entity CHECK (
            entity_name IN ('Membership','Payment','MembershipUpgrade','MembershipCancellationRequest'))
    );

    CREATE TABLE dbo.phase2a_clone_rehearsal_manifest_run (
        manifest_key tinyint NOT NULL CONSTRAINT pk_phase2a_clone_manifest_run PRIMARY KEY,
        server_name sysname NOT NULL,
        database_name sysname NOT NULL,
        captured_at datetime2(6) NOT NULL,
        membership_count bigint NOT NULL,
        payment_count bigint NOT NULL,
        membership_upgrade_count bigint NOT NULL,
        cancellation_request_count bigint NOT NULL,
        manifest_record_count bigint NOT NULL,
        is_complete bit NOT NULL,
        CONSTRAINT ck_phase2a_clone_manifest_run_key CHECK (manifest_key=1),
        CONSTRAINT ck_phase2a_clone_manifest_run_counts CHECK (
            membership_count>=0 AND payment_count>=0 AND membership_upgrade_count>=0
            AND cancellation_request_count>=0 AND manifest_record_count>=0)
    );

    INSERT dbo.phase2a_clone_rehearsal_manifest_run(
        manifest_key,server_name,database_name,captured_at,membership_count,payment_count,
        membership_upgrade_count,cancellation_request_count,manifest_record_count,is_complete)
    SELECT 1,@expectedCloneServerProperty,@expectedCloneDatabase,SYSUTCDATETIME(),
           (SELECT COUNT_BIG(*) FROM dbo.membership),
           (SELECT COUNT_BIG(*) FROM dbo.payment),
           (SELECT COUNT_BIG(*) FROM dbo.membership_upgrade),
           (SELECT COUNT_BIG(*) FROM dbo.membership_cancellation_request),
           (SELECT COUNT_BIG(*) FROM dbo.membership)
             +(SELECT COUNT_BIG(*) FROM dbo.payment)
             +(SELECT COUNT_BIG(*) FROM dbo.membership_upgrade)
             +(SELECT COUNT_BIG(*) FROM dbo.membership_cancellation_request),0;

    INSERT dbo.phase2a_clone_rehearsal_manifest(entity_name,record_id,row_hash)
    SELECT 'Membership',m.id,HASHBYTES('SHA2_256',CONVERT(varbinary(max),j.row_json))
    FROM dbo.membership m
    CROSS APPLY (SELECT (SELECT m.member_id,m.plan_id,m.auto_renew,m.start_date,m.end_date,m.status,
                                m.purchase_payment_id,m.cancelled_at,m.cancel_reason
                         FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES) AS row_json) j;

    INSERT dbo.phase2a_clone_rehearsal_manifest(entity_name,record_id,row_hash)
    SELECT 'Payment',p.id,HASHBYTES('SHA2_256',CONVERT(varbinary(max),j.row_json))
    FROM dbo.payment p
    CROSS APPLY (SELECT (SELECT p.member_id,p.amount,p.method,p.purpose,p.reference_id,p.auto_renew,p.status,
                                p.transaction_code,p.processed_by,p.note,p.created_at,p.paid_at,
                                p.refunded_at,p.refunded_by
                         FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES) AS row_json) j;

    INSERT dbo.phase2a_clone_rehearsal_manifest(entity_name,record_id,row_hash)
    SELECT 'MembershipUpgrade',u.id,HASHBYTES('SHA2_256',CONVERT(varbinary(max),j.row_json))
    FROM dbo.membership_upgrade u
    CROSS APPLY (SELECT (SELECT u.payment_id,u.source_membership_id,u.target_plan_id,u.remaining_days,
                                u.expires_at,u.created_at,u.result_membership_id
                         FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES) AS row_json) j;

    INSERT dbo.phase2a_clone_rehearsal_manifest(entity_name,record_id,row_hash)
    SELECT 'MembershipCancellationRequest',r.id,HASHBYTES('SHA2_256',CONVERT(varbinary(max),j.row_json))
    FROM dbo.membership_cancellation_request r
    CROSS APPLY (SELECT (SELECT r.membership_id,r.member_id,r.reason,r.status,r.requested_at,
                                r.decided_by,r.decided_at,r.decision_note
                         FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES) AS row_json) j;

    IF (SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest)
       <> (SELECT manifest_record_count FROM dbo.phase2a_clone_rehearsal_manifest_run WHERE manifest_key=1)
       OR (SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest WHERE entity_name='Membership')
          <> (SELECT membership_count FROM dbo.phase2a_clone_rehearsal_manifest_run WHERE manifest_key=1)
       OR (SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest WHERE entity_name='Payment')
          <> (SELECT payment_count FROM dbo.phase2a_clone_rehearsal_manifest_run WHERE manifest_key=1)
       OR (SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest WHERE entity_name='MembershipUpgrade')
          <> (SELECT membership_upgrade_count FROM dbo.phase2a_clone_rehearsal_manifest_run WHERE manifest_key=1)
       OR (SELECT COUNT_BIG(*) FROM dbo.phase2a_clone_rehearsal_manifest WHERE entity_name='MembershipCancellationRequest')
          <> (SELECT cancellation_request_count FROM dbo.phase2a_clone_rehearsal_manifest_run WHERE manifest_key=1)
        THROW 50707, 'Baseline manifest record counts are incomplete. Manifest transaction rolled back.', 1;

    UPDATE dbo.phase2a_clone_rehearsal_manifest_run SET is_complete=1 WHERE manifest_key=1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Row-level manifest: hashes only, no names/contact details/notes or credentials.
SELECT entity_name,record_id,CONVERT(varchar(64),row_hash,2) AS row_sha256,captured_at
FROM dbo.phase2a_clone_rehearsal_manifest
ORDER BY entity_name,record_id;
SELECT * FROM dbo.phase2a_clone_rehearsal_manifest_run;

-- Rehearsal gates. Every returned anomaly must be reviewed; this script repairs none.
WITH upgrade_results AS (
    SELECT u.id,u.source_membership_id,u.payment_id,u.result_membership_id,
           COUNT_BIG(*) OVER (PARTITION BY u.source_membership_id) AS result_count
    FROM dbo.membership_upgrade u WHERE u.result_membership_id IS NOT NULL
)
SELECT 'DUPLICATE_SUCCESSFUL_UPGRADE_RESULT' AS issue,CONVERT(varchar(30),source_membership_id) AS record_key,
       CONVERT(varchar(30),id) AS related_record_id
FROM upgrade_results WHERE result_count>1
UNION ALL
SELECT 'SUCCESS_UPGRADE_PAYMENT_WITHOUT_ATTEMPT',CONVERT(varchar(30),p.id),NULL
FROM dbo.payment p
WHERE p.purpose='UPGRADE' AND p.status='SUCCESS'
  AND NOT EXISTS (SELECT 1 FROM dbo.membership_upgrade u WHERE u.payment_id=p.id)
UNION ALL
SELECT 'SUCCESS_UPGRADE_ATTEMPT_WITHOUT_RESULT',CONVERT(varchar(30),u.id),CONVERT(varchar(30),u.payment_id)
FROM dbo.membership_upgrade u JOIN dbo.payment p ON p.id=u.payment_id
WHERE p.purpose='UPGRADE' AND p.status='SUCCESS' AND u.result_membership_id IS NULL;

-- Reuse the approved Phase 2A preflight after reviewing its identity guard and
-- replacing the live-only guard with this clone guard; do not include live PII.

