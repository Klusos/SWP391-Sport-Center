-- Additive JWT revocation schema for Phase 16Q.2A (SQL Server 2016+).
-- Review the selected database, take a verified backup, and stop application writers first.
-- This script is not run automatically by Spring Boot. Apply it manually before deploying
-- the backend that maps dbo.auth_revoked_token with Hibernate schema validation enabled.
-- Update @expectedDatabase below only after the deployment target is explicitly approved.
-- Set @expectedServer to the exact server_property_name reviewed from the preflight output.
-- It intentionally defaults to NULL so this migration cannot run without that review.
-- Rollback before deploying the new backend: drop ix_auth_revoked_token_expires_at and
-- dbo.auth_revoked_token after confirming no application instance still uses revocation.
-- Dropping the table while the revocation-enabled backend runs makes checks fail closed
-- (503). Rolling back to pre-revocation backend code afterward can accept unexpired JWTs;
-- do not perform that combined rollback without a separate security decision.
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
SET NOCOUNT ON;

DECLARE @expectedDatabase sysname = N'SportCenterDemo';
DECLARE @expectedServer sysname = NULL;
DECLARE @actualServer sysname = CONVERT(sysname, SERVERPROPERTY('ServerName'));
IF DB_NAME() <> @expectedDatabase
    THROW 51621, 'Select the explicitly approved target database before applying JWT revocation schema.', 1;
IF @expectedServer IS NULL OR @actualServer IS NULL OR @actualServer <> @expectedServer
    THROW 51627, 'Set and verify the expected SQL Server instance from the approved preflight before migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 51622, 'Run the JWT revocation migration without an existing transaction.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.auth_revoked_token', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.auth_revoked_token (
            token_fingerprint BINARY(32) NOT NULL
                CONSTRAINT pk_auth_revoked_token PRIMARY KEY,
            expires_at DATETIME2(7) NOT NULL,
            revoked_at DATETIME2(7) NOT NULL
        );
        CREATE INDEX ix_auth_revoked_token_expires_at
            ON dbo.auth_revoked_token (expires_at);
    END
    ELSE
    BEGIN
        IF EXISTS (
            SELECT expected.column_name, expected.type_name, expected.max_length,
                   expected.scale, expected.is_nullable
            FROM (VALUES
                (N'token_fingerprint', N'binary', 32, 0, CONVERT(bit, 0)),
                (N'expires_at', N'datetime2', 8, 7, CONVERT(bit, 0)),
                (N'revoked_at', N'datetime2', 8, 7, CONVERT(bit, 0))
            ) AS expected(column_name, type_name, max_length, scale, is_nullable)
            LEFT JOIN sys.columns AS c
                ON c.object_id = OBJECT_ID(N'dbo.auth_revoked_token')
               AND c.name = expected.column_name
            LEFT JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
            WHERE c.column_id IS NULL OR ty.name <> expected.type_name
               OR c.max_length <> expected.max_length OR c.scale <> expected.scale
               OR c.is_nullable <> expected.is_nullable
        ) OR (SELECT COUNT(*) FROM sys.columns
              WHERE object_id = OBJECT_ID(N'dbo.auth_revoked_token')) <> 3
            THROW 51623, 'Existing dbo.auth_revoked_token has an incompatible column definition.', 1;

        IF NOT EXISTS (
            SELECT 1
            FROM sys.key_constraints AS kc
            JOIN sys.index_columns AS ic
              ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
            JOIN sys.columns AS c
              ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE kc.parent_object_id = OBJECT_ID(N'dbo.auth_revoked_token')
              AND kc.type = N'PK' AND c.name = N'token_fingerprint'
              AND ic.key_ordinal = 1
              AND (SELECT COUNT(*) FROM sys.index_columns AS all_ic
                   WHERE all_ic.object_id = ic.object_id
                     AND all_ic.index_id = ic.index_id AND all_ic.key_ordinal > 0) = 1
        )
            THROW 51624, 'Existing dbo.auth_revoked_token primary key is missing or incompatible.', 1;

        IF EXISTS (SELECT 1 FROM sys.indexes
                   WHERE object_id = OBJECT_ID(N'dbo.auth_revoked_token')
                     AND name = N'ix_auth_revoked_token_expires_at')
        BEGIN
            IF NOT EXISTS (
                SELECT 1
                FROM sys.indexes AS i
                JOIN sys.index_columns AS ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                JOIN sys.columns AS c ON c.object_id = i.object_id AND c.column_id = ic.column_id
                WHERE i.object_id = OBJECT_ID(N'dbo.auth_revoked_token')
                  AND i.name = N'ix_auth_revoked_token_expires_at'
                  AND i.is_unique = 0 AND i.is_disabled = 0
                  AND i.type = 2 AND i.has_filter = 0 AND i.is_hypothetical = 0
                  AND c.name = N'expires_at' AND ic.key_ordinal = 1
                  AND (SELECT COUNT(*) FROM sys.index_columns AS all_ic
                       WHERE all_ic.object_id = i.object_id
                         AND all_ic.index_id = i.index_id
                         AND (all_ic.key_ordinal > 0 OR all_ic.is_included_column = 1)) = 1
            )
                THROW 51625, 'Existing expiration index has an incompatible definition.', 1;
        END
        ELSE
        BEGIN
            CREATE INDEX ix_auth_revoked_token_expires_at
                ON dbo.auth_revoked_token (expires_at);
        END;
    END;

    IF OBJECT_ID(N'dbo.auth_revoked_token', N'U') IS NULL
       OR COL_LENGTH(N'dbo.auth_revoked_token', N'token_fingerprint') <> 32
       OR COL_LENGTH(N'dbo.auth_revoked_token', N'expires_at') <> 8
       OR COL_LENGTH(N'dbo.auth_revoked_token', N'revoked_at') <> 8
       OR NOT EXISTS (SELECT 1 FROM sys.indexes
                      WHERE object_id = OBJECT_ID(N'dbo.auth_revoked_token')
                        AND name = N'ix_auth_revoked_token_expires_at'
                        AND is_disabled = 0)
        THROW 51626, 'JWT revocation schema validation failed.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Read-only post-migration validation.
SELECT DB_NAME() AS database_name, s.name AS schema_name, t.name AS table_name,
       c.name AS column_name, ty.name AS type_name, c.max_length, c.scale, c.is_nullable
FROM sys.schemas AS s
JOIN sys.tables AS t ON t.schema_id = s.schema_id
JOIN sys.columns AS c ON c.object_id = t.object_id
JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
WHERE s.name = N'dbo' AND t.name = N'auth_revoked_token'
ORDER BY c.column_id;

SELECT i.name AS index_name, c.name AS indexed_column, i.is_unique, i.is_disabled
FROM sys.indexes AS i
JOIN sys.tables AS t ON t.object_id = i.object_id
JOIN sys.schemas AS s ON s.schema_id = t.schema_id
JOIN sys.index_columns AS ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns AS c ON c.object_id = t.object_id AND c.column_id = ic.column_id
WHERE s.name = N'dbo' AND t.name = N'auth_revoked_token'
ORDER BY i.name, ic.key_ordinal;
