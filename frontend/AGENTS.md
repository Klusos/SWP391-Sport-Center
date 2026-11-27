# Frontend guidance

## Application structure and route ownership

- `src/main.jsx` owns React startup and the single `BrowserRouter`.
- `src/App.jsx` owns route registration, authentication state, role-based frontend route wrappers, and the public/authenticated route split. Add or change a route there and keep route-specific behavior in the relevant page/component. Frontend route guards improve navigation but do not replace backend authorization.
- The public site shell and theme are under `src/public-site/`; the Landing page is under `src/landing/`. Authenticated role pages live under `src/pages/`, with shared portal layout/components under `src/components/`.
- `src/api.js` is the shared fetch/API boundary and reads `VITE_API_BASE_URL` (default `http://localhost:8080`). Reuse its API helpers and authentication handling; do not add a second API client for an existing endpoint.

## React and CSS conventions

- Reuse existing components and state/data helpers before adding another abstraction. Render backend data and preserve explicit loading, empty, error, and validation states. Do not substitute fixtures for real application data in product pages.
- Keep business rules and access enforcement in the backend. Frontend checks are presentation/navigation only unless the same rule is independently enforced by the API.
- Scope styles to their owning experience/page. Public site CSS is rooted at `.public-site`; Landing styles/tokens are rooted at `.lp`; authenticated portal styles belong to page or portal scopes. The Vite `publicCssIsolation` PostCSS plugin scopes the public stylesheet and selected Bootstrap styles. Do not treat `docs/SPORTCENTER_DESIGN_SYSTEM.md`'s future shared-token proposal as an active global token contract.
- For populated interaction states that change a Grid/Flex layout, verify child containment and sibling-panel overlap at target widths; document-level overflow checks alone can miss a child escaping its assigned column.
- Preserve accessible names, semantic controls, keyboard focus, responsive behavior, and reduced-motion handling already used by the component family. Avoid changing unrelated page styles.

## Vietnamese user-facing language

- Vietnamese is the application's user-facing language. Use natural, concise Vietnamese for navigation, headings, forms, dialogs, validation, empty/loading/error states, notifications, tooltips, and exports.
- Use consistent domain terms: Quản trị viên, Lễ tân, Huấn luyện viên, Hội viên, Khách, Bộ môn, Khóa học, Đợt học, Lớp học, Buổi học, Gói hội viên, Mua gói, Nâng cấp, Gia hạn, Thanh toán, Hoàn tiền, Lịch huấn luyện cá nhân, Đánh giá kỹ năng, Giáo án cá nhân, Nhật ký hoạt động, and Tổng quan. Use Học viên for class participants.
- Do not display raw technical enum values in ordinary UI. Translate status by entity/business context; for example, an ACTIVE account and an ACTIVE Membership have different meanings. Preserve enum values in API payloads and use a safe Vietnamese fallback for unknown statuses.
- Keep helper text only when it helps a user make a decision or complete an action. Preserve necessary eligibility, validation, payment, deadline, and destructive-action information.
- Format displayed dates as dd/MM/yyyy, date-times as HH:mm dd/MM/yyyy in Vietnam time, and currency consistently as VND. Preserve date-only semantics and native date-input serialization.
- Preserve backend/API/database identifiers, authentication roles, technical contracts, proper names/branding, file-format abbreviations, and user-entered values. Localize their presentation without translating or rewriting stored data.

## Accepted Member presentation rules

- Dashboard attendance preview shows the four most recent valid recorded attendance entries by session date/time, regardless of age. Exclude cancelled and `validForStatistics=false` records; do not change the monthly attendance KPI or full attendance history.
- Member Dashboard skill cards use a flexible content-sized horizontal row: sport name and complete nonshrinking level badge stay on the same row. Wrap whole cards onto another row when space runs out; never stack the badge below the sport name or force fixed-width cards.
- Member Dashboard Recent Attendance is a compact list of at most four newest valid recorded entries, regardless of age. Exclude cancelled and invalid records; show only authoritative course/class/Coach metadata and never infer missing values.
- Member Attendance History donuts segment recorded PRESENT, ABSENT, and EXCUSED counts in green, red, and amber. The center percentage keeps the established present/recorded formula; zero recorded rows use a neutral ring.
- Attendance colors are consistent: present green, absent red, excused amber, unrecorded neutral, and future sessions blue or neutral. Do not count future or unrecorded sessions as absences.
- Renewal Intents and Payment Attempts remain separate backend concepts. In a combined Member pending view, associate an Intent with a Payment only by its persisted attempt `paymentId`; never deduplicate by plan, amount, date, or Member. Keep unresolved collection/refund states visible and keep settled successor Memberships in the upcoming Membership area.
- Support conversation alignment is based on authenticated user ID versus persisted sender/member IDs, never role labels or display names. Staff reply composition precedes separate ticket-management actions.
- Support lists use server-backed pages of five where the endpoint returns authoritative page totals; do not replace pagination with a scrollable ticket list. Keep ticket information, the message history, reply composer, and authorized management actions distinct. The message history is the only internally scrolling support panel. Refresh preserves the selected ticket, filters, page, and unsent reply where practical.
- Claiming an unassigned OPEN support request is the single Receptionist claim-and-start action. Do not expose a separate Receptionist status action that bypasses claiming. Preserve Admin oversight, legacy unassigned IN_PROGRESS claiming, and existing lifecycle permissions.
- Attendance row validation errors belong in a full-width row associated with the affected learner, never in the actions cell where long text can shift action controls.
- Initial attendance is an explicit complete-roster submission: keep per-learner P/A selection, Mark All Present convenience, pending-selection count, and one official submit action. Do not expose individual save controls until the persisted roster is complete. EXCUSED is historical display/filtering only, not a new selection.
- Attendance work-queue and Member statistics use server-persisted completeness; unsaved selections must not alter completion summaries. Show elapsed unrecorded sessions as incomplete/provisional, not absent, and do not show a percentage when no attendance is recorded.
- Admin Attendance and Admin/Coach Training use course → class → session/workspace navigation where applicable. Preserve role-filtered APIs, and keep PT supervision separate from class training.
- Member portal routes share a centered desktop content width and the accepted blue/white page-header treatment, spacing, title hierarchy, and action rhythm. Keep the scope under the Member portal root and preserve meaningful page-specific banners and control layouts.

## Shared dashboard and header layout

- KPI cards across roles keep the domain icon anchored at the card's top-right, independent of title length. Reserve text space so long Vietnamese labels wrap without moving or overlapping the icon; verify populated cards at desktop and narrow widths.
- Ordinary portal page headers should follow the Coach Skill Assessment header treatment for title, padding, background, border, and action alignment. Reuse the existing header style/component rather than stacking a second generic header above a specialized business banner.
- Add regression coverage for shared layout invariants when changing dashboard KPI or page-header markup.

## Training plan editors

- Use the shared `WeeklyPlanEditor` focused workspace for Individual and General plans: only the selected week is edited, week navigation keeps the local draft, and preview is read-only and never persists.
- Individual weeks keep the existing 1–52 constraint and revision save contract. General plan week numbers are positive/unique without an arbitrary 52-week cap; display in ascending order and preserve nonconsecutive numbers.
- General week date ranges are derived from CourseOffering dates. Keep legacy exercises unassigned until the Coach explicitly moves them; retain their IDs and content during that move.
- A General weekly save submits the complete draft and its latest `expectedUpdatedAt`; a 409 is a stale-edit conflict and must not discard local edits. Saved weeks are Member-visible under the existing eligibility rules; do not expose unsaved drafts or add a publication state.

## Tests and commands

Run these from `frontend/`:

```powershell
npm test                 # Node built-in unit/source-contract tests in tests/**/*.test.mjs
npm run test:e2e         # Playwright Test browser suite in e2e/**/*.spec.js
npm run lint
npm run build
```

`npm test` intentionally does not include the Playwright suite. E2E defaults to `http://localhost:5173`; override with `PLAYWRIGHT_BASE_URL` when needed. It expects the frontend and any required backend APIs to already be available and does not start services or seed data. Install the runner browser after dependency installation with `npx playwright install chromium` when it is not present.

Use real API-backed data where the test is about application rendering. Avoid fixed record counts when the API owns the collection. Use stable roles, labels, and route behavior where possible; use page-specific class selectors only where no suitable semantic locator exists. Browser viewport emulation does not replace verification on real devices. Record viewport, role, data prerequisites, console errors, failed requests, and unverified states in browser reports.

For authenticated browser QA, create local role-specific sessions with the interactive workflow in [`docs/PLAYWRIGHT_AUTH.md`](docs/PLAYWRIGHT_AUTH.md). Use `createRolePage` from `e2e/support/roleAuth.js` so each role gets an isolated context and its session is checked against a read-only page before testing. Never copy credentials, storage contents, or tokens into tests or logs.

## Browser and data safety

- Do not start, stop, or reconfigure frontend/backend services unless the task authorizes it. Use an already-running instance for requested browser checks.
- Read-only browsing is preferred. Do not submit forms that create/update records or trigger payment, email, notification, or irreversible actions without explicit authorization and safe test data.
- Never treat a successful Node test, lint, or build as browser-runtime or visual verification. Report browser/environment limitations rather than claiming coverage.
