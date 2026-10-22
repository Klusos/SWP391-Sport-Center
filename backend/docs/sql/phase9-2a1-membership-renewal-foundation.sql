-- Phase 9.2A.1-A: nullable renewal-source link for future scheduled requests.
-- Back up the database, stop application writers, then run this standalone batch manually.
-- This migration does not create renewal requests or alter existing payment rows.
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() <> N'SportCenterDB'
    THROW 50941, 'Select SportCenterDB before applying the renewal foundation migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50942, 'Run the renewal foundation migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.payment', N'U') IS NULL OR OBJECT_ID(N'dbo.membership', N'U') IS NULL
    THROW 50943, 'Expected dbo.payment and dbo.membership before applying the renewal foundation migration.', 1;
IF NOT EXISTS (
    SELECT 1 FROM sys.columns c
    WHERE c.object_id = OBJECT_ID(N'dbo.membership') AND c.name = N'id'
      AND c.user_type_id = TYPE_ID(N'int') AND c.is_nullable = 0 AND c.is_identity = 1
)
    THROW 50944, 'dbo.membership.id must be the expected non-null INT identity before adding a payment reference.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter:Phase9.2A.1-A:PaymentRenewalMembership',
        @LockMode = N'Exclusive', @LockOwner = N'Transaction', @LockTimeout = 30000;
    IF @lockResult < 0
        THROW 50945, 'Could not acquire the renewal foundation migration lock.', 1;

    -- Dynamic DDL defers compilation until after the optional column has been created.
    IF COL_LENGTH(N'dbo.payment', N'renewal_membership_id') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment ADD renewal_membership_id INT NULL;';

    IF EXISTS (
        SELECT 1
        FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = OBJECT_ID(N'dbo.payment') AND c.name = N'renewal_membership_id'
          AND (t.name <> N'int' OR c.precision <> 10 OR c.scale <> 0 OR c.is_nullable <> 1
               OR c.is_identity <> 0 OR c.is_computed <> 0 OR c.default_object_id <> 0)
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = OBJECT_ID(N'dbo.payment') AND c.name = N'renewal_membership_id'
          AND t.name = N'int' AND c.precision = 10 AND c.scale = 0 AND c.is_nullable = 1
          AND c.is_identity = 0 AND c.is_computed = 0 AND c.default_object_id = 0
    )
        THROW 50946, 'dbo.payment.renewal_membership_id has an incompatible type or column definition.', 1;

    -- An existing relationship on this column under another name is ambiguous; do not layer
    -- a second FK over it. A same-named but incompatible FK is caught by final validation.
    IF EXISTS (
        SELECT 1
        FROM sys.foreign_key_columns fkc
        JOIN sys.foreign_keys fk ON fk.object_id = fkc.constraint_object_id
        JOIN sys.columns child ON child.object_id = fkc.parent_object_id AND child.column_id = fkc.parent_column_id
        WHERE fkc.parent_object_id = OBJECT_ID(N'dbo.payment') AND child.name = N'renewal_membership_id'
          AND fk.name <> N'fk_payment_renewal_membership'
    )
        THROW 50947, 'An unexpected foreign key already uses dbo.payment.renewal_membership_id.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys
        WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'fk_payment_renewal_membership'
    )
        EXEC sys.sp_executesql N'ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT fk_payment_renewal_membership FOREIGN KEY (renewal_membership_id) REFERENCES dbo.membership(id);';

    IF EXISTS (
        SELECT 1 FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal > 0
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.payment') AND i.is_unique = 1
          AND c.name = N'renewal_membership_id' AND i.name <> N'ux_payment_renewal_membership'
    )
        THROW 50948, 'An unexpected unique index already uses dbo.payment.renewal_membership_id.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.payment') AND name = N'ux_payment_renewal_membership'
    )
        EXEC sys.sp_executesql N'CREATE UNIQUE INDEX ux_payment_renewal_membership ON dbo.payment(renewal_membership_id) WHERE renewal_membership_id IS NOT NULL;';

    IF NOT EXISTS (
        SELECT 1
        FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns child ON child.object_id = fkc.parent_object_id AND child.column_id = fkc.parent_column_id
        JOIN sys.columns parent ON parent.object_id = fkc.referenced_object_id AND parent.column_id = fkc.referenced_column_id
        WHERE fk.parent_object_id = OBJECT_ID(N'dbo.payment')
          AND fk.name = N'fk_payment_renewal_membership'
          AND fk.referenced_object_id = OBJECT_ID(N'dbo.membership')
          AND child.name = N'renewal_membership_id' AND parent.name = N'id'
          AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1
          AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
          AND fk.is_disabled = 0 AND fk.is_not_trusted = 0 AND fk.is_not_for_replication = 0
    )
        THROW 50949, 'The renewal membership foreign key is missing, untrusted, disabled, or targets an unexpected column.', 1;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.payment') AND i.name = N'ux_payment_renewal_membership'
          AND i.is_unique = 1 AND i.is_disabled = 0 AND i.is_hypothetical = 0 AND i.has_filter = 1
          AND ic.key_ordinal = 1 AND ic.is_included_column = 0 AND c.name = N'renewal_membership_id'
          AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = i.object_id
               AND x.index_id = i.index_id AND x.key_ordinal > 0) = 1
          AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = i.object_id
               AND x.index_id = i.index_id AND x.is_included_column = 1) = 0
          AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(UPPER(i.filter_definition), N'[', N''), N']', N''), N' ', N''), N'(', N''), N')', N'')
              = N'RENEWAL_MEMBERSHIP_IDISNOTNULL'
    )
        THROW 50950, 'The renewal membership unique index is missing or has an incompatible key or filter.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
