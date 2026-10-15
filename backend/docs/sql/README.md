# SQL Server setup and migration order

These files are incremental schema migrations, not a complete database bootstrap. The repository does not contain the original core-schema creation script. Obtain and apply the approved core schema separately before running these migrations; at minimum it must provide the JPA-mapped core tables, including `dbo.users`, `dbo.sport`, `dbo.coach`, `dbo.coach_sport`, `dbo.course`, `dbo.course_offering`, `dbo.[class]`, `dbo.facility`, `dbo.member_profile`, `dbo.membership`, and `dbo.membership_plan`, with their established columns and keys. Do not use Hibernate `create`/`update` as a substitute; the application is configured for schema validation.

For a fresh laptop database, use this order:

1. Apply the approved existing core-schema source (not present in this repository).
2. Run `phase0-class-sessions.sql` to create/validate `dbo.class_session`; its prerequisites are `dbo.[class]` and `dbo.facility`.
3. Run `phase1-member-sport-level-assessment.sql`; it requires `dbo.users`, `dbo.sport`, and `dbo.coach`.
4. Run `phase1_5-consultation-requests.sql`; it requires Phase 1's `dbo.skill_assessment` as well as `dbo.users` and `dbo.sport`.
5. Run `phase2-course-registrations.sql`; it requires `dbo.users` and `dbo.[class]`.
6. Start the application after all required schema scripts complete; `spring.jpa.hibernate.ddl-auto=validate` will then verify mappings.

Run each SQL file as its own standalone batch while writers are stopped, after a backup, in `SportCenterDB` (or review the database-name guard for an explicitly renamed deployment). None of these scripts was executed as part of the repository change. A matching ClassSession table with incompatible metadata fails closed; the script never drops, rebuilds, or silently accepts it.

The three pre-existing migrations create `member_sport_level`, `skill_assessment`, `consultation_request`, and `course_registration`. They do not create the original core tables listed above. Therefore this repository alone still cannot provision a truly empty SportCenter database; the approved core schema source must be recovered and included or supplied by the deployment owner.

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
