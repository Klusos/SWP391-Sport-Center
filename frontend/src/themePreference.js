export const THEME_PREFERENCE_KEY = "sc-theme-preference";
export const LEGACY_PUBLIC_THEME_KEY = "sc-landing-theme";

const PREFERENCES = new Set(["light", "dark", "system"]);

export function isThemePreference(value) {
  return PREFERENCES.has(value);
}

function getBrowserStorage() {
  try {
    return globalThis.localStorage;
  } catch {
    return null;
  }
}

function getBrowserMatchMedia() {
  try {
    return typeof globalThis.matchMedia === "function"
      ? globalThis.matchMedia.bind(globalThis)
      : null;
  } catch {
    return null;
  }
}

export function readThemePreference(storage = getBrowserStorage()) {
  if (!storage) return "light";

  try {
    const current = storage.getItem(THEME_PREFERENCE_KEY);
    if (isThemePreference(current)) return current;

    const legacy = storage.getItem(LEGACY_PUBLIC_THEME_KEY);
    if (legacy === "light" || legacy === "dark") {
      try {
        storage.setItem(THEME_PREFERENCE_KEY, legacy);
      } catch {
        // Keep the legacy value in memory when migration cannot be persisted.
      }
      return legacy;
    }
  } catch {
    // Storage can be disabled or unavailable; use the stable Light default.
  }

  return "light";
}

export function persistThemePreference(preference, storage = getBrowserStorage()) {
  if (!storage || !isThemePreference(preference)) return false;
  try {
    storage.setItem(THEME_PREFERENCE_KEY, preference);
    return true;
  } catch {
    return false;
  }
}

export function resolveEffectiveTheme(preference, systemTheme = "light") {
  if (preference === "dark") return "dark";
  if (preference === "system") return systemTheme === "dark" ? "dark" : "light";
  return "light";
}

export function resolvePublicTheme(globalPreference) {
  // Public follows an explicit Dark choice only; global System remains Light here.
  return globalPreference === "dark" ? "dark" : "light";
}

export function readSystemTheme(matchMedia = getBrowserMatchMedia()) {
  if (typeof matchMedia !== "function") return "light";
  try {
    return matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  } catch {
    return "light";
  }
}

export function subscribeToSystemTheme(onChange, matchMedia = getBrowserMatchMedia()) {
  if (typeof matchMedia !== "function") return () => {};
  let mediaQuery;
  try {
    mediaQuery = matchMedia("(prefers-color-scheme: dark)");
  } catch {
    return () => {};
  }
  if (!mediaQuery) return () => {};

  const handleChange = (event) => onChange(event.matches ? "dark" : "light");
  if (mediaQuery.addEventListener) {
    mediaQuery.addEventListener("change", handleChange);
    return () => mediaQuery.removeEventListener("change", handleChange);
  }
  mediaQuery.addListener?.(handleChange);
  return () => mediaQuery.removeListener?.(handleChange);
}

export function initializeThemeDocument({
  storage = getBrowserStorage(),
  matchMedia = getBrowserMatchMedia(),
  documentRef = globalThis.document,
} = {}) {
  const preference = readThemePreference(storage);
  const effectiveTheme = resolveEffectiveTheme(preference, readSystemTheme(matchMedia));
  try {
    if (documentRef?.documentElement) documentRef.documentElement.dataset.theme = effectiveTheme;
  } catch {
    // Theme state still initializes even when the document cannot be updated.
  }
  return { preference, effectiveTheme };
}
