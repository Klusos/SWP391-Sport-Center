# SportCenter SQL Server submission schema

## Deliverables

> **As-built update (2026-11-28):** The original snapshot metadata date/counts predate the weekly General Training Plan addition. This file was synchronized with the reviewed DB5 additive migration: the current snapshot contains 38 user tables and 299 columns, including `dbo.training_plan_week`, nullable `dbo.training_exercise.week_id`, the plan-ownership composite FK, week-number check/unique constraints and indexes. This is a fresh-schema representation, not a DB5 migration. Existing DB5 deployment was separately guarded and Hibernate-validated; do not rerun the snapshot on an existing database. See [ERD](../../docs/ERD_AS_BUILT.md) and [training-plan acceptance evidence](../../docs/TRAINING_PLAN_REDESIGN_ACCEPTANCE.md).

- `SportCenter_FullSchema.sql` — schema-only snapshot originally generated from verified DB5 metadata on November 12, 2026 and updated on November 28 to include the separately reviewed weekly General Training Plan schema.
- No reference-data seed script is included. Existing demo rows include account data and password hashes; no scrubbed seed dataset was approved for submission.

## Snapshot inventory

The script currently includes **38 user tables** and **299 columns**, including `dbo.auth_revoked_token` and General Training Plan weeks. It also includes the migration-defined constraints and indexes. Inspect the SQL file for exact names and counts; counts can change with reviewed schema updates. Foreign keys are added after tables and keys so table creation order does not depend on relationship cycles.

The script contains no row data, user logins, passwords, password hashes, JWTs, application credentials, or database creation/drop commands. It does not execute the stored procedures or function; it only defines them.

## Fresh database setup

1. Create a new empty SQL Server database through the approved database administration process. The script deliberately does not create a database.
2. Connect to that database in SSMS or `sqlcmd` and verify `SELECT DB_NAME()` returns the intended empty target.
3. Review `SportCenter_FullSchema.sql`, then execute it once with DDL permissions.
4. Validate the resulting schema and application mappings before loading any approved reference data.

Target SQL Server 2016 or later. The script uses `GO` batch separators and should be run in SSMS or `sqlcmd`, not submitted as one JDBC statement.

This is a fresh-schema snapshot. **Do not run it against SportCenterDemo or an existing installation**: it creates objects and will fail on name collisions. Existing databases must follow the reviewed historical migration sequence in `backend/docs/sql/README.md` instead.

## JWT revocation migration

`dbo.auth_revoked_token` is already included in the full-schema snapshot. Do not also apply `backend/docs/sql/phase16q-2a-jwt-revocation.sql` after running this snapshot.

For a separate deployment that applies the JWT migration to an existing database, the checked-in migration intentionally leaves `@expectedServer` as `NULL` so it fails closed. After reviewing the exact target, set `@expectedDatabase` to that database name and `@expectedServer` to the verified `SERVERPROPERTY('ServerName')`; retain the guards, transaction, and schema checks. The SportCenterDemo migration has already been applied and verified separately.

## Scope limits

This deliverable scripts relational schema and the listed SQL modules. It does not provision SQL logins/users, roles or grants, server-level settings, SQL Agent jobs, database backup/restore, or application configuration. Those must be handled separately with least privilege and without placing credentials in submission files.

## Local application and Swagger

Start the backend from `backend/` using the documented Spring Boot run target after supplying required local secrets in an ignored `backend/.env` or process environment. The normal local SQL Server default is `SportCenterDemo`; do not copy credentials into shared files. Keep `spring.jpa.hibernate.ddl-auto=validate`. Swagger UI is `http://localhost:8080/swagger-ui/index.html`; OpenAPI JSON is `http://localhost:8080/v3/api-docs`. Use **Authorize** with a valid JWT for protected operations. Public docs do not make business endpoints public. Verify actual server/database identity before any mutation test; QA overrides must be process-local, target the approved clone and disable schedulers.
