# Sport Center business-rule register

This register separates **owner-approved policy**, **repository-recorded constraints**, **observed implementation**, and **unresolved questions**. It is an index, not a new policy source.

## Evidence standard

- **Owner-approved policy** requires an identifiable product-owner decision or approval record. Some API-guide sections label decisions as “approved” or “Policy,” but they do not identify the approving owner/date or link an approval record. This register records those claims as documented approval claims with unverified provenance; it does not independently certify owner approval.
- **Repository-recorded constraint** means an instruction or dated decision is explicitly written in project documentation. Its approval provenance may still be unknown.
- **Observed implementation** means source and/or named tests currently encode the behavior. It does not establish that the behavior is intended or approved.
- **Requires owner confirmation** means the policy is absent, ambiguous, or contradicted. Do not extend or change the behavior by inference.

The coding constraints in [`backend/AGENTS.md`](../../backend/AGENTS.md) are current repository instructions. Dated phase notes and demo instructions are historical evidence. The current implementation can change while those notes remain unchanged.

## Repository-recorded constraints

These are explicit in `backend/AGENTS.md`; no separate product-owner approval record was located:

- The application has ADMIN, RECEPTIONIST, MEMBER, and COACH actors. The file describes their broad responsibilities.
- There is no public self-registration. ADMIN and RECEPTIONIST create MEMBER accounts; the backend assigns the MEMBER role.
- A MEMBER manages their own MemberProfile; one profile at most per MEMBER. Identify the current member from the authenticated JWT subject and prevent cross-member profile modification.
- Store date of birth and calculate age rather than storing age.
- Course registration, membership purchase, and payment are separate operations.

**Status:** These remain repository instructions. Confirm with the product owner before changing or broadening them; the inspected files do not establish their approval history.

## Decisions documented as approved (provenance unverified)

`backend/docs/API_GUIDE.md` explicitly labels the CourseOffering `plannedSessions` and `sessionDuration` numeric ranges as “approved” (1–60 sessions; 30–180 minutes) and records a Phase 11.1 membership-upgrade policy (immediate upgrade, same end date, remaining-days price difference, no mid-term downgrade/refund of the difference). The guide does not identify the approver, date, or approval artifact. Preserve these as documented decisions; **Requires owner confirmation** before treating the documentation label as traceable product-owner approval or changing the terms.

## Observed implementation (not product approval)

| Area | What current source/tests show | Evidence | Policy status |
| --- | --- | --- | --- |
| Authentication and access | JWT-based authentication; `SecurityConfig` grants public access to login, public catalog reads, public images, and consultation submission, and applies role restrictions to protected API routes. Frontend route wrappers also gate page navigation. | `backend/src/main/java/group5/example/sportCenter/config/SecurityConfig.java`; `frontend/src/App.jsx`; backend auth/security tests | Access matrix is implementation evidence. Owner confirmation is required for any proposed policy change or any permission not explicitly specified. |
| Member creation/profile | Staff member creation is role-guarded; member self-profile endpoints use `/me` and the authenticated identity. | `backend/AGENTS.md`; `MemberProfileController`; `MemberProfileService`; `SecurityConfig`; `adminRegisterMember.test.mjs`; backend member/profile tests | Matches repository-recorded constraints; approval provenance is not recorded. |
| Public discovery | Public sports, course, coach, facility, image, and membership-plan reads are separate public API paths. Consultation submission is public; this is not public account registration. | `SecurityConfig`; public controllers; `frontend/src/api.js`; `Disciplines.jsx` | Whether public consultation collection/retention and anonymous fields are acceptable requires owner confirmation. |
| Course age policy | The evaluator treats a missing policy as RECOMMENDED; REQUIRED can reject out-of-range age and requires configured bounds. Frontend presentation helpers mirror labels; server validation is authoritative. | `CourseAgePolicyEvaluator.java`; `courseAgeEligibility.js`; backend course eligibility tests; `courseAgeEligibility.test.mjs` | Current behavior observed. The intended default, age calculation date/timezone, and who may set policy require owner confirmation if changed or not already approved elsewhere. |
| Course registration | Current implementation registers a MEMBER for a selected class, checks offering/date/eligibility/capacity constraints, creates a pending-payment state with a deadline, and snapshots price/discount values. Tests cover many boundary and concurrency cases. | `CourseRegistrationService.java`; registration controllers/repositories; `CourseRegistrationIntegrationTest.java`; `courseManagement.test.mjs`, `courseOfferingPolicy.test.mjs`, `courseCancellation.test.mjs`; dated Phase 2 notes in `IMPLEMENTATION_STATUS.md` | The exact window, hold duration, capacity behavior, discount combination, cancellation, expiry, and refund terms are business policy. Current implementation and phase notes do not prove owner approval; **Requires owner confirmation** before changing or relying on them as approved terms. |
| Membership lifecycle | Source contains membership purchase, validity/expiration, auto-renewal, upgrade, cancellation request, and refund flows; payment state transitions are separate services/endpoints. | `MembershipService.java`; `MembershipAutoRenewalService.java`; `MembershipUpgradeService.java`; `MembershipCancellationRequestService.java`; `PaymentService.java`; corresponding membership/payment tests | Implemented behavior is observable. Renewal timing/consent, upgrade proration, cancellation eligibility, refund timing, and operator discretion require owner confirmation. |
| Payments | Payment APIs cover membership, course, and PT contexts, counter confirmation, cancellation, refund-required queues, and online callbacks. The default mock gateway is disabled unless configured; VNPay is configured as a sandbox option. | `PaymentService.java`; `PaymentController.java`; `application.properties`; payment tests; `backend/docs/API_GUIDE.md` | Settlement, refund, callback, and retry semantics are consequential. Confirm policy and provider configuration with the owner/operator; do not infer production readiness from code. |
| PT price | `application.properties` currently defaults `APP_PT_SESSION_BASE_PRICE` to 500000. | `backend/src/main/resources/application.properties`; `PersonalTrainingBookingService.java`; PT booking tests | **Requires owner confirmation** for currency, price, tax/discount treatment, and whether this default is suitable outside local development. |
| Notifications | ADMIN broadcast sends title/message; the service selects ACTIVE recipients. Automatic business-event notifications also exist. | `AdminNotificationController.java`; `NotificationService.java`; `NotificationBusinessEventListener.java`; notification tests | Recipient definition, message rules, and event coverage require owner confirmation before they are treated as approved policy. |

## Product-owner decisions recorded 2026-11-26

The current owner instruction in the 2026-11-26 Coach/Member QA task explicitly approves these rules:

- **Renewal cancellation:** retain the strict 72-hour ordinary upcoming-Membership cancellation policy; do not add a Renewal-specific exception. Disabling Renewal before payment closes an applicable open Intent under current lifecycle rules. A successfully paid successor remains subject to ordinary cancellation eligibility.
- **Attendance and TrainingResult:** reject changing attendance away from PRESENT when a TrainingResult exists for that session and Member. Do not delete, invalidate, or rewrite the result. Enforce the invariant inside the shared ClassSession lock used by attendance and training writes.

These decisions supersede earlier unresolved questions only for these exact scopes. They do not approve changes to other Renewal, cancellation, settlement, or attendance policies.

The owner further approved on 2026-11-27 that:

- **Official attendance submission:** the server determines the eligible roster; the first submission must include one explicit PRESENT or ABSENT outcome for every eligible learner and commits atomically. Individual writes cannot create initial attendance. A partially recorded historical roster can be completed without rewriting unchanged rows. EXCUSED remains readable historical evidence and counts as recorded non-participation, but cannot be newly selected or used in a correction.
- **Attendance completeness and Member statistics:** derive NOT_RECORDED, PARTIAL, COMPLETE, and NOT_APPLICABLE from eligible learners and persisted attendance, independently of session lifecycle. A Member rate is PRESENT / recorded eligible outcomes; historical EXCUSED is in the denominator. Unrecorded elapsed sessions are disclosed and make the rate provisional; zero recorded outcomes has no percentage.
- **Attendance notifications:** after commit, notify only the affected Member for a newly recorded ABSENT or a genuine status correction. Unchanged retries, unsaved selections, failed transactions, and historical rows produce no notification.
- **Support participation:** an unassigned request must be claimed before Admin or Receptionist reply. A Receptionist may reply only when assigned; Admin may participate after assignment without taking ownership. Members may reply only to their own request within existing lifecycle rules.

The owner approved the following General and Individual Training Plan rules on 2026-11-27 in the Training Plan Redesign task:

- **Week numbering:** General plan week numbers are positive and unique within the class plan, may be nonconsecutive, and display in ascending order. The number is a curriculum ordinal, not an exercise duration. New weeks suggest the number after the current maximum; deletion does not renumber. Individual Training Plan keeps its existing 1–52 validation and revision behavior.
- **General curriculum dates:** Week 1 begins on the CourseOffering `startDate`; ordinal weeks advance in consecutive seven-day blocks and the final displayed in-range date is clamped to `endDate`. These dates are derived, never stored. ClassSession cancellation/rescheduling does not renumber content. Reanchoring or shortening an Offering preserves all week content; retained out-of-range weeks remain visible with a nonblocking warning.
- **Visibility and access:** one General plan remains associated with a class; Individual plans remain per eligible Member/class. Assigned Coaches may edit; Admin supervision is read-only. Eligible Members can see all saved General weeks, including future weeks. There is no persisted draft/publish state: unsaved editor changes remain client-local and saved changes are immediately visible. General plan revision/rollback tables are not introduced; existing audit writes do not constitute reconstructable version history.
- **Weekly General Plan persistence:** the approved additive model adds `training_plan_week` with a positive week number and unique `(plan_id, week_number)`, and nullable `training_exercise.week_id` while preserving `plan_id`. Ownership-safe constraints prevent cross-plan assignment. Existing exercises remain unassigned (`week_id IS NULL`) until an authorized Coach explicitly moves them; the move preserves exercise identity/content. Omitted content in the whole-plan Coach save is an explicit deletion, and the save is atomic. Offering edits never delete plan content.
- **Migration boundary:** the owner authorized the additive migration for QA clone DB12 (`LAPTOP-416VBDLK / SportCenterDemo_PTOutcome_8CF1 / DBID 12`) only in this task. No DB5 deployment was authorized. SQL preflight, backup, migration, verification, rollback guidance, and later DB5 procedure are recorded in `backend/docs/sql/proposals/` and the acceptance report.

## Product-owner decisions recorded 2026-11-27

The current owner instruction in the 2026-11-27 Final Owner QA task explicitly approves:

- **Elapsed class sessions:** a `SCHEDULED` class session becomes `COMPLETED` when its persisted end time is reached, using Vietnam business time. A startup catch-up and five-minute recurring job may perform this status transition; repeated execution is idempotent and audited as `SYSTEM`. This does not create attendance, results, assessments, or financial effects. The existing manual completion API remains available.
- **Support claim-and-start:** an authorized staff member claiming an unassigned `OPEN` support ticket is assigned and moves it to `IN_PROGRESS` in one transaction. An unassigned legacy `IN_PROGRESS` ticket remains claimable without a status change. A competing claim must not overwrite the committed assignee. Existing Admin oversight and other ticket lifecycle behavior remain in scope only as previously authorized.

## Product-owner decisions recorded 2026-11-28

- **Domestic phone format:** when a phone value is supplied, trim surrounding whitespace and require exactly ten ASCII digits beginning with `0` (`^0[0-9]{9}$`). Preserve each field's existing required/optional behavior; optional null and whitespace-only values remain optional. Duplicate phone values are allowed. Do not infer carrier, ownership, OTP, or international-number rules. For the 2026-11-28 DB5 demo-data remediation, the owner approved replacing the listed invalid values with the shared demo-only placeholder `0987654321`; this is not a general repair rule for other databases or records.

## Unresolved questions requiring owner confirmation

Every item below is unresolved; do not decide from current code, labels, seeded data, or old phase documentation:

1. Are the role responsibilities and detailed endpoint permissions in `SecurityConfig` the approved access matrix? In particular, which exact staff operations are allowed to RECEPTIONIST versus ADMIN?
2. Are the course registration opening window, deadline boundary, 24-hour-or-earlier payment hold, seat reservation, age/skill eligibility, membership discount selection, and stored price snapshot rules approved? What cancellation, expiration, and refund outcomes should members receive?
3. What are the membership terms: exclusive end-date interpretation, auto-renewal consent and execution timing, retries/failure handling, upgrade proration, cancellation cutoff, and refund eligibility/timing?
4. What are the authoritative PT prices and currency, and who may change them?
5. What exact consultation information may guests submit, how long is it retained, and what contact/marketing consent is required?
6. Which lifecycle events should notify which roles/users, and does ADMIN broadcast intentionally target every ACTIVE account?
7. What are the approved SkillAssessment rubric, level thresholds, reassessment/cancellation semantics, and coach-to-member assignment rules?
8. Which SQL Server schema artifact is approved for provisioning a new database: the `submission/SQL/SportCenter_FullSchema.sql` snapshot or a separately maintained core schema? Who owns confirmation that each manual migration has run in a given environment?
9. Are demo contact details, test accounts, and demo workflows still accurate for the current branch and target environment?

Record answers with owner, date, scope, and source. Update this register only when that evidence exists; then update implementation/tests separately if required.

## Documentation conflicts and historical notes

- `frontend/README.md` contains a Phase 6.2 note saying the frontend has no configured test runner. That is stale: `frontend/package.json` has a Node test script and a separate Playwright `test:e2e` script/configuration. The current scripts are authoritative for test commands.
- `backend/docs/sql/README.md` says the repository does not provide the original core schema / cannot provision a truly empty database. `submission/SQL/README_SQL_Setup.md` describes a 37-table fresh-schema snapshot. These may have different provenance or intended targets; neither document establishes which is currently approved. **Requires owner confirmation.**
- `backend/docs/IMPLEMENTATION_STATUS.md`, `DEMO_GUIDE.md`, and phase/test reports contain dated branch, database, migration, verification, and workflow snapshots. Do not treat them as current branch state or proof of current live database contents.
- `backend/docs/API_GUIDE.md` is internally inconsistent about auto-renew: its earlier Membership overview calls `autoRenew` an intent flag only, while its later Phase 11.2 section documents a daily renewal-payment workflow. Older Phase 2 status text also says intent-only. Current source includes `MembershipAutoRenewalService` and scheduled renewal/payment behavior. Use source to describe current code; **Requires owner confirmation** to reconcile intended terms and correct the historical contract text.
- `frontend/docs/SPORTCENTER_DESIGN_SYSTEM.md` labels its shared-token section and adoption path as a proposal/reference, not an active portal-wide contract. Do not infer a UI policy from it.
