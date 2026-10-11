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
| `GET /api/sports/{id}` | None | 200, `SportResponse` | Not verified |
| `POST /api/sports` | `CreateSportRequest` | 201, `SportResponse` | Not verified |
| `PUT /api/sports/{id}` | `UpdateSportRequest` | 200, `SportResponse` | Not verified |

Create body: `{"name":"Swimming","description":"Pool training"}`. Update body also includes `status`.

Response sample: `{"id":3,"name":"Swimming","description":"Pool training","status":"ACTIVE"}`.

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
| `GET /api/course-offerings/course/{courseId}` | None | 200, `CourseOfferingResponse[]` | Not verified |
| `POST /api/course-offerings` | `CreateCourseOfferingRequest` | 201, `CourseOfferingResponse` | Not verified |
| `PUT /api/course-offerings/{id}` | `UpdateCourseOfferingRequest` | 200, `CourseOfferingResponse` | Not verified |
| `GET /api/classes` | None | 200, `CourseClassResponse[]` | Not verified |
| `GET /api/classes/{id}` | None | 200, `CourseClassResponse` | Not verified |
| `GET /api/classes/offering/{offeringId}` | None | 200, `CourseClassResponse[]` | Not verified |
| `POST /api/classes` | `CreateClassRequest` | 201, `CourseClassResponse` | Not verified |
| `PUT /api/classes/{id}` | `UpdateClassRequest` | 200, `CourseClassResponse` | Not verified |

Course create body: `sportId`, `name`, `description`, `targetAgeMin`, `targetAgeMax`, `level`, `learningOutcome`; update also has `status` and does not accept `sportId`.

CourseOffering create body: `courseId`, `name`, `price`, `plannedSessions`, `sessionDuration`, `startDate`, `endDate`, `registrationDeadline`, `cancellationDeadline`; update has the same fields plus `status` and no `courseId`.

Class create body: `courseOfferingId`, `coachId`, `name`, `capacity`; update body: `coachId`, `name`, `capacity`, `status`.

Response samples: `CourseResponse` contains `id`, `sportId`, `sportName`, `name`, `description`, `targetAgeMin`, `targetAgeMax`, `level`, `learningOutcome`, `status`. `CourseOfferingResponse` contains `id`, course/sport IDs and names, `name`, `price`, `plannedSessions`, `sessionDuration`, `startDate`, `endDate`, registration/cancellation deadlines, and `status`. `CourseClassResponse` contains `id`, offering/course/sport/coach IDs and names, `name`, `capacity`, and `status`.

Representative course response: `{"id":10,"sportId":3,"sportName":"Swimming","name":"Beginner Swim","description":"Basics","targetAgeMin":10,"targetAgeMax":18,"level":"Beginner","learningOutcome":"Swim safely","status":"ACTIVE"}`.

Offering sample: `{"id":11,"courseId":10,"courseName":"Beginner Swim","sportId":3,"sportName":"Swimming","name":"October Swim","price":100,"plannedSessions":8,"sessionDuration":60,"startDate":"2026-10-20","endDate":"2026-11-20","registrationDeadline":"2026-10-18T23:59:00","cancellationDeadline":"2026-10-19T23:59:00","status":"ACTIVE"}`. Class sample: `{"id":6,"courseOfferingId":11,"courseOfferingName":"October Swim","courseId":10,"courseName":"Beginner Swim","sportId":3,"sportName":"Swimming","coachId":4,"coachName":"Example Coach","name":"Morning group","capacity":12,"status":"ACTIVE"}`.

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

## Modules not found in current controllers

No course-registration, payment, or public account-registration endpoint was found. `/api/classes` manages CourseClass records; `/api/class-sessions` manages individual meetings.

## OpenAPI / Swagger

No Swagger/OpenAPI dependency or configuration was found in `pom.xml` or source. For this Spring Boot 4.1.1 Web MVC application, the springdoc project documents Spring Boot 4 support in springdoc 3.x; the candidate starter is `org.springdoc:springdoc-openapi-starter-webmvc-ui`. Add it only after choosing a compatible 3.x release, then protect or explicitly permit `/v3/api-docs/**` and `/swagger-ui/**` in Spring Security. See [springdoc compatibility documentation](https://springdoc.org/) and [springdoc project README](https://github.com/springdoc/springdoc-openapi).

## Pre-merge review items

- Membership creation currently persists `ACTIVE` without a Payment record or processing step. This existing behavior was preserved; confirm the purchase/payment contract before production use.
- The application requires `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`; configure them in the runtime environment. If the previously tracked SQL Server password was real or shared, rotate it because Git history may retain it.
- Configure `CORS_ALLOWED_ORIGINS` for deployed frontend origins. Defaults permit only `http://localhost:5173` and `http://localhost:3000`.
- Build and all role/ownership behavior changed in this review are awaiting verification. Test with ADMIN, RECEPTIONIST, MEMBER, and COACH tokens before merge.
- No course-registration, payment, or public account-registration controller was found.
