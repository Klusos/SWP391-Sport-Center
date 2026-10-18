# SQL Server setup and migration order

These files are incremental schema migrations, not a complete database bootstrap. The repository does not contain the original core-schema creation script. Obtain and apply the approved core schema separately before running these migrations; at minimum it must provide the JPA-mapped core tables, including `dbo.users`, `dbo.sport`, `dbo.coach`, `dbo.coach_sport`, `dbo.course`, `dbo.course_offering`, `dbo.[class]`, `dbo.facility`, `dbo.member_profile`, `dbo.membership`, and `dbo.membership_plan`, with their established columns and keys. Do not use Hibernate `create`/`update` as a substitute; the application is configured for schema validation.

For a read-only review of text storage types and likely Vietnamese mojibake markers, use `phase3b1-vietnamese-unicode-diagnostic.sql`. It reports metadata and aggregate match counts, not stored personal text. It cannot prove a value is corrupted or recover characters already lost through a non-Unicode column.

## Phase 3B.2 Vietnamese Unicode conversion

The reviewed migration is `phase3b2-vietnamese-unicode.sql`. A successful read-only preflight on the target `SportCenterDB` confirmed the 14 columns and the sole candidate index on `dbo.sport.name`: a `UQ` unique, nonclustered, constraint-backed key on that column alone. The migration rechecks the constraint shape, enabled state, persisted index options, storage filegroup and dependencies. It reads the actual constraint name from SQL Server metadata and never assumes it. Before altering `sport.name`, it checks for Unicode uniqueness collisions; within the same transaction it drops only that verified constraint, converts the column, and recreates the same constraint name and supported options. Any DDL or verification failure rolls the transaction back, including restoring the original column and constraint. It preserves `Chinese_PRC_CI_AS`, column lengths and nullability, and stops on any unexpected schema/dependency or unsupported index metadata.

Execution order: apply the approved core schema and required feature migrations through `phase3a-coach-bio.sql` (see the fresh-database order below), then take and verify a full database backup. Run `phase3b2-vietnamese-unicode-preflight.sql` again immediately before migration and retain all result sets. Inspect trigger and SQL module definitions for dynamic or uncatalogued references. Restore the backup to a separate test database and rehearse this exact migration there. After stopping application writers, run `phase3b2-vietnamese-unicode.sql` as one standalone SSMS batch selected against `SportCenterDB`. It has a database guard, transaction, error rollback, application lock, exact metadata/dependency checks, and rerun handling for original or already-converted columns. The unique constraint must be named dynamically from metadata and recreated under that same name; do not substitute a hardcoded name. The script preserves the reviewed index options and filegroup, rejecting partitioned/compressed indexes and newer unsupported options. Large `TEXT` fields convert directly to `NVARCHAR(MAX)`. Do not execute the migration from application startup.

The reported `C? Vua` value has already lost its original character bytes (`0x3F`); converting the column to NVARCHAR will preserve the question mark and cannot reconstruct `ờ`. Correct that particular value only through an explicitly reviewed manual data correction after the schema conversion. Do not perform a bulk rewrite based on guessed values.

After execution on the approved target, run these read-only checks and start the backend with `spring.jpa.hibernate.ddl-auto=validate`. Keep that setting enabled. Recover using the verified pre-migration backup or the database owner's point-in-time restore procedure. Do not reverse-convert to `VARCHAR` after Unicode values have been written; that can irreversibly lose characters.

```sql
-- Column metadata: NVARCHAR(n) max_length is reported in bytes; MAX is -1.
SELECT s.name AS schema_name, t.name AS table_name, c.name AS column_name,
       ty.name AS data_type, c.max_length AS max_length_bytes,
       c.is_nullable, c.collation_name
FROM sys.schemas AS s
JOIN sys.tables AS t ON t.schema_id = s.schema_id
JOIN sys.columns AS c ON c.object_id = t.object_id
JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
WHERE s.name = N'dbo'
  AND ((t.name = N'users' AND c.name = N'name')
    OR (t.name = N'sport' AND c.name IN (N'name', N'description'))
    OR (t.name = N'coach_sport' AND c.name = N'certification')
    OR (t.name = N'member_profile' AND c.name = N'goal')
    OR (t.name = N'course' AND c.name IN (N'name', N'description', N'learning_outcome'))
    OR (t.name = N'class' AND c.name = N'name')
    OR (t.name = N'course_offering' AND c.name = N'name')
    OR (t.name = N'facility' AND c.name IN (N'name', N'type'))
    OR (t.name = N'membership_plan' AND c.name IN (N'name', N'description')))
ORDER BY t.name, c.name;

-- The actual constraint name is read from metadata; no name is assumed.
SELECT kc.name AS constraint_name, i.name AS backing_index_name,
       i.type_desc, i.is_unique, i.is_disabled, i.has_filter,
       ic.key_ordinal, c.name AS key_column, ic.is_descending_key,
       ic.is_included_column
FROM sys.key_constraints AS kc
JOIN sys.tables AS t ON t.object_id = kc.parent_object_id
JOIN sys.schemas AS s ON s.schema_id = t.schema_id
JOIN sys.indexes AS i ON i.object_id = kc.parent_object_id AND i.index_id = kc.unique_index_id
JOIN sys.index_columns AS ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE s.name = N'dbo' AND t.name = N'sport' AND kc.type = N'UQ'
ORDER BY ic.key_ordinal, ic.index_column_id;
```

Expect all 14 columns to be `nvarchar`, with byte lengths twice their declared character limits (or `-1` for `nvarchar(max)`), original nullability, and `Chinese_PRC_CI_AS`; the `sport.name` unique nonclustered single-key constraint must remain present and enabled.

For a fresh laptop database, use this order:

1. Apply the approved existing core-schema source (not present in this repository).
2. Run `phase0-class-sessions.sql` to create/validate `dbo.class_session`; its prerequisites are `dbo.[class]` and `dbo.facility`.
3. Run `phase1-member-sport-level-assessment.sql`; it requires `dbo.users`, `dbo.sport`, and `dbo.coach`.
4. Run `phase1_5-consultation-requests.sql`; it requires Phase 1's `dbo.skill_assessment` as well as `dbo.users` and `dbo.sport`.
5. Run `phase2-course-registrations.sql`; it requires `dbo.users` and `dbo.[class]`.
6. Run the read-only `phase2-personal-training-bookings-preflight.sql` and review column metadata, all CHECK constraints, foreign keys, and indexes. Then run `phase2-personal-training-bookings.sql`; it requires `dbo.users`, `dbo.coach`, `dbo.sport`, and `dbo.class_session`.
7. Run `phase4b1-class-attendance.sql`; it requires `dbo.class_session` and `dbo.users` and creates attendance tables. It also requires `phase2-course-registrations.sql` for the application's runtime roster eligibility checks.
8. Review and run `phase2-payment.sql` (Flow 3) manually; it requires `dbo.users`. It checks existing column metadata, key/FK structure and same-named indexes, replaces the payment CHECK constraints transactionally, and refuses legacy Course Registration rows whose reference meaning may differ. It is not executed automatically. The mock online callback is demo-only, disabled by default, and must never be used as real payment settlement.
9. Run `phase3-entity-image-urls.sql`; it requires `dbo.sport`, `dbo.coach`, and `dbo.facility` and adds nullable `image_url NVARCHAR(500)` columns only when absent. It validates existing columns and does not alter incompatible ones.
10. Run `phase3a-coach-bio.sql` after the image migration and before starting with `ddl-auto=validate`; it adds nullable `dbo.coach.bio NVARCHAR(1000)` and validates existing metadata.
11. After a verified backup and restored-database rehearsal, run `phase3b2-vietnamese-unicode-preflight.sql`, review its output and module/trigger dependencies, stop application writers, and run `phase3b2-vietnamese-unicode.sql` as a standalone batch. Confirm the metadata and unique constraint using the read-only checks above.
12. Run `phase6-1a-notifications.sql` after the core schema (it requires `dbo.users` with an `INT` primary key). It creates or validates `dbo.notification`, adds/validates its owner FK and defaults, and adds the user-created and filtered unread indexes. It is transactional and idempotent, rejects incompatible existing notification structures, and must be run manually; it has not been executed.
13. Run `phase7-1-audit-log.sql` after the core user table exists and before starting a build containing AuditLog (`ddl-auto=validate`). It creates or validates only `dbo.audit_log`, with a nullable non-cascading actor FK to `dbo.users`, and adds newest-first, actor, and entity indexes. It is transactional and rerunnable, preserves all existing data, and has not been executed.
14. Start the application after all required schema scripts complete; `spring.jpa.hibernate.ddl-auto=validate` will then verify mappings.

Run each SQL file as its own standalone batch while writers are stopped, after a backup, in `SportCenterDB` (or review the database-name guard for an explicitly renamed deployment). None of these scripts was executed as part of the repository change. A matching ClassSession table with incompatible metadata fails closed; the script never drops, rebuilds, or silently accepts it.

The PT preflight is read-only. If an older PT table has pending rows without `payment_expires_at`, the migration stops by default. After reviewing those rows and confirming that their timezone-less `created_at` values represent Asia/Ho_Chi_Minh wall time, an operator may explicitly set `@ConfirmLegacyPendingDeadlineBackfill` to `1` in the migration. The script then assigns `created_at + 15 minutes` only to pending rows whose deadline is null, and stops if a timestamp is null or cannot fit the added 15 minutes. Do not set the flag if the timestamp basis is uncertain; resolve those rows manually using a reviewed data plan first. Unexpected status-related CHECK constraints also stop the migration for manual inspection.

The incremental migrations create `member_sport_level`, `skill_assessment`, `consultation_request`, `course_registration`, and `personal_training_booking`; the image and Coach biography migrations add nullable columns to existing core tables. They do not create the original core tables listed above. Therefore this repository alone still cannot provision a truly empty SportCenter database; the approved core schema source must be recovered and included or supplied by the deployment owner.

The image URL migration is manual and has not been run automatically by the application. Apply it after the existing core and feature migrations, before starting the backend with `ddl-auto=validate`.

## Local development/demo seed

After the core schema and migrations are applied, `demo-seed.sql` can add fictional Flow 1 data to a dedicated local `SportCenterDB`. It is additive and transactional: it does not update/delete rows or reset passwords, and it rolls back if a deterministic account or relationship conflicts. Connect to the intended local SQL Server instance, select `SportCenterDB`, inspect the script, then change its `@ConfirmLocalDemoDatabase` variable from `0` to `1` to enable execution. The database-name guard cannot identify whether a server is local, so verify the connected server before enabling it. Do not run the script in shared or production environments.

Seeded login accounts all share this demo-only password and use the existing `POST /api/auth/login` endpoint:

| Role | Email |
| --- | --- |
| ADMIN | `demo.admin@sportcenter.example.test` |
| RECEPTIONIST | `demo.reception1@sportcenter.example.test`, `demo.reception2@sportcenter.example.test` |
| COACH | `demo.coach1@sportcenter.example.test`, `demo.coach2@sportcenter.example.test`, `demo.coach3@sportcenter.example.test` |
| MEMBER | `demo.member01@sportcenter.example.test` through `demo.member10@sportcenter.example.test` |

Password for local demonstration only: `SportCenterDemo!2026`. The database stores only a BCrypt hash. The seed creates 5 demo sports, 7 coach specializations, 10 member profiles, 3 membership plans, and 8 memberships. It does not seed payment, bill, enrollment, assessment, or consultation records.

## Phase 3A Coach biography migration

The project owner reports that `phase3-entity-image-urls.sql` was manually executed successfully in the local SQL Server environment. This repository did not execute that migration. The new `phase3a-coach-bio.sql` has not been run; back up the target database and apply it after the image URL migration, then start the backend with `ddl-auto=validate`. It adds only nullable `dbo.coach.bio NVARCHAR(1000)` and is idempotent.
