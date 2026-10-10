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
