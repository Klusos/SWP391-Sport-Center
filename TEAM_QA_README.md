# SportCenter Team QA Handoff

## Branch

- Branch: `qa/team-integration-20261122`
- Commit: record the final commit SHA with `git rev-parse HEAD` after checkout.

## Prerequisites

- JDK 21
- Node.js compatible with Vite 8 (Node 20.19+ or 22.12+)
- SQL Server only when testing against a locally provisioned database containing the required schema
- Maven and npm dependencies available locally

## Configure a local database

Never start the backend with the repository default datasource unless that target is intentional. The checked-in default points to `SportCenterDemo`. Select a teammate-owned local database explicitly using environment overrides, for example `SPRING_DATASOURCE_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Set Hibernate to `validate` (`SPRING_JPA_HIBERNATE_DDL_AUTO=validate`) and keep `sportcenter.renewal.scheduler.enabled=false`. Use the schema setup procedure approved for your local test database; this QA branch does not execute migrations automatically.

The backend imports `backend/.env` when started from `backend/`. Keep that file local and untracked. Relevant variable names include `SPRING_DATASOURCE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `PAYMENT_PROVIDER`, `PAYMENT_MOCK_GATEWAY_ENABLED`, `PAYMENT_CALLBACK_SECRET`, `VNPAY_TMN_CODE`, and `VNPAY_HASH_SECRET`. Use mock payment configuration for team tests. Never put values in source control, screenshots, logs, or bug reports.

## Start the applications

In one terminal, from `backend/`, after confirming the datasource target:

```powershell
mvn spring-boot:run
```

In another terminal, from `frontend/`:

```powershell
npm run dev
```

The frontend API base defaults to `http://localhost:8080`; set `VITE_API_BASE_URL` when the backend uses another local address.

## Verification commands

From `frontend/`:

```powershell
npm run lint
npm test
npm run build
```

From `backend/`, focused H2 tests can be run with the project Maven setup. SQL Server `*IT` tests are separate and must only be run after verifying their exact target and reviewing whether they write fixtures. Do not run the broad SQL Server Failsafe profile against a shared or production database.

Playwright authenticated Renewal tests require valid local role accounts, a locally generated fixture manifest, and test-only callback configuration. The test callback is simulated; it is not a VNPay sandbox transaction. Browser Return alone does not settle a Renewal. Do not commit fixture manifests, credentials, storage state, or tokens.

## Role smoke checklist

- **ADMIN:** sign in; check dashboard, User Management, Membership Plans, Upgrade Paths, and Course Management.
- **RECEPTIONIST:** check Membership Sales, member lookup, Purchase/Upgrade views, Renewal Intent, and COUNTER flow where eligible local test data exists.
- **MEMBER:** check Membership/Renewal quote, payment attempt, pending Return behavior, and course navigation.
- **COACH:** check dashboard, Attendance, Training Results, and Personal Training navigation.

Use accounts provisioned for the local test database. Do not reuse shared credentials or copy them into tracked files.

## Payment limitations and test data

Use the mock gateway and controlled simulated IPN only. No real VNPay transaction is part of this handoff. Renewal Intent creation remains scheduler-owned; keep the scheduler disabled for ordinary local startup and use only the approved test fixture procedure when an authenticated Renewal browser test is needed. Settled Intents cannot be reused; create fresh, uniquely marked fixtures through the guarded test setup and clean up only records owned by that run.

The complete browser workflow depends on local authentication and fixture setup and is not run by `npm test`. Do not infer SQL Server concurrency guarantees from H2 tests.

## Reporting a bug

Include the branch and commit SHA, role, exact steps, expected and actual result, approximate timestamp, request URL/status where safe, and a screenshot with personal data removed. Attach relevant redacted frontend/backend logs. Never include passwords, JWTs, signing secrets, `.env` contents, or full payment payloads.
