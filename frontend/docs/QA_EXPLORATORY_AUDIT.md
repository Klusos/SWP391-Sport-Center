# Exploratory Frontend UI/UX QA

Audit date: 2026-11-18
Status: partial; Coach issue fixed and browser-verified; Admin information-density finding remains under investigation
Scope: source/history review and read-only Playwright checks; current change is limited to the confirmed Coach Attendance layout issue

## Evidence labels

- **Browser reproduced** — directly exercised in the running browser.
- **Source confirmed** — current source or test demonstrates the condition; this is not a visual browser result.
- **Test confirmed** — an automated test exercised the stated behavior.
- **Suspected** — plausible from source, but evidence is incomplete.
- **Blocked / Not verified** — could not safely exercise in the browser.

## Route inventory and interaction states

Route patterns below come from `src/App.jsx`; parameterized routes are shown once. Redirect aliases are retained so QA does not mistake them for separate page implementations.

| Role | Implemented route patterns | Page owner / shared implementation | Important states to inspect |
|---|---|---|---|
| Public | `/`, `/landing-preview` (redirect), `/bo-mon`, `/bo-mon/:sportId`, `/khoa-hoc/:courseId`, `/huan-luyen-vien`, `/co-so-vat-chat`, `/tuyen-sinh`, `/membership-plans` | `LandingPage`, `PublicLayout`, `Disciplines` (list and detail), `CourseDetail`, `Facilities`; section aliases return to the Landing page | Header/nav and anchors; discipline cards and image loads; sport detail and course list; course details and enrollment CTA; theme toggle; consultation form; scroll-to-top. `/khoa-hoc/:courseId` redirects a Member to `/member/courses/:courseId`. |
| Authentication | `/login`, `*` (redirect to `/`) | `Login`, App fallback | Required-field validation, login error/session-expired feedback, redirect after successful auth. No credentials used in this audit. |
| Staff-shared | `/staff`, `/staff/attendance`, `/staff/dashboard`, `/staff/register-member`, `/staff/consultations`, `/staff/support-tickets`, `/staff/members` | Staff route guard; `Dashboard`, `RegisterMember`, `ConsultationQueue`, `SupportTickets`, `MemberList` | Route redirects by role; dashboard navigation; register validation; consultation filters/details/assignment; support filters/detail/reply; Member search/pagination/detail modal. Write actions were not exercised. |
| Admin | `/staff/membership-plans`, `/admin/attendance`, `/staff/staff`, `/staff/sports`, `/staff/facilities`, `/staff/courses`, `/staff/courses/new`, `/staff/courses/:courseId/edit`, `/staff/courses/:courseId`, `/staff/courses/:courseId/offerings/:offeringId`, `/staff/courses/:courseId/offerings/:offeringId/classes/:classId`, `/admin/notifications/broadcast`, `/admin/audit-logs`, `/admin/training`, `/admin/membership-sales`, `/admin`, `/admin/courses`, `/admin/classes`, `/admin/reports`, `/admin/courses/:courseId`, `/admin/courses/:courseId/offerings/:offeringId`, `/admin/courses/:courseId/offerings/:offeringId/classes/:classId` | Admin guard; Admin pages plus shared `Layout`; several `/admin/...` course routes redirect to `/staff/courses...`; membership sales shares `ReceptionistMembershipSales` with Receptionist | CRUD/edit modes, search/filter/pagination, confirmation dialogs, attendance selection, notification audience/validation, report filters/export/print, read-only training tabs. State-changing writes were not exercised. |
| Receptionist | `/receptionist/membership-sales`, `/receptionist/course-payments`, `/receptionist/pt-payments`, `/receptionist/course-registration`, `/receptionist/attendance` (redirect), `/receptionist/support` (redirect) | Receptionist guard; membership sales shares Admin implementation; payment queue components share `ReceptionistRefundQueue`; course registration has role variants | Refund category filters, pagination/refresh/details and confirmation prompt; PT/course queue separation; sequential registration member/course/offering/class selection and validation; redirects for attendance/support. Refund/payment/registration writes were not exercised. |
| Coach | `/coach`, `/coach/dashboard`, `/coach/schedule`, `/coach/pt-sessions`, `/coach/skill-assessments`, `/coach/attendance`, `/coach/training` | Coach guard; dashboard/schedule shared with other roles; `CoachPtSessions`, `CoachSkillAssessments`, `CoachAttendance`, `CoachTraining` | Schedule filters and detail; PT status/week/day filters; assessment detail/edit; Attendance class then session selection, roster loading/search/filter, focus, save and history; Training class/tab/detail and result states. Save/mark actions were not exercised. |
| Member | `/member`, `/member/dashboard`, `/member/attendance`, `/member/training`, `/member/training/:classId`, `/member/pt-booking`, `/member/levels-assessments`, `/member/courses`, `/member/courses/:courseId`, `/member/course-checkout/:registrationId`, `/member/support-tickets`, `/member/membership`, `/member/schedule`, `/member/profile`, `/member/classes` (redirect) | Member guard; dashboard/schedule shared; `MemberTraining`, `PtBooking`, `MemberLevelsAssessments`, `MemberCourses`, `CourseDetail`, `CourseCheckout`, `SupportTickets`, `MemberMembership`, `MemberProfile` | Filters, tabs, history/pagination, class/course details, booking and checkout states, support create/detail/reply, membership purchase/renewal/upgrade/cancellation/auto-renew, profile editing and validation. No writes were submitted. |

The admin and receptionist `/admin/membership-sales` and `/receptionist/membership-sales` routes use the same page component with role-specific access. Coach and Member dashboard and schedule routes also reuse shared page implementations. These are not separate component implementations.

## Browser coverage matrix

| Route / role | Viewport | States exercised | Result |
|---|---:|---|---|
| Public `/bo-mon` | 1440×900 | Initial layout, API-backed cards, images, horizontal overflow | **Browser reproduced**: seven API-backed sport cards rendered; all seven images had nonzero natural width; document width did not exceed viewport. Screenshot: `.playwright-mcp/audit-public-disciplines-1440.png`. |
| Public `/bo-mon/1` | 1440×900 | Card navigation to Badminton detail | **Browser reproduced**: card navigation reached `/bo-mon/1`; sport/course API requests returned 200. |
| Public `/bo-mon` E2E | 1440×900, 390×844 | API card rendering, image loads, sport navigation, overflow | **Test confirmed**: all five existing Playwright tests passed. Direct browser overflow check also passed at 390px on the detail page. |
| Admin pages | 1366×768; selected checks at 1440×900 and 1920×1080; Attendance also at 768×1024 | Initial page views for Dashboard, Consultations, Support, Register Member, Membership Plans, Member List, Staff, Sports, Facilities, Courses, Notifications, Audit Logs, Training, Membership Sales, Reports; Course → Offering navigation; Member detail/edit modal; Admin Attendance session selection, filters, independent list scroll | **Browser reproduced**: no document-level horizontal overflow on the inspected initial pages. Member modal stayed within viewport at all three requested sizes; at 1366×768 its internal scroll exposes both save actions, focus remains trapped, Escape closes, and focus returns to the triggering Details button. A Course → Offering detail path loaded. Admin Attendance now keeps the selected roster beside the scrollable 98-session list at desktop sizes; at 768×1024 panels stack and document width remains within viewport. No write actions were triggered. |
| Receptionist pages | 1366×768, 1440×900, 1920×1080 | Initial pages and safe interactions | **Blocked / Not verified**: Admin is not authorized for Receptionist-only route guards. No route guard was bypassed. |
| Coach pages | 1920×1080 initial views; Attendance at 1366×768, 1440×900, and 1920×1080 | Dashboard, Schedule, PT Sessions, Skill Assessments, Training initial views; PT confirmed-status filter; Attendance class then session selection before and after fix | **Browser reproduced**: after the fix, the selected Attendance card stays within its 330px list panel and does not intersect the roster at all three target widths. Other inspected Coach pages had no document-level horizontal overflow. Attendance selection and PT filtering were read-only. |
| Member pages | 1366×768, 1440×900, 1920×1080 | Initial pages and safe interactions | **Blocked / Not verified**: direct navigation to `/member/membership` while signed out redirected to `/login`; Admin route access also redirects to the Admin home. No authorization bypass was used. |

Admin page snapshots taken at 1366×768 showed no document-level horizontal overflow. The Member modal was measured at 1366×768, 1440×900, and 1920×1080. At 1366×768, the Member table had the same measured bounds while the modal was open and after it closed. At the other two sizes, modal fit and page overflow were checked, but a separate closed-state table measurement was not recorded. Coach pages were visually inspected at 1920×1080; the selected Attendance state was measured at all three requested desktop sizes. This is partial role/route coverage, not a full application QA pass.

Receptionist and Member pages remain unverified in an authorized browser session. Many loading, empty, error, long-text, dropdown, stale-response, and state-changing workflows remain unverified; no state-changing actions were submitted. Admin Attendance's long list was observed earlier, but its master-detail/scrolling options still require Admin-session follow-up before changing that page.

## Findings register

### QA-CA-01 — Selected session card exceeds its assigned Attendance column

- **Role / route:** Coach, `/coach/attendance`
- **Severity / type:** Medium / Layout
- **Evidence:** **Browser reproduced** and **Source confirmed**.
- **Steps to reproduce:** Sign in with a Coach account; open Coach Attendance; choose class BA01 (“Cầu lông nâng cao cấp tốc”) and select the first assigned session. At 1366×768, 1440×900, and 1920×1080, compare the selected card with the side panel and roster bounds.
- **Expected:** The session list remains inside its left grid track and does not cover or overlap the roster panel.
- **Actual:** At 1440×900, the grid begins at x=337.59 with width 1005.81; the side panel ends at x=667.59, but the card extends to x=1013.81 and overlaps the roster beginning at x=685.59. At 1366×768 it extends to x=1010.84 across a side panel ending x=664.63 and roster beginning x=682.63. At 1920×1080 it extends to x=1060.72 across a side panel ending x=714.50 and roster beginning x=732.50. The document itself does not horizontally overflow at these viewports, so document-width checks alone miss the defect.
- **Viewport:** 1366×768, 1440×900, and 1920×1080.
- **Evidence references:** `.playwright-mcp/coach-attendance-before-session-1440.png`, `.playwright-mcp/coach-attendance-overlap-1366.png`, `.playwright-mcp/coach-attendance-overlap-1440.png`, `.playwright-mcp/coach-attendance-overlap-1920.png`; `.coach-attendance-grid` and `.coach-attendance-session` in `src/pages/CoachAttendance.css`; selected-state switch in `src/pages/CoachAttendance.jsx`; history below.
- **Confirmed root cause:** The session button's normal grid tracks (`minmax(170px, 1.3fr) minmax(180px, 1fr) auto minmax(145px, .8fr)`) plus gaps and padding require about 651px. The selected-state page grid gives the list a 330px track, but the card retained its four-column intrinsic minimum. The roster's overflow clipping does not constrain this sibling card.
- **Confidence:** High; reproduced at all three target viewports and supported by current CSS and commit history.
- **Regression:** **Introduced by the historical two-column interaction layout in commit `38839ab`**, not by the original session-card rules. `git blame` dates the session-card minimum tracks to `3dcf144` (2026-10-18); `git show 38839ab` shows the later addition of the fixed 270–330px side-by-side panel and selected-state grid. The original card rules were already too wide for a narrow column, but the fixed narrow column activated the conflict. Current outer page max-width refinements do not remove that constraint.
- **Fix:** In the selected two-panel state, the card now uses two flexible `minmax(0, 1fr)` tracks and allows its direct content to wrap. This is scoped to the narrow selected-state card; it preserves all session fields, session selection, keyboard activation, and the roster layout.
- **After behavior:** At all three requested viewports, the card width was 280px inside the panel's 330px width. Card right edges were x=639.63/642.59/689.50 while panel right edges were x=664.63/667.59/714.50; the card did not intersect the roster. No document-level horizontal overflow occurred. The compact card is taller than the original wide card, but the content remains visible and contained.
- **Verification status:** Before/after Playwright measurements and screenshot at 1440×900: `.playwright-mcp/coach-attendance-after-1440.png`. A focused authenticated E2E regression now checks panel containment, sibling non-overlap, visible selectors, selection state, and keyboard activation at all three widths when `PLAYWRIGHT_STORAGE_STATE` points to a local authorized Coach session. That state file is not stored in the repository. The suite was not yet run with an authenticated E2E storage state. Existing `node --test tests/coachAttendance.test.mjs` passed (11 tests); existing Public Disciplines E2E passed (5 tests).

### QA-ADM-ATT-01 — Attendance roster follows a very long unpaginated session list

- **Role / route:** Admin, `/admin/attendance`
- **Severity / type:** Low / Layout (information density and interaction flow)
- **Evidence:** **Browser reproduced** and **Source confirmed**.
- **Steps to reproduce:** Sign in as Admin; open Admin Attendance at 1440×900 with default filters; select the first listed session. Do not use attendance controls.
- **Expected:** Selecting a session makes its roster discoverable while preserving access to filters and other sessions.
- **Actual:** The current demo data renders 98 session cards in one list. The list box measured about 9,691px high; the selected roster started around document y=10,233px. Selection does not bring it into the viewport. The page had no horizontal overflow, and the selected roster did not overlap the list.
- **Viewport:** 1440×900.
- **Evidence references:** `.playwright-mcp/admin-attendance-session-list-1440.png`, `.playwright-mcp/admin-attendance-selected-roster-1440.png`; `StaffAttendance.jsx` maps `visibleSessions` and renders the selected roster after the session list.
- **Suspected root cause:** All filtered sessions are rendered in one list before the roster; there is no pagination or list virtualization. Search, sport, and status filters exist and can reduce the list.
- **Confidence:** High for the observed dataset and composition; severity depends on whether this full-list workflow is intentional for Admins.
- **Regression:** Not assessed; no historical regression is claimed.
- **Fix:** Admin Attendance now presents the session list and selected roster as a two-column master-detail workspace. The session list has a `60vh` independent scroll region; its heading and filters remain outside the scroller. At narrower viewport widths the workspace returns to normal document flow. The session source and roster remain the same API-backed records, with existing filters, selection, roster status filtering, pagination, and attendance actions unchanged.
- **After behavior:** At 1440×900, the roster begins alongside the session list at viewport y≈309px instead of document y≈10,233px. The page height fell from about 11,055px to about 1,205px, with no horizontal overflow. The list contains all 98 sessions and scrolls internally (15,205px content / 540px viewport). At 1366×768 and 1920×1080, the same 98-session list and adjacent roster remained within the page width; the narrowest content column adapted the card layout without overlap.
- **Verification status:** Browser checked at 1366×768, 1440×900, and 1920×1080. Search/status/sport filters remain outside the session scroller; changing status to CANCELLED displayed 8 rows, retained the selected roster, and resetting restored all 98. Keyboard PageDown moved the focusable session list; Enter on a session button preserved native keyboard selection and loaded the roster. No attendance state was changed. Screenshot: `.playwright-mcp/admin-attendance-master-detail-1440.png`.

#### Follow-up investigation for Admin master-detail

- **Observed workflow:** `StaffAttendance.jsx` provides labeled search/status/sport filters, keyboard-operable session buttons with `aria-pressed`, selection-scoped roster loading, roster status filters, and member pagination. The 98-session list is in normal document flow; the roster is rendered after it, so selecting a session does not bring the roster into view. Session filtering and selection do not modify records.
- **Ownership:** Admin Attendance routes to `StaffAttendance`. Coach Attendance is a separate page component; both currently import the base rules from `CoachAttendance.css`. A layout can be isolated to Admin markup and `.staff-attendance-*` rules without changing Coach behavior or API contracts.
- **Assessment:** The existing separate selection/roster workflow supported a desktop master-detail layout. The session list is independently scrollable, named, and keyboard-focusable; filters remain outside it. At widths up to 1000px the panels stack in normal document flow, while the long session list remains bounded and keyboard-scrollable.
- **Disposition:** Implemented and verified in a safe Admin session. The user initially reported the Logout control missing while trying to switch roles; it was present in the inspected Coach and Admin DOM, and the browser subsequently showed the authorized Admin session. No logout action was triggered. Coach Attendance remains a separate component and its layout rules are not changed by the Admin master-detail styles.

No additional defect met the evidence threshold in the other inspected Admin or Coach initial views. Receptionist and Member pages are not recorded as defect-free.

## Shared layout and design-system observations

- The Coach defect came from intrinsic minimum sizing in nested CSS Grid: a page-level fixed track was narrower than the selected card's child track minima. The Admin fix also confirms that a populated record list can make a selected detail panel effectively unreachable when both remain in ordinary vertical flow.
- Existing token guidance treats the design-system document as reference and keeps `.lp`, `.public-site`, and authenticated portal styling separate. Proposed `--sc-*` names are not active global tokens. This audit did not propose token consolidation.
- Public Disciplines at 1440px uses readable centered content and loaded images; it had no horizontal overflow at the checked desktop and mobile widths. That single public page is not representative of every Public route.
- Portal width and spacing differences remain source-only observations until authenticated pages can be compared. Do not classify varied page-owned max-width values as defects without rendered evidence.

## Console and network notes

- On the inspected Public Disciplines page, the recent console window had 0 errors and 0 warnings. `/api/public/sports` returned 200 and `/api/public/sports/1/courses` returned 200. No failed application API request was listed for that page. On Coach Training, the console had 0 errors and 0 warnings; `/api/training/classes`, `/api/training/classes/1/plan`, and unread-count requests returned 200. Coach and Admin Attendance roster loading succeeded after selection; their recent browser console had 0 errors/warnings and inspected API requests returned 200.
- The Playwright session's cumulative console history also contained older entries from earlier browsing: a `favicon.ico` 404, Vite HMR/import errors for `MemberList.jsx` and `ReceptionistRefundQueue.jsx`, and a 401 for `/api/notifications/unread-count`. These were not present in the current page's recent console window and are not attributed to this audit's public route. They require a fresh authorized session to determine whether any persist.

## Regression strategy

1. Added `e2e/coach-attendance-layout.spec.js`: select an API-backed class and session, check card containment and no sibling overlap, verify the selector and session button remain usable, and activate the selected button by keyboard at 1366×768, 1440×900, and 1920×1080. It does not click attendance save controls. The test requires `PLAYWRIGHT_STORAGE_STATE` to point at a local authorized Coach session; it skipped without that state.
2. Retain existing unit coverage for stale roster requests and session-scoped saves. Those tests cover async correctness, not layout.
3. Keep the existing Public Disciplines Playwright checks for API-driven card count, image load, navigation, and desktop/mobile overflow. Avoid screenshot snapshots for every route until role-authenticated test data and a stable browser environment exist.

## Frontend knowledge addition

Added one concise CSS verification rule to `frontend/AGENTS.md`: check child containment and sibling-panel overlap in populated Grid/Flex states at target widths. The detailed case study remains in this QA register; no separate guide or token policy was added.

## Remaining verification gaps

- Receptionist and Member sessions were not available; their routes and interaction states remain blocked.
- Only selected Admin and Coach routes/states were inspected; unvisited states on accessible routes remain unverified.
- Coach Attendance containment was verified at the target sizes with current demo data; other device widths and unusually long session labels were not tested.
- The new Coach E2E test requires a local authorized storage-state file. Authenticated runner execution remains pending.
- Admin Attendance was verified with the current 98-session Admin dataset; smaller or unusually long localized labels may change card heights, though containment uses flexible tracks and wrapping.
- Empty/loading/error states and API failures for protected pages were not inspected.
- Browser findings depend on the current demo data and running local services; availability may vary by session.
- Existing E2E coverage is limited to Public Disciplines; it does not establish visual quality for the wider route inventory.
