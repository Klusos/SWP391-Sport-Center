import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const reportSource = () => readFile(new URL("../src/pages/ReportPage.jsx", import.meta.url), "utf8");

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
