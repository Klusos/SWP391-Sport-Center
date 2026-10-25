-- Phase 8.7B: Facility-to-Sport compatibility join table.
-- SQL Server 2016+. Run manually as a standalone batch after a verified backup,
-- with application writers stopped, in SportCenterDB. This script is not auto-run.
-- It preserves dbo.facility.sport_id and its existing FK as a compatibility mirror;
-- it does not drop or alter any legacy object. Existing assignments are copied once
-- only when this script creates the new join table. Later reruns validate the table
-- and do not backfill or modify assignments.
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
    THROW 50801, 'Select SportCenterDB before applying the Facility-Sport migration.', 1;
IF @@TRANCOUNT <> 0
    THROW 50802, 'Run this script without an existing transaction.', 1;
IF OBJECT_ID(N'dbo.facility', N'U') IS NULL OR OBJECT_ID(N'dbo.sport', N'U') IS NULL
    THROW 50803, 'Expected existing dbo.facility and dbo.sport core tables.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @lockResult INT, @facilityId INT = OBJECT_ID(N'dbo.facility', N'U'),
            @sportId INT = OBJECT_ID(N'dbo.sport', N'U'), @joinId INT,
            @created BIT = 0;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'SportCenter.FacilitySport.Schema', @LockMode = 'Exclusive',
        @LockOwner = 'Transaction', @LockTimeout = 10000;
    IF @lockResult < 0
        THROW 50804, 'Could not acquire the Facility-Sport schema lock; retry with writers stopped.', 1;

    -- Require INT identity primary keys for the referenced entities.
    IF EXISTS (
        SELECT 1 FROM (VALUES (@facilityId), (@sportId)) t(object_id)
        WHERE NOT EXISTS (
            SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = N'id'
              AND c.system_type_id = TYPE_ID(N'int') AND c.user_type_id = c.system_type_id
              AND c.is_nullable = 0 AND c.is_identity = 1 AND c.is_computed = 0
              AND c.generated_always_type = 0
        ) OR NOT EXISTS (
            SELECT 1 FROM sys.indexes i
            JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
            JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE i.object_id = t.object_id AND i.is_primary_key = 1 AND i.is_unique = 1
              AND i.is_disabled = 0 AND i.is_hypothetical = 0 AND ic.key_ordinal = 1
              AND c.name = N'id'
              AND (SELECT COUNT(*) FROM sys.index_columns k WHERE k.object_id = i.object_id
                   AND k.index_id = i.index_id AND k.key_ordinal > 0) = 1
        )
    ) THROW 50805, 'dbo.facility.id and dbo.sport.id must be INT IDENTITY primary keys.', 1;

    -- Backfill source must be the established required facility.sport_id column,
    -- protected by exactly one trusted, enabled, single-column NO ACTION FK to sport.id.
    IF NOT EXISTS (
        SELECT 1 FROM sys.columns c WHERE c.object_id = @facilityId AND c.name = N'sport_id'
          AND c.system_type_id = TYPE_ID(N'int') AND c.user_type_id = c.system_type_id
          AND c.is_nullable = 0 AND c.is_identity = 0 AND c.is_computed = 0
          AND c.generated_always_type = 0
    ) THROW 50806, 'Expected dbo.facility.sport_id INT NOT NULL; inspect the live schema before proceeding.', 1;
    IF (SELECT COUNT(*) FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        WHERE fk.parent_object_id = @facilityId
          AND fkc.parent_column_id = COLUMNPROPERTY(@facilityId, N'sport_id', 'ColumnId')) <> 1
       OR NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        WHERE fk.parent_object_id = @facilityId
          AND fkc.parent_column_id = COLUMNPROPERTY(@facilityId, N'sport_id', 'ColumnId')
          AND fkc.referenced_object_id = @sportId
          AND COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) = N'id'
          AND fk.is_disabled = 0 AND fk.is_not_trusted = 0 AND fk.is_not_for_replication = 0
          AND fk.delete_referential_action = 0 AND fk.update_referential_action = 0
          AND (SELECT COUNT(*) FROM sys.foreign_key_columns x
               WHERE x.constraint_object_id = fk.object_id) = 1
    ) THROW 50807, 'dbo.facility.sport_id must have exactly one trusted, enabled FK to dbo.sport.id with NO ACTION semantics.', 1;

    IF EXISTS (SELECT 1 FROM sys.objects WHERE name = N'facility_sport' AND schema_id = SCHEMA_ID(N'dbo'))
       AND OBJECT_ID(N'dbo.facility_sport', N'U') IS NULL
        THROW 50808, 'dbo.facility_sport is occupied by a non-table object.', 1;

    IF OBJECT_ID(N'dbo.facility_sport', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.facility_sport (
            facility_id INT NOT NULL,
            sport_id INT NOT NULL,
            CONSTRAINT pk_facility_sport PRIMARY KEY (facility_id, sport_id),
            CONSTRAINT fk_facility_sport_facility FOREIGN KEY (facility_id) REFERENCES dbo.facility(id),
            CONSTRAINT fk_facility_sport_sport FOREIGN KEY (sport_id) REFERENCES dbo.sport(id)
        );
        CREATE INDEX ix_facility_sport_sport ON dbo.facility_sport(sport_id);
        SET @created = 1;
    END;

    SET @joinId = OBJECT_ID(N'dbo.facility_sport', N'U');
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = @joinId)
       OR EXISTS (SELECT 1 FROM sys.default_constraints WHERE parent_object_id = @joinId)
       OR EXISTS (SELECT 1 FROM sys.triggers WHERE parent_id = @joinId)
        THROW 50817, 'dbo.facility_sport has unexpected CHECK/default/trigger dependencies; inspect manually.', 1;
    IF (SELECT COUNT(*) FROM sys.indexes WHERE object_id = @joinId AND index_id > 0) <> 2
        THROW 50816, 'dbo.facility_sport must have only its primary key and the sport_id lookup index.', 1;
    IF (SELECT COUNT(*) FROM sys.columns WHERE object_id = @joinId) <> 2
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = @joinId AND name = N'facility_id'
                      AND system_type_id = TYPE_ID(N'int') AND user_type_id = system_type_id
                      AND is_nullable = 0 AND is_identity = 0 AND is_computed = 0)
       OR NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = @joinId AND name = N'sport_id'
                      AND system_type_id = TYPE_ID(N'int') AND user_type_id = system_type_id
                      AND is_nullable = 0 AND is_identity = 0 AND is_computed = 0)
        THROW 50809, 'dbo.facility_sport must contain exactly facility_id and sport_id as INT NOT NULL columns.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.key_constraints kc
        JOIN sys.indexes i ON i.object_id = kc.parent_object_id AND i.index_id = kc.unique_index_id
        WHERE kc.parent_object_id = @joinId AND kc.type = N'PK' AND kc.name = N'pk_facility_sport'
          AND i.type = 1 AND i.is_unique = 1 AND i.is_disabled = 0 AND i.is_hypothetical = 0
          AND i.has_filter = 0 AND i.ignore_dup_key = 0 AND i.is_padded = 0
          AND i.fill_factor = 0 AND i.allow_row_locks = 1 AND i.allow_page_locks = 1
          AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = i.object_id
               AND ic.index_id = i.index_id AND (ic.key_ordinal > 0 OR ic.is_included_column = 1)) = 2
          AND EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                      ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                      WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                        AND ic.key_ordinal = 1 AND ic.is_descending_key = 0 AND c.name = N'facility_id')
          AND EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                      ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                      WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                        AND ic.key_ordinal = 2 AND ic.is_descending_key = 0 AND c.name = N'sport_id')
    ) THROW 50810, 'dbo.facility_sport requires the verified enabled two-column primary key (facility_id, sport_id).', 1;

    IF (SELECT COUNT(*) FROM sys.foreign_keys fk WHERE fk.parent_object_id = @joinId) <> 2
       OR EXISTS (
        SELECT 1 FROM sys.foreign_keys fk
        WHERE fk.parent_object_id = @joinId
          AND (fk.is_disabled = 1 OR fk.is_not_trusted = 1 OR fk.is_not_for_replication = 1
               OR fk.delete_referential_action <> 0 OR fk.update_referential_action <> 0
               OR (SELECT COUNT(*) FROM sys.foreign_key_columns x
                   WHERE x.constraint_object_id = fk.object_id) <> 1)
    ) THROW 50811, 'dbo.facility_sport has incompatible or unsupported foreign-key metadata.', 1;

    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
        WHERE fk.parent_object_id = @joinId AND fk.name = N'fk_facility_sport_facility'
          AND c.name = N'facility_id' AND fkc.referenced_object_id = @facilityId
          AND COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) = N'id')
       OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
        WHERE fk.parent_object_id = @joinId AND fk.name = N'fk_facility_sport_sport'
          AND c.name = N'sport_id' AND fkc.referenced_object_id = @sportId
          AND COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) = N'id')
        THROW 50812, 'dbo.facility_sport foreign keys do not match the expected Facility and Sport references.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id = @joinId AND i.name = N'ix_facility_sport_sport'
          AND i.type = 2 AND i.is_unique = 0 AND i.is_disabled = 0 AND i.is_hypothetical = 0
          AND i.has_filter = 0 AND i.ignore_dup_key = 0 AND i.is_padded = 0
          AND i.fill_factor = 0 AND i.allow_row_locks = 1 AND i.allow_page_locks = 1
          AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = i.object_id
               AND ic.index_id = i.index_id AND (ic.key_ordinal > 0 OR ic.is_included_column = 1)) = 1
          AND EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.columns c
                      ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                      WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
                        AND ic.key_ordinal = 1 AND ic.is_descending_key = 0 AND c.name = N'sport_id')
    ) THROW 50813, 'dbo.facility_sport requires the expected enabled sport_id lookup index.', 1;

    -- Creation and initial copy are atomic. Existing join tables are never repaired
    -- by guessing; a rerun validates only and fails if any Facility lacks an assignment.
    IF @created = 1
        INSERT dbo.facility_sport(facility_id, sport_id)
        SELECT f.id, f.sport_id FROM dbo.facility f;

    IF EXISTS (SELECT 1 FROM dbo.facility f WHERE NOT EXISTS
               (SELECT 1 FROM dbo.facility_sport fs WHERE fs.facility_id = f.id))
        THROW 50814, 'At least one Facility has no supported Sport assignment; review data before continuing.', 1;
    IF EXISTS (SELECT 1 FROM dbo.facility f WHERE NOT EXISTS
               (SELECT 1 FROM dbo.facility_sport fs
                WHERE fs.facility_id = f.id AND fs.sport_id = f.sport_id))
        THROW 50818, 'A Facility legacy sport_id is not among its supported Sport assignments; inspect manually.', 1;
    IF EXISTS (SELECT 1 FROM dbo.facility_sport fs
               LEFT JOIN dbo.facility f ON f.id = fs.facility_id
               LEFT JOIN dbo.sport s ON s.id = fs.sport_id
               WHERE f.id IS NULL OR s.id IS NULL)
        THROW 50815, 'dbo.facility_sport contains orphan assignments; no data was changed.', 1;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Hibernate mapping uses dbo.facility_sport(facility_id, sport_id). The existing
-- dbo.facility.sport_id column remains required and mirrors the first assigned Sport;
-- removal of that legacy column/FK requires a later separately reviewed migration.
