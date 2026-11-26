# Membership Redesign — Phase 2A Schema and Migration Design

**Status: READY FOR OWNER/ENGINEERING REVIEW — NOT APPROVED FOR EXECUTION.** This is a schema design and SQL draft only. No SQL in this phase has been executed. The latest live evidence is in [`membership-phase1-live-preflight.md`](membership-phase1-live-preflight.md).

**Target reviewed:** SQL Server `LAPTOP-416VBDLK`, default instance, `SportCenterDemo`, `dbo`, read on 2026-11-19. The live preflight used the owner-approved local TLS exception `encrypt=true;trustServerCertificate=true`; it encrypts but does not authenticate the server certificate. Do not reuse this exception outside local development/test.

**Purpose:** add purchase/period snapshots and a durable Renewal Intent/attempt relation without rewriting existing Membership, Payment, Upgrade, or refund history.

## 1. Phase 1 facts that constrain this design

- Live schema has 27 Memberships: 7 ACTIVE/effective, 11 CANCELLED, 9 UPGRADED; no ACTIVE future Queue remains.
- Nine future-start Memberships have CANCELLED status and linked REFUNDED Membership Payments. Memberships 2 and 3 are CANCELLED future rows without `purchase_payment_id`; their payment provenance is unknown.
- Current Plans all have one-month duration. Nine Membership intervals differ from current Plan duration, including Upgrade periods. Memberships 2002 and 3002 are active paid multi-month rows; 3002 has 163 days left at the read date. None is a data-fix target in this migration.
- All historical purchased benefits are not reconstructable from the live schema. No benefit snapshots or Renewal Intent table exist.
- No Membership/Upgrade Payments are PENDING or REFUND_REQUIRED at preflight. This is a point-in-time result, not a lasting deployment assumption.
- Current FK direction is `membership.purchase_payment_id → payment.id`; `payment.reference_id` is not an FK. `membership_upgrade.payment_id` is unique. There is no unique Membership-successor-per-source or Renewal retry relation.

No row is eligible for automatic legacy backfill based on current Plan values.

## 2. Owner-approved Renewal contract

Approved direction:

1. `autoRenew` is opt-in to the scheduled Renewal request; it never authorizes automatic collection.
2. Scheduler creates a durable `membership_renewal_intent` three days before the source Membership's exclusive `endDate` for eligible opted-in Memberships.
3. One Intent can have multiple distinct Payment attempt rows. Every attempt references the same Intent and uses its immutable quote/benefit snapshot.
4. Payment SUCCESS must pass deadline/provider validation before creating the next Membership. The successor starts at source `endDate` and stores its own entitlement snapshot.
5. The already-paid future-start Renewal successor is the only new future Queue case. Ordinary Purchase and Upgrade do not create a Queue.

The current model has no separate `QUEUED` Membership status; historical future periods use the existing status field plus future dates. The schema proposal does not add a new Membership status. Renewal provenance is explicit through `renewal_intent_id`; effective-entitlement queries must continue checking the exclusive date interval. Confirm this representation when the service contract is implemented.

The exact Renewal deadline is still proposed: `valid_from = source.endDate - 3 calendar days at 00:00 Asia/Ho_Chi_Minh`; `valid_until = source.endDate at 00:00 Asia/Ho_Chi_Minh`, exclusive. Proposed attempt deadlines are `min(createdAt + 15 minutes, valid_until)` for ONLINE and `valid_until` for COUNTER. Failed/expired attempts may be retried while the intent is open. Provider verified paid time, not callback arrival, determines timeliness. Payment after `valid_until` cannot create entitlement.

### Intent and attempt state proposal

- Intent states: `OPEN`, `SETTLED`, `EXPIRED`, `CANCELLED`.
- Payment continues using existing generic statuses. A failed/expired attempt can remain `CANCELLED` and records a Renewal-only close reason; a late/duplicate collected payment uses the existing `REFUND_REQUIRED` workflow. No new generic `EXPIRED` Payment status is proposed.
- When a timely provider callback arrives after the intent deadline, the Intent may already be `EXPIRED`; settlement may transition it to `SETTLED` only if the provider paid time is before both applicable deadlines, no other attempt won, and no unresolved cancellation rule prohibits it.
- `renewal_settlement_accepted_at` on the winning Payment is a permanent marker. Do not clear it if the Payment is later refunded. This lets a filtered unique index prevent a second winner even after a status changes from SUCCESS to REFUNDED.
- A Member-cancelled attempt with a provider-paid timestamp and a callback after cancellation, provider timestamp trust source, duplicate late collection after another attempt wins, missed scheduler window, and exact inclusive/exclusive boundary behavior need owner confirmation.

## 3. Proposed schema changes

All new columns on existing tables are nullable first. Existing rows remain untouched and their NULL snapshot status is interpreted as legacy `UNKNOWN`. No current Plan value is copied into a historical snapshot.

### `dbo.membership_renewal_intent` — new table

| Column | Proposed type | Purpose |
|---|---|---|
| `id` | `int IDENTITY` PK | Stable Renewal Intent identity. |
| `source_membership_id` | `int NOT NULL` FK | Period being renewed; unique for the approved one-intent-per-source-period flow. |
| `target_plan_id` | `int NOT NULL` FK | Plan reference/provenance; its mutable values are never the settlement source. |
| `creation_source` | `varchar(24) NOT NULL` | Audit whether scheduler created it; allowed source values need owner/API review. |
| `auto_renew_opt_in_snapshot` | `bit NOT NULL` | Records that the source was opted in when the scheduler created the Intent; it is not payment authorization. |
| `intent_status` | `varchar(20) NOT NULL` | `OPEN`, `SETTLED`, `EXPIRED`, `CANCELLED`. |
| `created_at`, `valid_from`, `valid_until`, `quote_captured_at` | `datetime2(6) NOT NULL` | Intent lifecycle and immutable quote times; document these as Vietnam-local wall-clock values consistently with current backend `LocalDateTime`. |
| `source_end_date_snapshot`, `successor_start_date`, `successor_end_date` | `date NOT NULL` | Freeze source exclusive end and next period; successor start equals source end. Successor end is exclusive. |
| `plan_name_snapshot` | `nvarchar(100) NOT NULL` | Historical offer identity. |
| `plan_description_snapshot` | `nvarchar(max) NULL` | Optional; whether description is contractual remains open. |
| `plan_price_snapshot`, `quoted_amount` | `decimal(12,2) NOT NULL` | Captured list price and amount owed; never take amount from client. They may be equal for Renewal, but both are explicit for audit. |
| `duration_months_snapshot` | `int NOT NULL` | Term used to calculate successor end date. |
| `course_discount_percent_snapshot`, `pt_discount_percent_snapshot` | `decimal(5,2) NOT NULL` | Captured discounts. |
| `allow_pt_plan_snapshot`, `allow_one_on_one_snapshot` | `bit NOT NULL` | Captured entitlements. |
| `settled_payment_id` | `int NULL` FK (composite) | Winning Payment attempt. Set once when settlement succeeds. |
| `settled_at`, `closed_at` | `datetime2(6) NULL` | Settlement/terminal time. |
| `closed_reason` | `varchar(32) NULL` | Structured reason; do not parse notes. |

Constraints/indexes:

- PK `id`; FK source Membership and target Plan, NO ACTION on delete/update.
- Unique `source_membership_id` (or an explicitly approved source-period/generation key if repeat intents per source are later allowed).
- CHECK intent states; `valid_until > valid_from`; `successor_end_date > successor_start_date`; positive duration; nonnegative price/quote; discounts 0–100.
- CHECK settled state has `settled_payment_id` and `settled_at`; non-settled state has no settled payment/time. CHECK successor start equals captured source end. Late valid callback may transition EXPIRED to SETTLED under the approved provider-time rule.
- Composite unique/FK pairings tie `settled_payment_id` to a Payment whose `renewal_intent_id` is this Intent.

### `dbo.membership` — period benefit and value snapshot

Add nullable columns:

- `benefit_snapshot_status varchar(16)`: `COMPLETE` or `UNKNOWN`.
- `benefit_snapshot_source varchar(24)`: `PURCHASE`, `RENEWAL`, `UPGRADE`, `ADMIN_ASSIGNMENT`, `LEGACY_VERIFIED`, or `LEGACY_UNKNOWN`.
- `plan_name_snapshot nvarchar(100)`, `plan_description_snapshot nvarchar(max) NULL`, `plan_price_snapshot decimal(12,2)`, `plan_duration_months_snapshot int`.
- `course_discount_percent_snapshot decimal(5,2)`, `pt_discount_percent_snapshot decimal(5,2)`, `allow_pt_plan_snapshot bit`, `allow_one_on_one_snapshot bit`.
- `paid_value_basis_status varchar(20)`: `VERIFIED`, `UNKNOWN`, `NO_PAID_VALUE`.
- `paid_value_basis_amount decimal(12,2) NULL`; `paid_value_basis_source varchar(24) NULL` for auditable derivation category.
- `renewal_intent_id int NULL` FK to the creating Intent for a paid successor period.

Constraints/indexes:

- Snapshot CHECK: NULL/UNKNOWN must not contain a partial or fabricated snapshot; COMPLETE requires the name, price, positive term, discounts and entitlement flags. Description may remain NULL if owner decides it is not contractual.
- Paid-value CHECK: VERIFIED requires a positive amount and source; UNKNOWN/NO_PAID_VALUE have no amount. Legacy NULL remains valid and maps to UNKNOWN in application logic.
- `renewal_intent_id` has a filtered unique index (`IS NOT NULL`) so one Intent creates at most one successor Membership.
- CHECK renewal successor has `purchase_payment_id IS NOT NULL`; composite FK pairs `(renewal_intent_id, purchase_payment_id)` with `(intent.id, intent.settled_payment_id)` so the successor uses the winning attempt.
- Existing `plan_id`, dates, status, `auto_renew`, payment link and cancellation fields remain unchanged. Existing rows are not updated.

Each successful Membership period stores its own snapshot. Ordinary purchase snapshot comes from its captured Payment quote; Renewal snapshot comes from RenewalIntent; Upgrade snapshot comes from the frozen target quote on MembershipUpgrade; Admin assignment snapshot comes from the active Plan at assignment and must remain no-Payment (exact assignment audit is separate).

### `dbo.payment` — ordinary Membership quote and Renewal attempt relation

Add nullable columns:

- `membership_plan_name_snapshot nvarchar(100)`, `membership_plan_description_snapshot nvarchar(max) NULL`, `membership_plan_price_snapshot decimal(12,2)`, `membership_duration_months_snapshot int`, `membership_course_discount_percent_snapshot decimal(5,2)`, `membership_pt_discount_percent_snapshot decimal(5,2)`, `membership_allow_pt_plan_snapshot bit`, `membership_allow_one_on_one_snapshot bit`, `membership_quote_captured_at datetime2(6)`.
- `renewal_intent_id int NULL` FK.
- `attempt_valid_until datetime2(6) NULL` (Renewal attempt only; do not alter Course/PT/ordinary Purchase deadline rules).
- `renewal_attempt_close_reason varchar(32) NULL`.
- `renewal_settlement_accepted_at datetime2(6) NULL`, permanent marker for the one accepted attempt.

Constraints/indexes:

- Existing `transaction_code` uniqueness is preserved.
- CHECK Renewal FK implies `purpose='MEMBERSHIP'` and an attempt deadline exists; close reason/accepted timestamp are Renewal-only.
- Filtered unique index on `renewal_intent_id` where `renewal_settlement_accepted_at IS NOT NULL` prevents a second accepted attempt even if the winning Payment is later refunded.
- Composite unique `(id, renewal_intent_id)` supports the Intent-to-winning-Payment FK.
- Ordinary MEMBERSHIP Payment quote fields are all-null for existing legacy rows or a complete captured set for new purchase intents. Renewal attempts read their quote from RenewalIntent; Payment.amount is the server-created amount for that attempt. Existing `amount`, `status`, `paid_at`, refund fields, references and notes remain unchanged.

### `dbo.membership_upgrade` — quote and credit evidence per attempt

Add nullable columns, preserving the existing unique `payment_id` (one Upgrade quote per Payment attempt):

- `source_period_start_date`, `source_period_end_date`, `credit_valuation_date` (`date`).
- `source_total_days`, `source_unused_days` (`int`).
- `source_paid_value_basis_amount`, `target_plan_price_snapshot`, `applied_credit_amount` (`decimal(12,2)`). Payment.amount remains the actual amount charged; no duplicate payable amount is authoritative.
- `target_plan_name_snapshot nvarchar(100)`, `target_plan_description_snapshot nvarchar(max) NULL`, `target_duration_months_snapshot int`, target course/PT discounts (`decimal(5,2)`), target entitlement flags (`bit`).

Constraints/indexes:

- Evidence CHECKs when present: end > start; total days > 0; unused days between 0 and total; values nonnegative; applied credit cannot exceed the evidenced source basis or captured target price. Do not encode a final rounding formula or `credit >= target price` behavior before owner approval.
- Filtered unique index on `source_membership_id` where `result_membership_id IS NOT NULL` protects one successful source replacement. Preflight reports any duplicates for owner disposition; the migration guard aborts if existing data violates this.
- Quote fields all NULL for historical attempts or complete for new Upgrade attempts; no legacy backfill.

## 4. Legacy UNKNOWN compatibility

Migration adds nullable columns and performs **no UPDATE**. Existing 27 Membership rows remain byte-for-byte unchanged in status, dates, Plan FK, `purchase_payment_id`, auto-renew, cancellation fields and linked Payment data. NULL `benefit_snapshot_status`/`paid_value_basis_status` means `UNKNOWN`, not “use today's Plan as historical truth.”

To avoid denying existing rights or breaking existing pages in the first rollout, a compatibility proposal is to leave current Plan-based behavior in place only for legacy UNKNOWN rows while all new COMPLETE rows read their snapshot. This fallback does not establish historical benefits and should be temporary/observable. The owner must approve the Member-facing UNKNOWN presentation and the later point at which UNKNOWN legacy rows can stop using live Plan fallback. No synthetic values or guessed benefit snapshots are written.

The following live rows require owner review and remain unchanged: Memberships 2 and 3 (future dates, cancelled, no Payment link); 2002 and 3002 (ACTIVE, paid multi-month periods, `autoRenew=true`); Upgrade-derived mismatches 1002, 1003, 4002, 4004, 5005; the nine future Memberships with REFUNDED Payment (verify external provider settlement before any refund conclusion). These have per-row detail in Phase 1 report.

## 5. JPA/service impact map (not implemented)

- `Membership`: snapshot/value fields, RenewalIntent association, nullable legacy mapping. `MembershipService` must copy captured quote data at successful settlement and never re-read Plan for new periods.
- New `MembershipRenewalIntent` entity/repository; unique source period and transactional state transitions. `MembershipAutoRenewalService`/scheduler create OPEN intent only for opted-in effective Membership and notify; no auto-charge.
- `Payment`: many-to-one nullable RenewalIntent relation, attempt deadline/close reason and permanent settlement marker. `PaymentService` creates multiple attempts from frozen Intent terms, uses provider paid time, and locks Member/Intent/attempt in consistent order.
- `MembershipUpgrade`: frozen source basis/period/credit and target quote fields per attempt. `MembershipUpgradeService` calculates server-side; policy decisions remain gates. On timely SUCCESS, atomically replace source and persist full target period/snapshot.
- Benefit readers `CourseRegistrationService`, `PersonalTrainingBookingService`, `IndividualTrainingPlanService`, membership responses and Member/Receptionist UIs use COMPLETE snapshots; preserve legacy UNKNOWN fallback pending owner-approved cutover.
- Database constraints are defense in depth; service transactions, callback idempotence and scheduler locks remain required. JPA `ddl-auto=validate` must validate the manually migrated SQL Server schema.

## 6. Migration order, verification and recovery

### Settlement write order for the FK cycle

The three relationships are intentionally nullable until settlement: Payment points to its Intent; the Intent points to its winning Payment; and the successor Membership points to the settled Intent and Payment. SQL Server does not defer FK checks, so settlement must occur in one transaction in this order: create the OPEN Intent; create the Payment attempt referencing it; after provider/payment validation, mark the winning Payment successful and set its permanent accepted marker; update the Intent to SETTLED with that Payment; then insert the successor Membership using that Intent and Payment. The transaction must roll back all steps if any write fails. Do not try to insert a SETTLED Intent or successor before the Payment exists.

The FKs prove that the winning Payment belongs to the Intent and that the successor references the Intent's selected Payment. They cannot prove that the selected Payment has an accepted marker or an allowed settled status. The service transaction must enforce that invariant, and postflight must report any mismatch explicitly.

1. **Review/gate:** resolve owner decisions in section 7; reconfirm exact target identity and repeat the Phase 1 preflight immediately before execution. Save result output without PII/secrets.
2. **Backup:** take a full SQL Server backup through the DBA process; validate backup checksum/metadata and restore to an isolated database. Do not run this task's scripts for rehearsal automatically.
3. **Rehearsal:** run the reviewed draft against the restored clone; verify SQL Server version, constraints, filtered indexes, transaction rollback, lock behavior and Hibernate schema validation. Capture before/after row manifests.
4. **Production deployment window:** stop application writers; take/verify the approved backup; run preflight; run the additive migration as one standalone batch. It creates schema only, with no data updates/backfill.
5. **Postflight:** run verification SQL; compare Membership, Payment, Upgrade and CancellationRequest counts/values to the saved preflight. All old data fields remain unchanged, all snapshot statuses remain NULL for legacy, and new table is empty. Start the app only after Phase 2 code/deployment is compatible and `ddl-auto=validate` passes.
6. **Recovery:** before code writes snapshots, a reviewed transactional reverse-DDL may remove empty new objects only after proving no rows/references exist. After any new snapshot/intent/attempt is written, do not drop columns/tables: use a tested full backup restore or a separately reviewed forward-compatible recovery. Never roll back by deleting customer/payment/history data.

## 7. Decisions still required before migration approval

1. Confirm Renewal deadline boundaries/time-zone conversion, scheduler downtime/missed window, and attempt retry/callback exact-boundary semantics. Three-day deadline is proposed, not approved.
2. Define provider paid-time trust source and COUNTER receipt-time evidence. Decide handling of a timely payment callback after Member cancellation, a duplicate collection after another attempt wins, and refund review responsibility.
3. Confirm whether a new Renewal Intent can be manually requested when `autoRenew=false`; confirm source Plan deactivation before Intent and Plan changes after Intent.
4. Decide Upgrade credit rounding unit, target-price-or-greater credit behavior, legacy UNKNOWN/Admin-grant eligibility, minimum remaining days, and target effective date for delayed callback.
5. Define the authoritative paid-value basis carried into an Upgrade result and its audit chain. Do not derive legacy basis from current Plan price.
6. Decide whether Plan description is contractual and belongs in the snapshot.
7. Approve the temporary Plan fallback/UI treatment for UNKNOWN legacy Memberships, including when fallback can be retired.
8. Confirm handling for Memberships 2/3 with no Payment FK and reconcile the nine recorded refunds against provider/bank records.
9. Confirm whether old cancelled/pending attempt retention needs a policy. No cleanup is included here.

## 8. SQL draft inventory and execution status

- [`membership-redesign-phase2a-preflight.sql`](../backend/docs/sql/proposals/membership-redesign-phase2a-preflight.sql) — read-only identity/schema/data guards and baseline counts.
- [`membership-redesign-phase2a-migration.sql`](../backend/docs/sql/proposals/membership-redesign-phase2a-migration.sql) — additive DDL draft inside a transaction; **not executed**.
- [`membership-redesign-phase2a-verify.sql`](../backend/docs/sql/proposals/membership-redesign-phase2a-verify.sql) — read-only postflight checks.
- [`membership-redesign-phase2a-clone-preflight.sql`](../backend/docs/sql/proposals/membership-redesign-phase2a-clone-preflight.sql) — clone-only identity guard and per-record SHA-256 baseline manifest. It writes a sidecar manifest table only when separately approved and run on the clone.
- [`membership-redesign-phase2a-clone-migration.sql`](../backend/docs/sql/proposals/membership-redesign-phase2a-clone-migration.sql) — separate copy with placeholder clone identity values; always rejects `SportCenterDemo`.
- [`membership-redesign-phase2a-clone-verify.sql`](../backend/docs/sql/proposals/membership-redesign-phase2a-clone-verify.sql) — clone-only verification with per-record comparison against the sidecar manifest.
- [`membership-redesign-phase2a-clone-rehearsal-checklist.md`](../backend/docs/sql/proposals/membership-redesign-phase2a-clone-rehearsal-checklist.md) — authorization gates, transaction/FK/check tests, compatibility, backup/restore and recovery checklist.

## 9. Phase 2B static review notes

- Static review found and corrected a SQL Server three-valued CHECK issue: nullable snapshot status and provenance columns used with `IN`/`=` now have explicit `IS NOT NULL` predicates in the complete/verified/unknown branches. Without these guards SQL Server can accept `UNKNOWN` CHECK results and permit a partial snapshot/provenance record.
- The SQL scripts have not been parsed or executed by SQL Server. Clone rehearsal must still validate batch syntax, physical names/types, Hibernate validation, and locking/rollback behavior.
- The settlement FK cycle is insertable using the write order above, but cross-table payment-status/accepted-marker consistency is an application transaction invariant and needs a postflight assertion.
- No Phase 1 live rows violate the new-column checks because all added columns start NULL; the pre-existing Upgrade-success guard and filtered unique index still require clone verification against the owner-reviewed manifest.

### SQL Server clone rehearsal test cases

1. Run the read-only preflight on the isolated clone and confirm identity, baseline counts, existing upgrade source/result rows, and all orphan/inconsistency result sets are understood before DDL.
2. Run the draft as one transaction; assert the new table is empty, all legacy snapshot columns remain NULL, original Membership/Payment/Upgrade manifests are unchanged, and all new constraints are enabled/trusted with the expected keys and filters.
3. Exercise both permitted settlement order and rollback: OPEN Intent → multiple unaccepted Payment attempts → winner status/accepted marker → SETTLED Intent → one successor; then force an error before commit and verify every row rolls back.
4. Assert a second accepted Payment for one Intent fails; a second successor fails; a Payment from another Intent cannot become the winner; a successor cannot reference a non-winning Payment; and a second successful Upgrade result for one source fails.
5. Attempt partial/invalid snapshots, especially NULL benefit status/source, NULL paid-value status/source, unknown enum values, out-of-range discounts, negative amounts/credit, and impossible date ranges; each must fail. Confirm all-NULL legacy rows still pass.
6. Validate ONLINE/COUNTER deadline precision and Vietnam-time conversion at just-before/equal/after boundaries, plus decimal(12,2) limits and JPA/Hibernate `ddl-auto=validate` against the exact SQL Server version.
7. Measure schema-lock duration with representative clone row counts and a controlled old-application session; verify old code's all-NULL inserts remain compatible, writers are stopped for final deployment, and a failed DDL transaction restores the original schema.

**Phase 2B review status: READY FOR A SEPARATE, OWNER-APPROVED SQL Server clone rehearsal.** This static review did not run or parse the scripts with SQL Server. Clone rehearsal requires an isolated disposable clone, captured preflight manifest, and approval for that rehearsal. It is not approval to execute against the live database or production.

**Review status: READY for schema/migration review. Migration approval/execution status: NOT APPROVED.** No source changes, DB writes, migrations, backups, restores, tests, commits or pushes were performed.
