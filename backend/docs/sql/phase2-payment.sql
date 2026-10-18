-- SQL Server 2016+ manual migration for Flow 3 (Payment).
-- Run as a standalone batch in SportCenterDB with writers stopped and a verified backup available.
-- Hibernate remains ddl-auto=validate. This script does not execute automatically.
-- reference_id is purpose-scoped (membership_plan, course_registration, or personal_training_booking),
-- so it intentionally has no polymorphic FK. Application services validate each referenced record.
-- Existing payment tables are metadata-checked before any constraint changes. Existing named CHECK
-- constraints are replaced transactionally to apply the current purpose/status contract.
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
    THROW 50601, 'Select SportCenterDB before running the payment migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50602, 'Run this migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.users', N'U') IS NULL
    THROW 50603, 'Expected dbo.users before creating payment foreign keys.', 1;
IF NOT EXISTS (
    SELECT 1 FROM sys.columns c
    WHERE c.object_id = OBJECT_ID(N'dbo.users') AND c.name = N'id'
      AND c.system_type_id = TYPE_ID(N'int') AND c.user_type_id = c.system_type_id
)
    THROW 50604, 'dbo.users.id must be SQL Server INT for payment foreign keys.', 1;
IF OBJECT_ID(N'dbo.payment') IS NOT NULL AND OBJECT_ID(N'dbo.payment', N'U') IS NULL
    THROW 50605, 'dbo.payment is occupied by a non-table object.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.Phase2.Payment.Schema',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50606, 'Could not acquire the Payment migration lock; retry with writers stopped.', 1;

    IF OBJECT_ID(N'dbo.payment', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.payment (
            id               INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_payment PRIMARY KEY,
            member_id        INT NOT NULL,
            amount           DECIMAL(12,2) NOT NULL,
            method           VARCHAR(20) NOT NULL,
            purpose          VARCHAR(30) NOT NULL,
            reference_id     INT NOT NULL,
            auto_renew       BIT NULL,
            status           VARCHAR(20) NOT NULL,
            transaction_code VARCHAR(40) NOT NULL,
            processed_by     INT NULL,
            note             NVARCHAR(500) NULL,
            created_at       DATETIME2(6) NOT NULL,
            paid_at          DATETIME2(6) NULL,
            CONSTRAINT uq_payment_txn UNIQUE (transaction_code),
            CONSTRAINT fk_payment_member FOREIGN KEY (member_id) REFERENCES dbo.users(id),
            CONSTRAINT fk_payment_processed_by FOREIGN KEY (processed_by) REFERENCES dbo.users(id),
            CONSTRAINT ck_payment_method CHECK (method IN ('ONLINE', 'COUNTER')),
            CONSTRAINT ck_payment_purpose CHECK (purpose IN ('MEMBERSHIP', 'COURSE_REGISTRATION', 'PT_BOOKING')),
            CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'SUCCESS', 'CANCELLED', 'REFUND_REQUIRED')),
            CONSTRAINT ck_payment_amount CHECK (amount >= 0)
        );
    END
    ELSE
    BEGIN
        IF EXISTS (SELECT 1 FROM dbo.payment WHERE purpose = 'COURSE_REGISTRATION')
        BEGIN
            IF OBJECT_ID(N'dbo.course_registration', N'U') IS NULL
                THROW 50607, 'dbo.payment has COURSE_REGISTRATION rows but dbo.course_registration is missing; inspect reference_id values before migration.', 1;
            IF EXISTS (
                SELECT 1 FROM dbo.payment p
                WHERE p.purpose = 'COURSE_REGISTRATION'
                  AND NOT EXISTS (SELECT 1 FROM dbo.course_registration r WHERE r.id = p.reference_id)
            )
                THROW 50614, 'A COURSE_REGISTRATION payment does not reference an existing CourseRegistration; inspect legacy reference IDs before migration.', 1;
            IF NOT EXISTS (
                SELECT 1 FROM sys.check_constraints
                WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_purpose'
                  AND definition LIKE N'%PT_BOOKING%'
            ) OR NOT EXISTS (
                SELECT 1 FROM sys.check_constraints
                WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_status'
                  AND definition LIKE N'%REFUND_REQUIRED%'
            )
                THROW 50607, 'dbo.payment has COURSE_REGISTRATION rows with legacy or unknown CHECK contracts; review the original reference_id meaning manually.', 1;
        END;

        DECLARE @expectedColumns TABLE (
            column_name SYSNAME PRIMARY KEY,
            system_type_id TINYINT NOT NULL,
            max_length SMALLINT NULL,
            precision_value TINYINT NULL,
            scale_value TINYINT NULL,
            is_nullable BIT NOT NULL,
            is_identity BIT NOT NULL
        );
        INSERT @expectedColumns VALUES
            (N'id', TYPE_ID(N'int'), 4, 10, 0, 0, 1),
            (N'member_id', TYPE_ID(N'int'), 4, 10, 0, 0, 0),
            (N'amount', TYPE_ID(N'decimal'), 9, 12, 2, 0, 0),
            (N'method', TYPE_ID(N'varchar'), 20, 0, 0, 0, 0),
            (N'purpose', TYPE_ID(N'varchar'), 30, 0, 0, 0, 0),
            (N'reference_id', TYPE_ID(N'int'), 4, 10, 0, 0, 0),
            (N'auto_renew', TYPE_ID(N'bit'), 1, 1, 0, 1, 0),
            (N'status', TYPE_ID(N'varchar'), 20, 0, 0, 0, 0),
            (N'transaction_code', TYPE_ID(N'varchar'), 40, 0, 0, 0, 0),
            (N'processed_by', TYPE_ID(N'int'), 4, 10, 0, 1, 0),
            (N'note', TYPE_ID(N'nvarchar'), 1000, 0, 0, 1, 0),
            (N'created_at', TYPE_ID(N'datetime2'), 8, NULL, 6, 0, 0),
            (N'paid_at', TYPE_ID(N'datetime2'), 8, NULL, 6, 1, 0);

        IF (SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.payment'))
              <> (SELECT COUNT(*) FROM @expectedColumns)
           OR EXISTS (
                SELECT 1
                FROM @expectedColumns e
                LEFT JOIN sys.columns c
                  ON c.object_id = OBJECT_ID(N'dbo.payment') AND c.name = e.column_name
                WHERE c.column_id IS NULL
                   OR c.system_type_id <> e.system_type_id
                   OR c.user_type_id <> c.system_type_id
                   OR (e.max_length IS NOT NULL AND c.max_length <> e.max_length)
                   OR (e.precision_value IS NOT NULL AND c.precision <> e.precision_value)
                   OR (e.scale_value IS NOT NULL AND c.scale <> e.scale_value)
                   OR c.is_nullable <> e.is_nullable
                   OR c.is_identity <> e.is_identity
                   OR c.is_computed <> 0
                   OR c.generated_always_type <> 0
            )
            THROW 50608, 'Existing dbo.payment columns do not match the Payment migration contract; inspect the table before migration.', 1;

        IF NOT EXISTS (
            SELECT 1 FROM sys.indexes i
            JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1
            JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE i.object_id = OBJECT_ID(N'dbo.payment') AND i.is_primary_key = 1
              AND i.is_unique = 1 AND i.is_disabled = 0 AND i.is_hypothetical = 0
              AND c.name = N'id'
              AND (SELECT COUNT(*) FROM sys.index_columns k
                   WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1
        )
            THROW 50609, 'Existing dbo.payment must have an enabled single-column id primary key.', 1;

        IF EXISTS (
            SELECT 1 FROM sys.indexes i
            WHERE i.object_id = OBJECT_ID(N'dbo.payment') AND i.name = N'uq_payment_txn'
              AND (i.is_unique <> 1 OR i.is_disabled = 1 OR i.is_hypothetical = 1 OR i.has_filter = 1
                   OR (SELECT COUNT(*) FROM sys.index_columns k
                       WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0) <> 1
                   OR NOT EXISTS (
                       SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                         ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                       WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                         AND ic.key_ordinal = 1 AND c.name = N'transaction_code'))
        )
            THROW 50610, 'Existing uq_payment_txn has an incompatible structure.', 1;

        IF EXISTS (
            SELECT 1 FROM sys.foreign_keys fk
            WHERE fk.parent_object_id = OBJECT_ID(N'dbo.payment') AND fk.name IN (N'fk_payment_member', N'fk_payment_processed_by')
              AND (fk.is_disabled = 1 OR fk.is_not_trusted = 1
                   OR fk.delete_referential_action <> 0 OR fk.update_referential_action <> 0
                   OR NOT EXISTS (
                       SELECT 1 FROM sys.foreign_key_columns fkc
                       JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
                       JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
                       WHERE fkc.constraint_object_id = fk.object_id
                         AND fk.referenced_object_id = OBJECT_ID(N'dbo.users')
                         AND ((fk.name = N'fk_payment_member' AND pc.name = N'member_id')
                           OR (fk.name = N'fk_payment_processed_by' AND pc.name = N'processed_by'))
                         AND rc.name = N'id'
                         AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1))
        )
            THROW 50611, 'An existing named Payment foreign key is disabled, untrusted, or incompatible.', 1;

        IF EXISTS (
            SELECT 1 FROM sys.foreign_keys fk
            JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
            JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
            WHERE fk.parent_object_id = OBJECT_ID(N'dbo.payment')
              AND pc.name IN (N'member_id', N'processed_by')
              AND (fk.is_disabled = 1 OR fk.is_not_trusted = 1
                   OR fk.delete_referential_action <> 0 OR fk.update_referential_action <> 0)
        )
            THROW 50613, 'An existing Payment actor/member foreign key is disabled, untrusted, or cascades; inspect it before migration.', 1;

        IF EXISTS (
            SELECT 1 FROM sys.check_constraints
            WHERE parent_object_id = OBJECT_ID(N'dbo.payment')
              AND name IN (N'ck_payment_method', N'ck_payment_purpose', N'ck_payment_status', N'ck_payment_amount')
              AND (is_disabled = 1 OR is_not_trusted = 1)
        )
            PRINT 'Existing named Payment CHECK constraints will be replaced and revalidated in this transaction.';
    END;

    IF OBJECT_ID(N'dbo.payment', N'U') IS NOT NULL
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM sys.foreign_keys fk
            JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
            JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
            JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
            WHERE fk.parent_object_id = OBJECT_ID(N'dbo.payment') AND fk.referenced_object_id = OBJECT_ID(N'dbo.users')
              AND pc.name = N'member_id' AND rc.name = N'id' AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
              AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
              AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1
        )
            ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT fk_payment_member FOREIGN KEY (member_id) REFERENCES dbo.users(id);

        IF NOT EXISTS (
            SELECT 1 FROM sys.foreign_keys fk
            JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
            JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
            JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
            WHERE fk.parent_object_id = OBJECT_ID(N'dbo.payment') AND fk.referenced_object_id = OBJECT_ID(N'dbo.users')
              AND pc.name = N'processed_by' AND rc.name = N'id' AND fk.is_disabled = 0 AND fk.is_not_trusted = 0
              AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
              AND (SELECT COUNT(*) FROM sys.foreign_key_columns x WHERE x.constraint_object_id = fk.object_id) = 1
        )
            ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT fk_payment_processed_by FOREIGN KEY (processed_by) REFERENCES dbo.users(id);

        IF NOT EXISTS (
            SELECT 1 FROM sys.indexes i
            JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = 1
            JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE i.object_id = OBJECT_ID(N'dbo.payment') AND i.is_unique = 1 AND i.is_disabled = 0
              AND i.is_hypothetical = 0 AND i.has_filter = 0 AND c.name = N'transaction_code'
              AND (SELECT COUNT(*) FROM sys.index_columns k
                   WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1
        )
            ALTER TABLE dbo.payment ADD CONSTRAINT uq_payment_txn UNIQUE (transaction_code);

        IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_method')
            ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_method;
        IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_purpose')
            ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_purpose;
        IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_status')
            ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_status;
        IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.payment') AND name = N'ck_payment_amount')
            ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_amount;

        ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_method CHECK (method IN ('ONLINE', 'COUNTER'));
        ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_purpose
            CHECK (purpose IN ('MEMBERSHIP', 'COURSE_REGISTRATION', 'PT_BOOKING'));
        ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_status
            CHECK (status IN ('PENDING', 'SUCCESS', 'CANCELLED', 'REFUND_REQUIRED'));
        ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_amount CHECK (amount >= 0);

        DECLARE @expectedIndexKeys TABLE (
            index_name SYSNAME NOT NULL,
            key_ordinal TINYINT NOT NULL,
            column_name SYSNAME NOT NULL,
            is_descending BIT NOT NULL,
            PRIMARY KEY (index_name, key_ordinal)
        );
        INSERT @expectedIndexKeys VALUES
            (N'ix_payment_member', 1, N'member_id', 0),
            (N'ix_payment_member', 2, N'created_at', 1),
            (N'ix_payment_paid_at', 1, N'status', 0),
            (N'ix_payment_paid_at', 2, N'paid_at', 0),
            (N'ix_payment_reference', 1, N'purpose', 0),
            (N'ix_payment_reference', 2, N'reference_id', 0),
            (N'ix_payment_reference', 3, N'status', 0);

        IF EXISTS (
            SELECT 1 FROM (SELECT DISTINCT index_name FROM @expectedIndexKeys) expected
            JOIN sys.indexes i ON i.object_id = OBJECT_ID(N'dbo.payment') AND i.name = expected.index_name
            WHERE i.type <> 2 OR i.is_unique = 1 OR i.is_disabled = 1 OR i.is_hypothetical = 1 OR i.has_filter = 1
               OR (SELECT COUNT(*) FROM sys.index_columns k
                   WHERE k.object_id = i.object_id AND k.index_id = i.index_id AND k.key_ordinal > 0)
                  <> (SELECT COUNT(*) FROM @expectedIndexKeys k WHERE k.index_name = expected.index_name)
               OR EXISTS (
                    SELECT 1 FROM @expectedIndexKeys ek
                    LEFT JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                        AND ic.key_ordinal = ek.key_ordinal
                    LEFT JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                    WHERE ek.index_name = expected.index_name
                      AND (ic.column_id IS NULL OR c.name <> ek.column_name OR ic.is_descending_key <> ek.is_descending)
               )
               OR EXISTS (SELECT 1 FROM sys.index_columns inc WHERE inc.object_id = i.object_id
                          AND inc.index_id = i.index_id AND inc.is_included_column = 1)
        )
            THROW 50612, 'A same-named Payment supporting index has incompatible keys or options.', 1;

        IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment') AND name = N'ix_payment_member')
            CREATE INDEX ix_payment_member ON dbo.payment(member_id ASC, created_at DESC);
        IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment') AND name = N'ix_payment_paid_at')
            CREATE INDEX ix_payment_paid_at ON dbo.payment(status ASC, paid_at ASC);
        IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment') AND name = N'ix_payment_reference')
            CREATE INDEX ix_payment_reference ON dbo.payment(purpose ASC, reference_id ASC, status ASC);
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
