# SQL Server setup and migration order

These files are incremental schema migrations, not a complete database bootstrap. The repository does not contain the original core-schema creation script. Obtain and apply the approved core schema separately before running these migrations; at minimum it must provide the JPA-mapped core tables, including `dbo.users`, `dbo.sport`, `dbo.coach`, `dbo.coach_sport`, `dbo.course`, `dbo.course_offering`, `dbo.[class]`, `dbo.facility`, `dbo.member_profile`, `dbo.membership`, and `dbo.membership_plan`, with their established columns and keys. Do not use Hibernate `create`/`update` as a substitute; the application is configured for schema validation.

For a fresh laptop database, use this order:

1. Apply the approved existing core-schema source (not present in this repository).
2. Run `phase0-class-sessions.sql` to create/validate `dbo.class_session`; its prerequisites are `dbo.[class]` and `dbo.facility`.
3. Run `phase1-member-sport-level-assessment.sql`; it requires `dbo.users`, `dbo.sport`, and `dbo.coach`.
4. Run `phase1_5-consultation-requests.sql`; it requires Phase 1's `dbo.skill_assessment` as well as `dbo.users` and `dbo.sport`.
5. Run `phase2-course-registrations.sql`; it requires `dbo.users` and `dbo.[class]`.
6. Run the read-only `phase2-personal-training-bookings-preflight.sql` and review column metadata, all CHECK constraints, foreign keys, and indexes. Then run `phase2-personal-training-bookings.sql`; it requires `dbo.users`, `dbo.coach`, `dbo.sport`, and `dbo.class_session`.
7. Run `phase3-entity-image-urls.sql`; it requires `dbo.sport`, `dbo.coach`, and `dbo.facility` and adds nullable `image_url NVARCHAR(500)` columns only when absent. It validates existing columns and does not alter incompatible ones.
8. Run `phase3a-coach-bio.sql` after the image migration and before starting with `ddl-auto=validate`; it adds nullable `dbo.coach.bio NVARCHAR(1000)` and validates existing metadata.
9. Start the application after all required schema scripts complete; `spring.jpa.hibernate.ddl-auto=validate` will then verify mappings.

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
