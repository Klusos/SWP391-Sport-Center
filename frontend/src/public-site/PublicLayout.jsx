import { useEffect } from "react";
import { Outlet, useLocation } from "react-router-dom";
import Footer from "./components/Footer";
import Header from "./components/Header";

export default function PublicLayout() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  return (
    <div className="public-site">
      <Header />
      <main><Outlet /></main>
      <Footer />
    </div>
  );
}
