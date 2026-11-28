# SportCenter — ERD as built

This focused diagram records current source/schema relationships for principal flows. It is not a generated exhaustive table diagram. Column-level SQL Server types, nullability, keys, checks, and indexes are in [submission/SQL/SportCenter_FullSchema.sql](../submission/SQL/SportCenter_FullSchema.sql); the weekly-plan upgrade for existing databases is in [phase-training-plan-weekly-db5.sql](../backend/docs/sql/proposals/phase-training-plan-weekly-db5.sql). A checked-in script is not proof it ran.

```mermaid
erDiagram
  USERS ||--o| MEMBER_PROFILE : owns
  USERS ||--o| COACH : has
  USERS ||--o{ MEMBERSHIP : owns
  MEMBERSHIP_PLAN ||--o{ MEMBERSHIP : plan
  MEMBERSHIP ||--o{ PAYMENT : may_be_paid_by
  MEMBERSHIP ||--o{ MEMBERSHIP_RENEWAL_INTENT : source
  MEMBERSHIP_RENEWAL_INTENT ||--o{ PAYMENT : attempts
  MEMBERSHIP_PLAN ||--o{ MEMBERSHIP_UPGRADE_PATH : source_plan
  MEMBERSHIP_PLAN ||--o{ MEMBERSHIP_UPGRADE_PATH : target_plan
  MEMBERSHIP ||--o| MEMBERSHIP_UPGRADE : lineage

  SPORT ||--o{ COURSE : classifies
  COURSE ||--o{ COURSE_OFFERING : schedules
  COURSE_OFFERING ||--o{ CLASS : contains
  COACH ||--o{ CLASS : teaches
  CLASS ||--o{ CLASS_SESSION : has
  FACILITY ||--o{ CLASS_SESSION : hosts
  USERS ||--o{ COURSE_REGISTRATION : member
  CLASS ||--o{ COURSE_REGISTRATION : receives
  CLASS_SESSION ||--o{ ATTENDANCE : records
  USERS ||--o{ ATTENDANCE : learner
  ATTENDANCE ||--o{ ATTENDANCE_HISTORY : changes

  CLASS ||--o| TRAINING_PLAN : general_plan
  TRAINING_PLAN ||--o{ TRAINING_PLAN_WEEK : contains
  TRAINING_PLAN ||--o{ TRAINING_EXERCISE : owns
  TRAINING_PLAN_WEEK o|--o{ TRAINING_EXERCISE : optionally_groups
  CLASS_SESSION ||--o{ TRAINING_RESULT : measures
  USERS ||--o{ TRAINING_RESULT : learner
  CLASS ||--o{ INDIVIDUAL_TRAINING_PLAN : context
  USERS ||--o{ INDIVIDUAL_TRAINING_PLAN : learner
  INDIVIDUAL_TRAINING_PLAN ||--o{ INDIVIDUAL_TRAINING_PLAN_REVISION : versions
  INDIVIDUAL_TRAINING_PLAN_REVISION ||--o{ INDIVIDUAL_TRAINING_PLAN_WEEK : contains
  INDIVIDUAL_TRAINING_PLAN_WEEK ||--o{ INDIVIDUAL_TRAINING_PLAN_EXERCISE : contains

  USERS ||--o{ PERSONAL_TRAINING_BOOKING : books
  COACH ||--o{ PERSONAL_TRAINING_BOOKING : assigned
  SPORT ||--o{ PERSONAL_TRAINING_BOOKING : qualifies
  PERSONAL_TRAINING_BOOKING ||--o{ PAYMENT : may_be_paid_by
  USERS ||--o{ CONSULTATION_REQUEST : submits_or_handles
  USERS ||--o{ SUPPORT_TICKET : requester_or_assignee
  SUPPORT_TICKET ||--o{ SUPPORT_TICKET_MESSAGE : conversation
  USERS ||--o{ NOTIFICATION : receives
  USERS ||--o{ AUDIT_LOG : actor
```

## Relationship notes

- `users` is the shared identity root. Authentication role does not replace service-level ownership checks. `member_profile` and `coach` are role-specific records.
- Membership rows preserve purchase-time benefit snapshots. The plan relation remains provenance; later plan edits do not rewrite snapshot entitlements.
- Upgrade paths are directed source-plan to target-plan configuration. Membership and Payment rows retain transaction lineage; refunds remain separate Payment-level obligations.
- Registration is for a `class`; its `course_offering` supplies course, sport and date context. A `class_session` is one scheduled meeting. Attendance and TrainingResult are per session and Member.
- General class-plan weekly content is separate from Individual Training Plan revision history. Nullable General Plan `training_exercise.week_id` preserves legacy ungrouped exercises. A composite foreign key ensures a week belongs to the same `plan_id`.
- PT conflict checks compare Member active class sessions and PT bookings, and Coach schedules. These are service invariants, not a single database exclusion constraint.
- Payment purpose/status and business records remain separate. A Payment is not interchangeable with a Renewal Intent, registration, or Membership entitlement.
- This diagram omits lookup, join, audit-only and auxiliary tables for readability. Consult the SQL snapshot and JPA mapping before writing joins.

## Evidence and limits

Relationships are cross-checked against current migration and entity mappings. DB5 passed Hibernate `validate` on 2026-11-28. The SQL snapshot itself was not executed as an installation script during this documentation task. Never use it to update an existing database.
