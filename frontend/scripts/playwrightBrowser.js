import process from "node:process";

export const DEFAULT_PLAYWRIGHT_BROWSER = "chromium";

export function resolvePlaywrightBrowser(value = process.env.PLAYWRIGHT_BROWSER) {
  const browser = String(value || DEFAULT_PLAYWRIGHT_BROWSER).trim().toLowerCase();
  if (!["chromium", "msedge"].includes(browser)) {
    throw new Error(`Unsupported Playwright browser "${browser}". Use chromium or msedge.`);
  }
  return browser;
}

export function playwrightLaunchOptions(browser, headless) {
  return browser === "msedge" ? { channel: "msedge", headless } : { headless };
}

export function playwrightUseOptions(browser) {
  return browser === "msedge" ? { channel: "msedge" } : {};
}
