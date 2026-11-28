# SRD/SRS as-built addendum — 2026-11-28

This addendum supplements `SportCenter_SRD_Reviewed_Aligned_v1.5.9.docx` with current integration behavior. It is not a new requirements approval. Where older SRD text differs, distinguish an approved requirement from as-built behavior; unresolved policy conflicts require owner decision. Detailed navigation and limitations are in [the Vietnamese defense handbook](SPORTCENTER_DEFENSE_HANDBOOK_VI.md), [the as-built ERD](ERD_AS_BUILT.md), and [the adversarial test matrix](SPORTCENTER_ADVERSARIAL_TEST_MATRIX.md).

## Current implemented scope

- Actors are Guest, Member, Receptionist, Coach and Admin. Business APIs use JWT authentication plus role/ownership checks. Public discovery is read-only; there is no public self-registration.
- Guest Consultation uses supplied contact identity. Authenticated Member Consultation uses account identity. A persisted valid Consultation DOB is authoritative when staff create a linked Member; a legacy request without DOB uses validated submitted DOB.
- Nonblank phone values must be ten ASCII digits beginning with `0`. Database CHECK constraints protect `users.phone` and `consultation_request.phone`; phone is intentionally non-unique. Account email uniqueness is separate.
- Membership purchase, Renewal, Upgrade, Payment, entitlement, cancellation and refund obligations are distinct persisted concepts. Purchased benefit snapshots and Payment-level financial lineage must be preserved.
- Upgrade Path is directed. An eligible quote uses a verified paid-value basis, whole-VND unused-day credit rounded down and capped at target price; the target receives its full configured duration. Frozen terms are not recomputed at settlement.
- Renewal is opt-in; eligible lifecycle work creates an Intent within the final three calendar days before source Membership end. It does not automatically collect. A paid successor begins at source end date. Ordinary upcoming-Membership cancellation retains the strict 72-hour boundary; no Renewal exception exists.
- Course registration opens 14 days before `CourseOffering.registrationDeadline`, not 30 days before Offering start. The 24-hour payment hold is capped by registration deadline.
- A Member cannot hold an active overlapping class registration and active PT booking. PT bookings last 90 minutes with a 15-minute initial payment hold. Confirmed Member PT cancellation uses a 24-hour notice rule, with the boundary enforced by `PersonalTrainingBookingService`.
- Initial attendance is one atomic complete-roster operation. New outcomes are PRESENT or ABSENT; historical EXCUSED remains readable and counts as recorded nonparticipation. Session completion does not imply attendance completeness. A PRESENT-to-ABSENT/EXCUSED correction is rejected if a TrainingResult exists.
- Elapsed scheduled class sessions transition to COMPLETED through application-ready catch-up and a five-minute fixed-delay scheduler in Vietnam business time with audited locking. No attendance/results are created. Global scheduling can disable jobs in QA.
- General class plans have ordered weekly sections in `training_plan_week`; legacy exercises remain unassigned until explicitly moved. Individual plans remain independently revisioned. Admin is read-only; assigned Coach writes use transactional and stale-draft protection.
- Support claim-and-start atomically assigns an unassigned OPEN ticket and moves it to IN_PROGRESS. Legacy unassigned IN_PROGRESS tickets remain claimable. Admin can participate after assignment without replacing the Receptionist owner.
- OpenAPI is served by Springdoc. Swagger UI/OpenAPI resources are permitted, while business endpoint authorization remains enforced. Swagger supports JWT Bearer through Authorize.

## Database and deployment references

The current fresh-schema snapshot is [submission/SQL/SportCenter_FullSchema.sql](../submission/SQL/SportCenter_FullSchema.sql). For an existing DB5, use reviewed incremental scripts and exact target guards; never run the fresh-schema snapshot. The General Plan week change is represented in the snapshot and DB5 migration record.

Current normal defaults: SQL Server `SportCenterDemo`, Hibernate `validate`, VNPay sandbox selected, mock settlement disabled, global/Renewal scheduling enabled, and PT outcome fallback disabled. Secrets remain external. See [API guide](../backend/docs/API_GUIDE.md) and [teammate SQL/setup guide](../submission/SQL/README_SQL_Setup.md).

## Use-case delta and actor traceability

| Actor | Use cases represented in current system | Main denial/alternative path |
|---|---|---|
| Guest | Browse public Sports/Courses/Offerings/Plans; submit a Consultation | Invalid required contact data or account-owned email is rejected; no self-registration |
| Member | Maintain own profile; buy/renew/upgrade Membership; register/cancel Course; book/cancel PT; view own attendance/training; manage own Support | Eligibility, deadline, capacity, ownership, payment and schedule-conflict guards; failed payment grants no entitlement |
| Receptionist | Work Consultations; create Member; assist Course/Membership/PT counter payments; claim/reply/manage eligible Support | Assignment/state/role validation; claim is atomic; no unauthorized reply |
| Coach | Manage assigned-class attendance, TrainingPlan, TrainingResult, progress and assigned PT outcomes | Assignment, elapsed-session/presence, result conflict, reason and role checks |
| Admin | Manage catalog/plans/path/staff/queues and reports; exceptional cancellation and PT outcome correction; read-only class-training supervision | Audit/financial lineage and explicit permissions remain mandatory; training view does not grant Coach writes |

Detailed preconditions, persistence effects, boundary rules and source entry points are in the Vietnamese handbook's “Actors and business flows” and “Business rules” sections. This table describes implementation scope and does not establish approval for unresolved policy in the business-rule register.

This addendum describes implementation, not complete live production verification. The adversarial matrix identifies executed evidence per scenario; H2 tests do not establish SQL Server behavior. DB5 Hibernate validation and the three focused backend suites (111/111) passed on 2026-11-28.

## Final hardening decisions and implementation status (2026-11-28)

The owner-approved account/price/path/password policies are recorded in the business-rule register. Current source includes Admin self/last-Admin guards, Coach deactivation checks, status-only Member deactivation, positive Membership Plan price validation, active directed Upgrade Path checks on Plan price edits, eight-character minimum for newly created/changed passwords, categorized Membership revenue, and the related Consultation, Support, PT, assessment, sport-discovery and Coach search corrections. The focused evidence and gaps are in `docs/FINAL_BUSINESS_RULE_HARDENING_AND_QA_REPORT.md`.

**Important gap:** Sport/Course/CourseOffering/Class parent deactivation protection (BR-06) has not been implemented. Do not infer it from existing Offering update restrictions. The original SRD Word document is unchanged; this Markdown addendum is not a substitute for editing it.
