# SportCenter Backend API Guide

This guide was generated from the current Spring MVC controllers, DTOs, SecurityConfig, and MembershipService ownership checks. It describes the working-tree source; it is not a claim that every endpoint has passed integration testing.

## Base URL and authentication

- Local base URL: `http://localhost:8080` (set `baseUrl` in the Postman collection for another environment).
- Except for `POST /api/auth/login`, requests require `Authorization: Bearer {{token}}`.
- Valid role names are `ADMIN`, `RECEPTIONIST`, `MEMBER`, and `COACH`.
- `GET /api/users/me` permits any authenticated user. `POST /api/members` permits ADMIN and RECEPTIONIST. MemberProfile `/me` routes permit MEMBER; numeric-ID MemberProfile routes permit ADMIN. User management is ADMIN-only.
- MembershipPlan GET routes permit all authenticated roles; mutations are ADMIN-only. Membership access follows the ownership rules below.
- Sport, Course, CourseOffering, Class, Facility, Coach, and CoachSport GET routes permit all authenticated roles; mutation routes are ADMIN-only.
- CORS allows only explicit origins configured through `CORS_ALLOWED_ORIGINS` (default local React origins: ports 5173 and 3000). Wildcard origins are rejected.
- The response fields below follow the DTOs. Error response bodies can vary: `GlobalExceptionHandler` formats resource-not-found and `IllegalArgumentException` errors, while Spring Security, validation, and `ResponseStatusException` may use framework error bodies.

Common status codes: 400 for request validation or invalid role, 401 for missing/invalid JWT or invalid login credentials, 403 for a role/account that is not permitted, 404 for a missing resource, 409 for conflicts handled as `IllegalArgumentException` or duplicate MemberProfile. The not-found error shape is `{"status":404,"error":"Not Found","message":"..."}`; the conflict shape is similar with status 409 and error `Conflict`.

### Status and test evidence

- **Verified by user** means the user reported a successful Postman result in this conversation.
- **Not verified** means no Postman result was available during this review; the endpoint may still be implemented.
- Maven Wrapper has previously failed before Maven starts (`Cannot index into a null array`); verify the latest ClassSession source with a successful build before integration.

## Authentication

| Method and path | Allowed caller | Request body | Success and response | Test status |
|---|---|---|---|---|
| `POST /api/auth/login` | Public | `LoginRequest` | 200, `LoginResponse` | Not verified in this review |

```json
{"email":"member@example.com","password":"<password>"}
```

Success response shape (token value is illustrative):

```json
{"accessToken":"<JWT>","tokenType":"Bearer","expiresIn":7200,"userId":12,"name":"Example Member","email":"member@example.com","role":"MEMBER"}
```

Invalid credentials return 401; inactive accounts return 403. Confirm the exact framework error body with the running application before frontend integration.

## User and Member registration

| Method and path | Allowed caller | Request body | Success and response | Test status |
|---|---|---|---|---|
| `GET /api/users/me` | Any authenticated role | None | 200, `UserProfileResponse` | AGENTS.md says tested; not independently re-tested here |
| `GET /api/users` | ADMIN (all users); RECEPTIONIST (MEMBER/COACH only) | None | 200, `UserResponse[]` | Not verified after role-specific read changes |
| `GET /api/users/{id}` | ADMIN (all users); RECEPTIONIST (MEMBER/COACH only) | None | 200, `UserResponse`; 403 when RECEPTIONIST targets ADMIN/RECEPTIONIST | Not verified after role-specific read changes |
| `POST /api/users` | ADMIN | `CreateUserRequest` | 201, `UserResponse` | Not verified after role allow-list changes |
| `PUT /api/users/{id}` | ADMIN | `UpdateUserRequest` | 200, `UserResponse` | Not verified after role allow-list changes |
| `DELETE /api/users/{id}` | ADMIN | None | 204, empty body | Not verified |
| `PATCH /api/users/{id}/password` | ADMIN | `ChangePasswordRequest` | 200, text message | Not verified |
| `POST /api/members` | ADMIN, RECEPTIONIST | `CreateMemberRequest` | 201, `MemberResponse`; service assigns role `MEMBER` | Not verified in this review |

Representative user request bodies:

```json
{"name":"Example Member","email":"member@example.com","dateOfBirth":"2000-05-20","gender":"F","phone":"0900000000","password":"<password>","role":"MEMBER"}
```

`UpdateUserRequest` contains `name`, `email`, `dateOfBirth`, `gender`, `phone`, `role`, and `status`. `ChangePasswordRequest` contains `currentPassword` and `newPassword`. All `/api/users` mutations remain ADMIN-only; the user and current-user response DTOs do not expose `passwordHash` or password credentials. User read filters are also enforced in `UserService`, not only by URL authorization.

Member registration body (no role field):

```json
{"name":"Example Member","email":"member@example.com","password":"<password>","dateOfBirth":"2000-05-20","phone":"0900000000","gender":"F"}
```

Member registration is not public; `/api/members` requires ADMIN or RECEPTIONIST.

`UserProfileResponse` sample:

```json
{"id":12,"name":"Example Member","email":"member@example.com","dateOfBirth":"2000-05-20","gender":"F","phone":"0900000000","role":"MEMBER","status":"ACTIVE"}
```

`UserResponse` adds `createdAt` and has the same user fields; `MemberResponse` contains the member fields through `status`. Neither includes a password hash.

## MemberProfile

| Method and path | Allowed caller | Request body | Success and response | Test status |
|---|---|---|---|---|
| `POST /api/member-profiles/me` | MEMBER | Height/weight/goal; no member ID | 201, `MemberProfileResponse` | User verified 201, duplicate 409, wrong role 403, no token 401 before latest validation changes |
| `GET /api/member-profiles/me` | MEMBER | None | 200, `MemberProfileResponse`; 404 when absent | User verified 200, 404, 403, 401 before latest DTO changes |
| `PUT /api/member-profiles/me` | MEMBER | Complete body with `height`, `weight`, and non-null `goal` present | 200, `MemberProfileResponse`; 404 when absent | Previous version verified; current stricter body rules need retesting |
| `POST /api/member-profiles` | ADMIN | `CreateMemberProfileRequest` including `memberId` | 201, `MemberProfileResponse` | Not verified after shared validation changes |
| `GET /api/member-profiles/{id}` | ADMIN | None | 200, `MemberProfileResponse` | Not verified |
| `GET /api/member-profiles/member/{memberId}` | ADMIN | None | 200, `MemberProfileResponse` | Not verified |
| `PUT /api/member-profiles/{id}` | ADMIN | `UpdateMemberProfileRequest` | 200, `MemberProfileResponse` | Not verified after shared validation changes |

Profile values use height 50-250 cm, weight 10-500 kg, at most two decimal places, and goal length up to 1000 characters. For PUT `/me`, omitted `goal` and `goal: null` return 400; `goal: ""` explicitly clears it. Height and weight are required. No profile ID is accepted by self-service routes.

```json
{"height":170.5,"weight":65.2,"goal":"Improve endurance"}
```

`MemberProfileResponse` contains `id`, `memberId`, `memberName`, `height`, `weight`, `goal`, and `updatedAt`.

## Membership and MembershipPlan

Membership ownership is checked in `MembershipService` using the authenticated JWT subject. ADMIN and RECEPTIONIST can act for any MEMBER. MEMBER can create, view, and change auto-renew only for their own Membership. COACH has no Membership access. Membership creation still sets status to `ACTIVE` immediately; no Payment entity or processing service was found, so the endpoint does not verify that payment occurred.

Membership validity uses an exclusive `endDate`: it is date-valid only when `startDate <= today < endDate`. The scheduled expiration job runs daily at midnight in `Asia/Ho_Chi_Minh`; the ADMIN expiration endpoint invokes the same service behavior. Every `ACTIVE` Membership with `today >= endDate` becomes `EXPIRED`, regardless of `autoRenew`. `autoRenew` is currently an intent flag only; it does not extend membership or trigger renewal/payment. Existing CourseRegistration discount and price snapshots are not recalculated when a Membership expires.

| Method and path | Allowed caller | Request body | Success and response | Test status |
|---|---|---|---|---|
| `GET /api/memberships` | ADMIN, RECEPTIONIST | None | 200, `MembershipResponse[]` | Not verified after authorization changes |
| `GET /api/memberships/{id}` | ADMIN, RECEPTIONIST; MEMBER only for own membership | None | 200, `MembershipResponse` | Not verified after authorization changes |
| `GET /api/memberships/member/{memberId}` | ADMIN, RECEPTIONIST; MEMBER only when memberId is own ID | None | 200, `MembershipResponse[]` | Not verified after authorization changes |
| `POST /api/memberships` | ADMIN, RECEPTIONIST; MEMBER for self only | `CreateMembershipRequest` | 201, `MembershipResponse` | Not verified after authorization changes |
| `PATCH /api/memberships/{id}/cancel-renewal` | ADMIN, RECEPTIONIST; MEMBER only for own membership | None | 200, `MembershipResponse` | Not verified after authorization changes |
| `PATCH /api/memberships/{id}/enable-auto-renew` | ADMIN, RECEPTIONIST; MEMBER only for own membership | None | 200, `MembershipResponse` | Not verified after authorization changes |
| `POST /api/memberships/expire` | ADMIN | None | 200, text message | Not verified after authorization changes |
| `GET /api/membership-plans` | All authenticated roles | None | 200, `MembershipPlanResponse[]` | Not verified after authorization changes |
| `GET /api/membership-plans/{id}` | All authenticated roles | None | 200, `MembershipPlanResponse` | Not verified after authorization changes |
| `POST /api/membership-plans` | ADMIN | `CreateMembershipPlanRequest` | 201, `MembershipPlanResponse` | Not verified |
| `PUT /api/membership-plans/{id}` | ADMIN | `UpdateMembershipPlanRequest` | 200, `MembershipPlanResponse` | Not verified |
| `DELETE /api/membership-plans/{id}` | ADMIN | None | 204, empty body | Not verified |

Membership creation body:

```json
{"memberId":12,"planId":2,"startDate":"2026-10-10","autoRenew":false}
```

`MembershipResponse` sample: `{"id":8,"memberId":12,"memberName":"Example Member","planId":2,"planName":"Standard","startDate":"2026-10-10","endDate":"2026-11-10","autoRenew":false,"status":"ACTIVE"}`.

MembershipPlan bodies contain `name`, `description`, `price`, `durationMonths`, `courseDiscountPercent`, `personalTrainingDiscountPercent`, `allowPersonalTrainingPlan`, and `allowOneOnOneCoaching`; update also contains `status`.

`MembershipPlanResponse` adds `id` and `status` to those plan fields.

Sample: `{"id":2,"name":"Standard","description":"Monthly membership","price":50,"durationMonths":1,"courseDiscountPercent":0,"personalTrainingDiscountPercent":0,"allowPersonalTrainingPlan":false,"allowOneOnOneCoaching":false,"status":"ACTIVE"}`.

## Sport

Sport GET endpoints permit all authenticated roles; mutations are ADMIN-only.

| Method and path | Request body | Success and response | Test status |
|---|---|---|---|
| `GET /api/sports` | None | 200, `SportResponse[]` | Not verified |
| `GET /api/public/sports` | None; no JWT required | 200, active `PublicSportOptionResponse[]` with only `id` and `name` | H2 integration tested |
| `GET /api/public/sports/{sportId}/courses` | None; no JWT required | 200, active `CourseResponse[]` for an active sport | Added; H2 integration tested |
| `GET /api/public/courses/{id}` | None; no JWT required | 200, active `CourseResponse`; inactive course/sport is hidden as 404 | Added; H2 integration tested |
| `GET /api/sports/{id}` | None | 200, `SportResponse` | Not verified |
| `POST /api/sports` | `CreateSportRequest` | 201, `SportResponse` | Not verified |
| `PUT /api/sports/{id}` | `UpdateSportRequest` | 200, `SportResponse` | Not verified |

Create body: `{"name":"Swimming","description":"Pool training"}`. Update body also includes `status`.

Response sample: `{"id":3,"name":"Swimming","description":"Pool training","status":"ACTIVE"}`.

The public sport-options response is intentionally limited to `[{"id":3,"name":"Swimming"}]`; inactive Sports are omitted. The existing `/api/sports` routes remain authenticated and administrative fields are not returned by the public route.

## Coach and CoachSport

Coach and CoachSport GET endpoints permit all authenticated roles; mutations are ADMIN-only.

| Method and path | Request body | Success and response | Test status |
|---|---|---|---|
| `GET /api/coaches` | None | 200, `CoachResponse[]` | Not verified |
| `GET /api/coaches/{id}` | None | 200, `CoachResponse` | Not verified |
| `POST /api/coaches` | `CreateCoachRequest` | 201, `CoachResponse` | Not verified |
| `PATCH /api/coaches/{id}/status` | `UpdateCoachStatusRequest` (`status`) | 200, `CoachResponse` | Not verified |
| `POST /api/coaches/{coachId}/sports` | `AssignCoachSportRequest` | 201, `CoachSportResponse` | Not verified |
| `GET /api/coaches/{coachId}/sports` | None | 200, `CoachSportResponse[]` | Not verified |
| `PUT /api/coaches/{coachId}/sports/{sportId}` | `UpdateCoachSportRequest` | 200, `CoachSportResponse` | Not verified |

Create coach body has `name`, `email`, `phone`, `gender`, `password`. Assign sport body has `sportId`, `experienceYears`, `certification`; update has `experienceYears` and `certification`.

Response samples: `CoachResponse` is `{"id":4,"userId":12,"userName":"Example Coach","email":"coach@example.com","status":"ACTIVE"}`. `CoachSportResponse` is `{"id":2,"coachId":4,"coachName":"Example Coach","sportId":3,"sportName":"Swimming","experienceYears":2,"certification":"<certification>"}`.

## Course, CourseOffering, and Class

GET endpoints permit all authenticated roles; mutations are ADMIN-only.

| Method and path | Request body | Success and response | Test status |
|---|---|---|---|
| `GET /api/courses` | None | 200, `CourseResponse[]` | Not verified |
| `GET /api/courses/{id}` | None | 200, `CourseResponse` | Not verified |
| `GET /api/courses/sport/{sportId}` | None | 200, `CourseResponse[]` | Not verified |
| `POST /api/courses` | `CreateCourseRequest` | 201, `CourseResponse` | Not verified |
| `PUT /api/courses/{id}` | `UpdateCourseRequest` | 200, `CourseResponse` | Not verified |
| `GET /api/course-offerings` | None | 200, `CourseOfferingResponse[]` | Not verified |
| `GET /api/course-offerings/{id}` | None | 200, `CourseOfferingResponse` | Not verified |
| `GET /api/course-offerings/course/{courseId}` | All authenticated roles | 200, `CourseOfferingResponse[]` | Not verified |
| `POST /api/course-offerings` | `CreateCourseOfferingRequest` | 201, `CourseOfferingResponse` | Not verified |
| `PUT /api/course-offerings/{id}` | `UpdateCourseOfferingRequest` | 200, `CourseOfferingResponse` | Not verified |
| `GET /api/classes` | All authenticated roles | 200, `CourseClassResponse[]` | Not verified |
| `GET /api/classes/{id}` | All authenticated roles | 200, `CourseClassResponse` | Not verified |
| `GET /api/classes/offering/{offeringId}` | All authenticated roles | 200, `CourseClassResponse[]` | Not verified |
| `POST /api/classes` | `CreateClassRequest` | 201, `CourseClassResponse` | Not verified |
| `PUT /api/classes/{id}` | `UpdateClassRequest` | 200, `CourseClassResponse` | Not verified |

Course create body: `sportId`, `name`, `description`, `targetAgeMin`, `targetAgeMax`, `level`, `learningOutcome`; update also has `status` and does not accept `sportId`.

Course `level` is required on create and update and accepts `BEGINNER`, `INTERMEDIATE`, or `ADVANCED` (case-insensitive; surrounding whitespace is trimmed), matching CourseRegistration eligibility. Course and Sport update statuses accept `ACTIVE` or `INACTIVE`; CourseOffering accepts `OPEN` or `CLOSED`; CourseClass accepts `ACTIVE` or `INACTIVE`. Values are normalized to uppercase; unsupported values return 400. Existing records are not rewritten by this validation.

CourseOffering create body: `courseId`, `name`, `price`, `plannedSessions`, `sessionDuration`, `startDate`, `endDate`, `registrationDeadline`, `cancellationDeadline`; update has the same fields plus `status` and no `courseId`.

Class create body: `courseOfferingId`, `coachId`, `name`, `capacity`; update body: `coachId`, `name`, `capacity`, `status`.

Response samples: `CourseResponse` contains `id`, `sportId`, `sportName`, `name`, `description`, `targetAgeMin`, `targetAgeMax`, `level`, `learningOutcome`, `status`. `CourseOfferingResponse` contains `id`, course/sport IDs and names, `name`, `price`, `plannedSessions`, `sessionDuration`, `startDate`, `endDate`, registration/cancellation deadlines, and `status`. `CourseClassResponse` contains `id`, offering/course/sport/coach IDs and names, `name`, `capacity`, and `status`.

Representative course response: `{"id":10,"sportId":3,"sportName":"Swimming","name":"Beginner Swim","description":"Basics","targetAgeMin":10,"targetAgeMax":18,"level":"Beginner","learningOutcome":"Swim safely","status":"ACTIVE"}`.

Offering sample: `{"id":11,"courseId":10,"courseName":"Beginner Swim","sportId":3,"sportName":"Swimming","name":"October Swim","price":100,"plannedSessions":8,"sessionDuration":60,"startDate":"2026-10-20","endDate":"2026-11-20","registrationDeadline":"2026-10-18T23:59:00","cancellationDeadline":"2026-10-19T23:59:00","status":"OPEN"}`. Class sample: `{"id":6,"courseOfferingId":11,"courseOfferingName":"October Swim","courseId":10,"courseName":"Beginner Swim","sportId":3,"sportName":"Swimming","coachId":4,"coachName":"Example Coach","name":"Morning group","capacity":12,"status":"ACTIVE"}`.

## Facility

Facility GET endpoints permit all authenticated roles; mutations are ADMIN-only.

| Method and path | Request body | Success and response | Test status |
|---|---|---|---|
| `GET /api/facilities` | None | 200, `FacilityResponse[]` | Not verified |
| `GET /api/facilities/{id}` | None | 200, `FacilityResponse` | Not verified |
| `GET /api/facilities/sport/{sportId}` | None | 200, `FacilityResponse[]` | Not verified |
| `POST /api/facilities` | `CreateFacilityRequest` (`sportId`, `name`, `type`) | 201, `FacilityResponse` | Not verified |
| `PUT /api/facilities/{id}` | `UpdateFacilityRequest` (`name`, `type`, `status`) | 200, `FacilityResponse` | Not verified |

Response sample: `{"id":5,"sportId":1,"sportName":"Swimming","name":"Main pool","type":"POOL","status":"ACTIVE"}`.

## ClassSession

`ClassSession` represents one scheduled meeting belonging to a `CourseClass` (`/api/classes`). All GET routes allow ADMIN, RECEPTIONIST, MEMBER, and COACH. POST, PUT, and PATCH are ADMIN-only; no DELETE endpoint is exposed. Status values are `SCHEDULED`, `COMPLETED`, and `CANCELLED` only.

| Method and path | Allowed caller | Request body | Success and response | Test status |
|---|---|---|---|---|
| `GET /api/class-sessions` | All authenticated roles | None | 200, `ClassSessionResponse[]` | Not verified |
| `GET /api/class-sessions/{id}` | All authenticated roles | None | 200, `ClassSessionResponse` | Not verified |
| `GET /api/class-sessions/class/{classId}` | All authenticated roles | None | 200, `ClassSessionResponse[]` | Not verified |
| `POST /api/class-sessions` | ADMIN | `CreateClassSessionRequest` | 201, `ClassSessionResponse` | Not verified |
| `PUT /api/class-sessions/{id}` | ADMIN | `UpdateClassSessionRequest` | 200, `ClassSessionResponse` | Not verified |
| `PATCH /api/class-sessions/{id}/status` | ADMIN | `UpdateClassSessionStatusRequest` | 200, `ClassSessionResponse` | Not verified |

Create sample (status omitted to use the `SCHEDULED` default; `facilityId` may be null):

```json
{"classId":3,"facilityId":6,"startTime":"2026-10-12T10:00:00","endTime":"2026-10-12T11:00:00"}
```

Update body is the same shape and must include `classId`, `startTime`, and `endTime`; `facilityId: null` clears the facility. Status update body: `{"status":"CANCELLED"}`. The response includes `id`, class/facility identifiers and names, start/end times, status, and non-blocking `warnings` (outside CourseOffering date range or the per-CourseClass planned-session threshold). Overlap or invalid state transitions return 409; missing referenced resources return 404; bean validation failures return 400.

Response sample:

```json
{"id":21,"classId":3,"className":"Morning group","facilityId":6,"facilityName":"Main court","startTime":"2026-10-12T10:00:00","endTime":"2026-10-12T11:00:00","status":"SCHEDULED","warnings":[]}
```

Facility and Coach overlaps use half-open interval semantics (`existing.startTime < requested.endTime` and `existing.endTime > requested.startTime`), so sessions that only touch at an endpoint do not overlap. Cancelled sessions do not reserve facilities or coaches. Make-up sessions outside CourseOffering dates and counts above `plannedSessions` are saved with warnings; the current code interprets `plannedSessions` per CourseClass for warning purposes only, pending confirmation of the intended counting scope.

Lifecycle transitions: `SCHEDULED` can become `COMPLETED` only when current time is at or after `endTime`, and can become `CANCELLED` only before `startTime`. `COMPLETED` and `CANCELLED` are terminal; repeating the same status is idempotent, but changing to another status returns 409. PUT schedule updates are allowed only for `SCHEDULED` sessions. Invalid status/time request data returns 400. Statuses change only through the API; there is no time-driven auto-transition.

Facility status updates accept `ACTIVE` or `INACTIVE`, case-insensitively, and persist uppercase. Null, blank, or any other value returns 400. Switching from ACTIVE to INACTIVE returns 409 if a `SCHEDULED` ClassSession using the Facility has `endTime` later than the current time, covering sessions in progress and upcoming sessions. A session ending exactly now, an ended session, or a `COMPLETED`/`CANCELLED` session does not block deactivation. No session is deleted, cancelled, or edited by this operation. When a Facility is supplied on ClassSession POST or changed on PUT, its current status must be ACTIVE; missing Facility IDs return 404 and any other status returns 409. On PUT, a future `SCHEDULED` session cannot retain the same Facility if that Facility has become unavailable; choose another ACTIVE Facility or cancel the session. GET continues returning historical sessions and their Facility data regardless of Facility status. `FacilityService` creates facilities as ACTIVE.

## Module coverage note

The Phase 2 CourseClass registration API is documented below. `/api/classes` manages CourseClass records; `/api/class-sessions` manages individual meetings.

## OpenAPI / Swagger

No Swagger/OpenAPI dependency or configuration was found in `pom.xml` or source. For this Spring Boot 4.1.1 Web MVC application, the springdoc project documents Spring Boot 4 support in springdoc 3.x; the candidate starter is `org.springdoc:springdoc-openapi-starter-webmvc-ui`. Add it only after choosing a compatible 3.x release, then protect or explicitly permit `/v3/api-docs/**` and `/swagger-ui/**` in Spring Security. See [springdoc compatibility documentation](https://springdoc.org/) and [springdoc project README](https://github.com/springdoc/springdoc-openapi).

## Pre-merge review items

- Membership creation currently persists `ACTIVE` without a Payment record or processing step. This existing behavior was preserved; confirm the purchase/payment contract before production use.
- The application requires `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`; configure them in the runtime environment. If the previously tracked SQL Server password was real or shared, rotate it because Git history may retain it.
- Configure `CORS_ALLOWED_ORIGINS` for deployed frontend origins. Defaults permit only `http://localhost:5173` and `http://localhost:3000`.
- The CourseClass registration revision passed the full isolated-H2 Maven suite and package build; Postman verification remains pending. SQL Server locking and migration behavior still need validation in a disposable database before deployment.
- CourseClass registration is implemented under `/api/course-registrations`; no Payment/Bill or public account-registration endpoint is exposed.


## Phase 1: Skill assessments and current sport levels (2026-10-12)

All endpoints require a Bearer JWT. The JWT subject identifies the current User; Coach is resolved by `Coach.user.id`. IDs are positive integers. Responses are DTOs containing IDs and timestamps, never User/password entities.

| Method | Path | Permission | Success |
|---|---|---|---|
| POST | `/api/skill-assessments` | ADMIN, RECEPTIONIST | 201 PENDING assessment |
| GET | `/api/skill-assessments` | ADMIN, RECEPTIONIST | 200 all history |
| GET | `/api/skill-assessments/my-assignments` | COACH | 200 own assignments |
| GET | `/api/skill-assessments/me` | MEMBER | 200 own history |
| GET | `/api/skill-assessments/{id}` | Staff, owning MEMBER, assigned COACH | 200 assessment |
| PATCH | `/api/skill-assessments/{id}/complete` | Assigned COACH or temporarily RECEPTIONIST | 200 COMPLETED assessment |
| PATCH | `/api/skill-assessments/{id}/cancel` | ADMIN, RECEPTIONIST | 200 CANCELLED assessment |
| GET | `/api/member-sport-levels/me` | MEMBER | 200 own current levels |
| GET | `/api/member-sport-levels/member/{memberId}` | ADMIN, RECEPTIONIST | 200 specified member's levels |

POST body (`consultationRequestId` is optional and may be omitted for standalone assessments):

```json
{"memberId": 1, "sportId": 1, "assignedCoachId": 1, "notes": "Assess current technique", "consultationRequestId": 15}
```

Completion body (no coach ID is accepted as an identity source):

```json
{"assessedLevel": "intermediate", "notes": "Verified technique and control"}
```

Completion normalizes trimmed level names to BEGINNER, INTERMEDIATE, or ADVANCED. Notes are optional and limited to 1000 characters. Omitted/null completion notes retain the request notes; an empty string clears them. Cancellation has no body. Unknown JSON properties follow the existing application's deserialization policy and never select the completing coach.

Assessment DTO fields: `id`, `memberId`, `sportId`, `assignedCoachId`, `requestedByUserId`, `status`, `assessedLevel`, `notes`, `createdAt`, `assessedAt`, `cancelledAt`, nullable `consultationRequestId`. Level DTO fields: `id`, `memberId`, `sportId`, `level`, `verifiedByCoachId`, `verifiedAt`, `updatedAt`. Timestamps use existing `LocalDateTime` conventions (ISO local date/time, no offset). Assessment lists sort newest creation first, with ID as tie-breaker; level lists sort by sport ID. Empty lists return `[]`; an absent level does not imply BEGINNER and no record is synthesized.

Creation requires an ACTIVE MEMBER, ACTIVE Sport, ACTIVE Coach linked to an ACTIVE COACH account, and an existing CoachSport qualification. Only one PENDING assessment is allowed per member/sport. Completion or cancellation can transition only PENDING; terminal records have no edit endpoint. Completion atomically creates or replaces the current level, including a downgrade. Staff can consult inactive members' recorded levels, but creation requires an active member. Current callers must have an active account; coaches must also have an active linked Coach. Temporary policy: an authenticated RECEPTIONIST may enter a result for an assessment assigned to an ACTIVE qualified Coach; `MemberSportLevel.verifiedByCoachId` continues to record that assigned Coach because the current schema has no separate assessor field. This is a delegated temporary workflow and should be revised when Coach-led assessment is restored.

Errors: 400 invalid IDs/body/level/notes, 401 absent/invalid JWT identity, 403 wrong role/ownership or inactive caller, 404 missing resource, 409 invalid target state/qualification, duplicate pending, or terminal transition. Status errors follow the existing Spring `ResponseStatusException` convention; clients should use HTTP status rather than assume a particular error-body shape.

### Phase 1 SQL Server migration and concurrency

Run [phase1-member-sport-level-assessment.sql](sql/phase1-member-sport-level-assessment.sql) against the selected database before enabling the feature. It adds only `member_sport_level` and `skill_assessment`, their foreign keys, the member/sport unique constraint, and a SQL Server filtered unique index on pending assessments. It does not alter existing tables or insert default levels. Hibernate `ddl-auto=update` creates entity tables but cannot express the filtered index; the script also handles tables already created by Hibernate. Review default `dbo` and snake_case naming against the deployment. Back up and stop writers; existing duplicate data fails the migration rather than being deleted. No SQL migration was executed during implementation.

All three mutations use a transaction and `PESSIMISTIC_WRITE` on the existing member User as a stable lock anchor. Completion/cancellation first select only the assessment's member ID (not an entity snapshot), then lock member, then assessment; state is read after the member lock. Hibernate's SQL Server dialect implements the lock with SQL Server locking hints. All mutations consistently acquire member before assessment, hold locks until commit/rollback, and serialize different sports for the same member. The lock also covers a missing level row and the duplicate-pending check, so concurrent first inserts and repeated completion cannot overwrite a winner. The filtered unique index provides database enforcement even for external pending inserts. External level writers must follow this lock protocol; there is no general level mutation API.

The latest successful serialized completion sets the current level, without comparing creation dates or preventing downgrades. `assessedAt` is captured after acquiring the lock; transaction order determines the winner even if wall-clock timestamps tie. Persistence errors roll back both writes. Database deadlocks/timeouts can still abort a transaction and require caller retry; the application does not silently retry or translate every database infrastructure failure to 409. H2 integration tests exercise locking/rollback, but SQL Server migration, dialect lock behavior, and concurrent calls still require verification on the deployed SQL Server.

### Postman Phase 1 verification checklist

Use separate tokens for ADMIN, RECEPTIONIST, two MEMBER accounts, and two COACH accounts. Create an ACTIVE Sport and qualified Coach with existing APIs; configure the collection's `memberId`, `sportId`, `coachId`, and `assessmentId` variables. The Phase 1 folder contains all nine endpoints; choose the token for each named permission.

1. Verify staff POST 201, duplicate POST 409; missing IDs 404; inactive/wrong-role member, inactive sport/coach, and missing qualification 409.
2. Verify member history/levels and coach assignments contain only own records; detail access to another member or coach returns 403; no token returns 401.
3. Complete with the assigned coach and lowercase level: 200, persisted level/timestamps. Unassigned coach returns 403; invalid level and notes >1000 characters return 400.
4. Reassess to ADVANCED then BEGINNER; verify one current level and retained assessment history. Missing level returns an empty list before completion.
5. Cancel PENDING as staff; verify no level mutation. Re-complete/re-cancel either terminal status returns 409. Verify wrong-role POST/PATCH and list access return 403.
6. Send concurrent duplicate creations, two completions, and cancellation versus completion; exactly one succeeds, and stored assessment/level agree. Verify the filtered index in SQL Server.

These are verification instructions, not recorded Postman results.

## Phase 1.5: Consultation requests

| Method | Path | Permission | Success |
|---|---|---|---|
| POST | `/api/consultation-requests` | Guest or MEMBER | 201 PENDING request; MEMBER is linked from JWT subject |
| GET | `/api/consultation-requests/me` | MEMBER | 200 own linked requests |
| GET | `/api/consultation-requests?status=PENDING&page=0&size=20` | ADMIN, RECEPTIONIST | 200 paged queue; status optional |
| GET | `/api/consultation-requests/{id}` | ADMIN, assigned RECEPTIONIST, linked MEMBER | 200 request |
| PATCH | `/api/consultation-requests/{id}/take` | ADMIN, RECEPTIONIST | 200; PENDING -> IN_PROGRESS, records handler/time |
| PATCH | `/api/consultation-requests/{id}/complete` | ADMIN, assigned handler | 200; IN_PROGRESS -> COMPLETED |
| PATCH | `/api/consultation-requests/{id}/cancel` | ADMIN, assigned handler | 200; IN_PROGRESS -> CANCELLED |
| POST | `/api/consultation-requests/{id}/create-member` | ADMIN, assigned handler | 200; creates MEMBER and links atomically |
| PATCH | `/api/consultation-requests/{id}/link-member` | ADMIN, assigned handler | 200; links selected ACTIVE MEMBER |

Guest/MEMBER submission body:

```json
{"name":"Nguyễn An","phone":"0900000000","email":"an@example.test","sportId":1,"age":25,"experience":"Đã tập cơ bản","note":"Muốn được tư vấn lộ trình"}
```

`name` and `phone` are required (100/20 characters); `email` is optional (150 characters); `age` is optional (1-120); `sportId` must identify an ACTIVE Sport. `experience` and `note` are optional Vietnamese text. The request has no `memberId` property; a MEMBER is resolved from the validated JWT, while a guest stays unlinked. Authenticated non-MEMBER roles are rejected on this public route.

The queue accepts `page` >= 0 and `size` from 1 to 100. Status values are `PENDING`, `IN_PROGRESS`, `COMPLETED`, and `CANCELLED`. Taking uses a database pessimistic row lock so simultaneous staff requests cannot both claim a PENDING request. Only the assigned handler or ADMIN may complete/cancel/link/create a member; terminal consultation states cannot transition again. Assessment completion does not complete its consultation, and consultation cancellation does not mutate assessment or MemberSportLevel history.

Complete body (resolution note is optional): `{"resolutionNote":"Consultation completed; course registration remains separate"}`. Cancel body requires a reason: `{"reason":"Member cancelled the consultation"}`. Example response:

```json
{"id":12,"memberId":null,"name":"Nguyễn An","phone":"0900000000","email":"an@example.test","sportId":1,"sportName":"Tennis","age":25,"experience":"Beginner","note":"Consultation request","status":"PENDING","createdAt":"2026-10-12T14:30:00","handledBy":null,"handledAt":null,"resolutionNote":null,"completedAt":null,"cancelledAt":null}
```

For automatic account creation, the consultation must be IN_PROGRESS, unlinked, and have an email. Request body:

```json
{"password":"<staff-selected temporary password>","dateOfBirth":"1998-05-01"}
```

This reuses the existing member-registration BCrypt encoder and required date-of-birth rule; there is no default password or activation/reset workflow. The password is request-only and never returned. Use HTTPS and do not log the request body. If the consultation lacks email, collect it through an approved channel or explicitly select an existing account instead:

```json
{"memberId":20}
```

The selected account must exist, be ACTIVE, and have MEMBER role. No automatic email/phone matching is performed. Create-member email uniqueness conflicts return 409; missing/invalid required input returns 400.

Assessment creation can include `consultationRequestId`; when present, the request must be IN_PROGRESS and linked to the same member and sport. Omitting it preserves standalone assessment behavior. Assessment response includes nullable `consultationRequestId`.

### Phase 1.5 migration and verification status

Run [phase1_5-consultation-requests.sql](sql/phase1_5-consultation-requests.sql) manually against the selected SportCenterDB after backup and with writers stopped. It creates `consultation_request`, adds nullable `skill_assessment.consultation_request_id`, foreign keys, enum/age/nonblank checks, and lookup indexes. It is idempotent and does not drop data. The default `spring.jpa.hibernate.ddl-auto=validate` prevents application startup from applying schema changes automatically; startup validates successfully only after the manual migration is applied. The isolated Phase 1 test profile continues to use H2 `create-drop`.

Phase 1.5 source and integration tests passed the offline Maven test lifecycle (67 total tests, zero failures/errors/skips). The SQL Server migration has not been executed, and Phase 1.5 behavior has not been verified through Postman. Postman examples are in the Consultation requests folder; replace sample identifiers and use the appropriate role tokens. The Maven/H2 result does not establish SQL Server migration or locking behavior. HTTP error responses are configured not to include internal messages, exception class names, or stack traces.


### Phase 1 migration hardening (2026-10-12)

The migration now sets all seven required filtered-index options: ANSI_NULLS, ANSI_PADDING, ANSI_WARNINGS, ARITHABORT, CONCAT_NULL_YIELDS_NULL, and QUOTED_IDENTIFIER ON; NUMERIC_ROUNDABORT OFF. XACT_ABORT remains ON. These options must also hold for application connections writing the filtered index; running the migration does not change JDBC session configuration. Reference: https://learn.microsoft.com/en-us/sql/t-sql/statements/create-index-transact-sql#required-set-options-for-filtered-indexes.

`SkillAssessment.notes` now uses Hibernate `@Nationalized` with length 1000, mapping to SQL Server `NVARCHAR(1000) NULL`. The migration explicitly upgrades an existing nullable built-in `VARCHAR(1000)` column to that representation; it does not delete notes. Conversion cannot restore characters already lost by previous non-Unicode writes. Other existing notes shapes are rejected for manual review.

Verified mapping contract (default Spring/Hibernate physical naming, schema `dbo`):

| Table | Columns and SQL Server types | Foreign keys |
|---|---|---|
| `member_sport_level` | `id INT IDENTITY(1,1)` PK; required `member_id INT`, `sport_id INT`, `level VARCHAR(20)`, `updated_at DATETIME2(6)`; nullable `verified_by_coach_id INT`, `verified_at DATETIME2(6)` | member_id -> users.id; sport_id -> sport.id; verified_by_coach_id -> coach.id |
| `skill_assessment` | `id INT IDENTITY(1,1)` PK; required `member_id INT`, `sport_id INT`, `assigned_coach_id INT`, `requested_by_user_id INT`, `status VARCHAR(20)`, `created_at DATETIME2(6)`; nullable `assessed_level VARCHAR(20)`, `notes NVARCHAR(1000)`, `assessed_at DATETIME2(6)`, `cancelled_at DATETIME2(6)` | member_id/requested_by_user_id -> users.id; sport_id -> sport.id; assigned_coach_id -> coach.id |

This script targets SQL Server 2016+ catalog metadata. Run it as a standalone batch in `SportCenterDB` after backup and stopping writers. It rejects an outer transaction to avoid rolling back a caller's unrelated work. A transaction-owned application lock serializes migration reruns, and exclusive table locks protect Phase 1 data during validation. Dynamic DDL avoids binding statements to missing or incompatible old columns before preflight. The database-name guard must be deliberately reviewed if deploying to a renamed database; custom physical naming/schema needs a separately reviewed script.

Existing tables must have exactly the mapped columns, built-in types, lengths, datetime precision, nullability, identity definition, and enabled single-id primary keys. Temporal/memory-optimized tables and extra columns fail closed. Existing FK names may differ from the script/Hibernate, but their single-column targets must match and they must be trusted, enabled, and non-cascading. Missing FKs and enum checks are added WITH CHECK, so orphaned references and invalid enum data fail the whole transaction. Existing CHECK definitions are accepted only in the documented canonical enum IN/OR forms; unusual but logically equivalent predicates may require manual review.

Required indexes must be unique, enabled, non-hypothetical, have IGNORE_DUP_KEY OFF, and have exactly member_id then sport_id as key columns. The level index must be unfiltered; the assessment index must filter exactly status = 'PENDING'. Included columns do not change uniqueness and are allowed. Different extra unique indexes are rejected to prevent accidentally forbidding terminal assessment history. Incorrect/disabled/untrusted objects are reported rather than dropped, rebuilt, renamed, or silently accepted. Duplicate member/sport levels or PENDING assessments fail index creation and roll back all changes. Compatible reruns retain existing tables/indexes and skip the completed Unicode upgrade.

No live SQL Server inspection or migration execution was performed. Database permissions/metadata visibility, deployed schema/naming, actual SQL Server CHECK/filter canonicalization, migration/rerun behavior, JDBC SET options, and locking remain SQL Server verification items. Strict fingerprint validation can reject safe alternative definitions; it intentionally requires review rather than weakening constraints automatically.

## Phase 2: CourseClass registration (2026-10-13)

The registration unit is one `CourseClass` selected from the existing hierarchy Course → CourseOffering → CourseClass. Existing browse APIs are `GET /api/course-offerings/course/{courseId}` and `GET /api/classes/offering/{offeringId}`; they expose offerings/classes for selection, so no duplicate discovery endpoints were added.

| Method | Endpoint | Allowed role | Behavior |
|---|---|---|---|
| POST | `/api/course-registrations` | MEMBER | Registers the JWT-authenticated member for the selected CourseClass; returns 201 and CONFIRMED registration |
| GET | `/api/course-registrations/me` | MEMBER | Lists the caller's CONFIRMED registrations, identifying Course, CourseOffering, and CourseClass |
| GET | `/api/course-registrations/me/class-sessions` | MEMBER | Lists sessions only for CourseClasses in the caller's CONFIRMED registrations |
| GET | `/api/course-registrations` | ADMIN, RECEPTIONIST | Lists registrations with Course, CourseOffering, and CourseClass identity |

POST body: `{"classId":6}`. Client-supplied member/course/offering identifiers and prices are not part of the request contract; member identity comes from JWT, Course/Offering are derived from the selected CourseClass, and all prices are calculated by the server. Response fields also include the immutable enrollment-time snapshots `originalPrice`, `membershipDiscount` (money amount), and `finalPrice`. Example: `{"id":31,"memberId":4,"courseId":10,"courseName":"Beginner Swim","courseOfferingId":11,"courseOfferingName":"October Swim","classId":6,"className":"Morning group","sportId":3,"requiredLevel":"BEGINNER","status":"CONFIRMED","registeredAt":"2026-10-13T10:30:00","cancelledAt":null,"originalPrice":100.00,"membershipDiscount":10.00,"finalPrice":90.00}`.

Registration requires an ACTIVE MEMBER, ACTIVE CourseClass, OPEN CourseOffering (the existing offering service uses `OPEN` as its registration-accepting status), ACTIVE Course and Sport, and a date on or after `CourseOffering.startDate.minusDays(30)`. It also requires the existing deadline to remain strictly in the future; registration is rejected at the exact deadline. Membership is optional. At registration time, an ACTIVE Membership whose exclusive validity interval contains the registration date (`startDate <= registrationDate < endDate`) contributes the highest eligible `courseDiscountPercent`; discounts do not stack, and one eligible Membership may discount multiple separate CourseRegistrations during its validity period. The monetary discount is rounded to two decimals using HALF_UP, and original price, discount amount, and final price are stored as snapshots. Membership expiry, renewal, Plan discount changes, and Offering price changes do not recalculate existing snapshots. Course age restrictions are enforced; if an age range exists, missing date of birth or an out-of-range age returns 409. A member without a coach-verified sport level may register only for BEGINNER; a verified level must meet or exceed the course requirement. Unsupported course-level values are rejected. Capacity is checked per CourseClass. A member may have only one CONFIRMED registration per CourseOffering, even if the offering contains multiple classes. Duplicate class/another class in the same offering, full capacity, insufficient level, age ineligibility, schedule conflicts, inactive resources, and closed/expired offerings return 409; missing CourseClass returns 404; malformed request returns 400; missing JWT/wrong role returns 401/403.

Registration is transactional. It locks the member row first to serialize that member's offering and schedule checks, then locks the selected CourseClass row before counting seats. The SQL Server migration adds a filtered unique index on CONFIRMED `(member_id, class_id)` as a database guard for duplicate class registration. Schedule checks compare only future/ongoing SCHEDULED sessions in the selected class and the member's already registered classes; intervals that touch only at an endpoint do not conflict. Viewing sessions is scoped to registered classes, not every class in the same Course or Offering.

Cancellation remains omitted because no CourseRegistration cancellation policy has been confirmed. Registration is independent of ConsultationRequest and SkillAssessment; no Payment or Bill operation is performed. The migration at `docs/sql/phase2-course-registrations.sql` is an idempotent manual SQL Server script including the three DECIMAL(12,2) snapshots and has not been executed. Automated tests/build and Postman status are recorded in `docs/IMPLEMENTATION_STATUS.md`.
