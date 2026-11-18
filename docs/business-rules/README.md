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
