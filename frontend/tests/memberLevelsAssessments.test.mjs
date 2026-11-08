import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { getMemberRelevantSports } from "../src/memberSportEligibility.js";

const memberId = 5;
const sports = [
  { id: 1, name: "Swimming" },
  { id: 2, name: "Tennis" },
  { id: 3, name: "Yoga" },
  { id: 4, name: "Badminton" },
  { id: 1, name: "Swimming duplicate" },
];
const registration = (overrides = {}) => ({ memberId, classId: 10, sportId: 1, status: "CONFIRMED", ...overrides });
const assessment = (overrides = {}) => ({
  memberId, sportId: 2, status: "COMPLETED", assessedAt: "2026-11-01T10:00:00", assessedLevel: "INTERMEDIATE", ...overrides,
});
const relevant = (overrides = {}) => getMemberRelevantSports({
  sports, memberId, registrations: [], assessments: [], ...overrides,
});

test("CONFIRMED registration qualifies with a future scheduled session", () => {
  assert.deepEqual(relevant({ registrations: [registration()], sessions: [{ classId: 10, status: "SCHEDULED", endTime: "2026-11-07T10:00:00" }] }).map((sport) => sport.id), [1]);
});

test("CONFIRMED registration qualifies when there is no future session", () => {
  assert.deepEqual(relevant({ registrations: [registration()], sessions: [{ classId: 10, status: "COMPLETED", endTime: "2026-11-01T10:00:00" }] }).map((sport) => sport.id), [1]);
});

test("CONFIRMED registration qualifies when its class has zero sessions", () => {
  assert.deepEqual(relevant({ registrations: [registration()], sessions: [] }).map((sport) => sport.id), [1]);
});

test("CONFIRMED registration remains eligible after the final session", () => {
  assert.deepEqual(relevant({ registrations: [registration()], sessions: [{ classId: 10, status: "COMPLETED", endTime: "2026-10-01T10:00:00" }] }).map((sport) => sport.id), [1]);
});

test("CONFIRMED registration qualifies without a Coach assessment", () => {
  assert.deepEqual(relevant({ registrations: [registration()], assessments: [] }).map((sport) => sport.id), [1]);
});

test("CANCELLED registration without an assessment is hidden", () => {
  assert.deepEqual(relevant({ registrations: [registration({ status: "CANCELLED" })] }), []);
});

test("EXPIRED registration without an assessment is hidden", () => {
  assert.deepEqual(relevant({ registrations: [registration({ status: "EXPIRED" })] }), []);
});

test("genuine completed Coach assessment qualifies without a confirmed registration", () => {
  assert.deepEqual(relevant({ assessments: [assessment()] }).map((sport) => sport.id), [2]);
});

test("consultation, default BEGINNER level, and catalog presence alone do not qualify", () => {
  const consultationOnly = [{ sportId: 1, status: "COMPLETED" }];
  const defaultLevelOnly = [{ sportId: 2, level: "BEGINNER" }];
  assert.deepEqual(relevant({ consultations: consultationOnly, levels: defaultLevelOnly }), []);
});

test("assessment validation rejects wrong Member, unknown sport, non-completed status, invalid level, and invalid timestamp", () => {
  const invalid = [
    assessment({ memberId: 6 }),
    assessment({ sportId: 999 }),
    assessment({ status: "PENDING" }),
    assessment({ assessedAt: null }),
    assessment({ assessedAt: "2026-02-30T10:00:00" }),
    assessment({ assessedLevel: "" }),
    assessment({ assessedLevel: "UNRECOGNIZED" }),
  ];
  assert.deepEqual(relevant({ assessments: invalid }), []);
});

test("registration and assessment sources are unioned and duplicate qualifying sports render once", () => {
  const result = relevant({ registrations: [registration()], assessments: [assessment({ sportId: 1 })] });
  assert.deepEqual(result.map((sport) => sport.id), [1]);
});

test("summary, status tabs, and search derive from the qualifying sport list", async () => {
  const component = await readFile(new URL("../src/pages/MemberLevelsAssessments.jsx", import.meta.url), "utf8");
  assert.match(component, /const enrichedSports = useMemo\(\(\) => \{\s*return relevantSports\.map/);
  assert.match(component, /enrichedSports\.filter\(\(s\) => s\.isVerified\)/);
  assert.match(component, /enrichedSports\.length - verifiedSportsCount/);
  assert.match(component, /return enrichedSports\.filter/);
  assert.match(component, /filteredSports\.map\(\(sport\) =>/);
});

test("required API failures fail closed and class-session data is not an eligibility dependency", async () => {
  assert.deepEqual(getMemberRelevantSports({ sports, registrations: null, assessments: [assessment()], memberId }), []);
  assert.deepEqual(getMemberRelevantSports({ sports, registrations: [registration()], assessments: null, memberId }), []);
  const component = await readFile(new URL("../src/pages/MemberLevelsAssessments.jsx", import.meta.url), "utf8");
  assert.doesNotMatch(component, /getMyClassSessionsApi|errors\.sessions/);
  assert.match(component, /eligibilityAvailable = !errors\.sports && !errors\.registrations && !errors\.assessments/);
  assert.match(component, /eligibilityAvailable && relevantSports\.length === 0/);
});

test("consultation form retains full public catalog and decorative sport icons stay removed", async () => {
  const component = await readFile(new URL("../src/pages/MemberLevelsAssessments.jsx", import.meta.url), "utf8");
  assert.match(component, /sports\.map\(\(sport\) => \(\s*<option/);
  assert.doesNotMatch(component, /sport\.meta\.icon|mla-sport-icon|mla-hero-watermark/);
});
