# Final Business Rule Hardening and QA Report

**Snapshot:** 2026-11-28 · branch `qa/team-integration-20261122` · expected base `466d2c5241ea817226468451cada65db241d80ee`

## Verdict

**PASS_WITH_GAPS.** Focused frontend checks/lint and backend classes passed, including new assessment display and revenue category assertions. Several approved rules were implemented in source, but this batch did not add or execute sufficient direct regression coverage for all new authorization, concurrency, or deactivation invariants. BR-06 parent lifecycle protection is not implemented. The owner-approved production behavior must not be represented as fully accepted or fully verified. No database was accessed or changed, no migration was applied, and no commit/push was made.

## Implemented and preserved

### BR-01 Coach deactivation

`CoachDeactivationGuard.requireNoUnfinishedObligations` checks active classes whose Offering has not ended, SCHEDULED sessions, nonexpired `PENDING_PAYMENT` PT holds, `CONFIRMED` PT bookings, and assigned `PENDING` Skill Assessments. `StaffManagementService.updateStatus`, `CoachService.updateCoachStatus`, and generic `UserService.updateUser` invoke it before deactivation. User and Coach rows are locked in the established User-then-Coach order; Skill Assessment creation also locks the Coach row. Generic Coach deletion and role transitions are rejected to preserve historical references and User/Coach consistency. Errors identify counts and the kind of outstanding work.

**Gap:** only source-level inspection was completed for the new guard. No focused integration test, SQL Server concurrency test, or live database check exercised those branches. The current active-class condition may conservatively block a class through its Offering end date even if its sessions are complete; verify this interpretation against owner expectations before relaxing it.

### BR-02 Member deactivation

`MemberRegistrationService.deactivateMember` remains a status-only account operation, now serialized by the Member User lock. It does not mutate Memberships, bookings, payments, registrations, or history. Existing authentication resolves active account status on requests, so a previously issued JWT is not sufficient after deactivation.

**Gap:** no new direct test in this batch covered paid-entitlement preservation, reactivation, stale JWT, pending payment callback, or Renewal processing on an inactive Member. Preserve normal payment reconciliation; no new cancellation/settlement policy was added.

### BR-03 Membership revenue reporting

`PaymentRepository.membershipRevenueByCategory` and `PaymentReportService.revenueReport` add backend-authoritative `PURCHASE`, `UPGRADE`, `RENEWAL`, and `TOTAL` values. Upgrade classification uses `PaymentPurpose.UPGRADE`; Renewal classification uses the persisted `renewalIntent` relationship, so categories do not double-count. The existing report basis remains SUCCESS Payments in the selected `paidAt` half-open date range, with existing gross/refund semantics. `ReportPage` renders the category breakdown.

**Gap:** focused Payment integration assertions for purchase + Upgrade category totals passed. Renewal-association classification, pending/failed exclusion, refund treatment, and exact date-boundary cases were not all independently tested in this batch. No DB5 reconciliation query was run.

### BR-04 positive Membership Plan price

Create/update DTO constraints and `MembershipPlanService` reject prices `<= 0`; Admin UI minimum is `0.01`. `backend/docs/sql/membership-plan-positive-price-preflight.sql` is read-only and guarded; `membership-plan-positive-price.sql` is a proposed migration. The fresh schema snapshot includes a positive-price CHECK.

**Not applied:** the owner authorized preparation but did not authorize execution against DB5. No live query checked existing zero-price plans. If preflight finds any, preserve those records and request a data policy decision before migration.

### BR-05 active Upgrade Path validity

`MembershipPlanService.updatePlan` validates active incoming and outgoing paths under the plan lock. The target price must remain strictly greater than source price; inactive paths are ignored. Existing path creation/reactivation locking and frozen quote snapshots remain unchanged. No path is silently deactivated.

**Gap:** focused tests for incoming/outgoing/equal/multiple/inactive/reactivation/concurrent edits and frozen quotes were not run in this batch. SQL Server locking behavior is unverified.

### BR-06 parent lifecycle protection

**NOT IMPLEMENTED.** `SportService.updateSport`, `CourseService.updateCourse`, `CourseClassService.updateClass`, and `CourseOfferingService.updateOffering` still accept lifecycle status updates without the newly approved cross-descendant unfinished-obligation guard. The existing `CourseOffering` values `OPEN/CLOSED` are a registration lifecycle, and closing registration is not necessarily deactivation; the implementation must preserve that distinct use. This is a material approved-scope gap. No business-rule reinterpretation or cascade behavior was added.

### BR-07 eight-character password minimum

`CreateUserRequest`, `CreateCoachRequest`, `CreateStaffCoachRequest`, and `ChangePasswordRequest` now use minimum length 8. Member and Consultation account-creation paths already require eight. Login remains unchanged, preserving authentication for existing hashes. No extra complexity requirement was added.

**Gap:** direct API boundary tests for 6/7/8 characters across every creation/change path and successful login of an existing shorter-password account were not run in this batch.

## Confirmed QA fixes

| Item | Status and implementation |
|---|---|
| FIX-01 Admin self-lockout/account integrity | Implemented in `UserService.updateUser`, `deleteUser`, and generic creation path; controller passes authenticated `Jwt`. Blocks self-demotion/deactivation, last active Admin removal, invalid status, and unsafe generic Coach role transitions. Direct API regression matrix is **NOT_RUN**. |
| FIX-02 Consultation identity | Member Course Consultation contact fields are read-only; missing-profile help now directs the Member to authorized staff/Admin. Guest DOB maximum uses yesterday in Vietnam business time, and invalid DOB response is privacy-safe. Existing backend authenticated identity source remains authoritative and Consultation does not save User. Frontend suite and Consultation integration class passed; dedicated Course modal and DOB rendered tests remain absent. |
| FIX-03 Support null IDs | Assignment is considered owned only when both viewer and assignee IDs are non-null finite IDs. Frontend suite passed; the identity case is source-contract coverage, not browser proof. |
| FIX-04 Support last activity | `SupportTicketService.reply` advances `updatedAt` in the reply transaction. Member and staff unchanged-status reply regression passed in the 7-test `SupportTicketIntegrationTest`. |
| FIX-05 PT pending cancellation | Member UI exposes existing cancel action for actionable pending-payment rows only when existing `canMemberCancel` eligibility permits. Frontend suite passed; no live API acceptance or late-payment integration was repeated. |
| FIX-06 pending-only sport visibility | Member sport overview includes own pending Consultation/Assessment records without granting course/PT eligibility. Focused frontend tests and full frontend suite passed. |
| FIX-07 Coach Assessment learner display | Assessment DTO includes `memberName` and `sportName`; list queries fetch Member/Sport with entity graphs to avoid per-row lazy lookup. `SkillAssessmentIntegrationTest` passed 21/21, including HTTP name assertions. |
| FIX-08 CoachTraining search | Course and class search state are separate; returning preserves course search. Frontend suite passed, including existing focused search-state regression. |
| FIX-09 Consultation cancellation UI | **Intentionally withdrawn by owner on 2026-11-28 before demo.** The Admin/Receptionist button, prompt handler, frontend API helper, and related import are removed. Other Consultation actions and status history/filtering remain. Backend endpoint, authorization, business logic, and database are unchanged. |

## Adversarial matrix reconciliation

Updated matrix statuses must distinguish execution from source changes. Existing accepted cases remain recorded evidence, not a claim of rerun. New or changed high-risk paths still lacking run evidence include:

- Admin self-demotion/deactivation, last-Admin protection, invalid status, and another-account management.
- Coach deactivation via every Admin endpoint; Coach responsibility blockers and concurrent class/PT/Assessment assignment.
- Member deactivation preserving paid Membership, payments and bookings; old JWT rejection and post-reactivation entitlement behavior.
- Revenue category separation including Renewal by association, refunds, pending/failure, and report boundaries.
- Plan zero/negative direct API create/update and DB CHECK preflight.
- Active Upgrade Path incoming/outgoing/equality/multiple path conflicts; activation vs price-edit race and frozen quote continuity.
- Six/seven/eight-character password API boundaries and historical short-password login.
- Cross-role DOB/phone update restrictions and Course Consultation read-only behavior.
- PT cancellation while hold is active, expired-hold behavior, and late provider collection.
- Assessment response scoping for assigned vs other Coach.
- Parent deactivation vs active dependent obligations and concurrent dependent creation (BR-06 is not implemented).

The updated rows are in `docs/SPORTCENTER_ADVERSARIAL_TEST_MATRIX.md`. Existing evidence remains cross-referenced there; `NOT_RUN` is not converted to PASS from source inspection.

## Verification

- `frontend`: `npm.cmd test` — **611 passed, 0 failed**.
- `frontend`: `npm.cmd run lint` — **passed**.
- `frontend`: `npm.cmd run build` — **passed**, 207 modules, existing large JS chunk warning (~1,038 kB).
- `backend`: `MembershipPlanIntegrationTest`, `MembershipPlanAuditTest`, `SupportTicketIntegrationTest`, `ConsultationRequestIntegrationTest` — **59 passed, 0 failures/errors** on isolated H2 (Maven `-Dtest` command using a task-specific copied local repository). These tests do not prove SQL Server locking.
- `backend`: `SkillAssessmentIntegrationTest` — **21 passed, 0 failed**; `PaymentIntegrationTest#revenueReportGroupsSupportedPaymentPurposesBySettlementDate` — **1 passed, 0 failed** on corrected rerun. The first attempt exposed an incorrect expected aggregate after adding an Upgrade fixture; the test expectation was corrected and the focused method passed. Both use H2, not DB5.
- Frontend browser smoke: **NOT_RUN**. No authenticated browser session or active verified runtime was used in this batch.
- SQL Server/DB5 concurrency and data checks: **NOT_RUN**. No database was accessed or written.
- `git diff --check` — **passed** (no whitespace errors; Git reported only its configured LF-to-CRLF working-copy notices).

## Files changed in this batch

Backend service/repository/DTO files include `UserController`, `UserService`, `StaffManagementService`, `CoachService`, new `CoachDeactivationGuard`, `MemberRegistrationService`, Membership Plan DTO/service/repository, Payment report DTO/repository/service, Skill Assessment DTO/repository/service, and `SupportTicketService`. Focused tests include `MembershipPlanIntegrationTest` + `MembershipPlanAuditTest` + `SupportTicketIntegrationTest` + `ConsultationRequestIntegrationTest` (59 total), `SkillAssessmentIntegrationTest` (21), and one `PaymentIntegrationTest` report method (1): **81 passing test executions across the final successful commands**. One earlier Payment test attempt failed due to the changed fixture's stale expected total; expectation was fixed and the method passed on rerun.

Frontend changes include `CourseConsultationModal`, Guest `ConsultForm`, `CourseDetail`, `ConsultationQueue`, `MemberLevelsAssessments`, `memberSportEligibility`, `PtBooking`, `CoachTraining`, `SupportTickets`, `MembershipPlans`, `ReportPage`, and related regression tests. SQL artifacts include the guarded positive-price preflight/proposed migration and `submission/SQL/SportCenter_FullSchema.sql`. Documentation updates include the business-rule register, independent reconciliation dispositions, adversarial matrix supplement, API guide, architecture map, SRD as-built Markdown addendum, Vietnamese hardening Q&A, and this report. The original SRD Word file was not edited; most earlier handbook prose and Q&A remain English, so full handbook localization is incomplete.

No DB5 or DB12 data was changed. No migration, backup, scheduler activation, commit, or push was performed. The owner's untracked QA audit and browser evidence directories were preserved and not staged.

## Remaining work before team handoff

1. Implement and test BR-06, with Offering `OPEN/CLOSED` semantics preserved.
2. The focused Skill Assessment and Payment category tests passed; add direct negative/authorization/concurrency regression cases identified above.
3. Add direct tests for FIX-01, BR-01/02/05/07 and complete renewal report/refund/date coverage.
4. Decide whether the proposed positive-price CHECK is compatible with existing DB5 data using the read-only preflight; separately authorize migration if required.
5. Run `git diff --check`, review all staged content and secrets, then commit/push only after the approved scope has adequate verification. Current branch/HEAD and remote state should be rechecked at that time.
6. Finish Vietnamese handbook localization and review API guide/architecture/SRD claims against actual tests. The original SRD Word file has not been edited.

## Demo correction — FIX-09 UI withdrawn (2026-11-28)

Per the owner's urgent pre-demo instruction, the Consultation cancellation button was removed from `ConsultationQueue.jsx`, along with its prompt handler, import, and now-unused `cancelConsultationRequestApi` frontend helper. No other Consultation action, status filter, backend endpoint, authorization, business rule, or database object changed. FIX-09 is intentionally withdrawn from the UI; do not restore it without renewed owner direction.

Verification after this correction: `node --test tests/receptionistSalesConsultationVisuals.test.mjs` passed **8/8** and `npm.cmd run lint` passed. Full frontend tests/build were not repeated for this narrow removal. The current demo commit and push status is recorded in the handoff response.
