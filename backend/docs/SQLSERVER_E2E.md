# SQL Server HTTP E2E harness

`CourseRegistrationSqlServerE2EIT` is an opt-in Failsafe test for an isolated, disposable SQL Server instance. It performs real HTTP requests through the application and writes test fixtures. Ordinary Surefire tests do not select `*IT` classes; the harness runs only with `mvn -Pe2e-sqlserver verify`.

## Target requirements

The harness requires an explicit Failsafe profile, a separate destructive-test opt-in, explicit JDBC target values, and a read-only identity check before Spring starts. Configure all values in the approved process environment; do not put credentials in command arguments, shell history, source files, or reports.

- `SPORTCENTER_E2E_SQLSERVER_DESTRUCTIVE_OPT_IN=true`
- `E2E_SQLSERVER_JDBC_URL`
- `E2E_SQLSERVER_EXPECTED_JDBC_URL` (must exactly equal the configured URL)
- `E2E_SQLSERVER_EXPECTED_SERVER` (must equal SQL Server's `@@SERVERNAME`)
- `E2E_SQLSERVER_USERNAME`
- `E2E_SQLSERVER_PASSWORD`
- `E2E_SQLSERVER_JWT_SECRET`
- `E2E_ADMIN_EMAIL`
- `E2E_ADMIN_PASSWORD`
- `E2E_ADMIN_RUN_ID` (at least 8 characters and included in the test account email)

The JDBC URL must explicitly name `databaseName=SportCenterDB`, and the database must be `ONLINE`. The migrations retain their `SportCenterDB` name guard. Use a dedicated SQL Server instance and disposable database with that exact name; do not point the harness at a shared, production, or default local target. The guard rejects common local/default and production-like host names, but naming checks cannot prove that a server is disposable. The operator is responsible for provisioning and approving the isolated instance.

Before Spring refresh, the initializer verifies that the URL matches both configured URL values, the resolved datasource credentials and `ddl-auto=validate` match the test profile, the connected server/database identity matches the explicit expected identity, the database is online, the configured account is the unique ACTIVE ADMIN whose email carries the run marker, and the required registration schema columns exist. It performs SELECT-only checks. Missing, ambiguous, mismatched, or obviously unsafe target configuration stops the run before application startup.

Provision a dedicated test-owned ACTIVE ADMIN account for the run and provide its password through `E2E_ADMIN_PASSWORD`. The harness authenticates with that account and never resets or updates its password. Do not use an employee, administrator, or other shared account. Create a new unique run marker/account for each isolated database rehearsal.

## Writes, side effects, and cleanup

The destructive opt-in permits the application tests to write fixtures after the read-only guard succeeds. Scenarios create users, coaches, sports, courses, offerings, classes, sessions, registrations, membership plans, memberships, and payments. Fixtures are deliberately retained, and the report prints created record IDs. Failed tests can leave partial fixtures. The harness does not delete fixtures or restore the database. Keep a verified backup and restore plan, and prefer a fresh disposable database for each run.

Membership, CourseRegistration, and PT booking expiration services are replaced in the test context so their scheduled sweeps cannot change unrelated rows. The harness still starts the application with Hibernate schema validation and real HTTP/security handling. The configured database should contain only the schema and test-owned seed records required for the test.

The current harness uses the configured test-owned ADMIN account but creates other test users through the application's ADMIN APIs. It does not directly insert fixture users or modify preexisting account credentials. Fixture cleanup remains a manual database restore/recreation operation.

## Execution

Run only after separately authorizing writes to the named disposable target and verifying the environment values without printing them:

```text
mvn -Pe2e-sqlserver verify
```

Do not combine this profile with `-Dspring.profiles.active=phase1`; that would override the SQL Server profile. The run executes ordinary tests before Failsafe integration tests. Never use this harness against a production or shared database.

The test currently covers registration eligibility and role checks, duplicate/offering protection, last-seat concurrency, schedule overlap, member visibility, price snapshots, and COUNTER settlement. It does not exercise the Phase 8.8A/B receptionist queue or real VNPay callbacks. SQL Server migration behavior and provider E2E still require separate rehearsals.
