import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  initializeThemeDocument,
  LEGACY_PUBLIC_THEME_KEY,
  persistThemePreference,
  readSystemTheme,
  readThemePreference,
  resolveEffectiveTheme,
  resolvePublicTheme,
  subscribeToSystemTheme,
  THEME_PREFERENCE_KEY,
} from "../src/themePreference.js";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

function createStorage(entries = {}, { failReads = false, failWrites = false } = {}) {
  const values = new Map(Object.entries(entries));
  return {
    values,
    getItem(key) {
      if (failReads) throw new Error("storage read blocked");
      return values.get(key) ?? null;
    },
    setItem(key, value) {
      if (failWrites) throw new Error("storage write blocked");
      values.set(key, String(value));
    },
  };
}

test("global preference validates values, defaults to Light, and survives initialization", () => {
  assert.equal(readThemePreference(createStorage()), "light");
  assert.equal(readThemePreference(createStorage({ [THEME_PREFERENCE_KEY]: "light" })), "light");
  assert.equal(readThemePreference(createStorage({ [THEME_PREFERENCE_KEY]: "dark" })), "dark");
  assert.equal(readThemePreference(createStorage({ [THEME_PREFERENCE_KEY]: "system" })), "system");
  assert.equal(readThemePreference(createStorage({ [THEME_PREFERENCE_KEY]: "corrupt" })), "light");

  const storage = createStorage();
  assert.equal(persistThemePreference("dark", storage), true);
  assert.equal(readThemePreference(storage), "dark");
  assert.equal(persistThemePreference("sepia", storage), false);
});

test("legacy Public Light and Dark preferences migrate unless a valid global value exists", () => {
  for (const legacy of ["light", "dark"]) {
    const storage = createStorage({ [LEGACY_PUBLIC_THEME_KEY]: legacy });
    assert.equal(readThemePreference(storage), legacy);
    assert.equal(storage.values.get(THEME_PREFERENCE_KEY), legacy);
    assert.equal(storage.values.get(LEGACY_PUBLIC_THEME_KEY), legacy);
  }

  const preferred = createStorage({
    [THEME_PREFERENCE_KEY]: "system",
    [LEGACY_PUBLIC_THEME_KEY]: "dark",
  });
  assert.equal(readThemePreference(preferred), "system");
  assert.equal(preferred.values.get(LEGACY_PUBLIC_THEME_KEY), "dark");
});

test("storage failures fall back safely and do not prevent in-memory preference use", () => {
  assert.equal(readThemePreference(createStorage({}, { failReads: true })), "light");
  assert.equal(readThemePreference(createStorage({ [LEGACY_PUBLIC_THEME_KEY]: "dark" }, { failWrites: true })), "dark");
  assert.equal(persistThemePreference("dark", createStorage({}, { failWrites: true })), false);
});

test("effective theme follows System and explicit choices override the OS", () => {
  assert.equal(resolveEffectiveTheme("light", "dark"), "light");
  assert.equal(resolveEffectiveTheme("dark", "light"), "dark");
  assert.equal(resolveEffectiveTheme("system", "dark"), "dark");
  assert.equal(resolveEffectiveTheme("system", "light"), "light");
  assert.equal(resolveEffectiveTheme("system", "unavailable"), "light");
});

test("Public resolves global System to Light but honors explicit Light and Dark", () => {
  assert.equal(resolvePublicTheme("light"), "light");
  assert.equal(resolvePublicTheme("dark"), "dark");
  assert.equal(resolvePublicTheme("system"), "light");
});

test("system preference listener follows changes and removes its listener", () => {
  let listener;
  let removed;
  const media = {
    addEventListener(type, callback) { assert.equal(type, "change"); listener = callback; },
    removeEventListener(type, callback) { removed = type === "change" && callback === listener; },
  };
  const values = [];
  const cleanup = subscribeToSystemTheme((value) => values.push(value), () => media);
  listener({ matches: true });
  listener({ matches: false });
  assert.deepEqual(values, ["dark", "light"]);
  cleanup();
  assert.equal(removed, true);

  let legacyRemoved = false;
  const legacyMedia = {
    addListener(callback) { listener = callback; },
    removeListener(callback) { legacyRemoved = callback === listener; },
  };
  subscribeToSystemTheme(() => {}, () => legacyMedia)();
  assert.equal(legacyRemoved, true);
  assert.doesNotThrow(() => subscribeToSystemTheme(() => {}, undefined));
});

test("missing or failing matchMedia safely resolves System to Light", () => {
  assert.equal(readSystemTheme(() => undefined), "light");
  assert.equal(readSystemTheme(() => { throw new Error("matchMedia unavailable"); }), "light");
  assert.doesNotThrow(() => subscribeToSystemTheme(() => {}, () => { throw new Error("unsupported"); }));
});

test("pre-render initialization applies the effective root theme without coupling to a role", () => {
  const documentRef = { documentElement: { dataset: {} } };
  const storage = createStorage({ [THEME_PREFERENCE_KEY]: "system" });
  const initialized = initializeThemeDocument({
    storage,
    matchMedia: () => ({ matches: true }),
    documentRef,
  });
  assert.deepEqual(initialized, { preference: "system", effectiveTheme: "dark" });
  assert.equal(documentRef.documentElement.dataset.theme, "dark");
  assert.equal(storage.values.get(THEME_PREFERENCE_KEY), "system");
});

test("one app-level provider serves Public and portal routes while Public CSS remains scoped", async () => {
  const [app, main, provider, preferences, tokens, publicStyles, layout, landing] = await Promise.all([
    read("../src/App.jsx"),
    read("../src/main.jsx"),
    read("../src/public-site/PublicThemeContext.jsx"),
    read("../src/themePreference.js"),
    read("../src/public-site/publicTheme.css"),
    read("../src/public-site/styles.css"),
    read("../src/public-site/PublicLayout.jsx"),
    read("../src/landing/LandingPage.jsx"),
  ]);
  assert.match(app, /<ThemeProvider>/);
  assert.equal((app.match(/<NotificationBell\b/g) || []).length, 1);
  assert.match(main, /initializeThemeDocument\(\)/);
  assert.match(provider, /readThemePreference/);
  assert.match(provider, /resolveEffectiveTheme/);
  assert.match(provider, /subscribeToSystemTheme/);
  assert.match(provider, /export function useTheme\(\)/);
  assert.match(provider, /const publicTheme = resolvePublicTheme\(preference\)/);
  assert.match(provider, /const theme = resolvePublicTheme\(context\.preference\)/);
  assert.match(preferences, /matchMedia\("\(prefers-color-scheme: dark\)"\)/);
  assert.match(provider, /export const PublicThemeProvider = ThemeProvider/);
  assert.match(provider, /preference,\s*effectiveTheme:/);
  assert.match(app, /<ThemeProvider>[\s\S]*?<Routes key=\{auth\?\.accessToken/);
  assert.match(layout, /usePublicTheme\(\)/);
  assert.match(landing, /usePublicTheme\(\)/);
  assert.match(layout, /data-theme=\{theme\}/);
  assert.match(landing, /data-theme=\{theme\}/);
  assert.match(tokens, /\.lp,\s*\.public-site/);
  assert.match(tokens, /\.lp\[data-theme="dark"\],\s*\.public-site\[data-theme="dark"\]/);
  assert.match(publicStyles, /\.public-site\[data-theme="dark"\]/);
  assert.doesNotMatch(tokens, /\.layout|\.sidebar|\.member-course-detail/);
});

test("Public keeps its compact manual Light/Dark controls and exposes no System choice", async () => {
  const [header, landing, context] = await Promise.all([
    read("../src/public-site/components/Header.jsx"),
    read("../src/landing/LandingPage.jsx"),
    read("../src/public-site/PublicThemeContext.jsx"),
  ]);
  const navbarStart = landing.indexOf("function Navbar(");
  const navbarEnd = landing.indexOf("\nfunction Hero()", navbarStart);
  const navbar = landing.slice(navbarStart, navbarEnd);
  assert.match(header, /className="public-icon-button public-theme-toggle"/);
  assert.match(header, /onClick=\{toggleTheme\}/);
  assert.match(header, /theme === "dark" \? Sun : Moon/);
  assert.match(navbar, /className="lp-icon-btn"/);
  assert.match(navbar, /onClick=\{onToggleTheme\}/);
  assert.match(navbar, /theme === "dark" \? Sun : Moon/);
  assert.match(context, /setPreference\(preference === "dark" \? "light" : "dark"\)/);
  assert.doesNotMatch(header, /Theo hệ thống|value="system"/);
  assert.doesNotMatch(navbar, /Theo hệ thống|value="system"/);
});

test("Public Dark selectors remain scoped away from portal and preserve existing palette", async () => {
  const [tokens, styles, courseStyles, portalTokens] = await Promise.all([
    read("../src/public-site/publicTheme.css"),
    read("../src/public-site/styles.css"),
    read("../src/public-site/pages/CourseDetail.css"),
    read("../src/tokens/admin-pilot.css"),
  ]);
  assert.match(styles, /\.public-site\[data-theme="dark"\] \.disciplines-sport-image-wrap/);
  assert.match(styles, /\.public-site \.course-public-detail \.course-detail-card/);
  assert.match(courseStyles, /\.member-course-detail/);
  assert.match(tokens, /--sc-public-page:\s*#090b0f/);
  assert.match(tokens, /--sc-public-nav:\s*#111318/);
  assert.match(tokens, /--sc-public-surface:\s*#171c25/);
  assert.doesNotMatch(portalTokens, /data-theme="dark"|prefers-color-scheme/);
});
