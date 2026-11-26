# Membership Upgrade Path table draft

The Phase 4B application requires explicit directed Plan pairs. These SQL files are drafts only; they have not been executed:

- `membership-upgrade-paths-preflight.sql` — read-only identity, schema, and plan inspection.
- `membership-upgrade-paths-migration.sql` — additive creation of `dbo.membership_plan_upgrade`.
- `membership-upgrade-paths-verify.sql` — read-only metadata and integrity verification.

The table has a generated integer key, foreign keys to both Plans, a unique directed `(from_plan_id, to_plan_id)` pair, a self-link check, enabled state, UTC `datetime2(6)` audit timestamps, and a lookup index. Disabling retains the configuration history. The application does not expose hard deletion.

## Review and rehearsal order

1. Confirm the target database identity and application/schema version.
2. Run the read-only preflight and review inactive/zero-price plans and existing Upgrade anomalies.
3. Back up and restore to an isolated SQL Server clone using the repository's approved rehearsal process.
4. Run this additive migration only on that clone; then run verify and SQL Server FK, unique-pair, self-link, rollback, and Hibernate validation checks.
5. Compare the DDL and verify output, then obtain separate owner approval before any target-database execution.

## Rollback guidance (not an executable script)

Path configuration is business configuration and is not referenced by historical quotes. Prefer disabling paths through the Admin API. If a schema rollback is later approved, first stop new quote creation, export and retain every path row with its keys and audit timestamps, verify there are no application instances expecting the table, and obtain owner approval to drop the table. A rollback would permanently remove path configuration; do not drop it as routine cleanup. The Phase 2D frozen `membership_upgrade` quote columns and all historical rows remain untouched.

## Required SQL Server verification

- Both foreign keys reject unknown Plan IDs.
- The check constraint rejects a self-link.
- The unique constraint rejects duplicate directed pairs while allowing the reverse pair.
- The lookup index supports active paths by source Plan.
- A disabled path cannot be used for a new quote; an existing complete, unexpired quote still settles using its frozen columns.
- Existing path table and application compatibility checks fail closed on unexpected schema state.

No migration, rollback, or database write has been performed for Phase 4B.
