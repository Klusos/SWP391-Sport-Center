# Consultation DOB additive migration — rollback assessment

## Scope

The proposal adds only nullable `dbo.consultation_request.date_of_birth date`. It does not alter `age` or write historical DOB values. No rollback SQL is supplied because dropping a column after new requests have populated it would destroy member-provided data.

## Safe response by state

- **Migration did not commit:** inspect the exact error and target identity, rerun read-only preflight, and resolve the cause before a separately authorized retry.
- **Migration committed but no DOB values have been collected:** retaining the nullable column is the preferred rollback. If a later approved application version requires schema reversal, first verify exact target, backup, zero non-NULL DOB rows, no active writers, and obtain separate DDL authorization. Dropping the column is still destructive and is not authorized by this proposal.
- **One or more DOB values have been collected:** do not drop, clear, or rewrite the column. Keep it and roll application code forward/back only in a way that preserves the data; prepare a separately reviewed compatibility migration if required.
- **Unexpected column/schema baseline:** stop. Do not attempt to normalize, rename, or drop objects. Capture metadata and obtain target-specific review.
- **Post-verification fails:** stop application rollout and preserve the database state. Do not run automatic cleanup or rollback. Compare preflight and verification aggregate outputs, inspect metadata read-only, and prepare a reviewed correction only if necessary.

## Preconditions for any future reversal

1. Explicitly approved server, database name, database ID, and product version.
2. Fresh verified backup and tested recovery plan.
3. Read-only confirmation that the column is exactly nullable SQL Server `date` and contains zero non-NULL values.
4. Confirmation that application writers are stopped through an independently approved operational procedure.
5. Separate owner authorization for the exact destructive DDL.

The additive migration itself contains no DML and has transaction/`XACT_ABORT`/`TRY-CATCH` protections. A failed migration must not cause age-to-DOB conversion or any other historical rewrite.
