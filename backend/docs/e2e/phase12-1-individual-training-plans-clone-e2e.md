# Phase 12.1 Individual Training Plan clone E2E

This is an owner-run test against the approved disposable clone only. It does not migrate or provision fixtures. The clone must already contain the Phase 12.1 schema and owner-designated test accounts/data.

## Required pre-provisioned fixtures

Choose one unique marker (6–40 letters, digits, `_` or `-`) and ensure fixture names use it exactly:

- Coach display name contains the marker and the Coach is assigned to exactly one test class named `<marker>-ITP-ACTIVE`.
- Member display name contains the marker, has a `CONFIRMED` registration in that class, and has a currently effective `ACTIVE` Membership whose plan has `allowPersonalTrainingPlan=true`.
- The marked active class/member pair has no individual plan yet.
- Other Member display name contains the marker and does not own the test plan.
- Unassigned Coach display name contains the marker and is not assigned to the active class.
- Expired Member display name contains the marker, has no currently effective qualifying Membership, and already has a historical plan in a class named `<marker>-ITP-EXPIRED`. The Coach account used above must be assigned to that class.

Use valid existing accounts and passwords. Do not create fixtures by direct SQL or fabricate registration/payment settlement. Missing or ambiguous fixtures fail the test before it writes a plan. The test creates no accounts, Memberships, classes or registrations.

## Run from PowerShell

Open a normal PowerShell terminal. Password values are prompted as secure input and are kept only in that process environment; do not paste them into a command line or commit them.

```powershell
Set-Location 'E:\00Project\SWP391-Sport-Center\backend'

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
mvn.cmd -version

$env:SPRING_DATASOURCE_URL = 'jdbc:sqlserver://127.0.0.1:14333;databaseName=SportCenterPtE2E;encrypt=true;trustServerCertificate=true'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:SPORTCENTER_SCHEDULING_ENABLED = 'false'
$env:SPORTCENTER_ITP_E2E_MARKER = 'REPLACE_WITH_UNIQUE_MARKER'

function Set-PrivateE2EPassword([string]$Name) {
    $secure = Read-Host "Password for $Name" -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Environment]::SetEnvironmentVariable($Name, [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr), 'Process') }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

$env:SPORTCENTER_ITP_E2E_COACH_EMAIL = Read-Host 'Marked Coach email'
Set-PrivateE2EPassword 'SPORTCENTER_ITP_E2E_COACH_PASSWORD'
$env:SPORTCENTER_ITP_E2E_MEMBER_EMAIL = Read-Host 'Marked eligible Member email'
Set-PrivateE2EPassword 'SPORTCENTER_ITP_E2E_MEMBER_PASSWORD'
$env:SPORTCENTER_ITP_E2E_OTHER_MEMBER_EMAIL = Read-Host 'Marked other Member email'
Set-PrivateE2EPassword 'SPORTCENTER_ITP_E2E_OTHER_MEMBER_PASSWORD'
$env:SPORTCENTER_ITP_E2E_UNASSIGNED_COACH_EMAIL = Read-Host 'Marked unassigned Coach email'
Set-PrivateE2EPassword 'SPORTCENTER_ITP_E2E_UNASSIGNED_COACH_PASSWORD'
$env:SPORTCENTER_ITP_E2E_EXPIRED_MEMBER_EMAIL = Read-Host 'Marked expired Member email'
Set-PrivateE2EPassword 'SPORTCENTER_ITP_E2E_EXPIRED_MEMBER_PASSWORD'

# Stop here unless the clone and Phase 12.1 schema are already approved and ready.
mvn.cmd "-Dtest=CloneDatasourceIdentityDiagnosticTest" test
if ($LASTEXITCODE -ne 0) { throw 'Clone datasource diagnostic failed; E2E suite was not started.' }

mvn.cmd "-Dtest=IndividualTrainingPlanCloneE2EIT" test
if ($LASTEXITCODE -ne 0) { throw 'Individual Training Plan clone E2E failed.' }
```

The diagnostic uses the actual Spring-injected `DataSource`, requires the exact clone server/database identity, checks Hibernate `ddl-auto=validate`, and asserts scheduled-task registration infrastructure is absent. The E2E test repeats those checks immediately before its test requests. Both use a servlet-capable MockMvc context; no public HTTP listener starts.

## Writes and cleanup

The main workflow creates and revises one plan through authenticated application endpoints. The test method is transaction-scoped and checks after rollback that the created plan row is absent. Its revisions, weeks, exercises and audit rows are expected to participate in the same transaction. SQL Server identity counters may advance despite rollback. No fixture cleanup SQL is used. If rollback verification fails, stop and inspect only the marked plan ID reported by the test; do not run broad cleanup.

## Results

Surefire reports are under `backend/target/surefire-reports/`, including `TEST-group5.example.sportCenter.e2e.CloneDatasourceIdentityDiagnosticTest.xml` and `TEST-group5.example.sportCenter.e2e.IndividualTrainingPlanCloneE2EIT.xml`, plus matching `.txt` reports.

## Manual Coach → Member browser checklist

1. Sign in as the marked Coach, open the marked assigned class, and confirm the eligible Member is listed.
2. Create a plan with a goal, a week and an exercise; verify the saved detail.
3. Edit the plan; verify the newest revision shows the edit and history retains the original.
4. Confirm duplicate creation is rejected and an unassigned Coach cannot manage the plan.
5. Sign in as the Member; verify the plan, weeks, exercises and revisions are readable, with no edit controls.
6. Sign in as the expired Member; verify the historical plan remains readable while Coach editing is rejected.

Automated MockMvc coverage is not browser E2E. Do not report browser verification as passed unless performed separately in a browser.
