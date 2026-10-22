# Renewal settlement SQL Server rehearsal (C2.1.2)

This manually selected Failsafe harness calls `RenewalSettlementService` directly. It has no HTTP endpoint, executes no migration, changes no schema, and performs no cleanup. It is not selected by ordinary `mvnw verify`. Every row it creates persists; the owner accepts synthetic test payments in the academic development database.

## Modes and safety checks

Set exactly one mode opt-in:

- Development database: `RENEWAL_SQLSERVER_DEVELOPMENT_OPT_IN=true`. This mode requires expected database name exactly `SportCenterDB` and permits the normal application JDBC URL only with this explicit opt-in.
- Disposable clone: `RENEWAL_SQLSERVER_DISPOSABLE_OPT_IN=true`. This preserves the separately restored clone mode; the expected database name may be owner-selected.

Both modes require exact equality of `RENEWAL_SQLSERVER_JDBC_URL` and `RENEWAL_SQLSERVER_EXPECTED_JDBC_URL`, owner-supplied `RENEWAL_SQLSERVER_EXPECTED_SERVER` and `RENEWAL_SQLSERVER_EXPECTED_DATABASE`, and a target identity query confirming `@@SERVERNAME`, `DB_NAME()`, and ONLINE state. The target must name its database explicitly. The harness checks Checkpoint 1 and 1.1 tables, columns, constraints, foreign keys and indexes before Spring starts. Hibernate is configured with `ddl-auto=validate`; no migration is run. The web server is disabled. Membership, course-registration and PT scheduled callbacks are replaced with no-ops. `mvnw verify` without the dedicated profile does not connect to SQL Server or run this class.

The identity guard prevents accidental URL/identity mismatch; it cannot independently prove that an owner-designated server is disposable or private. Use credentials scoped to the intended development/test database. Do not run concurrent harness executions: fixture rows are run-scoped and independent, but concurrent runs can contend for locks and make results harder to interpret.

## Development-mode scenarios

These five scenarios are enabled in development mode and create only fresh fixture records:

| Scenario | Persisted effect |
|---|---|
| Concurrent replay of one ONLINE attempt | One synthetic successful renewal and one new Membership. |
| Two captures under one renewal Payment | One synthetic successful renewal/Membership; the other attempt is retained for reconciliation. |
| Capture versus cancellation | Fixture payment either settles or is cancelled/reconciled, depending on lock order; at most one Membership is created. |
| Competing renewal generation | One pending renewal Payment for a newly created source Membership. |
| Timely and late COUNTER settlement | One synthetic successful renewal/Membership and one late `REFUND_REQUIRED` fixture Payment. No VNPay attempt is created for COUNTER. |

The global membership expiration race, the deliberately inconsistent cross-linked application-failure scenario, and deliberate filtered-index violation scenario are aborted in development mode and remain available only in disposable-clone mode. The expiration case calls a global service that scans all due memberships. The other two leave deliberately inconsistent fixture links, so they are unsuitable for persistent shared development data.

## Fixture identification and persistent rows

Each execution prints a run marker (`RNLIT-...`). Every fixture member/staff email and name and plan name includes that marker. Payment transaction codes include a shortened run marker where the test creates the payment; scheduler-generated renewal payments are traceable from the marked source Membership. Every created user, plan, source Membership, source Payment, renewal Payment, attempt, and settlement-created Membership ID is printed. Successful Payments and fixture payment notes are labeled `TEST DATA`; source payments are synthetic historical-payment fixtures, not receipts. Checkout attempt references are linked to printed Payment IDs and are printed with their IDs.

The five development scenarios currently create approximately 12 users, 6 plans, 12 payments, 6 source Memberships, 4 checkout attempts, and 3–4 settlement-created Memberships (9–10 Membership rows total). The cancellation race determines whether its extra Membership is created. Synthetic successful payments will appear in payment history and may affect revenue views; no record is removed afterward. Do not mistake any such row for a real payment or receipt.

## Run in PowerShell

From the repository root, set target values from the owner-verified connection configuration. Use a private PowerShell session; do not put passwords in literal assignments, arguments, repository files, or logs. Example for the development mode (replace the non-secret placeholders with the owner-confirmed target):

```powershell
$env:RENEWAL_SQLSERVER_DEVELOPMENT_OPT_IN = 'true'
Remove-Item Env:RENEWAL_SQLSERVER_DISPOSABLE_OPT_IN -ErrorAction SilentlyContinue
$env:RENEWAL_SQLSERVER_JDBC_URL = '<owner-confirmed exact JDBC URL>'
$env:RENEWAL_SQLSERVER_EXPECTED_JDBC_URL = '<same exact JDBC URL>'
$env:RENEWAL_SQLSERVER_EXPECTED_SERVER = '<owner-confirmed @@SERVERNAME>'
$env:RENEWAL_SQLSERVER_EXPECTED_DATABASE = 'SportCenterDB'
$credential = Get-Credential -Message 'SQL Server development test login'
$env:RENEWAL_SQLSERVER_USERNAME = $credential.UserName
$secretPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($credential.Password)
try { $env:RENEWAL_SQLSERVER_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPtr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPtr) }
$jwtBytes = New-Object byte[] 48
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($jwtBytes) } finally { $rng.Dispose() }
$env:RENEWAL_SQLSERVER_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
Set-Location backend
.\mvnw.cmd -Prenewal-sqlserver-rehearsal verify
```

The database password exists in the current process environment so Maven can inherit it, but is not printed or passed as a command-line argument. Clear process-scoped values after the run by closing that PowerShell session. The command runs only `RenewalSettlementSqlServerIT`; do not combine it with the SQL Server E2E profile. Review the output and retain the run marker and fixture IDs.

For the optional disposable-clone mode, use a separately restored clone and replace the two mode settings with:

```powershell
Remove-Item Env:RENEWAL_SQLSERVER_DEVELOPMENT_OPT_IN -ErrorAction SilentlyContinue
$env:RENEWAL_SQLSERVER_DISPOSABLE_OPT_IN = 'true'
$env:RENEWAL_SQLSERVER_EXPECTED_DATABASE = '<owner-confirmed clone database name>'
```

Supply that clone's exact JDBC URL and expected server identity using the same variables, then use the same Maven command. The clone mode runs all scenarios, including global expiry and the two intentionally inconsistent fixture tests.

## Read-only postflight queries

Use the printed IDs. These queries only inspect rows and schema:

```sql
SELECT @@SERVERNAME AS server_name, DB_NAME() AS database_name,
       CONVERT(varchar(20), DATABASEPROPERTYEX(DB_NAME(), 'Status')) AS database_state;

SELECT u.id AS user_id, u.email, u.name, p.id AS plan_id, p.name AS plan_name,
       m.id AS membership_id, m.start_date, m.end_date, m.status AS membership_status,
       pay.id AS payment_id, pay.purpose, pay.status AS payment_status, pay.amount,
       pay.transaction_code, pay.note, pay.renewal_membership_id
FROM dbo.users u
LEFT JOIN dbo.Membership m ON m.member_id = u.id
LEFT JOIN dbo.membership_plan p ON p.id = m.plan_id
LEFT JOIN dbo.payment pay ON pay.member_id = u.id
WHERE u.email LIKE N'%<run-marker>%'
ORDER BY u.id, m.id, pay.id;

SELECT a.id AS attempt_id, a.payment_id, a.transaction_reference, a.status,
       a.provider_transaction_id, a.provider_amount, a.provider_response_code,
       a.verified_at, a.reconciliation_reason, a.applied_membership_id
FROM dbo.payment_checkout_attempt a
WHERE a.id IN (<printed attempt IDs>) OR a.payment_id IN (<printed renewal payment IDs>)
ORDER BY a.payment_id, a.created_at, a.id;
```

No cleanup command is provided. Preserve existing rows and inspect only the marker-owned fixture graph. The disposable-clone mode remains the place to run the three excluded scenarios; discard that clone after its review rather than cleaning it with broad deletes.
