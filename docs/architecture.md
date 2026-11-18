# Current architecture map

This is a source-oriented map of the checked-in implementation. It does not assert deployment topology, live database state, or product approval.

## System shape

```text
Browser
  └─ React/Vite SPA (frontend, normally :5173)
       ├─ React Router routes and role-aware page shells
       └─ src/api.js ── HTTP/JSON ──> Spring Boot API (normally :8080)
                                      ├─ controllers + DTO validation
                                      ├─ services / authorization decisions
                                      ├─ Spring Data repositories / JPA entities
                                      └─ SQL Server (default local URL: SportCenterDemo)
                                           └─ manually applied SQL scripts
```

The diagram reflects source defaults and code boundaries. Environment overrides and actual runtime targets must be verified for each run.

## Frontend

- `frontend/src/main.jsx` starts React, imports Bootstrap, app styles/tokens, initializes theme preference, and mounts one `BrowserRouter`.
- `frontend/src/App.jsx` owns route registration and authentication/role route wrappers. Public routes are nested under `public-site/PublicLayout.jsx`; the Landing page is lazy-loaded from `landing/`. Authenticated surfaces use role-specific pages and shared layout/components under `components/`.
- Feature pages and view components are under `frontend/src/pages/` and `frontend/src/public-site/pages/`. Reusable state/presentation helpers are also kept as focused modules under `src/`.
- `frontend/src/api.js` is the shared fetch boundary. `VITE_API_BASE_URL` selects the API origin; absent that variable, the source default is `http://localhost:8080`. Authenticated calls attach a bearer token. Backend authorization remains authoritative.
- Styling is split among public site, Landing, portal/page, and token styles. `frontend/vite.config.js` contains `publicCssIsolation`, which scopes the public stylesheet and selected Bootstrap rules. The design-system shared-token section is a proposal, not a currently adopted global contract.
- Tests are split by runner: `frontend/tests/**/*.test.mjs` uses Node's built-in test runner (`npm test`); `frontend/e2e/**/*.spec.js` uses Playwright Test (`npm run test:e2e`) and `playwright.config.js`. E2E does not start services or seed the database.

## Backend and API

- `backend/` is a Java 21 / Spring Boot 4.1.1 application using Spring MVC, Spring Security OAuth2 Resource Server, JWT, Spring Data JPA, and validation (see `backend/pom.xml`).
- Main code is grouped by `controller/`, `dto/`, `service/`, `repository/`, `entity/`, `config/`, `security/`, and `scheduler/` under `backend/src/main/java/group5/example/sportCenter/`.
- Controllers expose JSON APIs. Controllers/DTOs handle HTTP mapping and input shape; services hold workflows; repositories and JPA entities map persistence. `config/SecurityConfig.java` defines request authorization/CORS, with JWT subject and service-level ownership checks used for user-scoped actions.
- Public catalog endpoints are under `/api/public/**`; authenticated business APIs are under resource paths such as `/api/memberships`, `/api/course-registrations`, `/api/payments`, `/api/training`, and `/api/notifications`. See `backend/docs/API_GUIDE.md` for the endpoint inventory, then confirm current behavior in controllers and `SecurityConfig`.
- Local entity images use `LocalImageStorageService` and a filesystem directory (default `uploads` relative to the backend working directory); public image reads use `/api/public/images/**`. This is separate from SQL Server blob storage.
- Payment code supports a mock/simulator path and a VNPay sandbox configuration. Do not infer that a live provider is enabled or production-ready from source defaults.

## Persistence and schema management

- `backend/src/main/resources/application.properties` defaults to SQL Server at `127.0.0.1:1433`, database `SportCenterDemo`; username/password are externalized. It imports an optional local `.env` file. Verify effective settings and target identity before any database-backed task.
- Hibernate is configured with `spring.jpa.hibernate.ddl-auto=validate` in the main profile. Schema changes are represented by SQL scripts under `backend/docs/sql/` and must be reviewed/applied manually; application startup is not the migration runner.
- Some test profiles intentionally use isolated H2 (`create-drop`) or a guarded SQL Server clone. Read test properties, Maven profiles, target guards, and `backend/docs/sql/README.md` before running database-related tests. H2 is not evidence of SQL Server locking, constraint, or migration behavior.
- `submission/SQL/SportCenter_FullSchema.sql` is documented as a schema-only fresh-database snapshot; its README explicitly warns against running it on an existing database. Its authority relative to the migration guide's missing-core-schema note is unresolved; see [the business-rule register](business-rules/README.md).
- A migration file or historical verification report does not prove that a target database has that schema today. Current authorized read-only inspection is required.

## Verification boundaries

- Frontend Node tests, backend unit/H2 tests, lint, and builds each cover their configured scope only.
- Browser E2E requires the frontend and necessary APIs to be running, real/known test data, and a Playwright browser. Visual/layout verification should state browser, role, route, and viewport.
- SQL Server migrations, locking, payment provider behavior, and live data require their own explicitly authorized and isolated verification. Do not perform those checks by modifying shared data.
- Product decisions are not established by implementation, tests, demo seeds, or this architecture map. See [`docs/business-rules/README.md`](business-rules/README.md); unresolved items require owner confirmation.
