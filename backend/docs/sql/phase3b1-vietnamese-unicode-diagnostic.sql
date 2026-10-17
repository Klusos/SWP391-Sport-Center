-- READ-ONLY diagnostic for Vietnamese text storage/rendering.
-- Select SportCenterDB before running. This script performs no writes.
USE SportCenterDB;
GO

-- Inspect character storage and collation for user-visible string columns.
SELECT
    QUOTENAME(s.name) + N'.' + QUOTENAME(t.name) AS table_name,
    c.name AS column_name,
    ty.name AS sql_type,
    c.max_length AS max_length_bytes,
    c.is_nullable,
    c.collation_name
FROM sys.tables AS t
JOIN sys.schemas AS s ON s.schema_id = t.schema_id
JOIN sys.columns AS c ON c.object_id = t.object_id
JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
WHERE s.name = N'dbo'
  AND t.name IN (N'users', N'sport', N'coach', N'coach_sport', N'member_profile',
                 N'course', N'course_class', N'course_offering', N'facility',
                 N'membership_plan', N'consultation_request', N'skill_assessment')
  AND ty.name IN (N'char', N'varchar', N'nchar', N'nvarchar', N'text', N'ntext')
ORDER BY t.name, c.column_id;

-- Count likely mojibake markers in common public/profile text fields.
-- Counts avoid printing names, emails, notes, or other personal text.
DECLARE @checks TABLE (table_name SYSNAME, column_name SYSNAME, marker NVARCHAR(20));
INSERT @checks (table_name, column_name, marker) VALUES
    (N'users', N'name', N'Ã'), (N'users', N'name', N'Ä'), (N'users', N'name', N'á»'),
    (N'sport', N'name', N'Ã'), (N'sport', N'name', N'Ä'), (N'sport', N'description', N'á»'),
    (N'coach', N'bio', N'Ã'), (N'coach', N'bio', N'á»'),
    (N'coach_sport', N'certification', N'Ã'), (N'coach_sport', N'certification', N'á»'),
    (N'member_profile', N'goal', N'Ã'), (N'member_profile', N'goal', N'á»'),
    (N'course', N'name', N'Ã'), (N'course', N'name', N'á»'),
    (N'course', N'description', N'Ã'), (N'course', N'description', N'á»'),
    (N'course', N'learning_outcome', N'Ã'), (N'course', N'learning_outcome', N'á»'),
    (N'course_class', N'name', N'Ã'), (N'course_class', N'name', N'á»'),
    (N'course_offering', N'name', N'Ã'), (N'course_offering', N'name', N'á»'),
    (N'facility', N'name', N'Ã'), (N'facility', N'name', N'á»'),
    (N'facility', N'type', N'Ã'), (N'facility', N'type', N'á»'),
    (N'membership_plan', N'name', N'Ã'), (N'membership_plan', N'name', N'á»'),
    (N'membership_plan', N'description', N'Ã'), (N'membership_plan', N'description', N'á»'),
    (N'consultation_request', N'name', N'Ã'), (N'consultation_request', N'name', N'á»'),
    (N'consultation_request', N'experience', N'Ã'), (N'consultation_request', N'experience', N'á»'),
    (N'consultation_request', N'note', N'Ã'), (N'consultation_request', N'note', N'á»'),
    (N'consultation_request', N'resolution_note', N'Ã'), (N'consultation_request', N'resolution_note', N'á»'),
    (N'skill_assessment', N'notes', N'Ã'), (N'skill_assessment', N'notes', N'á»');

DECLARE @table SYSNAME, @column SYSNAME, @marker NVARCHAR(20), @sql NVARCHAR(MAX);
DECLARE marker_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT table_name, column_name, marker FROM @checks;
OPEN marker_cursor;
FETCH NEXT FROM marker_cursor INTO @table, @column, @marker;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF OBJECT_ID(N'dbo.' + QUOTENAME(@table), N'U') IS NOT NULL
       AND COL_LENGTH(N'dbo.' + @table, @column) IS NOT NULL
    BEGIN
        SET @sql = N'SELECT @table_name AS table_name, @column_name AS column_name, @marker AS marker, '
            + N'COUNT_BIG(*) AS matching_rows FROM dbo.' + QUOTENAME(@table)
            + N' WHERE TRY_CONVERT(NVARCHAR(MAX), ' + QUOTENAME(@column) + N') LIKE N''%'' + @marker + N''%'';';
        EXEC sys.sp_executesql @sql,
            N'@table_name SYSNAME, @column_name SYSNAME, @marker NVARCHAR(20)',
            @table_name = @table, @column_name = @column, @marker = @marker;
    END;
    FETCH NEXT FROM marker_cursor INTO @table, @column, @marker;
END;
CLOSE marker_cursor;
DEALLOCATE marker_cursor;
