import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import postcss from "postcss";
import { publicCssIsolation } from "../vite.config.js";

const publicRoot = new URL("../src/public-site/", import.meta.url);
const readPublic = (path) => readFile(new URL(path, publicRoot), "utf8");

test("canonical Landing and legacy page URLs redirect to their intended Landing sections", async () => {
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const publicRoutes = app.split('<Route element={<PublicLayout')[1]?.split("</Route>")[0] || "";

  assert.match(app, /<Route path="\/" element=\{landingRoute\} \/>/);
  assert.match(app, /path="\/landing-preview"[\s\S]*?pathname: "\/", search: location\.search, hash: location\.hash/);
  assert.match(publicRoutes, /path="\/huan-luyen-vien" element=\{<LandingSectionRedirect sectionId="huan-luyen-vien" \/>\}/);
  assert.match(publicRoutes, /path="\/tuyen-sinh" element=\{<LandingSectionRedirect sectionId="tuyen-sinh" \/>\}/);
  assert.match(publicRoutes, /path="\/membership-plans"\s+element=\{<LandingSectionRedirect sectionId="tuyen-sinh" \/>\}/);
  assert.match(app, /function LandingSectionRedirect\(\{ sectionId \}\)[\s\S]*?search: location\.search, hash: `#\$\{sectionId\}`/);
});

test("remaining Sports, Facilities and Course routes preserve their components and Member redirect", async () => {
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const publicRoutes = app.split('<Route element={<PublicLayout')[1]?.split("</Route>")[0] || "";
  assert.match(publicRoutes, /path="\/bo-mon" element=\{<Disciplines \/>\} \/>/);
  assert.match(publicRoutes, /path="\/bo-mon\/:sportId" element=\{<Disciplines \/>\} \/>/);
  assert.match(publicRoutes, /path="\/co-so-vat-chat" element=\{<Facilities \/>\} \/>/);
  assert.match(publicRoutes, /path="\/khoa-hoc\/:courseId"[\s\S]*?auth\?\.role === "MEMBER"[\s\S]*?`\/member\/courses\/\$\{location\.pathname\.split\("\/"\)\.at\(-1\)\}`/);
  assert.match(app, /path="\/login"/);
});

test("retired page components are no longer imported and Landing owns API-backed Coaches and Membership", async () => {
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const landing = await readFile(new URL("../src/landing/LandingPage.jsx", import.meta.url), "utf8");
  assert.doesNotMatch(app, /pages\/(?:Admission|Coaches)/);
  assert.match(landing, /getPublicCoachesApi\(\)/);
  assert.match(landing, /getPublicMembershipPlansApi\(\)/);
  assert.match(landing, /getActiveLandingPlans\(plans\)/);
  assert.match(landing, /catalog\.coaches/);
  assert.match(landing, /<ConsultForm showFieldPlaceholders=\{false\} \/>/);
});

test("Membership actions distinguish Members, Guests, and authenticated staff", async () => {
  const landing = await readFile(new URL("../src/landing/LandingPage.jsx", import.meta.url), "utf8");
  const card = landing.match(/<article className="lp-plan-card"[\s\S]*?<\/article>/)?.[0] || "";
  assert.match(card, /auth\?\.role === "MEMBER"[\s\S]*?to="\/member\/membership"/);
  assert.match(card, /!auth[\s\S]*?to="\/#public-consultation"/);
  assert.doesNotMatch(card, /to="\/tuyen-sinh"/);
});

test("Public header stays compact while legacy Public links target the relevant Landing sections", async () => {
  const nav = await readPublic("data/content.js");
  const footer = await readPublic("components/Footer.jsx");
  const cta = await readPublic("components/PublicConsultationCta.jsx");
  const facilities = await readPublic("pages/Facilities.jsx");
  const course = await readPublic("pages/CourseDetail.jsx");
  assert.deepEqual([...nav.matchAll(/\{ to: '([^']+)', label: '([^']+)' \}/g)].map(([, to, label]) => [to, label]), [
    ["/", "Trang chủ"], ["/bo-mon", "Bộ môn"], ["/co-so-vat-chat", "Cơ sở vật chất"],
  ]);
  assert.match(footer, /to="\/#huan-luyen-vien"/);
  assert.match(footer, /to="\/#tuyen-sinh">Hội viên/);
  assert.match(footer, /to="\/#public-consultation">Tư vấn/);
  assert.match(cta, /to="\/#public-consultation"/);
  assert.match(course, /to="\/#public-consultation" className="btn btn-cyan course-public-action-primary"/);
  assert.match(facilities, /to="\/#tuyen-sinh" className="link-arrow">Tìm hiểu hội viên/);
});

test("Landing consultation form remains singular and uses the existing API and fields", async () => {
  const landing = await readFile(new URL("../src/landing/LandingPage.jsx", import.meta.url), "utf8");
  const layout = await readPublic("PublicLayout.jsx");
  const form = await readPublic("components/ConsultForm.jsx");
  assert.equal((landing.match(/<ConsultForm showFieldPlaceholders=\{false\} \/>/g) || []).length, 1);
  assert.doesNotMatch(layout, /ConsultForm|public-consultation/);
  assert.match(form, /createPublicConsultationRequestApi\(/);
  assert.match(form, /normalizeConsultationEmail\(form\.email\)/);
  for (const field of ["name", "phone", "email", "sportId", "age", "experience", "note"]) {
    assert.match(form, new RegExp(`name="${field}"`));
  }
});

test("Public CSS remains isolated and the Landing and Public shell share the persisted theme", async () => {
  const stylesheetPath = fileURLToPath(new URL("../src/public-site/styles.css", import.meta.url));
  const stylesheet = await readFile(stylesheetPath, "utf8");
  const compiled = await postcss([publicCssIsolation()]).process(stylesheet, { from: stylesheetPath });
  const landing = await readFile(new URL("../src/landing/LandingPage.jsx", import.meta.url), "utf8");
  const landingCss = await readFile(new URL("../src/landing/landing.css", import.meta.url), "utf8");
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const theme = await readPublic("PublicThemeContext.jsx");
  const themeCss = await readPublic("publicTheme.css");
  assert.match(compiled.css, /\.public-site \.btn\.btn-cyan\s*\{/);
  assert.doesNotMatch(compiled.css, /\.public-site \.public-site \.btn\.btn-cyan/);
  assert.match(theme, /persistThemePreference/);
  assert.match(theme, /preference,\s*effectiveTheme:/);
  assert.match(theme, /function usePublicTheme\(/);
  assert.match(landing, /usePublicTheme\(\)/);
  assert.match(landingCss, /\.lp\[data-theme="dark"\]/);
  assert.match(themeCss, /\.lp,\s*\.public-site\s*\{/);
  assert.match(themeCss, /\.lp\[data-theme="dark"\],\s*\.public-site\[data-theme="dark"\]/);
  assert.doesNotMatch(themeCss, /\.layout|\.member-course-detail|\.sidebar/);
  assert.match(app, /<ThemeProvider>/);
});
