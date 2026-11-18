import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import {
  persistThemePreference,
  readSystemTheme,
  readThemePreference,
  resolvePublicTheme,
  resolveEffectiveTheme,
  subscribeToSystemTheme,
} from "../themePreference.js";

const ThemeContext = createContext(null);

export function ThemeProvider({ children }) {
  const { pathname } = useLocation();
  const [preference, setPreferenceState] = useState(readThemePreference);
  const [systemTheme, setSystemTheme] = useState(() => readSystemTheme());
  const theme = resolveEffectiveTheme(preference, systemTheme);
  const publicTheme = resolvePublicTheme(preference);
  const setPreference = useCallback((nextPreference) => {
    if (nextPreference === "light" || nextPreference === "dark" || nextPreference === "system") {
      persistThemePreference(nextPreference);
      setPreferenceState(nextPreference);
    }
  }, []);
  const toggleTheme = useCallback(() => {
    setPreference(preference === "dark" ? "light" : "dark");
  }, [preference, setPreference]);

  useEffect(() => {
    return subscribeToSystemTheme(setSystemTheme);
  }, []);

  useLayoutEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  useEffect(() => {
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) {
      if (!meta.dataset.publicOriginalColor) meta.dataset.publicOriginalColor = meta.content;
      const isPublicRoute = pathname === "/" || pathname === "/landing-preview" ||
        /^\/(?:bo-mon(?:\/[^/]+)?|khoa-hoc\/[^/]+|co-so-vat-chat|huan-luyen-vien|tuyen-sinh|membership-plans)\/?$/.test(pathname);
      meta.setAttribute("content", isPublicRoute
        ? (publicTheme === "dark" ? "#090b0f" : "#ffffff")
        : meta.dataset.publicOriginalColor);
    }
  }, [pathname, publicTheme]);

  const value = useMemo(() => ({
    preference,
    effectiveTheme: theme,
    theme,
    setPreference,
    setTheme: setPreference,
    toggleTheme,
  }), [preference, setPreference, theme, toggleTheme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export const PublicThemeProvider = ThemeProvider;

// Shared hooks are intentionally co-located with their provider context.
// eslint-disable-next-line react-refresh/only-export-components
export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error("useTheme must be used within ThemeProvider");
  return context;
}

// Keep the original Public hook available to existing callers.
// eslint-disable-next-line react-refresh/only-export-components
export function usePublicTheme() {
  const context = useTheme();
  const theme = resolvePublicTheme(context.preference);
  return { ...context, theme, effectiveTheme: theme, preference: theme };
}
