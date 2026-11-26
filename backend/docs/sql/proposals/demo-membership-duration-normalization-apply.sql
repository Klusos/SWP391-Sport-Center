/*
  DRAFT ONLY — do not execute without owner approval for the exact rows below.
  Default @execute = 0 prints a preview and returns without modifying data.
  No MembershipPlan or Payment fields are updated by this proposal.

  Candidate rows 2 and 3 are CANCELLED, have no purchase_payment_id, and have
  approved cancellation requests. However, member 5 has successful, unlinked
  Membership payments 1004 and 1005 for the same plan. Their mapping to rows 2/3
  is unresolved. Owner confirmation/payment reconciliation is required before
  enabling execution. These candidates are not classified as test data.

  Before a future execution: take and verify a full SQL Server backup, restore
  it to an isolated database, rehearse this exact script there, save the complete
  preflight output externally, and stop application writers. Then set all four
  approval flags explicitly. Never run from application startup.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @expectedServer sysname = N'LAPTOP-416VBDLK';
DECLARE @expectedDatabase sysname = N'SportCenterDemo';
DECLARE @execute bit = 0;
DECLARE @ownerApprovedExactRows bit = 0;
DECLARE @unlinkedPaymentsReviewed bit = 0;
DECLARE @backupAndRestoreRehearsalVerified bit = 0;
DECLARE @approvedBy nvarchar(128) = NULL;

IF CONVERT(sysname, SERVERPROPERTY('ServerName')) <> @expectedServer
   OR DB_NAME() <> @expectedDatabase
    THROW 50502, 'Target identity differs from the reviewed local SportCenterDemo instance. Stop.', 1;

DECLARE @targets table (
    membership_id int NOT NULL PRIMARY KEY,
    expected_start_date date NOT NULL,
    expected_current_end_date date NOT NULL,
    proposed_end_date date NOT NULL
);
INSERT @targets (membership_id, expected_start_date, expected_current_end_date, proposed_end_date)
VALUES (2, '2026-11-25', '2027-05-25', '2026-12-25'),
       (3, '2027-05-25', '2027-11-25', '2027-06-25');

-- Always show the before/after preview, even while execution is disabled.
SELECT t.membership_id,
       m.member_id,
       u.name AS member_name,
       m.plan_id,
       mp.name AS plan_name,
       mp.duration_months,
       m.start_date,
       m.end_date AS current_exclusive_end_date,
       t.proposed_end_date AS proposed_exclusive_end_date,
       m.status,
       m.auto_renew,
       m.purchase_payment_id,
       (SELECT COUNT_BIG(*) FROM dbo.payment p
        WHERE p.member_id = m.member_id AND p.purpose = 'MEMBERSHIP'
          AND p.reference_id = m.plan_id
          AND p.status IN ('SUCCESS', 'REFUND_REQUIRED', 'REFUNDED')) AS same_member_plan_paid_payment_count,
       (SELECT COUNT_BIG(*) FROM dbo.membership_upgrade u
        WHERE u.source_membership_id = m.id OR u.result_membership_id = m.id) AS upgrade_relation_count
FROM @targets AS t
LEFT JOIN dbo.membership AS m ON m.id = t.membership_id
LEFT JOIN dbo.users AS u ON u.id = m.member_id
LEFT JOIN dbo.membership_plan AS mp ON mp.id = m.plan_id
ORDER BY t.membership_id;

IF @execute = 0
BEGIN
    PRINT 'PREVIEW ONLY: no rows were changed. Owner confirmation and payment reconciliation are still required.';
    RETURN;
END;

IF @ownerApprovedExactRows <> 1
   OR @unlinkedPaymentsReviewed <> 1
   OR @backupAndRestoreRehearsalVerified <> 1
   OR NULLIF(LTRIM(RTRIM(@approvedBy)), N'') IS NULL
    THROW 50503, 'Execution requires owner approval for exact rows, unlinked-payment review, verified backup/rehearsal, and approver identity.', 1;

-- Fail closed if any preflight value/state changed or if a candidate now has
-- a linked payment, an upgrade relation, an unapproved cancellation, or an
-- unexpected state. The successful same-member/plan payments still require
-- explicit manual reconciliation before the approval flag may be set.
IF EXISTS (
    SELECT 1
    FROM @targets AS t
    LEFT JOIN dbo.membership AS m ON m.id = t.membership_id
    WHERE m.id IS NULL
       OR m.start_date <> t.expected_start_date
       OR m.end_date <> t.expected_current_end_date
       OR m.status <> 'CANCELLED'
       OR m.purchase_payment_id IS NOT NULL
       OR t.proposed_end_date <> DATEADD(month, 1, t.expected_start_date)
       OR EXISTS (SELECT 1 FROM dbo.membership_upgrade u
                  WHERE u.source_membership_id = m.id OR u.result_membership_id = m.id)
       OR NOT EXISTS (SELECT 1 FROM dbo.membership_cancellation_request cr
                      WHERE cr.membership_id = m.id AND cr.status = 'APPROVED')
)
    THROW 50504, 'Candidate rows no longer match the reviewed cancellation/date/payment/upgrade preconditions. Stop and rerun preflight.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @changed table (
        membership_id int NOT NULL,
        old_end_date date NOT NULL,
        new_end_date date NOT NULL
    );

    UPDATE m
       SET end_date = t.proposed_end_date
    OUTPUT inserted.id, deleted.end_date, inserted.end_date
      INTO @changed (membership_id, old_end_date, new_end_date)
    FROM dbo.membership AS m
    JOIN @targets AS t ON t.membership_id = m.id
    WHERE m.start_date = t.expected_start_date
      AND m.end_date = t.expected_current_end_date
      AND m.status = 'CANCELLED'
      AND m.purchase_payment_id IS NULL;

    IF (SELECT COUNT(*) FROM @changed) <> (SELECT COUNT(*) FROM @targets)
        THROW 50505, 'Updated row count did not match the explicit target list.', 1;

    SELECT membership_id, old_end_date, new_end_date FROM @changed ORDER BY membership_id;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
