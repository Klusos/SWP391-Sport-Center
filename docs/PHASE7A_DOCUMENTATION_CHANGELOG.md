# Phase 7A Documentation Changelog

Date: 2026-11-23

## Files changed

- `backend/docs/API_GUIDE.md` — aligned Membership Purchase wording, Upgrade contract, VNPay Return/IPN distinction, scheduler-created Renewal Intent and Payment Attempt lifecycle.
- `docs/PHASE7A_DOCUMENTATION_CHANGELOG.md` — this change record.
- `docs/PHASE7A_SCREEN_FLOW_PATCH.md` — editable Mermaid proposal and Figure 3 editing instructions.
- `docs/PHASE7A_REMAINING_MISMATCHES.md` — source-backed remaining findings and evidence limits.

The SRD DOCX was not modified. No application source, test, SQL, migration, or runtime configuration files were changed.

## Corrections made

1. Corrected the API Guide's Renewal description: scheduler-created Intent, default-off scheduler, Member/Receptionist attempt creation, one live pending attempt, terminal Intent behavior, and verified-evidence settlement.
2. Distinguished Renewal browser Return (display/refresh only) from verified VNPay IPN settlement, while preserving separate Purchase/Upgrade callback behavior.
3. Corrected obsolete Upgrade tier/price-per-day/30-day approximation rules to explicit directed Admin paths, verified paid-value credit, frozen quote, and full captured target duration after successful settlement.
4. Removed the obsolete claim that the Member UI is COUNTER-only because no online provider exists. The guide now states that available ONLINE checkout depends on provider configuration and that the mock is test/demo only.
5. Updated the Member membership page summary to include Upgrade, Payment history, and Renewal Intent visibility where available.

## Evidence checked

- Renewal endpoints: `MemberRenewalController`, `ReceptionistRenewalController`.
- Renewal scheduler gate: `MembershipRenewalScheduler` and `application.properties`.
- Renewal attempt gate and evidence: `MembershipRenewalPaymentAttemptService`, `MembershipRenewalSettlementService`, `RenewalPaymentAdapterService`, and `PaymentController`.
- Upgrade path/quote: `MembershipUpgradePathController`, `MembershipUpgradeService`, `MembershipController`, `PaymentService`.
- Age policy: `CourseAgePolicyEvaluator`, `CourseRegistrationService`, `CourseRegistrationIntegrationTest`, and retained Surefire report.
- Frontend routes: `frontend/src/App.jsx`.

## Verification limits

No tests, services, browser sessions, SQL Server connections, migrations or database writes were run. Existing reports were inspected only; they are not fresh execution evidence. `git diff --check` was run for the documentation changes.
