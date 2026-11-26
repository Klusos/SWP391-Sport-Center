# Phase 7A — Figure 3 Screen Flow Patch

## Editable source availability

No editable Figure 3 source (`.drawio`, `.puml`, `.mmd`, `.mermaid`, `.vsdx`, `.pptx` or source SVG) was found under `docs/` or `backend/docs/`. The latest SRD is a DOCX and is not modified in this phase. Use the diagram below as the flow-content patch; apply its nodes and edges in the existing Figure 3 style rather than replacing the diagram wholesale.

## Mermaid source proposal

```mermaid
flowchart LR
    Guest[Guest] --> Public[Public Home / Catalog]
    Public --> Login[Login]

    Login --> MemberHome[MEMBER Dashboard]
    MemberHome --> MemberMembership[Member Membership]
    MemberMembership --> Purchase[Purchase: select active Plan]
    MemberMembership --> Upgrade[Upgrade: select configured directed Path]
    MemberMembership --> Renewal[View scheduler-created OPEN Renewal Intent]
    Purchase --> MemberPayment[Create Purchase Payment]
    Upgrade --> UpgradePayment[Create Upgrade Payment]
    Renewal --> RenewalAttempt[Create ONLINE attempt for existing Intent]
    MemberPayment --> Online[ONLINE checkout or eligible COUNTER flow]
    UpgradePayment --> Online
    RenewalAttempt --> Vnpay[VNPay checkout]
    Vnpay --> Return[Renewal Browser Return: display / refresh only]
    Vnpay --> IPN[Verified VNPay IPN]
    Online --> Settlement[Authorized payment settlement]
    IPN --> RenewalSettle[Renewal settlement]
    Settlement --> MembershipResult[Membership / payment result]
    RenewalSettle --> MembershipResult

    Login --> Receptionist{RECEPTIONIST}
    Receptionist --> StaffHome[Staff Dashboard]
    Receptionist --> Sales[Membership Sales / member lookup]
    Sales --> StaffPurchase[Create allowed Purchase / Upgrade Payment]
    Sales --> StaffRenewal[View existing Renewal Intent]
    StaffRenewal --> CounterAttempt[Create COUNTER attempt]
    CounterAttempt --> CounterConfirm[Authorized staff confirmation]
    CounterConfirm --> RenewalSettle

    Login --> Admin{ADMIN}
    Admin --> PlanAdmin[Membership Plan Management]
    Admin --> UpgradePathAdmin[Upgrade Path Management]
    UpgradePathAdmin -. configures directed Plan pairs .-> Upgrade

    Login --> Coach{COACH}
    Coach --> CoachHome[Coach Dashboard / Training / Attendance]
    MemberHome --> Courses[Course Registration / Schedule]
    MemberHome --> PT[Personal Training Booking]
```

## Editing notes

- Keep the existing Figure 3 color palette, typography, rounded node style, arrow style and layout density. Add a Renewal sub-flow or linked inset if the main figure becomes crowded.
- Keep roles as swimlanes or visually grouped regions: Member self-service; Receptionist operations; Admin configuration; Coach workspace; public Guest.
- Draw the Upgrade Path configuration relation as a configuration dependency, not as a Member action.
- Show the Renewal Intent as created by the scheduler; Member and Receptionist act only on an existing OPEN Intent.
- Show browser Return as a display/refresh branch, separate from Renewal settlement. Verified IPN and authorized COUNTER confirmation are the Renewal collection/settlement inputs.
- Do not imply auto-charge, user-created Intent, or browser-return settlement.

## Route evidence

- Member Membership: `frontend/src/App.jsx` route `/member/membership` (around lines 713–719).
- Receptionist Membership Sales: `/receptionist/membership-sales` (around lines 560–567).
- Admin Plan/Upgrade Path management: `/staff/membership-plans` and `MembershipUpgradePathController` at `/api/membership-upgrade-paths`.
- Role-specific route groups: `MemberRoute`, `ReceptionistRoute`, `AdminRoute`, and `CoachRoute` in `frontend/src/App.jsx`.
- Member Renewal API: `/api/memberships/renewal-intents` in `MemberRenewalController`.
- Receptionist Renewal API: `/api/receptionist/members/{memberId}/renewal-intents` in `ReceptionistRenewalController`.
- VNPay Return/IPN: separate handlers in `PaymentController`.

Route presence is static source evidence, not a browser-rendering or live authorization result.
