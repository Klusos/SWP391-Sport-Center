# Pre-Demo QA Report

**Audit date:** 2026-11-19
**Final verification:** 2026-11-19

**Scope:** Read-only profile capability audit and desktop-first browser QA. No application source, backend, or database changes were made.

## A. Member profile editing capability

### Field matrix

| Field | Current Member UI | Backend/API support | Authorization | Recommendation |
|---|---|---|---|---|
| Full name | Read-only account card | `UpdateUserRequest` and `UserService.updateUser` support updates | `PUT /api/users/{id}` is Admin-only; no Member self-edit endpoint | Keep read-only for demo. A future self-service feature needs an owner-approved, narrowly scoped endpoint/DTO. |
| Phone | Read-only account card | Supported by the Admin account-update DTO/service | Admin-only account endpoint | Same as full name. |
| Date of birth | Read-only account card | Supported by the Admin account-update DTO/service | Admin-only account endpoint | Same as full name. |
| Gender | Read-only when a value is present; no card appeared for the audited account | Supported by the Admin account-update DTO/service | Admin-only account endpoint | The audited account has no gender value. Confirm/prepare the demo account data; a placeholder is an owner-facing presentation decision. |
| Email | Read-only account card | Supported by the Admin account-update DTO/service, including uniqueness validation | Admin-only account endpoint; no Member self-edit endpoint | Keep read-only for demo. Owner must decide email-change/verification policy before future work. |
| Height | Editable in Member Profile | Supported by `POST/PUT /api/member-profiles/me`; request range 50–250 cm | Member can create/update their own profile | Available in current UI. |
| Weight | Editable in Member Profile | Supported by `POST/PUT /api/member-profiles/me`; request range 10–500 kg | Member can create/update their own profile | Available in current UI. |
| Training goal | Editable textarea in Member Profile | Supported by `POST/PUT /api/member-profiles/me`; maximum 1,000 characters | Member can create/update their own profile | Available in current UI. |

The Member UI reads account data through `GET /api/users/me` and only writes the physical profile through `/api/member-profiles/me`. The profile DTO contains height, weight, and goal, not account identity fields. `PUT /api/users/{id}` must not be called from the Member UI: the backend protects it as an Admin operation and its DTO also includes role/status fields. Backend authorization tests cover the Member denial and Admin access for account updates.

**Smallest safe future solution:** after owner approval, add a distinct Member self-service account endpoint tied to the authenticated subject and an allowlisted DTO for the approved fields. Exclude role/status; define email verification and audit expectations first. This is not a pre-demo change. The current read-only account display and physical-profile form are safe to demonstrate.

### Browser observation

`/member/profile` rendered at 1366×768, 1440×900, and 1920×1080. Account values were presented as non-editable cards; height, weight, and goal controls were enabled. There was no document horizontal overflow or overlap. This account had no saved physical profile: `GET /api/member-profiles/me` returned 404 and the page showed its “Tạo hồ sơ thể chất” state. The page handled that response without a crash. Final read-only inspection of `GET /api/users/me` returned HTTP 200 with the `gender` property present but `null`. The frontend filters empty account fields, so the missing gender card is explained by missing account data, not a rendering defect. No data was changed.

Evidence screenshot (local, ignored test output): `frontend/test-results/pre-demo/member/profile-1440.png` (other viewport captures are `profile-1366.png` and `profile-1920.png`). These local captures can contain account data and are not tracked.

## B. Route coverage

Routes were taken from `frontend/src/App.jsx`, including aliases/redirects. There are **68 declared route patterns** in the inventory, counting the wildcard fallback. A concrete URL can exercise a dynamic pattern, but does not establish every possible record state.

| Role | Discovered patterns | Rendered in browser | Source-only / blocked / not tested |
|---|---:|---|---|
| Public / Guest | 11 | 3 patterns exercised: `/`, `/bo-mon`, `/bo-mon/1` | 8 source-only or unvisited: public redirect/detail variants, facilities, login, and fallback. `/bo-mon` showed seven cards with all seven images loaded. |
| Admin | 29 accessible patterns (22 Admin + 7 shared staff) | 28 concrete URLs at 1440×900, including route aliases and redirect destinations | `/staff` base alias not separately exercised. Complex write actions were intentionally not submitted. |
| Receptionist | 13 accessible patterns (6 Receptionist + 7 shared staff) | 11 role-authorized paths rendered; two redirect paths were exercised. An Admin-only path redirected to the Receptionist dashboard. | `/staff` and `/staff/attendance` were not rendered under Receptionist authorization. |
| Member | 15 | All 15 route entries rendered at 1440×900, including `/member/classes` and `/member` redirects; dynamic examples used ID `1`. | Alternate record states and write actions were not tested. |
| Coach | 7 | All 7 routes rendered at 1440×900, including `/coach` redirect; Attendance regression also exercised at 1366×768, 1440×900, and 1920×1080 | No route blocked. Other Coach routes were visually checked at 1440×900 only; detailed interactions beyond Attendance were not exhaustively tested. |

This is **64 concrete browser URL visits** (3 Public, 28 Admin, 11 Receptionist-authorized, 15 Member, and 7 Coach), including aliases, redirects, and repeated route instances. It is not full interaction-state coverage. The full route sweep was primarily at 1440×900; Coach Attendance regression also covered all three primary desktop viewports.

### Route inventory by role

- **Public:** `/`, `/landing-preview`, `/bo-mon`, `/bo-mon/:sportId`, `/khoa-hoc/:courseId`, `/huan-luyen-vien`, `/co-so-vat-chat`, `/tuyen-sinh`, `/membership-plans`, `/login`, `*`.
- **Shared staff routes:** `/staff`, `/staff/attendance`, `/staff/dashboard`, `/staff/register-member`, `/staff/consultations`, `/staff/support-tickets`, `/staff/members`.
- **Receptionist-specific:** `/receptionist/membership-sales`, `/receptionist/course-payments`, `/receptionist/pt-payments`, `/receptionist/course-registration`, `/receptionist/attendance` (redirect), `/receptionist/support` (redirect).
- **Admin-specific:** `/staff/membership-plans`, `/admin/attendance`, `/staff/staff`, `/staff/sports`, `/staff/facilities`, `/staff/courses`, `/staff/courses/new`, `/staff/courses/:courseId/edit`, `/staff/courses/:courseId`, `/staff/courses/:courseId/offerings/:offeringId`, `/staff/courses/:courseId/offerings/:offeringId/classes/:classId`, `/admin/notifications/broadcast`, `/admin/audit-logs`, `/admin/training`, `/admin/membership-sales`, `/admin`, `/admin/courses`, `/admin/classes`, `/admin/reports`, `/admin/courses/:courseId`, `/admin/courses/:courseId/offerings/:offeringId`, `/admin/courses/:courseId/offerings/:offeringId/classes/:classId`.
- **Member:** `/member`, `/member/dashboard`, `/member/attendance`, `/member/training`, `/member/training/:classId`, `/member/pt-booking`, `/member/levels-assessments`, `/member/courses`, `/member/courses/:courseId`, `/member/course-checkout/:registrationId`, `/member/support-tickets`, `/member/membership`, `/member/schedule`, `/member/profile`, `/member/classes` (redirect).
- **Coach:** `/coach`, `/coach/dashboard`, `/coach/schedule`, `/coach/pt-sessions`, `/coach/skill-assessments`, `/coach/attendance`, `/coach/training`.

## C. Findings

### Confirmed

| ID | Role / route | Severity / type | Evidence and reproduction | Root cause / recommendation |
|---|---|---|---|---|
| F-01 | Public / `/` (also logged during `/bo-mon`) | **P3 — Visual/asset** | Browser console reports `GET /favicon.ico` as 404. Reproduced in the guest Playwright context. No page crash or layout impact observed. Console evidence is in the ignored `.playwright-mcp/console-2026-11-19T02-57-22-672Z.log`. | The referenced favicon resource is unavailable at that path; exact HTML/static-file cause was not established. Defer to a small asset/reference correction after the demo. |

### Data-dependent observation (not a confirmed defect)

| ID | Role / route | Severity / type | Evidence and reproduction | Root cause / recommendation |
|---|---|---|---|---|
| O-01 | Member / `/member/profile` | **Not a defect — missing account data** | Read-only `GET /api/users/me` returned HTTP 200 with `gender: null`; the profile page omitted that empty field at all three desktop sizes. | Frontend behavior matches its populated-fields-only rendering. Do not change records. If the demo should show an explicit “not provided” value, obtain owner approval for that presentation change. |

No P0, P1, or P2 defect was confirmed in accessible routes. One P3 favicon issue remains. This does not mean every interaction or route state passed.

### Visual and interaction observations

- **Public `/bo-mon`:** At 1366×768 and 1920×1080, seven sport links rendered, seven images reported successfully loaded, and document width stayed within the viewport. Badminton opened `/bo-mon/1`; the detail page rendered without horizontal overflow. Public home and discipline pages were visually inspected in the fresh guest context. No broken images were observed.
- **Admin:** 28 route URLs rendered at 1440×900 with a visible main region and no document horizontal overflow. Dashboard, Members, Attendance, Courses, and Reports screenshots were captured. The selected-route scan was rapid: some pending GETs were aborted when the next page was navigated to; these are scan-induced cancellations, not confirmed application failures. The course-list screenshot was captured during its loading state and is not evidence of a persistent loading defect.
- **Receptionist:** Membership Sales, Course Payments, PT Payments, and Course Registration screenshots were inspected at 1440×900. The payment/refund queues were empty and presented explicit empty-state text; no refund/payment action was triggered. Course Registration showed its sequential member and course/class sections. No horizontal overflow was measured in the route scan.
- **Member:** All 15 route entries rendered at 1440×900 without document horizontal overflow. Dashboard, membership, courses, support tickets, and profile were included in the route sweep; Profile was also checked at all three primary desktop viewports. During fast navigation, one `GET /api/support-tickets/me` request was aborted as the next route loaded. No non-GET API request was observed. Profile’s `GET /api/member-profiles/me` 404 corresponds to the no-profile state described above.
- **Coach:** All seven route patterns rendered at 1440×900 with no document horizontal overflow. Dashboard data replaced its loading state after approximately 1.5 seconds; subsequent API responses were HTTP 200. Schedule showed the assigned week, PT Sessions showed assigned/confirmed counts and a session detail action, Skill Assessments showed an empty pending state and a populated history tab, and Training showed the assigned class and its existing multi-step editor. Screenshots are under `frontend/test-results/pre-demo/final-coach/`. No save, delete, attendance update, or other write action was submitted. The Attendance E2E test covered the 2-session and 10+ session class flows at all three desktop widths; it verified 8 sessions per page, detail/back navigation, focus/context restoration, and visible roster controls. All three tests passed with zero captured app console errors, page errors, failed API requests, or failed API responses.

Local screenshots are under the ignored `frontend/test-results/pre-demo/` directory; they are local evidence, not tracked deliverables. The public MCP snapshots/logs under `.playwright-mcp/` are also ignored.

## D. Demo readiness

- **Safe to demonstrate based on this run:** Public landing and Public Disciplines; Admin dashboard, member list, attendance initial view, course list (allow its data to finish loading), and reports; Receptionist Membership Sales, payment queues, and Course Registration; Member dashboard, Membership, Courses, Support Tickets, and Profile.
- **Use with caution:** Receptionist payment/refund pages currently showed empty queues, so the demo should not depend on a populated transaction or refund. Member Profile showed the physical-profile creation form and had no gender value for the audited account. Verify the intended demo account/data state before presenting those details.
- **Safe to demonstrate after routine data preparation:** Coach Dashboard, Schedule, PT Sessions, Skill Assessments, Training, and Attendance. Attendance passed the focused browser regression at the three desktop sizes. Skill Assessments currently has no pending items; it has existing history. Training exposes edit controls, so only use the existing read-only views during the demo unless the workflow calls for a change.
- **Authentication:** ADMIN, RECEPTIONIST, MEMBER, and refreshed COACH saved sessions validated. No credentials were requested, exposed, or entered by the audit.

## E. Verification and remaining gaps

- `npm.cmd test` in `frontend/`: **523 passed, 0 failed, 0 skipped** in the preceding audit; not rerun in this final verification.
- `npm.cmd run auth:check -- --role COACH --browser msedge`: **passed**; the refreshed session validated against the read-only Coach dashboard.
- With `$env:PLAYWRIGHT_BROWSER = "msedge"`, `npm.cmd run test:e2e -- e2e/coach-attendance-layout.spec.js`: **3 passed** (1366, 1440, 1920). The test selected a class with 2 sessions and one with at least 10 sessions, checked 8-session pagination and second-page context restoration, opened details, and made no attendance writes. Captured diagnostics were empty.
- `npm.cmd run lint`: **passed** after the Coach Attendance E2E spec explicitly imported `Buffer` from `node:buffer`; this is Node.js test-runner code used to attach diagnostic JSON, not browser code.
- `npm.cmd run build`: **passed**. Vite emitted its existing warning that the main minified chunk exceeds 500 kB.
- Browser: Playwright with Microsoft Edge for role-state scans and Coach E2E; Playwright MCP guest context for Public pages. App reads only; no payment, registration, attendance, refund, profile, or account writes were performed.
- Browser console: confirmed `favicon.ico` 404. Coach route scans also emitted a generic `ERR_FAILED` console message when the external Kaspersky-injected script was blocked in the isolated test context; the focused E2E diagnostics had no app console errors. No page-level JavaScript exception was observed. Rapid route sweeps aborted one Member Support GET and several Admin report/support GETs; those cancellations occurred on navigation. The Profile API 404 is the handled no-profile state. No other failed application API response was confirmed.
- Known environment limitation: Microsoft Edge injects a Kaspersky script that can stall page parsing. Authenticated Playwright contexts blocked only that external script request locally for the test context; no app authentication or API checks were bypassed. Public pages worked in a fresh guest context.
- Interaction coverage was selective/read-only. Coach Attendance received the focused interaction test; filters, modals, and populated/error states on other Coach routes were not exhaustively exercised. Most unvisited Public routes remain browser-unverified.

### Recommended next actions, ordered by demo impact

1. Confirm the selected Member demo account has the intended profile data; its gender is currently `null` and its physical profile is not created.
2. After the demo, fix the favicon 404 with a focused static asset/reference change.
3. For post-demo assurance, exercise representative filters, detail dialogs, and populated/error states for unvisited routes and interactions.

## F. Final recommendation

**GO WITH CAVEATS.** No P0 or P1 UI defect was found, and Coach Attendance passed the focused browser regression at all three primary desktop widths. The Member gender omission is explained by `gender: null` in the read-only API response and is not a rendering bug. Remaining caveats are the P3 favicon 404, empty Receptionist payment/refund queues, and untested interaction states on many routes. The production build passed with a large-chunk warning. Lint and unit tests now pass. The only test-code change was the explicit Node `Buffer` import; no application, backend, or database changes were made.
