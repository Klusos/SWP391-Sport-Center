import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const reportSource = () => readFile(new URL("../src/pages/ReportPage.jsx", import.meta.url), "utf8");

test("Reports uses the shared Admin context bar and current authenticated identity without mounting another bell", async () => {
  const source = await reportSource();
  assert.match(source, /function ReportPage\(\{ token, currentUserName \}\)/);
  assert.match(source, /className="rc-main report-admin-shell"/);
  assert.match(source, /<AdminTopBar[\s\S]*?pageLabel=\{["']B\\u00c1O C\\u00c1O["']\}/);
  assert.match(source, /className="report-admin-topbar"/);
  assert.match(source, /displayName=\{currentUserName \|\| "Qu\\u1ea3n tr\\u1ecb vi\\u00ean"\}/);
  assert.doesNotMatch(source, /import NotificationBell/);

  const topBar = await readFile(new URL("../src/components/AdminTopBar.jsx", import.meta.url), "utf8");
  assert.match(topBar, /SPORTCENTER/);
  assert.ok(topBar.includes('breadcrumbRoleLabel = "QU\\u1ea2N TR\\u1eca"'));
  assert.match(topBar, /<b>\{pageLabel\}<\/b>/);
  assert.match(topBar, /initialsFrom\(avatarName\)/);
  assert.match(topBar, /slice\(-2\)[\s\S]*?toUpperCase\(\) \|\| "\\u2014"/);
  assert.match(topBar, /String\(displayName \|\| ""\)\.trim\(\) \|\| roleLabel/);

  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  assert.match(app, /path="\/admin\/reports"\s+element=\{<ReportPage \{\.\.\.staff\} \/>\}/);
  assert.match(app, /<NotificationBell\s+key=\{notificationIdentityKey\(auth\)\}/);
});

test("Reports keeps icon-only Refresh and Print in the filter panel header, with Update beneath filters", async () => {
  const source = await reportSource();
  const introStart = source.indexOf('<div className="page-intro">');
  const filterStart = source.indexOf('<form className="panel revenue-filters"');
  const formEnd = source.indexOf("</form>", filterStart);
  assert.ok(introStart >= 0 && filterStart > introStart && formEnd > filterStart);
  const intro = source.slice(introStart, filterStart);
  const filters = source.slice(filterStart, formEnd);
  assert.doesNotMatch(intro, /revenue-page-actions/);
  const panelHeaderStart = filters.indexOf('className="revenue-filter-panel-header"');
  const controlsStart = filters.indexOf('className="revenue-filter-controls"');
  const submitStart = filters.indexOf('className="revenue-filter-submit"');
  assert.ok(panelHeaderStart >= 0 && controlsStart > panelHeaderStart);
  assert.ok(submitStart > controlsStart);
  const panelHeader = filters.slice(panelHeaderStart, controlsStart);
  assert.match(panelHeader, /className="revenue-page-actions revenue-filter-actions"/);
  assert.equal((filters.match(/className="btn-light"/g) || []).length, 2);
  assert.equal((filters.match(/aria-label="Làm mới"/g) || []).length, 1);
  assert.equal((filters.match(/aria-label="In báo cáo"/g) || []).length, 1);
  assert.match(panelHeader, /title="Làm mới"[\s\S]*?<svg viewBox="0 0 16 16" width="18" height="18" fill="currentColor"/);
  assert.match(panelHeader, /title="In báo cáo"[\s\S]*?<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"/);
  const refreshSvg = await readFile(new URL("../node_modules/bootstrap-icons/icons/arrow-clockwise.svg", import.meta.url), "utf8");
  const refreshPaths = [...refreshSvg.matchAll(/<path[^>]*d="([^"]+)"/g)].map((match) => match[1]);
  assert.equal(refreshPaths.length, 2);
  for (const path of refreshPaths) assert.ok(panelHeader.includes(path), "refresh SVG uses the verified clockwise-arrow geometry");
  assert.doesNotMatch(filters.slice(controlsStart, submitStart), /revenue-filter-actions|revenue-page-actions/);
  assert.match(filters, /onClick=\{refreshApplied\}/);
  assert.match(filters, /onClick=\{\(\) => window\.print\(\)\}/);
  assert.ok(filters.indexOf("Làm mới") < filters.indexOf("In báo cáo"));
  assert.ok(filters.indexOf("revenue-filter-actions") < filters.indexOf("Cập nhật"));
  for (const field of ["revenue-presets", "type=\"date\"", "value={groupBy}", "onSubmit={applyFilters}"]) {
    assert.ok(filters.includes(field), `${field} remains in the filter form`);
  }
  const css = await readFile(new URL("../src/pages/ReportPage.css", import.meta.url), "utf8");
  assert.match(css, /\.app-shell \.revenue-filter-panel-header\s*\{[^}]*align-items: flex-start;[^}]*justify-content: space-between;/);
  assert.match(css, /\.app-shell \.revenue-filter-actions button\s*\{[^}]*width: 40px;[^}]*height: 40px;[^}]*justify-content: center;/);
  assert.match(css, /\.app-shell \.revenue-filter-actions svg\s*\{[^}]*display: block;[^}]*width: 18px;[^}]*height: 18px;[^}]*color: currentColor;/);
  assert.match(css, /\.app-shell \.revenue-filter-submit button\s*\{[^}]*width: 100%;/);
  assert.match(css, /\.app-shell \.revenue-filter-controls\s*\{[^}]*flex-wrap: wrap;/);
  assert.match(css, /@media print[\s\S]*?\.app-shell \.revenue-page-actions,[\s\S]*?\.app-shell \.revenue-filters,[\s\S]*?display: none !important;/);
  const globalStyles = await readFile(new URL("../src/styles.css", import.meta.url), "utf8");
  assert.match(globalStyles, /\.app-shell \.btn-light\{background:#eef4fb;color:#22527f\}/);
});

test("Reports context bar is excluded from print while report content retains print spacing", async () => {
  const css = await readFile(new URL("../src/pages/ReportPage.css", import.meta.url), "utf8");
  assert.match(css, /@media print[\s\S]*?\.report-admin-topbar\s*\{\s*display:\s*none;/);
  assert.match(css, /@media print[\s\S]*?\.report-admin-shell\s*\{\s*min-height:\s*0;/);
  assert.match(css, /\.main:has\(\.report-admin-shell\)\s*\{\s*padding:\s*0;/);
});

test("Reports print mode hides route chrome and interactive controls without screen rules", async () => {
  const source = await reportSource();
  const css = await readFile(new URL("../src/pages/ReportPage.css", import.meta.url), "utf8");
  assert.match(source, /document\.body\.classList\.add\("report-print-route"\)/);
  assert.match(source, /return \(\) => document\.body\.classList\.remove\("report-print-route"\)/);
  assert.match(css, /@media print[\s\S]*?\.report-print-route \.layout:has\(\.report-admin-shell\) \.sidebar/);
  assert.match(css, /@media print[\s\S]*?\.report-print-route \.notification-widget/);
  assert.match(css, /@media print[\s\S]*?\.revenue-filters/);
  assert.match(css, /@media print[\s\S]*?\.revenue-page-actions/);
  assert.match(css, /@media print[\s\S]*?\.revenue-chart-panel/);
  assert.match(css, /\.report-print-header[\s\S]*?display:\s*none/);
});

test("Printable revenue summary uses the loaded report dates, total, and API category values", async () => {
  const source = await reportSource();
  assert.match(source, /<strong>SPORTCENTER<\/strong>/);
  assert.match(source, /report-print-header[\s\S]*?<h1>/);
  assert.match(source, /report\.from\}[^\n]+report\.to/);
  assert.match(source, /formatVnd\(report\.totalRevenue\)/);
  assert.match(source, /REVENUE_PURPOSES\.map/);
  assert.match(source, /formatVnd\(totals\[purpose\]\)/);
  assert.match(source, /getAdminRevenueReportApi\(token, applied, controller\.signal\)/);
  assert.match(source, /Doanh thu ghi nhận từ các giao dịch thanh toán đã xác nhận thành công/);
  assert.doesNotMatch(source, /report-print-header[\s\S]{0,350}\b\d{4,}\b/);
});

test("Reports print rules use A4 portrait, omit the chart, and retain grayscale-safe summary cards", async () => {
  const css = await readFile(new URL("../src/pages/ReportPage.css", import.meta.url), "utf8");
  const printRules = css.slice(css.indexOf("@media print"));
  assert.match(printRules, /@page admin-report\s*\{\s*size:\s*A4 portrait;\s*margin:\s*14mm;/);
  assert.match(printRules, /\.report-print-route \.notification-widget/);
  assert.match(printRules, /\.revenue-chart-panel\s*\{\s*display:\s*none !important;/);
  assert.match(printRules, /\.revenue-summary-grid \.panel\s*\{[\s\S]*?background:\s*#fff !important;[\s\S]*?box-shadow:\s*none;/);
  assert.match(printRules, /\.revenue-summary-grid \.panel[\s\S]*?break-inside:\s*avoid/);
});

test("Report Page starts with the Vietnam current-month range and MONTH grouping", async () => {
  const source = await reportSource();
  assert.match(source, /defaultReportRevenueRange/);
  assert.match(source, /const \[initialRange\] = useState\(\(\) => defaultReportRevenueRange\(\)\)/);
  assert.match(source, /const \[groupBy, setGroupBy\] = useState\("MONTH"\)/);
  assert.match(source, /const \[applied, setApplied\] = useState\(\(\) => \(\{ \.\.\.initialRange, groupBy: "MONTH" \}\)\)/);
});

test("quick date presets are accessible and only update draft date inputs until Apply", async () => {
  const source = await reportSource();
  assert.match(source, /aria-label="Khoảng ngày nhanh"/);
  assert.match(source, /aria-pressed=\{activePreset === preset\}/);
  assert.match(source, /REVENUE_DATE_PRESETS\.map/);
  const choosePreset = source.match(/function choosePreset\(preset\) \{([\s\S]*?)\n  \}/)?.[1] || "";
  assert.match(choosePreset, /setRange\(presetRange\)/);
  assert.match(choosePreset, /setActivePreset\(preset\)/);
  assert.doesNotMatch(choosePreset, /setApplied/);
  assert.match(source, /setActivePreset\(""\)/);
  assert.match(source, /setApplied\(\{ \.\.\.range, groupBy \}\)/);
});

test("Refresh reloads applied filters without applying edited draft values", async () => {
  const source = await reportSource();
  const refresh = source.match(/function refreshApplied\(\) \{([\s\S]*?)\n  \}/)?.[1] || "";
  assert.match(refresh, /if \(!visibleLoading\) setRetry\(\(current\) => current \+ 1\)/);
  assert.doesNotMatch(refresh, /setRange|setGroupBy|setApplied/);
  assert.match(source, /getAdminRevenueReportApi\(token, applied, controller\.signal\)/);
  assert.match(source, /currentResult = result\?\.key === queryKey && result\?\.retry === retry/);
  assert.match(source, /if \(!controller\.signal\.aborted\) setResult/);
  assert.match(source, /return \(\) => controller\.abort\(\)/);
  assert.match(source, /onClick=\{refreshApplied\}/);
  assert.match(source, /disabled=\{visibleLoading\}/);
});

test("Revenue report retains filter validation and an explicit apply action", async () => {
  const source = await reportSource();
  assert.match(source, /if \(!range\.from \|\| !range\.to\)/);
  assert.match(source, /if \(!isValidRevenueRange\(range\)\)/);
  assert.match(source, /type="submit" disabled=\{visibleLoading\}>Cập nhật/);
});
