import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  formatLandingPlanDuration,
  formatLandingPlanPrice,
  getActiveLandingPlans,
  getLandingPlanBenefits,
  getLandingSports,
} from "../src/landing/landingData.js";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("landing preview preserves API sport order and caps cards at six records", () => {
  const records = Array.from({ length: 8 }, (_, index) => ({ id: index + 1, name: `Sport ${index + 1}` }));
  assert.deepEqual(getLandingSports(records).map(({ id }) => id), [1, 2, 3, 4, 5, 6]);
  assert.deepEqual(getLandingSports([]), []);
  assert.deepEqual(getLandingSports(null), []);
});

test("landing preview membership summary includes only active API plans", () => {
  const records = [
    { id: 1, name: "A", status: "ACTIVE" },
    { id: 2, name: "B", status: "INACTIVE" },
    { id: 3, name: "C", status: "active" },
  ];
  assert.deepEqual(getActiveLandingPlans(records).map(({ id }) => id), [1, 3]);
  assert.deepEqual(getActiveLandingPlans(null), []);
});

test("landing membership price and duration use the Admissions VND and month conventions", () => {
  assert.equal(formatLandingPlanPrice(1200000), "1.200.000\u00a0₫");
  assert.equal(formatLandingPlanPrice(null), null);
  assert.equal(formatLandingPlanPrice("invalid"), null);
  assert.equal(formatLandingPlanDuration(1), "1 tháng");
  assert.equal(formatLandingPlanDuration(12), "1 năm");
  assert.equal(formatLandingPlanDuration(18), "1 năm 6 tháng");
  assert.equal(formatLandingPlanDuration(0), null);
  assert.equal(formatLandingPlanDuration(null), null);
});

test("landing membership benefits distinguish zero, missing, enabled, and unavailable plan fields", () => {
  const benefits = getLandingPlanBenefits({
    courseDiscountPercent: 0,
    personalTrainingDiscountPercent: 15.5,
    allowPersonalTrainingPlan: false,
    allowOneOnOneCoaching: false,
  });
  assert.deepEqual(benefits, [
    { label: "Ưu đãi khóa học", state: "excluded", value: "Không áp dụng" },
    { label: "Giáo án tập luyện cá nhân", state: "excluded", value: "Không bao gồm" },
    { label: "Huấn luyện 1 kèm 1", state: "excluded", value: "Không bao gồm" },
  ]);
  assert.ok(!benefits.some(({ label }) => label.includes("Ưu đãi huấn luyện")), "Do not show PT discount when 1:1 is disallowed");

  const allowedPt = getLandingPlanBenefits({
    courseDiscountPercent: 12.5,
    personalTrainingDiscountPercent: 0,
    allowPersonalTrainingPlan: true,
    allowOneOnOneCoaching: true,
  });
  assert.deepEqual(allowedPt.map(({ state, value }) => [state, value]), [
    ["included", "12,5%"],
    ["excluded", "Không áp dụng"],
    ["included", "Được bao gồm"],
    ["included", "Được bao gồm"],
  ]);

  const unknown = getLandingPlanBenefits({});
  assert.deepEqual(unknown.map(({ state, value }) => [state, value]), [
    ["unknown", "Chưa có dữ liệu"],
    ["unknown", "Chưa có dữ liệu"],
    ["unknown", "Chưa có dữ liệu"],
  ]);
});

test("the approved Landing is canonical and the preview alias redirects without losing search or hash", async () => {
  const app = await read("../src/App.jsx");
  const publicRoutes = app.split('<Route element={<PublicLayout')[1]?.split("</Route>")[0] || "";
  assert.match(app, /const landingRoute = \([\s\S]*?<LandingPage auth=\{auth\} dashboardPath=\{home\(auth\?\.role\)\} \/>[\s\S]*?\);/);
  assert.match(app, /<Route path="\/" element=\{landingRoute\} \/>/);
  assert.match(app, /path="\/landing-preview"[\s\S]*?<Navigate to=\{\{ pathname: "\/", search: location\.search, hash: location\.hash \}\} replace \/>/);
  assert.doesNotMatch(app, /<Route path="\/landing-preview" element=\{landingRoute\} \/>/);
  assert.ok(app.indexOf('<Route path="/" element={landingRoute} />') < app.indexOf('<Route element={<PublicLayout'));
  assert.doesNotMatch(app, /<Route element=\{<PublicLayout[^>]*\/>\}>[\s\S]*?<Route path="\/"/);
  assert.match(app, /lazy\(\(\) => import\("\.\/landing\/LandingPage\.jsx"\)\)/);
  assert.match(app, /<Suspense fallback=/);
  for (const route of ["/bo-mon", "/bo-mon/:sportId", "/khoa-hoc/:courseId", "/co-so-vat-chat", "/huan-luyen-vien", "/tuyen-sinh", "/membership-plans"]) {
    assert.ok(publicRoutes.includes(`path="${route}"`), `Expected existing Public route ${route}`);
  }
  assert.match(app, /function LandingSectionRedirect\(\{ sectionId \}\)[\s\S]*?search: location\.search, hash: `#\$\{sectionId\}`/);
  assert.match(publicRoutes, /path="\/huan-luyen-vien" element=\{<LandingSectionRedirect sectionId="huan-luyen-vien" \/>\}/);
  assert.match(publicRoutes, /path="\/tuyen-sinh" element=\{<LandingSectionRedirect sectionId="tuyen-sinh" \/>\}/);
  assert.match(publicRoutes, /path="\/membership-plans"\s+element=\{<LandingSectionRedirect sectionId="tuyen-sinh" \/>\}/);
  assert.match(publicRoutes, /path="\/khoa-hoc\/:courseId"[\s\S]*?auth\?\.role === "MEMBER"[\s\S]*?`\/member\/courses\/\$\{location\.pathname\.split\("\/"\)\.at\(-1\)\}`/);
  assert.doesNotMatch(publicRoutes, /element=\{<Admission|element=\{<Coaches/);
  assert.match(app, /path="\/login"/);
});

test("Landing direct hashes use the stable anchor handler and preserve supported section targets", async () => {
  const page = await read("../src/landing/LandingPage.jsx");
  const anchorNavigation = await read("../src/landing/anchorNavigation.js");
  assert.match(page, /resolveLandingHashTarget\(location\.hash, SECTION_IDS\)/);
  assert.match(page, /scrollToLandingTarget\(\{/);
  assert.match(page, /\[location\.pathname, location\.hash, catalog\.loading, plansLoading\]/);
  assert.match(anchorNavigation, /requestedId === "consultation" \? "public-consultation"/);
  assert.match(anchorNavigation, /scrollIntoView\(\{ block: "start", behavior: reducedMotion \? "auto" : "smooth" \}\)/);
  for (const id of ["bo-mon", "co-so-vat-chat", "huan-luyen-vien", "tuyen-sinh", "public-consultation"]) {
    assert.ok(page.includes(`id="${id}"`), `Expected Landing anchor #${id}`);
  }
});

test("landing preview uses real catalog APIs and one existing consultation form", async () => {
  const page = await read("../src/landing/LandingPage.jsx");
  assert.match(page, /getPublicSportsApi\(\)/);
  assert.match(page, /getPublicCoachesApi\(\)/);
  assert.match(page, /getPublicMembershipPlansApi\(\)/);
  assert.match(page, /<ConsultForm showFieldPlaceholders=\{false\} \/>/);
  assert.doesNotMatch(page, /SPORT_FALLBACK|2,?400\+|1,?248|18,4%|facebook\.com|instagram\.com/);
});

test("Landing remains the sole Public consultation form after legacy route consolidation", async () => {
  const app = await read("../src/App.jsx");
  const landing = await read("../src/landing/LandingPage.jsx");
  const form = await read("../src/public-site/components/ConsultForm.jsx");
  const publicLayout = await read("../src/public-site/PublicLayout.jsx");

  assert.match(app, /<Route path="\/" element=\{landingRoute\} \/>/);
  assert.match(app, /path="\/tuyen-sinh" element=\{<LandingSectionRedirect sectionId="tuyen-sinh" \/>\}/);
  assert.doesNotMatch(publicLayout, /ConsultForm|public-consultation/);
  assert.match(landing, /<ConsultForm showFieldPlaceholders=\{false\} \/>/);
  assert.equal((landing.match(/<ConsultForm showFieldPlaceholders=\{false\} \/>/g) || []).length, 1);
  assert.equal((publicLayout.match(/ConsultForm/g) || []).length, 0);
  assert.match(form, /showFieldPlaceholders = true/);
  assert.match(form, /createPublicConsultationRequestApi\(/);
  for (const field of ["name", "phone", "email", "sportId", "age", "experience", "note"]) {
    assert.match(form, new RegExp(`name="${field}"`));
  }
  assert.match(form, /placeholder=\{showFieldPlaceholders \?/);
});

test("landing consultation keeps its form and centered information while improving themed contrast", async () => {
  const page = await read("../src/landing/LandingPage.jsx");
  const css = await read("../src/landing/landing.css");
  const consultation = page.match(/function Consultation\(\)\s*\{[\s\S]*?\n\}\s*function Footer/)?.[0] ?? "";
  assert.ok(consultation, "Expected the existing consultation section");
  assert.match(consultation, /id="public-consultation"/);
  assert.match(consultation, /<ConsultForm showFieldPlaceholders=\{false\} \/>/);
  assert.match(css, /--lp-placeholder:\s*#929baa/);
  assert.match(css, /\.lp-consult \.form-control::placeholder\s*\{\s*color:\s*var\(--lp-placeholder\)/);
  assert.match(css, /\.lp-consult \.form-control\s*\{[\s\S]*?text-align:\s*left/);
  assert.match(css, /\.lp-consult-copy\s*\{[\s\S]*?justify-content:\s*center/);
  assert.match(css, /\.lp\[data-theme="light"\] \.lp-consult::before[\s\S]*?rgba\(255, 255, 255, 0\.6\)/);
  assert.match(css, /\.lp\[data-theme="dark"\] \.lp-consult::before[\s\S]*?rgba\(9, 11, 15, 0\.76\)/);
});

test("Landing coach cards center variable API result counts without stretching portraits", async () => {
  const page = await read("../src/landing/LandingPage.jsx");
  const css = await read("../src/landing/landing.css");
  const coaches = page.match(/function Coaches\([\s\S]*?\n\}\s*function Facilities/)?.[0] ?? "";

  assert.ok(coaches, "Expected the Landing Coaches section");
  assert.match(coaches, /const list = coaches;/);
  assert.match(coaches, /list\.map\(/);
  assert.match(coaches, /loading &&/);
  assert.match(coaches, /list\.length === 0/);
  assert.match(css, /\.lp-coaches\s*\{[\s\S]*?repeat\(auto-fit, minmax\(min\(100%, 240px\), 280px\)\)[\s\S]*?justify-content:\s*center/);
  assert.match(css, /\.lp-coach\s*\{\s*width:\s*100%;\s*max-width:\s*280px/);
  assert.match(css, /\.lp-coach-photo\s*\{\s*aspect-ratio:\s*4\s*\/\s*5/);
});

test("landing Footer links match section labels and preserve scroll-to-top behavior", async () => {
  const page = await read("../src/landing/LandingPage.jsx");
  const css = await read("../src/landing/landing.css");
  const tokens = await read("../src/public-site/publicTheme.css");
  const footer = page.match(/function Footer\([\s\S]*?\n\}\s*\/\*[- ]+/)?.[0] ?? "";
  assert.ok(footer, "Expected the landing Footer component");
  assert.match(page, /\{ id: "tuyen-sinh", label: "Hội viên" \}/);
  assert.match(footer, /<SectionLink id="tuyen-sinh">Hội viên<\/SectionLink>/);
  assert.match(footer, /<SectionLink id="bo-mon">Bộ môn<\/SectionLink>/);
  assert.match(footer, /<SectionLink id="huan-luyen-vien">Huấn luyện viên<\/SectionLink>/);
  assert.match(footer, /<SectionLink id="co-so-vat-chat">Cơ sở vật chất<\/SectionLink>/);
  assert.match(footer, /<SectionLink id="trang-chu" className="lp-to-top">/);
  assert.match(page, /scrollIntoView\(\{ behavior: prefersReducedMotion\(\) \? "auto" : "smooth", block: "start" \}\)/);
  assert.match(css, /\.lp-anchor,[\s\S]*?scroll-margin-top:\s*calc\(var\(--lp-nav-h\) \+ var\(--lp-anchor-gap\)\)/);
  assert.match(tokens, /--sc-public-footer-muted:\s*#b8c0cc/);
  assert.match(tokens, /--sc-public-footer-subtle:\s*#a3abba/);
  assert.match(css, /--lp-footer-muted:\s*var\(--sc-public-footer-muted\)/);
  assert.match(css, /\.lp-footer-col a,\s*\.lp-footer-col span\s*\{[\s\S]*?color:\s*var\(--lp-footer-muted\)/);
});

test("landing membership cards map API fields, preserve role destinations, and keep API states", async () => {
  const page = await read("../src/landing/LandingPage.jsx");
  const card = page.match(/<article className="lp-plan-card"[\s\S]*?<\/article>/)?.[0] ?? "";
  assert.ok(card, "Expected the membership card markup");
  assert.match(page, /getPublicMembershipPlansApi\(\)/);
  assert.match(page, /getActiveLandingPlans\(plans\)/);
  assert.match(page, /formatLandingPlanPrice\(plan\.price\)/);
  assert.match(page, /formatLandingPlanDuration\(plan\.durationMonths\)/);
  assert.match(page, /\{plan\.name\}/);
  assert.match(card, /<h3>\{plan\.name\}<\/h3>[\s\S]*?className="lp-plan-description"[\s\S]*?className="lp-plan-pricing"/);
  assert.doesNotMatch(card, /lp-eyebrow|lp-dot/);
  assert.match(card, /className="lp-plan-price"[\s\S]*?<strong>\{formatLandingPlanPrice\(plan\.price\)/);
  assert.match(card, /className="lp-plan-duration"[^>]*>[\s\S]*?<strong>\{formatLandingPlanDuration\(plan\.durationMonths\)/);
  assert.match(page, /plan\.description \|\| "Chưa có mô tả cho gói này\."/);
  assert.match(page, /getLandingPlanBenefits\(plan\)\.map/);
  assert.match(page, /auth\?\.role === "MEMBER" \?/);
  assert.match(page, /to="\/member\/membership"/);
  assert.match(page, /to="\/#public-consultation"/);
  assert.match(page, /\) : !auth \?/);
  assert.doesNotMatch(page, /to="\/tuyen-sinh"|Chi tiết tuyển sinh/);
  assert.match(page, /\{loading && <Status/);
  assert.match(page, /!loading && error && <p className="lp-status" role="alert"/);
  assert.match(page, /!loading && !error && activePlans\.length === 0/);
  assert.match(page, /!loading && !error && activePlans\.length > 0/);
  assert.match(card, /auth\?\.role === "MEMBER"[\s\S]*?to="\/member\/membership"/);
  assert.match(card, /!auth[\s\S]*?to="\/#public-consultation"/);
  assert.match(card, /\) : null\}/);
});

test("landing membership cards use responsive, theme-token surfaces and wrap long descriptions", async () => {
  const css = await read("../src/landing/landing.css");
  assert.match(css, /\.lp-plan-grid\s*\{[^}]*repeat\(3, minmax\(0, 1fr\)\)/);
  assert.match(css, /@media \(max-width: 899px\)\s*\{[^}]*\.lp-plan-grid\s*\{[^}]*repeat\(2, minmax\(0, 1fr\)\)/);
  assert.match(css, /@media \(max-width: 539px\)\s*\{[^}]*\.lp-plan-grid\s*\{[^}]*1fr/);
  assert.match(css, /\.lp-plan-card\s*\{[^}]*height:\s*100%[^}]*background:\s*var\(--lp-surface\)/);
  assert.match(css, /\.lp-plan-description\s*\{[^}]*overflow-wrap:\s*anywhere/);
  assert.match(css, /\.lp-plan-pricing\s*\{[^}]*flex-direction:\s*column[^}]*align-items:\s*flex-start/);
  assert.match(css, /\.lp-plan-duration\s*\{[^}]*padding:\s*0[^}]*border:\s*0[^}]*border-radius:\s*0[^}]*background:\s*transparent/);
  assert.match(css, /\.lp-plan-action\s*\{[^}]*margin-top:\s*auto/);
});

test("Admission Bridge is a compact text-only transition between Membership and Consultation", async () => {
  const page = await read("../src/landing/LandingPage.jsx");
  const css = await read("../src/landing/landing.css");
  const bridge = page.match(/function AdmissionBridge\(\)\s*\{[\s\S]*?\n\}\s*function Consultation/)?.[0] ?? "";
  assert.ok(bridge, "Expected the isolated Admission Bridge component");
  assert.ok(page.indexOf("<Admissions plans=") < page.indexOf("<AdmissionBridge />"));
  assert.ok(page.indexOf("<AdmissionBridge />") < page.indexOf("<Consultation />"));
  assert.match(bridge, /SPORTCENTER ĐỒNG HÀNH CÙNG BẠN/);
  assert.match(bridge, /Chưa biết bắt đầu từ đâu\?/);
  assert.match(bridge, /Chia sẻ nhu cầu tập luyện của bạn để chúng tôi có thể hỗ trợ lựa chọn phù hợp\./);
  assert.doesNotMatch(bridge, /SectionLink|Đăng ký tư vấn|lp-admission-bridge-action/);
  assert.doesNotMatch(bridge, /lp-admission-bridge-steps|<ConsultForm|24 ?giờ|24h|cam kết|bảo đảm/i);
  assert.match(css, /\.lp-admission-bridge\s*\{[^}]*padding:\s*clamp\(40px, 4vw, 44px\) 0/);
  assert.match(css, /\.lp-section\.lp-admissions\s*\{\s*padding-bottom:\s*clamp\(32px, 3vw, 44px\)/);
  assert.match(css, /\.lp-admission-bridge-copy\s*\{[^}]*max-width:\s*820px/);
  assert.match(css, /\.lp-admission-bridge-copy > p:last-child\s*\{[^}]*max-width:\s*64ch/);
  assert.doesNotMatch(css, /lp-admission-bridge-layout|lp-admission-bridge-action/);
  assert.match(css, /@media \(max-width: 767px\)\s*\{\s*\.lp-admission-bridge\s*\{[^}]*padding:\s*34px 0/);
});

test("landing preview uses a full-width diagonal Hero with a stacked narrow layout", async () => {
  const page = await read("../src/landing/LandingPage.jsx");
  const css = await read("../src/landing/landing.css");
  assert.match(page, /<Img src="\/images\/trungtam\.jpg" alt="Không gian Sport Center"/);
  assert.match(css, /\.lp-hero-inner\s*\{[\s\S]*?min-height:\s*clamp\(620px, 70vh, 700px\)/);
  assert.match(css, /\.lp-hero-copy\s*\{[\s\S]*?flex:\s*0 0 42%[\s\S]*?padding:[^;]*clamp\(24px, 3vw, 48px\)[\s\S]*?clip-path:\s*polygon\(0 0, 100% 0, 88% 100%, 0 100%\)/);
  assert.match(css, /\.lp-hero-visual\s*\{[\s\S]*?flex:\s*0 0 66%/);
  assert.match(css, /\.lp-hero-photo\s*\{[^}]*object-fit:\s*cover/);
  assert.match(css, /@media \(max-width: 899px\)\s*\{[\s\S]*?\.lp-hero-inner\s*\{[^}]*flex-direction:\s*column/);
});

test("landing navbar stays readable and separate from both Hero themes", async () => {
  const css = await read("../src/landing/landing.css");
  assert.match(css, /\.lp-nav\s*\{[\s\S]*?z-index:\s*100[\s\S]*?color:\s*var\(--lp-text\)[\s\S]*?background:\s*var\(--lp-nav-bg\)/);
  assert.match(css, /\.lp-nav\.is-scrolled,[\s\S]*?\.lp-nav\.is-open\s*\{[\s\S]*?background:\s*var\(--lp-nav-bg\)/);
  assert.match(css, /\.lp-menu\s*\{[\s\S]*?background:\s*var\(--lp-surface\)/);
  assert.match(css, /\.lp-brand b \{[^}]*color: var\(--lp-text\)/);
  assert.match(css, /\.lp-menu a\.active::after \{ transform: scaleX\(1\); \}/);
  assert.match(css, /@media \(max-width: 1199px\)\s*\{[\s\S]*?\.lp-burger \{ display: inline-grid/);
});

test("dark preview uses neutral charcoal surfaces with restrained navy and blue accents", async () => {
  const css = await read("../src/landing/landing.css");
  const tokens = await read("../src/public-site/publicTheme.css");
  const darkTheme = css.match(/\.lp\[data-theme="dark"\]\s*\{([^}]*)\}/)?.[1] ?? "";
  assert.match(darkTheme, /--lp-bg:\s*var\(--sc-public-page\)/i);
  assert.match(darkTheme, /--lp-nav-bg:\s*var\(--sc-public-nav\)/i);
  assert.match(darkTheme, /--lp-surface:\s*var\(--sc-public-surface\)/i);
  assert.match(darkTheme, /--lp-surface-2:\s*var\(--sc-public-surface-muted\)/i);
  assert.match(darkTheme, /--lp-hero-bg:\s*var\(--sc-public-brand-navy\)/i);
  assert.match(darkTheme, /--lp-accent:\s*var\(--sc-public-primary\)/i);
  assert.match(darkTheme, /--lp-accent-2:\s*var\(--sc-public-cyan\)/i);
  assert.match(darkTheme, /--lp-accent-ink:\s*#090b0f/i);
  assert.match(tokens, /--sc-public-page:\s*#090b0f/);
  assert.match(tokens, /--sc-public-nav:\s*#111318/);
  assert.match(tokens, /--sc-public-surface:\s*#171c25/);
  assert.match(tokens, /--sc-public-brand-navy:\s*#102540/);
  assert.match(tokens, /--sc-public-primary:\s*#4a9bf0/);
  assert.match(tokens, /--sc-public-cyan:\s*#55cbe8/);
  assert.doesNotMatch(darkTheme, /#0b1422|#17283e|#101c2d|#102a49/i);
  assert.match(css, /\.lp-hero\s*\{[^}]*background:\s*var\(--lp-hero-bg\)/);
  assert.match(css, /\.lp-nav\s*\{[^}]*background:\s*var\(--lp-nav-bg\)/);
  assert.match(css, /\.lp-sport \.lp-card-inner\s*\{\s*background:\s*var\(--lp-photo-card-bg\)/);
  assert.match(css, /\.lp-section\s*\{[^}]*transition:\s*background-color 0\.35s ease, color 0\.35s ease/);
});

test("all landing anchors reserve space below the sticky navbar and direct hashes use the same scroll behavior", async () => {
  const css = await read("../src/landing/landing.css");
  const page = await read("../src/landing/LandingPage.jsx");
  const anchorNavigation = await read("../src/landing/anchorNavigation.js");
  assert.match(css, /--lp-nav-h:\s*72px/);
  assert.match(css, /--lp-anchor-gap:\s*20px/);
  assert.match(css, /\.lp-anchor,[\s\S]*?scroll-margin-top:\s*calc\(var\(--lp-nav-h\) \+ var\(--lp-anchor-gap\)\)/);
  assert.match(page, /el\.scrollIntoView\(\{ behavior: prefersReducedMotion\(\) \? "auto" : "smooth", block: "start" \}\)/);
  assert.match(page, /resolveLandingHashTarget\(location\.hash, SECTION_IDS\)/);
  assert.match(anchorNavigation, /target\.scrollIntoView\(\{ block: "start", behavior: reducedMotion \? "auto" : "smooth" \}\)/);
  for (const id of ["bo-mon", "co-so-vat-chat", "huan-luyen-vien", "tuyen-sinh", "public-consultation"]) {
    assert.ok(page.includes(`id="${id}"`), `Expected anchor target ${id}`);
  }
  assert.match(css, /@media \(prefers-reduced-motion:\s*reduce\)/);
});

test("landing navigation has no redundant Explore item and maps membership to its section", async () => {
  const page = await read("../src/landing/LandingPage.jsx");
  assert.match(page, /\{ id: "tuyen-sinh", label: "Hội viên" \}/);
  assert.doesNotMatch(page, /id: "loi-ich"|function Features\(|<Features\s*\/>/);
  assert.match(page, /const SECTION_IDS = NAV\.map\(\(n\) => n\.id\)/);
  assert.match(page, /id="tuyen-sinh" aria-labelledby="lp-admissions-title"/);
});

test("Facilities bento uses existing home facility records, descriptions, images, and route", async () => {
  const page = await read("../src/landing/LandingPage.jsx");
  const css = await read("../src/landing/landing.css");
  assert.match(page, /facilities\.filter\(\(facility\) => facility\.homeKey\)/);
  assert.match(page, /<Img src=\{f\.img\} alt=\{f\.title\} label=\{f\.title\}/);
  assert.match(page, /<p>\{f\.text\}<\/p>/);
  assert.match(page, /to="\/co-so-vat-chat" className="lp-link"/);
  assert.match(css, /\.lp-facilities-bento\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 57fr\) minmax\(0, 43fr\)[\s\S]*?grid-template-rows:\s*repeat\(2, minmax\(270px, 1fr\)\)[\s\S]*?grid-template-areas:[\s\S]*?facility-lead[\s\S]*?facility-pool[\s\S]*?facility-studio/);
  assert.match(css, /@media \(max-width: 639px\)\s*\{[\s\S]*?\.lp-facilities-bento[\s\S]*?grid-template-columns:\s*1fr/);
});
