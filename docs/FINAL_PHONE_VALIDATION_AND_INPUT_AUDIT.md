# Final Phone Validation and Input Audit

Date: 2026-11-28
Branch/HEAD at inspection: `qa/team-integration-20261122` / `c6121835b73fb2c598a8afd330c8c0246ec3540b`
Verdict: **PASS_WITH_GAPS** (DB remediation applied; focused backend tests remain blocked by the shared Maven lock)

## Phone-number defect

The confirmed defect was inconsistent validation: consultation DTOs enforced only `@NotBlank` and a 20-character maximum, while account DTOs enforced at most length. The authenticated Member Consultation service stores the authenticated account's name, phone and email rather than trusting the submitted identity fields; its service path previously did not validate every DTO violation. Account, Coach, Receptionist, Member and staff service writes accepted arbitrary phone strings.

The implementation now applies `^0[0-9]{9}$` to nonblank values, trims outer whitespace, preserves phone values as strings, and maps blank optional values to `null`. It does not strip invalid characters, reject duplicate phone values, or change which fields are required. Backend validation is on DTOs and repeated at service persistence boundaries. Authenticated Consultation continues to use the stored account identity; invalid client phone payloads are rejected by DTO validation and an invalid stored account phone is rejected before creating a consultation or Member account.

Frontend checks now cover Guest Consultation, Member Consultation, Member account create/update, and Admin staff create/update. They display `Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.` and leave the entered string unchanged. Telephone fields use `type="tel"` where applicable.

## Phone data and database status

SQL Server identity verified before backup and mutation: server `LAPTOP-416VBDLK`, database `SportCenterDemo`, DBID `5`, state `ONLINE`. No backend process was running; the configured repository default points at this endpoint/database. Owner `.env` credentials were held only in process memory and were not printed.

| Table.column | SQL type | Nullable | Rows | Valid | Invalid nonblank | Blank | NULL | Existing phone check |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| `dbo.users.phone` | `varchar(20)` | Yes | 19 | 18 | 0 | 0 | 1 | `ck_users_phone_format`: strict 10 ASCII digits beginning 0; enabled and trusted |
| `dbo.consultation_request.phone` | `nvarchar(20)` | No | 21 | 21 | 0 | 0 | 0 | `ck_consultation_request_phone`: strict 10 ASCII digits beginning 0; enabled and trusted |

Duplicate values remain permitted. Current duplicate groups: **1** in `users` and **3** in `consultation_request`; the shared placeholder is intentionally repeated for demonstration records. No unique phone constraint was added.

Pre-remediation values, with identifiers and masked values only: `users` IDs `9, 1009, 1010, 1011, 1013, 1014, 1015, 1016, 1017` had 10 ASCII digits but an invalid leading digit; each masked value was `12*******0`. Consultation IDs `1002, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2010, 2011, 2015, 2016, 2017` also had 10 ASCII digits with an invalid leading digit; each masked value was `12*******0`. Consultation ID `2019` had 12 characters (24 bytes) and non-ASCII-digit content; masked value `12*********0`. All 23 were rechecked and replaced as approved.

The owner explicitly approved replacing the 23 listed invalid demonstration values with `0987654321`, recorded as a shared demo-only placeholder. Immediately before the transaction, each approved row was rechecked; only still-invalid values in those IDs were changed. No other columns, rows, required/nullable semantics, or already-valid numbers were altered. The pre-change database backup was created at `D:\5School\SportCenterBackups\SportCenterDemo_PrePhoneValidation_20261128_091047.bak` (23,164,416 bytes), passed `RESTORE VERIFYONLY WITH CHECKSUM`, SHA-256 `4A3F22F8D8692E169EEF11E68ED32AD8FC9F751DF1E7E6B960030CE394590554`.

The guarded SQL change is in [phone-number-validation.sql](../backend/docs/sql/phone-number-validation.sql); its read-only inventory is [phone-number-validation-preflight.sql](../backend/docs/sql/phone-number-validation-preflight.sql). The deployed transaction used the same DB5/server/schema guards, remapped only approved invalid IDs, and added enabled/trusted checks using `DATALENGTH` plus `Latin1_General_100_BIN2` ASCII digit matching. It preserves `users.phone` nullable and `consultation_request.phone` required, adds no uniqueness rule, and committed atomically in SQL Server.

Post-change identity/count verification found DB5 ONLINE with DBID 5, 19 users, 21 consultation requests, zero invalid phones, 9 user placeholders, 14 consultation placeholders, one preserved nullable user phone, and both checks enabled/trusted. SQL Server negative probes for both tables failed with check-constraint error 547; duplicate-placeholder update probes succeeded inside rolled-back transactions. No schema migration beyond the two approved CHECK constraints occurred. The full-schema snapshot contains matching fresh-schema checks.

## Focused source validation audit

Classification reflects the inspected DTOs, controller `@Valid` use, services, entity mappings, and checked-in full-schema snapshot. `MISSING_DATABASE_PROTECTION` means a rule is enforced by the ordinary API path but no matching SQL CHECK constraint was found in that snapshot; it does not claim that a live database has no other constraint. Only the phone and clearly low-risk API defects described below were changed.

| Category | Classification | Source findings and disposition |
|---|---|---|
| Account and Consultation: email, uniqueness, names, whitespace, DOB, lengths | **ALREADY_VALIDATED**; one gap fixed | Email format and bounds are present on DTOs and controllers use `@Valid`; `users.email` is unique. Consultation email collision handling is explicit. Names use `@NotBlank`; Consultation trims name/email. DOB is past-validated where required, and Guest Consultation requires a date. `CreateMemberRequest` lacked name/email maximum lengths and `CreateUserRequest` / `UpdateUserRequest` did not reject a supplied future DOB; added the existing 100/150 database-matching lengths and nullable `@Past` checks. No DB-only whitespace check was added. |
| Account and Consultation phone | **ALREADY_VALIDATED** at UI/API; database protection implemented | Format is 10 ASCII digits beginning with 0 when supplied. Optional account phone remains nullable; Consultation phone remains required. DB5 now has enabled/trusted CHECK constraints and no uniqueness rule. |
| Member Profile height and weight | **ALREADY_VALIDATED**; **MISSING_DATABASE_PROTECTION** for documented numeric bounds | Create/update DTOs already enforce height 50–250 cm and weight 10–500 kg, and all profile writes use `@Valid`. Entity precision is 5,2, but the fresh-schema snapshot has no matching range CHECK. No new limit or schema change was introduced. |
| Membership Plan price, duration and discounts | **ALREADY_VALIDATED**; **MISSING_DATABASE_PROTECTION** in the checked-in snapshot | DTOs and controller enforce nonnegative price (precision/scale), positive duration, and 0–100 discount percentages. No corresponding snapshot CHECK constraints were found. No business boundaries were changed. |
| Course, Offering and Class price, capacity, duration, age and dates | **ALREADY_VALIDATED**; **MISSING_DATABASE_PROTECTION** for several API invariants | DTOs/controller validate required names, nonnegative Offering price, planned sessions and duration ranges, capacity ≥1 and nonnegative course age bounds; Offering services enforce integer/range checks, date ordering, deadline ordering, and chronological consistency. Added `@Digits(10,2)` to Offering create/update price to match the `decimal(12,2)` entity and prevent a normal invalid value from reaching SQL precision errors. Course age ordering is service-validated. No unsupported maximum age was invented. The snapshot lacks corresponding checks for several API-only rules. |
| General and Individual Training Plans | **ALREADY_VALIDATED**; some DB checks present | Nested week/exercise DTOs are cascaded with `@Valid`; week numbers, names, optional positive sets/reps/minutes and text lengths are checked. General week uniqueness and plan ownership are service-validated; the SQL snapshot contains exercise value checks and the Individual week 1–52 constraint. General week count remains uncapped. |
| Coach experience, skill assessments, training results and progress evaluation | **ALREADY_VALIDATED**; **MISSING_DATABASE_PROTECTION** for some ranges | DTO/controller and service checks enforce nonnegative experience years, assessment levels, TrainingResult score 1–10, progress rating 1–5, and bounded notes. No blanket DB constraints for each of these ranges were found in the snapshot. |
| Support, notifications, descriptions and notes | **ALREADY_VALIDATED** for inspected API writes | DTOs use `@NotBlank` and/or limits aligned with bounded entity columns; freeform fields mapped as large text are not arbitrarily capped. Support messages, notification title/body, audit reasons and Training notes have explicit limits at their write APIs. |
| Image upload | **ALREADY_VALIDATED** | Backend checks configured byte limit, JPEG/PNG/WebP signatures and structure, decodability, dimensions/pixel ceiling, and generated safe filenames. Browser `accept` or extension alone is not trusted. |

No unresolved product limit was inferred. Historical remediation used the owner's explicit approved mapping. A source-level PT schedule review found conflicts were checked for Coach class/PT and Member PT/PT, but not Member class/PT. PT booking eligibility and creation now reject overlap with the Member's SCHEDULED sessions in a CONFIRMED registration or nonexpired PENDING_PAYMENT hold. New class registration now rejects overlap with the Member's active PT booking. CANCELLED sessions/registrations and expired holds do not block; strict overlap permits adjacent sessions. Both service paths already lock the same Member row, serializing these checks.

## Changed files

- Backend phone constraint/validator: `validation/VietnamesePhone.java`, `VietnamesePhoneValidator.java`, and `VietnamesePhoneNumbers.java`.
- PT conflict correction: `CoachScheduleConflictService.java`, `ClassSessionRepository.java`, `PersonalTrainingBookingService.java`, `CourseRegistrationService.java`, and the PT/course-registration integration tests.
- Phone DTO and persistence paths: Consultation, user, Member, Coach, Receptionist/staff DTOs; `ConsultationRequestService`, `MemberRegistrationService`, `UserService`, `StaffManagementService`, and `CoachService`.
- Confirmed related invalid-input defects: Member account name/email bounds, optional account DOB past validation, and Offering price precision on create/update.
- Focused backend regression additions: `ConsultationRequestIntegrationTest.java` (direct invalid Consultation and Member API cases; execution blocked by Maven cache lock).
- Frontend shared validator and form wiring: `frontend/src/phoneValidation.js`, Guest/Member Consultation forms, Member create/update, and staff create/update; `frontend/tests/phoneValidation.test.mjs`.
- Database artifacts: read-only `phone-number-validation-preflight.sql`, guarded remediation/constraint script `phone-number-validation.sql`, and the intended constraints in `submission/SQL/SportCenter_FullSchema.sql`.
- Documentation: `docs/business-rules/README.md` and this report.

## Tests and runtime checks

- Frontend `npm.cmd test`: **610 passed, 0 failed** (includes parameterized ASCII/Unicode/punctuation phone cases and source-contract checks on the relevant forms).
- Frontend `npm.cmd run lint`: **passed**.
- Frontend `npm.cmd run build`: **passed**; existing large-chunk warning remains (1,036.67 kB minified JS bundle).
- `git diff --check`: **passed**; only Git line-ending normalization warnings were emitted.
- Backend focused tests: **blocked before compilation**. Direct Maven first targeted unavailable `C:\.m2\repository`; with `MAVEN_OPTS=-Dmaven.repo.local=C:\Users\Asus\.m2\repository`, offline Maven failed opening the existing shared `spring-boot-starter-parent:4.1.1` lock after five attempts. No cache/lock was removed or modified. Added tests cover direct Consultation API rejection and both directions of Member class/PT overlap; they remain unexecuted.
- Authenticated browser smoke test: **not run** because no local frontend/backend listener was available during the check. No service was started and no authentication bypass was attempted.

## Remaining work and handoff

1. Rerun `ConsultationRequestIntegrationTest`, `PersonalTrainingBookingIntegrationTest`, and `CourseRegistrationIntegrationTest` when Maven can access its existing cache; current execution is blocked by that lock.
2. Run Hibernate schema validation for the application's actual changed entity model when Maven becomes available; the DB5 phone constraints do not alter entity schema shape.
3. Browser smoke tests were not run because no frontend or backend listener was available; no services were started.
4. Weekly Training Plan schema/source synchronization remains incomplete across SRD, ERD, and `submission/SQL/SportCenter_FullSchema.sql`; do not claim those artifacts are fully synchronized.

The implementation was committed as `07c8ae0be0bb4a954f9a42b73906bf22ed5ca250` and pushed to `origin/qa/team-integration-20261122`; a later documentation-only correction was also committed and pushed to that branch. The final branch SHA was verified after the documentation update. No tracked changes remain. The only DB5 mutation was the explicitly approved 23 phone replacements and two CHECK constraints; no other business data changed. A fresh post-change backup at `D:\5School\SportCenterBackups\SportCenterDemo_PostPhoneQA_20261128_092403.bak` is 23,164,416 bytes, passed `RESTORE VERIFYONLY WITH CHECKSUM`, and has SHA-256 `8EBDF978C5414E0E7DB3479C030DF4A45D591176968EB3BDDD311FD296D10BB4`. Local browser artifacts under `.playwright-mcp/` and `docs/evidence/` were preserved and excluded from Git.

## Superseding final backend smoke check — 2026-11-28

This section supersedes the earlier Maven-lock, backend-startup, and browser-smoke limitations above for the checks listed here. It does not claim completion of the unrelated SRD/ERD/schema-snapshot synchronization.

- Maven's shared cache was left untouched. The three requested test classes ran using a task-local Maven repository and a file mirror of the existing cache: `ConsultationRequestIntegrationTest` **44/44**, `PersonalTrainingBookingIntegrationTest` **29/29**, and `CourseRegistrationIntegrationTest` **38/38**; total **111 passed, 0 failed/errors/skipped**. The tests use their configured H2 profile.
- Latest backend source started on task-owned port `18081` with `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` and all scheduler switches disabled. Startup completed successfully with the effective DB5 target `LAPTOP-416VBDLK / SportCenterDemo / DBID 5`; Hibernate validation succeeded. No migration or schema write was run. The task-owned process was stopped after the smoke checks; unrelated services were not terminated.
- Authenticated Member API smoke succeeded. A consultation request with an invalid phone returned **HTTP 400**; the rejected request did not create a Consultation. Existing Member class-session and PT read endpoints also returned successfully. No PT booking or registration was submitted to DB5 solely to force a conflict.
- Both conflict directions are exercised by the focused integration tests: `PersonalTrainingBookingIntegrationTest.existingRegisteredClassSessionBlocksMemberPtBookingButCancelledRegistrationsDoNot` and `CourseRegistrationIntegrationTest.preventsRegistrationWhenScheduledClassOverlapsMembersActivePtBooking`. Their assertions passed. The runtime PT write rejection was not separately induced against historical DB5 data, to avoid risking an unintended business write.
- Admin class-session create/reschedule source calls `rejectScheduleMutationForActiveRegistrations`, which checks occupied `CONFIRMED` and unexpired `PENDING_PAYMENT` seats and rejects schedule changes while they exist. Class slot validation also checks Coach schedule conflicts, including PT bookings. Thus the current implemented guard prevents Admin schedule changes from creating a class/PT overlap for an actively registered Member; there is no newly identified cancellation/refund policy gap.
- `git diff --check` passed. A one-file test-fixture correction remains uncommitted: it uses an alternate qualified Coach after the original Coach is correctly blocked by the class overlap. No production source correction was required, so no commit or push was made. Preserved untracked `.playwright-mcp/` and `docs/evidence/` artifacts remain outside this report's scope.
