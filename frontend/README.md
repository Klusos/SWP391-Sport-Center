# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.

## Phase 6.2 — Notifications

Authenticated ADMIN, RECEPTIONIST, COACH, and MEMBER users see a notification bell in the application shell. It polls `GET /api/notifications/unread-count` every 45 seconds, opens a paginated newest-first notification list (`GET /api/notifications?page=…&size=20`), and uses the existing mark-one/read-all PATCH endpoints. Polling is stopped when the authenticated shell unmounts; count requests do not overlap.

ADMIN users can send an announcement at `/admin/notifications/broadcast`. The page posts only `title` and `message` to `POST /api/admin/notifications/broadcast`; the server selects all ACTIVE recipients. A successful response displays the server-reported recipient count. The frontend disables repeated submissions while sending; backend broadcast requests do not have an idempotency key, so retrying after an uncertain network outcome can create duplicate notifications.

The frontend has no configured test runner. Verify with `npm run lint` and `npm run build`; browser/API checks require the notification backend endpoints and suitable role accounts.

## Phase 7.2 — ADMIN Audit Logs

- Route: `/admin/audit-logs`, nested under the existing ADMIN route guard. The sidebar entry is visible only in ADMIN navigation. Backend authorization remains authoritative.
- The read-only page calls `GET /api/admin/audit-logs` through the shared authenticated API helper. It uses the documented Spring `Page<AuditLogResponse>` fields and displays actor ID/role, action, entity type/ID, description, and timestamp. The API does not provide actor names, so the UI does not invent them.
- Filters are applied on submit and sent to the backend: actor User ID, action, entity type, entity ID, from, and to. Action/entity filters remain free text rather than exhaustive enumerations. Page size is 20; previous/next controls request one server page at a time.
- Datetime inputs use the browser's local timezone and are converted to ISO timestamps with an explicit UTC offset (`toISOString`, `Z`). Backend `createdAt` is a timezone-less UTC datetime, so the page appends `Z` before parsing and renders it in the browser's local timezone. `from` is inclusive; the selected `to` value is exclusive and the UI labels it accordingly.
- A null `actorUserId` is labeled “Hệ thống / tác nhân nội bộ”; the label intentionally does not claim a specific scheduler or process. 401/403 responses show an access/session message; other errors include retry.
- Verification: `npm run lint`, `npm run build`, and `git diff --check` passed. The project has no configured frontend test runner. Browser checks still require an ADMIN account and a running backend with the Phase 7.1 migration applied.
