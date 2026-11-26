# PT outcome status constraint rollback assessment

Proposal only. No rollback SQL is supplied or executed.

## Safe rollback conditions

A return to the four-state status CHECK is only potentially safe after an authorized, exact-target read-only verification establishes that:
- the approved server/database identity is still selected and ONLINE;
- the exact current six-state CHECK definition, enabled/trusted state, status-column contract, and audit_log contract match the reviewed baseline;
- there are zero COMPLETED and zero NO_SHOW rows, and no NULL or unknown statuses;
- application code that can persist COMPLETED/NO_SHOW has been removed or is stopped; and
- a target-specific backup has been created and verified, with a separately authorized isolated rollback rehearsal completed.

A target-specific rollback must use the exact reviewed prior four-state CHECK definition. Do not improvise or recreate it from memory.

## Unsafe after outcomes are recorded

If any COMPLETED or NO_SHOW booking exists, the four-state CHECK would reject valid persisted outcome history. Deleting, remapping, or backfilling those rows to force rollback is prohibited. Preserve the outcomes and stop for an owner-approved forward-fix/recovery plan.

## Unexpected baseline or failed verification

If target identity, schema, constraint definition/hash, enabled/trusted state, audit_log contract, or status data differs from the reviewed baseline, do not execute rollback or migration. Preserve diagnostic metadata and prepare a target-specific review.

If post-migration verification fails, do not automatically rewrite business rows or run rollback. First determine whether the migration transaction committed, verify the exact target and current constraint/data metadata using separately authorized read-only inspection, preserve the backup, and request a reviewed recovery plan. The migration transaction is designed to roll back DDL on an in-transaction assertion/error; that does not justify automatic data repair.

No rollback is authorized by these notes.

