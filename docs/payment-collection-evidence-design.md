# Payment Collection Evidence — Phase 5B.4B-1 Proposal

**Status:** Review proposal only. No migration has been executed and no live database was queried for this document. The current mapping and Phase 2A SQL artifacts were inspected on 2026-11-21. R01–R12 remain owner-approved; this document proposes technical storage and integration, not new business policy.

## 1. Current contract and blocker

Current `Payment` mapping has the merchant `transactionCode`, method, amount, status, local `paidAt`, `processedBy`, `refundedAt/refundedBy`, Renewal FK, attempt deadline/close reason, and `renewalSettlementAcceptedAt`. `MembershipRenewalIntent` holds `settledPayment` and `settledAt`. Phase 2A adds one-winner-per-Intent and one-successor-per-Intent indexes and composite FKs. These cover the current projection and settlement topology, but they do not durably identify which verified collection evidence was evaluated or accepted.

`GatewayCallback` contains merchant transaction code, success, amount, failure reason, and the signed provider `PayDate` string. `VnpayPaymentGateway.verifyCallback` validates the signature, merchant code, transaction reference, amount syntax, and success response/status. It does not expose `vnp_TransactionNo`, and does not currently validate `vnp_CurrCode`. The controller routes both VNPay Return and IPN through the same `PaymentService.processOnlineCallback` path. The browser Return path therefore currently has settlement side effects for existing Payment purposes; Renewal must be excluded from that path.

`Payment.note` is mutable, bounded to 500 characters, and used by legacy auto-renew identification and evidence text. It is not a durable verification ledger and must not be used as authoritative Renewal identity or collection evidence.

**Blocker resolved by this proposal:** before adapter implementation, add structured immutable collection evidence and append-only settlement/reconciliation decisions. No historical evidence is backfilled. Existing `paidAt`, `processedBy`, status, and notes remain as legacy/payment projections; they are not upgraded into verified evidence by inference.

## 2. Proposed model

Use two additive tables and one nullable Payment linkage column.

### 2.1 `dbo.payment_collection_evidence`

One row represents one distinct, cryptographically/operationally verified collection, not one HTTP delivery. It stores immutable normalized facts:

| Column | Proposed SQL Server type | Rule |
|---|---|---|
| `id` | `bigint IDENTITY` | PK |
| `payment_id` | `int NOT NULL` | FK to `dbo.payment(id)` |
| `source_code` | `varchar(12) NOT NULL` | `VNPAY` or `COUNTER` |
| `merchant_scope` | `nvarchar(64) NOT NULL` | VNPay TMN code or stable Sport Center counter scope; never a secret |
| `provider_transaction_reference` | `nvarchar(100) NULL` | Required for VNPAY; NULL for COUNTER unless a real provider reference exists |
| `counter_collection_reference` | `nvarchar(100) NULL` | Required for COUNTER: physical receipt/POS reference, or an owner-approved stable confirmation idempotency reference |
| `merchant_transaction_reference` | `nvarchar(40) NOT NULL` | Must equal the Payment `transaction_code` at verification |
| `collection_amount` | `decimal(12,2) NOT NULL` | Verified amount; must equal frozen Payment amount |
| `currency_code` | `char(3) NOT NULL` | Currently require `VND` |
| `verified_collection_at` | `datetimeoffset(6) NOT NULL` | Collection instant. Parse VNPay `PayDate` as `Asia/Ho_Chi_Minh`, then normalize to UTC offset; COUNTER uses server time. Never use callback arrival time. |
| `verified_at` | `datetimeoffset(6) NOT NULL` | Server verification instant, stored UTC |
| `verification_provenance` | `varchar(32) NOT NULL` | `VNPAY_SIGNED_IPN` or `AUTHENTICATED_COUNTER` |
| `verification_actor_user_id` | `int NULL` | FK to `dbo.users(id)`; required for COUNTER, NULL for VNPay |
| `verification_result` | `varchar(24) NOT NULL` | `VERIFIED_COLLECTED`; non-collection and rejected callback outcomes are not collection evidence |
| `digest_algorithm` | `varchar(12) NOT NULL` | `SHA-256` |
| `canonical_evidence_digest` | `binary(32) NOT NULL` | Hash of documented canonical normalized signed/confirmed fields; not a signature and not a secret |
| `created_at` | `datetimeoffset(6) NOT NULL` | Server-generated UTC persistence time |

The digest input must have a versioned canonicalization definition. For VNPay it includes source/scope, TxnRef, TransactionNo, amount, currency, response/status, and original PayDate text after signature verification. For COUNTER it includes Payment ID/merchant reference, counter collection reference, amount/currency, authenticated actor ID, and server confirmation instant. It must not include a signing key or raw sensitive payload. A digest alone does not prove signature validity; it is an integrity/correlation reference to the verified adapter operation.

Only a successfully verified collection is inserted here. Invalid signatures are not trusted business evidence and should be handled by security/operational logging, not converted into a collection row.

### 2.2 `dbo.payment_collection_decision`

An append-only decision/event history records how immutable evidence was used, and records verified but non-collection outcomes or a conflicting repeat without rewriting collection facts.

| Column | Proposed SQL Server type | Rule |
|---|---|---|
| `id` | `bigint IDENTITY` | PK |
| `payment_id` | `int NOT NULL` | FK to Payment |
| `evidence_id` | `bigint NULL` | Optional evidence associated with this Payment; composite FK prevents cross-Payment linkage |
| `related_evidence_id` | `bigint NULL` | Optional evidence involved in a provider-reference collision, may belong to another Payment |
| `event_code` | `varchar(32) NOT NULL` | `VERIFIED_NON_COLLECTION`, `SETTLEMENT_ACCEPTED`, `RECONCILIATION_REQUIRED`, `RECONCILIATION_RESOLVED`, `DUPLICATE_DELIVERY`, `REFERENCE_CONFLICT`, `REFUND_COMPLETION_RECORDED` |
| `event_at` | `datetimeoffset(6) NOT NULL` | Server time of decision/observation |
| `actor_user_id` | `int NULL` | FK to users for staff action; COUNTER/explicit manual decisions require it |
| `reason_code` | `varchar(64) NULL` | Stable machine-readable reason; no secrets or free-form provider payload |
| `external_reference` | `nvarchar(100) NULL` | Provider/receipt reference where applicable; required for a recorded refund-completion event |
| `event_digest` | `binary(32) NULL` | Digest of the normalized signed/confirmed event; includes event code to avoid collisions |
| `created_at` | `datetimeoffset(6) NOT NULL` | Server-generated UTC persistence time |

`REFUND_REQUIRED` remains a current Payment reconciliation state only. It never asserts that money was refunded. `REFUND_COMPLETION_RECORDED` is a separate audit event and must require a real external receipt/provider reference; it must not be emitted merely because staff set Payment status to `REFUNDED`. Whether current `confirmRefund` can establish that level of evidence needs an explicit owner/operations decision before integration.

Decision rows are immutable: transitions append a new row. Application services must not update/delete either table. Granting the application role SELECT/INSERT-only access is recommended but is an operational permission change and is outside this proposal/migration. No retention duration is assumed; do not auto-delete evidence. The owner must establish applicable retention/privacy requirements before production rollout.

### 2.3 Accepted evidence linkage

Add nullable `dbo.payment.accepted_collection_evidence_id bigint NULL`. Add a unique candidate key on `(payment_id,id)` in the evidence table and a composite FK:

```text
payment(id, accepted_collection_evidence_id)
    -> payment_collection_evidence(payment_id, id)
```

This proves that the selected evidence belongs to that exact Payment. Add a filtered unique index on `payment.accepted_collection_evidence_id IS NOT NULL` so one evidence row cannot be accepted by two Payment rows. The one-column Payment pointer allows at most one accepted evidence per Payment. The existing Phase 2A `renewal_settlement_accepted_at` unique filtered index still guarantees at most one winning attempt per Intent; the existing Intent `settled_payment_id` and successor FK/index remain authoritative for the winner/successor relation. Postflight must also verify that a newly settled Intent's selected Payment has an accepted-evidence link. SQL Server CHECK constraints cannot enforce this cross-table invariant alone; settlement service transaction plus a verification query are required.

The Payment↔Evidence FK cycle is nullable and insertable in order: existing Payment attempt → evidence row referencing Payment → update Payment with accepted evidence ID. Intent settlement then references the already existing Payment; successor creation follows existing Phase 2A order. No FK is added from evidence to Intent, avoiding another Intent/Payment/Evidence cycle. Renewal identity remains the relational `Payment.renewal_intent_id`.

## 3. Uniqueness and duplicate handling

- VNPAY provider identity is `(source_code, merchant_scope, provider_transaction_reference)`, not the callback digest. A filtered unique index on that tuple prevents a provider collection ID from being attached to multiple Payment attempts within one merchant scope.
- A separate filtered unique index on `(payment_id, canonical_evidence_digest)` deduplicates an identical verified collection payload for one Payment.
- A filtered unique index on `(merchant_scope,counter_collection_reference)` for COUNTER deduplicates a receipt/confirmation reference. Distinct references remain separate evidence rows, including multiple collections against one Payment. A repeated request with the same reference returns the existing row; the same reference with mismatched facts becomes a conflict event.
- Different provider transaction references for one Payment are allowed as separate collection rows. This preserves evidence of multiple collections. Across attempts for one Intent, multiple collection evidence rows may exist, but the existing filtered unique accepted-attempt index permits only one winner; all other collected attempts are recorded as `RECONCILIATION_REQUIRED` without automatic refund.
- `event_digest` plus `(payment_id,event_digest)` uniqueness deduplicates the same decision/event retry. A same provider reference with different amount/time/payment mapping is a `REFERENCE_CONFLICT` decision that points to the existing evidence where possible and stores the incoming event digest; it never edits the original evidence.
- Browser Return must not insert collection evidence or accept Renewal settlement. VNPay evidence is eligible only from verified IPN. A verified signed failure may append `VERIFIED_NON_COLLECTION`, but it never creates evidence or a Membership.

## 4. Proposed SQL Server checks and FKs

Evidence checks:

- source/provenance/result combinations are valid;
- VNPAY requires provider reference and no actor; COUNTER requires actor and no provider transaction reference;
- amount is positive; currency is `VND`; digest algorithm is `SHA-256`;
- `merchant_transaction_reference` is nonblank;
- `verified_at` is not before `verified_collection_at` only if owner confirms clock-skew policy. Do not add that comparison without policy because delayed verification is normal, but verification should ordinarily occur after collection.

FKs: evidence→Payment; evidence actor→users; Payment accepted evidence composite FK; decision→Payment; decision `(payment_id,evidence_id)`→evidence `(payment_id,id)`; decision `related_evidence_id`→evidence(id); decision actor→users. Do not cascade delete. All constraints are additive and nullable linkages preserve historical Payment/Intent records.

Index definitions and full guards are in `backend/docs/sql/proposals/payment-collection-evidence-migration.sql`.

## 5. Integration/transaction plan (not implemented)

### VNPay IPN

1. Verify signature and normalize required signed fields, including `vnp_CurrCode='VND'`, merchant scope/TmnCode, `vnp_TxnRef`, `vnp_TransactionNo`, amount, response/status and PayDate. Extend the trusted callback result to carry provider reference, currency, and verified timestamp without exposing secrets.
2. Resolve Payment by merchant TxnRef using an unlocked metadata lookup. A browser Return is never a Renewal settlement source; keep current Purchase/Upgrade compatibility isolated.
3. For a relational Renewal Payment (`renewal_intent_id IS NOT NULL`), dispatch before any Payment row lock to a Renewal-specific internal adapter. Invalid/missing provider fields cannot create collection evidence or Membership.
4. Acquire `Member → Intent → Payment → source Membership` locks. Re-read and validate relational identities and deadlines; persist immutable evidence, append decision, set Payment accepted evidence/marker, settle Intent, and create successor atomically.
5. For a same provider transaction and identical digest, return idempotently. Same provider ID with different facts is reconciliation/conflict. A different collected attempt after a winner creates evidence plus reconciliation decision; no automatic refund.

### COUNTER

1. Authenticate/authorize Receptionist before constructing evidence. Ignore any client timestamp; server creates `verified_collection_at` and `verified_at`.
2. Resolve Renewal FK without locking Payment, then acquire `Member → Intent → Payment → source Membership` and recheck OPEN state/deadlines.
3. Persist the evidence with `verification_actor_user_id`, append decision, and invoke the same engine transaction. Existing generic Purchase/Upgrade counter path remains unchanged.
4. Duplicate confirmation returns existing evidence. Post-deadline collection is preserved as evidence plus reconciliation; never accept backdated time.

Current `PaymentService.processOnlineCallback` already locks Member before Payment for membership purposes, and `processAtCounter` does likewise. They do not lock Intent. Calling the new engine after either has locked Payment would create the inversion `Member→Payment→Intent`. The Renewal dispatcher must branch after signature/metadata resolution but before `findByTransactionCodeForUpdate`/`findByIdForUpdate`, then let the Renewal engine acquire the full order. Non-Renewal branches retain their existing logic. VNPay Return must check the relational Renewal FK and return a pending/result view without settlement; IPN is the only provider settlement entry.

No provider network call belongs inside the DB transaction. Signature verification is local cryptographic work and should be completed before acquiring locks. If SQL Server unique conflict/deadlock occurs, roll back; retry only by reloading the same evidence/Intent/payment state in a fresh transaction, never by repeating provider collection.

## 6. Migration proposal and safety

The SQL is additive: two tables, one nullable Payment column, FKs/checks/indexes, no updates/backfill/deletes, and no permissions or triggers. Existing Payment rows remain NULL for accepted evidence. They are legacy state, not fabricated evidence. Preflight reports existing Renewal accepted markers with no evidence and any partial schema; those rows need manual review before enabling adapters. The migration must not update them.

Rollback is allowed only before any evidence/event rows exist and after no application version uses the new column. Drop the Payment composite FK/index, new Payment column, decision table, then evidence table/objects in reverse dependency order. If any evidence exists, rollback by dropping these objects would destroy audit records and is prohibited; retain schema and deploy a forward fix instead. No automatic restore plan is implied.

The migration script intentionally has unset server/database identity placeholders and throws until an operator fills an approved exact target. It contains no `USE`, no reference to the original DB, and is a proposal only. Preflight and verify scripts are SELECT/metadata-only and use the same fail-closed identity parameters.

## 7. Verification plan

After separate authorization for an isolated SQL Server clone: Hibernate `ddl-auto=validate`; test FK composite mismatch, evidence source CHECKs, provider-reference collision, duplicate digest, multiple distinct collections, duplicate COUNTER attempt, two collected Renewal attempts/one winner, same-Payment accepted evidence, cross-Payment evidence link rejection, decision history append-only service behavior, transaction rollback, deadlock/lock-timeout bounded behavior, old-app writes with NULL accepted evidence, and manifest integrity. H2 can cover deterministic service behavior but not SQL Server filtered-index/locking proof. No test is run in this design phase.

## 8. Decisions requiring owner review before Phase 5B.4B-2

1. Approve the two-table model and nullable `Payment.accepted_collection_evidence_id` linkage.
2. Confirm VND-only evidence and define `merchant_scope` for COUNTER; confirm the exact VNPay merchant reference fields (`vnp_TxnRef`, `vnp_TransactionNo`). Decide what stable counter receipt/idempotency reference exists. The current COUNTER request has no such reference. Recommended: use a real receipt/POS reference; if unavailable, approve one server-created collection reference reused on request retries. Without a stable reference, the system cannot distinguish a repeated confirmation request from a second physical collection for the same Payment.
3. Approve `datetimeoffset(6)` normalized-UTC storage for verified evidence while legacy `Payment.paidAt` remains Vietnam-local `datetime2`.
4. Define what receipt/provider reference and actor evidence are sufficient to record actual refund completion; keep `REFUND_REQUIRED` distinct.
5. Confirm whether exact duplicate deliveries are deduplicated without a delivery counter, and operational/security logging handles invalid-signature traffic.
6. Set retention/privacy policy and whether DB-role permissions may be tightened to SELECT/INSERT for evidence tables.
7. Confirm no existing Renewal intents/accepted markers require disposition; preflight must establish this on the approved clone before migration rehearsal.

## 9. Evidence reviewed

- `docs/membership-final-redesign-implementation-plan.md` (approved R01–R12 design and intended Phase 5 boundaries; its status/current-implementation prose predates Phases 5B.3/5B.4A and is stale).
- `backend/docs/sql/proposals/membership-redesign-phase2a-migration.sql`, `...preflight.sql`, `...verify.sql` (proposal artifacts; not executed here).
- `Payment.java`, `MembershipRenewalIntent.java`, `MembershipRenewalSettlementService.java`, `RenewalSettlementEvidence.java`.
- `GatewayCallback.java`, `VnpayPaymentGateway.java`, `PaymentController.java`, `PaymentService.java`.
- `PaymentRepository.java`, existing Phase 2A FK/index conventions.

**Phase 5B.4B-1 conclusion:** proposal ready for owner/schema review. Not migration-ready until the listed decisions are confirmed and SQL Server clone rehearsal is separately authorized.
