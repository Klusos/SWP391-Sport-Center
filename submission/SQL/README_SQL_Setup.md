# SportCenter SQL Server submission schema

## Deliverables

- `SportCenter_FullSchema.sql` — schema-only snapshot generated from the verified `LAPTOP-416VBDLK / SportCenterDemo` metadata on November 12, 2026.
- No reference-data seed script is included. Existing demo rows include account data and password hashes; no scrubbed seed dataset was approved for submission.

## Snapshot inventory

The script includes all **37 user tables** and their **288 columns**, including `dbo.auth_revoked_token`. It also scripts 35 identity properties, 11 defaults, 37 primary keys, 15 unique constraints, 36 check constraints, 69 foreign keys, 43 standalone indexes, and the 8 SQL modules associated with SQL Server diagram support. Foreign keys are added after tables and keys so table creation order does not depend on relationship cycles.

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
