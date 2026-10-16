-- Read-only preflight for phase2-personal-training-bookings.sql.
-- Select SportCenterDB first. This script performs SELECTs only and never changes schema/data.
SET NOCOUNT ON;

IF DB_NAME() <> N'SportCenterDB'
    THROW 50501, 'Select SportCenterDB before running the PT booking preflight.', 1;

SELECT DB_NAME() AS database_name,
       OBJECT_ID(N'dbo.users', N'U') AS users_object_id,
       OBJECT_ID(N'dbo.coach', N'U') AS coach_object_id,
       OBJECT_ID(N'dbo.sport', N'U') AS sport_object_id,
       OBJECT_ID(N'dbo.class_session', N'U') AS class_session_object_id,
       OBJECT_ID(N'dbo.personal_training_booking', N'U') AS pt_booking_object_id;

-- Actual PT column types, lengths, precision, scale, nullability, and identity metadata.
SELECT c.column_id, c.name AS column_name, t.name AS type_name,
       c.max_length, c.precision, c.scale, c.is_nullable, c.is_identity,
       c.is_computed, c.generated_always_type
FROM sys.columns c
JOIN sys.types t ON t.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID(N'dbo.personal_training_booking', N'U')
ORDER BY c.column_id;

-- Show every CHECK expression, regardless of its constraint name or enabled/trusted state.
SELECT cc.name AS constraint_name, cc.definition, cc.is_disabled, cc.is_not_trusted
FROM sys.check_constraints cc
WHERE cc.parent_object_id = OBJECT_ID(N'dbo.personal_training_booking', N'U')
ORDER BY cc.name;

-- Actual FK source/target columns and referential actions.
SELECT fk.name AS foreign_key_name, src.name AS source_column,
       OBJECT_SCHEMA_NAME(fk.referenced_object_id) AS target_schema,
       OBJECT_NAME(fk.referenced_object_id) AS target_table, dst.name AS target_column,
       fk.delete_referential_action_desc, fk.update_referential_action_desc,
       fk.is_disabled, fk.is_not_trusted
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
JOIN sys.columns src ON src.object_id = fkc.parent_object_id AND src.column_id = fkc.parent_column_id
JOIN sys.columns dst ON dst.object_id = fkc.referenced_object_id AND dst.column_id = fkc.referenced_column_id
WHERE fk.parent_object_id = OBJECT_ID(N'dbo.personal_training_booking', N'U')
ORDER BY fk.name, fkc.constraint_column_id;

-- Index key order, included columns, type, filter, and disabled state.
SELECT i.name AS index_name, i.type_desc, i.is_unique, i.is_disabled, i.is_hypothetical,
       i.has_filter, i.filter_definition, ic.key_ordinal, ic.is_included_column,
       ic.is_descending_key, c.name AS column_name
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id = OBJECT_ID(N'dbo.personal_training_booking', N'U')
ORDER BY i.name, ic.key_ordinal, ic.index_column_id;

-- Legacy rows requiring review before the migration derives a deadline from created_at.
-- DATETIME has no timezone; confirm the old application writer's local timezone separately.
IF OBJECT_ID(N'dbo.personal_training_booking', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH(N'dbo.personal_training_booking', N'status') IS NULL
       OR COL_LENGTH(N'dbo.personal_training_booking', N'created_at') IS NULL
        SELECT N'Cannot inspect pending rows: status or created_at is missing.' AS pending_row_diagnostic;
    ELSE IF COL_LENGTH(N'dbo.personal_training_booking', N'payment_expires_at') IS NULL
        EXEC sys.sp_executesql N'
            SELECT status, COUNT_BIG(*) AS row_count,
                   SUM(CASE WHEN created_at IS NULL THEN CONVERT(bigint, 1) ELSE CONVERT(bigint, 0) END) AS null_created_at_count,
                   MIN(created_at) AS earliest_created_at,
                   MAX(created_at) AS latest_created_at
            FROM dbo.personal_training_booking
            WHERE status = ''PENDING_PAYMENT''
            GROUP BY status;';
    ELSE
        EXEC sys.sp_executesql N'
            SELECT status, COUNT_BIG(*) AS row_count,
                   SUM(CASE WHEN payment_expires_at IS NULL THEN CONVERT(bigint, 1) ELSE CONVERT(bigint, 0) END) AS missing_deadline_count,
                   SUM(CASE WHEN created_at IS NULL THEN CONVERT(bigint, 1) ELSE CONVERT(bigint, 0) END) AS null_created_at_count,
                   MIN(created_at) AS earliest_created_at, MAX(created_at) AS latest_created_at
            FROM dbo.personal_training_booking
            WHERE status = ''PENDING_PAYMENT''
            GROUP BY status;';
END;
