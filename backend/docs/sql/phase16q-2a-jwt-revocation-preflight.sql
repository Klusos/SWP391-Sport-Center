-- Read-only preflight for Phase 16Q.2A JWT revocation schema.
-- Do not execute against a production or demo database without separate approval.
SET NOCOUNT ON;

SELECT CONVERT(sysname, SERVERPROPERTY('ServerName')) AS server_property_name,
       @@SERVERNAME AS configured_server_name,
       DB_NAME() AS selected_database,
       DB_ID() AS database_id,
       d.state_desc AS database_state,
       d.compatibility_level,
       OBJECT_ID(N'dbo.auth_revoked_token', N'U') AS existing_table_object_id
FROM sys.databases AS d
WHERE d.database_id = DB_ID();

SELECT s.name AS schema_name,
       t.name AS table_name,
       c.name AS column_name,
       ty.name AS type_name,
       c.max_length,
       c.scale,
       c.is_nullable,
       c.is_identity
FROM sys.schemas AS s
JOIN sys.tables AS t ON t.schema_id = s.schema_id
JOIN sys.columns AS c ON c.object_id = t.object_id
JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
WHERE s.name = N'dbo' AND t.name = N'auth_revoked_token'
ORDER BY c.column_id;

SELECT kc.name AS primary_key_name,
       i.name AS index_name,
       c.name AS key_column,
       ic.key_ordinal,
       i.is_unique,
       i.is_disabled
FROM sys.key_constraints AS kc
JOIN sys.tables AS t ON t.object_id = kc.parent_object_id
JOIN sys.schemas AS s ON s.schema_id = t.schema_id
JOIN sys.indexes AS i ON i.object_id = t.object_id AND i.index_id = kc.unique_index_id
JOIN sys.index_columns AS ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns AS c ON c.object_id = t.object_id AND c.column_id = ic.column_id
WHERE s.name = N'dbo' AND t.name = N'auth_revoked_token' AND kc.type = N'PK'
ORDER BY ic.key_ordinal;

SELECT i.name AS index_name,
       c.name AS indexed_column,
       ic.key_ordinal,
       ic.is_included_column,
       i.is_unique,
       i.is_disabled,
       i.type_desc,
       i.has_filter,
       i.filter_definition,
       i.is_hypothetical
FROM sys.indexes AS i
JOIN sys.tables AS t ON t.object_id = i.object_id
JOIN sys.schemas AS s ON s.schema_id = t.schema_id
JOIN sys.index_columns AS ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns AS c ON c.object_id = t.object_id AND c.column_id = ic.column_id
WHERE s.name = N'dbo' AND t.name = N'auth_revoked_token'
  AND i.name = N'ix_auth_revoked_token_expires_at'
ORDER BY ic.key_ordinal;
