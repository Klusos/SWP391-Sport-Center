# Phase 2C.1 — Membership Phase 2A Clone Rehearsal Checklist

**Preparation only. Not approval to execute.** No database connection, backup, restore, or SQL execution was performed for this checklist. Never use the rehearsal scripts on `SportCenterDemo`; they reject that database by design.

## 0. Owner authorization and clone identity

- [ ] Obtain explicit approval for the separate Phase 2C.2 clone rehearsal, including the approved backup source and restore destination.
- [ ] DBA confirms the backup is for the expected development/test source, records checksum/metadata, and restores it to an isolated disposable SQL Server database.
- [ ] Do not use a clone name or server name from this draft as a default. Read actual `SERVERPROPERTY('ServerName')`, `@@SERVERNAME`, `SERVERPROPERTY('InstanceName')`, and `DB_NAME()` from the restored clone through an approved identity-only check.
- [ ] Confirm the rehearsal target is isolated from the source and production, and that no application or scheduler is writing to it.
- [ ] Fill in both `__SET_VERIFIED_CLONE_SERVERPROPERTY_SERVERNAME__` and `__SET_VERIFIED_CLONE_DATABASE_NAME__` in each clone script from the verified identity. `SportCenterDemo` is always rejected.
- [ ] Confirm SQL Server version, compatibility level, collation, database options, and JDK/backend build intended for Hibernate validation.

## 1. Baseline and preflight

- [ ] Review `membership-redesign-phase2a-clone-preflight.sql` in full before execution.
- [ ] Run only on the owner-approved clone after completing the guard values.
- [ ] Review schema/column metadata and baseline counts against the Phase 1 report; differences are expected to be investigated, not normalized.
- [ ] Save its row-level SHA-256 manifest output and anomaly result sets with the rehearsal record. The script stores clone-local sidecar manifest/header tables containing clone identity, counts, completion state, table name, record ID and row hash; it does not modify Membership, Payment, Upgrade, or CancellationRequest rows.
- [ ] Confirm each manifest hash covers the full expected original field set below; the primary key is stored separately as `record_id` and is compared by ID.
- [ ] Resolve each returned duplicate Upgrade result, SUCCESS Payment without an Upgrade attempt, and SUCCESS attempt without a result by owner disposition. Do not fix rows as part of rehearsal preparation.
- [ ] Confirm the migration's duplicate guards abort on those anomalies before any DDL commits.
- [ ] Confirm the baseline header is exactly one completed row for the verified clone identity, per-table and total counts match manifest rows, and every source record ID has one manifest row. Migration also re-hashes original columns and stops if baseline content changed before DDL.

## 2. Migration and structural checks

- [ ] Stop clone application/scheduler writers and record this state.
- [ ] Run the separate `membership-redesign-phase2a-clone-migration.sql` only after the Phase 2C.2 approval gate. It is a copy of the additive draft with a clone-only identity guard; it is not idempotent and must not be blindly rerun.
- [ ] Confirm schema objects, CHECK/FK/index definitions, enabled/trusted state, referenced unique keys, and filters against the Phase 2A design.
- [ ] Confirm new Membership, Payment and Upgrade snapshot columns remain NULL for all pre-existing rows, and the Renewal Intent table is empty.
- [ ] Compare the postflight row manifest for every original Membership, Payment, MembershipUpgrade and MembershipCancellationRequest by record ID; require zero changed/missing/new rows unless individually explained and approved.
- [ ] Run `ddl-auto=validate` with the matching application build against the clone. Record the exact result; do not treat H2 or source inspection as a substitute.
- [ ] Measure schema lock duration with representative clone size and document application writer behavior. No production lock estimate can be inferred from a small clone.

## 3. Constraint and transaction test matrix

Use disposable test rows only inside the approved clone. Record cleanup through restoring the clone snapshot, not by editing source/demo history.

| Case | Expected result |
|---|---|
| Insert OPEN Intent → Payment attempt referencing Intent → set Payment SUCCESS and accepted marker → settle Intent → insert successor | One transaction commits; all FK links agree; successor starts at source `endDate`. |
| Force an error after marking Payment but before successor insert | Whole transaction rolls back; no accepted Payment, settled Intent, or successor remains. |
| Multiple unaccepted attempts referencing one Intent | Allowed; each has a distinct existing transaction code. |
| Mark a second attempt accepted for the same Intent | Filtered unique index rejects it. |
| Insert a second Membership successor for the same Intent | Filtered unique index rejects it. |
| Link successor to a Payment other than Intent's selected settlement Payment | Composite FK rejects it. |
| Successful Upgrade result already exists for source, then add another result | Filtered unique index rejects it. |
| SUCCESS Upgrade Payment with no attempt; attempt with SUCCESS Payment but no result | Clone preflight reports; migration guard aborts without committed DDL. |
| Legacy rows with all newly added nullable columns NULL | CHECK constraints permit them unchanged. |
| Partial snapshot/provenance: NULL status/source, invalid enum, out-of-range discount, negative amount/credit, invalid days/dates | Corresponding CHECK rejects each invalid insert/update. |
| Valid full Membership, Payment and Upgrade snapshots | CHECK constraints accept complete rows. |
| Wrong server, wrong database, placeholder identity, or `SportCenterDemo` | Each script stops before writing/DDL. |
| Migration failure injected before commit | Transaction rolls back all added objects; baseline business rows remain unchanged. |
| Re-run after successful migration | Guard stops and requires manual review; no blind rerun. |

### Manifest field coverage contract

The clone preflight allowlist requires exact column-name set equality and emits actual column metadata. A missing or additional column stops before it writes the manifest. Each hash covers all listed non-key fields, including nullable fields; the table ID is the manifest key.

| Table | Hashed original business columns (ID compared separately) |
|---|---|
| `membership` | `member_id`, `plan_id`, `auto_renew`, `start_date`, `end_date`, `status`, `purchase_payment_id`, `cancelled_at`, `cancel_reason` |
| `payment` | `member_id`, `amount`, `method`, `purpose`, `reference_id`, `auto_renew`, `status`, `transaction_code`, `processed_by`, `note`, `created_at`, `paid_at`, `refunded_at`, `refunded_by` |
| `membership_upgrade` | `payment_id`, `source_membership_id`, `target_plan_id`, `remaining_days`, `expires_at`, `created_at`, `result_membership_id` |
| `membership_cancellation_request` | `membership_id`, `member_id`, `reason`, `status`, `requested_at`, `decided_by`, `decided_at`, `decision_note` |

**Phase 1 evidence gap:** the written live preflight summarizes CancellationRequest columns by purpose but does not preserve the complete raw column metadata result in the report. Its field set above is supported by the checked-in entity and migration, not a complete recorded live metadata result. Therefore the clone preflight's exact metadata output must be reviewed and retained; do not claim full live-schema verification until that comparison is done.

## 4. Precision, compatibility and recovery

- [ ] Verify `datetime2(6)` round-trips to Java `LocalDateTime` and Hibernate `ddl-auto=validate` passes.
- [ ] Test Vietnam-local deadline conversion at just-before, exact-boundary and just-after instants; document that `datetime2` has no timezone metadata.
- [ ] Verify `decimal(12,2)` Payment/quote and `decimal(5,2)` percentage boundaries with representative values.
- [ ] On clone only, run old application code with new nullable columns present and confirm legacy all-NULL Payment/Membership/Upgrade writes still satisfy constraints; do not use production/shared data.
- [ ] Force a failed DDL transaction and verify rollback. After a successful rehearsal, use a fresh restore to reset the clone for any repeat; do not hand-delete test history.
- [ ] Preserve the baseline manifest and all results. Do not drop its sidecar table until the rehearsal record is exported and an owner-approved cleanup step exists.
- [ ] Recovery before any new application data is written: restore the clone snapshot. For a failed transactional DDL batch, verify the transaction rolled back. Do not infer a production rollback method from clone behavior.

## 5. Rehearsal exit criteria

- [ ] All identity guards were tested with deliberately wrong values on a disposable harness/clone and rejected before writes.
- [ ] Preflight anomaly sets are empty or have explicit owner dispositions; no anomalous row was changed.
- [ ] DDL, constraints, filtered indexes, transaction rollback, manifest comparison and Hibernate validation pass.
- [ ] Per-record manifests for all four existing tables show no unexplained changes.
- [ ] SQL Server version, lock timing, logs and residual risks are recorded for owner review.
- [ ] Report rehearsal outcome as evidence only. A successful clone rehearsal does not authorize live/demo/production migration execution.
