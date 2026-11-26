# Sport Center — Post-Defense Business Audit

**Audit date:** 2026-11-19

**Scope:** repository source/tests plus read-only inspection of the live SQL Server configured by the backend. No source, schema, or data was changed; no migration or test suite was run.

## Evidence and live database scope

The backend connection configured for this audit resolved to SQL Server `LAPTOP-416VBDLK`, default instance (`SERVERPROPERTY('InstanceName')` is `NULL`), database `SportCenterDemo`, schema `dbo`, endpoint `127.0.0.1:1433`. Credentials were read from the ignored backend environment file and were not printed or recorded. Claims about live data below apply only to that exact database at audit time; they do not establish production state or approved business policy.

Live aggregate snapshot:

- 15 `dbo.consultation_request` rows: 6 have `age`, 9 have no age; 14 are linked to a Member and 1 is unlinked; all 15 have `handled_by`.
- 10 `dbo.users` rows with role `MEMBER`; all 10 currently have `date_of_birth`.
- 5 `dbo.membership_plan` rows; none has `duration_months <> 1`.
- 27 `dbo.membership` rows; 9 have `end_date <> DATEADD(month, 1, start_date)`: 5 `ACTIVE`, 2 `CANCELLED`, 2 `UPGRADED`. No mismatch is reported as `EXPIRED`.
- 3 future-start `ACTIVE` Memberships are queue periods. All three currently span one calendar month; their linked payments were made before their `start_date`.
- 30 Course Registrations; all satisfy the stored price arithmetic invariant `final_price = original_price - membership_discount`.
- The live schema has `dbo.membership_plan` benefit fields and `dbo.membership.plan_id`, but no benefit-snapshot table/columns. `dbo.course_registration` stores original, discount amount, and final prices. `dbo.consultation_request` has nullable integer `age`; `dbo.users.date_of_birth` is nullable `date`.

## Audit findings

### 1. P0 — Consultation account linking

**Status: CONFIRMED** (source, tests, live schema/data). The security boundary restricts who can link, but the link itself does not verify that the account belongs to the consultation sender.

- **Relevant implementation:** `frontend/src/public-site/components/ConsultForm.jsx`; `frontend/src/public-site/pages/CourseDetail.jsx`; `frontend/src/pages/MemberLevelsAssessments.jsx`; `frontend/src/pages/ConsultationQueue.jsx`; backend `ConsultationRequestController`, `ConsultationRequestService`, `CreateConsultationRequest`, `LinkConsultationMemberRequest`, `ConsultationRequest`, `SecurityConfig`.
- **Implemented flow:** public `POST /api/consultation-requests` is permitted without login. If a JWT is present, service code resolves the actor from JWT and requires role MEMBER, then stores that actor as the request's `member`; client `memberId` is not part of `CreateConsultationRequest`. Guest requests are unlinked. Receptionist/ADMIN must first have the request in progress; a Receptionist must be its assigned handler (ADMIN is allowed regardless of assignment). `PATCH /{id}/link-member` accepts a client-supplied `memberId` and checks only that the target is an ACTIVE MEMBER and the request is not already linked. It does not compare sender identity, verified contact, or consent. The endpoint is authenticated; controller/service checks enforce the role/assignment boundary.
- **Test evidence:** `ConsultationRequestIntegrationTest` verifies a Member submission remains linked to the JWT Member even when a different `memberId` is included. It also explicitly expects a Receptionist to link an unrelated active Member to a guest request. Tests verify assignment and active-role restrictions, not ownership verification.
- **Root cause and impact:** the API treats selecting a Member record as sufficient proof of identity. A staff member assigned to a request can associate it with any active Member ID. That can disclose/link consultation history to the wrong account and corrupt account history. In the live database, 1 of 15 requests is currently unlinked; the count alone does not indicate whether its identity was verified.
- **Recommendation:** remove/disable unverified manual linking. Keep the authenticated Member path JWT-derived. For Guests, offer account creation from consultation details as the current flow does, but require a securely chosen/confirmed DOB and credentials; never match/link automatically by email or phone. If linking to an existing account remains a product need, require an explicit verified proof/consent flow (owner must define the acceptable proof) and audit the actor and verification result. Do not accept caller-supplied Member identity from the Member consultation endpoint.
- **Migration:** none is inherently needed to remove the insecure linking operation or change API validation. Existing `member_id` is nullable and already has the link relation. A data review may be needed to determine whether historic links were verified; do not bulk unlink without owner-directed case review.
- **Regression tests:** assert guest creation is unlinked; authenticated Member creation derives identity from JWT regardless of an extra submitted ID; existing link endpoint rejects arbitrary accounts; verified linking succeeds only through the approved proof flow; assigned-handler/Admin authorization and audit trail remain correct.

### 2. P0 — Membership benefit snapshot

**Status: CONFIRMED** (source and live schema). Plan edits can change benefits used by existing active and queued Memberships.

- **Relevant implementation:** backend `MembershipPlanService`, `MembershipPlan`/`Membership` entities, `CourseRegistrationService`, `PersonalTrainingBookingService`, `MembershipRepository`, `IndividualTrainingPlanService`, `MembershipUpgradeService`; tables `dbo.membership_plan`, `dbo.membership`, `dbo.membership_upgrade`, `dbo.payment`, `dbo.course_registration`, `dbo.personal_training_booking`.
- **Implemented rule:** Admin update mutates the same MembershipPlan row, including discounts and entitlement booleans. A Membership stores only a foreign key to that Plan, so services read current Plan values through `membership.getPlan()`:
  - Course discount is read when a course registration is created; active Memberships effective on the registration date are considered and the highest valid percent is used.
  - PT discount and `allowOneOnOneCoaching` are read when booking PT.
  - `allowPersonalTrainingPlan` is checked when accessing individual training plans.
  - Upgrade eligibility/options compare current and target Plan benefits.
  - Future-start Memberships (the queue) are still linked to the mutable Plan and use its values when they become effective.
- **Root cause and impact:** the model stores a mutable Plan reference, not benefits/version as sold. A Plan edit can silently change future course/PT discounts, PT booking eligibility, and training-plan entitlement for existing ACTIVE or queued memberships. Already-created Course Registrations and PT bookings retain their stored price breakdowns; the current evidence does not show those historic amounts being recalculated. Upgrade comparisons and plan labels may still reflect the changed Plan.
- **Live data:** the 27 Memberships use the current Plan relation; 3 are future-start queue periods. The live schema contains no per-purchase benefit snapshot. Current Plan rows all have one-month duration, but that does not prove their benefit values have never been edited.
- **Recommendation:** version Plan terms immutably and bind each successful purchase/renewal/upgrade to the exact version quoted. Alternatively store a complete benefit snapshot per Membership (including discount percentages and entitlement booleans) and ensure all benefit/eligibility reads use that snapshot. Freeze the quoted terms across pending payment settlement; decide explicitly whether upgrades replace the active benefit snapshot and how renewal versions are selected. Preserve existing paid entitlements; do not populate historical snapshots from today's mutable Plan values unless verified from audit/payment history or explicitly approved as a fallback policy.
- **Migration:** yes, for a snapshot/version model. Add version/snapshot schema, enforce its relationship to payment and Membership, and backfill only from authoritative historical evidence. Since the schema has no existing snapshot and prior Plan versions may not exist, exact historical values may be unrecoverable; legacy handling requires owner approval. No migration was run.
- **Regression tests:** changing a Plan must not change benefits for existing active or queued Memberships; new purchase/renewal captures the quoted version; upgrade preserves the intended remaining term and snapshots target benefits; existing course/PT booking prices remain immutable; entitlement checks read frozen values.

### 3. P1 — Consultation date of birth

**Status: CONFIRMED** for age-only consultation input/storage and separate DOB entry during account creation; **NOT VERIFIED** for any claimed incorrect age value in a particular legacy row.

- **Relevant implementation:** public `ConsultForm.jsx` and Member consultation form in `CourseDetail.jsx`; backend `CreateConsultationRequest`, `ConsultationRequest.age`, `ConsultationRequestService.create/createMember`, `CreateConsultationMemberRequest`, `CreateMemberRequest`; live `dbo.consultation_request.age` and `dbo.users.date_of_birth`.
- **Implemented rule:** public consultation sends optional integer `age` constrained to 1–120 when supplied. The request table stores nullable age, not DOB. To create a Member from an in-progress consultation, Receptionist/Admin must separately enter a required past DOB; the service copies consultation name/email/phone but uses the separately entered DOB. Receptionist queue response/UI expose age (not DOB) for consultation details. Member accounts already store a nullable SQL `date` DOB and current Member signup requires a DOB.
- **Root cause and impact:** age is an imprecise, time-dependent value and cannot later establish a birth date. The current create-account step can enter a DOB inconsistent with the original consultation. Existing consultation rows cannot be losslessly converted from age to DOB.
- **Live data:** 6 of 15 consultations have age and 9 do not. All 10 current Member accounts have DOB. No DOB is currently recorded on Consultation rows.
- **Recommendation:** replace the public age field with required `dateOfBirth`; validate it as past and calculate age for display using the current date and an agreed timezone. Receptionist should see submitted DOB and derived age; account creation should prefill the submitted DOB and require confirmation/edit before final creation. If correction is allowed, preserve the originally submitted value/audit rather than silently overwriting it.
- **Migration:** yes, add nullable `date_of_birth` to consultation requests for legacy compatibility. Require DOB in new API requests/forms. Do not infer an exact date from existing integer ages; preserve legacy age data and leave DOB null unless verified by the person. Existing users need no migration for this feature.
- **Regression tests:** required/past DOB API validation; derived age across birthdays and leap-day cases; timezone/date boundary; create-member form prefills consultation DOB; staff correction confirmation; old rows with age but no DOB remain readable.

### 4. P1 — Member details Unicode and modal readability

**Status: CONFIRMED** source defect for one literal Unicode escape in Member detail UI; **NOT VERIFIED** in a rendered browser during this audit. Source already implements the requested responsive column structure.

- **Relevant implementation:** shared `frontend/src/pages/MemberList.jsx` / `MemberList.css`, used for Admin and Receptionist member management. Backend account/profile data comes from `UserController`/`MemberController` and `MemberProfileController`/`MemberProfileService`.
- **Unicode root cause:** the missing-profile empty text at `MemberList.jsx` is written as a bare JSX text node containing `Ch\u...`; backslash-u sequences are decoded only inside JavaScript string literals/expressions, not as JSX text. That node therefore renders the escape spelling literally. Other occurrences wrapped in `{ "..." }` expressions or HTML entities are decoded correctly. The defect can appear in the same detail modal for either role when the selected Member lacks a profile.
- **Modal behavior in source:** both roles share the modal. Admin sees account/profile editing controls; Receptionist receives a read-only detail view. Existing `.member-edit-grid` uses two columns and changes to one column at widths at or below 900px. Modal CSS uses compact 10–12px detail labels/value text, so readability merits visual review, but no overlap or specific runtime layout defect is established from source alone.
- **Impact:** the literal escape is a user-visible text defect for members without a physical profile. Small modal typography could reduce readability, but severity is not established without rendered inspection. There is no account or profile data migration need.
- **Recommendation:** replace only the bare escaped text with normal Vietnamese JSX text or a quoted JavaScript expression. Review Admin and Receptionist modal at desktop/mobile widths and increase detail typography only if browser inspection confirms readability is inadequate.
- **Migration:** none.
- **Regression tests:** component/browser assertion that the empty state contains decoded Vietnamese and no `\\u` sequence; open the same profile modal under Admin and Receptionist; assert read-only role boundary; inspect 2-column desktop / 1-column mobile controls and modal bounds.

### 5. P1 — Course discount and payment confirmation

**Status: CONFIRMED** (source, tests, live schema/data). Backend computes the authoritative price, but the member confirmation step does not show the complete breakdown before creating the registration.

- **Relevant implementation:** frontend `ReceptionistCourseRegistration.jsx`, `receptionistRegistration.js`, `CourseDetail.jsx`, `CourseCheckout.jsx`; backend `CourseRegistrationService`, `CourseRegistration`/`CourseRegistrationResponse`, `CourseRegistrationPaymentHandler`, `PaymentService`; tables `dbo.course_registration` and `dbo.payment`.
- **Implemented price flow:** at registration creation, backend selects the maximum valid course discount among effective ACTIVE Memberships, calculates original price, discount amount, and final price, then stores those three values on Course Registration. This is a snapshot for that registration. `PaymentService` ignores client-supplied price and asks `CourseRegistrationPaymentHandler` for the stored registration `finalPrice`; payment amount is server-derived.
- **Where the UI displays price:** Receptionist registration shows a client-side estimated discount percentage and estimated final price before creating a hold, explicitly labels it an estimate, then displays server-returned original/discount/final amounts in the success notice and pending list. Member Course Detail's pre-registration confirmation shows the offering's reference tuition, not the member discount/final amount; after registration creation it shows the server-returned breakdown. Member Checkout shows only `finalPrice`; payment history displays payment amount. The response/database store discount amount but not the applied discount percentage.
- **Root cause and impact:** no explicit server-quoted breakdown is presented to the Member before registration creates a `PENDING_PAYMENT` hold. The visible price before that point can be only a reference value, while the complete authoritative amount becomes visible after creation. The authoritative computation itself is server-side and is preserved through Payment.
- **Recommendation:** add a read-only server price-quote endpoint for a specific class/member context, return original price, applied percent, discount amount, final price, and expiry/validity conditions; show this confirmation before creating the hold. Revalidate and persist the quote atomically at registration. Do not make the frontend's estimate authoritative. Consider storing applied percent on Course Registration for audit/display if business owners require it; current schema stores only amount.
- **Migration:** no migration is required to add a quote API or pre-registration UI. A schema migration is needed only if the applied percent is added to historical Registration records; do not backfill it from current Membership plans because those can change.
- **Regression tests:** Member sees complete server quote before hold creation; altered client estimate cannot set payment amount; registration price snapshot remains unchanged after Plan/offering edits; Payment amount equals stored final price; expired/changed quotes are rejected/requoted; applied percentage display is tied to the captured quote.

### 6. P2 — Consultation staff display

**Status: CONFIRMED** (source; current DB has handled records). Both list and detail show a staff ID, not the staff member's name.

- **Relevant implementation:** backend `ConsultationResponse` and `ConsultationRequestService.response`; frontend `ConsultationQueue.jsx`; `dbo.consultation_request.handled_by` references a user.
- **Implemented rule/root cause:** response maps `handledBy` to only `request.getHandledBy().getId()`. Frontend renders `#<id>` in both the reception table and detail view. The response omits handler name even though the associated User has a name. All 15 live Consultation rows currently have a handler ID.
- **Impact:** staff must translate IDs to people, reducing handoff clarity; no underlying assignment behavior is changed.
- **Recommendation:** expose a handler display name (and optionally ID for traceability) in staff-only response and render the name in list/detail. Ensure list queries fetch/project the association efficiently and do not expose extra user data.
- **Migration:** none; user names and relation already exist.
- **Regression tests:** response includes assigned handler name; unassigned request renders em dash; list/detail agree; non-staff endpoints do not leak additional staff fields.

### 7. P1 — Membership duration and legacy periods

**Status: CONFIRMED** live values and source behavior. Policy “only new Memberships may be one month” is **REQUIRES OWNER CONFIRMATION**; code and data are not policy evidence.

- **Relevant implementation:** `CreateMembershipPlanRequest`, `UpdateMembershipPlanRequest`, `MembershipPlanService`, `MembershipService`, `MembershipUpgradeService`, `MembershipAutoRenewalService`; tables `dbo.membership_plan`, `dbo.membership`, `dbo.membership_upgrade`, `dbo.payment`.
- **Implemented rule:** Plan create/update accepts any positive `durationMonths`; there is no one-month cap in request validation or current DB schema. Normal purchase and ordinary queued renewal set end date with `startDate.plusMonths(plan.durationMonths)`. Auto-renew starts at source end date and adds the current Plan duration. Upgrade Memberships preserve the source Membership's remaining end date; `membership_upgrade` records payment, source, target Plan, remaining days, and result Membership. There is no dedicated Renewal entity; Membership rows form a date chain, while auto-renew source is associated via payment metadata and `purchase_payment_id`.
- **Live preview:** all 5 Plans currently have duration 1, so there are no Plan rows to change. These 9 Membership intervals differ from one calendar month (exclusive end):

  | Membership | Member ID | Current interval | One-month preview end | Status | Linked payment |
  |---:|---:|---|---|---|---|
  | 1002 | 5 | 2026-10-27 → 2026-11-25 | 2026-11-27 | UPGRADED | #4004 UPGRADE SUCCESS |
  | 1003 | 5 | 2026-10-27 → 2026-11-25 | 2026-11-27 | ACTIVE | #4005 UPGRADE SUCCESS |
  | 2 | 5 | 2026-11-25 → 2027-05-25 | 2026-12-25 | CANCELLED | no direct payment FK |
  | 3 | 5 | 2027-05-25 → 2027-11-25 | 2027-06-25 | CANCELLED | no direct payment FK |
  | 2002 | 6 | 2026-10-29 → 2027-01-29 | 2026-11-29 | ACTIVE, auto-renew | #6004 MEMBERSHIP SUCCESS |
  | 3002 | 7 | 2026-11-01 → 2027-05-01 | 2026-12-01 | ACTIVE, auto-renew | #7022 MEMBERSHIP SUCCESS |
  | 4002 | 8 | 2026-11-02 → 2026-11-26 | 2026-12-02 | UPGRADED | #9002 UPGRADE SUCCESS |
  | 4004 | 8 | 2026-11-02 → 2026-11-26 | 2026-12-02 | ACTIVE | #9007 UPGRADE SUCCESS |
  | 5005 | 9 | 2026-11-08 → 2026-12-02 | 2026-12-08 | ACTIVE | #11008 UPGRADE SUCCESS |

- **Queue/payment relationships:** future queued periods #5007 (Member 9, 2026-12-02→2027-01-02, payment #11023 paid 2026-11-12), #5014 (Member 6, 2027-01-29→2027-02-28, payment #11038 paid 2026-11-19), and #4003 (Member 7, 2027-05-01→2027-06-01, payment #9004 paid 2026-11-02) are each one month and start later than payment date. This confirms payment/purchase date must not be used as start date. Existing 9 mismatch rows include active benefits, upgrades, and cancelled periods; duration normalization would extend two upgrade intervals, shorten two canceled periods, shorten active auto-renew periods for #2002/#3002, and extend #5005. No update is proposed.
- **Root cause/impact:** current date intervals have been shaped by queue, upgrade/remaining-term, historical data, or prior duration values; a blanket `endDate = startDate + 1 month` would not preserve all paid rights or upgrade history. Changing Plan duration can also change later renewal calculation for existing chains.
- **Recommendation:** if the owner approves a one-month rule, scope it prospectively to new Plan versions/purchases and explicitly decide whether renewals/upgrades inherit it. Add backend validation and optionally a DB constraint for future Plan duration; do not rewrite existing Membership intervals. Preserve current ACTIVE/QUEUED periods and payment/upgrade history unless individually reconciled and separately approved. Before any future data operation, require verified backup, isolated restore/rehearsal, immutable per-ID preview, and exact target identity guard.
- **Migration:** no historical interval migration is justified by this audit. A schema constraint may be appropriate only after policy approval. Snapshot-model migration is separately required for benefit immutability (finding 2).
- **Regression tests:** reject non-one-month Plan create/update only if approved; new purchase and queue end dates are exclusive one-month intervals; renewal starts at source end and uses the explicitly selected version; upgrades preserve remaining rights; existing ACTIVE/QUEUED/MembershipUpgrade/payment rows are unchanged by Plan edits.

## Contradictions and unresolved product decisions

- The code and tests currently permit arbitrary linking of a guest consultation to an ACTIVE Member, while the desired security outcome is verified identity ownership. Existing tests encode the risky behavior; implementation and tests need owner-approved verification semantics.
- Plan benefits are mutable in source and live schema, whereas a fixed-at-purchase benefit policy would require a version/snapshot model. No current owner approval or historical plan-version evidence was found in this audit.
- One-month duration is not enforced by code/schema; live plans happen to be one month while 9 existing membership intervals are not. This is not evidence of a one-month policy for renewals, upgrades, or existing paid periods.
- Consultation DOB, staff display names, member price-quote behavior, and legacy snapshot backfill choices need product/security owner confirmation where stated above.

## Verification limits

- This was a source/test review and read-only SQL Server metadata/aggregate/targeted Membership query, not a browser QA run. Member modal appearance/readability and the rendered Unicode defect were not visually confirmed.
- Tests were inspected but not executed. In particular, the Consultation integration test currently confirms arbitrary active-Member linking is accepted for an assigned Receptionist.
- Live database evidence is a point-in-time view of the explicitly identified local/demo database. No statement here asserts production data or user-approved policy.
- No migration, SQL update, database write, backend/frontend source edit, commit, or push was performed. The only intended new file from this audit is this report; pre-existing working-tree changes were preserved.
