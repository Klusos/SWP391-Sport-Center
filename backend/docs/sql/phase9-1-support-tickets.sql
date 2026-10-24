-- Additive Support Ticket Management schema (SQL Server 2016+).
-- Back up the selected database and stop application writers before running.
-- Run as one standalone batch; do not add GO or wrap this script in another transaction.
-- Java entities populate timestamps with LocalDateTime.now(); the SYSDATETIME() defaults below are
-- fallbacks for inserts that omit timestamps. Both are local wall-clock values, so keep the JVM
-- and SQL Server host on the same intended timezone (Asia/Ho_Chi_Minh for this application).
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
    THROW 50901, 'Select SportCenterDB before applying the support ticket migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50902, 'Run the support ticket migration without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.users', N'U') IS NULL
    THROW 50903, 'Expected dbo.users before creating support tickets.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @ticketExists BIT = CASE WHEN OBJECT_ID(N'dbo.support_ticket', N'U') IS NULL THEN 0 ELSE 1 END;
    DECLARE @messageExists BIT = CASE WHEN OBJECT_ID(N'dbo.support_ticket_message', N'U') IS NULL THEN 0 ELSE 1 END;
    IF @ticketExists <> @messageExists
        THROW 50904, 'Only one support ticket table exists; inspect the partial schema before retrying.', 1;

    IF @ticketExists = 0
    BEGIN
        EXEC sys.sp_executesql N'
            CREATE TABLE dbo.support_ticket (
                id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_support_ticket PRIMARY KEY,
                member_id INT NOT NULL,
                subject NVARCHAR(160) NOT NULL,
                category VARCHAR(30) NOT NULL,
                description NVARCHAR(MAX) NOT NULL,
                status VARCHAR(20) NOT NULL CONSTRAINT df_support_ticket_status DEFAULT (''OPEN''),
                assigned_staff_id INT NULL,
                created_at DATETIME2(6) NOT NULL CONSTRAINT df_support_ticket_created_at DEFAULT (SYSDATETIME()),
                updated_at DATETIME2(6) NOT NULL CONSTRAINT df_support_ticket_updated_at DEFAULT (SYSDATETIME()),
                resolved_at DATETIME2(6) NULL,
                closed_at DATETIME2(6) NULL,
                CONSTRAINT fk_support_ticket_member FOREIGN KEY (member_id) REFERENCES dbo.users(id),
                CONSTRAINT fk_support_ticket_assigned_staff FOREIGN KEY (assigned_staff_id) REFERENCES dbo.users(id),
                CONSTRAINT ck_support_ticket_category CHECK (category IN (''GENERAL'', ''MEMBERSHIP'', ''COURSE'', ''PT_BOOKING'', ''PAYMENT'', ''OTHER'')),
                CONSTRAINT ck_support_ticket_status CHECK (status IN (''OPEN'', ''IN_PROGRESS'', ''RESOLVED'', ''CLOSED'')),
                CONSTRAINT ck_support_ticket_subject CHECK (LEN(LTRIM(RTRIM(subject))) > 0),
                CONSTRAINT ck_support_ticket_description CHECK (LEN(LTRIM(RTRIM(description))) > 0)
            );
            CREATE TABLE dbo.support_ticket_message (
                id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_support_ticket_message PRIMARY KEY,
                ticket_id INT NOT NULL,
                sender_id INT NOT NULL,
                message NVARCHAR(MAX) NOT NULL,
                created_at DATETIME2(6) NOT NULL CONSTRAINT df_support_ticket_message_created_at DEFAULT (SYSDATETIME()),
                CONSTRAINT fk_support_ticket_message_ticket FOREIGN KEY (ticket_id) REFERENCES dbo.support_ticket(id),
                CONSTRAINT fk_support_ticket_message_sender FOREIGN KEY (sender_id) REFERENCES dbo.users(id),
                CONSTRAINT ck_support_ticket_message_nonblank CHECK (LEN(LTRIM(RTRIM(message))) > 0)
            );';
        EXEC sys.sp_executesql N'CREATE INDEX ix_support_ticket_member_updated ON dbo.support_ticket(member_id, updated_at DESC, id DESC);';
        EXEC sys.sp_executesql N'CREATE INDEX ix_support_ticket_status_updated ON dbo.support_ticket(status, updated_at DESC, id DESC);';
        EXEC sys.sp_executesql N'CREATE INDEX ix_support_ticket_category_status ON dbo.support_ticket(category, status, updated_at DESC);';
        EXEC sys.sp_executesql N'CREATE INDEX ix_support_ticket_assigned_staff ON dbo.support_ticket(assigned_staff_id, status, updated_at DESC);';
        EXEC sys.sp_executesql N'CREATE INDEX ix_support_ticket_message_ticket_created ON dbo.support_ticket_message(ticket_id, created_at, id);';
    END;

    DECLARE @expectedColumns TABLE (
        table_name sysname NOT NULL,
        column_name sysname NOT NULL,
        type_name sysname NOT NULL,
        max_length smallint NOT NULL,
        scale tinyint NOT NULL,
        is_nullable bit NOT NULL,
        is_identity bit NOT NULL,
        PRIMARY KEY (table_name, column_name)
    );
    INSERT INTO @expectedColumns (table_name, column_name, type_name, max_length, scale, is_nullable, is_identity)
    VALUES
        (N'support_ticket', N'id', N'int', 4, 0, 0, 1),
        (N'support_ticket', N'member_id', N'int', 4, 0, 0, 0),
        (N'support_ticket', N'subject', N'nvarchar', 320, 0, 0, 0),
        (N'support_ticket', N'category', N'varchar', 30, 0, 0, 0),
        (N'support_ticket', N'description', N'nvarchar', -1, 0, 0, 0),
        (N'support_ticket', N'status', N'varchar', 20, 0, 0, 0),
        (N'support_ticket', N'assigned_staff_id', N'int', 4, 0, 1, 0),
        (N'support_ticket', N'created_at', N'datetime2', 8, 6, 0, 0),
        (N'support_ticket', N'updated_at', N'datetime2', 8, 6, 0, 0),
        (N'support_ticket', N'resolved_at', N'datetime2', 8, 6, 1, 0),
        (N'support_ticket', N'closed_at', N'datetime2', 8, 6, 1, 0),
        (N'support_ticket_message', N'id', N'int', 4, 0, 0, 1),
        (N'support_ticket_message', N'ticket_id', N'int', 4, 0, 0, 0),
        (N'support_ticket_message', N'sender_id', N'int', 4, 0, 0, 0),
        (N'support_ticket_message', N'message', N'nvarchar', -1, 0, 0, 0),
        (N'support_ticket_message', N'created_at', N'datetime2', 8, 6, 0, 0);

    IF EXISTS (
        SELECT 1
        FROM @expectedColumns expected
        LEFT JOIN sys.tables t ON t.name = expected.table_name
        LEFT JOIN sys.schemas s ON s.schema_id = t.schema_id AND s.name = N'dbo'
        LEFT JOIN sys.columns c ON c.object_id = t.object_id AND c.name = expected.column_name
        LEFT JOIN sys.types ty ON ty.user_type_id = c.user_type_id
        WHERE s.schema_id IS NULL OR c.column_id IS NULL
           OR ty.name <> expected.type_name
           OR c.max_length <> expected.max_length
           OR c.scale <> expected.scale
           OR c.is_nullable <> expected.is_nullable
           OR c.is_identity <> expected.is_identity
    )
        THROW 50905, 'A support ticket column is missing or has an incompatible type, length, scale, nullability, or identity definition.', 1;

    DECLARE @expectedDefaults TABLE (
        table_name sysname NOT NULL,
        column_name sysname NOT NULL,
        constraint_name sysname NOT NULL,
        canonical_definition nvarchar(100) NOT NULL,
        PRIMARY KEY (table_name, column_name, constraint_name)
    );
    INSERT INTO @expectedDefaults (table_name, column_name, constraint_name, canonical_definition)
    VALUES
        (N'support_ticket', N'status', N'df_support_ticket_status', N'''OPEN'''),
        (N'support_ticket', N'created_at', N'df_support_ticket_created_at', N'SYSDATETIME'),
        (N'support_ticket', N'updated_at', N'df_support_ticket_updated_at', N'SYSDATETIME'),
        (N'support_ticket_message', N'created_at', N'df_support_ticket_message_created_at', N'SYSDATETIME');
    IF EXISTS (
        SELECT 1
        FROM @expectedDefaults expected
        JOIN sys.tables t ON t.name = expected.table_name
        JOIN sys.schemas s ON s.schema_id = t.schema_id AND s.name = N'dbo'
        JOIN sys.columns c ON c.object_id = t.object_id AND c.name = expected.column_name
        LEFT JOIN sys.default_constraints dc ON dc.parent_object_id = t.object_id AND dc.parent_column_id = c.column_id
        CROSS APPLY (SELECT UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            COALESCE(dc.definition, N''), N' ', N''), CHAR(9), N''), CHAR(10), N''), CHAR(13), N''), N'(', N''), N')', N'')) AS normalized_definition) normalized
        WHERE dc.object_id IS NULL OR dc.name <> expected.constraint_name
           OR normalized.normalized_definition <> expected.canonical_definition
    )
        THROW 50910, 'A support ticket default is missing or differs from the expected status or local timestamp default.', 1;

    DECLARE @expectedPrimaryKeys TABLE (table_name sysname NOT NULL, constraint_name sysname NOT NULL, PRIMARY KEY (table_name, constraint_name));
    INSERT INTO @expectedPrimaryKeys (table_name, constraint_name)
    VALUES (N'support_ticket', N'pk_support_ticket'), (N'support_ticket_message', N'pk_support_ticket_message');
    IF EXISTS (
        SELECT 1
        FROM @expectedPrimaryKeys expected
        JOIN sys.tables t ON t.name = expected.table_name
        JOIN sys.schemas s ON s.schema_id = t.schema_id AND s.name = N'dbo'
        LEFT JOIN sys.key_constraints kc ON kc.parent_object_id = t.object_id AND kc.name = expected.constraint_name AND kc.type = N'PK'
        LEFT JOIN sys.indexes i ON i.object_id = t.object_id AND i.index_id = kc.unique_index_id
        WHERE kc.object_id IS NULL OR i.is_primary_key <> 1 OR i.is_unique <> 1
           OR (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = t.object_id AND ic.index_id = kc.unique_index_id AND ic.key_ordinal > 0) <> 1
           OR NOT EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                          WHERE ic.object_id = t.object_id AND ic.index_id = kc.unique_index_id AND ic.key_ordinal = 1 AND c.name = N'id')
    )
        THROW 50906, 'A support ticket primary key is missing or does not consist solely of id.', 1;

    DECLARE @expectedForeignKeys TABLE (
        constraint_name sysname NOT NULL PRIMARY KEY,
        table_name sysname NOT NULL,
        column_name sysname NOT NULL,
        referenced_table sysname NOT NULL,
        referenced_column sysname NOT NULL
    );
    INSERT INTO @expectedForeignKeys (constraint_name, table_name, column_name, referenced_table, referenced_column)
    VALUES
        (N'fk_support_ticket_member', N'support_ticket', N'member_id', N'users', N'id'),
        (N'fk_support_ticket_assigned_staff', N'support_ticket', N'assigned_staff_id', N'users', N'id'),
        (N'fk_support_ticket_message_ticket', N'support_ticket_message', N'ticket_id', N'support_ticket', N'id'),
        (N'fk_support_ticket_message_sender', N'support_ticket_message', N'sender_id', N'users', N'id');
    IF EXISTS (
        SELECT 1
        FROM @expectedForeignKeys expected
        JOIN sys.tables parent_table ON parent_table.name = expected.table_name
        JOIN sys.schemas parent_schema ON parent_schema.schema_id = parent_table.schema_id AND parent_schema.name = N'dbo'
        LEFT JOIN sys.foreign_keys fk ON fk.parent_object_id = parent_table.object_id AND fk.name = expected.constraint_name
        LEFT JOIN sys.tables referenced_table ON referenced_table.object_id = fk.referenced_object_id
        LEFT JOIN sys.schemas referenced_schema ON referenced_schema.schema_id = referenced_table.schema_id
        LEFT JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        LEFT JOIN sys.columns parent_column ON parent_column.object_id = fkc.parent_object_id AND parent_column.column_id = fkc.parent_column_id
        LEFT JOIN sys.columns referenced_column ON referenced_column.object_id = fkc.referenced_object_id AND referenced_column.column_id = fkc.referenced_column_id
        WHERE fk.object_id IS NULL OR fk.is_disabled = 1 OR fk.is_not_trusted = 1
           OR fk.delete_referential_action <> 0 OR fk.update_referential_action <> 0
           OR referenced_schema.name <> N'dbo' OR referenced_table.name <> expected.referenced_table
           OR parent_column.name <> expected.column_name OR referenced_column.name <> expected.referenced_column
           OR (SELECT COUNT(*) FROM sys.foreign_key_columns fkcx WHERE fkcx.constraint_object_id = fk.object_id) <> 1
    )
        THROW 50907, 'A support ticket foreign key is missing, untrusted, disabled, or targets an unexpected column.', 1;

    -- Set to 1 for one diagnostic run: mismatch details are selected before the normal THROW
    -- rolls this migration transaction back. The default is silent fail-closed validation.
    DECLARE @diagnoseChecks BIT = 0;
    DECLARE @expectedChecks TABLE (
        check_id tinyint IDENTITY(1,1) NOT NULL PRIMARY KEY,
        table_name sysname NOT NULL,
        constraint_name sysname NOT NULL,
        predicate_kind char(1) NOT NULL,
        column_name sysname NOT NULL,
        canonical_definition nvarchar(200) NULL
    );
    INSERT INTO @expectedChecks (table_name, constraint_name, predicate_kind, column_name, canonical_definition)
    VALUES
        (N'support_ticket', N'ck_support_ticket_category', 'E', N'category', NULL),
        (N'support_ticket', N'ck_support_ticket_status', 'E', N'status', NULL),
        (N'support_ticket', N'ck_support_ticket_subject', 'N', N'subject', N'LENLTRIMRTRIMSUBJECT>0'),
        (N'support_ticket', N'ck_support_ticket_description', 'N', N'description', N'LENLTRIMRTRIMDESCRIPTION>0'),
        (N'support_ticket_message', N'ck_support_ticket_message_nonblank', 'N', N'message', N'LENLTRIMRTRIMMESSAGE>0');
    DECLARE @expectedCheckValues TABLE (
        constraint_name sysname NOT NULL,
        value_name sysname NOT NULL,
        PRIMARY KEY (constraint_name, value_name)
    );
    INSERT INTO @expectedCheckValues (constraint_name, value_name)
    VALUES
        (N'ck_support_ticket_category', N'GENERAL'),
        (N'ck_support_ticket_category', N'MEMBERSHIP'),
        (N'ck_support_ticket_category', N'COURSE'),
        (N'ck_support_ticket_category', N'PT_BOOKING'),
        (N'ck_support_ticket_category', N'PAYMENT'),
        (N'ck_support_ticket_category', N'OTHER'),
        (N'ck_support_ticket_status', N'OPEN'),
        (N'ck_support_ticket_status', N'IN_PROGRESS'),
        (N'ck_support_ticket_status', N'RESOLVED'),
        (N'ck_support_ticket_status', N'CLOSED');

    DECLARE @checkResults TABLE (
        check_id tinyint NOT NULL PRIMARY KEY,
        table_name sysname NOT NULL,
        constraint_name sysname NOT NULL,
        expected_definition nvarchar(400) NOT NULL,
        actual_definition nvarchar(4000) NULL,
        normalized_definition nvarchar(4000) NULL,
        mismatch_reason nvarchar(100) NULL
    );
    INSERT INTO @checkResults (check_id, table_name, constraint_name, expected_definition, actual_definition, normalized_definition, mismatch_reason)
    SELECT expected.check_id, expected.table_name, expected.constraint_name,
           CASE WHEN expected.predicate_kind = 'N' THEN expected.canonical_definition
                WHEN expected.constraint_name = N'ck_support_ticket_category' THEN N'category in (GENERAL, MEMBERSHIP, COURSE, PT_BOOKING, PAYMENT, OTHER)'
                ELSE N'status in (OPEN, IN_PROGRESS, RESOLVED, CLOSED)' END,
           cc.definition,
           UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
               COALESCE(cc.definition, N''), N'[', N''), N']', N''), N' ', N''), CHAR(9), N''), CHAR(10), N''), CHAR(13), N''), N'(', N''), N')', N'')),
           CASE WHEN cc.object_id IS NULL THEN N'MISSING'
                WHEN cc.is_disabled = 1 THEN N'DISABLED'
                WHEN cc.is_not_trusted = 1 THEN N'UNTRUSTED'
                ELSE NULL END
    FROM @expectedChecks expected
    JOIN sys.tables t ON t.name = expected.table_name
    JOIN sys.schemas s ON s.schema_id = t.schema_id AND s.name = N'dbo'
    LEFT JOIN sys.check_constraints cc ON cc.parent_object_id = t.object_id AND cc.name = expected.constraint_name;

    DECLARE @checkId tinyint = 1;
    DECLARE @lastCheckId tinyint;
    SELECT @lastCheckId = MAX(check_id) FROM @expectedChecks;
    DECLARE @kind char(1), @constraintName sysname, @columnName sysname, @canonical nvarchar(200);
    DECLARE @normalized nvarchar(4000), @remaining nvarchar(4000), @value sysname, @term nvarchar(300);
    DECLARE @foundInForm bit, @formValid bit, @occurrences int;
    WHILE @checkId <= @lastCheckId
    BEGIN
        SELECT @kind = predicate_kind, @constraintName = constraint_name, @columnName = column_name,
               @canonical = canonical_definition
        FROM @expectedChecks WHERE check_id = @checkId;
        SELECT @normalized = normalized_definition FROM @checkResults WHERE check_id = @checkId;

        IF NOT EXISTS (SELECT 1 FROM @checkResults WHERE check_id = @checkId AND mismatch_reason IS NOT NULL)
        BEGIN
            IF @kind = 'N'
            BEGIN
                IF @normalized <> @canonical
                    UPDATE @checkResults SET mismatch_reason = N'PREDICATE_MISMATCH' WHERE check_id = @checkId;
            END
            ELSE
            BEGIN
                -- Strip only formatting already proven harmless, and accept either an IN list
                -- or a pure OR of equality terms. Compare enum values as a set, independent of order.
                SET @normalized = REPLACE(@normalized, N'=N''', N'=''');
                SET @normalized = REPLACE(@normalized, N'INN''', N'IN''');
                SET @foundInForm = CASE WHEN LEFT(@normalized, LEN(UPPER(@columnName) + N'IN')) = UPPER(@columnName) + N'IN' THEN 1 ELSE 0 END;
                SET @formValid = 1;
                IF @foundInForm = 1
                BEGIN
                    SET @remaining = SUBSTRING(@normalized, LEN(UPPER(@columnName) + N'IN') + 1, 4000);
                    DECLARE check_values_in CURSOR LOCAL FAST_FORWARD FOR
                        SELECT value_name FROM @expectedCheckValues WHERE constraint_name = @constraintName;
                    OPEN check_values_in;
                    FETCH NEXT FROM check_values_in INTO @value;
                    WHILE @@FETCH_STATUS = 0
                    BEGIN
                        SET @term = N'''' + @value + N'''';
                        SET @occurrences = (LEN(@remaining) - LEN(REPLACE(@remaining, @term, N''))) / LEN(@term);
                        IF @occurrences <> 1 SET @formValid = 0;
                        SET @remaining = REPLACE(@remaining, @term, N'');
                        FETCH NEXT FROM check_values_in INTO @value;
                    END;
                    CLOSE check_values_in;
                    DEALLOCATE check_values_in;
                    SET @remaining = REPLACE(@remaining, N',', N'');
                    IF @remaining <> N'' SET @formValid = 0;
                END
                ELSE
                BEGIN
                    SET @remaining = @normalized;
                    DECLARE check_values_or CURSOR LOCAL FAST_FORWARD FOR
                        SELECT value_name FROM @expectedCheckValues WHERE constraint_name = @constraintName;
                    OPEN check_values_or;
                    FETCH NEXT FROM check_values_or INTO @value;
                    WHILE @@FETCH_STATUS = 0
                    BEGIN
                        SET @term = UPPER(@columnName) + N'=' + N'''' + @value + N'''';
                        SET @occurrences = (LEN(@remaining) - LEN(REPLACE(@remaining, @term, N''))) / LEN(@term);
                        IF @occurrences <> 1 SET @formValid = 0;
                        SET @remaining = REPLACE(@remaining, @term, N'');
                        FETCH NEXT FROM check_values_or INTO @value;
                    END;
                    CLOSE check_values_or;
                    DEALLOCATE check_values_or;
                    SET @remaining = REPLACE(@remaining, N'OR', N'');
                    IF @remaining <> N'' SET @formValid = 0;
                END;
                IF @formValid = 0
                    UPDATE @checkResults SET mismatch_reason = N'ENUM_SET_MISMATCH' WHERE check_id = @checkId;
            END;
        END;
        SET @checkId += 1;
    END;

    IF EXISTS (SELECT 1 FROM @checkResults WHERE mismatch_reason IS NOT NULL)
    BEGIN
        IF @diagnoseChecks = 1
            SELECT table_name, constraint_name, expected_definition, actual_definition, normalized_definition, mismatch_reason
            FROM @checkResults WHERE mismatch_reason IS NOT NULL ORDER BY check_id;
        THROW 50908, 'A support ticket CHECK constraint is missing, untrusted, disabled, or has an unexpected definition. Set @diagnoseChecks = 1 for details.', 1;
    END;

    DECLARE @expectedIndexes TABLE (
        table_name sysname NOT NULL,
        index_name sysname NOT NULL,
        expected_key_count tinyint NOT NULL,
        PRIMARY KEY (table_name, index_name)
    );
    DECLARE @expectedIndexKeys TABLE (
        table_name sysname NOT NULL,
        index_name sysname NOT NULL,
        key_ordinal tinyint NOT NULL,
        column_name sysname NOT NULL,
        is_descending_key bit NOT NULL,
        PRIMARY KEY (table_name, index_name, key_ordinal)
    );
    INSERT INTO @expectedIndexes (table_name, index_name, expected_key_count)
    VALUES
        (N'support_ticket', N'ix_support_ticket_member_updated', 3),
        (N'support_ticket', N'ix_support_ticket_status_updated', 3),
        (N'support_ticket', N'ix_support_ticket_category_status', 3),
        (N'support_ticket', N'ix_support_ticket_assigned_staff', 3),
        (N'support_ticket_message', N'ix_support_ticket_message_ticket_created', 3);
    INSERT INTO @expectedIndexKeys (table_name, index_name, key_ordinal, column_name, is_descending_key)
    VALUES
        (N'support_ticket', N'ix_support_ticket_member_updated', 1, N'member_id', 0),
        (N'support_ticket', N'ix_support_ticket_member_updated', 2, N'updated_at', 1),
        (N'support_ticket', N'ix_support_ticket_member_updated', 3, N'id', 1),
        (N'support_ticket', N'ix_support_ticket_status_updated', 1, N'status', 0),
        (N'support_ticket', N'ix_support_ticket_status_updated', 2, N'updated_at', 1),
        (N'support_ticket', N'ix_support_ticket_status_updated', 3, N'id', 1),
        (N'support_ticket', N'ix_support_ticket_category_status', 1, N'category', 0),
        (N'support_ticket', N'ix_support_ticket_category_status', 2, N'status', 0),
        (N'support_ticket', N'ix_support_ticket_category_status', 3, N'updated_at', 1),
        (N'support_ticket', N'ix_support_ticket_assigned_staff', 1, N'assigned_staff_id', 0),
        (N'support_ticket', N'ix_support_ticket_assigned_staff', 2, N'status', 0),
        (N'support_ticket', N'ix_support_ticket_assigned_staff', 3, N'updated_at', 1),
        (N'support_ticket_message', N'ix_support_ticket_message_ticket_created', 1, N'ticket_id', 0),
        (N'support_ticket_message', N'ix_support_ticket_message_ticket_created', 2, N'created_at', 0),
        (N'support_ticket_message', N'ix_support_ticket_message_ticket_created', 3, N'id', 0);
    IF EXISTS (
        SELECT 1
        FROM @expectedIndexes expected
        JOIN sys.tables t ON t.name = expected.table_name
        JOIN sys.schemas s ON s.schema_id = t.schema_id AND s.name = N'dbo'
        LEFT JOIN sys.indexes i ON i.object_id = t.object_id AND i.name = expected.index_name
        WHERE i.index_id IS NULL OR i.type <> 2 OR i.is_unique <> 0 OR i.is_disabled = 1
           OR i.is_hypothetical = 1 OR i.has_filter = 1
           OR (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal > 0) <> expected.expected_key_count
           OR (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 1) <> 0
           OR EXISTS (
                SELECT 1
                FROM @expectedIndexKeys expected_key
                LEFT JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal = expected_key.key_ordinal
                LEFT JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                WHERE expected_key.table_name = expected.table_name AND expected_key.index_name = expected.index_name
                  AND (c.name IS NULL OR c.name <> expected_key.column_name OR ic.is_descending_key <> expected_key.is_descending_key)
           )
    )
        THROW 50909, 'A support ticket index is missing, disabled, filtered, or has unexpected key columns or ordering.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
