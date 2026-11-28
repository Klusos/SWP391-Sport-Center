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

## As-built documentation status (2026-11-28)

The role/module walkthrough and current ER diagram are maintained in [ERD_AS_BUILT.md](ERD_AS_BUILT.md). The user and developer explanation is [SPORTCENTER_DEFENSE_HANDBOOK_VI.md](SPORTCENTER_DEFENSE_HANDBOOK_VI.md). These are source-oriented supplements to the reviewed SRD, not claims that every old phase note is current. Current API behavior is summarized in the **Current source addendum** in `backend/docs/API_GUIDE.md`.

The fresh-schema snapshot `submission/SQL/SportCenter_FullSchema.sql` now includes the additive General Training Plan week model matching `backend/docs/sql/proposals/phase-training-plan-weekly-db5.sql`: `training_plan_week`, nullable `training_exercise.week_id`, positive/unique week numbering, actor/parent foreign keys, plan ownership protection, and ordering indexes. Legacy exercises remain unassigned (`week_id IS NULL`). The snapshot remains a fresh-database script; it is not a migration and must not be run against an existing database.

The reviewed SRD v1.5.9 Word file is retained as the baseline artifact. [SRD_AS_BUILT_ADDENDUM_2026-11-28.md](SRD_AS_BUILT_ADDENDUM_2026-11-28.md) records current implementation deltas and limitations; an addendum does not retroactively establish product approval for undocumented policy.

### Training plan persistence

- A class has one general `training_plan`; `training_plan_week` contains its ordered-by-number curriculum sections. `training_exercise.plan_id` remains authoritative, while nullable `week_id` places an exercise in a week. A null week is preserved legacy content, not implicitly Week 1.
- A Coach's whole-plan weekly save is atomic and is protected by the class write lock. Admin reads class training plans but does not edit them. Member reads remain subject to the existing confirmed-registration and ownership/benefit eligibility checks.
- General curriculum date ranges are derived from the CourseOffering start/end dates: ordinal week 1 begins on `startDate`, each ordinal advances seven days, and the last in-range week is clamped to `endDate`. Week content outside a shortened Offering remains stored and visible with a warning; no ClassSession relationship or stored derived dates are used.
- Individual Training Plan revision/week/exercise persistence is separate and unchanged. General plans do not have a Draft/Published lifecycle or reconstructable revision history; successfully saved content is immediately visible to eligible Members.
- The additive SQL Server change is recorded in `backend/docs/sql/proposals/phase-training-plan-weekly.sql`. Applying it to a database is a separate, target-guarded operation; application startup remains Hibernate `validate`.

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
