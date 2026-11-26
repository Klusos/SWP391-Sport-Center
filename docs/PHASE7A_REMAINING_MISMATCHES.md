# Phase 7A — Remaining Mismatches and Evidence Limits

Date: 2026-11-23

## Resolved in this phase

The API Guide Renewal auto-renew description, generic Return/IPN claim, Upgrade pricing/eligibility description, Purchase provider wording, and Member membership-page summary were updated. The SRD DOCX Figure 3 and age-policy text remain untouched, as required.

## SRD review findings

| Finding | Classification | Evidence | Recommended document action |
|---|---|---|---|
| Renewal navigation absent from Figure 3 | DOCUMENT_MISMATCH | SRD v1.5.7 Section I.4 says Renewal navigation is not drawn. Current routes are `/member/membership` and `/receptionist/membership-sales` in `frontend/src/App.jsx`; Renewal controllers provide the separate Member and staff API paths. | Apply the grouped flow patch in `PHASE7A_SCREEN_FLOW_PATCH.md` using Figure 3's current style. |
| Course age-policy note still calls implementation/verification a gap | DOCUMENT_MISMATCH, with live acceptance UNVERIFIED | `CourseRegistrationService.java:157,386-390` invokes `CourseAgePolicyEvaluator`. Evaluator enforces REQUIRED bounds and under-10 safety rules. `CourseRegistrationIntegrationTest.java` contains age-policy and child-safety cases; existing report `backend/target/surefire-reports/TEST-group5.example.sportCenter.service.CourseRegistrationIntegrationTest.xml` records 37 tests, 0 failures/errors/skips and includes `registrationEnforcesAgePolicyAndUnderTenSafetyOnTheServer`. | SRD wording should be: **“Implemented in backend source; focused automated test evidence exists; runtime/browser acceptance verification pending.”** Do not claim full acceptance. |
| Physical and conceptual ERD terminology | MATCH in the available SRD prose; live alignment UNVERIFIED | SRD Section I.5 and Section III.2 state conceptual relationships are not an exact physical FK inventory and distinguish the manifest-derived physical ERD. No current SQL catalog inspection was authorized/performed in this documentation phase. | Preserve the distinction; do not describe the physical ERD as live-verified without catalog evidence for the target database. |
| Upgrade/Purchase policy descriptions | MATCH in current backend source; latest SRD wording is intentionally qualified | `PaymentService` blocks a Purchase when an open Renewal Intent conflicts; `MembershipUpgradeService` checks explicit paths, verified source paid value and conflicts. SRD II.3.2 expressly says to verify current behavior and preserve historical queued rows. | Retain the SRD's qualification; do not imply all historical queued Membership records are newly creatable. |
| Payment and Renewal transitions | Source/document alignment observed; runtime acceptance UNVERIFIED | `PaymentStatus` includes `PENDING`, `SUCCESS`, `CANCELLED`, `REFUND_REQUIRED`, `REFUNDED`; `RenewalIntentStatus` includes `OPEN`, `SETTLED`, `EXPIRED`, `CANCELLED`. SRD II.3.6 and II.6.5 distinguish Return, IPN, COUNTER and reconciliation. | No additional text correction identified from static review. Keep actual provider/refund verification marked pending. |

## Source and test evidence for age policy

- **Backend implementation:** `backend/src/main/java/group5/example/sportCenter/service/CourseAgePolicyEvaluator.java` evaluates configured minimum/maximum ages, REQUIRED versus RECOMMENDED policy and under-10 safeguards. `CourseRegistrationService.java` invokes it before registration.
- **Automated test source:** `backend/src/test/java/group5/example/sportCenter/service/CourseAgePolicyEvaluatorTest.java` and age-focused cases in `CourseRegistrationIntegrationTest.java`.
- **Existing report only:** retained Surefire report for `CourseRegistrationIntegrationTest` has 37 tests with 0 failures, 0 errors and 0 skipped; it includes the named age-policy server-enforcement test. This report was not generated in Phase 7A and was not rerun here.
- **Live/browser acceptance:** not verified in this phase. No backend/frontend was started and no database was accessed.

## Additional boundaries

- SRD v1.5.7 labels itself a reviewed draft, not a signed-off final submission; its own evidence section lists actual UI screenshots, complete backend-suite execution, code-level state-transition checks and real VNPay settlement as pending.
- SQL Server schema/physical diagram alignment was not rechecked. Treat schema claims as document provenance, not fresh database evidence.
- No implementation defect was confirmed. This is not proof that no runtime defect exists.
