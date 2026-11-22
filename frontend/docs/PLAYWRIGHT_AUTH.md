# Authenticated Playwright QA

The app stores the current identity, including its bearer access token and role, in `localStorage` under `sportcenter.auth` (`src/authStorage.js`). `src/api.js` sends that token in the `Authorization: Bearer` header. Cookies are not the app's authentication source. Playwright storage state captures cookies and localStorage, which covers this app's authentication; it does **not** capture sessionStorage. The app uses sessionStorage for page-specific data such as payment uncertainty/checkout state, not for login identity.

## Create or refresh a role session

Start the frontend and backend using the normal local workflow. In a terminal opened in `frontend/`, run one command per role:

```powershell
npm run auth:setup -- --role ADMIN
npm run auth:setup -- --role RECEPTIONIST
npm run auth:setup -- --role COACH
npm run auth:setup -- --role MEMBER
```

Each command opens a fresh browser context at the normal login page. Enter that role's credentials directly in the application UI. The script does not request, read, or print credentials. It checks that the resulting app identity has the requested role, visits that role's read-only dashboard, and requires an authenticated API response before saving. Setup fails without replacing the saved state if login is incomplete, the role differs, or validation cannot complete.

Playwright Chromium is the default. To use the installed Microsoft Edge channel instead, add `--browser msedge`:

```powershell
npm run auth:setup -- --role COACH --browser msedge
npm run auth:setup -- --role ADMIN --browser msedge
```

Confirm browser launch and reach the normal login page without entering credentials:

```powershell
npm run auth:smoke -- --browser msedge
```

The option also applies to saved-session validation:

```powershell
npm run auth:check -- --role COACH --browser msedge
```

States are written to `frontend/.playwright/auth/{admin,receptionist,coach,member}.json`. They contain bearer credentials and must remain private. This directory is Git-ignored. Do not copy its contents into issues, chat, test output, screenshots, or tracked files. Refresh a role by rerunning its command after expiry or account changes.

Check a saved session without opening a headed browser:

```powershell
npm run auth:check -- --role ADMIN
```

Use any of the four role names. A missing, expired, role-mismatched, or API-rejected session gives a role-specific reauthentication command. If the app or API is unreachable, the diagnostic distinguishes that from an invalid session.

## Use a role in an E2E test

Import `createRolePage` from `e2e/support/roleAuth.js` and create a new context for each role/test:

```js
const { context, page } = await createRolePage(browser, "COACH");
try {
  await page.goto("/coach/attendance");
  // Read-only browser assertions...
} finally {
  await context.close();
}
```

The helper validates the session on the role's dashboard before returning the page. Contexts never share in-memory cookies or storage. Public E2E tests remain unauthenticated and continue using the existing Playwright configuration. The existing `PLAYWRIGHT_STORAGE_STATE` path remains supported as a Coach-only compatibility fallback; prefer the role-specific files for new tests. `PLAYWRIGHT_BASE_URL` continues to override the frontend origin.

Select Microsoft Edge for the complete E2E suite with this PowerShell command:

```powershell
$env:PLAYWRIGHT_BROWSER = "msedge"
npm run test:e2e
Remove-Item Env:PLAYWRIGHT_BROWSER
```

Omit the variable or set it to `chromium` to use the bundled Playwright Chromium. The supported values are `chromium` and `msedge`; unsupported values fail with a diagnostic. Authentication setup, session validation, and E2E use the same `PLAYWRIGHT_BROWSER` setting when the setup command does not specify `--browser` explicitly.

Do not bypass normal login, frontend role guards, or backend authorization to create browser state. Never use state setup as a substitute for safe, read-only test data.
