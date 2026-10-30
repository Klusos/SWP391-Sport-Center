# Phase 12.1 Individual Training Plan clone E2E

Owner-run test for the approved disposable SQL Server clone only. It does not migrate or provision fixtures. Never point it at `SportCenterDB`. Do not run it from a laptop until the isolated clone identity and schema have been independently verified.

## Required owner-provisioned fixtures

Choose one unique marker of 6–40 letters, digits, `_` or `-`. The test requires:

- A COACH whose display name contains the marker, assigned to both `<marker>-ITP-ACTIVE` and `<marker>-ITP-EXPIRED` classes. The test does not require this Coach to have exactly one class assignment overall.
- A MEMBER whose display name contains the marker, with a `CONFIRMED` course registration in `<marker>-ITP-ACTIVE`, a currently effective `ACTIVE` Membership (`startDate <= Vietnam business date < endDate`) whose plan has `allowPersonalTrainingPlan=true`, and no individual plan yet for this class/member pair.
- A second MEMBER whose display name contains the marker and who does not own the plan created by the test.
- A second COACH whose display name contains the marker and who is not assigned to `<marker>-ITP-ACTIVE`.
- An EXPIRED MEMBER whose display name contains the marker, with no currently effective qualifying Membership, at least one qualifying Membership whose exclusive `endDate` is on or before the Vietnam business date, and an existing historical individual plan in `<marker>-ITP-EXPIRED`.
- Unique marked class names, correct coach assignment, valid active accounts/passwords, and exactly one matching class for each required class name.

The test logs in all five accounts and checks the marker in each login display name. It loads the active class from the Coach's `/api/training/classes`, checks the Member through `/api/training/classes/{classId}/progress`, and checks active Membership eligibility. The expired test checks the Member's historical plan through `/api/training/me/individual-plans`, confirms the same Coach is assigned to its class, allows historical member reads, and expects Coach editing to be forbidden.

There is no safe fixture automation script in this checkpoint. Fixture provisioning requires ordinary supported workflows: staff-created Coach/Member accounts, class creation and assignment, Member course registration followed by legitimate payment settlement, and an effective Membership with a qualifying plan. The expired case additionally requires genuine elapsed Membership history and an existing plan created while eligible; no endpoint accepts backdated membership or plan history. Reuse owner-designated fixtures where possible. If any are absent, provision only through normal application workflows and wait for expiry/history, or treat E2E as blocked. Do not fabricate payment, registration, or history with SQL.

## PC PowerShell runbook

Run on the PC hosting the approved clone, from the verified integration checkout. Replace only the repository path, JDK path if different, and marker. The test annotation and safety guard hard-pin the datasource to `127.0.0.1:14333/SportCenterPtE2E`; `SPRING_DATASOURCE_URL` below is an explicit matching setting, not an override of that guard. `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` are required by `application.properties` and must be supplied from the private clone configuration. Never paste passwords/secrets in commands or commit them.

```powershell
Set-Location 'E:\00Project\SWP391-Sport-Center\backend' # verified PC integration checkout

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
mvn.cmd -version

$env:SPRING_DATASOURCE_URL = 'jdbc:sqlserver://127.0.0.1:14333;databaseName=SportCenterPtE2E;encrypt=true;trustServerCertificate=true'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:SPORTCENTER_SCHEDULING_ENABLED = 'false'
$env:PAYMENT_PROVIDER = 'mock'
$env:PAYMENT_MOCK_GATEWAY_ENABLED = 'false'
$env:SPORTCENTER_ITP_E2E_MARKER = 'REPLACE_WITH_UNIQUE_MARKER'
$env:DB_USERNAME = Read-Host 'Clone SQL Server username'

function Set-PrivateProcessSecret([string]$Name) {
    $secure = Read-Host "Enter $Name" -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Environment]::SetEnvironmentVariable($Name, [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr), 'Process') }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

Set-PrivateProcessSecret 'DB_PASSWORD'
Set-PrivateProcessSecret 'JWT_SECRET'
$env:SPORTCENTER_ITP_E2E_COACH_EMAIL = Read-Host 'Marked Coach email'
Set-PrivateProcessSecret 'SPORTCENTER_ITP_E2E_COACH_PASSWORD'
$env:SPORTCENTER_ITP_E2E_MEMBER_EMAIL = Read-Host 'Marked eligible Member email'
Set-PrivateProcessSecret 'SPORTCENTER_ITP_E2E_MEMBER_PASSWORD'
$env:SPORTCENTER_ITP_E2E_OTHER_MEMBER_EMAIL = Read-Host 'Marked other Member email'
Set-PrivateProcessSecret 'SPORTCENTER_ITP_E2E_OTHER_MEMBER_PASSWORD'
$env:SPORTCENTER_ITP_E2E_UNASSIGNED_COACH_EMAIL = Read-Host 'Marked unassigned Coach email'
Set-PrivateProcessSecret 'SPORTCENTER_ITP_E2E_UNASSIGNED_COACH_PASSWORD'
$env:SPORTCENTER_ITP_E2E_EXPIRED_MEMBER_EMAIL = Read-Host 'Marked expired Member email'
Set-PrivateProcessSecret 'SPORTCENTER_ITP_E2E_EXPIRED_MEMBER_PASSWORD'

# First identity/config/schema gate. This test is read-only and must pass before fixture writes.
mvn.cmd "-Dtest=CloneDatasourceIdentityDiagnosticTest" test
if ($LASTEXITCODE -ne 0) { throw 'Clone datasource diagnostic failed; do not provision fixtures or run E2E.' }

# Verify/reuse the required marked fixtures using approved normal application workflows.
# If any are missing, stop. Do not use SQL, mock settlement, or backdate records.

# Recheck clone identity/configuration immediately before E2E.
mvn.cmd "-Dtest=CloneDatasourceIdentityDiagnosticTest" test
if ($LASTEXITCODE -ne 0) { throw 'Clone datasource recheck failed; E2E suite was not started.' }

mvn.cmd "-Dtest=IndividualTrainingPlanCloneE2EIT" test
if ($LASTEXITCODE -ne 0) { throw 'Individual Training Plan clone E2E failed.' }

Get-ChildItem 'target/surefire-reports/TEST-group5.example.sportCenter.e2e.CloneDatasourceIdentityDiagnosticTest.xml', `
    'target/surefire-reports/TEST-group5.example.sportCenter.e2e.IndividualTrainingPlanCloneE2EIT.xml' |
    Select-Object FullName, Length, LastWriteTime
```

The diagnostic inspects the actual Spring-injected `DataSource` and requires database `SportCenterPtE2E`, `SERVERPROPERTY('ServerName')` and `MachineName` equal to `sportcenter-recovery-sql`, default instance, ONLINE state, `ddl-auto=validate`, and absent scheduled-task registration infrastructure. `CloneDatasourceIdentityDiagnosticTest` performs no application writes. The E2E repeats this check in `@BeforeEach` before each test's requests. Both use MockMvc; no public HTTP listener starts.

The primary test is transactional: it creates and revises a plan through the application API, then `@AfterTransaction` verifies the plan row rolled back. SQL Server identity counters may advance. Its child revision/week/exercise/audit records are expected to join the transaction. The expired-history test is read-only. Do not run cleanup SQL; if rollback verification fails, inspect only the marked plan ID reported by the test.

## Results and manual review

Surefire reports are in `target/surefire-reports/`, including the two XML reports named above and matching `.txt` files. Automated MockMvc coverage is not browser E2E.

For optional browser review: sign in as the marked Coach, open the active class and create/edit a plan; verify revision history and duplicate rejection; confirm the unassigned Coach cannot access/edit it. Sign in as the active Member and verify read-only plan/history access. Sign in as the expired Member and verify historical reads remain available while Coach edits are rejected.
