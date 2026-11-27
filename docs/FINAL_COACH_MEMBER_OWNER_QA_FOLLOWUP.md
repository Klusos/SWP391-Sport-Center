# Final Coach/Member Owner QA Follow-up

**Date:** 2026-11-26
**Verdict:** PASS_WITH_GAPS

This follow-up records scoped UI corrections and source-level business consistency findings. Existing worktree changes were preserved; no commit, push, schema change, original database write, historical business update, or scheduler activation was performed.

## Implemented corrections

- **Coach Dashboard:** aligned the assigned-class pager buttons and page indicator to equal dimensions and spacing. Assigned-class totals, data, KPIs, and links are unchanged.
- **Member Dashboard attendance:** unified the populated and empty content/footer junction so the shared edge is square while the outside corners remain rounded. The preview remains the four newest valid recorded entries, without an age cutoff; cancelled/invalid attendance remains excluded. Monthly KPI logic is unchanged.
- **Member Dashboard skill cards:** restored the owner-requested stable two-row presentation. The sport name occupies the first row; the complete skill label stays nonshrinking and on one line in the second row. Long names may wrap within their own row.
- **Member Attendance:** added the Coach label/name to overview cards from the existing authenticated Member Training data, with an honest fallback. The detail header now emphasizes Coach consistently with class, offering, and date range. Higher-specificity scoped status styles now map PRESENT green, ABSENT red, EXCUSED amber, unrecorded neutral gray, and future/scheduled blue in attendance rows and counters.
- **Member Membership:** removed visible Membership IDs from Member-facing current, upcoming, and historical cards while retaining identifiers in application data. Changed user-facing terminology to “Gói hội viên.” Renewal opt-out guidance is separate from the 72-hour upcoming-entitlement cancellation policy.
- **Support conversations:** original request/messages align by authenticated viewer ID versus persisted member/sender IDs, not display names. Staff assignment/status controls are separated below the reply composer. Resolving now asks for confirmation. Existing ticket API and lifecycle actions are unchanged.
- **Repository guidance:** documented stable two-row Member skill cards and identity-based Support message alignment in `frontend/AGENTS.md`.

## Attendance and training consistency audit

**Confirmed implementation inconsistency; policy correction deferred for owner approval.** `AttendanceService.mark` serializes attendance writes on the session row, records corrections in attendance history, and requires a reason. `TrainingService.recordResult` uses the same session lock and only permits recording against a confirmed registration whose current attendance is PRESENT. However, after a result exists, a later authorized correction to ABSENT or EXCUSED leaves the existing `TrainingResult` persisted and included in Member-visible results and class progress averages/counts. Results are stored as one mutable row per session/member; no invalidation or revision mechanism was found. No result is hard-deleted by attendance correction.

The safest minimal recommendation is to reject correction away from PRESENT while a TrainingResult exists (option A), unless the owner approves a result invalidation/history policy. Option B needs explicit historical-result preservation and active-result semantics that the current model does not provide cleanly. Hard deletion is not recommended. No business-policy change was made. Attendance status (PRESENT/ABSENT/EXCUSED) and class-session lifecycle status (SCHEDULED/COMPLETED/CANCELLED) are distinct persisted concepts; different displays may reflect those different fields rather than contradictory values for one field.

## Renewal and cancellation semantics audit

- Disabling autoRenew before an Intent exists disables the preference; there is no Intent to close.
- Disabling it with an OPEN Intent closes that Intent. Pending attempts without collection evidence become CANCELLED. Attempts with collection/accepted evidence are routed to REFUND_REQUIRED/reconciliation, not represented as money already returned.
- A Member can cancel an owned pending Renewal attempt only while it is PENDING and has no collection evidence. The Intent remains open when still eligible, allowing another attempt.
- A successfully paid upcoming Membership is an entitlement, not an unpaid Intent. Member cancellation requests are subject to the strict 72-hour-before-start boundary and staff approval. Approval creates a refund obligation; it does not confirm disbursement. The Admin exceptional cancellation path is separate.
- “Nhận xử lý” is offered when a ticket is unassigned and not CLOSED, irrespective of IN_PROGRESS. Assignment is separate from lifecycle status in the current source, so an IN_PROGRESS but unassigned ticket is representable. No new transition or permission was introduced.

## Portal layout review and limitations

Member routes were reviewed against the existing page-specific blue/white layouts. There is no single shared Member page-header/layout component today; current routes contain differing header implementations. This batch kept scoped fixes in accepted pages and did not start a broad portal layout refactor. Full route-by-route visual standardization remains an audit-only follow-up.

## Verification

- Frontend suite: **589/589 passed** (`npm.cmd test -- --run`).
- Frontend lint: **passed** (`npm.cmd run lint`).
- Production build: **passed** (`npm.cmd run build`); existing >500 kB chunk warning remains.
- `git diff --check`: **passed**; Git emitted existing LF/CRLF conversion warnings only.
- Clone fixture setup and direct credential validation: **1/1 passed** using `Phase9MemberBrowserFixtureIT`; runtime identity guard targeted DB12 and Hibernate validation. The generated account credential check passed through the fixture’s `AuthService`.
- Exact synthetic fixture cleanup: **1/1 passed**; helper reported `originalRowsPreserved=true` and deleted its run manifest. It removed only records matching the unique run marker and generated IDs.
- Browser Renewal workflow: **BLOCKED**. The isolated backend was verified on DB12 and the temporary frontend on 5174 pointed to backend 8081. The backend initially rejected the browser origin because its default CORS allowlist omits 5174; the task-owned backend was restarted with a process-local allowlist. The actual `/api/auth/login` then returned HTTP 401 for the fixture account, although the guarded fixture test verified the same credential with the service. No preference or lifecycle state was bypassed, and no browser screenshot can truthfully be reported as acceptance evidence.
- Renewal Intent/payment/successor acceptance: **NOT_TESTED** in this batch because Member UI authentication did not complete. No autoRenew flag, Intent, Payment, or successor Membership was forced or created.

The clone runtime was `LAPTOP-416VBDLK / SportCenterDemo_PTOutcome_8CF1 / DBID 12`, with Hibernate `validate`, all schedulers disabled, mock payment selected, and no external notification/payment effects. The owner’s DB5 app on 8080 and frontend on 5173 were left running and untouched. The current source branch and HEAD remained `qa/team-integration-20261122` / `1f96272df08f156c5a84b74183f833de61defcf4`.

## Files changed for this follow-up

- `frontend/src/pages/Dashboard.css`
- `frontend/src/pages/Membertheme.css`
- `frontend/src/pages/MemberAttendance.jsx`
- `frontend/src/pages/MemberAttendance.css`
- `frontend/src/pages/MemberMembership.jsx`
- `frontend/src/pages/SupportTickets.jsx`
- `frontend/src/pages/SupportTickets.css`
- `frontend/AGENTS.md`
- `frontend/tests/memberAttendance.test.mjs`
- `frontend/tests/memberPortalTargetedFixes.test.mjs`
- `frontend/tests/memberMembershipView.test.mjs`
- `frontend/tests/supportTickets.test.mjs`

The repository already contained numerous unrelated accumulated modifications and untracked owner work; none were discarded or staged.

## Remaining owner decisions and work

1. Approve or reject the proposed rule preventing PRESENT-to-ABSENT/EXCUSED attendance correction after a TrainingResult exists, or define an explicit invalidation/history policy.
2. Resolve the clone runtime HTTP login 401 discrepancy before completing Renewal browser acceptance. The test helper/service credential check and HTTP endpoint behavior disagree; do not use direct database preference updates as a workaround.
3. Complete the Renewal browser path (opt-in, lifecycle Intent, attempt presentation, mock settlement, one successor, duplicate settlement check) after normal login succeeds.
4. Consider a separate shared Member layout/header consistency task if consistent sizing across every Member route remains required.

**Overall:** scoped presentation changes and automated checks PASS. Business consistency has one confirmed implementation-policy mismatch requiring owner decision. Renewal runtime/browser acceptance remains blocked by the unexplained authentication discrepancy; demo acceptance should remain PASS_WITH_GAPS until that path is verified.

## Superseding owner decisions and final follow-up — 2026-11-26

This section supersedes the outstanding decision statements above where they conflict with the owner's decisions received on 2026-11-26. It does not erase historical evidence from earlier runs.

### Approved business decisions and implementation

- **Attendance/TrainingResult — PASS:** the owner selected Option A. `AttendanceService` now rejects Coach/Admin attempts to change an attendance away from PRESENT when a TrainingResult exists for that class session and Member. The check runs in the existing locked transaction shared with result writes. A rejected correction preserves attendance, result, history, and audit state. Corrections remain allowed when no result conflicts; absent Members remain ineligible for result recording. `AttendanceIntegrationTest` and `TrainingIntegrationTest`: **23/23 passed**. Clone SQL Server concurrency/rejection test: **2/2 passed**; the competing operations serialize and only one can win. No schema or historical data changes were made.
- **Historical inconsistency — known limitation:** the read-only DB5 check found one existing ABSENT attendance with a TrainingResult (`attendanceId=5`, `resultId=3`, `sessionId=2027`, `memberId=9`). DB12 had zero such pairs at inspection. The DB5 row was not changed. It remains historical evidence whose disposition requires separate owner authorization.
- **Renewal cancellation — policy confirmed:** retain the existing strict 72-hour ordinary upcoming Membership cancellation rule, with no Renewal-specific exception. Renewal is opt-in; disabling it before payment remains distinct from cancelling an unpaid attempt. A paid successor is not cancelled by turning off future Renewal. No backend cancellation or refund policy was changed.
- Member copy now separates Renewal preference guidance from the 72-hour warning. The warning appears for the OPEN Renewal request and its linked pending Renewal payment, not for ordinary Purchase or Upgrade rows. The pending section remains consolidated and uses persisted Payment/Intent associations. Cancelling an unpaid attempt refreshes the UI and preserves the OPEN Intent for retry.

### Renewal authenticated runtime evidence

- **Authentication — PASS:** the known owner-provided Member account logged in normally. The synthetic-account 401 was traced to the local harness parser failing to unescape Java `Properties.store`'s escaped exclamation mark. Java `Properties.load` confirmed the stored credential, and a fresh synthetic account subsequently authenticated through the normal browser login form. No security/JWT bypass or account modification was used.
- **Opt-in and lifecycle — PASS:** on the verified clone (`LAPTOP-416VBDLK / SportCenterDemo_PTOutcome_8CF1 / DBID 12`), an eligible synthetic Member enabled Renewal in the UI. Controlled lifecycle invocation created one OPEN Intent; repeated invocation reported `ALREADY_EXISTS`. No payment or successor was created merely by enabling Renewal.
- **Pending attempt/retry UI — PASS:** normal authenticated UI payment initiation created a persisted pending Renewal Payment linked to the Intent. The combined section displayed one transaction, its frozen quote, amount, method, deadline, cancellation/continuation actions, and the Renewal-specific 72-hour notice; it did not duplicate the Intent as a second row. Cancelling the unpaid attempt left the Intent OPEN and exposed retry. A second attempt followed the same presentation. After the latest cancellation, refresh showed the attempt as cancelled and the eligible request as retryable.
- **Online settlement — PASS_WITH_GAPS:** a locally generated signed callback reached the real DB12 application's VNPay IPN endpoint and was accepted as a verified collection, but the settlement engine returned “Collection recorded for reconciliation.” Independent persisted state at that point was Payment `REFUND_REQUIRED`, collected amount `725,000 VND`, Intent `OPEN`, zero successor Memberships, and zero successful settlement audit events. This correctly did not grant an entitlement or claim a refund had been disbursed. The attempt's Payment/Intent dates and collected amount were within their recorded bounds, but the exact domain reconciliation reason was not captured from the callback response; therefore the cause remains **unresolved** and this run does **not** establish successful browser settlement, duplicate callback behavior, paid-successor UI, or 72-hour cancellation UI. Do not reinterpret the reconciliation record as a successful Renewal. Previously recorded `Phase5BFinalRenewalHttpIT` signed-IPN acceptance remains separate evidence (including settlement/replay/invalid facts); it was not rerun here.
- **Cleanup — PASS:** the guarded `Phase9MemberBrowserFixtureIT` cleanup initially stopped at an FK constraint because it attempted to delete the Intent before its run-owned Payment attempts. The helper was corrected to delete only the marked Member's linked Renewal attempts before the Intent, then remove Memberships and remaining fixture Payments. Rerun passed **1/1**, printed `originalRowsPreserved=true`, removed its manifest, and the manifest is now absent. Only the run-owned synthetic marker records were targeted. No historical owner-created rows were cleaned.

### Member portal layout and visual checks

- Added a scoped Member main-content shell/header treatment in `Membertheme.css` and documented the standard in `frontend/AGENTS.md`. Specialized Dashboard and Courses hero headers remain page-specific.
- Authenticated browser checks covered Dashboard, Courses/detail, Membership, Schedule, Levels/Assessments, Training overview/detail, Attendance overview/detail, PT Booking, Support, and Profile at desktop widths **1280, 1366, 1440, and 1920 CSS px** where practical. The sampled routes had no horizontal document overflow. Shared header surfaces use the common blue/white treatment; the intentionally specialized Dashboard Membership hero and Courses hero remain intact. This is a rendered overflow/layout check, not a full visual sign-off of every conditional state.
- Owner corrections verified during the browser pass include the square attendance preview/footer junction in populated/empty states, stable two-row skill cards, four newest valid attendance entries, attendance colors and Coach metadata, hidden Member-facing Membership IDs, consolidated Renewal section, and viewer-relative Support message alignment. The latest changed Renewal warning/status/cancel-refresh behavior was retested in the authenticated browser; the rest were read-only checks.

### Verification and final state

- Frontend tests: **592/592 passed**, 0 failed/skipped.
- Frontend lint: **passed**.
- Production build: **passed**; the existing ~1,011 kB minified main chunk warning remains.
- `git diff --check`: **passed**; Git emitted only existing CRLF conversion warnings.
- Attendance backend focused suites: **23/23 passed**; clone SQL Server attendance consistency/concurrency: **2/2 passed**.
- Renewal lifecycle helper cleanup: **1/1 passed**. Broader SQL Server Renewal/IPN suite was not repeated because its prior successful evidence remains in the earlier acceptance report.
- The local DB12 runtime used explicit clone configuration, Hibernate `validate`, all schedulers disabled, and no real provider transaction. Task-owned backend port 8081 and frontend port 5175 were stopped. Existing owner services on ports 8080 and 5173 remain running. No DB5 writes, migrations, commits, or pushes occurred. Branch/HEAD remain `qa/team-integration-20261122` / `1f96272df08f156c5a84b74183f833de61defcf4`.

**Final verdict: PASS_WITH_GAPS.** Attendance/TrainingResult consistency and the authenticated Renewal opt-in/Intent/pending-attempt/retry flow pass. Successful settlement through the current browser-runtime synthetic IPN attempt remains unresolved because that collection correctly entered reconciliation rather than settlement. Existing SQL Server signed-IPN acceptance is prior evidence, not a replacement for the missing current browser settlement result. No additional owner business decision is pending; the remaining item is technical diagnosis and a successful clean synthetic settlement run.

## Superseding Renewal settlement diagnosis and acceptance — 2026-11-26

This section supersedes the unresolved Renewal settlement statements above. It records this run only; it does not claim a real VNPay Sandbox transaction.

### Diagnosis

- **Root cause: test callback construction defect.** The earlier signed callback used provider transaction reference `QA11316`. The Renewal callback contract requires a numeric provider transaction reference (`GatewayCallback.isRenewalCollectionEligible`, `[0-9]+`). `RenewalPaymentAdapterService.onlineEvidence` therefore attached the explicit validation issue “Signed VNPay collection is missing or has invalid Renewal settlement fields”. The signed collection was retained for reconciliation, which correctly prevented entitlement creation. This was not a production settlement defect, fixture-date defect, amount/currency mismatch, or reason to relax financial checks.
- No production business code was changed. The successful reproduction used the existing callback signature verifier and a numeric synthetic provider reference, with a test-only local secret that is not recorded here.

### Successful SQL Server and browser settlement

- Target identity was verified as `LAPTOP-416VBDLK / SportCenterDemo_PTOutcome_8CF1 / DBID 12`; the run used a unique synthetic fixture. Global, Renewal, and PT fallback schedulers were disabled. No real payment was completed.
- The normal authenticated Member UI enabled Renewal; the controlled lifecycle created one OPEN Intent, and a repeat did not create another. The UI created a persisted ONLINE Renewal Payment for **725,000 VND**. The configured Sandbox checkout returned error 72 before any provider transaction, so settlement was performed with the actual application's IPN endpoint using a locally generated valid signature and numeric synthetic provider reference.
- The IPN endpoint returned `RspCode 00 / Confirm Success`. Independent Member API reads showed Payment **11327** `SUCCESS` for 725,000 VND, Intent **57** `SETTLED`, and exactly one associated successor Membership **5321**. The successor belongs to the synthetic Member and Plan **2190**, starts at the source Membership's exclusive end date (**28/11/2026**), ends **28/12/2026**, and links to both the Renewal Intent and Payment. Its status is ACTIVE in storage and it was upcoming/not yet effective in the Member UI. Frozen benefit fields are not exposed by the Member response DTO; frozen-quote/snapshot and settlement persistence are covered by the SQL Server integration assertions below.
- Replaying the same valid callback returned `RspCode 02 / Order already confirmed`. It did not create a second successor or duplicate successful settlement audit. The Member payment result displayed only Membership **5321**, its correct dates, Intent association, and transaction amount; refresh showed one upcoming Membership and no pending Renewal transaction.
- The new successor fell within the strict ordinary 72-hour cancellation restriction on the test date. The Member UI presented it as beyond the ordinary cancellation request deadline and did not offer an ordinary cancellation action. Turning off future Renewal changed the preference only; the paid successor remained visible and unchanged.
- The browser's initial Sandbox checkout attempt was not a Sandbox acceptance: it returned provider error 72 and no real transaction was made. **Signed synthetic IPN acceptance: PASS. Actual VNPay Sandbox end-to-end: NOT_TESTED.**

### Reused lifecycle and regression evidence

- `Phase5BFinalRenewalHttpIT`: **5/5 passed** on the guarded DB12 SQL Server target. Existing cases cover the authenticated Renewal journey; valid signed VNPay IPN, persisted collection evidence, successor and one settlement audit; duplicate callback; invalid signature, amount, and transaction reference; concurrent lifecycle idempotency; and concurrent settlement single-successor behavior. This is integration evidence, distinct from the browser checks above.
- `Phase9MemberBrowserFixtureIT` setup: **1/1 passed**. Exact fixture cleanup: **1/1 passed** with `originalRowsPreserved=true` and the run manifest removed. The cleanup helper was adjusted to delete the run-owned successor first, clear the Intent's settlement links using its valid lifecycle transition, then remove linked attempts and remaining run-owned rows in FK-safe order. No historical or owner-created records were targeted.
- `ConsultationRequestIntegrationTest`: **25/25 passed** under its isolated H2 test configuration. No DB5 write occurred. No frontend source changed in this settlement diagnosis, so frontend suites were not rerun.
- Existing lifecycle evidence remains applicable for outside-window no-intent behavior, concurrent/repeated Intent suppression, unpaid attempt cancellation and retry, and disabling Renewal with an OPEN Intent. The browser run additionally verified the paid successor and future-Renewal opt-out behavior. No operating-system clock or historical Membership date was changed.

### Cleanup and final state

- Run-owned manifest is absent. Historical-row preservation assertion passed. Task-started backend port 8081 and frontend port 5175 were stopped; owner services on 8080 and 5173 were left running.
- Branch/HEAD remain `qa/team-integration-20261122` / `1f96272df08f156c5a84b74183f833de61defcf4`. No commit, push, migration, DB5 write, real payment, or scheduler activation occurred. `git diff --check` passed (only existing line-ending conversion warnings).

**Renewal synthetic settlement acceptance: PASS.** **Overall Renewal/provider acceptance: PASS_WITH_GAPS** because a VNPay Sandbox-originated transaction remains untested. No production settlement fix was required; the correction was to use a contract-valid numeric provider transaction reference in the synthetic callback.

## Final Owner Visual QA Corrections — 2026-11-26

### Implemented

- **Coach Dashboard pagination:** replaced the text chevrons with matched inline SVG chevrons. Both buttons now have the same fixed 34px box, centered 16px icon, padding, border, and disabled geometry; the page indicator uses the same height and centered flex alignment. Pagination behavior and six-item pages are unchanged.
- **Member Dashboard Recent Attendance:** replaced the nested table/frame and footer button with compact rows, date/time tiles, course and class metadata, available Coach name, and existing status colors. “Xem tất cả” now sits in the card header. Empty state is plain text in the card. The accepted four newest valid recorded entries remain the only source; the preview has no age cutoff, and invalid/cancelled records remain excluded.
- **Member Dashboard skill cards:** restored flexible content-sized horizontal cards. Sport name and nonshrinking, nonwrapping badge share one row; cards wrap as flex items when needed. No fixed card width was added.
- **Member Attendance History:** the overview and detail share a segmented donut based on existing recorded counts: green PRESENT, red ABSENT, amber EXCUSED. The center percentage still uses present/recorded. Zero recorded entries keep the neutral ring and no-data label. Added pure share-calculation regression cases for 50/50 and 50/25/25 distributions.
- **Member Support modal:** removed the close button's rectangular border, retained its accessible name and click target, and added hover/focus-visible treatment. Escape, focus trapping/restoration, and form behavior are unchanged.
- **Receptionist Support:** applied the existing blue/white framed header treatment and desktop content alignment. Management controls remain below the reply composer in a compact flex group. Inspection confirmed “Nhận xử lý” calls the assignment endpoint and assigns a staff member, while “Bắt đầu xử lý” changes ticket status; they are distinct, so both remain. Resolved tickets retain “Mở lại” and now show “Đóng yêu cầu” as a destructive outline with confirmation. Existing resolve confirmation is preserved.
- **Member Training:** centered the overview title and refresh control on the shared header's cross axis; title margins remain zero and no filler subtitle was added.
- **Guidance/tests:** replaced the superseded two-row skill-card rule in `frontend/AGENTS.md`; added coverage for the horizontal layout, attendance list, donut shares, pager geometry, Receptionist support header/actions, close confirmation, and Training header alignment.

### Verification

- Focused frontend tests: **39/39 passed**.
- Full frontend suite: **592/592 passed**, 0 failed, 0 skipped.
- Frontend lint: **passed** (`npm.cmd run lint`).
- Production build: **passed** (`npm.cmd run build`). The existing 1,011.64 kB main-chunk warning remains.
- `git diff --check`: **passed**; Git only reported existing LF/CRLF conversion warnings.
- The clone identity was independently checked read-only as `LAPTOP-416VBDLK / SportCenterDemo_PTOutcome_8CF1 / DBID 12 / ONLINE`.

### Browser verification limitation

Rendered browser verification and screenshots are **BLOCKED**. The previous browser tabs referenced ports 5175/8081, but those task runtimes were stopped and ports 5173, 5175, 8080, and 8081 were all closed. The existing packaged backend was tried on DB12 with Hibernate `validate` and schedulers disabled; it exited because its mappings require a missing `attendance` table. A source rebuild was attempted with `mvn.cmd -o -DskipTests package`; Maven first resolved its default local repository under `C:\.m2` and could not create it. With the explicit repository path, Maven failed opening the shared `spring-boot-starter-parent:4.1.1` named lock after five attempts. The lock/cache was not deleted or altered. DB5 was not used. No browser screenshots or rendered geometry claims are made; run-time visual acceptance remains for owner retest once a current DB12-compatible backend runtime is available.

### Scope and state

Membership UI and Renewal settlement were not reopened. No backend source, business rule, database, or historical record was changed. No screenshot was captured because no compatible application runtime was available. No owner decision is pending; the Receptionist assignment and status actions are confirmed distinct and both are retained. Branch/HEAD remain `qa/team-integration-20261122` / `1f96272df08f156c5a84b74183f833de61defcf4`. No commit or push occurred.

**Verdict: PASS_WITH_GAPS.** The requested UI corrections and automated checks pass at source/test level; authenticated rendered visual acceptance is blocked by the unavailable current DB12-compatible runtime.

## Final Owner QA Corrections — 2026-11-27

### Implementation

- **Attendance rejection layout:** Coach and Admin attendance now render each learner's validation rejection in a full-width row associated with that learner. The actions column is right-aligned and does not expand with long error text; draft and reason inputs remain on the learner row.
- **Elapsed class sessions:** Added a guarded five-minute completion scheduler and transaction service. On application readiness it catches up elapsed `SCHEDULED` sessions; each candidate is locked and revalidated against its persisted end time in `Asia/Ho_Chi_Minh` before transitioning to `COMPLETED`. Concurrent/repeated invocations are idempotent and require a system-actor audit event. It creates no attendance, TrainingResult, score, assessment, or financial change. Global scheduling remains the controlling enablement switch; no scheduler was activated against DB5. The existing authorized manual completion API remains available; no Coach UI completion action was found to remove.
- **Support Option B:** Receptionist claim of an unassigned `OPEN` ticket now atomically assigns the actor and changes status to `IN_PROGRESS`. Legacy unassigned `IN_PROGRESS` tickets remain claimable. Conflicting claims do not overwrite the winner; repeat claims by the same actor are idempotent. Admin oversight remains. The existing assignment endpoint is preserved.
- The Support create modal close control uses an X icon with a 44px target and no normal border, background, or shadow; accessible focus and Escape behavior remain.
- Member and Receptionist Support use five-item list pagination, refresh while retaining selection/filter/page/draft where possible, and isolate scrolling to the conversation history. Receptionist management actions remain separated from reply composition.
- Receptionist Dashboard, Member List, and Course/PT payment pages use the accepted framed header styling. Browser inspection caught a Dashboard selector mismatch; the selector scope is corrected and regression-covered. Member List's existing scoped selector was already correct.

### Verification

- Frontend: **594/594 tests passed**, 0 failures or skips; lint passed; production build passed. The existing large main-chunk warning remains (~1,013 kB).
- Focused backend H2 tests: **11/11 passed** across class-session completion, Support claim behavior, and scheduler enabled/disabled cases.
- Guarded DB12 SQL Server tests: **ClassSessionCompletionSqlServerIT 2/2** and **SupportClaimSqlServerIT 2/2 passed**. Tests verified committed state, concurrent claims/completions, audit count, and no automatic attendance/result. Their exact synthetic fixtures were removed with preservation assertions. No DB5 writes or migrations occurred.
- `git diff --check` passed; Git emitted existing LF/CRLF conversion warnings. A full backend suite was not run.
- Authenticated browser: Member modal close was inspected at rendered size/position and tested with Escape/focus restoration. Receptionist Support refresh retained the selected ticket; the existing sample data had only closed tickets, so the live claim workflow and >5-item pagination could not be exercised in the browser. SQL Server tests cover claim concurrency. Long Attendance error-row behavior has regression coverage but was not browser-rendered with an eligible conflicting fixture.
- At 1440x900, the Receptionist Dashboard header computed to a 1px framed border, 18px radius, and blue-white gradient; Member List computed to the same treatment. No document horizontal overflow. Member List screenshot: `docs/evidence/final-owner-qa-receptionist-members-1440.png`. Dashboard header geometry was verified from computed styles, but a correctly labelled Dashboard screenshot was not retained. Existing Support screenshots remain `docs/evidence/final-owner-qa-support-create-modal.png`, `docs/evidence/final-owner-qa-receptionist-support-1440.png`, and `docs/evidence/final-owner-qa-receptionist-support-detail-1440.png`.

### Runtime and remaining limits

- Browser/API runtime used the verified clone DB12, Hibernate `validate`, schedulers disabled, and mock payment settings. DB5 was not written to. The read-only DB5 snapshot found zero elapsed scheduled sessions at its capture time; the DB12 snapshot found 15. The DB12 scheduler was never run over those existing rows; the SQL integration test constrained candidate IDs to run-owned fixtures. Recheck DB5 immediately before a future scheduler-enabled startup because the count is time-sensitive.
- The task-owned backend/frontend listeners on ports 8081/5175 were stopped. Existing owner services were left untouched. Repository branch/HEAD remain `qa/team-integration-20261122` / `1f96272df08f156c5a84b74183f833de61defcf4`; no commit or push.
- **PASS_WITH_GAPS:** implementation and focused automated/database checks passed. Remaining browser evidence is the live claim flow, five-item pagination with more than five tickets, and long Attendance error geometry; the existing records did not provide safe suitable examples. No known defect or pending business decision remains.


## Final Internal Role UI Polish — 2026-11-27

### Changes

- **Coach/Admin Attendance:** wrapped each row's Save and History controls in a dedicated right-aligned vertical action group. Their right edges now align independently of label width; the existing full-width, learner-associated validation row is unchanged. No attendance rules, drafts, history, or save behavior changed.
- **Support refresh and messages:** header action buttons are non-shrinking and keep a single-line label. Conversation history now aligns grid content to the top and sizes rows to their content; each bubble uses a content-driven width capped at 90%/46rem, with unbroken text wrapping. Persisted sender IDs still determine viewer alignment. The thread remains the independently scrollable region.
- **Receptionist Support:** reply composition is shown only to Members, Admins, or the assigned staff user; an unassigned claimable request shows details and the existing “Nhận xử lý” action without reply controls. Management no longer has a separate bordered box or redundant heading/assignee. Reply is primary, lifecycle actions are compact secondary controls, and close remains destructive outlined. Existing resolved/reopen/reply-to-reopen and closed read-only behavior remain.
- **Feedback:** no shared toast component is available. Errors and success status now appear beside the relevant reply, claim/lifecycle, create-modal, or selected-ticket interaction; list-load errors remain with the list. Competing claim errors remain visible even if the refreshed ticket is no longer claimable.

Files changed in this pass: `frontend/src/pages/CoachAttendance.jsx`, `frontend/src/pages/StaffAttendance.jsx`, `frontend/src/pages/CoachAttendance.css`, `frontend/src/pages/SupportTickets.jsx`, `frontend/src/pages/SupportTickets.css`, `frontend/tests/coachAttendance.test.mjs`, and `frontend/tests/supportTickets.test.mjs`.

### Verification

- Focused Coach Attendance and Support tests: **20/20 passed**.
- Full frontend suite: **596/596 passed**, 0 failed or skipped.
- `npm.cmd run lint`: passed.
- `npm.cmd run build`: passed; the existing ~1,013 kB main bundle warning remains.
- `git diff --check`: passed; only existing line-ending conversion warnings were emitted.
- Rendered browser checks were **not run**: no existing frontend/backend listeners were active on the supported local ports, and this pass did not start services or create/mutate ticket fixtures. Thus computed bubble geometry and state screenshots remain unverified in a live authenticated browser. Presentation state contracts are covered by focused frontend tests.

No backend, scheduler, database, historical record, Membership, Renewal, payment, or business-rule changes were made. The current branch and HEAD were preserved; no commit or push occurred.

**Verdict: PASS_WITH_GAPS** — requested code and automated checks pass; live authenticated visual verification remains for owner QA.

## Attendance & Training Workflow Completion — 2026-11-27

### Implemented

- Initial attendance is now an explicit full-roster submission. The server derives the eligible roster, rejects missing, duplicate, unexpected, or invalid members, and commits the first submission atomically. Individual saves cannot create an incomplete initial record. Historical partial rosters can be completed without rewriting existing unchanged rows. EXCUSED remains readable as historical recorded non-participation, but cannot be newly submitted. Persisted completeness is derived separately from class-session lifecycle; future, empty-roster, and cancelled sessions are not attendance work.
- Coach Attendance now presents API-backed NOT_RECORDED/PARTIAL work queues and counts for assigned sessions, with roster navigation and refresh after official submission. Admin Attendance now navigates Course → Class → Session → Roster with search, sport/date/status/completeness filters, overlap-aware class date filtering, and 10-item pages. Admin/Coach Training uses Course → Class selection while preserving the four existing training workspace sections and the separate Admin PT supervision tab.
- Member attendance summaries include recorded and eligible elapsed counts, present and non-participation counts, historical EXCUSED detail, future/unrecorded separation, and provisional-rate messaging when records are incomplete. A zero-record state does not imply a 0% or 100% rate. Successful official ABSENT submissions and genuine status corrections publish member-scoped notifications after commit; unchanged retries and rolled-back writes do not publish.
- Support reply authorization now requires an unassigned request to be claimed first. An assigned Receptionist may reply only to their assigned ticket; Admin may participate in an already-assigned ticket without changing its owner; competing claims remain protected. The UI reflects role-based reply availability and author roles.
- Scoped Attendance toolbar sizing, Member Support metadata sizing, Admin Support header, and Admin sidebar typography were corrected. The sidebar root cause was selector drift: localized `aria-label` text no longer matched old English-specific CSS selectors. A stable Admin navigation class now owns the styles.

### Verification evidence

- Focused H2 backend regression: **35/35 passed** across Attendance (18), Training (9), Support (6), and class-session completion (2).
- Guarded DB12 SQL Server regressions: **4/4 passed** across Attendance/Training consistency (2) and Support claim concurrency (2). The attendance race produced correction HTTP 409 while result creation succeeded; committed attendance remained PRESENT with its result. Support claim concurrency had one winner and one claim audit. Test output and clone guard identified `LAPTOP-416VBDLK`, `SportCenterDemo_PTOutcome_8CF1`, DBID 12. Schedulers were disabled and payment configuration was mock.
- Full frontend suite: **598/598 passed**, no failures or skips. Lint passed. Production build passed; the existing ~1,025 kB main-bundle warning remains. `git diff --check` passed (Git emitted only existing line-ending conversion notices).
- Authenticated browser checks used Vite `127.0.0.1:5175` and the source Spring Boot backend `127.0.0.1:8081`, with frontend API traffic to that backend. Hibernate validation was enabled; DB12 identity was verified; all schedulers were disabled. Member, Coach, Receptionist, and Admin login paths were exercised. Member attendance showed 1/2 elapsed sessions recorded and a provisional rate. Coach showed the real incomplete queue and disabled official submission until a status was selected. Admin Course/Class/Session navigation, paging, roster, and separate PT supervision were inspected read-only. Support claim/reply was exercised with an exact run-owned synthetic ticket; Receptionist claimed and replied, then Admin replied without taking ownership. The run-owned ticket, messages, and claim audit were removed and protected support-table hashes matched their pre-run baselines.
- Browser visual checks also measured Member Support at 1280/1440/1920 CSS px without document overflow; the metadata remained intrinsic and message bubbles content-sized. Admin “Tổng quan” now computes to 11px, weight 750, uppercase, 0.88px letter spacing, matching section headings. Earlier Admin/Coach/Member attendance views were inspected at desktop widths; no existing historical attendance was mutated.
- Task-started backend/frontend processes on ports 8081/5175 were stopped. No owner service was stopped. No DB5 writes, migrations, commit, or push occurred.

### Gaps and disposition

- The initial official attendance submission was not performed against an existing historical roster in the browser because that would mutate non-run-owned records. Atomic submission, validation, and race behavior were verified by focused tests, including the clone-pinned integration cases.
- Browser verification did not exercise an Admin/Coach attendance correction with a long row error, a Member notification generated by a live browser action, or every Support lifecycle action. These are covered in backend/frontend regression tests where applicable; the available browser records were not safe or suitable for those writes.
- Read-only data exposed 15 already elapsed SCHEDULED sessions in DB12 during the prior recorded snapshot. The new completion scheduler was not run against pre-existing rows; its SQL integration fixture constrained eligible IDs to run-owned data. DB5 remained untouched. Recheck DB5 before a future scheduler-enabled deployment because that count is time-sensitive.
- No remaining business decision is pending. These results establish focused technical acceptance, not final release readiness.

**Current batch verdict: PASS_WITH_GAPS.** Implemented behavior and focused automated/database checks passed. Authenticated browser checks covered the available safe workflows; the mutation-sensitive and data-limited browser gaps above remain explicitly unverified.

## Final Owner QA Corrections: Attendance, Training, Support and Admin UI — 2026-11-27

### Implemented

- **Coach/Admin Attendance controls:** The initial atomic submission action is labeled “Gửi điểm danh”; “chính thức” wording and obsolete per-student initial-save actions are absent. The row action column is hidden when initial submission has no persisted row actions, but remains available for historical partial rows. Corrections continue to use “Lưu điều chỉnh” and retain history access. The empty roster row and row-specific error row span the actual visible columns. Disabled save controls now use readable foreground/background styling; the later save-all opacity rule no longer fades its label. Toolbar button height and alignment are shared. Session lists show one date plus one time range rather than a full timestamp followed by the same time again.
- **Coach Attendance queue and assigned sessions:** The summary contains only “Cần điểm danh” and “Đã điểm danh.” Its pending count and paginated work queue both include persisted `NOT_RECORDED` and historical `PARTIAL` sessions; partial entries retain the “Chưa hoàn tất” label and “Tiếp tục” action. Assigned-session completeness is presented independently of session lifecycle, so a completed session with complete attendance reads “Đã điểm danh.” Successful individual and batch corrections refresh the roster and session list.
- **Historical EXCUSED:** New selection remains restricted to PRESENT/ABSENT. Historical EXCUSED remains available for display and filtering. Zero “Có phép” count cards are hidden on Coach, Admin and Member attendance views; positive historical counts remain visible. The Member detail count now follows the same zero-hiding rule. No attendance calculation or stored record changed.
- **Support modal stacking:** The bell layer now uses the shared notification layer token and the Support create modal uses the shared higher modal token. This keeps the backdrop/modal above the topbar notification control without page-specific z-index escalation. Existing Escape, focus trap and focus restoration behavior remains.
- **Admin Attendance navigation/filter presentation:** Course search is “Tìm khóa học” with sport filter; class search is “Tìm lớp học” with “Từ ngày/Đến ngày”; session selection has only date range, session status and attendance completeness filters. Course/class/session filter layouts use two, three and four columns respectively on wide desktop, then collapse at narrower widths. Course-level sport selection no longer leaks into class/session results. Existing overlap checks use Offering start/end dates. `NOT_APPLICABLE` is produced for a cancelled session or an empty eligible roster; “Không cần điểm danh” accurately describes that existing value. Its API value and filter behavior are unchanged.
- **Admin/Coach Training navigation:** Existing course → class → workspace navigation uses the course/class search labels, course-only sport filter, class date filters, and back controls in the selection heading/workspace content area. The four training sections and separate Admin PT tab remain in place.
- **Admin header consistency:** Applied the accepted framed blue/white header to remaining ordinary Admin management views and added scoped overrides where prior page CSS would otherwise override the shared treatment. Actions and page-specific controls are retained.

### Admin route inventory

| Route/page | Result |
|---|---|
| `/admin` → `/staff/dashboard` and `/staff/dashboard` | Intentionally specialized Dashboard; redirect retained |
| `/admin/attendance`, `/staff/attendance` | Already consistent framed Attendance header |
| `/staff/consultations`, `/staff/support-tickets` | Already consistent framed staff headers |
| `/staff/register-member`, `/staff/members` | Updated Admin framed header; Receptionist member-list variant preserved |
| `/staff/sports`, `/staff/facilities` | Already consistent via `AdminSectionHeader` |
| `/staff/courses`, `/staff/courses/new`, `/staff/courses/:courseId/edit` | Updated |
| `/staff/courses/:courseId`, offering detail and class detail | Updated |
| `/staff/membership-plans`, `/staff/staff` | Updated |
| `/admin/membership-sales` | Updated Admin variant; Receptionist behavior preserved |
| `/staff/course-payments`, `/staff/pt-payments` | Updated Admin variants; Receptionist variant preserved; payment clock retained |
| `/admin/training` | Already has its framed Training header and separate PT supervision tab |
| `/admin/notifications/broadcast`, `/admin/audit-logs`, `/admin/reports` | Updated |
| `/admin/courses`, `/admin/classes`, `/admin/courses/:courseId...` | Redirects to the corresponding `/staff/courses` views; no duplicate header introduced |

### Verification and limits

- Focused frontend regressions: **25/25 passed** across Coach/Admin Attendance, Support modal layering, Admin route headers and filter presentation.
- Full frontend suite: **599/599 passed**, 0 failures, 0 skipped.
- Frontend lint: passed with no warnings after adding the selected-course filter dependency.
- Production build: passed (201 modules). Existing large bundle warning remains: main JS chunk approximately **1,026.69 kB** minified.
- `git diff --check`: passed; Git emitted only the existing LF/CRLF working-copy conversion warnings.
- **Authenticated browser verification: BLOCKED.** Local ports 5173, 5175, 8080 and 8081 were all closed. No owner service was started or stopped; no login or rendered screenshot was performed in this batch. Thus the corrected geometry and state appearance still need the owner's authenticated browser retest. Browser verification must use the existing approved QA clone runtime, not DB5.
- `NOT_APPLICABLE` meaning was confirmed from `AttendanceService.completeness()`: cancelled session or eligible roster size zero. Future sessions are `NOT_YET_ELIGIBLE`; elapsed sessions with zero recorded rows are `NOT_RECORDED`.
- No database connection or writes were made; historical records and DB5/DB12 data were not touched. No migration, commit or push occurred.
- Git branch/HEAD preserved: `qa/team-integration-20261122` / `1f96272df08f156c5a84b74183f833de61defcf4`.

**Batch verdict: PASS_WITH_GAPS.** Source corrections and automated checks pass. Authenticated rendered browser acceptance remains blocked until a compatible existing QA runtime is available; Admin route framing was source/CSS audited, not visually measured live.

## Final Owner Visual QA Corrections — 2026-11-27

This section supersedes the earlier visual-verification limitation for the specific Attendance, Training navigation, and Sports/Facilities header items below. It does not supersede unrelated earlier acceptance results.

### Corrections and root causes

- **Attendance controls:** The Coach toolbar's shared action group now centers its children and sets a common 42px border-box height, zero margin, and identical padding/line-height. The original visible misalignment was parent alignment/intrinsic control geometry, rather than different declared heights. On an existing historical partial session, live bounding rectangles at both 1366px and 1440px showed “Đánh dấu tất cả có mặt”, “Xuất CSV”, and “Gửi điểm danh” all at top 668px, bottom 710px, height 42px. The disabled initial-submit state also retained readable contrast and the same geometry. No submission button was clicked.
- **Attendance status:** The persisted `NOT_APPLICABLE` value alone does not distinguish future sessions from elapsed empty rosters: backend completeness returns that value for cancelled sessions or an empty eligible roster, while future sessions can also have an empty roster. A read-model `attendanceTimeEligible` boolean now carries the server's existing start-time eligibility decision (Vietnam business clock) without changing completeness enums or queue membership. Shared presentation precedence is: cancelled (lifecycle badge only, no duplicate attendance text); not yet eligible (“Chưa đến giờ điểm danh”); eligible empty roster (“Không có học viên”); no recorded outcomes (“Chưa điểm danh”); partial (“Chưa hoàn tất”); complete (“Đã điểm danh”). Cancellation and future precedence are covered by frontend and backend regression tests. Counts, historical EXCUSED rendering, completeness values, and work-queue rules remain unchanged.
- **Admin filter geometry:** Browser inspection found the four session filter controls shared grid widths but date inputs rendered 44px high while selects rendered 42px. A scoped rule sets all Admin Attendance selection controls to 44px border-box height. At 1440px, all four controls now share y=515.3px and height 44px, with equal 234.8px cells. The class filter row renders its search and date controls in the requested 2:1:1 grid with inputs filling each cell.
- **Admin selection labels:** The course stage now says “Chọn khóa học”, uses the requested description, and shows the actual course count (8 in the inspected DB12 data). The class stage says “Chọn lớp học”, identifies the selected course, and shows the actual class count. Session selection visibly separates “Lớp:” and “Khóa:” and omits the numeric class identifier. After browser capture, source and regression coverage were tightened to avoid appending the first session date redundantly to the selected class name. That final text-only adjustment was not re-rendered because the task-owned services had already been stopped; the saved class/session screenshots show the prior heading suffix. Cancellation remains the sole badge for cancelled sessions; future sessions visibly show “Chưa đến giờ điểm danh”.
- **Training navigation:** Admin course and class selection and the class workspace were rendered. The workspace keeps its selected class/course, back action, summary, and four training tabs inside the framed content area. The inspected Admin-only class had no plan; the page showed its empty-plan state. Its plan endpoint returned HTTP 404 for that absent plan, which was handled as empty UI but produced one browser console error; this is reported as a data/runtime limitation and was not changed in this visual-only scope.
- **Sports and Facilities headers:** The issue was in rendered CSS, not the `AdminSectionHeader` component name: those routes previously had no visible framed panel treatment. The shared component CSS now provides the blue-white gradient, 1px border, 18px radius and 30px padding. Both routes were visually inspected at 1440px. Computed header geometry was x=304, y=96, width=1073px, height about 157px, with the expected border, radius and padding.
- **Support actions:** Existing implementation groups the claim/reply/lifecycle actions in a compact right-aligned row, using the recovered compact 176px width and 40px height. The previous source-level claim/resolve sizing issue is corrected. This batch did not obtain authenticated live screenshots of unassigned and assigned Support states; the current rendered browser pass focused on the requested Attendance hierarchy and management headers. Support state-specific visual confirmation remains for owner retest.

### Evidence and verification

- Frontend focused suite: **28/28 passed**. Full suite: **601/601 passed**, 0 failures, 0 skipped. Lint passed. Production build passed; the existing ~1,028 kB main JavaScript chunk warning remains.
- Backend `AttendanceIntegrationTest` on the isolated H2 test profile: **19/19 passed**, including future-empty, future-with-roster, cancelled and elapsed-empty summary cases. No SQL Server business writes, migration, or DB5 access occurred in this batch.
- `git diff --check` passed; Git printed existing LF/CRLF conversion notices.
- Authenticated browser used the source frontend at `localhost:5173` and source Spring application at `localhost:8080`. The backend's effective JDBC connection was verified as `LAPTOP-416VBDLK / SportCenterDemo_PTOutcome_8CF1 / DBID 12`; Hibernate was `validate`; global, Renewal and PT fallback schedulers were disabled; payment provider was mock. Browser actions were read-only apart from unsaved client-side attendance selections used to reveal the submit-control state; no save/submission action was invoked.
- Captured rendered evidence (CSS-pixel screenshots): `.playwright-mcp/coach-attendance-controls-1366.png`, `.playwright-mcp/coach-attendance-controls-1440.png`, `.playwright-mcp/admin-attendance-course-1366.png`, `.playwright-mcp/admin-attendance-class-1366.png`, `.playwright-mcp/admin-attendance-session-1366.png`, `.playwright-mcp/admin-attendance-session-1440-aligned.png`, `.playwright-mcp/admin-training-course-1366.png`, `.playwright-mcp/admin-training-class-1366.png`, `.playwright-mcp/admin-sports-framed-1440.png`, and `.playwright-mcp/admin-facilities-framed-1440.png`. The Coach controls and Sports header screenshots were opened and visually inspected; live DOM bounding rectangles were measured for the Coach controls and Admin filters.
- Task-owned Vite and Spring processes were stopped. Ports 5173 and 8080 were confirmed closed afterward. Other owner services were not stopped. Branch/HEAD remain `qa/team-integration-20261122` / `1f96272df08f156c5a84b74183f833de61defcf4`; no commit or push occurred.

### Changed files in this correction pass

`backend/src/main/java/group5/example/sportCenter/dto/AttendanceSessionResponse.java`, `backend/src/main/java/group5/example/sportCenter/service/AttendanceService.java`, `backend/src/test/java/group5/example/sportCenter/service/AttendanceIntegrationTest.java`, `frontend/src/attendanceStatusPresentation.js`, `frontend/src/pages/CoachAttendance.jsx`, `frontend/src/pages/StaffAttendance.jsx`, `frontend/src/pages/CoachAttendance.css`, `frontend/src/pages/StaffAttendance.css`, `frontend/src/components/AdminSectionHeader.css`, `frontend/src/pages/SupportTickets.jsx`, `frontend/src/pages/SupportTickets.css`, `frontend/src/pages/CoachTraining.jsx`, `frontend/src/pages/Training.css`, and focused frontend regression tests for attendance status, Coach/Staff Attendance, Support, Training hierarchy, and Admin headers.

**Verdict: PASS_WITH_GAPS.** The corrected Attendance labels and alignment, Admin hierarchy/filter geometry, Training navigation frames, and Sports/Facilities header frames passed automated and rendered checks on DB12. The Support state-specific buttons were not browser-measured in this pass, and the inspected empty Training plan endpoint emitted one handled 404 console error; both are explicitly left for owner retest/data-specific follow-up.

## Final UI Corrections and Weekly General Training Plan Design — 2026-11-27

### Part A — implemented UI corrections

- **A1 Coach Attendance:** The search label is a two-row grid (caption plus field), while the action group occupies one row. The parent grid aligns its children to the field baseline. I set the input and all three actions to `44px`, `box-sizing: border-box`, with zero control margins. This makes the field and actions share both top and bottom edges while the caption remains above the field. The Admin roster filter and action controls use the same 44px edge alignment.
- **A2 Training navigation:** Admin and Coach class-selection views retain the selected course heading and class count on the right, followed by a divider and a left-aligned back button. The selected-class workspace places its left-aligned “Quay lại danh sách lớp” below the heading divider and keeps the four workspace tabs and content together in the existing frame. Existing selected course/class, filters, pagination, and tab behavior remain intact.
- **A3 Sports and Facilities:** `AdminSectionHeader` is used only on these two pages. I removed its redundant eyebrow and eyebrow icon and removed the two icon props. The framed title, description, counters, and Facilities refresh action remain. The one SVG visible in the Facilities header is the retained refresh control; it is not the removed eyebrow icon.

### Rendered browser evidence

The existing authenticated Admin and Coach contexts were used for read-only page navigation. At both 1366px and 1440px, Coach Attendance’s search field and each of its three action buttons measured `44px` high with identical viewport-relative `top` and `bottom` values. At both widths, the Admin roster filter and its three actions also measured `44px` with identical top/bottom edges. Coach values at both widths were top `679.28px`, bottom `723.28px`; Admin values were top `814.17px`, bottom `858.17px` in the captured roster.

The Admin and Coach Training class-selection and selected-workspace screens rendered with their back controls below the heading divider and aligned left. Sports and Facilities rendered the blue/white framed header with zero eyebrow elements; the Facilities refresh icon remained visible. Screenshots were visually inspected:

- `.playwright-mcp/coach-attendance-toolbar-1366.png`
- `.playwright-mcp/coach-attendance-toolbar-1440.png`
- `.playwright-mcp/admin-attendance-toolbar-1366.png`
- `.playwright-mcp/admin-attendance-toolbar-1440.png`
- `.playwright-mcp/coach-training-class-selection-1366.png`
- `.playwright-mcp/coach-training-workspace-1366.png`
- `.playwright-mcp/admin-training-class-selection-1366.png`
- `.playwright-mcp/admin-training-workspace-1366.png`
- `.playwright-mcp/admin-sports-header-1366.png`
- `.playwright-mcp/admin-facilities-header-1366.png`

The existing browser API responded at `localhost:8080`. A read-only authenticated `GET /api/training/classes/2/plan` returned plan 1, class 2, with one exercise, matching the independently verified DB12 aggregate row. The direct SQL check verified `LAPTOP-416VBDLK / SportCenterDemo_PTOutcome_8CF1 / DBID 12 / ONLINE` before reading plan data. The process itself is outside this shell’s process/port view; effective Hibernate and scheduler flags were not independently re-read in this batch. A source backend start was attempted with `validate` and all schedulers disabled, but Maven could not start because the shared repository lock for Spring Boot 4.1.1 could not be opened. No cache lock was removed and no process was terminated. The rendered checks therefore used the already-running authenticated QA browser/runtime, with its dataset cross-checked against DB12; the runtime flags remain a verification gap.

### Part A verification and changed files

- Focused frontend tests: **21/21 passed**.
- Full frontend suite: **602/602 passed**, no failures or skipped tests.
- Frontend lint: passed.
- Production build: passed; Vite reported the existing main JavaScript chunk warning at approximately **1,027.56 kB** minified.
- `git diff --check`: passed; Git emitted the existing LF/CRLF working-copy notices.
- UI implementation/test files in this correction scope: `frontend/src/pages/CoachAttendance.css`, `frontend/src/pages/StaffAttendance.css`, `frontend/src/pages/CoachTraining.jsx`, `frontend/src/pages/Training.css`, `frontend/src/components/AdminSectionHeader.jsx`, `frontend/src/components/AdminSectionHeader.css`, `frontend/src/pages/SportManagement.jsx`, `frontend/src/pages/FacilityManagement.jsx`, `frontend/tests/coachAttendance.test.mjs`, `frontend/tests/staffAttendance.test.mjs`, `frontend/tests/trainingHierarchy.test.mjs`, and `frontend/tests/adminSectionHeader.test.mjs`.
- No task-owned application process was successfully started. Local shell ports 5173 and 8080 were closed at inspection; no owner process was stopped. No database was written, no migration was run, and no data on DB5 was accessed.

### Part B — current general-plan behavior and DB12 data

The current general model is one `TrainingPlan` per `CourseClass`, enforced by a unique class foreign key. It stores an overall goal (500 characters), description (2,000), updater, and create/update timestamps. `TrainingExercise` belongs to that plan and stores name, sets, reps, duration, notes, and `sortOrder`; reads order exercises by `sortOrder`, then ID. There is no week relation or plan publication state. The current plan response contains a single flat exercise list, and the upsert request changes only the overall goal and description.

The assigned Coach can create/edit the general plan and its exercises. Admin can inspect the workspace in read-only mode. A Member can read a plan through the Member training response only for a class with a `CONFIRMED` registration. Existing saves are visible immediately; no separate publish step or general-plan revision history exists. The class itself has no start/end date; its `CourseOffering` supplies required start/end dates.

The read-only DB12 query found **4 general plans and 5 existing exercises**. Per-plan/class/offering counts were: plan 1 / class 2 / 2026-11-15–2026-12-15 / 1 exercise; plan 2 / class 1 / the same offering dates / 1; plan 1002 / class 2010 / 2026-11-12–2026-12-10 / 1; plan 1003 / class 2006 / 2026-11-23–2026-12-07 / 2. The clone also contains 4 individual training plans, 8 personal-plan revisions, 9 weekly sections, and 14 personal-plan exercises. These are aggregate/source identifiers only; no personal account data was read or reported.

The personal-plan implementation is a useful UI and validation reference, but it is a different ownership model: individual plans are per Member and class, content is stored as append-only revisions, weeks have unique numbers within a revision (currently 1–52), and exercises are ordered within a week. The general plan is shared by all confirmed Members of a class, so it should not be coupled to a Member’s individual-plan revisions.

### Proposed weekly general-plan design (not implemented)

1. Keep `training_plan` as the one-per-class parent with its existing overall goal and description.
2. Add `training_plan_week` with `plan_id`, unique `(plan_id, week_number)`, title, weekly goal, description, publication timestamp/state, and create/update actor/timestamps. Use positive week numbers; do not impose the personal-plan 52-week cap without a business reason.
3. Add nullable `week_id` to existing `training_exercise`. `NULL` preserves current plan-level legacy exercises; a non-null value places the same exercise record in a weekly section. Retain `plan_id` for compatibility and add a composite constraint/index so a weekly exercise cannot point to a week owned by a different plan. Order exercises within a week by `sort_order`, then ID. This avoids a second exercise schema and preserves current rows and API behavior.
4. Extend the plan read DTO with weekly sections and their ordered exercises while retaining the current top-level legacy `exercises` field. Keep the existing endpoint usable. Add focused week edit/publish operations under the current class-plan authorization. The assigned Coach edits; Admin supervises read-only; Members with confirmed registration receive published weeks only. A Member should see every published week, including future weeks, rather than only the current week.
5. Adapt `CoachTraining` to edit week title/goal/description/exercises and publish state. Adapt the Admin workspace as read-only. Adapt `MemberTraining` to show the overall plan, any preserved legacy/unassigned exercises, and all published weekly sections.

**Week calendar rule recommendation:** derive each section’s date range from fixed seven-day blocks anchored to `CourseOffering.startDate` (week 1 starts on the offering start date; weeks do not follow individual session rows). Clamp the displayed final range to the offering end date when appropriate. Cancelled or rescheduled sessions do not renumber curriculum weeks. Extending or shortening the offering must not delete weekly content; retained weeks remain attached to their week number. The owner should decide whether changing an offering start date re-anchors displayed week dates or freezes the original curriculum anchor. Re-anchoring is simpler; freezing protects historical date labels but requires storing an anchor date.

**Existing-data migration:** use an additive migration only after approval. Create weekly rows without assigning inferred week numbers to existing flat exercises; leave all current `training_exercise.week_id` values NULL. Keep those exercises readable as legacy plan-level content. Coaches can move or recreate them within weeks through an explicit workflow later; do not automatically classify them as week 1. Preserve plan IDs, goals, descriptions, exercise IDs/order, and audit records. Run a preflight that records row counts and verifies every existing exercise remains attached to its original plan; then run Hibernate validation against the migrated clone before deployment. No migration or data rewrite was performed here.

**Revision history:** the general model currently has change audit events but no reconstructable content snapshots. Revision history can remain outside the first weekly-plan implementation if the owner accepts that edits overwrite the currently published content. If Members or staff must reconstruct prior published plans, immutable revisions are required; the current audit log alone cannot restore old field values. This is an owner scope decision before implementation.

**Likely implementation/test files after approval:** add the week entity and repository; update `TrainingExercise`, training request/response DTOs, `TrainingService`, and `TrainingController`; extend focused `TrainingIntegrationTest` and clone migration preflight coverage; update `CoachTraining.jsx`/`Training.css`, the Admin read-only training workspace, and `MemberTraining.jsx`/`MemberTraining.css`; add frontend editor/render tests for ordering, draft/published visibility, future weeks, and the legacy exercise list. Expected schema impact is additive: one weekly table, a nullable exercise `week_id` column with ownership-safe constraints/indexes, and no Member-plan or ClassSession foreign-key coupling.

**Unresolved owner decisions:** whether offering start-date edits re-anchor existing week date labels; whether published weekly content needs immutable revision/rollback history in the first release; and whether Coaches need an explicit legacy-exercise-to-week reassignment action or may initially leave existing exercises in the legacy section.

**Part B status: design complete, implementation intentionally deferred pending owner approval.**

### Final state

Branch and HEAD remain `qa/team-integration-20261122` / `1f96272df08f156c5a84b74183f833de61defcf4`. Existing dirty and untracked work was preserved. No commit, push, migration, database write, scheduler activation, or historical-data change occurred. Overall verdict for this task: **PASS_WITH_GAPS** because the requested rendered UI checks and data/schema design were completed, but this turn could not independently inspect effective runtime scheduler/Hibernate flags due to the inaccessible shared Maven lock and remote browser process boundary.

## Superseding update — Training Plan Redesign implementation (2026-11-27)

The owner approved the General weekly plan policy and implementation. The design-only status above is superseded: the shared focused week editor, General weekly sections, derived Offering calendar ranges, explicit legacy exercise reassignment, transactional whole-plan save, stale-save conflict detection, and Member/Admin read-only weekly views are now implemented. The additive schema migration was applied to DB12 only after a verified COPY_ONLY/CHECKSUM backup. Existing General plan exercises remain unassigned; no Individual plan schema or revision behavior changed. Full implementation, migration, test and browser-gap evidence is in [`TRAINING_PLAN_REDESIGN_ACCEPTANCE.md`](TRAINING_PLAN_REDESIGN_ACCEPTANCE.md).

Focused backend H2 checks passed: `TrainingIntegrationTest` 10/10 and `IndividualTrainingPlanIntegrationTest` 13/13. The explicit DB12 read-only Hibernate validation passed (38/38 mapped entities). Frontend tests passed 605/605; lint and production build passed. DB12 SQL constraints rejected duplicate/nonpositive weeks and cross-plan exercise references in a transaction that was rolled back. Browser screenshots and authenticated Coach/Admin/Member edits remain pending because the saved role sessions expired and redirected to login after starting the source-compatible DB12 runtime. Final verdict for this batch: **PASS_WITH_GAPS**. No DB5 writes, commit, or push occurred.
