# Sport Center repository guidance

## Repository map

- `frontend/` is the React 19, Vite, and React Router application. Its instructions are in [`frontend/AGENTS.md`](frontend/AGENTS.md).
- `backend/` is the Java 21 / Spring Boot application. Read [`backend/AGENTS.md`](backend/AGENTS.md) before backend work; retain its security and schema constraints.
- `backend/docs/API_GUIDE.md` is a broad API reference. Treat dated phase notes in `backend/docs/IMPLEMENTATION_STATUS.md`, `DEMO_GUIDE.md`, and migration documents as historical evidence unless independently reverified.
- `backend/docs/sql/` contains manually reviewed SQL Server scripts and setup notes. `submission/SQL/` contains a separate fresh-schema snapshot and its own scope warnings.
- `docs/` is the concise cross-project architecture and business-rule index. It does not replace feature-level source, tests, SQL review, or owner decisions.

## Source of truth and decision status

Keep these kinds of evidence separate:

1. A current, explicit product-owner decision is the authority for business policy. Record its source and date when available. Do not infer approval from a UI label, code path, test, old meeting/phase note, or demo fixture.
2. Current backend and frontend source show implemented behavior. Automated tests show only the cases they execute; neither establishes that a behavior is approved policy.
3. A checked-in SQL script describes a proposed/manual schema transition, not whether it was run. A dated preflight or implementation report is a snapshot, not current database evidence.
4. For live schema/data claims, use current authorized read-only evidence for the exact server and database. Never infer the target from its database name alone.

When these sources disagree, document the contradiction and mark the product question **Requires owner confirmation**. Do not silently update business behavior or present a historical implementation note as current policy.

## Safe workflow

- Inspect the relevant source, tests, local instructions, and working-tree status before editing. Preserve existing staged, unstaged, and untracked work. Do not use destructive Git commands (`reset`, `clean`, forced checkout, or equivalent) to make a workspace tidy.
- Keep changes scoped to the request. Do not commit, push, merge, rebase, or rewrite another contributor's work unless explicitly requested.
- Treat database access as potentially destructive. Do not run migrations, seed scripts, data fixes, or commands that write to a database without explicit authorization for the exact target and operation. Do not assume a local URL points to disposable data. Require a reviewed backup and isolated rehearsal when the migration documentation calls for them.
- Do not change schema, roles, permissions, payment behavior, or business rules as a documentation task. Keep credentials, tokens, and personal data out of tracked files and command output.
- Prefer existing helpers, components, DTOs, services, and migrations over parallel implementations. Follow `backend/AGENTS.md` for backend-specific constraints.

## Verification expectations

- Read the package scripts and test configuration first. Run focused tests for changed behavior and the relevant broader checks when requested and safe; report exact commands and outcomes. Never claim a check passed if it was not run.
- Frontend Node tests are `npm test` in `frontend/`; browser tests are separate: `npm run test:e2e`. E2E requires an already-running frontend and API plus the Playwright browser installation. See `frontend/AGENTS.md`.
- Backend tests use Maven. Inspect the selected profile and integration-test configuration before running them; some tests can connect to SQL Server. Use only a verified disposable clone for database integration tests. Do not run migrations as a test shortcut.
- Unit, mock, H2, and static tests do not prove SQL Server behavior, deployment state, browser appearance, or product-owner approval. State those limitations explicitly.
- For documentation-only changes, check links and claims against source and use `git diff --check`; do not run application or database workflows without a reason.

## Autonomous execution and approval boundaries

1. **One phase, one scoped authorization.** Once the owner explicitly authorizes a phase, complete its approved steps without requesting permission for every command. This does not authorize future phases or override narrower task restrictions.
2. **Fix routine technical blockers autonomously.** Within scope, diagnose and correct shell/PowerShell errors, type-conversion and harness errors, incorrect arguments, non-destructive compilation/test failures, and other small directly related implementation defects.
3. **Retry intelligently.** After identifying and correcting a routine issue, rerun the necessary safety checks and continue. Do not repeat a failing command without understanding its cause.
4. **Reserve BLOCKED for meaningful boundaries.** Stop when an operation exceeds authorization; server/database identity cannot be verified; a destination exists or may be overwritten; destructive/irreversible work is required; migration preflight or verification detects a real schema/data-integrity anomaly; a failure cannot be safely understood or corrected in scope; or an explicit prohibition would be violated.
5. **Database safety remains mandatory.** Never modify the original `SportCenterDemo` schema or business records without explicit authorization. Never overwrite backups, database files, or existing databases. Never automatically execute destructive rollback. Recheck identity and destinations before writes. Preserve backups and historical data.
6. **Do not overengineer.** Prefer the smallest correct solution. Do not add infrastructure, tables, abstractions, or workflows without a demonstrated requirement.
7. **Report after meaningful progress.** Summarize completed work, verification evidence, real blockers, and remaining risks. Avoid stopping after a minor recoverable error.
