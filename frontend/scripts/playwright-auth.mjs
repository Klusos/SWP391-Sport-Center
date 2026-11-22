import { mkdir, access, chmod } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import { chromium } from "@playwright/test";
import { roleSetupCommand, roleStatePath, validateRoleSession } from "../e2e/support/roleAuth.js";
import { playwrightLaunchOptions, resolvePlaywrightBrowser } from "./playwrightBrowser.js";

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const authDirectory = path.join(frontendRoot, ".playwright", "auth");
const roles = new Set(["ADMIN", "RECEPTIONIST", "COACH", "MEMBER"]);

function parseRole(args) {
  const roleIndex = args.indexOf("--role");
  const role = roleIndex >= 0 ? String(args[roleIndex + 1] || "").toUpperCase() : "";
  if (!roles.has(role)) throw new Error("Specify one role: --role ADMIN|RECEPTIONIST|COACH|MEMBER.");
  return role;
}

function parseBrowser(args) {
  const browserIndex = args.indexOf("--browser");
  if (browserIndex >= 0 && !args[browserIndex + 1]) throw new Error("Specify a browser after --browser: chromium or msedge.");
  const browser = resolvePlaywrightBrowser(browserIndex >= 0 ? args[browserIndex + 1] : undefined);
  return browser;
}

async function smokeBrowser(browserName) {
  const browser = await chromium.launch(playwrightLaunchOptions(browserName, true));
  const context = await browser.newContext({ baseURL: process.env.PLAYWRIGHT_BASE_URL || "http://localhost:5173" });
  try {
    const page = await context.newPage();
    await page.goto("/login", { waitUntil: "domcontentloaded" });
    await page.getByRole("heading", { name: "Đăng nhập" }).waitFor({ state: "visible", timeout: 10000 });
    console.log(`Opened the normal login page in ${browserName === "msedge" ? "Microsoft Edge" : "Playwright Chromium"}. No credentials were entered.`);
  } finally {
    await context.close();
    await browser.close();
  }
}

async function setupRole(role, browserName) {
  const browser = await chromium.launch(playwrightLaunchOptions(browserName, false));
  const context = await browser.newContext({ baseURL: process.env.PLAYWRIGHT_BASE_URL || "http://localhost:5173" });
  const page = await context.newPage();

  try {
    await page.goto("/login", { waitUntil: "domcontentloaded" });
    console.log(`Opened the normal login page in ${browserName === "msedge" ? "Microsoft Edge" : "Playwright Chromium"}.`);
    console.log(`Complete the normal login in the opened browser using the ${role} account. Credentials are entered only in the application and are not read or logged by this script.`);
    await page.waitForFunction(() => {
      try {
        const value = JSON.parse(localStorage.getItem("sportcenter.auth") || "null");
        return typeof value?.accessToken === "string" && value.accessToken.length > 0 && typeof value?.role === "string";
      } catch {
        return false;
      }
    }, null, { timeout: 5 * 60 * 1000 }).catch(() => {
      throw new Error(`No completed login detected for ${role} within five minutes. Nothing was saved.`);
    });

    const actualRole = await page.evaluate(() => {
      try { return JSON.parse(localStorage.getItem("sportcenter.auth") || "null")?.role || null; } catch { return null; }
    });
    if (actualRole !== role) throw new Error(`The signed-in account is ${actualRole || "unknown"}, but ${role} was requested. Nothing was saved.`);

    await validateRoleSession(page, role);
    await mkdir(authDirectory, { recursive: true });
    const destination = roleStatePath(role);
    await context.storageState({ path: destination });
    await chmod(destination, 0o600).catch(() => {});
    console.log(`${role} session validated and saved locally. Keep this browser state private; refresh with ${roleSetupCommand(role)}.`);
  } finally {
    await context.close();
    await browser.close();
  }
}

async function checkRole(role, browserName) {
  const destination = roleStatePath(role);
  try {
    await access(destination);
  } catch {
    throw new Error(`No saved session for ${role}. Sign in interactively with: ${roleSetupCommand(role)}`);
  }

  const browser = await chromium.launch(playwrightLaunchOptions(browserName, true));
  const context = await browser.newContext({
    baseURL: process.env.PLAYWRIGHT_BASE_URL || "http://localhost:5173",
    storageState: destination,
  });
  try {
    await validateRoleSession(await context.newPage(), role);
    console.log(`${role} session is valid on its read-only dashboard page.`);
  } finally {
    await context.close();
    await browser.close();
  }
}

async function main() {
  const [command, ...args] = process.argv.slice(2);
  const browser = parseBrowser(args);
  if (command === "smoke") await smokeBrowser(browser);
  else {
    const role = parseRole(args);
    if (command === "setup") await setupRole(role, browser);
    else if (command === "check") await checkRole(role, browser);
    else throw new Error("Use `setup`, `check`, or `smoke`; setup/check require --role ROLE.");
  }
}

main().catch((error) => {
  // Only fixed diagnostics are printed; browser storage and error payloads are never included.
  console.error(error instanceof Error ? error.message : "Playwright authentication setup failed.");
  process.exitCode = 1;
});
