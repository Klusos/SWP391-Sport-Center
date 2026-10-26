# SportCenter Implementation Status

## Flow 4 training and demo fixes (2026-10-26, branch `feature/flow4-training`)

| Area | Change | Verification |
|---|---|---|
| Flow 4 training | `TrainingPlan` (goal, description), `TrainingExercise`, `TrainingResult` (score 1–10 per PRESENT member and session), `ProgressEvaluation` (rating 1–5, append-only); `/api/training/**`; Coach page "Giáo án & kết quả" (also at `/admin/training` as "Quản lý tập luyện"; training writes are locked against double submits) and Member page "Kết quả tập luyện" | `TrainingIntegrationTest` 6 passed (including admin access, 401/403 for anonymous and member writes, 404 for unknown ids, 400 for invalid exercise values); frontend `npm test` covers the API client paths and role routing; API end-to-end on local SQL Server (plan, exercises, attendance gate 409, session completion, result, evaluation, member view, other-coach 403) |
| `phase10-1-flow4-training.sql` | New additive, idempotent migration | Rehearsed twice on a restored copy, then applied to local `SportCenterDB` |
| `ClassSessionService` | Facility guidance check used `LocalDateTime.now()` instead of the injected `Clock` | `ClassSessionServiceTest` 31 passed; its tests now use the fixed clock instead of the real date (four tests had started failing on 2026-10-23) |

- Team demo setup: `DEMO_GUIDE.md` (repo root), `backend/.env.example`, and `docs/sql/demo-seed-flow2-4.sql` (additive, date-relative Flow 2–4 demo data guarded by `@ConfirmLocalDemoDatabase`). The seed was first rehearsed with the Phase 9 migrations applied; on this branch it needs only migrations 8-7b, 8-7c and 10-1, and that sequence has not been rehearsed separately.
- Full backend suite (H2 `phase1`): 263 tests, 0 failures. Frontend: build passed, `npm test` 5 passed, and lint is clean for the Flow 4 files.
- Not done: browser click-through; no component test renders the training pages. Membership upgrade and VNPay for PT bookings are not part of this branch.

## Flow 3: VNPay sandbox gateway (2026-10-19)

| Area | Code | Build and automated tests | Manual (VNPay sandbox) |
|---|---|---|---|
| `VnpayPaymentGateway` (API v2.1.0, HMAC-SHA512 signed `paymentUrl`; verifies signature, TMN code, amount and `vnp_ResponseCode`/`vnp_TransactionStatus`) | Implemented; selected with `PAYMENT_PROVIDER=vnpay` | `VnpayPaymentGatewayTest`: 6 passed | Sandbox accepted the signed URL; NCB test card payment completed |
| `GET /api/payments/vnpay/return` (updates payment, redirects to frontend with `paymentId`/`paymentStatus`) | Implemented | Not covered by automated tests | Passed: payment #10 `PENDING` -> `SUCCESS`, membership activated |
| `GET /api/payments/vnpay/ipn` (VNPay `RspCode` response) | Implemented | Not covered by automated tests | Invalid checksum -> `97` verified locally; real IPN not tested (VNPay cannot reach localhost) |
| Callback amount check (`AMOUNT_MISMATCH` rejected) | Implemented in `PaymentService.processOnlineCallback` | Covered for VNPay tampering in unit tests | Not tested |
| `PaymentGateway` refactor (callback params map); mock gateway behaviour unchanged | Implemented | `MockPaymentGatewayTest` 4 passed, `PaymentIntegrationTest` 23 passed, `AuthControllerIntegrationTest` 3 passed | Not re-tested manually after the refactor (mock flow was tested manually before it) |

- `PAYMENT_PROVIDER` defaults to `mock`, so existing setups keep the simulator. VNPay requires `PAYMENT_PROVIDER=vnpay`, `VNPAY_TMN_CODE` and `VNPAY_HASH_SECRET` in `backend/.env` (never commit them). Optional: `VNPAY_PAY_URL`, `VNPAY_RETURN_URL`, `PAYMENT_FRONTEND_RESULT_URL`.
- No database schema change.
- Frontend: `main` still has no ONLINE purchase button. The `feflow3` branch `Checkout.jsx` works with VNPay (opens `paymentUrl` in a new tab and polls `GET /api/payments/me`); verified manually before merge. The new tab is opened with `noopener`, so it has no session and lands on the login page after the redirect; setting `PAYMENT_FRONTEND_RESULT_URL` to a public page avoids this.
- Not implemented: expiry of abandoned `PENDING` ONLINE payments (the VNPay link expires after 15 minutes but the payment stays `PENDING` until cancelled), automatic refunds, VNPay refund/query APIs.

## Flow 3: Payment (2026-10-18)

| Area | Code | Build and automated tests | Postman |
|---|---|---|---|
| Create payment (ONLINE/COUNTER) for MEMBERSHIP, COURSE_REGISTRATION, PT_BOOKING; server-derived amount | Implemented | Full suite passed: 166 tests, 0 failures/errors/skips | Not tested |
| Online callback (HMAC-signed, idempotent), counter processing, cancel PENDING | Implemented; mock gateway only | Passed (H2) | Not tested |
| Membership activate/renew on payment | Implemented; renewal adds a new ACTIVE membership starting at the latest current `endDate` | Passed (H2) | Not tested |
| PT booking confirmation; late payment -> `REFUND_REQUIRED`, booking stays `EXPIRED` | Implemented via `PtBookingExpirationService.confirmAfterVerifiedPayment` under the booking row lock | Passed (H2) | Not tested |
| Revenue / member / class-registration reports (ADMIN) | Implemented; `SUCCESS` only | Passed (H2) | Not tested |
| SQL Server migration `docs/sql/phase2-payment.sql` | Idempotent; not executed | Static review only | Not applicable |

- Course Registration now starts in `PENDING_PAYMENT`; only verified payment before the stored deadline confirms it. Payment references `CourseRegistration.id` (not `CourseOffering.id`) and uses the stored `finalPrice`; details and verification are recorded in Phase 8.7C below.
- `POST /api/memberships` is restricted to ADMIN for manual assignment; MEMBER self-activation without payment was removed.
- Duplicate-payment protection: payment rows are locked during counter/callback processing; the referenced registration/booking row is locked before checking for another `SUCCESS` payment.
- Demo-only mock online payments are disabled by default. Explicit opt-in requires `PAYMENT_MOCK_GATEWAY_ENABLED=true` and an allowed demo/test profile; production profiles remain disabled and callback URLs must be loopback. This simulator is not a real payment provider and does not verify settlement.
- Not implemented (as of 2026-10-18; VNPay sandbox added 2026-10-19, see above): real payment gateway (VNPay/MoMo/PayOS), automatic refunds, expiry of abandoned `PENDING` payments. MEMBER membership purchase UI is implemented as a counter-payment request; no client-side success action is available. The SQL Server E2E harness (`CourseRegistrationSqlServerE2EIT`) now also requires `dbo.payment` to exist.

## Phase 8.7C: Course Registration seat holds (2026-10-19)

- MEMBER course registration now starts `PENDING_PAYMENT`, stores `registeredAt` and `paymentDeadline = min(registeredAt + 24 hours, registrationDeadline)`, and returns the server deadline. A registration at/after the offering deadline or with no positive hold is rejected. Existing ACTIVE membership discount selection and persisted price snapshots are unchanged.
- Capacity includes CONFIRMED rows and pending rows whose deadline is strictly after current Vietnam time. Lazy expiration runs under row locks when a class is checked for capacity, member registration history is loaded, and verified payment is applied. A bounded scheduled worker expires due rows idempotently. PENDING cancellation is available only to the owning MEMBER and releases capacity; confirmed cancellation/refund policy remains unimplemented because none existed in the baseline.
- PaymentService continues to verify/authorize settlement through existing Flow 3 counter processing or gateway callback. Its course handler accepts only a live pending hold, confirms within the same transaction as `Payment.SUCCESS`, and changes a late hold to EXPIRED while recording `REFUND_REQUIRED`. No gateway endpoint, signature, callback contract, or payment amount behavior was changed. Existing callback lacks a provider settlement timestamp, so success first observed at/after deadline is conservatively treated as late and requires manual Flow 3 reconciliation.
- SQL Server migration `docs/sql/phase8-7c-course-registration-holds.sql` is manual and unexecuted. It adds nullable `payment_deadline DATETIME2(6)`, validates/replaces the known status CHECK, preserves legacy confirmed rows, and adds filtered pending uniqueness and expiration indexes. Apply after Phase 2 CourseRegistration and Payment migrations, rehearse against a restored backup, and deploy with `ddl-auto=validate` only after migration.
- Verification: full ordinary Maven `clean verify` with isolated H2: **251 tests passed, 0 failures, 0 errors, 0 skipped**. SQL Server E2E, SQL execution, real callback settlement and browser verification were not run. SQL Server concurrency, migration catalog compatibility, and rollback of successful deployment remain manual rehearsal items.
- The guarded SQL Server E2E target guard now requires `payment_deadline`, and its registration harness confirms test holds through the existing staff COUNTER settlement flow before checking confirmed-session behavior. The E2E harness was compiled as ordinary test source but was not activated or run.

## Phase 8.4C Membership Purchase and Receptionist Workflow Consolidation

- MEMBER self-service uses existing `POST /api/payments`, `/api/payments/me`, and payment cancellation APIs. Member identity comes from JWT, amount and plan eligibility are server-derived, inactive plans are rejected, and an existing pending payment for the same plan returns 409. No Membership is activated until staff confirms a COUNTER payment successfully. The UI does not expose online mock-payment success; the existing mock capability remains subject to its demo/test configuration guard.
- Receptionist membership sales use the existing staff member, membership, public-plan and payment APIs in one page. Payment history restores a pending counter transaction after refresh; confirmation remains explicit and only the existing PaymentService activates the membership. The Consultation Queue already supports linking/creating the member, so redundant receptionist registration and plan browsing navigation were removed while ADMIN plan management and registration remain available.
- `PaymentIntegrationTest`: added coverage for JWT-scoped member purchase, duplicate pending purchase, inactive plan rejection, coach denial, and existing pending-versus-confirmed activation behavior. Focused suite: 20 tests passed. Full isolated suite: 232 tests passed; 0 failures, errors, or skips. Frontend lint/build passed; no frontend test runner is configured.
- No schema changes, migration execution, SQL Server writes, browser testing, or real payment settlement were performed for this phase.

## SQL schema synchronization review (2026-10-15)

- Static inspection confirmed none of the three existing scripts defined `dbo.class_session`. They are incremental migrations, not a core database bootstrap; twelve of the sixteen JPA entity tables are not created by those scripts. The approved original core-schema source is not in this repository.
- Added `docs/sql/phase0-class-sessions.sql`. It creates the table from the current JPA mapping when absent and validates an existing table's columns, types, nullability, identity, primary key, and foreign keys without dropping or rebuilding objects. It expects the established `dbo.[class]` and `dbo.facility` tables. No JPA entity or application behavior changed.
- `docs/sql/README.md` documents prerequisites and an ordered setup sequence. The all-from-empty database process remains incomplete until the original core schema bootstrap is recovered.
- Verification was static only (`git diff --check` and source/mapping comparison). No SQL script was executed; no SQL Server was accessed. H2 tests were not run because this is a SQL Server DDL/schema-bootstrap change and H2 cannot validate SQL Server catalog behavior or Hibernate SQL Server type compatibility.

## Consultation workflow and public course discovery (2026-10-15)

- Consultation lifecycle remains `PENDING → IN_PROGRESS → COMPLETED` or `CANCELLED`. `PATCH /api/consultation-requests/{id}/complete` is independent of SkillAssessment, enrollment, and payment; the H2 integration test confirms completion creates no assessment or MemberSportLevel.
- Public discovery now has `GET /api/public/sports`, `GET /api/public/sports/{sportId}/courses`, and `GET /api/public/courses/{id}`. These anonymous GETs return only active catalogue records; existing administrative catalogue endpoints remain authenticated and mutations ADMIN-only.
- Temporary assessment policy: RECEPTIONIST may complete a PENDING assessment assigned to an ACTIVE qualified Coach. Existing `verifiedByCoachId` records that assigned Coach; the schema has no separate performing-user column. Revisit this temporary attribution when Coach-led assessment is restored.
- H2 coverage was added for active/inactive public course visibility, completion without assessment, and Receptionist assessment completion. Cached Maven 3.9.16 ordinary `mvn test` completed: **113 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. The new tests ran in isolated H2 contexts. Postman and live SQL Server verification have not been performed for these changes.
- Frontend production build and ESLint for all changed frontend files passed. Vite GET route-fallback checks returned 200 for `/`, `/bo-mon`, `/bo-mon/3`, `/khoa-hoc/7`, `/login`, and `/staff/consultations`; no headless browser was available, so React rendering and real API interactions were not browser-verified.

## Stabilization iteration 3 (2026-10-14)

- Course create/update now require one of the enrollment-compatible levels `BEGINNER`, `INTERMEDIATE`, or `ADVANCED`; input is case-insensitive and trimmed before storage. Course, Sport, CourseOffering, and CourseClass update status values are restricted to the existing states used by the code and normalized to uppercase.
- `GET /api/public/sports` returns only ACTIVE Sport `id`/`name` options without JWT. The existing `/api/sports` routes remain authenticated; CORS defaults include React/Vite `http://localhost:5173` and CRA `http://localhost:3000` (an explicit `CORS_ALLOWED_ORIGINS` deployment override still applies).
- Ordinary Maven `clean verify` completed successfully on the H2 profile: **107 tests, 0 failures, 0 errors, 0 skipped**. This includes four new focused H2 integration tests. SQL Server E2E and Postman were not run.
- Existing database values were not scanned or changed. Course levels that are unsupported remain stored and may cause CourseRegistration to reject registrations; editing such a course now requires choosing a supported level.

This document records source-code status separately from build and Postman verification. Sections dated 2026-10-09 below are historical snapshots; use the latest dated section for current authorization/build status.

## Status definitions

- **Code:** Implemented in the source inspected on 2026-10-09.
- **Build:** Build success is recorded only when a build command completes successfully.
- **Postman:** Successful manual verification is recorded only after evidence is provided.

## Authentication and user management

| Feature / endpoint | Allowed role | Code | Build | Postman |
|---|---|---|---|---|
| `POST /api/auth/login` | Public | Implemented | Not verified in this task | Not verified here |
| `GET /api/users/me` | Authenticated | Implemented | Not verified in this task | AGENTS.md says tested; no test evidence reviewed here |
| `POST /api/members` | ADMIN, RECEPTIONIST | Implemented; service assigns `MEMBER` | Not verified in this task | Not verified here |
| `POST /api/users` | ADMIN | Implemented; role is normalized and limited to ADMIN, RECEPTIONIST, MEMBER, COACH | Not completed: Maven Wrapper failed to start (`Cannot index into a null array`) | Not verified after role validation change |
| Other `/api/users/**` management routes | ADMIN, except the earlier `GET /api/users/me` rule | Implemented; user role updates use the same allow-list | Not completed: Maven Wrapper failed to start (`Cannot index into a null array`) | Not verified after role validation change |

## MemberProfile

| Endpoint | Allowed role | Code | Build | Postman |
|---|---|---|---|---|
| `POST /api/member-profiles/me` | MEMBER | Implemented; user is resolved from JWT subject; returns 201; existing profile and unique-key conflicts map to 409 | Not completed: Maven Wrapper failed to start (`Cannot index into a null array`) | User confirmed prior version: 201, 409, 403, 401; retest after DTO validation changes pending |
| `GET /api/member-profiles/me` | MEMBER | Implemented; resolves current member from JWT subject; returns 404 when no profile exists | Not completed: Maven Wrapper failed to start (`Cannot index into a null array`) | User confirmed prior version: 200, 401 without JWT, 403 for wrong role, 404 without profile; retest pending |
| `PUT /api/member-profiles/me` | MEMBER | Implemented; complete update requires height, weight, and a present non-null goal; empty goal is stored as null | Not completed: Maven Wrapper failed to start (`Cannot index into a null array`) | Prior version confirmed by user; retest requested after new validation behavior |
| `POST /api/member-profiles` | ADMIN | Existing ID-based create endpoint retained; shared range, precision, and goal-length validation applied | Not completed: Maven Wrapper failed to start (`Cannot index into a null array`) | Not verified after validation change |
| `GET /api/member-profiles/{id}` | ADMIN | Existing endpoint retained | Not completed: Maven Wrapper failed to start (`Cannot index into a null array`) | Not verified here |
| `GET /api/member-profiles/member/{memberId}` | ADMIN | Existing endpoint retained | Not completed: Maven Wrapper failed to start (`Cannot index into a null array`) | Not verified here |
| `PUT /api/member-profiles/{id}` | ADMIN | Existing endpoint retained; shared range, precision, and goal-length validation applied | Not completed: Maven Wrapper failed to start (`Cannot index into a null array`) | Not verified after validation change |

The `MemberProfile` one-to-one mapping and unique `memberId` constraint are unchanged. The numeric-ID endpoints are restricted to ADMIN; MEMBER self-service routes identify the profile from JWT subject.

### Postman confirmation

Before the current DTO validation changes, the user confirmed successful Postman checks for all self-service endpoints:

- POST `/api/member-profiles/me`: 201 on creation, 409 when a profile already exists, 403 for the wrong role, and 401 without JWT.
- GET `/api/member-profiles/me`: 200 when a profile exists, 404 when it does not, 403 for the wrong role, and 401 without JWT.
- PUT `/api/member-profiles/me`: 200 on update and persisted values verified by a subsequent GET; 404 without a profile, 403 for the wrong role, 401 without JWT, and 400 for invalid data.

## Database configuration

- `src/main/resources/application.properties` configures SQL Server (`SportCenterDB` on localhost:1433) as the default datasource.
- No profile-specific `application-*.properties` or YAML configuration was found.
- H2 and the H2 console starter are runtime dependencies, but the inspected application configuration does not select H2. No H2 test profile/configuration was found.
- `spring.jpa.hibernate.ddl-auto=update` is configured; review its schema-change behavior before production use.

## Known gaps and follow-up

- MemberProfile DTO validation is now aligned across all three DTOs: height 50-250 cm, weight 10-500 kg, at most two decimal places, and goal up to 1000 characters. `MemberProfileRequest` records whether the JSON goal property was provided so PUT `/me` can reject both a missing goal and `goal: null`; `goal: ""` clears the stored goal. These changes are written but await build and new Postman verification.
- `UserService.createUser` and `updateUser` now normalize and restrict role to ADMIN, RECEPTIONIST, MEMBER, or COACH, returning 400 for invalid roles. `/api/users/**` management routes remain ADMIN-only (except the earlier authenticated `GET /api/users/me` rule); `/api/members` remains ADMIN/RECEPTIONIST and the service assigns MEMBER. No public registration route was added; JWT login configuration was not changed.
- AGENTS.md current-development notes now point to this file for build and Postman evidence rather than describing completed features as upcoming work.
- Next step: run the build and verify the updated MemberProfile validation and User role allow-list in Postman.

## Authentication branch handoff review (2026-10-09)

- Git branch is `feature/authentication`; it currently points to the same commit as `origin/main` (0 commits ahead/behind). Changes are present only in the working tree; no staged changes were reported at review time.
- `docs/API_GUIDE.md` was generated from the current controllers, DTOs, and security rules. `docs/postman/SportCenter-API.postman_collection.json` contains 59 requests, uses `{{baseUrl}}` and `{{token}}`, and its JSON syntax was validated. This validates the collection file only, not the API behavior.
- Controller source contains 59 request mappings; the collection was checked to contain 59 requests.
- Maven build remains unverified: `mvnw.cmd -DskipTests package` failed before starting Maven with `Cannot index into a null array`.
- `git diff --check` reports trailing whitespace in `CreateUserRequest.java` and `UpdateUserRequest.java`; these were not changed during the handoff review.
- A literal SQL Server password is present in the tracked `application.properties` in both HEAD and the working tree. Do not stage/push that file until the credential is externalized; rotate it if it is real. The JWT secret uses the `JWT_SECRET` environment placeholder.
- No Swagger/OpenAPI dependency or configuration was found. Springdoc 3.x is the documented compatible major line for Spring Boot 4; dependency integration is not implemented.
- Authorization review found that membership lookups and mutations, including `POST /api/memberships/expire`, currently allow any authenticated role. Sports, courses, offerings, classes, and facilities mutations also allow any authenticated role. Decide intended role restrictions before merge.
- No CORS configuration was found; configure allowed frontend origins before browser-based cross-origin integration.
- No ClassSession, course registration, payment, or public account-registration controller was found. `/api/classes` is the implemented course-class API.
- API behavior has not been Postman-verified as part of this handoff review. Earlier user-confirmed MemberProfile results are recorded above and predate the latest DTO validation changes.

## Authorization, CORS, and credential hardening (2026-10-10)

| Area | Current source status | Build | Postman |
|---|---|---|---|
| User/Profile authorization | Existing rules retained: user management ADMIN-only, `/api/members` ADMIN/RECEPTIONIST, `/api/users/me` authenticated, MemberProfile `/me` MEMBER, ID routes ADMIN | Awaiting build | Not retested in this change |
| Membership authorization | Roles restricted in SecurityConfig; MembershipService resolves caller from JWT subject, allows MEMBER only on own membership, staff can act for any MEMBER; manual `/expire` ADMIN-only | Awaiting build | Not tested; ADMIN, RECEPTIONIST, MEMBER, COACH cases pending |
| MembershipPlan | GET all authenticated roles; mutation ADMIN-only | Awaiting build | Not tested |
| Sports, courses, offerings, classes, facilities, coaches, CoachSport | GET all authenticated roles; mutation ADMIN-only | Awaiting build | Not tested |
| CORS | Explicit origins from `CORS_ALLOWED_ORIGINS`; defaults localhost:5173 and localhost:3000; wildcard rejected; REST methods and JWT headers allowed | Awaiting build | Preflight/browser behavior not tested |
| Database credentials | SQL Server username/password use `DB_USERNAME` and `DB_PASSWORD`; JWT uses `JWT_SECRET` | Awaiting build | Not applicable |

Membership purchase behavior was not changed: creation still stores status `ACTIVE` immediately. No Payment entity or payment service/controller was found. Confirm this purchase/payment contract before enabling production purchases. A SQL Server password was present in tracked source/history during the prior handoff review; rotate it if it is real/shared even though the working-tree config now reads `DB_PASSWORD`.

`docs/API_GUIDE.md` and the Postman collection describe the new authorization policy, but all changed access rules remain unverified by Postman. No build success is claimed for this change.

The previously untracked local `.env` is now covered by `.gitignore`; it was not read, modified, or deleted. No `.env.example` was added because safe example variable values and local conventions have not been confirmed.

## User lookup authorization (2026-10-10)

| Endpoint | Allowed caller/data | Code | Build | Postman |
|---|---|---|---|---|
| `GET /api/users` | ADMIN: all users; RECEPTIONIST: MEMBER and COACH only; MEMBER/COACH denied | Implemented in SecurityConfig and filtered again in UserService using the active caller from JWT subject | Not verified | Not tested |
| `GET /api/users/{id}` | ADMIN: any user; RECEPTIONIST: MEMBER/COACH only; other roles denied | Implemented in SecurityConfig and checked against target role in UserService | Not verified | Not tested |
| `GET /api/users/me` | Any authenticated active user | Existing behavior retained | Not verified in this change | Not tested in this change |
| User mutations | ADMIN only | Existing SecurityConfig policy retained; RECEPTIONIST cannot update, change password, or delete through `/api/users` | Not verified in this change | Not tested |
| `POST /api/members` | ADMIN, RECEPTIONIST; service assigns MEMBER | Existing behavior retained | Not verified in this change | Not tested |

`UserResponse` and `UserProfileResponse` contain no password hash or credential fields. No schema or check-in API changes were made. The build and all cases above are pending verification.

## ClassSession module (2026-10-10)

| Feature / endpoint | Allowed role | Code | Build | Automated / Postman verification |
|---|---|---|---|---|
| ClassSession entity and relations | N/A | Added one `ClassSession` entity mapped to `ClassSession`, with required `classId` to existing `CourseClass`, nullable `facilityId` to existing `Facility`, and `LocalDateTime` fields | Not verified | No database migration run; Postman N/A |
| `GET /api/class-sessions`, `GET /{id}`, `GET /class/{classId}` | ADMIN, RECEPTIONIST, MEMBER, COACH | Implemented | Not verified | Not verified |
| `POST /api/class-sessions` | ADMIN | Implemented; defaults status to SCHEDULED; supports historical COMPLETED and CANCELLED only under listed rules | Not verified | Unit tests added; execution pending; Postman not verified |
| `PUT /api/class-sessions/{id}` | ADMIN | Implemented; updates class, optional facility, start/end; session ID is path-only | Not verified | Unit tests added for excluding current ID and missing class; execution pending; Postman not verified |
| `PATCH /api/class-sessions/{id}/status` | ADMIN | Implemented; SCHEDULED can be completed after end or cancelled; cancelled cannot be restored; completed cannot transition to another status | Not verified | Transition and boundary tests added; execution pending; Postman not verified |

Overlap validation excludes CANCELLED sessions and uses strict interval overlap, checking both Facility and the Coach attached to CourseClass. The earlier behavior that allowed make-up sessions outside CourseOffering dates was superseded by Phase 8.4B: session creation and updates now reject dates outside the inclusive offering range and require exact offering duration. Counts above `plannedSessions` remain non-blocking warnings; counting per CourseClass is provisional because the model does not document whether the count applies per class or offering. No automatic schedule generation, physical DELETE, or schema change was introduced.

Next: get Maven build and unit tests running, then verify GET/POST/PUT/PATCH permissions and lifecycle/overlap rules in Postman against SQL Server.

### Facility availability validation (2026-10-10; prior behavior superseded below)

`FacilityService.createFacility` sets status to `ACTIVE`. The prior unrestricted update behavior described in the original review was replaced by ACTIVE/INACTIVE validation and the future-session guard documented below.

| Behavior | Code | Build | Automated / Postman verification |
|---|---|---|---|
| POST with null Facility | Allowed | Not verified | Unit test added, not run; Postman pending |
| POST with ACTIVE Facility | Allowed; existing Facility/Coach conflict checks continue | Not verified | Unit test added, not run; Postman pending |
| POST with non-ACTIVE Facility | Rejected with 409 via existing `IllegalArgumentException` handler | Not verified | Unit test added for INACTIVE, not run; Postman pending |
| POST with missing Facility ID | 404 via `ResourceNotFoundException` | Not verified | Unit test added, not run; Postman pending |
| PUT assigning another non-ACTIVE Facility | Rejected with 409 | Not verified | Unit test added, not run; Postman pending |
| PUT retaining the same unavailable Facility on a future SCHEDULED session | Rejected with guidance to choose an ACTIVE Facility or cancel | Not verified | Unit test added, not run; Postman pending |
| GET a historical session with INACTIVE Facility | Still returned; no mutation or auto-cancellation | Not verified | Unit test added, not run; Postman pending |

Only status `ACTIVE` is treated as available (case-insensitive); no schema or Facility/ClassSession data is changed by this code. Existing overlap checks remain in place. The Postman request examples need no body change; request names now document the ACTIVE Facility requirement. Maven Wrapper was not re-run successfully for this incremental change; latest known result remains the earlier wrapper failure before Maven startup.

## ClassSession lifecycle and Facility status rules (2026-10-11)

| Area | Code status | Build | Automated tests | Postman |
|---|---|---|---|---|
| ClassSession transitions | Only SCHEDULED/COMPLETED/CANCELLED accepted and normalized uppercase. SCHEDULED→COMPLETED requires now >= endTime; SCHEDULED→CANCELLED requires now < startTime. Terminal states cannot transition elsewhere. PUT edits are rejected for terminal sessions. | Not verified | Boundary, terminal, and invalid status/range tests added; not run | Not tested |
| Facility status normalization | Update accepts ACTIVE/INACTIVE case-insensitively and stores uppercase; null, blank, and unknown status return 400 | Not verified | Valid and invalid status tests added; not run | Not tested |
| Facility deactivation | ACTIVE→INACTIVE checks for SCHEDULED sessions with `startTime > now`; future sessions cause 409 without saving Facility; otherwise saves INACTIVE. No session is changed. | Not verified | With/without future session tests added; not run | Not tested |
| Existing ClassSession rules | ACTIVE Facility assignment, Facility/Coach overlap checks, make-up warnings, permissions, and GET of historical sessions retained | Not verified | Existing tests retained; not run | Not tested |

The future-session predicate in this snapshot used strict `StartTimeAfter`; it is superseded by the latest `EndTimeAfter` rule below. No automatic status transition or schema change was introduced. ClassSession service tests also cover completion before/at endTime, cancellation before/at/after startTime, status normalization, and terminal-state edits. Maven Wrapper test command failed before Maven startup with `Cannot index into a null array`, and no system `mvn` executable is available; no unit test or build success is claimed.

## Facility deactivation coverage (2026-10-11)

Before this change, the user reported **32/32 Maven tests passing (0 failures, 0 errors)** and confirmed the ClassSession Lifecycle and Facility Status Postman cases passed. Those results apply to the prior implementation only.

Facility deactivation now checks for a `SCHEDULED` ClassSession with `endTime > now` at one service-captured `now`. This blocks both sessions in progress and upcoming sessions, while allowing deactivation when sessions have ended or have `endTime == now`. The repository status predicate also excludes `COMPLETED` and `CANCELLED`. No ClassSession is changed and no schema, lifecycle, authorization, or overlap rule is changed.

| Verification for this change | Status |
|---|---|
| Code updated | Yes |
| Maven unit tests | Not yet verified; must rerun after this change |
| Build | Not yet verified; must rerun after this change |
| User Postman verification | Prior lifecycle/status cases were confirmed; this deactivation correction is not yet tested |


## Phase 1: MemberSportLevel and SkillAssessment (2026-10-12)

| Area | Source status | Automated verification | SQL Server / Postman |
|---|---|---|---|
| MemberSportLevel | Current level per member/sport, optional verification metadata, unique member/sport, no direct mutation API | Level absence, first creation, reassessment, downgrade, ownership tests passed | Migration not run; Postman not tested |
| SkillAssessment assignment | Staff only, active MEMBER/Sport/Coach and active linked COACH account, CoachSport required, PENDING default, duplicate-pending protection | Missing/inactive/invalid targets, qualification and concurrent creation tests passed | Filtered unique index supplied but not applied; Postman not tested |
| Completion/cancellation | Assigned active COACH completes; staff cancels; PENDING only; terminal states immutable | Completion, cancellation, invalid level/notes, terminal lifecycle and real JWT role/ownership tests passed | Postman not tested |
| Atomicity/concurrency | Transactional member-row pessimistic lock before assessment lock; atomic level upsert and completion; downgrades allowed | H2 tests passed for rollback of first insert and existing-level update, concurrent creation, duplicate completion, cancel versus complete | SQL Server lock behavior and migration still require verification |
| Documentation | API guide, nine Postman requests and manual SQL Server migration added | Collection parses as JSON; existing folders preserved; `git diff --check` passed | Collection syntax validation is not API verification |

### Test and build evidence

- The Maven Wrapper still fails before Maven starts: `Cannot index into a null array`. It was not modified.
- A cached Maven 3.9.16 executable was found at `C:/Users/Asus/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0/bin/mvn.cmd`.
- Maven's default local repository resolved incorrectly to `C:/.m2/repository` in this execution environment; commands explicitly used `-Dmaven.repo.local=C:/Users/Asus/.m2/repository`. A sandboxed compiler dependency access failed, and an approved run outside the sandbox compiled the source. The offline run then lacked Surefire's JUnit runner; the approved online run downloaded the runner without changing project dependencies.
- Focused command: cached `mvn.cmd -B -Dmaven.repo.local=C:/Users/Asus/.m2/repository -Dtest=*ServiceTest,SkillAssessmentIntegrationTest test`. **53 tests passed: 19 new integration tests, 27 existing ClassSession tests, 7 existing Facility tests; zero failures/errors/skips.** Maven reported BUILD SUCCESS.
- Full suite and package command: cached `mvn.cmd -o -B -Dmaven.repo.local=C:/Users/Asus/.m2/repository -Dspring.profiles.active=phase1 package`. Maven reported **BUILD SUCCESS, 54 tests passed, zero failures/errors/skips**, including existing `contextLoads`. The repackaged executable JAR was created. Output was captured in `target/phase1-build.log`; PowerShell surfaced Mockito/JDK stderr warnings as NativeCommandError and the enclosing tool returned 1 despite Maven's successful summary. The reports in `target/surefire-reports` independently confirm all four test classes passed.
- A separate cached Maven `-o -B -Dmaven.repo.local=C:/Users/Asus/.m2/repository -DskipTests package` check completed with **exit code 0 and BUILD SUCCESS** after the full test run; this confirms packaging independently without rerunning tests.
- `application-phase1.properties` is test-only: isolated H2 in-memory datasource, create-drop schema, and a deliberately public test signing key. No production database or local credentials were used. The full suite explicitly selected this profile so the unchanged existing context test did not connect to SQL Server.
- Test coverage includes assignment by staff, invalid/missing/inactive resources, missing qualification, duplicate PENDING, coach/member ownership and lists, all four roles through the actual security filter and signed JWTs, first level insertion, reassessment/downgrade, invalid levels/notes, terminal transitions, cancellation, transactional rollback and three concurrent-request scenarios.
- No Postman requests were executed and no SQL Server migration or production database test was performed. H2 verification does not establish SQL Server filtered-index or locking behavior.

### Schema and deployment notes

Run `docs/sql/phase1-member-sport-level-assessment.sql` after backup and before enabling the feature, with writers stopped. It adds only two tables and their constraints/indexes, uses current default snake_case/dbo naming, and supports tables already created by Hibernate. It includes a SQL Server filtered unique index for PENDING assessments that Hibernate `ddl-auto=update` cannot generate. Existing pending duplicates cause migration failure; the script does not delete history. Manual rollback instructions discard only the new tables and require an explicit decision to discard their data.

All assessment mutations lock the member User row first, then assessment where applicable. This intentionally serializes different sports for a member and also protects a level that does not yet exist. The most recent successful serialized completion sets current level even when it downgrades; timestamps may tie, and transaction order defines the result. External level writers must use the same lock protocol. SQL Server deadlock/timeout behavior remains a deployment verification item; failed transactions can be retried by the caller and no automatic retry is introduced.

No CourseRegistration, Payment, scheduling, notification, frontend, authentication or account-creation behavior was added or changed. Membership, ClassSession, and Facility source and existing tests were preserved. No dependencies were added, no secrets or `.env` files were read/modified, and no commit/push/merge was performed.

### Files added or modified

Added under `src/main/java/group5/example/sportCenter/`:

- `entity/MemberSportLevel.java`, `entity/SkillAssessment.java`, `entity/SportLevel.java`, `entity/AssessmentStatus.java`.
- `dto/CreateSkillAssessmentRequest.java`, `dto/CompleteSkillAssessmentRequest.java`, `dto/SkillAssessmentResponse.java`, `dto/MemberSportLevelResponse.java`.
- `repository/SkillAssessmentRepository.java`, `repository/MemberSportLevelRepository.java`.
- `service/SkillAssessmentService.java`, `service/MemberSportLevelService.java`, `service/AssessmentAccess.java`.
- `controller/SkillAssessmentController.java`, `controller/MemberSportLevelController.java`.

Also added `src/test/java/group5/example/sportCenter/service/SkillAssessmentIntegrationTest.java`, `src/test/resources/application-phase1.properties`, and `docs/sql/phase1-member-sport-level-assessment.sql`.

Modified `config/SecurityConfig.java` and `repository/UserRepository.java` under the main Java package, plus `docs/API_GUIDE.md`, this status document, and `docs/postman/SportCenter-API.postman_collection.json`.


## Phase 1 SQL Server migration hardening (2026-10-12)

Changes are limited to the migration, SkillAssessment notes mapping, Phase 1 Unicode test/test profile, and documentation. No SQL Server connection or migration execution was performed; no existing ClassSession, Facility, Membership, authentication, or security business logic changed.

- Added all seven required filtered-index SET options, retained XACT_ABORT and transactional rollback, added a SportCenterDB guard and rejection of outer transactions. Transaction-owned sp_getapplock serializes migration reruns; exclusive Phase 1 table locks protect preflight and changes.
- SkillAssessment.notes now has Hibernate @Nationalized and length 1000, selecting NVARCHAR(1000) on SQL Server. The migration upgrades only the known nullable built-in VARCHAR(1000) notes column; incompatible other representations fail. Previously lost characters cannot be reconstructed.
- Verified the default dbo/snake_case physical mappings, Integer/INT IDs, enum VARCHAR(20), nullable relationships, timestamp DATETIME2(6), and all seven foreign keys against current entity source. The API guide records the full table/column/type/FK contract.
- Added strict metadata checks for referenced primary keys, expected columns/types/lengths/precision/nullability/identity, table shape, Phase 1 primary keys, FK targets/trust/enabled state/cascade behavior, enum CHECK definitions, and unique index key/filter/enabled state. Missing FKs/checks are created WITH CHECK; duplicates, invalid enums and orphaned references abort and roll back. Invalid objects are never silently dropped/rebuilt. Extra unique indexes are rejected because they may forbid terminal history. Strict CHECK/filter fingerprints can reject equivalent noncanonical definitions and need manual review.
- Preserved unfiltered unique current level (member_id, sport_id) and filtered unique PENDING assessment (member_id, sport_id). Compatible reruns skip existing objects and an already completed Unicode conversion.
- Extended the existing HTTP completion test with real Vietnamese notes and verified their persisted value. A UTF-8 reread and compiled-class check confirmed the fixture retains non-ASCII characters. H2 test configuration now uses MODE=MSSQLServer and hibernate.hbm2ddl.halt_on_error=true. This resolves the previously observed H2-only unknown DATETIME schema warning without modifying ClassSession source and makes future schema-generation errors fail startup.

| Verification for this hardening | Result |
|---|---|
| SQL syntax | Microsoft SQL Server ScriptDom TSql160Parser: zero parse errors; syntax parsing only, no SQL execution |
| Automated tests | Cached Maven offline test command selecting SkillAssessmentIntegrationTest: 19 tests passed, zero failures/errors/skips, BUILD SUCCESS, exit 0; schema-error fail-fast enabled |
| Main/test compilation | Passed as part of Maven test; compiled entity includes Nationalized annotation |
| Existing full suite/package | Prior 54-test/package result above; not rerun for this hardening |
| Whitespace | git diff --check passed |
| SQL Server migration/runtime/rerun | Not executed or verified |
| Postman | Not executed for this hardening |

Remaining deployment verification: SQL Server 2016+ and adequate DDL/metadata permissions, database guard/default dbo physical naming, actual existing CHECK/filter definitions, migration and rerun behavior, Unicode storage, index enforcement, and JDBC-session SET options. Migration-session SET statements do not configure the connection pool. Parser/H2 success does not establish SQL Server runtime safety. No dependency additions, live database modifications, or Git commit/push/merge/pull/reset operations occurred.

## Phase 1.5 Consultation Requests (2026-10-12)

| Area | Code status | Build | Automated tests | SQL Server / Postman |
|---|---|---|---|---|
| ConsultationRequest entity/API | Implemented in source; role checks, ownership, status transitions, pagination/filtering, and locked take operation added | Maven test lifecycle passed (compile + tests) | Integration tests passed as part of 67/67 total | Migration provided but not executed; Postman not tested |
| Automatic MEMBER creation/linking | Implemented using existing BCrypt-backed `MemberRegistrationService` in the same REQUIRED transaction as linking | Passed Maven test lifecycle | success, duplicate email, and simulated link-write rollback cases passed | Not tested |
| Existing MEMBER linking | Implemented for selected ACTIVE MEMBER ID; no contact matching | Passed Maven test lifecycle | Valid, repeat same ID, different ID, inactive, wrong-role/nonexistent and wrong-handler cases passed | Not tested |
| SkillAssessment integration | Nullable consultation relation and optional request field added; standalone constructor/request remains supported | Passed Maven test lifecycle | linked same member/sport/status and standalone paths passed | Not tested |
| SQL migration | Idempotent SQL Server script added; creates consultation table and nullable assessment FK/index | N/A | Not parsed/executed by SQL Server | Not executed; manual execution only |

### Verified project conventions and business decisions

- Existing `User.email` is required and unique. Guest consultation email remains optional, but `/create-member` returns 400 when it is absent and recommends explicitly linking an existing MEMBER instead.
- Existing `CreateMemberRequest` requires date of birth and a staff-supplied password; existing `PasswordConfig` supplies BCrypt. `/create-member` therefore requires date of birth and a staff-selected password, reuses `MemberRegistrationService`, and never returns credentials. No default password, activation, or reset workflow was found or invented.
- MEMBER identity comes only from JWT subject on consultation submission. The DTO has no `memberId` field. Guest records keep `member_id` null.
- `PESSIMISTIC_WRITE` on the ConsultationRequest row serializes take/state/link operations. The SQL Server locking behavior still needs real-database verification; tests use the isolated H2 MSSQL mode.
- `server.error.include-message=never`, `server.error.include-exception=false`, and `server.error.include-stacktrace=never` are set to suppress internal default-error details; these configuration-only additions were not covered by a Maven rerun.
- Statuses are terminal after COMPLETED/CANCELLED. Assessment completion does not change consultation status; consultation cancellation does not mutate assessment or level history.
- Migration and runtime database were not executed or modified. No Postman result is claimed for Phase 1.5.
- Default SQL Server JPA mode is now `validate`, preventing a later application startup from auto-creating the new table/column. Run the migration manually first. The test-only Phase 1 H2 profile still uses `create-drop`.

### Test/build report

The Phase 1/Phase 1 migration verification documented above predates these Phase 1.5 changes; the 67-test run below includes the current suite and new Phase 1.5 tests. Offline Maven 3.9.16 test command completed successfully: **67 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Maven compiled 129 main and 5 test source files. This test lifecycle did not package a deployable artifact. New integration tests cover guest/MEMBER submission, lifecycle, role/handler ownership, concurrent take, account create/link rollback, explicit existing-member linking, linked assessment validation, and standalone assessment compatibility. No Postman or SQL Server execution is claimed.

After the successful test run, the request DTO was given a redacted `toString`, and default SQL Server schema/error-response settings were hardened (`ddl-auto=validate`; omit default error message, exception type, and stack trace). The test profile overrides DDL mode with isolated H2 `create-drop`. A rerun was declined at the command-approval step, so these final defensive changes have not been retested; the 67-test result covers the preceding Java/entity/API/lifecycle changes.

## Phase 2: CourseClass Registration (2026-10-13, revised)

| Area | Code | Build and automated tests | Postman |
|---|---|---|---|
| MEMBER self-registration, own registration/session reads | Implemented against selected CourseClass; JWT supplies member identity, hierarchy supplies Course and Offering | Full suite passed: 90 tests, 0 failures/errors/skips; latest verification below | Not tested |
| ADMIN/RECEPTIONIST registration list | Implemented; GET-only role restriction retained | Full suite passed: 85 tests, 0 failures/errors/skips | Not tested |
| Capacity and concurrency | Per-Class capacity count protected by CourseClass pessimistic lock; member row lock serializes same-member checks; migration adds filtered unique member/class guard | H2 concurrent-last-seat and duplicate attempts passed in full suite | Not tested |
| SQL Server migration | Idempotent, non-destructive Class-based migration at `docs/sql/phase2-course-registrations.sql`; includes price snapshots; not executed | Static review only; no SQL Server/parser execution claimed | Not applicable |
| Cancellation | Omitted pending confirmed cancellation policy | Not applicable | Not applicable |

The final registration unit is one CourseClass, not an entire Course or an individual ClassSession. Existing discovery routes are `GET /api/course-offerings/course/{courseId}` and `GET /api/classes/offering/{offeringId}`. CourseOffering must be `OPEN`, which is the state used by the existing offering creation workflow. The deadline is exclusive: registration is rejected at and after `registrationDeadline`. No cancellation, Payment, or Bill behavior was added. The migration was not run; no SQL Server was contacted. The integration suite asserts the datasource is isolated `jdbc:h2:mem:phase1` and not SportCenterDB.

### Current Phase 2 verification

- Previous baseline before the membership-discount enhancements: full Maven suite had **85 tests**.
- Test profile datasource is `jdbc:h2:mem:phase1;MODE=MSSQLServer;...`; the CourseRegistration integration test asserts the live test connection uses that in-memory URL and does not contain `SportCenterDB`. No production/development SQL Server was contacted.
- Package build (`package -DskipTests`): **BUILD SUCCESS, exit code 0**; executable JAR created in `target/`.
- `mvnw.cmd` remains unusable in this PowerShell environment (`Cannot index into a null array`), so the cached Maven 3.9.16 executable was used. A first test run exposed a test-fixture issue: a one-nanosecond deadline offset was truncated by database timestamp precision. The fixture now uses a one-second offset; no production rule was changed to resolve it.
- CourseClass tests cover selection/hierarchy response, sessions scoped to the enrolled class, exclusive deadline boundary (reject exactly at deadline), active/inactive eligibility, age and verified level checks, capacity/full class, duplicate and same-offering rejection, concurrent last-seat and duplicate attempts, schedule overlap versus touching endpoints, JWT identity/role boundaries, and persistence rollback.
- The integration test verifies concurrency behavior only under H2 MSSQLServer compatibility mode. SQL Server locking/filter-index enforcement and the migration still require validation against a disposable SQL Server database before deployment. Migration was not run. Postman verification is pending.
- `git diff --check` completed without whitespace errors; Git reported only existing LF-to-CRLF normalization warnings for two modified text files.

### Phase 2 registration window and membership price snapshots (2026-10-13)

- Registration opens on `CourseOffering.startDate.minusDays(30)`; registration before that date returns 409. The existing registration deadline remains exclusive: requests at or after the deadline return 409. Both comparisons use one captured application-clock instant in `Asia/Ho_Chi_Minh`, matching the project's existing scheduler timezone; no global timezone setting changed.
- Membership discount is optional and uses only Membership rows with `ACTIVE` status and `startDate <= registrationDate < endDate`. `EXPIRED` rows are not reactivated. If multiple eligible rows exist, the highest valid `courseDiscountPercent` is used without stacking; one valid Membership may discount multiple separate registrations. `autoRenew` is an intent flag only and does not bypass expiration or perform a renewal.
- Registration stores `originalPrice`, monetary `membershipDiscount`, and `finalPrice` as `DECIMAL(12,2)` snapshots. The server calculates the amount using BigDecimal and HALF_UP rounding, rejects an invalid negative Offering price, and keeps `finalPrice` non-negative. Membership/plan/offering changes do not recalculate stored snapshots, and the request body remains `{"classId":...}` only.
- Membership validity uses exclusive `endDate`: it is date-valid only when `startDate <= today < endDate`. All ACTIVE rows with `endDate <= today` now transition to EXPIRED, regardless of `autoRenew`. `autoRenew` remains an intent flag only and does not extend dates, create replacement memberships, or charge payment; renewal is pending a payment-backed workflow. The scheduled job and manual ADMIN endpoint share this service behavior. Existing CourseRegistration discounts and stored price snapshots are unchanged by expiration.
- `docs/sql/phase2-course-registrations.sql` now creates and strictly validates all three non-null DECIMAL(12,2) snapshot columns. It remains a manual migration and was not executed. Existing incompatible tables fail preflight; the script does not automatically rebuild or backfill them.
- Full isolated H2 test suite passed after the expiration and migration-validation changes: **92 tests, 0 failures, 0 errors, 0 skipped; exit code 0**. Counts: ClassSession 27, ConsultationRequest 19, CourseRegistration 19, Facility 7, SkillAssessment 19, application context 1. CourseRegistration coverage includes the 30-day boundary, deadline, membership validity boundaries, highest discount/no stacking, zero/100/fractional percentages, HALF_UP rounding, unchanged snapshots after plan/price/membership changes, request price tampering, expiration independent of autoRenew, idempotent expiration, authorization, no discount after expiry, and preserved price snapshots.
- Test database was the isolated `jdbc:h2:mem:phase1;MODE=MSSQLServer` profile; the integration test asserts the connected URL is in-memory H2 and not `SportCenterDB`. No SQL Server connection or migration was performed.
- The Phase 2 migration now validates same-named supporting indexes against their exact non-unique, unfiltered, enabled, non-hypothetical key structure, including ASC/DESC order and no included columns. An incompatible same-named index fails with a clear error and transaction rollback; it is never dropped or rebuilt. This remains a static source change only, and the migration is still unexecuted.
- Test database was the existing `jdbc:h2:mem:phase1;MODE=MSSQLServer` profile; the integration test checks that the connected JDBC URL is in-memory H2 and does not contain `SportCenterDB`. No SQL Server connection or migration was performed.
- Offline Maven 3.9.16 `package -DskipTests`: **exit code 0**. Maven Wrapper remains unusable in this PowerShell environment (`Cannot index into a null array`), so the cached Maven executable was used. Postman tests were not run or claimed.
- Final verification on this update: cached Maven 3.9.16 `test` and `package` both completed with **BUILD SUCCESS**; each ran 92 tests with 0 failures, 0 errors, 0 skipped. The SQL Server migration remains unexecuted.

### Phase 6.1A Notification Core Backend

- Added owner-scoped notification listing, unread count, mark-one-read, and mark-all-read APIs for ADMIN, RECEPTIONIST, COACH, and MEMBER. Ownership is resolved from the JWT subject and active User record. Another user's or unknown notification ID returns 404. No notification-send endpoint is exposed; `NotificationService.createForUser` is the internal event-integration seam.
- Listing is paginated (default page 0, size 20; maximum size 100) and newest-first. Mark-one and mark-all are idempotent; mark-all uses a bulk database update restricted to the authenticated owner. DTOs expose only `id`, `title`, `message`, `isRead`, and `createdAt`.
- Manual migration: `docs/sql/phase6-1a-notifications.sql`. It creates or validates `dbo.notification`, uses an `INT` FK to `dbo.users`, a false default for `is_read`, `DATETIME2(6)`, a user/newest index, and a filtered unread index. Existing incompatible table metadata fails closed. The migration has not been executed and needs SQL Server review plus Hibernate `ddl-auto=validate` after manual application.
- Phase 6.1A focused tests: **8 passed**. The full isolated H2 suite after the core implementation: **191 passed, 0 failures, 0 errors, 0 skipped**. No SQL Server connection or migration execution was performed.
- Remaining deployment checks: verify the updated migration and Hibernate mapping on a disposable SQL Server database, especially DECIMAL precision/scale, lowercase snake_case physical naming and filtered-index/session behavior. SportCenterDB migration remains pending.

### Phase 6.1B ADMIN Broadcast Notifications

- Added `POST /api/admin/notifications/broadcast`, restricted to ADMIN, to create one unread personal notification for every ACTIVE account with one of the four supported roles, including the initiating ADMIN. Request validation limits title to 150 characters and message to 5,000 characters; recipients, sender, read state, and timestamp are never selected from request fields.
- Recipient IDs are selected in one query and written in JDBC batches under the service transaction. A persistence failure rolls back all rows. The response count is derived from successful insert results. Each request is a separate broadcast; network retries can create duplicates because the schema intentionally has no broadcast ID/idempotency key. Status changes concurrent with the selected-recipient query may affect who receives that broadcast.
- No database migration or schema change is required. Verification: focused Notification core and broadcast tests **13 passed, 0 failures, 0 errors, 0 skipped**; full isolated H2 suite **196 passed, 0 failures, 0 errors, 0 skipped**. No SQL Server connection or migration execution was performed.

### Phase 6.1C Automatic Business Notifications

- Added internal typed application events for newly created consultations, linked consultation completion/cancellation, PT booking confirmation, and CourseClass Coach assignment/reassignment. No public notification-send endpoint or database migration was added.
- Events are handled with `@TransactionalEventListener(AFTER_COMMIT)`. A separate `NotificationEventDeliveryService` persists notifications under `REQUIRES_NEW`; the listener catches and logs delivery failures so committed business operations and their response contracts remain authoritative.
- Recipients are resolved from persisted IDs and account status/role. New PENDING consultations notify ACTIVE RECEPTIONIST accounts only. Completion/cancellation notify only a linked ACTIVE MEMBER. PT confirmation notifications go to the ACTIVE booking MEMBER and ACTIVE User linked from the assigned Coach. Class creation or actual Coach reassignment notifies only the newly assigned Coach's ACTIVE linked User. No event is sent for failed/rolled-back operations, repeated terminal transitions, failed/late/refund-required payments, booking expiration, or unchanged Coach assignments.
- Delivery is best-effort rather than an outbox: a process crash after the business commit can lose a notice, and no exactly-once/recovery guarantee is claimed. Notification persistence failures are logged and do not roll back the business transaction.

### Phase 8.4B CourseOffering and ClassSession stabilization

- CourseOffering creation now requires a non-past start date, end date after start, future registration/cancellation deadlines, both deadlines no later than the offering start, and registration deadline on or before cancellation deadline. The deadline-order rule is newly enforced. Updates preserve unchanged historical dates/deadlines, reject changed past values, and cannot exclude existing sessions.
- ClassSession create/update now require the persisted CourseOffering duration exactly in whole minutes as well as the inclusive offering date range and strict start-before-end ordering. A duration edit on an offering is rejected when existing sessions would no longer match. Existing records are never rewritten automatically.
- Member default course schedule omits CANCELLED sessions; authenticated administrative schedule APIs continue to return history. ClassSession status actions remain SCHEDULED→COMPLETED at/after end and SCHEDULED→CANCELLED before start, with terminal states unchanged; receptionist remains denied mutations.
- Approved numeric rules: new offerings require integer `plannedSessions` 1–60 and integer `sessionDuration` 30–180 minutes. Updates validate a numeric field only when it changes, preserving unchanged out-of-range legacy values during unrelated edits. Fractional JSON numbers are rejected. Changing sessionDuration still cannot invalidate persisted ClassSessions. No historical rows are rewritten.
- Read-only legacy check is documented in `docs/API_GUIDE.md` using `dbo.course_offering(planned_sessions, session_duration)`. The core schema bootstrap is not present in this repository, and this query has not been run against SQL Server; verify the physical columns against target metadata first.
- Numeric-rule verification: focused `CourseOfferingServiceTest` **10 passed, 0 failed, 0 errors, 0 skipped**; full clean isolated H2 suite **230 passed, 0 failed, 0 errors, 0 skipped**. Frontend ESLint and Vite production build passed. No frontend test runner is configured; manual browser/API and SQL Server checks remain pending.
- Verification: focused CourseOffering, ClassSession, CourseRegistration and authorization tests passed (**64 tests, 0 failures/errors/skips**); full clean isolated H2 suite passed (**225 tests, 0 failures/errors/skips**). Frontend ESLint and Vite production build passed. Manual browser/API and SQL Server checks remain pending; no SQL Server connection, SQL execution, or migration was performed.

### Phase 8.4D Automatic ClassSession schedule generation

- Added ADMIN-only `POST /api/class-sessions/generation/preview` and `/confirm`. Both accept `courseClassId`, ISO weekday names, a timezone-less daily `startTime`, optional `facilityId`, optional `firstEligibleDate`, and optional positive `requestedSessionCount`. The offering's persisted `sessionDuration` determines each end time.
- Recurrence scans matching weekdays chronologically from the requested date (default: offering start) through the offering end date inclusive. Preview is read-only and reports every proposed timestamp plus persisted and candidate conflicts; it never skips a conflicting date to fill the requested count.
- `plannedSessions` is enforced as a target per CourseClass. Existing non-CANCELLED sessions count whether manually or automatically created. CANCELLED rows remain in history but do not consume a slot. Manual creation shares the target rule. Generation never replaces/deletes sessions.
- Confirm recomputes and validates the preview under one database transaction, then creates every session through the existing ClassSession service. Any invalid candidate or persistence failure rolls back the batch. Application write paths coordinate with pessimistic locks in CourseClass → CourseOffering → Facility (if selected) → Coach order. Preview may become stale; confirmation revalidates.
- There is no durable idempotency key or database constraint for overlapping intervals. Repeated requests are separate attempts, not exactly-once operations. Locks coordinate application paths only; direct SQL or non-cooperating writers can still race. No schema change or SQL migration was added.
- Verification: focused `ClassSessionGenerationIntegrationTest`, `ClassSessionServiceTest`, `CourseOfferingServiceTest`, `FacilityServiceTest`, and `CourseClassNotificationEventsIntegrationTest` passed (**57 tests, 0 failures, 0 errors, 0 skipped**). Full isolated H2 backend suite passed (**239 tests, 0 failures, 0 errors, 0 skipped**). Frontend `npm run lint` and `npm run build` passed; `git diff --check` passed. No SQL Server or browser testing has been performed.

### Phase 7.1 Minimal AuditLog Backend

- Added the append-only `AuditLog` model and one manual table migration, `docs/sql/phase7-1-audit-log.sql`. It stores only actor ID/role, action, logical entity identity, a short description, and UTC timestamp; the non-cascading actor FK preserves history and UserService rejects physical deletion of an actor referenced by audit rows.
- Selected User, MembershipPlan, ConsultationRequest, Attendance, and ADMIN notification-broadcast mutations write AuditLog in the same transaction. No audit write occurs for reads, no-op updates, failed transitions, repeated attendance status, or password changes. Broadcast produces one audit row, not one per recipient.
- Added ADMIN-only `GET /api/admin/audit-logs` with server-side filters, page/size bounds, newest-first ordering, and ISO-8601 offset date ranges normalized to UTC (`from` inclusive, `to` exclusive). No API write/update/delete operations are exposed.
- Verification: focused AuditLog and affected-module H2 suites passed; full isolated backend suite **213 passed, 0 failures, 0 errors, 0 skipped**. `git diff --check` passed. The manual SQL migration has not been executed; SQL Server schema validation and Postman/live API verification remain pending. This work is on `feature/notification-core`, which includes the notification commits but is not yet an ancestor of `origin/main`.

### Phase 8.4E MEMBER course discovery and registration UX

- Added the guarded `/member/courses/:courseId` detail route while preserving the public `/khoa-hoc/:courseId` route for guests. The legacy public URL redirects authenticated MEMBERS to the member route; course-list Back navigation now returns to `/member/courses`. MEMBER login state is retained in `sessionStorage` so direct member URLs survive a page refresh in the same browser session.
- The MEMBER page reuses the public course detail endpoint and existing offering, class, class-session, profile, skill-level, and registration APIs. It shows offering dates, status, registration deadline, price, planned session count and duration; class Coach, capacity and published session times are displayed only when returned by their APIs. The class capacity is explicitly not described as remaining seats.
- Registration requires explicit class selection and a confirmation summary, submits only `{ "classId": ... }` to `POST /api/course-registrations`, prevents duplicate submission, and refreshes `GET /api/course-registrations/me` after success. Confirmed registrations block another registration in the same offering. The UI labels enrollment as CONFIRMED independently of payment and creates no Payment.
- Members can request a consultation from the course detail without selecting a class. The existing member consultation endpoint derives identity from JWT; the request supplies the selected Sport and course/class context as a note, and does not create a registration or assessment. Successful submissions link to the existing consultation history at `/member/levels-assessments`. Public guest consultation remains on the public route.
- The course list now shows confirmed registration history and links to the member schedule. Backend endpoints and response contracts were not changed; API_GUIDE.md therefore required no contract update. Current class APIs provide capacity but no remaining-seat count, so the page does not invent availability.
- Verification: frontend ESLint and Vite production build passed; `git diff --check` passed. No frontend test runner is configured and no test dependency was added. Browser and live API checks remain manual; verify MEMBER versus guest routing, direct refresh, offering/class selection, registration success/duplicate/conflict paths, consultation submission, and mobile layout. Backend code was not changed in this phase.

### Phase 8.5C Selective feflow3 frontend review

- Compared the `origin/feflow3` frontend routing, API helpers, shared navigation/styles, Member course and membership pages, payment/checkout, attendance, reports, profile, and support screens against the `integration/phase8.5` baseline. The branch reference inspected was `e263a0f0ecc0cf6cc7afaab3157c6cb974fea66b`.
- No feflow3 page implementation was copied. Its Member course page ties enrollment to checkout, its membership page offers online/auto-renew behavior, and its checkout exposes simulated payment success; those flows conflict with current registration and pending counter-payment semantics. Its attendance implementation omits current correction/history behavior and assumes response fields. Its API additions and reports calls are not adopted because their contracts are not established on this integration branch.
- The current Coach and Staff Attendance screens already share status badge styling for PRESENT, ABSENT, and EXCUSED, so the small feflow3 status-color rules are redundant. Current routing, role guards, sessionStorage authentication, Member registration/consultation/membership flows, receptionist payment confirmation, Coach/Admin attendance, notifications, AuditLog, and the authoritative CourseManagement UI remain unchanged.
- `backend/run.bat` and credential-like configuration from feflow3 were not integrated. No backend or API contract changed. No browser or live SQL Server verification was performed; manual responsive and role-navigation checks remain pending.

### Phase 8.5D Integrated application smoke-test readiness review

- Preflight: branch `integration/phase8.5` at `903590bef65f96769e214a4e13582c59948e8d4a`; the existing uncommitted change to this status document was preserved. No merge/rebase state was present. `origin/feflow3` is at `e263a0f0ecc0cf6cc7afaab3157c6cb974fea66b`; Git tree metadata confirms it no longer tracks `backend/run.bat`. It was not merged.
- Startup configuration inspection found the default backend datasource targets local `SportCenterDB`, takes credentials from environment placeholders, uses `ddl-auto=validate`, and has mock-gateway enablement defaulting false. No backend was started because the configured SQL Server target is not established as an isolated test database. Tests explicitly annotated with `phase1` use in-memory H2; SQL Server E2E tests are a separate profile and were not invoked.
- Frontend route review found role guards for staff, ADMIN, RECEPTIONIST, COACH, and MEMBER routes; public guest routes remain available. Exact static route-path scan found no duplicate paths. Sidebars link to registered routes or deliberate redirects. CRUD sample pages and the static report page label themselves as demos. No browser navigation, session restoration, logout, or authorization test was performed.
- Static contract review confirmed the inspected generation, registration, consultation, counter-payment, attendance, notification, and AuditLog frontend calls have corresponding controllers in this branch. No concrete mismatch was confirmed by static inspection. Runtime request/response compatibility remains unverified.
- Automated checks: frontend ESLint passed; Vite production build passed with a non-failing warning that the minified JavaScript chunk exceeds 500 kB; `git diff --check` and conflict-marker scan passed. The full backend H2 suite could not be run: `mvnw.cmd test` failed before Maven startup with a PowerShell wrapper `Cannot index into a null array` error, and no system Maven installation was available. Prior 239-test results are historical and are not claimed as a run for this review. No frontend test runner was found.
- P2 release-readiness issue: after JWT expiry, the API client surfaces 401 errors but does not clear the stored auth state or redirect to login. Reproduce by logging in, allowing the token to expire, then loading a protected page or triggering an API request. Expected: return to login or show a consistent reauthentication flow; current behavior can leave the user on an authenticated-looking screen with request errors. Relevant files: `frontend/src/App.jsx` and `frontend/src/api.js`. No fix was made in this review.
- End-to-end scenarios A–F (schedule generation; member course registration and schedule; consultation ownership/history; membership counter-payment confirmation; Coach/Receptionist attendance authorization; notifications/AuditLog) are NOT VERIFIED against a running browser/API or database. They require test accounts, seed records, and an explicitly approved isolated SQL Server database. No schema or data changes were made.

### Phase 8.5E JWT expiration handling and backend test recovery

- The shared frontend API client now signals the application only for HTTP 401 responses from requests carrying a Bearer token. The app clears `sportcenter.auth`, updates React auth state, and redirects to `/login` with a short session-expired message. A per-token guard prevents repeated concurrent 401s from causing repeated logout/navigation, and stale responses cannot expire a newer login. Login requests and public requests have no Bearer token and do not trigger this path; HTTP 403 remains an authorization error. Existing route guards and sessionStorage login persistence are unchanged. No refresh-token workflow was added.
- Direct navigation or refresh with a stored but expired/invalid token is handled when that page's authenticated API request returns 401. The app does not decode JWT expiry locally or redirect before a request is made.
- Maven recovery: the documented `C:\Users\Asus` cached executable was absent. The same Maven 3.9.16 distribution existed under the active `C:\Users\pc` profile. `mvnw.cmd` fails before launch because its PowerShell script indexes `.Target[0]` on the ordinary `.m2` directory, whose `Target` is null. The cached Maven executable was invoked directly with `-Dmaven.repo.local=C:\Users\pc\.m2\repository -Dspring.profiles.active=phase1 test`.
- Backend verification: exit code **0**, **239 tests run, 0 failures, 0 errors, 0 skipped**. This run used Java **25.0.4.1** (the available runtime), Maven **3.9.16**, and the isolated in-memory H2 `phase1` profile; the SQL Server E2E profile was not run. Auth-specific reports within the full run: `AuthControllerIntegrationTest` **3 passed** and `CurrentAccountAuthorizationIntegrationTest` **6 passed**.
- Frontend verification: `npm run lint` passed; `npm run build` passed with the existing advisory that the minified JS chunk exceeds 500 kB. No frontend test runner is configured. `git diff --check` passed. No SQL Server access or migration was performed.
- Manual browser checks still required: successful login; expired stored token on direct protected-page navigation and refresh; several simultaneous 401 responses produce one redirect; 403 leaves the session intact and shows authorization feedback; guest/public pages remain usable; manual logout clears the session and returns to login. No browser test was performed.

### Phase 8.5F Live SQL Server integration and E2E verification

- Git remained on `integration/phase8.5` at `903590bef65f96769e214a4e13582c59948e8d4a`; Phase 8.5E edits were preserved. No merge/rebase was in progress, no SQL migration ran, and no database records were created or changed.
- Static config confirms the datasource is configured for `localhost:1433/SportCenterDB`, credentials come from `DB_USERNAME`/`DB_PASSWORD`, Hibernate uses `ddl-auto=validate`, and mock-gateway enablement defaults false. Those credential environment variables are absent in this execution environment; no `.env` file was opened. The TCP port check succeeded, but read-only `sqlcmd` authentication failed because no integrated-auth credentials were available. Therefore the server/database identity, schema inventory, and Hibernate validation could not be confirmed. No recent verified backup could be confirmed. Per the safety gate, all live database write scenarios were stopped before fixture creation.
- Backend startup was not attempted because application credentials were unavailable and the target database/backup could not be verified. The frontend default API URL is `http://localhost:8080`; the backend CORS default permits `http://localhost:5173`. Vite started at `http://localhost:5173/` and returned HTTP 200 for `/`. This is an HTTP server check, not browser verification; the backend was not running, so no API request was tested.
- Verification matrix: ADMIN scheduling — **BLOCKED** (no authenticated database/API session or fixtures); MEMBER registration — **BLOCKED**; consultation — **BLOCKED**; membership counter-payment — **BLOCKED**; attendance — **BLOCKED**; notifications/AuditLog — **BLOCKED**. Authentication, role-negative APIs, direct refresh, logout, and public-page browser behavior — **NOT TESTED**. No test record IDs were created.
- Automated checks: isolated H2 backend suite **239 passed, 0 failures, 0 errors, 0 skipped** (Maven 3.9.16, Java 25.0.4.1, `phase1` profile; exit code 0). Frontend lint passed. Vite production build passed with a non-failing >500 kB minified-chunk warning. `git diff --check` passed. No SQL Server E2E or browser tests were run.
- Security follow-up: `backend/docs/sql/README.md` contains a tracked plaintext demo-account password (value intentionally omitted here). Since the actual database was not authenticated, whether the corresponding demo account exists in `SportCenterDB` is unknown. Do not use that credential for live verification; the deployment/database owner should confirm seed-account presence and rotate or disable it as appropriate before shared-environment testing. No credentials were changed.
- Next gate: provide the approved DB credential injection through the normal application environment without modifying `.env`, and confirm a recent verified backup. Then recheck `DB_NAME()`, inspect schema metadata, and start the backend with `ddl-auto=validate`; only after those checks should controlled, uniquely labeled test fixtures be created.
### Phase 8.6B Receptionist pending membership payment queue

- Added staff-only `GET /api/payments/pending-memberships?page=0&size=20`, returning a page of existing `PaymentResponse` DTOs. SecurityConfig and the service restrict access to ADMIN/RECEPTIONIST with the existing active-account checks. Page must be nonnegative; size is 1–100. Server-side predicates are exactly PENDING, COUNTER, MEMBERSHIP, ordered by `createdAt DESC, id DESC`. Existing member history, payment creation, and settlement semantics are unchanged; there is no schema change.
- Receptionist membership sales now loads the cross-member queue on entry, supports pagination and direct payment selection, and synchronizes member/plan context. Plan names use the existing public plan catalog with an explicit plan-ID fallback when unavailable. Queue responses use only the existing DTO, without adding personal information. Member-first lookup and manual counter sales remain available. ONLINE payments have no counter actions. Cancellation explicitly says the payment is canceled and cannot subsequently be confirmed.
- Existing counter confirmation remains authoritative for payment settlement and membership activation. After settlement, the queue and selected member histories refresh; errors are surfaced and success is reported only from the returned payment status. Queue loading, empty/error states, 401/403, stale-response protection, and empty final-page recovery are handled. No live payment was processed.
- Removed the receptionist support navigation entry and obsolete layout/navigation mappings. The guarded legacy `/receptionist/support` URL redirects to `/staff/consultations`; SupportPage is no longer imported or rendered. Its unused source file remains preserved. The real ConsultationQueue is unchanged. No SupportTicket entity, table, or API was introduced.
- Verification: PaymentIntegrationTest **23 passed**; full isolated H2 `phase1` suite **242 passed, 0 failures, 0 errors, 0 skipped**, including staff/forbidden/anonymous access, filter isolation, pagination and creation-time ordering, successful confirmation removing a queue entry and activating one membership, and canceled/repeated confirmations creating no extra membership. Frontend lint and production build passed; build retains the existing >500 kB chunk advisory. `git diff --check` passed. Java required execution outside the sandbox to read its installed security configuration; the selected datasource remained isolated H2, with no SQL Server profile selected.
- Remaining manual checks: browser queue pagination/selection, member-first sales, receipt confirmation/cancellation, refreshed histories, ONLINE action exclusion, expired-session/403/error display, responsive layout, and the legacy support redirect. SQL Server schema/runtime compatibility and live API verification were not performed. Existing live Payment #2, `.env`, credentials, migrations, database state, Git history, and Phase 8.5E/F changes were left untouched. No commit, push, or merge was performed.
