# Membership Phase 1 — Live Database Preflight

**Status:** Phase 1 read-only preflight completed for the local development/test database. No application data, schema, or configuration was changed.
**Read time:** 2026-11-19, using Vietnam business date `2026-11-19`.
**Scope:** Identity, schema metadata, Membership, Membership Payment/Upgrade, cancellation and recorded refund state. No credentials or unnecessary Member personal information are included.

## 1. Connection identity and TLS exception

Before connecting, the local `MSSQLSERVER` Windows service was found Running. `netstat` showed the host `sqlservr` process (PID 36248) listening on `0.0.0.0:1433`; this identifies a native local SQL Server listener consistent with the expected dev/test endpoint. The Docker CLI could not reach its daemon, so container listings were unavailable; no Docker service was started or changed.

Using the owner-approved local-only JDBC exception with the existing backend credentials and `encrypt=true;trustServerCertificate=true`, an identity-only SELECT returned:

| Property | Live result |
|---|---|
| `@@SERVERNAME` | `LAPTOP-416VBDLK` |
| `SERVERPROPERTY('InstanceName')` | `NULL` (default instance) |
| `DB_NAME()` | `SportCenterDemo` |
| Default schema | `dbo` |

The JDBC client was Microsoft SQL Server JDBC 13.4.0.jre11, target `127.0.0.1:1433`. The backend-configured host/database and prior audit identity match this result.

**TLS limitation:** `trustServerCertificate=true` encrypts this local session but does not validate the server certificate/identity. The owner approved this exception only for this local dev/test preflight. It must not be reused for production or treated as certificate-authenticated identity. Credentials were read from existing backend config and never printed or written to the report.

## 2. Membership inventory

Current database counts (classification uses Vietnam date 2026-11-19 and the stored status/date fields):

| Stored status | Date classification | Count |
|---|---|---:|
| ACTIVE | Effective (`startDate <= today < endDate`) | 7 |
| ACTIVE | Future start | 0 |
| ACTIVE | Past end date | 0 |
| EXPIRED | As stored | 0 |
| CANCELLED | As stored | 11 |
| UPGRADED | As stored | 9 |
| **Total** |  | **27** |

There are no other stored Membership statuses. Active Membership IDs are `1003, 2002, 3002, 4004, 5005, 5010, 5012`; each has a linked SUCCESS Payment. This is a database snapshot, not a statement of approved entitlement policy.

All 11 future-start Memberships are now CANCELLED; none remains an ACTIVE Queue. The future records and payment reconciliation are detailed in section 3.

### Plan duration comparison

There are 5 Plans: 4 ACTIVE and 1 INACTIVE. All 5 currently have `duration_months = 1`. Nine of 27 Membership intervals differ from `DATEADD(month, currentPlan.duration_months, start_date)`:

| Membership ID | Status | Dates | Current Plan duration | Payment/upgrade evidence | Review note |
|---:|---|---|---:|---|---|
| 2 | CANCELLED | 2026-11-25 → 2027-05-25 | 1 month | no `purchase_payment_id` | Six-month legacy future row; provenance/payment evidence unknown. |
| 3 | CANCELLED | 2027-05-25 → 2027-11-25 | 1 month | no `purchase_payment_id` | Six-month legacy future row; provenance/payment evidence unknown. |
| 1002 | UPGRADED | 2026-10-27 → 2026-11-25 | 1 month | Upgrade Payment 4004 SUCCESS; source/result chain | Existing Upgrade period/transition, not a normal one-month purchase. |
| 1003 | ACTIVE | 2026-10-27 → 2026-11-25 | 1 month | Upgrade Payment 4005 SUCCESS | Active Upgrade result; effective through 2026-11-24 (end exclusive). |
| 2002 | ACTIVE | 2026-10-29 → 2027-01-29 | 1 month | Membership Payment 6004 SUCCESS, 1,299,000 VND; `autoRenew=true` | Three-month interval; 71 days remain as of read date. Preserve pending owner review. |
| 3002 | ACTIVE | 2026-11-01 → 2027-05-01 | 1 month | Membership Payment 7022 SUCCESS, 2,599,000 VND; `autoRenew=true` | Six-month interval; **163 days remain** as of read date. Preserve pending owner review. |
| 4002 | UPGRADED | 2026-11-02 → 2026-11-26 | 1 month | Upgrade Payment 9002 SUCCESS; later attempt 9006 CANCELLED and 9007 SUCCESS | Upgrade/source/result chain; not a normal purchase interval. |
| 4004 | ACTIVE | 2026-11-02 → 2026-11-26 | 1 month | Upgrade Payment 9007 SUCCESS | Active Upgrade result. |
| 5005 | ACTIVE | 2026-11-08 → 2026-12-02 | 1 month | Upgrade Payment 11008 SUCCESS | Active Upgrade result. |

A Plan-duration mismatch is only a review signal: the Plan is mutable and Upgrade has historically retained remaining time. It is not evidence that a Membership should be shortened or lengthened. No dates or durations were changed.

## 3. Payment, Queue and Refund reconciliation

### Membership-purpose Payments

| Payment status | Count | COUNTER | ONLINE |
|---|---:|---:|---:|
| SUCCESS | 9 | 3 | 6 |
| PENDING | 0 | 0 | 0 |
| CANCELLED | 10 | 3 | 7 |
| REFUND_REQUIRED | 0 | 0 | 0 |
| REFUNDED | 9 | 6 | 3 |

The `PaymentStatus` enum has no EXPIRED value. Expired attempts currently appear under another stored state, if applicable; the database does not retain a distinct EXPIRED status for these rows.

Upgrade-purpose Payments: 9 SUCCESS (6 COUNTER, 3 ONLINE), 1 CANCELLED (ONLINE), 0 PENDING, 0 REFUND_REQUIRED, 0 REFUNDED. The cancelled Upgrade attempt is Payment 9006; the source Membership later has a separate successful Upgrade Payment 9007.

### Future Queue disposition

Nine future-start Membership rows with direct purchase Payments are now CANCELLED and their Payments are REFUNDED, with `refunded_at` and `refunded_by` populated:

| Membership | Payment | Member ID | Refund recorded |
|---:|---:|---:|---|
| 4003 | 9004 | 7 | 2026-11-19 |
| 4007 | 9011 | 9 | 2026-11-05 |
| 5002 | 11005 | 9 | 2026-11-08 |
| 5003 | 11006 | 9 | 2026-11-08 |
| 5004 | 11007 | 9 | 2026-11-08 |
| 5006 | 11009 | 9 | 2026-11-08 |
| 5007 | 11023 | 9 | 2026-11-19 |
| 5013 | 11037 | 1013 | 2026-11-18 |
| 5014 | 11038 | 6 | 2026-11-19 |

Each listed transaction has a distinct Payment ID and one recorded refund timestamp/actor. No Payment refund-field/status inconsistencies were found. This establishes the application database state only; it cannot prove how many transfers occurred at the bank/provider. Reconcile provider settlement/refund statements before concluding that no external duplicate refund occurred.

Two additional future-start Memberships, IDs `2` and `3`, are CANCELLED but have `purchase_payment_id = NULL`. Their payment/refund provenance cannot be established from the current relational links. Do not infer they were paid, refunded, or Admin-granted without owner/source evidence.

Cancellation requests: 11 APPROVED, 1 WITHDRAWN, 0 PENDING. Nine approved requests correspond to the refunded future Membership Payments above. Two approved requests concern Memberships without linked purchase Payments. No CANCELLED Membership with a linked Payment in SUCCESS, PENDING, or REFUND_REQUIRED was found. No pending Membership cancellation request remains.

No Payment-purpose MEMBERSHIP or UPGRADE row is currently PENDING or REFUND_REQUIRED. There are 10 CANCELLED Membership attempts with no `paid_at` or refund fields; those are recorded as cancelled unpaid attempts, not refunds.

**Duplicate-refund assessment:** no duplicate refund is indicated by the current Payment fields: all 9 REFUNDED Payments have their own refund timestamp and actor, and all use distinct transaction/Payment IDs. The schema records only one refund timestamp/actor per Payment and has no refund ledger/provider-refund identifier; duplicate external refunds cannot be ruled out from this database alone. The audit-log search found no event whose entity/action/description matched Payment/refund terms; this is not proof that no external refund occurred.

## 4. Live schema, FKs, indexes and Renewal design

Live metadata confirms:

- `dbo.membership_renewal_intent` does not exist.
- No benefit/entitlement snapshot columns exist on `dbo.membership`, `dbo.payment`, or `dbo.membership_plan` beyond the Plan's current mutable fields.
- `membership` includes `member_id`, `plan_id`, `auto_renew`, `start_date`, `end_date`, `status`, nullable `purchase_payment_id`, `cancelled_at`, and `cancel_reason`.
- `membership_plan` includes current price, duration, course/PT discounts, PT-plan/1-on-1 entitlement flags, and status.
- `payment` includes amount, method, purpose/reference, auto-renew flag, status, unique transaction code, processor, note, created/paid/refunded timestamps, and refund actor. There is no separate refund table among the queried live tables; refund state is stored on Payment.
- `membership_upgrade` includes Payment, source Membership, target Plan, remaining days, expiry, and nullable result Membership.
- `membership_cancellation_request` stores request/decision state and actor/timestamps; cancellation is not itself refund evidence.
- `audit_log` stores actor, role, action, entity type/id, description and created time. Its actor FK points to `users`; no Payment/refund action matched the read-only search.

Live FK/unique details:

- `membership.member_id → users.id`; `membership.plan_id → membership_plan.id`; `membership.purchase_payment_id → payment.id`.
- `payment.member_id → users.id`; `processed_by` and `refunded_by → users.id`. `payment.reference_id` is not a polymorphic FK to Membership/Plan.
- `membership_upgrade.payment_id → payment.id` is unique; source/result Membership and target Plan have FKs. An ordinary index exists on source Membership, but there is no unique successful result per source.
- `membership_cancellation_request.membership_id/member_id/decided_by` have FKs. A filtered unique index allows one PENDING request per Membership.
- `payment.transaction_code` is unique. A filtered unique index permits one PENDING UPGRADE Payment per Member. No unique Membership purchase-payment link, Renewal intent, or Renewal successor constraint exists.

The schema supports storing Membership benefit snapshots as added Membership columns without requiring a separate benefit-snapshot table. The owner has approved a separate `membership_renewal_intent` table because Intent must exist independently from attempts. Add a RenewalIntent FK on each Renewal Payment attempt; unique source-period/intent and successful successor constraints; keep each period's benefit snapshot on its resulting Membership. Multiple Payment attempts are structurally possible today, but they are not currently grouped by a durable Renewal key.

## 5. Owner-review list before Phase 2 migration design

1. Memberships `2` and `3`: cancelled, future, six-month dates, and no direct Payment link. Confirm provenance and whether any external payment evidence exists.
2. Memberships `2002` and `3002`: active Membership periods longer than their current one-month Plans, with successful Membership Payments and `autoRenew=true`. Preserve the historical terms; confirm how their next Renewal Intent should be quoted and which Plan terms govern it.
3. Upgrade-derived duration mismatches `1002`, `1003`, `4002`, `4004`, and `5005`: retain dates/history; confirm the new full-term Upgrade policy must not rewrite them.
4. Refunded future rows and Payments in section 3: reconcile against provider/bank records before any claim about external duplicate refunds. The DB records one refund confirmation each.
5. Existing successfully paid Memberships generally have no complete benefit/price/duration snapshot. Mark historical entitlement provenance UNKNOWN unless external records establish the sold terms; do not backfill from current Plan fields.
6. The 10 cancelled, unpaid Membership Payment attempts and one cancelled Upgrade attempt are historical attempts, not Refunds. Confirm any retention/cleanup policy separately; this preflight proposes no cleanup.

No row is proposed for UPDATE, DELETE, cancellation, refund, or duration normalization.

## 6. Phase 2 blockers and next steps

- Select and review RenewalIntent lifecycle/status, retry-after-expiry policy, uniqueness key, and cancellation/late-callback states. The table itself is owner-approved; its exact DDL is not.
- Confirm renewal behavior when the source Plan is inactive before intent creation and when the Plan changes after intent creation. Snapshot-at-intent should govern a valid pending intent, but the pre-intent behavior remains a product rule.
- Confirm intent deadline edge semantics/provider-paid-time evidence, COUNTER receipt-time evidence, and callback handling for an expired or already-settled intent.
- Confirm Upgrade credit rounding, credit >= target price, legacy UNKNOWN source eligibility, and effective date when a valid callback arrives late.
- Reconcile Memberships `2` and `3` and external refund records with the owner/provider before deciding any data treatment.
- Prepare additive migration and rollback only after the above decisions, an approved backup/restore rehearsal, and an owner-reviewed per-row manifest. Snapshot migration must leave Membership dates/status, Payment amount/status/refund fields, Upgrade links, and Course/PT price snapshots unchanged.

**Recommended next step:** owner reviews the listed legacy rows and refund reconciliation gaps, then approves the remaining lifecycle/credit policies. Phase 2 can then specify the RenewalIntent and snapshot DDL plus verification/rollback plan; do not execute it until separately authorized.

## 7. Evidence and safety boundary

- **Live database verified:** identity, table/column metadata, FKs/indexes, current status counts and the row-level Membership/Payment/Refund checks summarized above. Queries were SELECT and SQL Server metadata reads only.
- **Source verified:** current JPA entities/status enum and the absence of a current RenewalIntent entity/snapshot mapping.
- **Historical evidence only:** `docs/post-defense-audit.md` snapshot dated 2026-11-19. Its earlier future Queue list has since changed: Memberships `4003`, `5014`, and `5007` now show CANCELLED with linked REFUNDED Payments.
- **Not verified:** provider/bank refund settlement, historical Plan/benefit versions, payment provenance of Memberships `2`/`3`, and any data outside `SportCenterDemo`.
- No INSERT/UPDATE/DELETE/ALTER/DROP, migration, refund, backend service operation, source change, commit, or push was performed.
