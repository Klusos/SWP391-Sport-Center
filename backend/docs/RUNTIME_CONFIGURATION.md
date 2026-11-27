# Backend runtime configuration

## Normal local startup

The normal local datasource default is SQL Server `127.0.0.1:1433 / SportCenterDemo`, configured directly in `application.properties`. Teammates can override it using the standard `SPRING_DATASOURCE_URL` process environment variable or `--spring.datasource.url=...` launch argument. Explicit overrides take precedence over the default. The database username, password, and JWT signing key remain required in a local ignored `.env` or process environment.

From PowerShell:

```powershell
Set-Location <repository-path>\backend
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
.\mvnw.cmd spring-boot:run
```

The optional `.env` import is read relative to `backend/`. `.env.example` contains required secrets and conditional provider secrets only. Keep `.env` ignored and never commit it. Spring command-line arguments take precedence over process environment variables, which take precedence over imported `.env` and application defaults.

## Normal application defaults

`application.properties` owns ordinary configuration: application name and port, datasource default, Hibernate `validate`, upload limits, CORS defaults, PT session base price, token lifetime, VNPay as the owner-selected normal payment provider, mock gateway disabled, and scheduler settings. The approved scheduler defaults are:

```properties
sportcenter.scheduling.enabled=true
sportcenter.renewal.scheduler.enabled=true
sportcenter.pt.outcome-fallback.enabled=false
spring.jpa.hibernate.ddl-auto=validate
```

Membership expiry/cancellation and legacy lifecycle processing run daily at midnight in `Asia/Ho_Chi_Minh`. Renewal performs startup catch-up and then runs every 15 minutes in that timezone. Course registration expiry, PT booking expiry, and the disabled PT fallback sweep use 60,000 ms intervals. These intervals are declared in `application.properties`; the approved final-three-day Renewal window and exclusive Membership end-date rules still apply. PT fallback remains disabled pending explicit cutover approval.

## Temporary QA configuration

Do not edit the owner's `.env` or shared application defaults to run QA. Set per-process environment variables or launch arguments. For a QA process launched from `backend/`, select its target explicitly and disable all schedulers:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:sqlserver://127.0.0.1:1433;databaseName=EXPLICIT_QA_TARGET;encrypt=true;trustServerCertificate=true'
.\mvnw.cmd spring-boot:run `
  '--sportcenter.scheduling.enabled=false' `
  '--sportcenter.renewal.scheduler.enabled=false' `
  '--sportcenter.pt.outcome-fallback.enabled=false' `
  '--spring.jpa.hibernate.ddl-auto=validate'
```

Before any database mutation test, verify the live SQL Server identity, `DB_NAME()`, database ID, and state. A JDBC URL alone is not identity evidence. Never use the original database for QA writes. Test-specific configuration and guarded SQL Server harnesses must likewise select their target explicitly and disable schedulers.

Provider and profile overrides for payment testing must be explicit and process-local. Do not contact a real provider in synthetic QA. Mock settlement requires the demo/test profile, the mock provider, the mock gateway feature flag, and a local callback secret; use launch arguments or process environment variables for those overrides. The `demo` profile is only needed for the mock gateway and is not part of normal VNPay startup.

## Secrets and machine-specific values

Keep SQL credentials, JWT signing secret, payment callback secret, and VNPay merchant/hash credentials outside tracked files. Because the VNPay gateway validates its credentials during bean creation, both VNPay credentials must be present for normal startup while `vnpay` is selected. The datasource URL can use the local default or be overridden for another machine/database. CORS origins, image storage directory, and callback/return URLs remain optional machine/deployment overrides.

Hibernate `validate` checks entity mappings without changing schema. Apply schema changes only through separately reviewed, authorized SQL scripts.
