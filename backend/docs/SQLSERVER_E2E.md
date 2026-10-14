# Phase 2 SQL Server HTTP E2E harness

CourseRegistrationSqlServerE2EIT is an opt-in Failsafe integration test. It uses a random HTTP port and the application's real security/JWT filters. Ordinary mvn test and mvn verify do not select *IT classes with Surefire, and Failsafe is configured only in the e2e-sqlserver Maven profile.

## Safety gates

Before Spring context refresh, the test initializer requires all of the following:

- Failsafe's profile property sportcenter.e2e.sqlserver.enabled=true.
- SPORTCENTER_E2E_SQLSERVER_CONFIRM=DESKTOP-EMK77PD/SportCenterDB.
- E2E_SQLSERVER_JDBC_URL exactly equals E2E_SQLSERVER_EXPECTED_JDBC_URL, and its parsed host/database are the approved SQL Server and database.
- A read-only JDBC query confirms @@SERVERNAME, DB_NAME() and ONLINE state.
- dbo.users.id=11 has the supplied expected email, run marker 20261014095644-80179052, role ADMIN and status ACTIVE.
- dbo.course_registration has the expected Phase 2 columns.

Any missing or mismatched condition aborts before Spring starts Hibernate, the HTTP server or scheduled callbacks. The test profile sets ddl-auto=validate, disables SQL display and uses only environment-supplied connection properties. Never point this profile at H2 or another database.

MembershipScheduler is replaced with a Mockito test bean in the E2E context. This prevents its production expiration callback from mutating unrelated memberships during this run; the test also asserts that the callback was not invoked.

## Credentials

Provide these as process environment variables in the approved terminal/session, without putting values in shell history, command arguments, source files or reports:

- E2E_SQLSERVER_JDBC_URL
- E2E_SQLSERVER_EXPECTED_JDBC_URL
- E2E_SQLSERVER_USERNAME
- E2E_SQLSERVER_PASSWORD
- E2E_SQLSERVER_JWT_SECRET
- E2E_ADMIN_EMAIL
- E2E_ADMIN_RUN_ID (must be 20261014095644-80179052)
- SPORTCENTER_E2E_SQLSERVER_CONFIRM (must be DESKTOP-EMK77PD/SportCenterDB)

The harness generates a fresh password for only user ID 11 and updates that row with a parameterized statement constrained by ID, expected email, ADMIN role and ACTIVE status. It verifies exactly one affected row and then authenticates through /api/auth/login. Generated account passwords and JWTs stay in test-process memory and are not logged. E2E fixture data is deliberately retained after the run.

## Later execution

Only after a separate explicit authorization to perform database writes, set the environment values in the approved process and invoke the cached Maven 3.9.16 installation directly (do not use mvnw or a PowerShell script):

mvn -Pe2e-sqlserver verify

This runs the ordinary H2 suite first, then Failsafe *IT classes. The test uses real HTTP requests for login, role checks, registration, duplicate and offering-level protection, capacity/concurrent last-seat behavior, schedule overlap/adjacency, member session visibility, membership pricing, and SQL read-back of registration snapshots.

The harness does not delete records. Its report lists the IDs returned by fixture-creation APIs. If a test fails after creating fixtures, preserve that report for later review; do not rerun until deciding whether the retained records affect a subsequent run.

## Important implementation boundary

Password reset is a write. The guard performs only SELECTs; the reset occurs only after the guard succeeds and the Spring test context has started with schema validation. Running the E2E profile is not authorized by ordinary test/build approval and must be explicitly authorized separately.
