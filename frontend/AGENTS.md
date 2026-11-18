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
- Preserve accessible names, semantic controls, keyboard focus, responsive behavior, and reduced-motion handling already used by the component family. Avoid changing unrelated page styles.

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

## Browser and data safety

- Do not start, stop, or reconfigure frontend/backend services unless the task authorizes it. Use an already-running instance for requested browser checks.
- Read-only browsing is preferred. Do not submit forms that create/update records or trigger payment, email, notification, or irreversible actions without explicit authorization and safe test data.
- Never treat a successful Node test, lint, or build as browser-runtime or visual verification. Report browser/environment limitations rather than claiming coverage.
