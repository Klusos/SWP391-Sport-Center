-- READ-ONLY preflight for a future Phase 3B.2 Unicode migration.
-- Run in SportCenterDB and review every result set before preparing ALTER COLUMN.
-- This script performs no writes and deliberately does not alter/drop dependencies.
IF DB_NAME() <> N'SportCenterDB'
    THROW 50420, 'Select SportCenterDB before running the Unicode preflight.', 1;
SELECT DB_NAME() AS current_database;

DECLARE @targets TABLE (table_name SYSNAME NOT NULL, column_name SYSNAME NOT NULL,
    target_type SYSNAME NOT NULL, target_max_length_bytes INT NULL, target_nullable BIT NOT NULL,
    PRIMARY KEY (table_name, column_name));
INSERT @targets (table_name, column_name, target_type, target_max_length_bytes, target_nullable) VALUES
    (N'users', N'name', N'nvarchar', 200, 0),
    (N'sport', N'name', N'nvarchar', 200, 0),
    (N'sport', N'description', N'nvarchar', -1, 1),
    (N'coach_sport', N'certification', N'nvarchar', 510, 1),
    (N'member_profile', N'goal', N'nvarchar', -1, 1),
    (N'course', N'name', N'nvarchar', 510, 0),
    (N'course', N'description', N'nvarchar', -1, 1),
    (N'course', N'learning_outcome', N'nvarchar', -1, 1),
    (N'class', N'name', N'nvarchar', 200, 0),
    (N'course_offering', N'name', N'nvarchar', 300, 0),
    (N'facility', N'name', N'nvarchar', 200, 0),
    (N'facility', N'type', N'nvarchar', 100, 0),
    (N'membership_plan', N'name', N'nvarchar', 200, 0),
    (N'membership_plan', N'description', N'nvarchar', -1, 1);

-- Existing type, byte length, nullability, collation, identity/computed state.
SELECT t.table_name, t.column_name, t.target_type AS proposed_type,
       t.target_max_length_bytes AS proposed_max_length_bytes, t.target_nullable AS proposed_is_nullable,
       s.name AS schema_name, o.name AS actual_table_name, c.name AS actual_column_name,
       ty.name AS actual_type, c.max_length AS actual_max_length_bytes,
       c.is_nullable AS actual_is_nullable, c.collation_name,
       c.is_identity, c.is_computed, c.generated_always_type_desc
FROM @targets AS t
LEFT JOIN sys.schemas AS s ON s.name = N'dbo'
LEFT JOIN sys.tables AS o ON o.schema_id = s.schema_id AND o.name = t.table_name
LEFT JOIN sys.columns AS c ON c.object_id = o.object_id AND c.name = t.column_name
LEFT JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
ORDER BY t.table_name, t.column_name;

-- Every index containing a target column, with the complete key/include layout.
SELECT target.table_name, target.column_name, i.name AS index_name,
       i.type_desc, i.is_unique, i.is_primary_key, i.is_unique_constraint,
       i.has_filter, i.filter_definition, i.is_disabled, i.is_hypothetical,
       ic.key_ordinal, ic.is_included_column, ic.is_descending_key,
       indexed_column.name AS index_column_name
FROM @targets AS target
JOIN sys.schemas AS s ON s.name = N'dbo'
JOIN sys.tables AS o ON o.schema_id = s.schema_id AND o.name = target.table_name
JOIN sys.columns AS target_column ON target_column.object_id = o.object_id
                                  AND target_column.name = target.column_name
JOIN sys.index_columns AS target_ic ON target_ic.object_id = o.object_id
                                    AND target_ic.column_id = target_column.column_id
JOIN sys.indexes AS i ON i.object_id = target_ic.object_id AND i.index_id = target_ic.index_id
JOIN sys.index_columns AS ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns AS indexed_column ON indexed_column.object_id = ic.object_id
                                   AND indexed_column.column_id = ic.column_id
ORDER BY target.table_name, target.column_name, i.name, ic.key_ordinal, ic.index_column_id;

-- Defaults and checks attached to, or referencing, candidate columns.
SELECT target.table_name, target.column_name, dc.name AS default_constraint_name,
       dc.definition AS default_definition, cc.name AS check_constraint_name,
       cc.definition AS check_definition, cc.is_disabled AS check_is_disabled,
       cc.is_not_trusted AS check_is_not_trusted
FROM @targets AS target
JOIN sys.schemas AS s ON s.name = N'dbo'
JOIN sys.tables AS o ON o.schema_id = s.schema_id AND o.name = target.table_name
JOIN sys.columns AS c ON c.object_id = o.object_id AND c.name = target.column_name
LEFT JOIN sys.default_constraints AS dc ON dc.parent_object_id = o.object_id
                                        AND dc.parent_column_id = c.column_id
LEFT JOIN sys.check_constraints AS cc ON cc.parent_object_id = o.object_id
    AND (cc.parent_column_id = c.column_id OR cc.definition LIKE N'%' + QUOTENAME(c.name) + N'%'
         OR cc.definition LIKE N'%' + c.name + N'%')
ORDER BY target.table_name, target.column_name, dc.name, cc.name;

-- Foreign-key dependencies, if any, on either side of a candidate column.
SELECT target.table_name, target.column_name, fk.name AS foreign_key_name,
       parent_schema.name AS parent_schema, parent_table.name AS parent_table,
       parent_column.name AS parent_column, referenced_schema.name AS referenced_schema,
       referenced_table.name AS referenced_table, referenced_column.name AS referenced_column,
       fk.delete_referential_action_desc, fk.update_referential_action_desc,
       fk.is_disabled, fk.is_not_trusted
FROM @targets AS target
JOIN sys.schemas AS s ON s.name = N'dbo'
JOIN sys.tables AS target_table ON target_table.schema_id = s.schema_id
                               AND target_table.name = target.table_name
JOIN sys.columns AS target_column ON target_column.object_id = target_table.object_id
                                 AND target_column.name = target.column_name
JOIN sys.foreign_key_columns AS fkc
  ON (fkc.parent_object_id = target_table.object_id AND fkc.parent_column_id = target_column.column_id)
  OR (fkc.referenced_object_id = target_table.object_id AND fkc.referenced_column_id = target_column.column_id)
JOIN sys.foreign_keys AS fk ON fk.object_id = fkc.constraint_object_id
JOIN sys.tables AS parent_table ON parent_table.object_id = fkc.parent_object_id
JOIN sys.schemas AS parent_schema ON parent_schema.schema_id = parent_table.schema_id
JOIN sys.columns AS parent_column ON parent_column.object_id = fkc.parent_object_id
                                 AND parent_column.column_id = fkc.parent_column_id
JOIN sys.tables AS referenced_table ON referenced_table.object_id = fkc.referenced_object_id
JOIN sys.schemas AS referenced_schema ON referenced_schema.schema_id = referenced_table.schema_id
JOIN sys.columns AS referenced_column ON referenced_column.object_id = fkc.referenced_object_id
                                      AND referenced_column.column_id = fkc.referenced_column_id
ORDER BY target.table_name, target.column_name, fk.name;

-- Full-text and schema-bound SQL module dependencies can also block conversion.
SELECT target.table_name, target.column_name, fulltext_key_index.name AS fulltext_key_index_name,
       ftc.language_id, ftc.type_column_id
FROM @targets AS target
JOIN sys.schemas AS s ON s.name = N'dbo'
JOIN sys.tables AS o ON o.schema_id = s.schema_id AND o.name = target.table_name
JOIN sys.columns AS c ON c.object_id = o.object_id AND c.name = target.column_name
JOIN sys.fulltext_index_columns AS ftc ON ftc.object_id = o.object_id AND ftc.column_id = c.column_id
JOIN sys.fulltext_indexes AS fi ON fi.object_id = o.object_id
JOIN sys.indexes AS fulltext_key_index ON fulltext_key_index.object_id = fi.object_id
                                       AND fulltext_key_index.index_id = fi.unique_index_id
ORDER BY target.table_name, target.column_name;

SELECT target.table_name, target.column_name, referencing_schema.name AS referencing_schema,
       referencing_object.name AS referencing_object, referencing_object.type_desc,
       dependency.is_schema_bound_reference
FROM @targets AS target
JOIN sys.schemas AS target_schema ON target_schema.name = N'dbo'
JOIN sys.tables AS target_table ON target_table.schema_id = target_schema.schema_id
                               AND target_table.name = target.table_name
JOIN sys.columns AS target_column ON target_column.object_id = target_table.object_id
                                 AND target_column.name = target.column_name
JOIN sys.sql_expression_dependencies AS dependency
  ON dependency.referenced_id = target_table.object_id
 AND (dependency.referenced_minor_id = target_column.column_id OR dependency.referenced_minor_id = 0)
JOIN sys.objects AS referencing_object ON referencing_object.object_id = dependency.referencing_id
JOIN sys.schemas AS referencing_schema ON referencing_schema.schema_id = referencing_object.schema_id
ORDER BY target.table_name, target.column_name, referencing_schema.name, referencing_object.name;
