# Membership Redesign 鈥?Final Owner Direction and Implementation Plan

**Status:** Approved Renewal decision record (R01–R11, owner approval 2026-11-20), with Phase 5B.1 persistence mapping and Phase 5B.2 internal eligibility/quote service implemented. Ordinary Purchase Queue removal, full-term Upgrade, verified paid-value credit, and paid-only Renewal successor remain approved directions. Other Upgrade decisions in section 8 remain open. The Renewal creation service is not wired to the scheduler or any API; attempt, settlement, callback, notification, and opt-out behavior remain unimplemented.

**Evidence boundary:** Current source and prior design/audit documents were reviewed. Phase 1 has now verified the local SQL identity and read-only Membership/Payment/schema inventory; results and the TLS exception are documented in `docs/membership-phase1-live-preflight.md`. The preflight used owner-approved `trustServerCertificate=true` only for local dev/test and did not change configuration or data.

**Phase 2A design:** The column-level schema, RenewalIntent/FK and uniqueness model, nullable legacy strategy, migration gates, and unexecuted SQL drafts are in [`membership-phase2a-schema-migration-design.md`](membership-phase2a-schema-migration-design.md). That document is the current detailed Phase 2A proposal; this file remains the overall phased implementation plan.

## 1. Current implementation versus approved direction

| Area | Current source/schema | Required target |
|---|---|---|
| Ordinary purchase | `PaymentService` blocks a future-start period and pending Membership/Upgrade Payments, but permits purchase while a Membership is currently effective. `MembershipService.activateOrRenewFromPayment` chains the new row at the last active Membership's `endDate`, creating a Queue. | Under the member lock, reject purchase if any Membership is effective (`ACTIVE`, `startDate <= today < endDate`); reject conflicting pending intents; never create a future-start Membership from ordinary Purchase. After expiry, new purchase starts from its captured terms. |
| Admin assignment | `MembershipService.createMembership` creates no Payment but accepts a future `startDate` and does not check Plan ACTIVE. | Review this path as a separate grant operation. No future Membership creation under the new rule unless owner explicitly retains a reviewed exception; require active Plan and audit actor/reason if retained. |
| Upgrade | `MembershipUpgradeService` calculates incremental price for only the remaining days, compares mutable source/target Plan price per day, rounds up to 1,000 VND, and creates a target Membership with `startDate=today`, `endDate=source.endDate`. | New target Membership receives the target Plan's full captured term; charge target quote less a verifiable unused-value credit; source is replaced only after valid Payment SUCCESS. No future Queue period. |
| Renewal | `MembershipRenewalIntent` JPA mapping, enums, repository, and an unwired `MembershipRenewalIntentService.createForScheduler` now exist. `MembershipAutoRenewalService.createIfDue` remains paused; legacy note-based settlement code still exists for historical payments. | R01–R11 approved. The internal service validates eligibility/conflicts and freezes the active Plan quote. No scheduler/API calls it; no attempts, settlement, callbacks, notifications, or opt-out transitions are enabled. Purchase and Upgrade must check OPEN Intents before the service is wired. |
| Snapshot | `Membership` has a Plan FK and purchase Payment FK only. Payment has amount, method/purpose/reference/status/timestamps; no general plan-term quote or deadline columns. `MembershipUpgrade` is one row per Payment, with source/target references, remaining days, expiry, and result. | Persist terms at intent and on the resulting Membership. Store verifiable source-value/credit evidence for Upgrade. Keep legacy terms UNKNOWN when evidence is missing. |
| Callback/retry integrity | Same Payment row is locked by transaction code and terminal status makes duplicate callback idempotent. Different Payment attempts for the same Renewal are grouped by note text; member locking and later-period checks help serialize, but no relational intent key or DB uniqueness protects one successful successor. | Relate attempts to one Renewal Intent; lock member/intent/source consistently; ensure at most one successful renewal successor and at most one source replacement. Late duplicate collection follows refund-required/manual reconciliation, never a second Membership. |

The current membership purchase endpoint already rechecks pending Membership and Upgrade Payment state while holding the member row lock. Upgrade creation/settlement and renewal scheduler/callback paths also use the member row lock. Preserve a consistent lock order (`Member 鈫?Intent/Payment 鈫?Membership`) and perform the final eligibility check inside each write transaction.

## 2. Upgrade credit design to implement after approval

### Source calculation

Do not use the current source Plan price or reconstruct a legacy purchase from its current Plan. For a source Membership with verified paid-value basis and recorded purchased period:

```text
totalDays = days(source periodStart, source periodEndExclusive)
unusedDays = days(valuationDate, source periodEndExclusive)
rawCredit = sourcePaidValueBasis 脳 unusedDays / totalDays
credit = conservative currency rounding of rawCredit, capped by:
         sourcePaidValueBasis and targetListPrice
upgradeAmount = targetListPrice 鈭?credit
```

The source period uses actual Membership start/end dates (end exclusive). Quote/credit time is captured when Upgrade intent is created and frozen for that Payment deadline. Only a source Membership with a verified paid-value basis can contribute credit. A `purchasePayment` with `SUCCESS` proves that transaction's amount, but an upgraded Membership's payment is a net top-up; its full current-term basis must preserve the prior verified credit carried into it plus the successful top-up. Store that carry-forward basis explicitly so future Upgrade does not lose or invent value.

For an ordinary Membership purchase, initial paid-value basis is the actual successful Payment amount. For an Upgrade result, proposed basis is `sourceCreditApplied + successful Upgrade Payment amount` (subject to exact rounding/reconciliation). Renewal's new period basis is the successful renewal Payment amount. An Admin grant with no payment has no paid-value evidence and yields no credit unless owner approves another rule.

Do not issue credit for legacy `UNKNOWN`, missing/non-SUCCESS/refunded payment, unverifiable term boundaries, or an Admin grant by guessing from Plan price. Recommended safe behavior is block Upgrade pending manual reconciliation; alternatively zero credit and charge target price only with explicit owner approval and clear UI disclosure.

### Target Membership and amount

- Quote target price, name, duration and all four current Plan entitlements at intent creation; target must be ACTIVE at quote time. A later Plan edit/deactivation does not rewrite a valid pending quote.
- New Upgrade Membership gets a full target term using the captured `durationMonths`, with exclusive end date calculated from the approved start date. Source stays unchanged while pending. On valid SUCCESS, atomically mark source replaced and insert the target Membership with its own benefit snapshot, Payment link, paid-value basis and provenance.
- No target membership or source-status change on failed, cancelled, expired, malformed-time or late-paid Upgrade; follow the approved late-payment refund-required workflow.
- Upgrade does not create a Membership Queue. The new row starts at settlement/approved effective date, not at the old Membership's end date.
- Use Payment amount as the exact amount charged. Never send a client-calculated amount. If the current price quote changed between UI option display and intent creation, require a final confirmation using the server's newly created quote/Payment amount before checkout/Counter processing.

### Current formula versus new formula

The existing formula uses 30-day months and price-per-day difference, rounds the result up to 1,000 VND, and retains the source end date. It is **superseded** by target full price minus source unused-value credit. Do not reuse `proratedAmount` or the old remaining-days minimum as a silent default.

### Formula choices still needing approval

1. Credit rounding: recommend round **down** at the smallest supported VND unit so credit never exceeds evidence; current `Payment.amount` uses `decimal(12,2)` and VNPay scales VND by 100. Confirm whether rounding is to 1 VND, 1,000 VND, or another unit.
2. If credit is equal to or exceeds target list price, should Upgrade be blocked, be free, or cap credit at target price and discard the excess? Recommend block and require an explicit lower-tier/non-upgrade purchase path; do not create negative amounts.
3. Minimum remaining days: current code requires at least 3 days. Confirm whether it remains under the new full-term purchase model or is removed.
4. Upgrade effective date when provider paid time is timely but callback arrives later: use VN local date of verified provider payment or callback settlement date? A late callback can cross midnight, so this changes the term end date.
5. Confirm target eligibility retains current rule (active target, higher cost/day, all current benefits retained) or define a new 鈥淯pgrade鈥?eligibility rule for full-term pricing.

## 3. Renewal Intent, attempts, and deadlines

### Approved Renewal decision record (R01–R11, owner approval 2026-11-20)

These decisions supersede the prior open-policy wording in this document:

| ID | Approved policy |
|---|---|
| R01 | Capture the current ACTIVE Plan price, duration, and benefits when the Intent is created. The captured quote is immutable afterward. |
| R02 | Do not create an Intent when the Plan is INACTIVE before creation. Honor a valid captured Intent if the Plan is later edited or deactivated. |
| R03 | Only the scheduler creates Renewal Intents, and only for `autoRenew=true`; there is no manual Intent-creation path. |
| R04 | COUNTER paid time is server confirmation time; it cannot be backdated. Money collected after the applicable deadline goes to manual reconciliation. |
| R05 | A Member cancellation recorded before settlement prevents settlement. If funds were collected, set `REFUND_REQUIRED`; do not state that a refund has completed. |
| R06 | Scheduler catches up on startup and periodic runs while the request is within the three-day window. Catch-up does not extend the fixed deadline or create duplicate Intents. |
| R07 | Use `Asia/Ho_Chi_Minh`. Intent deadline is exclusive midnight at source `endDate`; COUNTER deadline equals it; ONLINE deadline is `min(createdAt + 15 minutes, intent deadline)`. Equality is late. A verified timely VNPay `paidAt` can support a delayed callback. |
| R08 | At most one attempt is accepted for settlement and one successor is created per Intent. Other collected attempts enter manual reconciliation; no automatic refund. |
| R09 | Disabling `autoRenew` atomically closes an unsettled Intent and its pending attempts. Already-settled successors remain valid. |
| R10 | No automatic legacy Renewal backfill or legacy settlement. Preserve completed history and payment evidence. Historical mutation requires separate owner approval. |
| R11 | Notifications are in-app only and idempotent per business event; no email/SMS. `REFUND_REQUIRED` must never be presented as refunded. |

Approved policy does not settle implementation details such as transaction/lock ordering, uniqueness-race recovery, enum-to-constraint compatibility, durable notification deduplication, and database-specific concurrency behavior. These require implementation and isolated verification. Phase 5B.1 does not enable Renewal scheduling, attempts, settlement, callbacks, or opt-out behavior.

### Renewal Intent entity (owner approved)

Payment supports multiple rows already: transaction codes are unique and `MembershipUpgrade` is one row per Payment attempt. But current Renewal attempts are grouped only by parsing `Payment.note`; that is not a durable intent relation. The owner approved a separate RenewalIntent business table so an intent exists independently from its payment attempts.

- **RenewalIntent:** one unique intent per source Membership period. Store source Membership, scheduler/opt-in provenance, target Plan reference, immutable price/benefit/duration snapshot, intent state, created time, and exclusive `validUntil`. An expired or cancelled intent is not reopened and does not get an automatic replacement for the same source period.
- **Payment attempt:** add a nullable FK `renewalIntentId` for Renewal payments. Each attempt owns its transaction code, method, amount, status, `attemptValidUntil`, provider paid time, and refund/closure outcome. Multiple attempts can reference one intent and must reuse its frozen amount/terms; do not re-read the mutable Plan.
- **Successful successor Membership:** created only after a verified successful attempt. Store the source relation through its unique `renewalIntentId` (the Intent itself identifies the source Membership), `purchasePaymentId` for the winning attempt, captured benefits/Plan name/duration, and `paidValueBasis`. `startDate=source.endDate`; `endDate=startDate + captured duration`; keep end exclusive. This is the specifically authorized Renewal successor, not an ordinary purchase Queue. Do not add a second direct `renewedFromMembershipId` relation unless a later reviewed design establishes a need for it.
- **Upgrade detail:** continue one `MembershipUpgrade` row per Upgrade Payment attempt; add verified source-value/period/credit fields or point it at the immutable Payment quote snapshot. Its result Membership remains null until settlement. Multiple failed Upgrade attempts may be retained as separate historical rows.

Enforce one RenewalIntent per source Membership and one successful successor per Intent using the existing unique/FK constraints. Do not fall back to matching free-text notes. Phase 5B.1 maps the current SQL columns; retry is allowed only as another attempt under the same open Intent.

### Attempt deadlines and retry behavior

Let `intentDeadline = sourceMembership.endDate at 00:00 Asia/Ho_Chi_Minh` (exclusive). Intent is open only when scheduler creates it in the approved three-day window and before that deadline.

- COUNTER attempt deadline = `intentDeadline`.
- ONLINE attempt deadline = `min(attemptCreatedAt + configured 15 minutes, intentDeadline)`.
- A valid successful ONLINE settlement requires signed/provider-verified `paidAt < attemptDeadline` **and** `paidAt < intentDeadline`; callback arrival may be later. Do not substitute callback arrival time for absent/invalid provider time. Exact equality is late. A recorded Member cancellation before settlement still blocks application even when provider time was timely; collected money enters `REFUND_REQUIRED`/manual reconciliation.
- A failed or expired attempt closes only that attempt. If the intent is still open, a new attempt can use the anchor snapshot/deadline. A provider callback for an expired attempt whose verified payment time was before its attempt deadline may still settle, but must atomically win the one-success-per-intent rule. Any second successful collection cannot create another Membership and must be reconciled/refunded.
- Store attempt closure reason in the structured renewal-only field; do not infer cause by parsing `Payment.note`. Keep `REFUND_REQUIRED` distinct from `REFUNDED` in records and UI.
- Never auto-charge. Scheduler creates one request and notification only. No Membership is created/extended at intent creation or expiry.

### Scheduler, retry, idempotency, and race controls

- Current daily scheduler runs at Vietnam midnight, expires Membership rows, then reaches the paused legacy renewal path. `createIfDue` now returns empty; this current behavior does not implement R06 catch-up or create durable Intents.
- This is insufficient for the new model: a cancelled/failed attempt can let a later scheduler run create a new intent; free-text note markers are not foreign keys; attempts lack a shared snapshot/deadline. Anchor the intent once on the source Membership and let scheduler retry only the **attempt** when that same intent remains open. Never create a second anchor for the source.
- Scheduler, Purchase, Upgrade, attempt creation/cancel, COUNTER settlement and ONLINE callback must lock the same Member row before source/Payment/Intent rows, then recheck state after lock. This is a Phase 5B implementation requirement, not a claim about the current Renewal path.
- Add a database uniqueness defense: one renewal anchor per source Membership and one successful successor per `renewalIntentId` (filtered unique index or unique nullable FK). On race, catch uniqueness conflict, reread authoritative state, and return the existing result or refund-required outcome; do not return SUCCESS without its Membership.
- Same Payment callback remains idempotent using Payment row lock/status. Different attempts for the same Renewal are serialized by source Member/intent lock; at most one may create a successor. Duplicate callback for the winning attempt returns its settled result. Distinct collected attempts after the winner are not silently accepted.
- Upgrade creation and Renewal scheduler share the Member lock. If Upgrade Payment is PENDING, scheduler must not create Renewal intent. If Renewal intent/attempt is open, Upgrade creation must be blocked. If Renewal has already succeeded and successor Membership is future-start, Upgrade is blocked until owner-defined eligible Membership becomes current. Never cancel/refund a successful Payment automatically.

## 4. Plan deactivation and legacy handling

- Plan deactivation before a Renewal Intent: do not create the Intent (R02). If the Plan is edited or deactivated after valid Intent creation, honor the captured quote through its deadline.
- Plan deactivation/edit after a valid intent: honor its immutable snapshot and deadline; do not require Plan ACTIVE at settlement or read current benefits/price/duration.
- Legacy Memberships without evidence stay `LEGACY_UNKNOWN`. Do not backfill current Plan terms, do not derive Upgrade credit from current Plan price, and do not modify old duration/endDate/status/payment. Before allowing Upgrade from an unknown source, block or use an explicitly owner-approved zero-credit/manual evidence flow.
- Legacy future-start Memberships: do not delete, cancel, refund, or rewrite. Phase 1 must list each current row and linked Payment/Upgrade. Define how existing paid future period coexists with the new no-Queue rule; until decided, block conflicting new intents without changing the old row.
- Existing pending Purchase/Upgrade/Renewal Payment without quote snapshots: current amount/reference does not prove all original terms. Produce an individual preflight list; require explicit owner disposition (honor only provable amount and review benefits, cancel/recreate, or another approved path). Never silently copy current Plan values.

## 5. Receptionist Membership Sales refresh

Current page refresh invokes `load` (Members/active Plans), `loadQueue` (pending Membership counter Payments), `loadReviewQueues` (cancellation requests), and bumps `historyVersion` for selected Member history. `ReceptionistRefundQueue` fetches independently and is not refreshed by the parent button.

Minimal implementation: parent owns/increments a refresh generation (or passes a `refreshAll` callback/ref); child refund queue re-fetches on that signal. One page button refreshes all feeds and keeps current Member, search, selected Payment, and refund pagination where valid. Retain per-feed loading/error/empty state and stale-request guards; partial failures do not erase successful/previous data. After Payment, cancellation or refund action success, invalidate only affected feeds plus the selected Member history. Do not change refund confirmation or payment semantics.

## 6. Seven implementation phases and completion gates

### Phase 1 鈥?Read-only preflight

**Work:** verify exact SQL Server instance/database/schema read-only; inspect current applied schema, indexes/FKs, Membership status/date chains, active and future rows, `purchasePaymentId`, pending/success/cancelled/refund Payments, Upgrade source/result edges, prior renewal note markers, Plan status/terms, and AutoRenew choices. Preview current values only; do not mutate.

**Tests/evidence:** query results and row-level classification reviewed by owner; capture baseline counts/manifest and identify pending transactions, duplicate candidates, historical Queue and legacy multi-month rows. Use no fixture assumptions.

**Done when:** exact target confirmed, every existing future-start row and pending intent is accounted for, and unresolved rows have an owner disposition. If DB is unavailable, mark live-data work blocked and do not start backfill.

### Phase 2 鈥?Snapshot schema and migration plan

**Proposed additive schema (RenewalIntent table approved):**

- `RenewalIntent` (new): source Membership FK, period/generation key, scheduler/opt-in provenance, lifecycle status/timestamps and intent deadline, target Plan reference, immutable Plan name/price/duration/benefit snapshot, quote amount, and successful Payment/successor relation if not safely derived.
- `Membership`: Plan name/description, price/duration, course/PT discount and entitlement snapshots, `paidValueBasis`, and unique nullable `renewalIntentId`. Each paid period stores its own snapshot. A Renewal successor copies its snapshot from RenewalIntent, never from the current Plan at settlement. Existing dates/status/Plan FK remain intact.
- `Payment`: nullable RenewalIntent FK and per-attempt `attemptValidUntil`, provider paid-time evidence, and attempt/refund outcome. Purchase/Upgrade quote snapshot and Upgrade source paid-value/period/unused/credit/rounding evidence as needed. Intent deadline/terms belong to RenewalIntent; `amount` remains exact payable amount. Do not change Course/PT or ordinary Purchase deadline rules.
- `MembershipUpgrade`: retain source/target/payment/remaining/status/result relation; persist frozen source period, paid-value basis, unused days, applied credit and quote version/reference if those fields are not fully represented on the Payment snapshot.
- DDL constraints: FKs/indexes for RenewalIntent/source/payment/result, unique intent per source period, unique renewal successor per source, and a constraint limiting successful settlement per intent; checks for nonnegative prices/credits, positive captured duration, discount range, and credit not exceeding verified source unused value. Avoid Plan cascade updates/deletes into snapshots.

For a retry, RenewalIntent holds the authoritative quote and each Payment attempt points to it and charges its frozen amount. For first ordinary Purchase/Upgrade Payment, that Payment may hold its own quote snapshot. On successful settlement, copy authoritative terms to the resulting Membership row. This is intentional: intent/Payment stores quote and transactions; Membership stores period entitlement/history.

**Legacy migration:** add nullable columns first. Backfill verified data only from authoritative evidence. Else mark UNKNOWN with null benefit/value-basis fields. Existing Membership `startDate`, `endDate`, status, Payment amount/status/paid/refund data, Upgrade remainingDays and Course/PT price fields must compare unchanged before/after. Do not set `durationMonths=1` or alter any old period in this migration.

**Tests/evidence:** SQL Server clone DDL/FK/unique/check tests, ORM schema validation, pre/post row manifests, rollback rehearsal, and duplicate anchor/success insertion tests. H2 alone does not prove SQL Server filtered-index behavior.

**Done when:** schema is deployed additively only after exact-target approval, verified backup/restore and clone rehearsal; UNKNOWN handling is approved; no business code has switched to missing snapshot fields.

### Phase 3 鈥?Remove general Purchase Queue

**Work:** under Member lock, allow ordinary Purchase only when no Membership is effective and no conflicting open Payment exists; reject future-start ADMIN assignment (or remove/exception only with owner approval); after expiry set new period start to approved purchase effective date, not previous history end. Keep eligible Upgrade separate. Preserve existing legacy future-start rows untouched.

**Tests:** active Membership blocks Member/Receptionist purchase; expired allows; pending Purchase/Upgrade blocks duplicate; race between two Purchase requests creates at most one Payment/Membership; settlement revalidates if Membership status changes; no new ordinary purchase creates a future start. Admin assignment permission/start-date tests.

**Done when:** backend enforces the rule even for direct API calls; no Queue can be created by ordinary purchase; legacy rows remain unchanged.

### Phase 4 鈥?Upgrade pricing redesign

**Work:** replace remaining-day delta pricing with target full quote minus verified unused source paid-value credit. Re-quote under Member lock when creating intent; freeze target Plan and source credit evidence. Source status and benefits remain unchanged while pending. On valid SUCCESS, atomically close source and create target Membership with full target captured duration, target entitlement snapshot, `purchasePayment`, and carry-forward paid-value basis. The target term should default to captured target `durationMonths`; one-month-only validation is a separate product decision.

**Tests:** original purchase Payment amount is basis, not current Plan; prior Upgrade carries credit + successful top-up into new basis; no credit from refunded/unverified/manual Admin data without policy; 28/29/30/31-day periods, same-day start, last day, zero/negative credit, rounding, target price bounds; Plan edits while pending; failed/late/duplicate Payment leaves source untouched; SUCCESS replaces exactly once and full target end date is created; API quote and Payment amount agree; target plan deactivation after intent.

**Done when:** no legacy current-Plan inference, every credit is traceable to paid-value and period evidence, one transaction replaces source only after valid success, and UI reconfirms the actual server amount.

### Phase 5 鈥?Renewal Intent, Payment attempts and scheduled Membership

**Work:** replace note-parsed Renewal identity with the approved RenewalIntent table. Scheduler creates one intent for each eligible Membership with `autoRenew=true` three days before exclusive `endDate`; this is opt-in and never authorizes automatic collection. Notify the Member. Multiple Payment attempts reference the intent and reuse its snapshot. COUNTER is available through the intent deadline; ONLINE attempts expire at min(15 minutes, intent deadline). Failed/expired attempts permit retry only while the intent is open. Valid Payment SUCCESS creates one future-start successor Membership at source exclusive `endDate`, with its own snapshot copied from the intent and the successful Payment link. This paid Renewal successor is the only new Queue case. No Membership is created at intent creation.

**Conflict policy:** open Upgrade blocks a new Renewal intent; open Renewal intent/attempt blocks a new Upgrade. A paid Renewal successor blocks ordinary Purchase and conflicting Upgrade until the relevant period becomes eligible. A late callback that pays on time but loses the unique-success race must not create a second row; set a reviewed refund-required result. Do not automatically cancel/refund a successful transaction.

**Tests:** scheduler runs twice and across concurrent instances; one RenewalIntent per source period; multiple Payment attempts remain linked to that intent; retry retains original amount/benefits despite Plan changes; Plan deactivation before/after intent; online deadline exact boundary, callback delayed but provider-paid-before-deadline, provider time after deadline, missing/malformed timestamp; COUNTER before/at/after deadline; retry after failure/attempt expiry; explicit Member cancellation versus expiry; simultaneous Upgrade/scheduler/attempt/callback; duplicate callback for same Payment; two distinct successful attempts produce one successor at most; no Membership before Payment SUCCESS; new Membership starts exactly at source exclusive end and its snapshot/duration come from the intent.

**Done when:** one intent per source, multiple attempts are auditable, no unpaid Membership appears, at most one paid successor is inserted, and successful period is linked to its own snapshot.

### Phase 6 鈥?Receptionist Refresh UX

**Work:** one Membership Sales page refresh coordinates Member/catalog, pending Membership Payment queue, cancellation requests, Membership refund queue, and selected Member history/payment. Refresh state is per feed; child refund queue responds to parent refresh signal; preserve search, selection and valid pagination. After successful Payment/cancellation/refund, refresh affected data without page reload.

**Tests:** one button triggers expected read API calls; stale responses ignored; one feed failure does not erase other data; loading/disabled states and selection preserved; successful actions refresh their queues. Do not click refund confirmation in shared browser QA.

**Done when:** all requested feeds can be made current from one control with clear partial-error feedback and existing action permissions/workflows unchanged.

### Phase 7 鈥?Regression, SRD and demo scenarios

**Work:** run backend integration tests against verified disposable SQL Server clone; frontend unit tests, lint/build; authenticated browser checks for Member, Receptionist, Admin; update SRD/business-rule docs and demo guide only after owner approves the final rounding, deadline, UNKNOWN and Admin grant rules.

**Demo scenarios:** active purchase blocked; expired purchase succeeds; Upgrade quote shows target full-term price, verified credit and actual amount; pending Upgrade leaves source unchanged; success replaces once; scheduler creates one Renewal intent at three-day window; no auto charge; COUNTER/ONLINE retry; valid delayed callback; deadline late payment refund-required; Plan edit/deactivation mid-intent; renewed Membership starts at source exclusive end with its own snapshot; legacy multi-month/UNKNOWN remains unchanged; Receptionist single Refresh updates every feed.

**Done when:** all phase checks pass; test environment is isolated; live legacy data has no unreviewed mutation; release notes distinguish new rules from preserved historical records.

## 7. Phases that must ship together

1. **Phase 2 schema must precede Phases 3鈥?**; expand migration is additive, and the new application must not deploy before required columns/indexes exist.
2. **Phases 3, 4 and 5 must be one coordinated backend release** or use explicit compatibility gates. They share `PaymentService`, member locking, Membership creation/settlement, pending-intent checks and callback behavior. Shipping one alone can reintroduce Queue creation or leave Purchase/Upgrade/Renewal conflicts unguarded.
3. Within **Phase 4**, quote snapshot, credit evidence, Payment amount and source replacement are one atomic contract. Do not ship price UI without the server contract.
4. Within **Phase 5**, Renewal anchor + attempt deadline + retry + callback validation + unique successor + Membership snapshot are one feature; implementing only scheduler or retry creates ambiguous/double settlement states.
5. **Phase 6 may follow backend release**, but if it displays new intent/deadline/credit fields, it must ship with the corresponding API DTOs. Do not move refund state behavior as part of refresh.
6. **Phase 7 tests are required alongside each phase**, not deferred until the end. SRD/demo updates wait until open policy choices are approved.

## 8. Remaining decisions and technical details

1. **Preflight exception handling:** disposition for each existing future-start paid Queue and each pending Membership/Upgrade/Renewal Payment. No automatic cancellation/refund/activation is proposed.
2. **Admin direct assignment:** remove, disallow future start, or preserve as an audited exception? Must it require a reason, and what start date/benefit basis applies?
3. **Upgrade rounding:** smallest VND unit vs 1,000 VND, and whether credit at/above target price blocks Upgrade or yields a zero-cost result.
4. **Upgrade source eligibility:** retain current higher-price-per-day/benefit-superset rule? Keep or remove current three-day minimum?
5. **Upgrade date anchor:** target full-term start uses verified provider paid date or settlement date when callback is late across midnight?
6. **Unverified source:** block Upgrade for legacy UNKNOWN/Admin grant, or permit zero credit/manual review? Recommendation: block until verified or explicitly reconciled.
7. **Renewal policy status:** R01–R11 are approved. Lock ordering, uniqueness-race handling, transaction rollback, and scheduler recovery require implementation plus SQL Server clone verification; these are engineering acceptance details, not policy approval gaps.
8. **Notification idempotency:** confirm the durable notification event key/mechanism before implementing delivery. Approved channels remain in-app only.
9. **Pending historic quote migration:** preserve rows and evidence; no automatic legacy settlement/backfill. Any historical mutation requires separate owner approval.
10. **Duration policy:** whether new Plans/ordinary purchases are restricted to one month or use captured positive Plan duration remains a separate decision. Do not modify legacy periods.
11. **Upgrade rounding and eligibility:** rounding unit, credit at/above target price, UNKNOWN/Admin-grant handling, minimum remaining days, and delayed callback effective date remain unresolved.
12. **Upgrade paid-value provenance:** define the exact carried basis/audit chain; never infer legacy basis from current Plan price.
13. **Plan description and legacy UI:** decide whether description is contractual, and approve Member/Receptionist treatment for legacy UNKNOWN entitlements.
14. **Legacy records:** disposition for Memberships 2/3 without Payment FK and reconciliation of the nine recorded refunds requires owner review.

**Phase 5B.1 record:** R01–R11 are owner-approved as of 2026-11-20. This phase adds only JPA persistence mapping and an isolated H2 mapping test; it does not execute migration/DDL or write to SQL Server and does not activate Renewal workflows. Phase 1 preflight and Phase 2A schema work are historical records; current database schema claims must use fresh read-only metadata evidence.
