import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "bootstrap/dist/css/bootstrap.min.css";
import "bootstrap-icons/font/bootstrap-icons.css";
import "./styles.css";
import "./tokens/admin-pilot.css";
import "./tokens/theme-foundation.css";
import { initializeThemeDocument } from "./themePreference.js";
import App from "./App.jsx";

initializeThemeDocument();

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
);
