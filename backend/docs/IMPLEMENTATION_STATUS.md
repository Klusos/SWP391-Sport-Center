# SportCenter Implementation Status

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

Overlap validation excludes CANCELLED sessions and uses strict interval overlap, checking both Facility and the Coach attached to CourseClass. Make-up sessions outside CourseOffering dates and a per-CourseClass count above `plannedSessions` are not blocked; response warnings report these conditions. Counting planned sessions per CourseClass is a provisional interpretation because the existing model does not document whether `plannedSessions` counts per class or across an offering. No automatic schedule generation, physical DELETE, schema changes to existing tables, or unrelated modules were added.

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
- Remaining deployment checks: verify the updated migration and Hibernate mapping on a disposable SQL Server database, especially DECIMAL precision/scale, lowercase snake_case physical naming and filtered-index/session behavior. SportCenterDB migration remains pending.
