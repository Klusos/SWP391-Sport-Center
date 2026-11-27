# Training Plan Redesign Acceptance

**Date:** 2026-11-27
**Verdict:** **PASS_WITH_GAPS**

Implementation, the DB12 additive migration, schema validation, focused backend/frontend regression, lint and production build passed. Authenticated browser acceptance remains incomplete because the available saved role sessions expired/redirected to login after the source-compatible QA backend was started; no login credentials were available in the current interaction. No screenshots are claimed.

## Implemented behavior

### Individual Training Plan editor

- Replaced the long all-weeks/all-exercises form with the shared focused weekly workspace: overall plan fields, a selectable ordered week list, one active-week editor, compact exercise rows, preview, copy, confirmed deletion, validation, and sticky save/cancel actions.
- Switching weeks keeps edits in the local draft. Copying clears persisted week/exercise IDs. Save continues through the existing Individual Training Plan API and revision contract; draft navigation and preview do not create revisions. Failed requests leave the editor draft mounted.
- Individual week numbers retain the established 1–52 validation. Existing revision history remains available through the existing view.

### General class Training Plan

- Added the weekly General Plan editor and read-only views for Admin and eligible Members. General and Individual persistence/services remain separate.
- General week numbers are positive, unique per plan, nonconsecutive where desired, and displayed ascending. New/copy actions use highest existing number + 1; no arbitrary 52-week General limit is imposed.
- Existing general exercises remain top-level legacy exercises with `week_id IS NULL`. They are shown as “Bài tập chưa phân tuần”; explicit reassignment preserves exercise ID and data. The atomic save accepts the complete intended plan, and omitted items represent explicit deletion.
- Offering-derived week ranges use `startDate + 7 × (weekNumber - 1)` and clamp an in-range end date to `endDate`. Dates are not persisted. Out-of-range retained weeks remain visible with a warning; changes to Offering dates do not delete content.
- No Draft/Published state or General revision/rollback history was added. Saved content is immediately returned to Members through the existing confirmed-registration/ownership eligibility path; unsaved state remains client-side.
- Added `expectedUpdatedAt` to the new whole-plan request. It is null only for initial creation; stale submissions return 409. Class row locking serializes the save with existing plan writes. The timestamp is normalized/advanced at `datetime2(7)` precision so a returned token remains usable.
- Added the Coach-only route to the existing Spring Security matcher. Before that correction the new endpoint returned 403 before service authorization; H2 integration now verifies Coach save and Admin denial.

## Database migration and preservation

Authorized target verified immediately before SQL operations: `LAPTOP-416VBDLK / SportCenterDemo_PTOutcome_8CF1 / DBID 12 / ONLINE`. DB5 (`SportCenterDemo`, ID 5) was not modified.

Pre-migration compatibility checks found no preexisting `training_plan_week`, no `training_exercise.week_id`, and no partially applied migration objects. The reviewed SQL adds:

- `training_plan_week` with plan/actor foreign keys, positive week check, unique `(plan_id, week_number)` and `(id, plan_id)` keys.
- Nullable `training_exercise.week_id`, a JPA single-column FK and composite `(week_id, plan_id)` ownership FK, plus the week/order index.

The migration is additive and contained in a transaction with exact server/database/DBID guard and application lock. It did not update existing business rows. Post-migration aggregate checks: 4 General plans; 5 General exercises; all 5 remain unassigned; 0 weekly rows; 4 Individual plans; 8 Individual revisions; 9 Individual weeks; 14 Individual exercises; 0 cross-plan references. The transactional SQL constraint probe rolled back, leaving those counts unchanged.

Post-migration `DBCC CHECKCONSTRAINTS` reported no violations. A transaction-scoped DB12 probe confirmed duplicate `(plan_id, week_number)`, nonpositive week numbers, and cross-plan exercise/week assignments are rejected; the probe rolled back.

Backup created before migration:

- `D:\5School\SportCenterBackups\SportCenterDemo_PTOutcome_8CF1_TrainingPlanWeekly_20261127_193608.bak`
- Size: 23,163,392 bytes
- SHA-256: `82F9F2E196AE7CF4BA399E50DB6ECD112B3B75AA97F875F38D8D0CDB50A14324`
- `RESTORE VERIFYONLY WITH CHECKSUM`: passed
- SQL Server `17.0.1155.3`, database compatibility level `160`

Hibernate validation passed against DB12 with `ddl-auto=validate`: `PtOutcomeCloneSchemaValidationTest`, 1 test, 0 failures/errors/skips; all 38 mapped entities were discovered and managed, with none missing or unexpected. The focused clone validation forced read-only datasource access, disabled schedulers, and verified server/name/ID before bootstrapping.

Existing-data evidence before migration recorded the six protected plan/history table counts and digests in the run log; post-migration counts match those recorded counts. Migration SQL contained no data DML, and the post-migration exercise/plan counts and NULL legacy assignments match the preflight snapshot. The run-owned constraint probe was transactionally rolled back. No persistent synthetic DB12 fixture or run manifest was created. The original DB5 remains outside the task target.

## API, authorization and test evidence

- Existing GET/PUT plan and exercise endpoints remain available. `TrainingPlanResponse.exercises` remains the top-level unassigned legacy collection and `weeks` adds ordered weekly content. The new `PUT /api/training/classes/{classId}/plan/weekly` requires a full-state payload and `expectedUpdatedAt`.
- Assigned Coach writes remain service-checked against the class assignment. Admin remains read-only. Member visibility remains governed by the existing Member-scoped confirmed-registration query. No role or registration rules were broadened.
- `TrainingIntegrationTest`: **10 tests, 10 passed** (H2, including multiweek ordering/nonconsecutive numbers, legacy preservation and identity-preserving move, Member future-week read, duplicate/cross-plan rejection, stale-token rejection, and Admin denial).
- `IndividualTrainingPlanIntegrationTest`: **13 tests, 13 passed** (H2), confirming the existing Individual revision/authorization path.
- Frontend full Node suite: **605 passed, 0 failed, 0 skipped**. Focused editor/Individual/read-only suite before final integration: **16 passed**.
- Frontend lint: passed. Production build: passed; existing large main-bundle warning remains (about 1.04 MB minified).
- `git diff --check`: passed after removing the one introduced trailing space.

## Authenticated browser status

The current frontend origin was `http://localhost:5173`; its API base was `http://localhost:8080`. The prior saved Admin and Coach contexts initially showed authenticated screens, but their API/backend process had stopped. After the task-owned source backend was started, a reload redirected the saved contexts to `/login`; the repository's `auth:check` reported the saved Admin, Coach and Member sessions missing/expired/invalid. The source backend connected to DB12 and completed Hibernate validation with all schedulers disabled and mock payment enabled, then the task-started process was stopped. No existing owner process was stopped.

The owner confirmed use of the existing QA roles and supplied a Member account identifier, but the actual current password/re-authenticated role sessions were not available in this interaction. Consequently Coach edit, Admin read-only, and Member post-save visibility were not visually exercised. No screenshots were captured. Resume browser acceptance after refreshing the local role sessions with `frontend`'s `npm run auth:setup -- --role ADMIN`, `COACH`, and `MEMBER` against the DB12 runtime. Keep those storage states private.

## SQL artifacts and DB5 procedure

- Migration: `backend/docs/sql/proposals/phase-training-plan-weekly.sql`
- Read-only verification: `backend/docs/sql/proposals/phase-training-plan-weekly-validation.sql`
- Conditional rollback guidance: `backend/docs/sql/proposals/phase-training-plan-weekly-rollback.md`

DB5 deployment is **not performed or authorized by this task**. Proposed later sequence: obtain separate owner authorization; create and verify a fresh DB5 `COPY_ONLY, CHECKSUM` backup; verify the live server/database/DBID 5 read-only; inspect for conflicting/partial objects and record pre-migration plan/exercise snapshots; rehearse the reviewed SQL on a disposable compatible clone; prepare a DB5-specific guard (the checked-in script intentionally rejects DB5); apply the additive DDL in a controlled window; verify trusted constraints, legacy NULL assignments, historical data preservation and Hibernate `validate`; retain the backup and verification output. Do not run the DB12-guarded script against DB5.

## Changed files in this batch

- Backend: `SecurityConfig.java`, `TrainingController.java`, weekly-plan DTOs, `TrainingPlanWeek.java`, `TrainingExercise.java`, plan/exercise repositories, `TrainingService.java`, and `TrainingIntegrationTest.java`.
- Frontend: `WeeklyPlanEditor.jsx`, `GeneralTrainingPlanTab.jsx`, `IndividualTrainingPlans.jsx`, `individualTrainingPlanHelpers.js`, `CoachTraining.jsx`, `MemberTraining.jsx`, `MemberTraining.css`, `api.js`, and focused editor/Individual/Member tests.
- Documentation/SQL: backend/frontend `AGENTS.md`, `backend/docs/API_GUIDE.md`, `docs/architecture.md`, `docs/business-rules/README.md`, the three weekly-plan SQL/rollback artifacts, this acceptance report, and the existing Coach/Member owner QA follow-up.

## Repository state and gaps

Branch remains `qa/team-integration-20261122`; HEAD remains `1f96272df08f156c5a84b74183f833de61defcf4`. The preexisting dirty worktree was preserved; no commit, push, reset, or cleanup was run. No DB5 writes, migration, or scheduler activation occurred.

Remaining gaps for full PASS: authenticated screenshots/browser verification for Coach/Admin/Member at 1366 and 1440; live authenticated editor copy/switch/save/reload and legacy reassignment; and SQL Server application-path transactional save/replay coverage beyond the H2 service tests plus DB12 DDL/constraint probe. The full frontend suite was run after the expectedUpdatedAt assertion was added. Owner action needed: refresh the local role sessions through the existing private workflow.
