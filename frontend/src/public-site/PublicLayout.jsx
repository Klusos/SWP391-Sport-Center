import { useEffect } from "react";
import { Outlet, useLocation } from "react-router-dom";
import Footer from "./components/Footer";
import Header from "./components/Header";
import { usePublicTheme } from "./PublicThemeContext.jsx";

export default function PublicLayout({ auth, dashboardPath }) {
  const { hash } = useLocation();
  const { theme } = usePublicTheme();
  useEffect(() => {
    if (!hash) {
      window.scrollTo(0, 0);
      return undefined;
    }
    const target = document.getElementById(hash.slice(1));
    if (!target) {
      window.scrollTo(0, 0);
      return undefined;
    }
    const reducedMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    const frame = window.requestAnimationFrame(() => target.scrollIntoView({
      block: "start",
      behavior: reducedMotion ? "auto" : "smooth",
    }));
    return () => window.cancelAnimationFrame(frame);
  }, [hash]);

  return (
    <div className="public-site" data-theme={theme}>
      <Header auth={auth} dashboardPath={dashboardPath} />
      <main><Outlet /></main>
      <Footer auth={auth} dashboardPath={dashboardPath} />
    </div>
  );
}
