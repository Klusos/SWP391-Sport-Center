import { access } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const authDirectory = path.join(frontendRoot, ".playwright", "auth");
const safePages = {
  ADMIN: "/staff/dashboard",
  RECEPTIONIST: "/staff/dashboard",
  COACH: "/coach/dashboard",
  MEMBER: "/member/dashboard",
};

export function roleStatePath(role) {
  return path.join(authDirectory, `${String(role).toLowerCase()}.json`);
}

export function roleSetupCommand(role) {
  return `npm run auth:setup -- --role ${role}`;
}

export async function hasRoleState(role) {
  const candidates = [roleStatePath(role)];
  if (role === "COACH" && process.env.PLAYWRIGHT_STORAGE_STATE) candidates.push(process.env.PLAYWRIGHT_STORAGE_STATE);
  for (const candidate of candidates) {
    try {
      await access(candidate);
      return true;
    } catch {
      // Try the next supported state location.
    }
  }
  return false;
}

export async function validateRoleSession(page, role) {
  const authenticatedApiStatuses = [];
  page.on("response", async (response) => {
    if (!response.url().includes("/api/")) return;
    try {
      const headers = await response.request().allHeaders();
      if (headers.authorization?.startsWith("Bearer ")) authenticatedApiStatuses.push(response.status());
    } catch {
      // A request that cannot be inspected does not count as session validation.
    }
  });

  await page.goto(safePages[role], { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(250);

  const auth = await page.evaluate(() => {
    try {
      const value = JSON.parse(localStorage.getItem("sportcenter.auth") || "null");
      return { role: value?.role || null, hasToken: typeof value?.accessToken === "string" && value.accessToken.length > 0 };
    } catch {
      return { role: null, hasToken: false };
    }
  });

  if (auth.role !== role || !auth.hasToken || new URL(page.url()).pathname === "/login") {
    throw new Error(`Saved ${role} session is missing, expired, or invalid. Reauthenticate with: ${roleSetupCommand(role)}`);
  }
  if (authenticatedApiStatuses.includes(401)) {
    throw new Error(`Saved ${role} session was rejected by the API (401). Reauthenticate with: ${roleSetupCommand(role)}`);
  }
  if (authenticatedApiStatuses.length === 0) {
    throw new Error(`Could not validate the ${role} session against an authenticated API on ${safePages[role]}. Check that the API is running, then retry ${roleSetupCommand(role)}.`);
  }
}

export async function createRolePage(browser, role, contextOptions = {}) {
  let storageState = roleStatePath(role);
  try {
    await access(storageState);
  } catch {
    if (role === "COACH" && process.env.PLAYWRIGHT_STORAGE_STATE) storageState = process.env.PLAYWRIGHT_STORAGE_STATE;
    else throw new Error(`No saved session for ${role}. Sign in interactively with: ${roleSetupCommand(role)}`);
  }

  const context = await browser.newContext({
    baseURL: process.env.PLAYWRIGHT_BASE_URL || "http://localhost:5173",
    ...contextOptions,
    storageState,
  });
  const page = await context.newPage();
  try {
    await validateRoleSession(page, role);
    return { context, page };
  } catch (error) {
    await context.close();
    throw error;
  }
}
