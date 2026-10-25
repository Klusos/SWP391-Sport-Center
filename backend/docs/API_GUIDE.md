# SportCenter Backend API Guide

This guide was generated from the current Spring MVC controllers, DTOs, SecurityConfig, and MembershipService ownership checks. It describes the working-tree source; it is not a claim that every endpoint has passed integration testing.

## Base URL and authentication

- Local base URL: `http://localhost:8080` (set `baseUrl` in the Postman collection for another environment).
- Except for `POST /api/auth/login`, requests require `Authorization: Bearer {{token}}`.
- Valid role names are `ADMIN`, `RECEPTIONIST`, `MEMBER`, and `COACH`.
- `GET /api/users/me` permits any authenticated user. `POST /api/members` permits ADMIN and RECEPTIONIST. MemberProfile `/me` routes permit MEMBER; numeric-ID MemberProfile routes permit ADMIN. User management is ADMIN-only.
- Public MembershipPlan catalog exposes ACTIVE plans only. The administrative plan catalog is ADMIN-only; RECEPTIONIST can read the public active catalog but cannot see inactive plans or mutate plans. Membership access follows the ownership rules below.
- Sport, Course, CourseOffering, Class, Facility, Coach, and CoachSport GET routes permit all authenticated roles; mutation routes are ADMIN-only.
- CORS allows only explicit origins configured through `CORS_ALLOWED_ORIGINS` (default local React origins: ports 5173 and 3000). Wildcard origins are rejected.
- The response fields below follow the DTOs. Error response bodies can vary: `GlobalExceptionHandler` formats resource-not-found and `IllegalArgumentException` errors, while Spring Security, validation, and `ResponseStatusException` may use framework error bodies.

Common status codes: 400 for request validation or invalid role, 401 for missing/invalid JWT or invalid login credentials, 403 for a role/account that is not permitted, 404 for a missing resource, 409 for conflicts handled as `IllegalArgumentException` or duplicate MemberProfile. The not-found error shape is `{"status":404,"error":"Not Found","message":"..."}`; the conflict shape is similar with status 409 and error `Conflict`.

### Status and test evidence

- **Verified by user** means the user reported a successful Postman result in this conversation.
- **Not verified** means no Postman result was available during this review; the endpoint may still be implemented.
- The ordinary H2 `mvn clean verify` currently passes 139 tests. SQL Server E2E and Postman verification were not run for PT booking.

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

Membership ownership is checked in `MembershipService` using the authenticated JWT subject. ADMIN and RECEPTIONIST can act for any MEMBER. MEMBER can view and change auto-renew only for their own Membership. COACH has no Membership access. Membership activation/renewal is payment-backed: it occurs only after a Payment reaches `SUCCESS`; the legacy `POST /api/memberships` assignment route is ADMIN-only.

Membership validity uses an exclusive `endDate`: it is date-valid only when `startDate <= today < endDate`. The scheduled expiration job runs daily at midnight in `Asia/Ho_Chi_Minh`; the ADMIN expiration endpoint invokes the same service behavior. Every `ACTIVE` Membership with `today >= endDate` becomes `EXPIRED`, regardless of `autoRenew`. `autoRenew` is currently an intent flag only; it does not extend membership or trigger renewal/payment. Existing CourseRegistration discount and price snapshots are not recalculated when a Membership expires.

| Method and path | Allowed caller | Request body | Success and response | Test status |
|---|---|---|---|---|
| `GET /api/memberships` | ADMIN, RECEPTIONIST | None | 200, `MembershipResponse[]` | Not verified after authorization changes |
| `GET /api/memberships/{id}` | ADMIN, RECEPTIONIST; MEMBER only for own membership | None | 200, `MembershipResponse` | Not verified after authorization changes |
| `GET /api/memberships/member/{memberId}` | ADMIN, RECEPTIONIST; MEMBER only when memberId is own ID | None | 200, `MembershipResponse[]` | Not verified after authorization changes |
| `POST /api/memberships` | ADMIN (manual assignment only) | `CreateMembershipRequest` | 201, `MembershipResponse` | Payment-backed purchase uses `POST /api/payments` |
| `PATCH /api/memberships/{id}/cancel-renewal` | ADMIN, RECEPTIONIST; MEMBER only for own membership | None | 200, `MembershipResponse` | Not verified after authorization changes |
| `PATCH /api/memberships/{id}/enable-auto-renew` | ADMIN, RECEPTIONIST; MEMBER only for own membership | None | 200, `MembershipResponse` | Not verified after authorization changes |
| `POST /api/memberships/expire` | ADMIN | None | 200, text message | Not verified after authorization changes |
| `GET /api/public/membership-plans` | GUEST, MEMBER, COACH, RECEPTIONIST, ADMIN | None | 200, active `MembershipPlanResponse[]` | Added in this change |
| `GET /api/membership-plans` | ADMIN | None | 200, all `MembershipPlanResponse[]`, including inactive | Added in this change |
| `GET /api/membership-plans/{id}` | ADMIN | None | 200, `MembershipPlanResponse` | Added in this change |
| `POST /api/membership-plans` | ADMIN | `CreateMembershipPlanRequest` | 201, `MembershipPlanResponse` | Not verified |
| `PUT /api/membership-plans/{id}` | ADMIN | `UpdateMembershipPlanRequest` | 200, `MembershipPlanResponse` | Not verified |
| `PATCH /api/membership-plans/{id}/status` | ADMIN | `{"status":"ACTIVE"}` or `{"status":"INACTIVE"}` | 200, `MembershipPlanResponse` | Added in this change |
| `DELETE /api/membership-plans/{id}` | ADMIN | None | 204, empty body; legacy route now deactivates rather than physically deleting | Added in this change |

Membership creation body:

```json
{"memberId":12,"planId":2,"startDate":"2026-10-10","autoRenew":false}
```

`MembershipResponse` sample: `{"id":8,"memberId":12,"memberName":"Example Member","planId":2,"planName":"Standard","startDate":"2026-10-10","endDate":"2026-11-10","autoRenew":false,"status":"ACTIVE"}`.

MembershipPlan bodies contain `name`, `description`, `price`, `durationMonths`, `courseDiscountPercent`, `personalTrainingDiscountPercent`, `allowPersonalTrainingPlan`, and `allowOneOnOneCoaching`; update also contains `status`.

Plan creation/update requires a nonblank name (max 100), nonnegative price with at most two decimal places, positive duration in months, and discounts from 0 through 100 with at most two decimal places. Status may only be `ACTIVE` or `INACTIVE`. `MembershipPlanResponse` adds `id` and `status` to the plan fields. ADMIN plans are the only plans returned by the administrative catalog; the public catalog returns active plans only. The legacy DELETE route performs soft deactivation to preserve existing Membership references; use the status PATCH route for explicit activation/deactivation. This change does not create or alter Membership records or implement purchase/payment behavior.

Sample: `{"id":2,"name":"Standard","description":"Monthly membership","price":50,"durationMonths":1,"courseDiscountPercent":0,"personalTrainingDiscountPercent":0,"allowPersonalTrainingPlan":false,"allowOneOnOneCoaching":false,"status":"ACTIVE"}`.

## Sport

Sport GET endpoints permit all authenticated roles; mutations are ADMIN-only.

| Method and path | Request body | Success and response | Test status |
|---|---|---|---|
| `GET /api/sports` | None | 200, `SportResponse[]` | Not verified |
| `GET /api/public/sports` | None; no JWT required | 200, active `PublicSportOptionResponse[]` with only `id` and `name` | H2 integration tested |
| `GET /api/public/sports/{sportId}/courses` | None; no JWT required | 200, active `CourseResponse[]` for an active sport | Added; H2 integration tested |
| `GET /api/public/courses/{id}` | None; no JWT required | 200, active `CourseResponse`; inactive course/sport is hidden as 404 | Added; H2 integration tested |
| `GET /api/public/coaches` | None; no JWT required | 200, active `PublicCoachResponse[]` (`id`, `name`, `imageUrl`) | Added for public catalog |
| `GET /api/public/facilities` | None; no JWT required | 200, active facilities of active sports, `PublicFacilityResponse[]` | Added for public catalog |
| `GET /api/sports/{id}` | None | 200, `SportResponse` | Not verified |
| `POST /api/sports` | `CreateSportRequest` | 201, `SportResponse` | Not verified |
| `PUT /api/sports/{id}` | `UpdateSportRequest` | 200, `SportResponse` | Not verified |
| `PUT /api/sports/{id}/image` | Multipart form field `file` | ADMIN; 200, updated `SportResponse` with `imageUrl` | H2 integration tested |

Create body: `{"name":"Swimming","description":"Pool training"}`. Update body also includes `status`.

Response sample: `{"id":3,"name":"Swimming","description":"Pool training","status":"ACTIVE","imageUrl":null}`.

The public sport-options response is intentionally limited to `[{"id":3,"name":"Swimming","imageUrl":null}]`; inactive Sports are omitted. `imageUrl` is null until an ADMIN uploads an image. The existing `/api/sports` routes remain authenticated.

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
| `PUT /api/coaches/{id}/profile` | `UpdateCoachProfileRequest` (`bio`, nullable, max 1000) | ADMIN; 200, updated `CoachResponse` | H2 integration tested |

Create coach body has `name`, `email`, `phone`, `gender`, `password`. Assign sport body has `sportId`, `experienceYears`, `certification`; update has `experienceYears` and `certification`.

Response samples: `CoachResponse` is `{"id":4,"userId":12,"userName":"Example Coach","email":"coach@example.com","status":"ACTIVE","imageUrl":null,"bio":null}`. `CoachSportResponse` is `{"id":2,"coachId":4,"coachName":"Example Coach","sportId":3,"sportName":"Swimming","experienceYears":2,"certification":"<certification>"}`.

`PUT /api/coaches/{id}/image` accepts multipart form field `file`, is ADMIN-only, and returns the updated `CoachResponse`. Public coach catalog responses omit account email and other account fields.

`POST /api/staff/coaches` optionally accepts `bio` (maximum 1000 characters) in addition to the existing account and specialization fields. Its `CoachResponse.id` identifies the Coach for the subsequent image upload. `GET /api/public/coaches` returns only public profile fields: `id`, `name`, `imageUrl`, nullable `bio`, and `sports[]` containing `sportId`, `sportName`, `experienceYears`, and nullable `certification`. Only ACTIVE Coach records with ACTIVE linked User accounts are included. Assignments for inactive Sports are omitted. A Coach without assignments returns an empty `sports` array; missing bio, image, or certification remains null. No account email, phone, or credential data is exposed.

Example public response: `[{"id":4,"name":"Example Coach","imageUrl":"/api/public/images/coach-photo.webp","bio":"Swimming instructor.","sports":[{"sportId":3,"sportName":"Swimming","experienceYears":2,"certification":"Regional certificate"}]}]`.

The Coach biography mapping requires manual migration `docs/sql/phase3a-coach-bio.sql`, after `phase3-entity-image-urls.sql` and before starting with `ddl-auto=validate`. It adds `dbo.coach.bio NVARCHAR(1000) NULL` and has not been executed.

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

CourseOffering dates use timezone-less `LocalDate`/`LocalDateTime` values. A new offering must start today or later, end after it starts, and have future registration and cancellation deadlines no later than midnight at the start date. `registrationDeadline` must be on or before `cancellationDeadline`; this ordering is newly enforced. On update, unchanged historical dates and deadlines may remain in the past; changed start dates cannot be moved into the past, and changed deadlines must be future. Updating the date range cannot exclude existing ClassSessions. Changing `sessionDuration` is rejected if it would make any existing session in the offering use a different duration. Existing registration opening remains 30 days before `startDate`; registration and cancellation deadlines remain separate.

CourseOffering numeric rules (approved): `plannedSessions` is required on creation and must be an integer from 1 through 60; `sessionDuration` is required on creation and must be an integer from 30 through 180 minutes. Updates enforce these ranges when the corresponding value changes. An unchanged legacy value outside the range (including a legacy null planned-session count) may remain during unrelated edits; changing it must set a valid value. Fractional JSON numbers are rejected rather than truncated. ClassSession duration continues to equal the persisted offering duration, and a duration update is rejected if any existing session would no longer match.

Read-only SQL Server legacy-data review (physical names follow the project's `dbo.course_offering` mapping; verify against the target database metadata before use because the original core-schema bootstrap is not in this repository):

```sql
SELECT id, planned_sessions, session_duration
FROM dbo.course_offering
WHERE planned_sessions IS NULL
   OR planned_sessions < 1 OR planned_sessions > 60
   OR session_duration IS NULL
   OR session_duration < 30 OR session_duration > 180
ORDER BY id;
```

This query only identifies rows for manual review; no existing offering is changed automatically.

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
| `POST /api/facilities` | `CreateFacilityRequest` (`sportIds`, `name`, `type`; legacy single `sportId` remains accepted) | 201, `FacilityResponse` | Not verified |
| `PUT /api/facilities/{id}` | `UpdateFacilityRequest` (`name`, `type`, `status`, `sportIds`) | 200, `FacilityResponse` | Not verified |
| `PUT /api/facilities/{id}/image` | Multipart form field `file` | ADMIN; 200, updated `FacilityResponse` with `imageUrl` | H2 integration tested |

Response sample: `{"id":5,"sportId":1,"sportName":"Swimming","sports":[{"id":1,"name":"Swimming","status":"ACTIVE"},{"id":3,"name":"Water Polo","status":"ACTIVE"}],"name":"Main pool","type":"POOL","status":"ACTIVE","imageUrl":null}`. `sports` contains all supported Sport IDs, names and statuses for authenticated Facility responses; the public Facility response includes only ACTIVE supported Sports. `sportId`/`sportName` remain as compatibility fields for the first assigned Sport. At least one Sport is required. New assignments must reference distinct ACTIVE Sports; a currently assigned inactive Sport can remain during unrelated edits, but cannot be newly assigned. Removing an assignment is rejected with 409 while a future or ongoing non-CANCELLED ClassSession at that Facility uses the corresponding Course Sport. The existing required `facility.sport_id` column is retained and mirrors the first selected Sport pending a separately reviewed schema cleanup.

## Local entity images

`PUT /api/sports/{id}/image`, `PUT /api/coaches/{id}/image`, and `PUT /api/facilities/{id}/image` require ADMIN and a multipart file field named `file`. Files are limited to 5 MB and detected from image bytes; accepted formats are JPEG, PNG, and WebP. Responses include the stored relative `imageUrl`, for example `/api/public/images/550e8400-e29b-41d4-a716-446655440000.png`. `GET /api/public/images/{filename}` is public and read-only. Public catalog endpoints include active Sport, Coach, and Facility images without returning private Coach account fields.

By default files are stored outside packaged resources in `backend/uploads` when Spring Boot is started with `backend` as its working directory. Override using `IMAGE_STORAGE_DIRECTORY`; `IMAGE_MAX_FILE_SIZE` can change the service limit, while the servlet multipart ceiling remains 5 MB per file. On replacement, the new file is written first; a database rollback removes that new file. The old file is removed only after commit. Cleanup is best-effort, so an unavailable filesystem may leave an orphan file; no database record is left pointing to an upload that failed before commit. Existing uploaded files are not removed by normal entity updates.

The SQL Server migration `docs/sql/phase3-entity-image-urls.sql` adds nullable `image_url NVARCHAR(500)` columns. It is manual; the project owner reports it has already been applied successfully to the local SQL Server. On a fresh database, run it after the existing migrations and before starting with `ddl-auto=validate`.

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
| `POST /api/class-sessions/generation/preview` | ADMIN | `ClassSessionGenerationRequest` | 200, preview response; read-only | Isolated tests |
| `POST /api/class-sessions/generation/confirm` | ADMIN | `ClassSessionGenerationRequest` | 201, `ClassSessionGenerationResponse` | Isolated tests |

Create sample (status omitted to use the `SCHEDULED` default; `facilityId` may be null):

```json
{"classId":3,"facilityId":6,"startTime":"2026-10-12T10:00:00","endTime":"2026-10-12T11:00:00"}
```

Update body is the same shape and must include `classId`, `startTime`, and `endTime`; `facilityId: null` clears the facility. Status update body: `{"status":"CANCELLED"}`. Both timestamps must fall within the offering's date range, including the whole end date, and their difference must exactly equal persisted CourseOffering `sessionDuration` in whole minutes. End must be strictly after start. Invalid dates or duration return 400; overlaps, planned-session target violations, and invalid state transitions return 409; missing referenced resources return 404. `SCHEDULED` can become `COMPLETED` only at/after `endTime`, or `CANCELLED` only before `startTime`; both terminal statuses remain terminal. There is no hard-delete route. `plannedSessions` is a target per CourseClass: all non-CANCELLED sessions count, while CANCELLED sessions do not. Manual creation and generated creation share this target.

### Automatic weekly schedule generation

Both generation endpoints are ADMIN-only and use the persisted CourseOffering's duration; the request cannot override it. Timestamps are timezone-less `LocalDateTime` wall-clock values. Weekdays are ISO Java weekday names (`MONDAY` through `SUNDAY`), the first eligible date defaults to the offering start date, and the requested count defaults to the selected CourseClass's remaining target slots. A date is eligible through the offering end date inclusive. Matching dates are traversed chronologically; each session ends exactly `sessionDuration` minutes after the requested daily start time.

Request example:

```json
{"courseClassId":3,"weekdays":["MONDAY","WEDNESDAY"],"startTime":"18:30:00","facilityId":6,"firstEligibleDate":"2026-10-12","requestedSessionCount":8}
```

`POST /api/class-sessions/generation/preview` returns candidate timestamps, class/offering/Coach/Facility context, current and remaining target counts, candidate-specific conflicts, overall conflicts, and `canGenerate`. It does not persist sessions. A candidate conflict or too few eligible weekdays makes the preview non-confirmable; conflicting dates are not silently skipped.

`POST /api/class-sessions/generation/confirm` accepts the same request, recomputes all candidates and validates current data again. On success it returns `{"courseClassId":3,"createdCount":8,"sessions":[...]}` with HTTP 201. The entire batch runs in one transaction and any validation or persistence failure rolls back every created session. It uses the normal ClassSession create validation for date range, duration, active Facility, planned target, and Coach/Facility overlaps. Expect 400 for malformed input, 404 for missing referenced records, and 409 when current business constraints prevent generation.

The policy is per CourseClass, although the target is configured on CourseOffering. Existing non-CANCELLED rows count toward that class target regardless of whether they were created manually or automatically; CANCELLED rows are retained but free a slot. Generation never deletes or replaces existing rows. Manual ClassSession creation also cannot exceed the same target. If existing rows already exceed the target, further creation is rejected.

Confirmation takes pessimistic locks in a consistent resource order: CourseClass, CourseOffering, optional Facility, then assigned Coach. ClassSession create/update and the corresponding CourseClass Coach reassignment, CourseOffering update, and Facility status update use the relevant locks to coordinate application writes. Preview is advisory and can become stale; confirm always revalidates. The API is not idempotent: a repeated request is a separate attempt and will normally be rejected when the target has already been filled. Database locks coordinate application operations, but direct SQL writes or other writers that do not follow this lock protocol can still race; there is no schema-level uniqueness constraint for overlapping time intervals.

Response sample:

```json
{"id":21,"classId":3,"className":"Morning group","facilityId":6,"facilityName":"Main court","startTime":"2026-10-12T10:00:00","endTime":"2026-10-12T11:00:00","status":"SCHEDULED","warnings":[]}
```

## Class attendance (Phase 4B.1)

Attendance is recorded per ClassSession and confirmed CourseRegistration. An unmarked Member is represented in the roster as `NOT_MARKED`; it is not a persisted status. Attendance statuses are `PRESENT`, `ABSENT`, and `EXCUSED`. Membership is not checked. The assigned Coach is resolved through ClassSession → CourseClass → Coach; actor IDs are always taken from the authenticated JWT.

| Method and path | Allowed caller | Request body | Behavior |
|---|---|---|---|
| `GET /api/attendance/coach/sessions` | COACH | None | Lists only ClassSessions for the authenticated Coach's assigned CourseClasses. Each session includes `sportId` and `sportName` derived from its Course. |
| `GET /api/attendance/sessions` | ADMIN, RECEPTIONIST | None | Lists sessions for staff attendance operations, using the same response fields as Coach discovery, including `sportId` and `sportName`. |
| `GET /api/attendance/sessions/{sessionId}/roster` | Assigned COACH, ADMIN, RECEPTIONIST | None | Returns session context and confirmed registrants with current status or `NOT_MARKED`; each row includes nullable `attendanceId`. |
| `PUT /api/attendance/sessions/{sessionId}/members/{memberId}` | Assigned COACH, ADMIN, RECEPTIONIST | `MarkAttendanceRequest` | Creates the initial mark or corrects an existing status. |
| `GET /api/attendance/{attendanceId}/history` | Assigned COACH, ADMIN, RECEPTIONIST, owning MEMBER | None | Returns immutable initial-mark and correction events. |
| `GET /api/attendance/me` | MEMBER | None | Returns only the authenticated Member's attendance history. |
Member attendance response fields include `attendanceId`, `classSessionId`, `courseClassId`, `courseClassName`, `startTime`, `endTime`, `sessionStatus`, `courseOfferingId`, `courseOfferingName`, `courseId`, `courseName`, `status`, `markedById`, `markedAt`, `note`, `validForStatistics`, `sportId`, and `sportName`. Sport identity is derived from the existing ClassSession → CourseClass → CourseOffering → Course → Sport relationship. No Sport fields are accepted from the client.

```json
{"attendanceId":15,"classSessionId":21,"courseClassId":3,"courseClassName":"Morning group","startTime":"2026-10-12T10:00:00","endTime":"2026-10-12T11:00:00","sessionStatus":"SCHEDULED","courseOfferingId":8,"courseOfferingName":"October offering","courseId":5,"courseName":"Swimming basics","status":"PRESENT","markedById":7,"markedAt":"2026-10-12T10:03:00","note":null,"validForStatistics":true,"sportId":2,"sportName":"Swimming"}
```

Mark request example:

```json
{"status":"PRESENT","note":"Arrived before warm-up","reason":null}
```

Roster members with and without a mark are represented as:

```json
{"classSessionId":21,"courseClassId":3,"courseClassName":"Morning group","startTime":"2026-10-12T10:00:00","endTime":"2026-10-12T11:00:00","sessionStatus":"SCHEDULED","markingAllowed":true,"members":[{"memberId":42,"attendanceId":15,"memberName":"Nguyen An","attendanceStatus":"PRESENT","markedById":7,"markedAt":"2026-10-12T10:03:00","note":null},{"memberId":43,"attendanceId":null,"memberName":"Tran Binh","attendanceStatus":"NOT_MARKED","markedById":null,"markedAt":null,"note":null}]}
```

`attendanceId` is the persisted Attendance primary key when a mark exists and `null` for `NOT_MARKED` rows. Roster reads do not create Attendance records. Clients can use a non-null ID with `GET /api/attendance/{attendanceId}/history` subject to the same object-level authorization.

Coach and staff session discovery share this response shape. Sport identity is derived through ClassSession → CourseClass → CourseOffering → Course → Sport; the session list order remains start time ascending, then session ID ascending.

```json
{"classSessionId":21,"courseClassId":3,"courseClassName":"Morning group","courseOfferingId":8,"courseOfferingName":"October offering","courseId":5,"courseName":"Swimming basics","sportId":2,"sportName":"Swimming","assignedCoachId":7,"startTime":"2026-10-12T10:00:00","endTime":"2026-10-12T11:00:00","sessionStatus":"SCHEDULED"}
```

Successful mark/correction response example:

```json
{"id":15,"classSessionId":21,"courseClassId":3,"courseClassName":"Morning group","startTime":"2026-10-12T10:00:00","endTime":"2026-10-12T11:00:00","sessionStatus":"SCHEDULED","memberId":42,"memberName":"Nguyen An","status":"PRESENT","markedById":7,"markedAt":"2026-10-12T10:03:00","note":null,"validForStatistics":true}
```

For a status correction, `reason` is required and nonblank. An initial mark creates a history row with `oldStatus: null`; each actual correction records both statuses, actor, timestamp, and reason. Repeating the same status is idempotent: the existing response is returned unchanged and no new history event is added. `markedBy` and `markedAt` on Attendance describe the latest actual mark/change; AttendanceHistory preserves the full sequence. Notes are optional and limited to 500 characters. Clients cannot supply actor IDs or timestamps.

Marking is allowed at or after the scheduled start time, including after the session is COMPLETED. A CANCELLED session returns 409 and cannot be marked or corrected. Other validation conflicts (before start or missing CONFIRMED registration) return 409; missing resources return 404; malformed/invalid request fields return 400; missing authentication returns 401 and unauthorized role/object access returns 403. A CANCELLED session's existing attendance and history remain readable by authorized callers; Member responses include the session status and set `validForStatistics` to false. Attendance operations do not change session or registration status, create ABSENT records automatically, or create Center CheckIn records.

## Attendance SQL migration

`docs/sql/phase4b1-class-attendance.sql` creates the Attendance and AttendanceHistory tables. Apply it manually after the approved core schema, the ClassSession migration, and the CourseRegistration migration, and before starting the backend with `spring.jpa.hibernate.ddl-auto=validate`. The migration was not executed or tested against SQL Server as part of this implementation; review and back up the intended database before manual execution.

Facility and Coach overlaps use half-open interval semantics (`existing.startTime < requested.endTime` and `existing.endTime > requested.startTime`), so sessions that only touch at an endpoint do not overlap. Cancelled sessions do not reserve facilities or coaches. Sessions outside CourseOffering dates are rejected. The per-CourseClass planned-session target is enforced for both manual and generated creation, counting every non-CANCELLED session. The Member default class schedule excludes CANCELLED sessions; authorized administrative GET routes retain historical visibility.

Lifecycle transitions: `SCHEDULED` can become `COMPLETED` only when current time is at or after `endTime`, and can become `CANCELLED` only before `startTime`. `COMPLETED` and `CANCELLED` are terminal; repeating the same status is idempotent, but changing to another status returns 409. PUT schedule updates are allowed only for `SCHEDULED` sessions. Invalid status/time request data returns 400. Statuses change only through the API; there is no time-driven auto-transition.

Facility status updates accept `ACTIVE` or `INACTIVE`, case-insensitively, and persist uppercase. Null, blank, or any other value returns 400. Switching from ACTIVE to INACTIVE returns 409 if a `SCHEDULED` ClassSession using the Facility has `endTime` later than the current time, covering sessions in progress and upcoming sessions. A session ending exactly now, an ended session, or a `COMPLETED`/`CANCELLED` session does not block deactivation. No session is deleted, cancelled, or edited by this operation. When a Facility is supplied on ClassSession POST or changed on PUT, its current status must be ACTIVE; missing Facility IDs return 404 and any other status returns 409. On PUT, a future `SCHEDULED` session cannot retain the same Facility if that Facility has become unavailable; choose another ACTIVE Facility or cancel the session. GET continues returning historical sessions and their Facility data regardless of Facility status. `FacilityService` creates facilities as ACTIVE.

ClassSession POST/PUT and generation preview/confirm also require the selected Facility to support the Sport associated with the CourseOffering's Course. A null Facility remains allowed. Generation preview reports this as a candidate conflict; confirmation and manual writes reject incompatible assignments. Facility assignment edits share the Facility schedule row lock with session scheduling and cannot remove a Sport needed by a future or ongoing non-CANCELLED session. Cancelled and historical completed records are retained.

## Flow 4: Training plans, results and progress (Phase 10.1)

Writes are limited to the class's assigned COACH or ADMIN (403 otherwise). Staff (ADMIN/RECEPTIONIST) and the assigned COACH can read class data; a MEMBER reads a plan only for a class with a CONFIRMED registration and reads everything else through `GET /api/training/me`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/training/classes` | COACH: assigned classes; staff: all classes. Includes member/session counts and plan goal. |
| GET / PUT | `/api/training/classes/{classId}/plan` | Read (404 until created) or create/update the training plan `{goal, description}`. |
| POST | `/api/training/classes/{classId}/plan/exercises` | Add `{name, sets?, reps?, durationMinutes?, notes?, sortOrder?}`; 409 before the plan exists. |
| PUT / DELETE | `/api/training/exercises/{exerciseId}` | Modify or remove an exercise. |
| GET | `/api/training/classes/{classId}/sessions` | Sessions with PRESENT/result counts, `canComplete`, `canRecordResults`. |
| POST | `/api/training/sessions/{sessionId}/complete` | Conduct session: SCHEDULED → COMPLETED at/after `endTime`; idempotent; 409 if CANCELLED or before end. |
| GET | `/api/training/sessions/{sessionId}/results` | Result sheet: CONFIRMED members with attendance status and recorded result. |
| PUT | `/api/training/sessions/{sessionId}/results/{memberId}` | Record/update `{score 1–10, notes?}`; 409 unless the session started, is not CANCELLED and the member is marked PRESENT. |
| GET | `/api/training/classes/{classId}/progress` | Per member: attendance, result count, average score, latest evaluation and history. |
| POST | `/api/training/classes/{classId}/members/{memberId}/evaluations` | Append `{rating 1–5, comment}`; 409 without a CONFIRMED registration. |
| GET | `/api/training/me` | MEMBER: plan, attendance summary, session results and evaluations for each CONFIRMED class. |

Plan, exercise, result, evaluation and session-completion writes create AuditLog entries. Apply `docs/sql/phase10-1-flow4-training.sql` before starting the backend with `ddl-auto=validate`.

## Module coverage note

The Phase 2 CourseClass registration API is documented below. `/api/classes` manages CourseClass records; `/api/class-sessions` manages individual meetings.

## OpenAPI / Swagger

No Swagger/OpenAPI dependency or configuration was found in `pom.xml` or source. For this Spring Boot 4.1.1 Web MVC application, the springdoc project documents Spring Boot 4 support in springdoc 3.x; the candidate starter is `org.springdoc:springdoc-openapi-starter-webmvc-ui`. Add it only after choosing a compatible 3.x release, then protect or explicitly permit `/v3/api-docs/**` and `/swagger-ui/**` in Spring Security. See [springdoc compatibility documentation](https://springdoc.org/) and [springdoc project README](https://github.com/springdoc/springdoc-openapi).

## Pre-merge review items

- Membership purchase is payment-backed through the Flow 3 `POST /api/payments` operation; pending payments do not activate memberships. See the Payment section for the current Member and receptionist workflows.
- The application requires `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`; configure them in the runtime environment. If the previously tracked SQL Server password was real or shared, rotate it because Git history may retain it.
- Configure `CORS_ALLOWED_ORIGINS` for deployed frontend origins. Defaults permit only `http://localhost:5173` and `http://localhost:3000`.
- The CourseClass registration revision passed the full isolated-H2 Maven suite and package build; Postman verification remains pending. SQL Server locking and migration behavior still need validation in a disposable database before deployment.
- CourseClass registration is implemented under `/api/course-registrations`; registration creates a PENDING_PAYMENT hold and only verified payment before paymentDeadline confirms it. No public account-registration endpoint is exposed.


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
| POST | `/api/course-registrations` | MEMBER | Reserves a seat for the JWT-authenticated member; returns 201 and a PENDING_PAYMENT registration with a payment deadline |
| GET | `/api/course-registrations/me` | MEMBER | Lists the caller's registration history and lifecycle state |
| POST | `/api/course-registrations/{id}/cancel` | MEMBER | Cancels only the caller's PENDING_PAYMENT registration; releases the seat |
| GET | `/api/course-registrations/me/class-sessions` | MEMBER | Lists sessions only for CourseClasses in the caller's CONFIRMED registrations |
| GET | `/api/course-registrations` | ADMIN, RECEPTIONIST | Lists registrations with Course, CourseOffering, and CourseClass identity |

POST body: `{"classId":6}`. Client-supplied member/course/offering identifiers and prices are not accepted; member identity comes from JWT, Course/Offering are derived from the selected CourseClass, and prices are calculated by the server. The response includes the immutable price snapshots `originalPrice`, `membershipDiscount` (money amount), and `finalPrice`, plus `registeredAt` and `paymentDeadline`. Example: `{"id":31,"memberId":4,"courseId":10,"courseName":"Beginner Swim","courseOfferingId":11,"courseOfferingName":"October Swim","classId":6,"className":"Morning group","sportId":3,"requiredLevel":"BEGINNER","status":"PENDING_PAYMENT","registeredAt":"2026-10-13T10:30:00","paymentDeadline":"2026-10-14T10:30:00","cancelledAt":null,"originalPrice":100.00,"membershipDiscount":10.00,"finalPrice":90.00}`. The timestamp values are timezone-less ISO LocalDateTime fields interpreted in `Asia/Ho_Chi_Minh`.

Registration requires an ACTIVE MEMBER, ACTIVE CourseClass, OPEN CourseOffering (the existing offering service uses `OPEN` as its registration-accepting status), ACTIVE Course and Sport, and a date on or after `CourseOffering.startDate.minusDays(30)`. Registration must be strictly before `registrationDeadline`. A new hold deadline is `min(registeredAt + 24 hours, registrationDeadline)`; a non-positive hold is rejected. The existing timezone-less LocalDateTime deadline is treated as Vietnam wall time (`Asia/Ho_Chi_Minh`); API timestamps are interpreted in that zone and stored as `DATETIME2`, consistently with existing conventions. Membership is optional. At registration time, an ACTIVE Membership whose exclusive validity interval contains the registration date (`startDate <= registrationDate < endDate`) contributes the highest eligible `courseDiscountPercent`; discounts do not stack. Original price, discount amount, and final price remain server-calculated snapshots. Course age, verified sport level, and schedule eligibility continue to be enforced.

`PENDING_PAYMENT` and `CONFIRMED` registrations occupy capacity; only a pending row with `paymentDeadline > now` occupies a seat. At or after the deadline, a hold is EXPIRED and does not count even if the scheduled worker has not run. Registration locks the MEMBER row and then CourseClass row before expiring due holds, checking duplicates/schedule and counting occupied seats. Payment settlement locks the Payment first and then its registration; the registration row is rechecked under lock. A successful verified Flow 3 payment confirms only a pending registration strictly before its deadline. At or after the deadline the registration becomes EXPIRED and Payment is recorded as `REFUND_REQUIRED`; no automatic refund or seat reclaim occurs. A callback received after deadline is conservatively treated as late because the current Payment contract carries no trusted provider settlement timestamp; Flow 3 must not later confirm the expired registration. The gateway owner should supply a verified settlement timestamp if business policy expects delayed callbacks to prove earlier settlement.

EXPIRED and CANCELLED registrations do not occupy seats. A member may cancel only their own PENDING_PAYMENT hold through the new cancel endpoint. Confirmed-registration cancellation/refund remains unsupported because no confirmed-course refund policy or endpoint existed in the inspected baseline; this phase does not invent one. A member may create a new hold after expiration/cancellation if eligibility, registration window, duplicate-offering rule and capacity checks pass. `/api/course-registrations/me/class-sessions` continues to include only CONFIRMED registrations.

The scheduled expiration worker processes bounded batches and locks/rechecks each row; lazy expiration runs during capacity checks, member history reads and payment settlement, so correctness does not depend on scheduling. Existing registration creation price snapshots and discount behavior are preserved. Existing offering-level duplicates are serialized by the MEMBER row lock; filtered unique indexes protect duplicate pending and confirmed `(member_id, class_id)` rows.

Registration remains separate from payment creation/collection. A member or staff client can use existing `POST /api/payments` with purpose `COURSE_REGISTRATION` and `referenceId` equal to the registration ID; the server quotes the persisted `finalPrice`. Only Flow 3's existing verified online callback or staff counter-processing can settle it. The manual hold migration is `docs/sql/phase8-7c-course-registration-holds.sql`, after `phase2-course-registrations.sql`; do not deploy this entity mapping before applying the migration. It preserves old confirmed rows with null deadlines and does not turn them into unpaid holds.


# Personal Training (PT 1-on-1)

PT bookings are individual 90-minute sessions priced at 500,000 VND. The default is `app.pt.session-base-price=500000`; set `APP_PT_SESSION_BASE_PRICE` in the environment to override it. A MEMBER with an ACTIVE membership valid on the requested session date (`startDate <= date < endDate`) may book when the membership plan has `allowOneOnOneCoaching=true`. The plan's `personalTrainingDiscountPercent` is applied to the PT price. `MembershipPlan.price` is unrelated.

All endpoints require authentication. MEMBER-only endpoints derive the member ID from the JWT subject. COACH assignments derive the coach from the authenticated account. Eligible coach lookup requires a proposed sport and exact start time; it returns active qualified coaches with no PT or non-cancelled group-class overlap at that time. Center operating hours and booking increments are not modeled, so clients may submit any future start time that meets the fixed 90-minute duration and conflict checks.

- `GET /api/pt-bookings/eligible-coaches?sportId={id}&startTime=2026-10-20T10:00:00` - MEMBER; quote and coaches available for the requested slot.
- `POST /api/pt-bookings` - MEMBER; JSON `{ "coachId": 12, "sportId": 3, "startTime": "2026-10-20T10:00:00" }`; returns 201 and a `PENDING_PAYMENT` booking with server-calculated `endTime`, prices, 90-minute duration, member, coach, sport, and `paymentExpiresAt` (15 minutes after creation).
- `GET /api/pt-bookings/me` - MEMBER's own history.
- `GET /api/pt-bookings/assigned` - authenticated COACH's bookings.

`PENDING_PAYMENT` reserves the interval for 15 minutes. A scheduled worker transitions due bookings to terminal `EXPIRED`; overlap checks also ignore pending rows whose `paymentExpiresAt` has passed, so a delayed worker does not hold the slot. `EXPIRED` and `CANCELLED` bookings do not block new bookings. Expiry and payment confirmation must lock the same booking row and recheck status/deadline in the transaction. Flow 3 may transition a booking from `PENDING_PAYMENT` to `CONFIRMED` only after verified payment and only before `paymentExpiresAt`; booking creation is not proof of payment. A late successful payment must leave the booking `EXPIRED`; Flow 3 must record/route the payment exception without confirming the booking. The refund or other remedy for late payment is intentionally unspecified. Payment amounts should reconcile to stored `originalPrice`, `discount`, and `finalPrice`. No cancellation endpoint is exposed.

SQL Server setup: after the core schema and `class_session` table, review and manually run `docs/sql/phase2-personal-training-bookings.sql` in `SportCenterDB`. The migration is not executed by the application and Hibernate remains `ddl-auto=validate`.

## Flow 3: Payment and reports

Payment is a separate operation from membership purchase, course registration and PT booking. The client never sends an amount; the server derives it from the referenced record.

| `purpose` | `referenceId` | Amount charged | On successful payment |
|---|---|---|---|
| `MEMBERSHIP` | `MembershipPlan.id` (plan must be `ACTIVE`) | `plan.price` | Activates a new ACTIVE membership from today, or, if one is still valid, renews by adding a new membership that starts on the latest current `endDate` |
| `COURSE_REGISTRATION` | `CourseRegistration.id` (own, `PENDING_PAYMENT`, deadline not reached) | registration `finalPrice` snapshot | Confirms the registration atomically with Payment success; late settlement becomes `REFUND_REQUIRED` and EXPIRED |
| `PT_BOOKING` | `PersonalTrainingBooking.id` (own, `PENDING_PAYMENT`, hold not expired, not yet paid) | booking `finalPrice` | Confirms the booking (`PENDING_PAYMENT` -> `CONFIRMED`) |

Payment status: `PENDING` -> `SUCCESS` | `CANCELLED` | `REFUND_REQUIRED`. `REFUND_REQUIRED` means money was received but could not be applied (PT hold expired first, registration no longer active, the reference was already paid by another payment, or the gateway reported success after the payment was cancelled). The booking is left `EXPIRED`; the refund itself is handled manually. `REFUND_REQUIRED` is excluded from revenue reports.

Endpoints:
- `POST /api/payments` - MEMBER (for self) or ADMIN/RECEPTIONIST (with `memberId`). Body `{"method":"ONLINE|COUNTER","purpose":"...","referenceId":1,"autoRenew":false}`; `autoRenew` applies to `MEMBERSHIP` only. `ONLINE` responses include `paymentUrl`.
- `GET /api/payments/online/callback?transactionCode=&success=&signature=` - public; mock gateway only (`PAYMENT_PROVIDER=mock`). Accepted only with a valid HMAC-SHA256 signature. Replays are idempotent. **Demo only:** `MockPaymentGateway` does not contact a payment provider or verify money settlement; opening its local callback URL simulates success. It is disabled by default and only works when explicitly enabled with `PAYMENT_MOCK_GATEWAY_ENABLED=true` under a demo/test profile. Do not enable it in production.
- `GET /api/payments/vnpay/return` - public browser redirect. Ordinary `TXN` purchase references preserve the existing signed-callback behavior. `RNL` renewal references never settle from this route; the route redirects with `paymentStatus=PENDING`, and the frontend must reload authenticated payment history for current status. Return query values are display hints only.
- `GET /api/payments/vnpay/ipn` - public VNPay server-to-server notification. Ordinary `TXN` references retain the existing payment callback path. Valid `RNL` references resolve to a persisted checkout attempt and are verified for HMAC-SHA512, merchant code and amount before renewal settlement. `RspCode` values: `00` durably processed (including a capture recorded for manual reconciliation), `02` already processed, `97` invalid signature/merchant, `01` unknown or invalid reference, `04` amount mismatch, `99` durable processing failed. A successful RNL capture uses `RenewalSettlementService`; failed signed outcomes are retained on the attempt and do not settle or cancel the parent Payment. Captures rejected by renewal eligibility, late captures, conflicting attempts, or evidence are retained for reconciliation; this flow does not issue refunds.
- `PATCH /api/payments/{id}/counter` - ADMIN/RECEPTIONIST; body `{"success":true,"note":"..."}`. Records `processedBy`.
- `PATCH /api/payments/{id}/cancel` - owner MEMBER or ADMIN/RECEPTIONIST; `PENDING` payments only.
- `GET /api/payments/me` - MEMBER history. `GET /api/payments/member/{memberId}` - ADMIN/RECEPTIONIST.
- `GET /api/reports/revenue?from=YYYY-MM-DD&to=YYYY-MM-DD&groupBy=DAY|MONTH|YEAR` - ADMIN only; inclusive Asia/Ho_Chi_Minh calendar date range over verified `paidAt`, `SUCCESS` payments only. `groupBy` defaults to `MONTH`. The response includes `totalRevenue`, `revenueByPurpose`, `revenueByMethod`, and `periods` with each period's `periodStart`, `totalRevenue`, and purpose totals. Empty periods and supported purposes are returned with zero values; the requested range is limited to 366 output periods. `GET /api/reports/members` and `/api/reports/class-registrations` accept the same inclusive `from` and `to` dates.

`POST /api/memberships` is ADMIN-only (manual assignment). A MEMBER purchases for the JWT-authenticated account through `POST /api/payments`; omit `memberId` in a MEMBER request. The server checks that the account is active and has MEMBER role, requires an active plan, and derives amount from the plan. A pending payment does not activate a Membership. The membership UI continues to use `method: "COUNTER"`; course checkout supports VNPay Sandbox ONLINE separately; it offers no simulated success action. The mock ONLINE capability remains restricted to explicit demo/test configuration. COUNTER confirmation remains staff-only; ONLINE settlement is verified by the existing gateway callbacks. A repeated pending purchase for the same member and plan returns 409, and the member may cancel their own pending payment through the existing cancel route before retrying. This prevents common repeated clicks but is not a general idempotency key.

The MEMBER membership page shows active memberships, pending payment requests, and active plans. It never confirms payment or activates a Membership from the client. Pending requests instruct the member to pay at reception; only a successful staff confirmation activates or renews the membership. The mock online gateway remains limited to its explicit demo/test configuration and is not a real payment provider.

Receptionist membership sales are handled in one workflow at `/receptionist/membership-sales`: select an active MEMBER, review current memberships and payment history, choose an active plan, create a COUNTER payment, then explicitly record whether payment was received. The payment API remains the only purchase and confirmation implementation. The Consultation Queue already supports converting/linking a consultation to a MEMBER, so the receptionist sidebar no longer duplicates that path with standalone member registration. The legacy receptionist `/staff/register-member` URL redirects to `/staff/consultations`; ADMIN access to the registration page remains.

Configuration: simulated callbacks require `PAYMENT_CALLBACK_SECRET` (a long random string) and `PAYMENT_MOCK_GATEWAY_ENABLED=true` under an explicitly active `demo`, `dev`, `development`, `test`, or `phase1` profile; the callback URL must use loopback. Production profiles always disable the simulator. When disabled, ONLINE payment creation returns 503; COUNTER payments remain available.

VNPay sandbox: set `PAYMENT_PROVIDER=vnpay`, `VNPAY_TMN_CODE` and `VNPAY_HASH_SECRET` (from the sandbox registration email at https://sandbox.vnpayment.vn/devreg) in `backend/.env`; never commit them. Optional overrides: `VNPAY_PAY_URL` (default sandbox `vpcpay.html`), `VNPAY_RETURN_URL` (default `http://localhost:8080/api/payments/vnpay/return`), `PAYMENT_FRONTEND_RESULT_URL` (default `http://localhost:5173/member/membership`). The backend fails to start if the TMN code or hash secret is missing while `PAYMENT_PROVIDER=vnpay`. With VNPay, ONLINE `paymentUrl` is the VNPay payment page (amount sent as VND x 100, `vnp_TxnRef` is the transaction code without `-`, link valid for 15 minutes). Sandbox testing uses the NCB test card listed in the VNPay sandbox documentation. An abandoned ONLINE payment stays `PENDING` until the member or staff cancels it. SQL Server: review and manually run `docs/sql/phase2-payment.sql` before starting with `ddl-auto=validate`; it has not been executed as part of this integration.

## Notifications (Phase 6.1A)

All four authenticated roles (ADMIN, RECEPTIONIST, COACH, MEMBER) may read and update only notifications owned by the authenticated user. The owner comes from the JWT subject; no endpoint accepts a user ID. There is intentionally no public notification creation endpoint. Internal services may create notifications for future business-event integrations.

| Method and path | Allowed caller | Request | Success |
|---|---|---|---|
| `GET /api/notifications?page=0&size=20` | All authenticated roles | Optional `page` (default 0) and `size` (default 20, maximum 100) | 200, Spring `Page` of notification DTOs, newest first (`createdAt`, then `id`, descending) |
| `GET /api/notifications/unread-count` | All authenticated roles | None | 200, `{"unreadCount":2}` |
| `PATCH /api/notifications/{id}/read` | All authenticated roles; owner only | None | 200, updated notification DTO; repeated reads are idempotent |
| `PATCH /api/notifications/read-all` | All authenticated roles | None | 200, `{"updatedCount":2}`; count is unread rows changed by this request |

Notification DTO fields are `id`, `title`, `message`, `isRead`, and `createdAt`. The list response uses Spring's standard paged JSON wrapper, with these fields among the wrapper metadata:

```json
{
  "content": [
    {"id": 42, "title": "Thông báo", "message": "Nội dung thông báo", "isRead": false, "createdAt": "2026-10-18T12:30:00"}
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true
}
```

Invalid pagination returns 400. A nonexistent notification and another user's notification both return 404 so ownership cannot be probed; missing/invalid authentication returns 401. An already-read row remains read and does not produce additional state changes. Mark-all updates only unread notifications belonging to the authenticated user, directly in the database. `createdAt` is generated by the backend and is not accepted from clients. This phase does not connect notifications to Payment, Attendance, Consultation, Membership, or other business events. The manual SQL Server migration is `docs/sql/phase6-1a-notifications.sql`; it has not been executed.

### ADMIN broadcast (Phase 6.1B)

`POST /api/admin/notifications/broadcast` is restricted to authenticated ADMIN accounts. It creates one unread personal Notification for each ACTIVE ADMIN, RECEPTIONIST, COACH, and MEMBER account, including the initiating ADMIN when active. Inactive accounts and accounts with other roles are excluded. The recipient set is selected by one database query within the write transaction; if account status changes concurrently after that query, an account deactivated during the broadcast can still receive it and a newly activated account can be omitted.

Request fields are limited to `title` (required, nonblank, at most 150 characters) and `message` (required, nonblank, at most 5,000 characters):

```http
POST /api/admin/notifications/broadcast
Authorization: Bearer <admin-access-token>
Content-Type: application/json
```

```json
{"title":"Center maintenance notice","message":"SportCenter will be temporarily closed for maintenance."}
```

The response is `200 OK` and `recipientCount` is the number of rows persisted by the transaction:

```json
{"recipientCount":100}
```

The operation uses a batched insert inside one transaction: database failure rolls back the whole broadcast. Invalid bodies return 400, unauthenticated callers receive 401, and non-ADMIN roles receive 403. Each accepted request is a distinct broadcast. Because the schema has no durable idempotency key, retrying after an uncertain network outcome may create duplicate notifications; the server does not deduplicate by title or message. No broadcast table or Notification schema change is used.

### Automatic business notifications (Phase 6.1C)

These are internal notifications; this phase adds no public endpoint and does not change the existing notification or ADMIN broadcast contracts. Typed application events are delivered after the successful business transaction commits. Notification persistence runs in a separate transaction. A delivery exception is logged and contained, so it does not roll back the consultation, PT booking/payment, or class operation.

| Business event | Recipients | Notification condition |
|---|---|---|
| Consultation created as `PENDING` | Every ACTIVE RECEPTIONIST | GUEST and MEMBER submissions; one message includes only the persisted consultation ID. Inactive receptionists and other roles are excluded. |
| Consultation transitions `IN_PROGRESS` -> `COMPLETED` | Linked ACTIVE MEMBER | Guest requests and inactive/non-MEMBER linked accounts are skipped. Rejected/repeated transitions do not notify. |
| Consultation transitions `IN_PROGRESS` -> `CANCELLED` | Linked ACTIVE MEMBER | Guest requests and inactive/non-MEMBER linked accounts are skipped. Private cancellation details are omitted. |
| PT booking transitions `PENDING_PAYMENT` -> `CONFIRMED` after verified successful payment | ACTIVE MEMBER and ACTIVE linked COACH User | The Coach is resolved through `Coach.user`; pending creation, failed/cancelled payment, expiration, `REFUND_REQUIRED`, and repeated confirmation do not notify. |
| CourseClass created with Coach or reassigned to a different Coach | Newly assigned Coach's ACTIVE linked User | An unchanged Coach assignment and unrelated edits do not notify; the previous Coach is not notified. |

The current Notification schema is sufficient; no migration is needed. Delivery is best-effort after commit, not a transactional outbox: a process crash after the business commit and before delivery can lose a notification, and there is no durable event ID for recovery or exactly-once deduplication. Successful transaction events are emitted once by the relevant state-transition code; retries that do not transition state do not emit another event. Recipients are resolved server-side from persisted entities and ACTIVE user/role status. No phone, email, private notes, password, or payment credentials are included. SQL Server migration validation and Postman/live API verification remain pending.

## Audit Log (Phase 7.1)

AuditLog is an append-only internal record of selected successful mutations. It stores the authenticated actor ID and role snapshot when an HTTP actor is present, action, logical entity type/ID, a controlled description, and a UTC `createdAt`. It does not store entity snapshots, request JSON, passwords, tokens, contact details, private consultation notes, payment payloads, or attendance free-text. The actor ID FK is nullable for trusted system-internal calls and uses NO ACTION on User deletion; physical deletion of a User referenced as an actor is rejected by UserService.

Only the following operations are logged: ADMIN User creation and actual role/status changes; MembershipPlan creation, meaningful detail updates, actual status transitions including legacy DELETE soft-deactivation; ConsultationRequest TAKE/COMPLETE/CANCEL transitions; initial Attendance MARK and actual status CORRECT; and one BROADCAST record per successful ADMIN notification broadcast. Reads, User password changes, consultation submission, failed or repeated transitions, idempotent attendance marks, unchanged plan/user updates, and individual notification recipients are not logged. Attendance correction reason/note and broadcast message are not copied into audit descriptions.

| Method and path | Access | Query parameters | Result |
|---|---|---|---|
| `GET /api/admin/audit-logs?page=0&size=20` | ADMIN only | Optional `actorUserId`, `action`, `entityType`, `entityId`, `from`, `to` | 200, Spring `Page<AuditLogResponse>`; size 1–100, newest first (`createdAt DESC`, then `id DESC`) |

Response fields are `id`, `actorUserId`, `actorRole`, `action`, `entityType`, `entityId`, `description`, and `createdAt`. All filters are applied in the database before pagination. `action` and `entityType` are case-normalized; `entityId` is an exact trimmed match. `from` and `to` must be ISO-8601 date-times including `Z` or an explicit offset, for example `2026-10-18T09:00:00+07:00`. They are normalized to UTC; `from` is inclusive and `to` is exclusive. `from` later than `to` returns 400. AuditLog timestamp values are written as UTC into timezone-less `DATETIME2(6)` and serialized as local date-time values without a zone suffix, following the existing API convention.

Unauthenticated callers receive 401; RECEPTIONIST, COACH, and MEMBER receive 403. Invalid pagination, date filters, or oversized filter values return 400. There are no create/update/delete AuditLog endpoints. Business mutation and audit insert share one transaction (`MANDATORY` audit write): business failure leaves no success audit, and audit failure rolls back the mutation. ADMIN broadcast notifications and its single audit row share the same transaction. User DELETE remains a soft business restriction only for MEMBER accounts as before; other Users referenced as audit actors now receive 409 rather than being physically removed.

The single-table migration is `docs/sql/phase7-1-audit-log.sql`; it requires `dbo.users.id` as SQL Server INT and must be applied manually after the core schema, before startup with `ddl-auto=validate`. It has not been executed. The API is not available on `origin/main` yet: the implementation branch is `feature/notification-core`, currently ahead of main. H2 tests do not establish SQL Server migration compatibility. AuditLog is transactional, not an outbox: it records only the listed business changes and does not provide immutable database-level protection against a privileged DBA modifying rows.


## Phase 8.7D MEMBER course checkout

- Entry route: /member/course-checkout/:registrationId, linked from the course detail's new/existing registration and member registration history. Result route: /member/payment-result. Both require normal MEMBER authentication; login preserves the internal destination including payment-result queries. Existing 401 handling clears the expired session and preserves the destination for reauthentication.
- The backend default frontend return URL, /member/membership, forwards paymentId/paymentStatus/reason query returns to /member/payment-result. Without those query parameters the membership page is unchanged. No provider configuration is modified. An operator override to a different route/origin must be verified separately; private environment files were not inspected. Same-tab navigation preserves the existing sessionStorage login on return to the same frontend origin.
- Before creating a payment the client refreshes GET /api/course-registrations/me and GET /api/payments/me. It uses registration response id, courseName, courseOfferingName, className, finalPrice, status and paymentDeadline. POST /api/payments sends only method ONLINE or COUNTER, purpose COURSE_REGISTRATION, referenceId equal to the registration id. It never submits price/memberId and never recreates the registration for retries.
- ONLINE navigation accepts only HTTPS sandbox.vnpayment.vn/paymentv2/vpcpay.html, without embedded credentials or a custom port, with gateway checkout command and signature parameters. The browser goes to the returned URL in the same tab. Local simulator URLs and other hosts are intentionally rejected; this UI does not verify HMAC or call any signed return/IPN endpoint. Alternative checkout hosts require explicit frontend review.
- sessionStorage checkout context contains only the current registration/payment IDs and an interrupted-request flag, scoped by account. No payment URL is retained. History does not regenerate URLs. VNPay links last about 15 minutes, independently of the registration deadline; the UI cannot tell whether a pending ONLINE attempt is in flight or its link expired. It warns against paying twice and requires explicit acknowledgement before a new attempt while the hold is valid. ONLINE attempts are never automatically canceled. A pending COUNTER request is displayed/reused and prevents additional payment creation by default. Explicit counter-request cancellation uses PATCH /api/payments/{id}/cancel; this does not cancel the registration hold.
- Redirect query values never determine settlement. The result page matches paymentId only against authenticated /api/payments/me, then matches referenceId to authenticated registration history. Invalid/missing/unowned IDs show guidance rather than a guessed payment. Non-course returned payments show their authenticated payment status and links to the existing membership/PT pages.
- Payment PENDING is distinct from registration PENDING_PAYMENT. Only backend registration CONFIRMED is described as an active registration. EXPIRED means the seat is released; CANCELLED registrations cannot be checked out. REFUND_REQUIRED is shown distinctly with staff reconciliation/refund guidance and blocks further checkout. A successful payment plus inconsistent registration state also blocks another attempt. The backend remains authoritative at the deadline, including its conservative late-callback policy.
- Status reads are sequential, bounded to 12 reads at five-second intervals, stop at terminal states/errors, and ignore results after unmount. Manual refresh remains available. Existing registration-history cancellation remains available via POST /api/course-registrations/{id}/cancel and refreshes registration history.
- Existing API gaps retained: no single-payment query, payment URL recovery/regeneration, provider-link expiry status, provider settlement timestamp, or general payment idempotency key. UI locking and refreshed history reduce accidental duplicates in one page but cannot guarantee exclusion across separate tabs/devices. The backend duplicate-success/REFUND_REQUIRED rules remain necessary. No new staff course-payment queue is exposed; counter customers provide their payment ID for existing authorized settlement.
- Manual verification remains required: same-tab sandbox return and real IPN, default/overridden return destinations, authentication loss/login recovery, browser refresh/back, pending/terminal/error displays, mobile layout, cancellation, deadline races and SQL Server compatibility. No live gateway or SQL Server verification was performed in Phase 8.7D.


## Phase 8.8A actionable course counter-payment queue

GET /api/payments/pending-courses?page=0&size=20 is available only to active ADMIN and RECEPTIONIST accounts. MEMBER/COACH receive 403; unauthenticated callers receive 401. The service independently checks the active staff account. Pagination matches pending-memberships: page is zero-based and nonnegative, size is 1-100 (default 20), invalid values return 400. There are no search or client-controlled sorting parameters in this phase.

The response is a Spring Page of PendingCoursePaymentResponse. Example (standard Page metadata abbreviated):

```json
{
  "content": [{
    "paymentId": 42,
    "memberId": 12,
    "memberName": "Example Member",
    "registrationId": 31,
    "courseId": 5,
    "courseName": "Beginner Swimming",
    "classId": 8,
    "className": "Morning class",
    "amount": 450000.00,
    "paymentStatus": "PENDING",
    "registrationStatus": "PENDING_PAYMENT",
    "paymentDeadline": "2026-10-20T10:00:00",
    "paymentCreatedAt": "2026-10-19T10:00:00"
  }],
  "number": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

Eligibility is evaluated in the database using one server-time cutoff in Asia/Ho_Chi_Minh per request: Payment COUNTER + COURSE_REGISTRATION + PENDING, referenced registration PENDING_PAYMENT, paymentDeadline strictly greater than now. Equality, null deadlines, terminal registration/payment states, ONLINE, MEMBERSHIP and PT_BOOKING are excluded. The purpose-scoped Payment.referenceId is joined to CourseRegistration.id; registration member must match payment member. Missing/inconsistent references are excluded. Course/class details come from CourseRegistration -> CourseClass -> CourseOffering -> Course. No passwords, email, phone, gateway URL, transaction code or secrets are exposed.

Results are ordered by Payment.createdAt ASC, Payment.id ASC (oldest first). Projection and count queries use database pagination with scalar DTO construction; no collection fetch, per-row lazy entity access, or in-memory pagination is used. This is a changing queue, not a reserved/snapshot selection: settlement must always revalidate, and offset pages may move as other staff process payments.

Settlement remains PATCH /api/payments/{paymentId}/counter with {"success":true,"note":"Receipt verified at reception"}. The existing transaction locks Payment, requires COUNTER/PENDING, locks/rechecks CourseRegistration and the current deadline, confirms a valid hold and records SUCCESS atomically. Repeated settlement of the same payment returns 409. Another received payment for an already confirmed registration becomes REFUND_REQUIRED without a second confirmation. success=false records CANCELLED and does not confirm a hold.

An expired/canceled hold with a still-pending Payment is NON-ACTIONABLE: listing this queue does not change Payment, paidAt, or registration status, and does not infer money receipt. Existing scheduled/lazy registration expiration continues independently. Only explicit verified receipt through settlement can record received money: a late receipt is REFUND_REQUIRED and cannot reclaim an expired seat; a canceled registration remains CANCELLED. Refunds/reconciliation remain manual. A missing queue entry is not evidence that money was received or refunded.

No separate exception API is added. Staff can review existing GET /api/payments/member/{memberId} history and registration records for stale attempts. A future exception screen may be useful, but should distinguish unpaid stale attempts from received funds requiring refund. This phase adds no frontend queue and does not broaden MEMBER history access.

No SQL/schema change is required. Existing payment purpose-scoped references and the deployed Phase 8.7C payment_deadline/status schema are prerequisites. No migration was executed. H2 confirms application query behavior; SQL Server pagination, query-plan/index performance, migrated-schema compatibility, concurrent staff settlement and deadline races still require approved integration verification.


## Phase 8.8B receptionist course-payment workflow

- Open /receptionist/course-payments from Thanh toan khoa hoc (Vietnamese course-payments label) in the ADMIN or RECEPTIONIST sidebar. The route uses the existing StaffRoute guard, with an additional page role check; MEMBER/COACH cannot use the page. Existing membership sales, consultations, PT and member checkout routes remain unchanged.
- The page reads GET /api/payments/pending-courses?page=0&size=20. Pagination is server-side, oldest first, without a partial-page search. It shows member, course/class, registration/payment IDs, VND amount, creation/deadline in Asia/Ho_Chi_Minh, payment status and last returned registration status. Loading, empty, error/retry and empty-last-page recovery are supported. It refreshes every 30 seconds and on focus, without issuing settlement requests while merely viewing.
- Xac nhan da nhan tien (confirm money received) first opens an explicit browser confirmation dialog with member, course, class, amount and payment ID. Only acceptance dispatches PATCH /api/payments/{paymentId}/counter with exactly {"success":true,"note":"Course payment received at reception"}. A synchronous action lock prevents duplicate clicks; there is no fake-success or automatic receipt action. This page does not offer a new cancellation/settlement API.
- The client checks the displayed deadline again after the dialog, disables stale/expired rows and refreshes the queue. Countdown uses the browser clock for guidance only. Backend transaction status/deadline checks remain authoritative and can still return REFUND_REQUIRED when a hold expires during settlement. Payment SUCCESS is shown only from a returned/verified Payment status; the settlement DTO does not return registrationStatus, so the page explicitly does not claim registration CONFIRMED. REFUND_REQUIRED is a separate exception requiring staff reconciliation/refund, never a success notification.
- Settlement errors (including 404/409 from concurrent staff activity) trigger an authorized GET /api/payments/member/{memberId} lookup and queue refresh. History matching checks payment ID, member ID, course-registration reference and COUNTER method. A missing queue entry never proves paid, canceled, expired or refunded. PENDING/missing history remains unresolved and may represent a canceled/expired hold or an in-flight transaction.
- The UI stops waiting for a receipt response after 15 seconds. This neither cancels nor retries the original server transaction. It checks history, refreshes the queue and blocks another receipt for the same unresolved payment. Minimal payment/member/registration IDs are saved in sessionStorage, scoped to the staff account, before settlement dispatch; storage failure prevents unsafe dispatch. On reload, unresolved entries remain blocked and expose a separate history-check action. Terminal authenticated history clears the local unresolved marker. No credentials, payment URLs or new gateway data are stored. Manual reconciliation is required if history remains pending/missing; this is not a cross-tab or cross-device lock.
- Staff can retry a failed queue GET or explicitly check unresolved history; neither action retries settlement. Existing backend payment row locking and duplicate handling remain necessary. Frontend source changes do not modify Phase 8.8A or gateway contracts.
- Verification used dependency-free Node workflow/API-helper tests, lint and build. Native browser dialog rendering, nested route navigation, session-expiry transitions, mobile/keyboard layout, real network interruption and multi-staff scenarios remain manual browser checks. No live VNPay, SQL Server or browser verification was performed.

## Phase 9.1 support tickets

Support tickets are separate from ConsultationRequest records. MEMBER creates a ticket through `POST /api/support-tickets` with `{"subject":"...","category":"GENERAL|MEMBERSHIP|COURSE|PT_BOOKING|PAYMENT|OTHER","description":"..."}`. Member identity is taken from JWT. `GET /api/support-tickets/me?page=0&size=20` lists only the authenticated member's tickets. `GET /api/support-tickets/{id}` returns the ticket and chronological `messages`; a MEMBER can read only their own ticket.

The create/detail response contains `id`, `memberId`, `memberName`, `subject`, `category`, `description`, `status`, `assignedStaffId`, `assignedStaffName`, `createdAt`, `updatedAt`, `resolvedAt`, `closedAt`, and `messages`. Each message contains `id`, `senderId`, `senderName`, `senderRole`, `message`, and `createdAt`. List rows contain the summary fields `id`, `memberId`, `memberName`, `subject`, `category`, `status`, `assignedStaffId`, `assignedStaffName`, `createdAt`, and `updatedAt`, wrapped in Spring's paged response (`content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`).

ADMIN and RECEPTIONIST use `GET /api/support-tickets?status=&category=&search=&page=0&size=20`. Search matches ticket subject or member name; results are newest-updated first with ID as a stable tie-breaker. Page size is limited to 100. Staff and owning MEMBER can add a public conversation entry with `POST /api/support-tickets/{id}/messages` and `{"message":"..."}`. The response is the updated ticket detail, including message sender name/role and creation time. Responses are DTOs and contain no account credentials or contact fields.

Staff updates status with `PATCH /api/support-tickets/{id}/status` and `{"status":"IN_PROGRESS|RESOLVED|CLOSED|OPEN"}`. Allowed staff transitions are OPEN -> IN_PROGRESS or RESOLVED; IN_PROGRESS -> RESOLVED; RESOLVED -> CLOSED or OPEN. CLOSED is terminal and read-only. A new reply to a RESOLVED ticket reopens it to OPEN in the same transaction; the UI explains this before sending. A staff member may claim an unassigned, non-closed ticket with `PATCH /api/support-tickets/{id}/assign-to-me`; an already assigned ticket cannot be taken over through this endpoint. ADMIN and RECEPTIONIST may view all tickets, reply, update status, and claim; COACH has no ticket access. Object ownership and role checks are enforced by both the security rules and service layer.

The additive schema is `phase9-1-support-tickets.sql`; apply it manually before starting this application version with `spring.jpa.hibernate.ddl-auto=validate`. Ticket actions do not change payments, memberships, registrations, or refunds. No automated notifications, attachments, or real-time chat are included.

## Phase 9.2A membership monthly pricing and purchase duration

`MembershipPlan` now exposes `monthlyPrice`; the former `price` and `durationMonths` database columns remain for compatibility and are not updated by plan edits. ADMIN creates/updates plans through the existing `POST/PUT /api/membership-plans` endpoints with the existing benefit/status fields plus `monthlyPrice` and `upgradeFromPlanIds`. For example, the plan create body adds `"monthlyPrice":500000.00` and `"upgradeFromPlanIds":[1,2]`; a plan update may omit/null `upgradeFromPlanIds` to preserve links, while a supplied list replaces them. The IDs are direct source plans permitted to upgrade into this target plan. Self-links, duplicate IDs, and cycles are rejected; no transitive relationship is implied. Active plan reads return `monthlyPrice` and `upgradeFromPlanIds`. The mutation endpoints remain ADMIN-only; public active-plan reads remain available under existing access rules.

MEMBER and existing staff-assisted purchase flows continue using `POST /api/payments`. New purchases are exactly one calendar month. Send `{"method":"ONLINE","purpose":"MEMBERSHIP","referenceId":<planId>}` (or `COUNTER`); `purchasedMonths` may be omitted for compatibility and defaults to `1`. If supplied, it must equal `1`; explicit `3`, `6`, or `12` requests are rejected. The server calculates the amount from the current server-side monthly price and stores `purchasedMonths=1` and the monthly-price snapshot on the payment. The client does not submit an amount or member ID. Existing pending payments keep their stored amount and duration snapshots and remain settleable under those original terms; a pending membership payment for the same member and plan prevents creation of another request until it is resolved. Payment responses expose `purchasedMonths`. Only verified successful settlement creates a membership; the resulting membership response exposes `purchasedMonths`, `paidAmount`, and `paymentId`. ONLINE redirects remain display hints; authenticated payment history is authoritative. COUNTER still requires explicit staff confirmation.

Renewal remains manual and starts after the latest relevant active/scheduled membership end date. New memberships receive one full month. Old Membership rows and successful Payment amounts are not rewritten. Memberships created from pre-9.2A pending payments retain null `purchasedMonths` because their original term cannot be reconstructed from the old payment contract; settlement uses the legacy plan duration and recorded payment amount. Future plan edits do not reprice a pending payment because its amount and pricing snapshot are captured at creation. Plan relationship changes affect only future eligibility and do not rewrite historical purchases. No immediate upgrade or prorated-credit behavior is included in Phase 9.2A.

### Phase 9.2A.1-A renewal foundation

`payment.renewal_membership_id` is a nullable association to the exact Membership period a future automatic renewal request will renew. It is null for all existing and ordinary purchase payments; no historical links are inferred or backfilled. The additive migration `phase9-2a1-membership-renewal-foundation.sql` adds the nullable FK and filtered unique index. That index allows at most one renewal payment attempt per source Membership period across every payment status, including `PENDING`, `CANCELLED`, and `REFUND_REQUIRED`. This deliberately strict invariant prevents ambiguous retries; a later renewal stage must resolve that attempt instead of creating another for the same source period. Stage A adds no scheduler, does not create renewal payments, and does not change renewal settlement behavior.

`POST /api/memberships` remains an ADMIN-only no-charge assignment. If `autoRenew` is omitted, it defaults to `false`. Each such assignment is recorded through the existing audit log with the ADMIN actor, target member, plan, dates, and the fixed reason `manual administrative assignment`; it does not create a Payment. Scheduled renewal requests, expiration handling, late-receipt reconciliation, toggle orchestration, and frontend duration-selector removal are later stages and are not implemented by Stage A.

### Phase 9.2A.1-B — Auto Renew request preparation

`PATCH /api/payments/{paymentId}/renewal-method` is MEMBER-only. The authenticated member may select `ONLINE` or `COUNTER` for their own pending, unexpired Auto Renew request. Request body: `{"method":"ONLINE"}` or `{"method":"COUNTER"}`. Ownership, source period, state and deadline are rechecked under locks. The response uses the existing `PaymentResponse`; generated requests initially have `method: null`.

The renewal method-selection request may also include `renewalAutoRenewChoice: true|false` to explicitly choose whether the next Membership period should have Auto Renew enabled. Omitted or null means no choice was made; false is a valid explicit choice. Example: `{"method":"COUNTER","renewalAutoRenewChoice":false}`. This preference does not authorize or initiate a charge. Method and preference can be changed only while the renewal Payment is pending and before any checkout attempt exists; after that they are immutable, including for COUNTER renewals.

### Phase 9.2A.1-C / Checkpoint 2.1 — renewal settlement boundary

`RenewalSettlementService.settleVerifiedOnlineCapture(VerifiedRenewalCapture)` remains the sole renewal capture application path. The RNL IPN adapter supplies evidence only after gateway signature and merchant verification; its transaction records provider transaction ID, amount, and response/status codes before applying or reconciling. `settleConfirmedCounterReceipt(paymentId, staffUserId)` remains a service contract for a future authorized staff controller. The browser return never settles an RNL renewal.

Settlement results are `APPLIED`, `ALREADY_APPLIED`, or `RECONCILIATION_REQUIRED`. The next Membership's Auto Renew value must already be explicitly stored on the renewal Payment; NULL is not inferred from the source Membership. The first backend-observed verified time is authoritative for the exclusive source `endDate` boundary. Captured funds that cannot be applied are retained for manual reconciliation; no automatic refund is issued.

The daily Asia/Ho_Chi_Minh scheduler creates at most one method-neutral PENDING MEMBERSHIP payment for the final paid ACTIVE period with Auto Renew enabled, beginning three calendar days before its exclusive end date. It snapshots the current monthly plan price and one-month duration. Selecting a method does not create another payment or grant access. Until Stage C, renewal settlement is unavailable: no renewal checkout URL is issued, and COUNTER confirmation is rejected. A verified successful callback is recorded as `REFUND_REQUIRED` for manual reconciliation rather than activating membership. Disabling Auto Renew or expiring the source period changes an unpaid linked request from PENDING/NULL-method to CANCELLED/NULL-method. The database permits NULL only for linked renewal MEMBERSHIP payments with the exact one-month snapshot and PENDING or CANCELLED status; SUCCESS, REFUND_REQUIRED, and ordinary payments require ONLINE or COUNTER. Existing and future paid periods are preserved.

### Phase 9.2A.1-C — Checkpoint 1 (persistence foundation only)

The additive migration `phase9-2a1-c-renewal-checkout-attempts.sql` adds nullable `payment.renewal_auto_renew_choice` and `dbo.payment_checkout_attempt`. The nullable choice distinguishes “not selected” from explicit `false`; it is valid only on a renewal-linked MEMBERSHIP payment. The attempt table stores each VNPay reference, amount snapshot, creation/expiry timestamps, local attempt state and minimal verified callback/reconciliation evidence. It does not store signing secrets, raw callback payloads, card data, or checkout URLs. Renewal references use a separate `RNL` namespace and each reference is unique. Multiple attempts may belong to one parent renewal payment without changing `payment.transactionCode`.

This checkpoint does not change renewal method selection, issue checkout URLs, settle renewals, or mark Stage C complete. The Stage B endpoint and fail-closed settlement behavior remain in effect until Checkpoint 2 is explicitly approved and implemented. After backup and writer freeze, run this standalone script manually in `SportCenterDB`, then verify the new nullable BIT column, trusted CHECK/FK constraints, the attempt table column metadata, identity primary key, unique transaction-reference index, and payment/creation lookup index. Start the matching application with `spring.jpa.hibernate.ddl-auto=validate`. SQL Server execution and VNPay behavior have not been verified by this checkpoint.

Before deploying, review actual legacy `MembershipPlan` rows. The migration copies `price` to `monthly_price` only for plans whose legacy `durationMonths` is exactly 1. For any other legacy duration, provide an explicitly reviewed monthly price in the migration's `#PlanMonthlyPriceOverrides` block; the migration stops rather than dividing a bundle price or guessing. Apply `phase9-2a-membership-purchase-refactor.sql` after the existing payment/membership schema and after backup/rehearsal, with application writers stopped. Do not run it from application startup. Start the updated application only after the migration succeeds and Hibernate `ddl-auto=validate` passes.
### Membership Upgrade checkout and settlement

- `POST /api/payments/{paymentId}/upgrade-checkout` — MEMBER only; the authenticated owner can issue/reuse a valid persisted VNPay UGP attempt for a pending ONLINE Upgrade draft. The backend derives amount and expiry from immutable Upgrade evidence. The browser return is display-only; signed VNPay IPN is the only online settlement trigger.
- `PATCH /api/payments/{paymentId}/counter` — existing ADMIN/RECEPTIONIST endpoint; for UPGRADE + COUNTER, a successful staff receipt is persisted separately and passed to the Upgrade settlement core. Client amount values are never accepted. Other payment purposes retain their existing counter handling.
