/*
    READ-ONLY SQL Server schema diagnostic for SportCenter.

    Run in SSMS after selecting the intended database. The first result set
    reports DB_NAME(); no database is changed and no schema/data is modified.
    This script inspects the 16 JPA entity tables in dbo and reports actual
    metadata, including extra columns and constraints. A final aggregate query
    checks whether users.date_of_birth contains non-midnight time components;
    it does not return names, emails, or individual dates.
*/
SET NOCOUNT ON;

SELECT DB_NAME() AS current_database,
       N'SportCenterDB' AS expected_database,
       CASE WHEN DB_NAME() = N'SportCenterDB' THEN N'EXPECTED' ELSE N'CHECK DATABASE' END AS database_check;

DECLARE @mapped_tables TABLE (table_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @mapped_tables (table_name) VALUES
    (N'users'), (N'coach'), (N'coach_sport'), (N'sport'), (N'course'),
    (N'course_offering'), (N'class'), (N'class_session'), (N'facility'),
    (N'member_profile'), (N'membership_plan'), (N'membership'),
    (N'member_sport_level'), (N'skill_assessment'), (N'consultation_request'),
    (N'course_registration');

-- 1. Presence of every table mapped by a JPA entity.
SELECT expected.table_name AS expected_table,
       schema_info.name AS actual_schema,
       actual.name AS actual_table,
       CASE WHEN actual.object_id IS NULL THEN N'MISSING' ELSE N'PRESENT' END AS presence
FROM @mapped_tables AS expected
LEFT JOIN sys.tables AS actual
    ON actual.name = expected.table_name
   AND actual.schema_id = SCHEMA_ID(N'dbo')
LEFT JOIN sys.schemas AS schema_info
    ON schema_info.schema_id = actual.schema_id
ORDER BY expected.table_name;

-- 2. Actual columns, types, lengths, precision/scale, nullability, identity,
--    defaults, computed/generated state, and collation for mapped tables.
SELECT schema_info.name AS schema_name,
       table_info.name AS table_name,
       column_info.column_id,
       column_info.name AS column_name,
       type_info.name AS data_type,
       column_info.max_length AS max_length_bytes,
       CASE
           WHEN type_info.name IN (N'nchar', N'nvarchar') AND column_info.max_length = -1 THEN -1
           WHEN type_info.name IN (N'nchar', N'nvarchar') THEN column_info.max_length / 2
           WHEN type_info.name IN (N'char', N'varchar') AND column_info.max_length = -1 THEN -1
           WHEN type_info.name IN (N'char', N'varchar') THEN column_info.max_length
           ELSE NULL
       END AS declared_character_length,
       column_info.precision,
       column_info.scale,
       column_info.is_nullable,
       column_info.is_identity,
       identity_info.seed_value,
       identity_info.increment_value,
       column_info.is_computed,
       column_info.generated_always_type_desc,
       default_info.definition AS default_definition,
       column_info.collation_name
FROM @mapped_tables AS expected
JOIN sys.tables AS table_info
    ON table_info.name = expected.table_name
   AND table_info.schema_id = SCHEMA_ID(N'dbo')
JOIN sys.schemas AS schema_info
    ON schema_info.schema_id = table_info.schema_id
JOIN sys.columns AS column_info
    ON column_info.object_id = table_info.object_id
JOIN sys.types AS type_info
    ON type_info.user_type_id = column_info.user_type_id
LEFT JOIN sys.identity_columns AS identity_info
    ON identity_info.object_id = column_info.object_id
   AND identity_info.column_id = column_info.column_id
LEFT JOIN sys.default_constraints AS default_info
    ON default_info.parent_object_id = column_info.object_id
   AND default_info.parent_column_id = column_info.column_id
ORDER BY schema_info.name, table_info.name, column_info.column_id;

-- 3. Primary keys, unique constraints, and indexes, including disabled or
--    filtered indexes. One row is returned per index column.
SELECT schema_info.name AS schema_name,
       table_info.name AS table_name,
       index_info.name AS index_name,
       index_info.type_desc,
       index_info.is_primary_key,
       index_info.is_unique,
       index_info.is_unique_constraint,
       index_info.is_disabled,
       index_info.is_hypothetical,
       index_info.has_filter,
       index_info.filter_definition,
       index_info.ignore_dup_key,
       index_column.key_ordinal,
       index_column.is_included_column,
       index_column.is_descending_key,
       column_info.name AS column_name
FROM @mapped_tables AS expected
JOIN sys.tables AS table_info ON table_info.name = expected.table_name AND table_info.schema_id = SCHEMA_ID(N'dbo')
JOIN sys.schemas AS schema_info ON schema_info.schema_id = table_info.schema_id
JOIN sys.indexes AS index_info ON index_info.object_id = table_info.object_id
JOIN sys.index_columns AS index_column
    ON index_column.object_id = index_info.object_id
   AND index_column.index_id = index_info.index_id
JOIN sys.columns AS column_info
    ON column_info.object_id = index_column.object_id
   AND column_info.column_id = index_column.column_id
WHERE index_info.name IS NOT NULL
ORDER BY schema_info.name, table_info.name, index_info.name, index_column.key_ordinal, index_column.index_column_id;

-- 4. Foreign-key column mappings and enforcement/trust state.
SELECT child_schema.name AS child_schema,
       child_table.name AS child_table,
       foreign_key.name AS foreign_key_name,
       child_column.name AS child_column,
       parent_schema.name AS referenced_schema,
       parent_table.name AS referenced_table,
       parent_column.name AS referenced_column,
       foreign_key.delete_referential_action_desc,
       foreign_key.update_referential_action_desc,
       foreign_key.is_disabled,
       foreign_key.is_not_trusted,
       foreign_key.is_not_for_replication
FROM sys.foreign_keys AS foreign_key
JOIN sys.foreign_key_columns AS foreign_key_column
    ON foreign_key_column.constraint_object_id = foreign_key.object_id
JOIN sys.tables AS child_table
    ON child_table.object_id = foreign_key.parent_object_id
JOIN sys.schemas AS child_schema
    ON child_schema.schema_id = child_table.schema_id
JOIN sys.columns AS child_column
    ON child_column.object_id = child_table.object_id
   AND child_column.column_id = foreign_key_column.parent_column_id
JOIN sys.tables AS parent_table
    ON parent_table.object_id = foreign_key.referenced_object_id
JOIN sys.schemas AS parent_schema
    ON parent_schema.schema_id = parent_table.schema_id
JOIN sys.columns AS parent_column
    ON parent_column.object_id = parent_table.object_id
   AND parent_column.column_id = foreign_key_column.referenced_column_id
WHERE child_table.name IN (SELECT table_name FROM @mapped_tables)
   OR parent_table.name IN (SELECT table_name FROM @mapped_tables)
ORDER BY child_schema.name, child_table.name, foreign_key.name, foreign_key_column.constraint_column_id;

-- 5. Check constraints on mapped tables, with SQL definitions and enforcement.
SELECT schema_info.name AS schema_name,
       table_info.name AS table_name,
       check_info.name AS check_name,
       check_info.definition,
       check_info.is_disabled,
       check_info.is_not_trusted,
       check_info.is_not_for_replication
FROM @mapped_tables AS expected
JOIN sys.tables AS table_info ON table_info.name = expected.table_name AND table_info.schema_id = SCHEMA_ID(N'dbo')
JOIN sys.schemas AS schema_info ON schema_info.schema_id = table_info.schema_id
JOIN sys.check_constraints AS check_info ON check_info.parent_object_id = table_info.object_id
ORDER BY schema_info.name, table_info.name, check_info.name;

-- 6. Aggregate quality check for the reported users.date_of_birth mismatch.
--    Dynamic SQL avoids a compile error if the table/column is absent. No PII
--    values are selected. The result distinguishes NULLs and non-midnight times.
IF OBJECT_ID(N'dbo.users', N'U') IS NOT NULL
   AND COL_LENGTH(N'dbo.users', N'date_of_birth') IS NOT NULL
BEGIN
    EXEC sys.sp_executesql N'
        SELECT COUNT_BIG(*) AS total_user_rows,
               COUNT_BIG(date_of_birth) AS nonnull_date_of_birth_rows,
               SUM(CONVERT(bigint, CASE WHEN date_of_birth IS NOT NULL
                                         AND TRY_CONVERT(datetime2(7), date_of_birth) IS NULL
                                        THEN 1 ELSE 0 END)) AS unconvertible_rows,
               SUM(CONVERT(bigint, CASE WHEN TRY_CONVERT(datetime2(7), date_of_birth) IS NOT NULL
                                         AND CONVERT(time(7), TRY_CONVERT(datetime2(7), date_of_birth)) <> CONVERT(time(7), ''00:00:00'')
                                        THEN 1 ELSE 0 END)) AS non_midnight_time_rows
        FROM dbo.users;';
END
ELSE
BEGIN
    SELECT N'dbo.users.date_of_birth is missing; data-quality check was skipped.' AS diagnostic;
END;
