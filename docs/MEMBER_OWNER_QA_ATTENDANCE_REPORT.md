# Member Owner QA Corrections and Attendance Redesign

Date: 2026-11-26
Verdict: **PASS_WITH_GAPS**

## Implemented

- Coach Dashboard and Member KPI cards use grid placement for icons, keeping them aligned independently from variable-length Vietnamese titles. Coach assigned classes use six rows per page, display the API total, and retain the training-management link. The former expand/collapse behavior is absent.
- Coach Schedule no longer repeats the role/brand breadcrumb in the page body; the shared application top bar remains.
- Member Dashboard keeps the current and following Vietnam-local week preview, sorts eligible sessions, and paginates client-side in groups of five. The recent attendance preview uses the four latest Vietnam-local calendar days, excludes cancelled sessions, and caps at four rows. Its full-history action stays at the bottom of the card. The empty-state PT action uses the blue outlined treatment.
- Member Attendance History now groups by `courseClassId`, not course or sport. The overview has start-year and sport filters, a six-class page, counts and a present/valid-recorded percentage. The detail view keeps the selected filters/page on return, separates future and unrecorded sessions, paginates session rows by ten, and remains read-only.
- Member Training Offering date ranges now use the shared `formatDateVN` helper (`dd/MM/yyyy`) rather than rendering raw ISO dates.
- Member Support was inspected in the authenticated browser. Its existing request form, category, description, history and detail empty state remain intact; no ticket was submitted.

## Attendance data and rules

The browser runtime used the existing packaged backend against `LAPTOP-416VBDLK / SportCenterDemo_PTOutcome_8CF1 / database ID 12`. Before startup, a read-only SQL query returned that exact server/database identity and `ONLINE`. The packaged backend started with `ddl-auto=validate`, SQL initialization disabled, all three schedulers disabled, and process-local DB12 targeting. Browser API requests were verified to go to that backend on port 8081. Vite ran on the IPv4 loopback origin with an explicit DB12 API URL.

The Member-scoped read contracts are `GET /api/attendance/me`, `GET /api/course-registrations/me/class-sessions`, and `GET /api/training/me`. Attendance and class-session endpoints use the authenticated Member identity; no client-supplied Member ID is used. The class-session response contains the complete relevant schedule list rather than a server-paginated subset, so the Dashboard preview pagination is over the available eligible data.

Attendance rows marked invalid for statistics (including cancelled sessions) are excluded from the calculation. The displayed percentage is **present records / valid recorded attendance records**. Future and unrecorded sessions are shown separately and do not become absences. When the denominator is zero, the UI says “Chưa có dữ liệu điểm danh.” No absence threshold or course-failure behavior was added. No backend source or schema change was needed.

## Verification

- Focused Node regressions: **32/32 passed** (`coachDashboard`, `memberAttendance`, `memberTraining`, and `memberPortalTargetedFixes`).
- Full frontend suite: **582/582 passed**.
- Frontend lint: **passed**.
- Production build: **passed**; the existing large JavaScript chunk warning remains.
- `git diff --check`: **passed**; Git emitted only existing LF-to-CRLF normalization warnings.
- Backend Consultation test retry: **BLOCKED** before test execution by Maven's shared-cache lock (`spring-boot-starter-parent~pom~4.1.1.lock`, unable to open after five attempts). No backend source changed in this batch. Existing Java processes and cache files were left untouched.

## Authenticated browser results

Checks used normal login forms with the existing authorized test accounts and the DB12-backed runtime; no business mutation was submitted.

- **Coach Dashboard — PASS:** API-backed KPI values included 11 assigned classes, 7 confirmed registrations and 26 upcoming teaching sessions. The section showed 6 rows and page 1/2; the remaining classes are reachable through pagination.
- **Coach Schedule — PASS:** week navigation and assigned class/PT entries rendered in the dedicated page. The in-content duplicate breadcrumb was absent.
- **Member Dashboard — PASS_WITH_GAPS:** the empty schedule state showed both course and PT actions; computed PT button styling had readable blue foreground, white background and blue outline. Another Member showed five chronological eligible upcoming items and two skill badges without overflow. The available data did not exceed five items, so browser interaction with a second preview page was not possible; the automated pagination test covers more than five items.
- **Member Attendance overview/detail — PASS:** one available Member showed one class with one recorded PRESENT and a separate unrecorded session; another showed two distinct class cards across Badminton and Yoga, then opened a class detail with two future sessions. Zero-record classes displayed the neutral no-data state. Year/sport filters were visible and usable on the training overview. Attendance detail/document widths were checked at 1280, 1366, 1440 and 1920 CSS pixels; no horizontal document overflow occurred.
- **Member Training — PASS:** two class cards displayed Vietnamese date ranges (`19/11/2026 – 30/12/2026` and `23/11/2026 – 07/12/2026`). Selecting 2026 and Badminton reduced the list to the matching class and retained the count.
- **Member Support — PASS (read-only):** the page and expanded request form rendered with existing fields and empty history. No request was submitted.

## Coverage gaps and deferred cases

- The available Member data contained five eligible Dashboard preview records, not more than five, and no recent attendance within the four-day preview. The maximum-four recent attendance case and five-plus preview pagination were verified by Node tests, not by a populated browser case.
- No suitable browser record exercised cancelled-session historical evidence, multiple attendance pages, a cross-year Offering, or long sport names. Their grouping, filters, date boundaries, and cancellation handling are covered by focused automated tests.
- No screenshots were retained. Browser accessibility snapshots, API request results, route state, and viewport measurements were used as evidence.
- The existing two refresh actions on Member Training detail were observed but were outside this attendance-focused batch; no behavior there was changed.

## Safety and repository state

- No business database writes, fixture creation, migrations, payments, refunds, or scheduler activation occurred. Browser actions were navigation, filtering, reads, authentication, and opening a local form only.
- One initial browser origin resolved to a separate pre-existing IPv6 `localhost` frontend/API on port 8080. The attempted requests were authentication and read-only GETs; source inspection confirms `AuthService.login` only reads the user and issues a JWT. That service was not stopped or modified. All acceptance evidence above is from the corrected IPv4 Vite origin and the DB12 backend.
- The task-started packaged backend and Vite processes were stopped. Pre-existing services were left running.
- Branch and HEAD were preserved: `qa/team-integration-20261122` at `1f96272df08f156c5a84b74183f833de61defcf4`. Existing accumulated dirty work was preserved.
- The requested master acceptance report is outside the workspace write boundary. This repository-local report is provided; updating the external report remains pending.
