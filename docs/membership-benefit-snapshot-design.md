# Membership Benefit Snapshot — Design and Implementation Plan

**Status:** Historical snapshot design, superseded in part by [`membership-final-redesign-implementation-plan.md`](membership-final-redesign-implementation-plan.md). The latest owner direction approves removal of ordinary Purchase Queue and the scheduled Renewal successor workflow. Renewal deadline details, Upgrade credit calculation details, legacy handling, and operational edge cases still require explicit confirmation. This document authorizes no implementation or data change.
**Evidence:** Current backend/frontend source and tests were inspected. `docs/post-defense-audit.md` contains a read-only live-schema snapshot dated 2026-11-19 for `SportCenterDemo`; the database was not queried again for this update. The checked-in SQL schema is a repository artifact, not proof of the current live schema.

**Scope update:** The current direction is no Queue from ordinary Purchase or Upgrade. A successful Renewal is the explicit exception: create its next Membership only after valid Payment SUCCESS, starting at the source Membership's exclusive `endDate`. The prior statement that Renewal Option B/C is undecided is superseded; the scheduled, no-auto-charge successor direction is approved. Do not delete, cancel, refund, or rewrite existing future-start Membership rows; reconcile them only after a fresh read-only preflight. Use the linked final redesign plan for current target behavior; the source audit and older recommendations below are historical evidence, not current policy.

## 1. Executive summary

`MembershipPlan` is mutable and existing Membership records refer to it directly. Entitlement consumers currently read discounts and access flags through that Plan relationship. Payment records retain amount and transaction fields, but a normal Membership Payment does not retain the full quoted Plan terms. A Plan edit can therefore change benefits for existing ACTIVE and queued memberships, while an edit between Payment creation and settlement can cause the charged amount and activated terms to disagree.

Recommended design: capture immutable terms for every valid Membership purchase intent, keyed to its Payment. On successful settlement, associate the exact terms period with the resulting Membership. This includes ordinary purchases, renewal intents, and Upgrade target terms. Existing queued records retain their own UNKNOWN/verified classification and must not be altered by the snapshot migration. An Admin assignment without a Payment gets a snapshot at assignment time and an audit record. Purchased Membership authorization, pricing, and history read the snapshot; active Plan data remains the source for offers and new intents.

Historical Membership terms without trustworthy evidence stay `UNKNOWN`. Current Plan values must not be presented as the original purchase terms. An owner-approved, explicitly unverified demo baseline is a separate future operation that requires a per-row preview.

## 2. Owner policy reconciliation

The listed policies are treated as **proposed and pending final approval**. Compatibility below describes current code, not a claim that a business rule is approved.

| Proposed policy | Current compatibility | Required implementation/design consequence |
|---|---|---|
| Snapshot price and all entitlements when a valid purchase intent is created | Partial: `PaymentService` stores the amount, while normal Membership Payment retains only Plan `referenceId`; Upgrade stores some quote metadata but not target benefits | Persist an immutable terms record in the same transaction as Payment/Upgrade details. Settlement consumes that record. |
| ONLINE deadline = earlier of creation + 15 minutes and Vietnam midnight | Partial: VNPay URL uses configured 15 minutes. Membership intent has no general persisted expiry. Upgrade has a 15-minute-or-midnight quote expiry; gateway URL expiry is separately generated | Persist a single business `valid_until` and pass the same effective deadline to the gateway; reject settlement only by verified provider paid time, not callback arrival time. Test exact-boundary semantics. |
| COUNTER deadline = Vietnam midnight | Incompatible: initial COUNTER Membership Payment has no general expiry; Upgrade currently expires at end of business day | Persist deadline at intent creation. Staff confirmation must be rejected if verified receipt/processing time is after the deadline. Define counter receipt timestamp source. |
| Late callback may settle if provider verifies paid time before deadline | Partially supported for auto-renew callback validation; normal Membership/Upgrade do not consistently enforce purchase deadline using provider time. Standard successful settlement currently records `paidAt=now()` rather than provider paid time; Upgrade `apply` also checks current time against quote expiry. | Parse/validate signed provider paid time, compare to the stored deadline, persist that verified time, and route payment after deadline to refund-required handling without activating benefits. Do not compare callback arrival time for eligibility. |
| Deactivated Plan blocks new intents, but existing valid intent uses snapshot | Creation paths check current Plan during quote/amount resolution; settlement re-fetches current Plan | Validate ACTIVE when intent is made. Settlement must not require Plan ACTIVE or read mutable benefits/duration. |
| Auto-renew is intent-only, never an automatic charge/activation | Compatible with current service shape: pending counter/online request is generated; user/staff payment is needed | Preserve explicit payment completion. Snapshot each renewal intent. Never activate merely because the source Membership reaches expiry. |
| Upgrade snapshots target at request; source is unchanged while pending; apply only on success, preserve existing remaining-time behavior | Partial: target Plan is referenced in `MembershipUpgrade`; source remains until apply; current result uses source `endDate`; target terms are mutable | Snapshot target terms alongside Upgrade Payment. Keep source benefits/status unchanged while pending; after eligible payment success mark source `UPGRADED`, create target Membership using target snapshot, and preserve current remaining-time/end-date calculation unless separately approved. |
| Admin assignment uses ACTIVE Plan, no fake SUCCESS Payment, with actor/time/reason audit | Partial: endpoint is ADMIN-only and creates no Payment, but source lookup does not enforce ACTIVE and Membership has no actor/reason fields | Enforce ACTIVE at assignment; add explicit assignment audit (existing `AuditLogService` is available) with actor, action, Membership/Plan, time, and required reason. Snapshot source = `ADMIN_ASSIGNMENT`. |
| Legacy without evidence = UNKNOWN; demo baseline only after preview/approval | Compatible with evidence constraints; no implementation currently exists | Keep unknown terms NULL/status-marked. Prepare a read-only per-record preview before any owner-approved baseline write. |

### Lifecycle vocabulary

- **Purchase intent:** user/system request to buy/renew/upgrade with a selected Plan and quote. It is not a Membership entitlement and does not create a payment success.
- **Payment:** transaction record with method, purpose, amount, state and deadline. It owns the snapshot while pending.
- **ACTIVE Membership:** under the new direction, a current effective Membership; a future-start ACTIVE row may still exist as legacy Queue data from the old workflow.
- **Legacy QUEUED Membership:** a paid future-start row already in the database. Do not create new ones; no automatic cleanup is approved.
- **Upgrade:** pending target quote linked to source Membership. Source remains unchanged while pending. A successful, timely payment creates the result Membership and applies the target snapshot; failed, cancelled, expired or late-paid Upgrade must not apply target benefits.

## 3. Current implementation and impact inventory

| Area and source | Current behavior observed | Snapshot work needed |
|---|---|---|
| Plan administration: `MembershipPlanController`, `MembershipPlanService`, `MembershipPlan`, request/response DTOs, `MembershipPlanRepository` | Edits update the same Plan row's name, description, price, duration, both discounts, and both entitlement flags. Deactivation is soft (`INACTIVE`). Audit entries note changes but do not preserve prior field values. | Keep Plan as current offer catalog. Reject new purchase/upgrade intents for inactive Plan; do not propagate edits/deactivation to snapshots. |
| Public offers: `PublicMembershipPlanController`, `MembershipPlanService.getActivePlans`; `MemberMembership.jsx`, `landingData.js`, `membershipPlanPresentation.js` | Active catalog is used to show plans for sale. | Continue using live Plan for offers, clearly separate it from purchased Membership terms. |
| Payment creation/settlement: `PaymentController`, `PaymentService`, `Payment`, `PaymentPurpose`, `PaymentResponse`, `PaymentGateway`, `VnpayPaymentGateway` | `Payment.amount` is stored at creation. Regular Membership settlement looks up Plan from `referenceId`, then uses current duration and Plan link. Standard callback parses provider paid time for Membership/Upgrade but does not apply a general Membership-intent deadline. Cancelled successful membership payment becomes refund-required. Auto-renew has a separate provider-time validation path. | Snapshot terms/deadline atomically with intent; settle from snapshot and provider paid time. Preserve current cancellation/refund safety; do not change unrelated payment purposes. |
| Membership create/list: `MembershipController`, `MembershipService`, `MembershipRepository`, `Membership`, `MembershipResponse` | Paid activation/renewal reads current Plan. Response uses current Plan name. Admin assignment is ADMIN-only and creates no Payment, but accepts a Plan lookup without an ACTIVE check and stores no assignment audit details on Membership. | Link snapshot on settlement; snapshot Plan terms at approved ADMIN assignment; expose immutable purchased terms in response. |
| Course Registration: `CourseRegistrationService`, `MembershipRepository`, registration DTO/entity/payment handler | Selects highest course discount by reading each effective Membership's Plan, then stores original price, discount and final price. Payment uses the stored final price. | Read discount from effective Membership snapshots. Never rewrite existing CourseRegistration price snapshots or Payments. |
| PT Booking: `PersonalTrainingBookingService`, `MembershipRepository`, booking entity/handler | Reads `allowOneOnOneCoaching` and PT discount through Plan; stores booking price. | Read eligibility/discount from Membership snapshot for new bookings. Keep existing booking prices unchanged. |
| Individual Training Plan: `TrainingController`, `IndividualTrainingPlanService`, `MembershipRepository.findEffectivePersonalTrainingPlanMemberships` | Repository query filters Memberships through `plan.allowPersonalTrainingPlan`. | Replace Plan predicate with snapshot entitlement; define behavior for `UNKNOWN` before enforcement. |
| Queue and renewal: `MembershipAutoRenewalService`, `MembershipService`, `MembershipScheduler`, `MembershipRepository` | Ordinary purchase can queue a future `startDate` period; auto-renew generates a pending request, not automatic charging. Renewal price/settlement read current Plan. | Do not create new purchase Queue rows. Reconcile existing Queue read-only. Renewal B/C remains undecided; Option C needs per-paid-period snapshots if one Membership row is extended. |
| Upgrade: `MembershipController`, `MembershipUpgradeService`, `MembershipUpgrade`, repository and response DTOs | Quote compares source and target current Plans, calculates prorated amount from their current price/duration, stores source/target IDs, remaining days and expiry. On success, source becomes `UPGRADED`; result Membership uses target Plan, `startDate=today`, `endDate=source.endDate`, and inherited auto-renew. | Capture target terms at intent; preserve source snapshot while pending and source's current remaining-time behavior. Whether source comparison/proration moves to source snapshot requires explicit confirmation. |
| Cancellation/refund/history: `MembershipCancellationRequestService`, `MembershipService`, `PaymentService`, `PaymentReportService`, relevant response DTOs | Some names/messages/history use current `membership.getPlan().getName()`. Refund amount comes from Payment. | Render historical name/terms from snapshot. Do not change refund rules, amount, or state. |
| Member/staff UI: `MemberMembership.jsx`, `ReceptionistMembershipSales.jsx`, `MembershipPlans.jsx` | Current offer catalog is separate in parts of the UI, but purchased benefits are resolved through active Plan list and can change/disappear after Plan edits/deactivation. | Membership API should return snapshot fields. UI must render purchased snapshot directly and use Plan API only for current offers. |

No separate Membership mapper was found in this flow: `MembershipService.toResponse` and related services assemble DTOs inline. Snapshot implementation needs a focused entity/repository and response changes, not a new mapping framework.

### Entitlements found in source

Current `MembershipPlan` has four benefit values: `courseDiscountPercent`, `personalTrainingDiscountPercent`, `allowPersonalTrainingPlan`, and `allowOneOnOneCoaching`. Snapshot the Plan name, description if approved, catalog price, duration, and these four fields. Do not infer more entitlement fields unless source changes; re-run this inventory during implementation. Historical transaction data remains on Payment/Membership and must be linked, not duplicated inconsistently.

## 4. Snapshot data model

### Recommended structure

Use an append-only `dbo.membership_terms_snapshot` row per purchase intent/paid entitlement period. At creation it references Payment; after successful settlement it also references the resulting Membership. An Admin assignment without Payment references Membership only. Existing legacy Queue rows are not a reason to create new Queue periods. Under Option C, multiple period rows can link to one extended Membership while preserving each renewal's paid terms.

| Column | Proposed SQL Server type | Notes |
|---|---|---|
| `id` | `int IDENTITY` | Primary key. |
| `payment_id` | `int NULL` | FK to Payment; unique when non-null. Membership purchase, renewal, and Upgrade target intent. |
| `membership_id` | `int NULL` | FK to Membership; may repeat across distinct paid terms if renewal extends one Membership row. Filled when the entitlement period is applied. |
| `plan_id` | `int NULL` | Provenance only; nullable if physical Plan deletion must be supported. Never read benefits through this FK. Current code uses soft deactivation; confirm deployment deletion policy. |
| `plan_name` | `nvarchar(100)` | Name at quote/assignment. |
| `plan_description` | `nvarchar(max) NULL` | Include only if approved as historical offer/contract wording. |
| `plan_price` | `decimal(12,2)` | Catalog price at intent/assignment; not Upgrade's prorated Payment amount. |
| `duration_months` | `int` | Captured term. Does not authorize changing any existing Membership `startDate`/`endDate`. |
| `period_start` | `date NULL` | Start-inclusive boundary of this purchased entitlement period; populated from the quote/settlement rule, not recomputed from a mutable Plan later. |
| `period_end` | `date NULL` | End-exclusive boundary of this period. Under Option C, each renewal period is separately recorded even if Membership's aggregate end date is extended. |
| `course_discount_percent` | `decimal(5,2)` | 0–100 for verified terms. |
| `personal_training_discount_percent` | `decimal(5,2)` | 0–100 for verified terms. |
| `allow_personal_training_plan` | `bit` | Captured entitlement. |
| `allow_one_on_one_coaching` | `bit` | Captured entitlement. |
| `captured_at` | `datetime2(6)` | Application business time, explicitly Asia/Ho_Chi_Minh for deadlines/audit. |
| `valid_until` | `datetime2(6) NULL` | For payment-linked intent, must be set to its effective deadline. Admin assignment has no Payment deadline. |
| `capture_source` | `varchar(32)` | `MEMBERSHIP_PAYMENT`, `AUTO_RENEW_PAYMENT`, `UPGRADE_PAYMENT`, `ADMIN_ASSIGNMENT`, `LEGACY_VERIFIED`, `LEGACY_UNKNOWN`, or owner-approved unverified baseline. |
| `snapshot_status` | `varchar(32)` | `VERIFIED`, `LEGACY_UNKNOWN`, optionally `LEGACY_FROZEN_UNVERIFIED` only after explicit approval. |
| `captured_by_user_id` | `int NULL` | Actor for Admin assignment; FK to User if present. Payment snapshots can resolve actor through Payment member/system process as appropriate. |
| `assignment_reason` | `nvarchar(500) NULL` | Required for Admin assignment; not a substitute for a payment. |
| `evidence_reference` | `nvarchar(500) NULL` | Evidence/provenance for verified legacy data or approved baseline. Must not contain secrets. |

Constraints and write rules:

- Primary key, Payment/Membership/User FKs as applicable, filtered unique index on non-null `payment_id`; unique `(membership_id, period_start)` when period boundaries are stored. Do not make `membership_id` alone unique if Option C can append renewal terms to one Membership row.
- Require at least one of `payment_id` or `membership_id`; settlement may populate both on the same row. A Membership may have several rows for distinct `period_start` values if Option C is selected.
- For `VERIFIED`, require all captured contractual terms, `duration_months > 0`, `plan_price >= 0`, discounts in 0–100, and non-null entitlement flags. `LEGACY_UNKNOWN` terms may be NULL and must not silently fall back to current Plan.
- Check Payment purpose/capture source and Plan reference in service transaction; cross-table constraints cannot express all rules.
- Store Payment `valid_until` on Payment or snapshot, but keep one canonical field. Recommended: snapshot owns immutable quote terms and deadline; Payment response exposes it. If Payment already has an established expiry field in the actual deployment, reuse it instead of duplicating it.
- Snapshot terms are immutable after creation. Only linkage/audit metadata may change at settlement. Enforce in service and consider DB permissions/trigger only after operational review.
- `Payment.amount`, status/timestamps, Membership dates/status, and Course/PT saved prices remain their existing source of truth. Do not duplicate or recalculate these.

## 5. Deadline and lifecycle behavior under proposed policy

All deadlines use `Asia/Ho_Chi_Minh`; represent them consistently as local business time or an instant with offset. Avoid ambiguous machine-local timestamps.

### New Membership and renewal intents

1. Under the member lock and transaction, validate the selected Plan is ACTIVE and the member is eligible; snapshot Plan name, price, duration, and all entitlements at the same point as Payment creation.
2. ONLINE `valid_until = min(createdAt + 15 minutes, next midnight Vietnam time)`. The current VNPay gateway creates a URL expiry using configured `expire-minutes` (default 15) but does not cap it at midnight; URL expiry and stored policy deadline must be made consistent.
3. COUNTER `valid_until = next midnight Vietnam time` (exclusive boundary should be defined precisely; recommended payment verified strictly before `valid_until`). The current normal counter Membership has no such expiry.
4. On successful settlement, use verified payment time, not callback arrival time. If `verifiedPaidAt < valid_until`, create Membership from snapshot. If paid at or after the exclusive deadline, do not activate and mark/refund through a reviewed late-payment path.
5. If Plan changes or deactivates after intent creation, the existing intent still uses its snapshot until its deadline. A new intent requires Plan ACTIVE and snapshots new values.
6. Expired or cancelled intents create no Membership. Re-purchase creates a new Payment and fresh snapshot from current active offer.

**Evidence gap:** Regular online Membership callback currently parses VNPay `PayDate` for Membership/Upgrade but normal `finalizePayment` does not apply a purchase deadline and stores callback-time `now()` as `paidAt`. Upgrade `apply` compares callback processing time to quote expiry, so a timely provider payment with a callback arriving after expiry may be rejected. Counter confirmation uses application processing time. Current regular membership Payment also lacks a general `valid_until`. Therefore these requirements need backend changes and focused tests; this is not current behavior.

### Existing Queue and auto-renew (new Queue creation is disallowed)

- Do not create future-start periods from a new ordinary purchase. An unexpired current Membership blocks purchase; only an eligible Upgrade may be initiated. An expired Membership may buy a new Plan under then-current offer rules.
- Existing future-start ACTIVE rows are legacy Queue. Migration must not modify any existing Membership start/end date, even if duration differs from one month. Snapshot migration is benefits/history only. A fresh read-only preflight and owner review decide their operational handling.
- Renewal behavior is undecided (Option B reminder-only or Option C pending Payment request). Do not implement scheduler changes until owner selects one and resolves its deadline/grace and payment-after-expiry behavior.
- If Option C extends the same Membership row, append a separate immutable entitlement period per successful renewal. A single snapshot on Membership cannot preserve different benefits across paid renewal periods. The date interval and which terms apply on each date must be represented explicitly.
- The current auto-renew path validates provider payment time against the source Membership end date and rejects `paidAt >= endDate at midnight`; this conflicts with any Option C policy accepting post-expiry settlement. Preserve no behavior by assumption; settle only after owner approval.

### Upgrade

- At Upgrade intent creation, verify current target Plan is ACTIVE, snapshot target offer price/duration/benefits, and persist the existing prorated amount and remaining days. Source Membership and source snapshot remain unchanged until success.
- On successful timely settlement, revalidate source eligibility and apply the result once: source becomes `UPGRADED`, target Membership links to the Upgrade Payment's snapshot, and existing remaining-time behavior remains (`startDate=today`, `endDate=source.endDate`, auto-renew inherited). Target duration remains historical/catalog data; do not extend source end date as part of snapshot work.
- On failure, cancellation, expiry, or late provider payment, do not create result Membership or change source status; use existing reviewed cancellation/refund-required semantics.
- Current Upgrade quote already computes `expiresAt` as earlier of 15 minutes and midnight for ONLINE, midnight for COUNTER. Keep one canonical deadline shared with Payment/snapshot; do not let VNPay URL expiry exceed it.
- **Open Upgrade decision:** current comparison and proration use mutable source Plan price/duration and target current Plan values. Switching source basis to historical snapshot could change quotes. Keep current formula unless owner explicitly approves a separate business rule change. Snapshotting the target at request is independent of that unresolved source-price basis.

## 6. Admin assignment audit

Current `MembershipService.createMembership` is ADMIN-only and creates no Payment, which matches the proposed no-fake-success requirement. It currently loads a Plan without checking `ACTIVE`; that must be corrected before assignment snapshots are introduced.

Proposed atomic operation:

1. Require authenticated ADMIN; validate target is an ACTIVE MEMBER and selected Plan is ACTIVE.
2. Require a non-empty reason and record actor ID/role, timestamp, Member ID, Plan ID, and Membership ID through existing `AuditLogService` (confirm whether a dedicated assignment reason column or audit description is preferable).
3. Create the Membership and `ADMIN_ASSIGNMENT` terms snapshot together. Do not create Payment or imply financial settlement.
4. Keep current authorization boundary; do not give Receptionist assignment rights through this design.

This behavior is not fully compatible with current code and needs backend changes plus authorization/audit tests. Whether manual assignment remains an approved product operation and whether reason is mandatory require final owner confirmation.

## 7. Legacy evidence, preview and migration

### What is known and unknown

The prior audit reported no snapshot/version table and that Plan audit entries do not preserve prior terms. `Payment` carries amount/reference/status/time but not original discounts or entitlement flags. `purchasePaymentId` can link a Membership to a payment but cannot reconstruct those benefits. The prior audit counted 27 Memberships, including future-start queue rows; this is a dated aggregate, not a current inventory. Do not rely on it for migration row selection.

Backfill classification:

1. **LEGACY_VERIFIED:** only when an immutable dated receipt, signed offer, database backup/catalog version, or other authoritative evidence proves every stored field. Record evidence source and reviewer.
2. **LEGACY_UNKNOWN:** default when evidence is incomplete. Keep terms NULL; do not copy current Plan values. This includes Memberships where payment amount might prove price but not discounts/duration/entitlements.
3. **LEGACY_FROZEN_UNVERIFIED:** optional demo-continuity baseline only after owner reviews a per-record preview and approves the explicit statement that values are observed at cutover, not historical truth. Preserve an evidence/provenance label and owner approval reference. Never silently classify these as verified.

Before any baseline, owner must review records grouped/listed by Member, Membership ID, status, start/end, Plan ID/name, linked Payment ID/purpose/status/method/amount/createdAt/paidAt, auto-renew, queue position, Upgrade source/result links, cancellation/refund state, and proposed snapshot fields. Mark which facts come from database versus external history. Do not alter duration/endDate or payment facts.

### Additive migration sequence (proposal only)

1. Reconfirm exact SQL Server instance/database/schema and current schema read-only at execution time; take and verify a restorable backup and rehearse on an isolated SQL Server clone.
2. Add snapshot table/constraints/indexes and, if necessary, a canonical Payment `valid_until`; do not alter existing Membership duration or dates.
3. Produce read-only preflight counts and per-row preview. Verify no duplicate Payment/Membership links and enumerate paid-but-no-result Upgrade and pending Membership/Upgrade rows.
4. Insert only evidence-backed verified legacy rows. Unknown rows remain NULL/status-marked. Any demo baseline is a separate explicitly approved, reviewed data operation, not part of automatic migration.
5. Deploy dual-write/transactional snapshot creation before switching consumers. Require snapshot on every newly created Membership/Upgrade intent and every Admin assignment; link the exact row to successful Membership result.
6. Switch discount/entitlement/history reads to snapshots. Define UNKNOWN handling before release; do not silently use live Plan as fallback.
7. Run postflight comparisons and retain pre-migration manifest.

### Read-only checks to prepare for migration review

- Identify target instance/database/schema using authorized connection metadata; do not infer from name.
- Inventory Plan fields, Membership status values (including future-start `ACTIVE` queue rows), all Membership/payment links, and associated Upgrade source/result edges.
- List current and historical `MEMBERSHIP`/`UPGRADE` Payment rows by status, method, amount, timestamps, auto-renew source note, and snapshot presence. Flag paid-without-result, pending, cancelled-then-provider-success, and refund-required cases.
- Preview one row per proposed snapshot with provenance, confidence, missing terms, and whether status is `LEGACY_VERIFIED`, `LEGACY_UNKNOWN`, or owner-approved cutover baseline. Do not derive original values from current Plan.
- Postflight: each new actionable pending Membership/Upgrade Payment has exactly one verified snapshot; each new paid/manual Membership has exactly one linked snapshot; no duplicate/orphan links; VERIFIED terms satisfy constraints; UNKNOWN rows are explicit.
- Compare before/after Payment amount/status/paid/refund fields, Membership dates/status/auto-renew, Upgrade payment/remainingDays/expiry/result links, CourseRegistration price fields and PT booking price fields. Snapshot-only migration must not modify them.

### Backup and rollback

- Require exact-target approval, verified full backup, row-level export/manifest, isolated clone rehearsal, and tested restore path before any write.
- Prefer additive rollback: keep snapshot data/table and roll application back only with a controlled plan. Returning consumers to live Plan reads reopens the P0 and is not a complete safe rollback.
- Do not automatically drop snapshots or restore over the live database. Recovery from backup requires owner approval for exact target and recovery point. No SQL has been executed or prepared as a runnable migration in this task.

## 8. Test matrix and phase plan

### Required behavior matrix

| Scenario | Test/assertion | Completion evidence |
|---|---|---|
| ONLINE initial purchase | Stored deadline is `min(createdAt+15m, VN midnight)`; gateway expiry does not exceed it; timely verified provider time creates Membership with exact quote snapshot | Unit tests for boundaries/timezone plus service integration test |
| ONLINE provider callback arrives late | Provider paid time before deadline succeeds even if callback arrives later; paid time at/after deadline never activates and follows refund-required workflow | Integration tests with fixed Clock and signed/mock verified callback |
| COUNTER initial purchase | Deadline is Vietnam midnight; staff processing before boundary settles snapshot, after boundary does not activate | Service integration tests with fixed Clock and processedBy authorization |
| Plan edit/deactivation while PENDING | Existing timely intent honors snapshot and duration; new intent against INACTIVE Plan rejected | Payment/Membership integration tests |
| Queue removal | Current Membership blocks a new ordinary purchase; no future-start Membership is created; eligible Upgrade remains available. Existing legacy future-start rows are not changed by the code path. | Membership service integration tests plus a read-only preflight; no DB writes |
| Renewal/auto-renew | Scheduler creates intent only; no status/date activation without payment; snapshot/deadline persisted; source-term eligibility retained | Scheduler + Payment service tests; assert no auto-charge |
| Upgrade Standard→Premium | Target terms snapshot at request; source Membership/status/snapshot unchanged while pending; on timely payment apply once, same existing end date/remaining-day behavior; target edit does not change result | Upgrade integration tests for pending/success/late/cancelled/idempotent states |
| Admin assignment | Only ADMIN, ACTIVE Plan and MEMBER allowed; no Payment row; required reason and actor/time audit; assignment snapshot matches chosen terms | Controller/service authorization and integration tests |
| Benefit readers | Course registration discount, PT eligibility/discount, Individual Training Plan entitlement use snapshot; no current-Plan fallback for verified snapshots | Focused service/repository tests; test edited and deactivated Plan |
| Legacy UNKNOWN | API/UI visibly marks unknown or applies owner-approved handling; no accidental zero-entitlement or current-Plan substitution | API/UI contract test based on approved UX decision |
| Existing financial history | CourseRegistration and PT saved price, Payment amount/status/refund state remain unchanged by migration | SQL Server clone pre/post manifest comparison |
| Expired then re-purchase | Expired intent creates no Membership; new intent captures then-current ACTIVE Plan terms | Payment integration test |

### Small implementation phases

**Phase 0 — Decisions and live preflight (no code):** record owner approvals; identify exact DB target; read-only inventory and per-row legacy preview; clone backup/restore rehearsal plan. **Done when:** policy questions below are resolved and every legacy row has an approved classification; no data writes.

**Phase 1 — Schema and persistence foundation:** add snapshot entity/repository/table, constraints, canonical deadline field, transactional writers for normal purchase, auto-renew intent, Upgrade target, Admin assignment, and settlement linkage. **Tests:** snapshot atomicity, uniqueness, rollback-on-failure, assignment no-Payment/audit. **Done when:** newly created intents all have immutable complete snapshots and existing behavior remains otherwise unchanged.

**Phase 2 — Deadline enforcement and settlement:** align ONLINE gateway and Payment deadline; enforce provider verified time for ONLINE and staff processing time for COUNTER; handle timely late callbacks and late-paid refund-required path; settle Membership/Renewal/Upgrade from snapshot only. Prevent new Queue periods and leave pre-existing legacy Queue rows unchanged. **Tests:** deadline boundaries, Vietnam midnight, late callback, Plan deactivation, purchase blocking, idempotency. **Done when:** no expired intent creates a Membership, no new ordinary purchase creates a future-start period, and timely valid intent does not depend on callback arrival or mutable Plan.

**Phase 3 — Entitlement consumers and response/UI:** migrate Course discount, PT eligibility/discount, Individual Training Plan access, membership/history/cancellation views to snapshot data; implement approved UNKNOWN presentation/handling. **Tests:** each reader with edited/deactivated Plan, API response contract, Member/staff rendering. **Done when:** no purchased-benefit consumer reads live Plan for a Membership with a snapshot and unknown behavior is explicit.

**Phase 4 — Legacy backfill and release:** after clone rehearsal and owner-reviewed preview, write verified rows only; any demo baseline runs as separate approved operation. Compare pre/post manifest and run full regression suite. **Done when:** SQL Server clone checks pass, financial/duration/status data is unchanged, live target and backup are explicitly approved, and every legacy unknown is disclosed.

Do not combine schema/backfill with a change to duration, endDate, payment amounts, or refund semantics.

## 9. Decisions and risks still requiring owner approval

### Policy decisions proposed but not yet final

1. Approve the ten proposed policies in Section 2, including exact exclusive deadline comparison (`verifiedPaidAt < valid_until`) and late-payment refund-required outcome.
2. Confirm the COUNTER timestamp that is authoritative (staff processing time vs recorded receipt time) and whether staff may enter a verified earlier receipt time. Current source uses processing `now()`; it does not expose a separately auditable counter receipt timestamp.
3. Confirm all deadline timezone/storage semantics and whether 15 minutes is fixed policy or should remain configurable while always capped at Vietnam midnight.
4. Confirm Admin assignment stays an available operation and that a mandatory reason plus existing AuditLog record is sufficient; decide if actor/time/reason also belong directly on snapshot row.
5. Approve handling/presentation of `LEGACY_UNKNOWN` entitlements in runtime (deny only that benefit, manual review, or another explicit path). Do not default to current Plan.
6. Approve whether `plan_description` is part of the immutable offer record and confirm no multi-currency requirement (current Payment uses VND, no currency column).
7. Upgrade source-price basis remains current Plan under current implementation. Approve separately before changing comparisons/proration to the source snapshot. Confirm target terms apply immediately after successful Upgrade while preserving current source endDate.

### Database/evidence questions not answerable from checked-in source

- The actual live schema, FK/delete behavior, SQL Server version, collation, and whether snapshot-like objects were added outside this checkout were not re-verified. Prior audit evidence is dated 2026-11-19.
- Current Membership/Payment/Upgrade row counts and exact legacy records are unknown until a fresh authorized read-only preflight. Do not treat the prior 27-row count as current.
- No evidence currently establishes original benefits for legacy records. Each requires documentary/backup evidence or remains `UNKNOWN`; a demo baseline needs owner review and explicit unverified labeling.
- The source schema/code does not establish an auditable COUNTER receipt-time field. Owner must choose whether to add one or use staff processing time.
- Product policy for legacy UNKNOWN access, contractual description retention, and upgrade price basis is not inferable from tests or historical reports.

### Approval risks before implementation

- Denying a benefit for UNKNOWN legacy snapshot could reduce a customer's expected entitlement; falling back to Plan could preserve the defect. This is a product decision, not a migration default.
- Treating verified provider `PayDate` as authoritative requires strict signature, amount, transaction, timezone and malformed/missing timestamp validation. Missing/invalid provider time must never silently become callback time.
- Aligning VNPay URL expiry with the earlier business deadline may change gateway parameters near midnight; verify provider behavior on a non-production environment.
- Admin assignment audit must be transactionally consistent with Membership creation; audit failure handling should be explicit.
- Upgrade result target snapshot needs a clear canonical relationship among Payment, MembershipUpgrade and Membership, preventing duplicate or mismatched terms.
- Any legacy demo baseline may freeze already-mutated current Plan values. It must never be described as original terms.

**Implementation gate:** no code or data changes until the owner records final policy approval, exact-target read-only preflight and per-record legacy review are complete, a restorable backup is verified, and the migration has passed on an isolated SQL Server clone. This design makes no claim that the live database was freshly inspected.
