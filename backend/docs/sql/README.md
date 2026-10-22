# SQL Server setup and migration order

These files are incremental schema migrations, not a complete database bootstrap. The repository does not contain the original core-schema creation script. Obtain and apply the approved core schema separately before running these migrations; at minimum it must provide the JPA-mapped core tables, including `dbo.users`, `dbo.sport`, `dbo.coach`, `dbo.coach_sport`, `dbo.course`, `dbo.course_offering`, `dbo.[class]`, `dbo.facility`, `dbo.member_profile`, `dbo.membership`, and `dbo.membership_plan`, with their established columns and keys. Do not use Hibernate `create`/`update` as a substitute; the application is configured for schema validation.

The historical Unicode diagnostic and preflight scripts are not present in the current working tree (they were deleted in commit `3dcf144`), although they remain recoverable from earlier Git history. Do not follow the Unicode instructions below until those exact reviewed scripts are restored or otherwise supplied and reviewed. The optional Unicode conversion is not a prerequisite for the Phase 8.7B/8.7C migrations or current course-payment integration path.

## Phase 3B.2 Vietnamese Unicode conversion

The reviewed migration is `phase3b2-vietnamese-unicode.sql`. A successful read-only preflight on the target `SportCenterDB` confirmed the 14 columns and the sole candidate index on `dbo.sport.name`: a `UQ` unique, nonclustered, constraint-backed key on that column alone. The migration rechecks the constraint shape, enabled state, persisted index options, storage filegroup and dependencies. It reads the actual constraint name from SQL Server metadata and never assumes it. Before altering `sport.name`, it checks for Unicode uniqueness collisions; within the same transaction it drops only that verified constraint, converts the column, and recreates the same constraint name and supported options. Any DDL or verification failure rolls the transaction back, including restoring the original column and constraint. It preserves `Chinese_PRC_CI_AS`, column lengths and nullability, and stops on any unexpected schema/dependency or unsupported index metadata.

If separately undertaking the optional Unicode conversion, first recover and review the historical diagnostic and preflight scripts from Git history. Only after that review, apply the approved core schema and required feature migrations through `phase3a-coach-bio.sql` (see the fresh-database order below), take and verify a full database backup, run the recovered preflight immediately before migration, and retain all result sets. Inspect trigger and SQL module definitions for dynamic or uncatalogued references. Restore the backup to a separate test database and rehearse this exact migration there. After stopping application writers, run `phase3b2-vietnamese-unicode.sql` as one standalone SSMS batch selected against `SportCenterDB`. It has a database guard, transaction, error rollback, application lock, exact metadata/dependency checks, and rerun handling for original or already-converted columns. The unique constraint must be named dynamically from metadata and recreated under that same name; do not substitute a hardcoded name. The script preserves the reviewed index options and filegroup, rejecting partitioned/compressed indexes and newer unsupported options. Large `TEXT` fields convert directly to `NVARCHAR(MAX)`. Do not execute the migration from application startup.

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
11. Optional only: the Unicode conversion is not needed for Phase 8.7B/8.7C. Its diagnostic and preflight scripts are absent from the current tree; recover and review them from Git history before considering the existing `phase3b2-vietnamese-unicode.sql` migration. Do not run that migration without its reviewed preflight.
12. Run `phase6-1a-notifications.sql` after the core schema (it requires `dbo.users` with an `INT` primary key). It creates or validates `dbo.notification`, adds/validates its owner FK and defaults, and adds the user-created and filtered unread indexes. It is transactional and idempotent, rejects incompatible existing notification structures, and must be run manually; it has not been executed.
13. Run `phase7-1-audit-log.sql` after the core user table exists and before starting a build containing AuditLog (`ddl-auto=validate`). It creates or validates only `dbo.audit_log`, with a nullable non-cascading actor FK to `dbo.users`, and adds newest-first, actor, and entity indexes. It is transactional and rerunnable, preserves all existing data, and has not been executed.
14. Run `phase8-7b-facility-sports.sql` after the approved core schema and `phase0-class-sessions.sql`, but before deploying the application version that maps the join table. It requires `dbo.facility.sport_id INT NOT NULL` with exactly one trusted, enabled, single-column NO ACTION FK to `dbo.sport(id)`. On first creation it copies exactly each Facility's existing assignment; it does not add other Sports. See the Phase 8.7B section below.
15. After `phase2-course-registrations.sql` and `phase2-payment.sql`, and before deploying CourseRegistration hold mappings, back up the database, stop writers, rehearse on a restored copy, then review/run `phase8-7c-course-registration-holds.sql`. On first install it requires `dbo.payment` and stops if any `COURSE_REGISTRATION` payment is still `PENDING`; operators must inspect and explicitly reconcile those legacy transactions. It preserves historical statuses and price data, adds nullable `payment_deadline` (legacy rows remain NULL), validates status constraints, retains the confirmed-only filtered unique index, and adds a pending-only filtered unique index plus expiration/capacity indexes. A rerun skips the legacy-payment blocker only when the deadline column, trusted expected status CHECK, and all four named indexes already exist and validate. A partial schema fails closed for manual inspection. The migration never changes payment status. It does not execute automatically. Phase 8.7B is independent; when both changes are deployed together, apply both migrations before the application restart.
16. Run `phase9-1-support-tickets.sql` after the core `dbo.users` table is present and before deploying an application build containing SupportTicket entities. It additively creates `dbo.support_ticket` and `dbo.support_ticket_message`, their user/ticket FKs, status/category/content checks, and paging/thread indexes. It is transactional and rerunnable only when both tables have the expected columns and named trusted constraints; a partial or incompatible structure fails closed. It does not create or modify ticket data. Back up and apply it manually; never run it from application startup.
17. Apply `phase9-2a-membership-purchase-refactor.sql` after the existing Membership/Payment schema and before deploying the matching application build. First inspect all legacy MembershipPlan rows. The migration copies legacy `price` only when legacy `durationMonths = 1`; for each other duration, provide a reviewed monthly price in its temporary `#PlanMonthlyPriceOverrides` values block. Never derive monthly rates by dividing legacy bundle prices. Back up and rehearse with writers stopped; the script is additive and transactional, keeps legacy `price`/`durationMonths`, and adds monthly price, payment/membership snapshots, and direct plan-upgrade relationships. It preserves old memberships/payments and supports old pending membership payments without inventing their purchased duration. New payment requests are fixed to one month by the later Phase 9.2A.1-A application change; it does not alter historical terms. Start with `ddl-auto=validate` only after the script completes successfully. Do not execute it automatically from application startup.
18. Apply `phase9-2a1-membership-renewal-foundation.sql` after Phase 9.2A and before starting a backend build containing `Payment.renewalMembership`. It requires `dbo.payment` and `dbo.membership`, adds nullable `dbo.payment.renewal_membership_id`, a trusted FK to `dbo.membership(id)`, and a filtered unique index allowing one renewal-payment attempt (regardless of payment status) per source membership period. It does not backfill or modify payment rows and creates no renewal requests. Back up, stop writers, and run the standalone transactional script manually; it validates existing metadata and fails closed on incompatible objects.
19. Apply `phase9-2a1-membership-renewal-scheduler.sql` after the renewal-foundation migration and before deploying Stage B. It makes `dbo.payment.method` nullable and replaces `ck_payment_method` so NULL is accepted only for MEMBERSHIP payments linked to a renewal Membership, in PENDING or CANCELLED status, and carrying the exact one-month price snapshot. Stage B creates requests only as PENDING; CANCELLED/NULL is retained only as a terminal result of opt-out or expiry. SUCCESS and REFUND_REQUIRED require a non-NULL ONLINE/COUNTER method. Existing values are not rewritten. The script validates known prior/current constraint definitions and relevant schema, and fails closed for unexpected definitions. Back up and stop writers; run the standalone transactional script manually. Stage B does not issue VNPay URLs or permit COUNTER settlement; those are deferred to Stage C.
20. For Phase 9.2A.1-C Checkpoint 1, apply `phase9-2a1-c-renewal-checkout-attempts.sql` after migrations 18–19 and before launching the Checkpoint 1 application build. It adds nullable `dbo.payment.renewal_auto_renew_choice BIT` and a trusted CHECK allowing a value only on renewal-linked MEMBERSHIP payments, plus `dbo.payment_checkout_attempt` with a trusted Payment FK, five trusted CHECKs, a unique provider transaction reference, and a payment/creation lookup index. Attempt references must use the `RNL` namespace, disjoint from the current `TXN-` ordinary-payment references. Existing rows remain unchanged and no attempt is backfilled. Back up and stop writers; execute the standalone script in `SportCenterDB`, then verify the listed metadata and run Hibernate `ddl-auto=validate`. Rollback after application use requires an explicit plan for attempt evidence; do not drop attempt records or restore an older schema while renewals may have been processed. This checkpoint does not enable renewal checkout or settlement.
21. Phase 9.2A.1-C Checkpoint 1.1 adds nullable `dbo.payment_checkout_attempt.applied_membership_id`, a trusted FK to `dbo.Membership(id)`, and two filtered unique indexes: `ux_payment_checkout_attempt_applied_membership` permits at most one winning attempt per created Membership, while `ux_payment_checkout_attempt_applied_payment` permits at most one winning attempt per parent Payment. Only an attempt that atomically applied a Membership receives this link; losing/additional captures keep it NULL and retain their own reconciliation status. No existing attempt is backfilled. After a verified backup and writer stop, apply `phase9-2a1-c1-applied-membership-link.sql` after Checkpoint 1 and before deploying code that writes the association. Verify the nullable INT column, exact trusted FK, both unique filtered indexes and predicates, then start with `spring.jpa.hibernate.ddl-auto=validate`. H2 tests do not prove SQL Server filtered-index behavior; validate this migration and the constraints on a disposable SQL Server `SportCenterDB` before owner deployment. Do not implement settlement until the owner confirms migration and schema validation succeeded. Rollback after use must preserve winning-link and capture reconciliation evidence; never drop the column or indexes as a routine rollback.
22. Start the application after all required schema scripts complete; `spring.jpa.hibernate.ddl-auto=validate` will then verify mappings.

For the 8.7C first-run preflight, list pending legacy course payments and inspect the referenced registrations before the migration. Do not treat `PENDING` as evidence that money was received, and do not update payment status to bypass the guard. Example read-only inventory:

```sql
SELECT p.id AS payment_id, p.member_id, p.reference_id AS registration_id,
       p.amount, p.method, p.status AS payment_status,
       r.status AS registration_status, r.payment_deadline
FROM dbo.payment p
LEFT JOIN dbo.course_registration r ON r.id = p.reference_id
WHERE p.purpose = 'COURSE_REGISTRATION' AND p.status = 'PENDING'
ORDER BY p.id;
```

For an 8.7C rehearsal or rerun, verify that `payment_deadline` is nullable `DATETIME2(6)`, the status CHECK is enabled/trusted and accepts `PENDING_PAYMENT`, `CONFIRMED`, `CANCELLED`, and `EXPIRED` while requiring a deadline for pending rows, `ux_course_registration_confirmed` and `ux_course_registration_pending` are enabled unique filtered indexes over `(member_id, class_id)`, and both expiration indexes have their documented ordered keys. The migration compares the named CHECK after removing whitespace/brackets and redundant parentheses around the entire expression; it preserves inner boolean grouping and accepts only the documented 8.7C expression (or known legacy expression on first install). Unknown or untrusted constraints are not replaced. Example read-only checks:

## Phase 9.3 — Membership Upgrade Payment drafts (C2.5A)

Run `phase9-3-membership-upgrade-payment-drafts.sql` manually after the Phase 9.2A Membership purchase migrations and Phase 9.2A.1 renewal migrations, and before deploying the C2.5A application version. Take and verify a restorable database backup, rehearse against a restored copy, stop application writers, and review the script's fail-closed checks. The script is not executed by the application and must not be run automatically.

This migration adds the `UPGRADE` payment-purpose value, a filtered unique index allowing at most one pending Upgrade Payment per member, and `membership_upgrade_payment_evidence` with restrictive foreign keys to the Payment, source Membership, and target plan. It does not insert/update Payment or Membership rows. Upgrade requests currently create non-payable drafts only: no gateway URL is requested, counter confirmation is rejected, and settlement is unavailable until C2.5B. `created_at_utc` and `expires_at_utc` are UTC instants stored in `DATETIME2(6)` by convention; the Vietnam business date is stored separately. Keep Hibernate `ddl-auto=validate` enabled.

After rehearsal, verify the payment purpose constraint is enabled/trusted and includes `UPGRADE`, the pending-upgrade index filters `purpose='UPGRADE' AND status='PENDING'`, and all evidence foreign keys/check constraints are enabled/trusted. Verify no preexisting rows were added by the migration. Application verification should confirm Hibernate startup/schema validation and create a test Upgrade draft in an isolated database, then verify its immutable snapshots, UTC expiry, and that no checkout URL is issued. The 15-minute expiry is capped at the next Asia/Ho_Chi_Minh midnight; drafts with less than a two-minute usable window are rejected. An expired unpaid draft is retained and marked CANCELLED when a later Upgrade or Renewal request next checks it; evidence is never deleted.

Recovery requires stopping the new application version and restoring the pre-migration backup if the schema must be reverted. Do not drop the evidence table, purpose constraint, or index while Upgrade draft Payments exist; first export/reconcile all such rows and obtain owner approval for a separately reviewed reverse migration. No source Membership, historical Payment, or provider evidence is rewritten by Phase 9.3.

## Phase 9.4 — Membership Upgrade settlement core (C2.5B.1)

Run `phase9-4-membership-upgrade-settlement.sql` manually after Phase 9.3 and before deploying the settlement mappings. Back up, rehearse on a restored copy, and stop writers. This additive script stores `REPLACED` Membership history without overwriting the original `start_date`/`end_date`, links the replacement Membership, adds a unique applied-Membership link to the immutable Upgrade draft evidence, and creates append-only verified provider capture evidence. It does not create or settle rows and is never run by application startup. Keep `ddl-auto=validate` enabled.

Only an object produced by the PaymentGateway's successful signature and merchant verification can be passed to the internal settlement service. The gateway extracts `vnp_PayDate` only from the signed callback; absent or malformed provider time is recorded as missing and requires reconciliation. A capture at or after the 15-minute/Vietnam-midnight draft expiry, amount or snapshot mismatch, stale source eligibility, or conflicting entitlement remains `REFUND_REQUIRED`; captured funds are never called unpaid or refunded automatically. A delayed callback uses the current Vietnam settlement date and is never backdated to provider capture time. If the source is no longer the unique current Membership on callback date, manual reconciliation is required.

The new `REPLACED` state has no existing Membership CHECK constraint in the reviewed migrations; Phase 9.4 adds a trusted constraint requiring both replacement audit fields for that state and forbidding those fields on other statuses. SQL Server DDL, filtered indexes, and lock behavior remain unverified until owner-approved rehearsal. Rollback after settlement use must preserve all applied links and capture evidence; do not drop the new objects as a routine rollback.

```sql
SELECT c.name, t.name AS type_name, c.max_length, c.scale, c.is_nullable
FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID(N'dbo.course_registration')
  AND c.name IN (N'status', N'registered_at', N'payment_deadline');

SELECT name, is_disabled, is_not_trusted, definition
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.course_registration');

SELECT name, is_unique, is_disabled, has_filter, filter_definition
FROM sys.indexes
WHERE object_id = OBJECT_ID(N'dbo.course_registration')
  AND name IN (N'ux_course_registration_confirmed', N'ux_course_registration_pending',
               N'ix_course_registration_expiry', N'ix_course_registration_class_deadline');
```

Rollback requires a verified backup/restore plan. The migration itself is transactional, so a failed run restores the prior schema. After a successful run, do not drop the new column/indexes or restore the old CHECK while any `PENDING_PAYMENT`/`EXPIRED` rows exist; application rollback first requires an explicit data and payment reconciliation plan. Do not convert legacy `CONFIRMED` rows into holds. Run the backend with `ddl-auto=validate` against the restored test database before production rollout. SQL Server locking/deadlock behavior must be tested on SQL Server; H2 verification does not prove it.

Run each SQL file as its own standalone batch while writers are stopped, after a verified backup, in `SportCenterDB`. The Phase 8.7B/C database-name guards remain in place; rehearse on a dedicated SQL Server instance containing a disposable database named exactly `SportCenterDB`, rather than weakening those guards to target an arbitrary database. None of these scripts was executed as part of the repository change. A matching ClassSession table with incompatible metadata fails closed; the script never drops, rebuilds, or silently accepts it.

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

## Phase 8.7B Facility-Sport compatibility

The application models `Facility.sports` through `dbo.facility_sport(facility_id, sport_id)`. Run `phase8-7b-facility-sports.sql` after the approved core schema (and after `phase0-class-sessions.sql` in the fresh-database sequence) but before deploying this application version. Stop writers, take a verified full backup, and rehearse against a restored copy first. The script checks the actual source column and FK metadata, creates the join table and its composite primary key, Sport lookup index and non-cascading FKs, and backfills only the existing one-Sport assignments on first creation. It never assigns additional Sports automatically.

The legacy required `facility.sport_id` column and its existing FK are deliberately retained because the original core DDL and dependency names are not in this repository. The application mirrors the first selected Sport there to keep the legacy schema valid. Do not remove that column or FK without a separate metadata review and migration. If the join table already exists, the script validates it and does not fill missing assignments; resolve any missing Facility assignment manually before rerunning. Rolling back after multi-Sport assignments have been made requires restoring the pre-migration backup or a reviewed data export because the legacy column cannot represent additional assignments.

After manual execution, verify every Facility has at least one join row and that the expected table, key, index and FK metadata are present, then start with `spring.jpa.hibernate.ddl-auto=validate`. A failed script rolls back its table creation and initial backfill. Do not run this SQL from the application.
### Phase 9.5 — Upgrade checkout integration

Apply `phase9-5-membership-upgrade-checkout-integration.sql` only after Phase 9.3 (Upgrade drafts) and Phase 9.4 (Upgrade settlement evidence), during a writer stop and after a verified backup. It adds UGP-specific checkout attempts, an optional attempt link on provider capture history, provider transaction ID uniqueness, and authenticated counter receipt records. The migration is manual and has not been run as part of this code change.
