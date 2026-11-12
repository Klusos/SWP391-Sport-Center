import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const memberPage = () => readFile(new URL("../src/pages/MemberTraining.jsx", import.meta.url), "utf8");
const memberStyles = () => readFile(new URL("../src/pages/MemberTraining.css", import.meta.url), "utf8");
const plansPage = () => readFile(new URL("../src/components/IndividualTrainingPlans.jsx", import.meta.url), "utf8");
const appSource = () => readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
const trainingService = () => readFile(new URL("../../backend/src/main/java/group5/example/sportCenter/service/TrainingService.java", import.meta.url), "utf8");
const individualPlanService = () => readFile(new URL("../../backend/src/main/java/group5/example/sportCenter/service/IndividualTrainingPlanService.java", import.meta.url), "utf8");

test("Member Training retains initial loading, empty, error, and retry states", async () => {
  const source = await memberPage();
  assert.match(source, /classes === null[\s\S]*?role="status"/);
  assert.match(source, /classes\.length === 0[\s\S]*?chưa có lớp/i);
  assert.match(source, /error &&[\s\S]*?role="alert"/);
  assert.match(source, /onClick=\{\(\) => void load\(\)\}/);
  assert.match(source, /disabled=\{refreshing\}/);
});

test("Member Training class list navigates by stable Class ID to a separate result detail route", async () => {
  const source = await memberPage();
  const app = await appSource();
  assert.match(source, /to=\{`\/member\/training\/\$\{encodeURIComponent\(id\)\}`\}/);
  assert.match(source, /useParams\(\)/);
  assert.match(source, /classId !== undefined/);
  assert.match(source, /classKey\(item\) === String\(classId\)/);
  assert.match(source, /className="training-back-button" to="\/member\/training"/);
  assert.match(source, /Danh sách lớp/);
  assert.match(app, /path="\/member\/training\/:classId"/);
  assert.match(app, /path="\/member\/training"/);
  assert.match(source, /<ClassResults key=\{`results-\$\{classId\}`\} item=\{selectedClass\} \/>/);
  assert.match(source, /key=\{`personal-\$\{classId\}`\}/);
  assert.match(source, /classId=\{Number\(classId\)\}/);
  assert.doesNotMatch(source, /selectedClassId|setSelectedClassId/);
});

test("Detail restores the original result layout and scopes every section to the selected class", async () => {
  const source = await memberPage();
  assert.match(source, /className="dash-card training-member-class"/);
  assert.match(source, /sessionsPresent/);
  assert.match(source, /averageScore/);
  assert.match(source, /className="training-grid"/);
  assert.match(source, /results\.map/);
  assert.match(source, /evaluations\.map/);
  assert.match(source, /plan\?\.exercises/);
  assert.match(source, /<h1>\{selectedClass\?\.courseClass\?\.className \|\| "Kết quả tập luyện"\}<\/h1>/);
  assert.match(source, /selectedClass\.courseClass\?\.courseName[\s\S]*?selectedClass\.courseClass\?\.sportName/);
  assert.match(source, /courseOfferingName/);
  assert.match(source, /Nội dung &amp; mục tiêu lớp học/);
  assert.doesNotMatch(source, /SPORTS CENTER|Giáo án &amp; mục tiêu/);
  assert.match(source, /classId=\{Number\(classId\)\}/);
  assert.match(source, /<div className="training-detail-plans">\s*<IndividualTrainingPlans/);
});

test("Class cards keep distinguishing metadata and compact session progress", async () => {
  const source = await memberPage();
  assert.match(source, /info\.className/);
  assert.match(source, /info\.courseName/);
  assert.match(source, /info\.sportName/);
  assert.match(source, /info\.coachName/);
  assert.match(source, /Mã lớp \{id\}/);
  assert.match(source, /completedSessions \?\? 0\}\/\{info\.totalSessions \?\? 0/);
  assert.match(source, /info\.courseOfferingName/);
});

test("Detail refresh remains separate for results and class-scoped Individual Plans", async () => {
  const source = await memberPage();
  const plans = await plansPage();
  assert.match(source, /className="member-training-refresh"[\s\S]*?onClick=\{\(\) => void load\(\)\}/);
  assert.match(plans, /className="secondary" onClick=\{\(\) => void load\(\)\}/);
  assert.match(source, /classId=\{Number\(classId\)\}/);
  assert.match(plans, /getMyIndividualTrainingPlansApi\(token\)/);
});

test("Member Training cleanup retains keyboard focus and responsive detail/card styling", async () => {
  const styles = await memberStyles();
  assert.match(styles, /\.training-back-button:focus-visible/);
  assert.match(styles, /\.member-training-class-option:focus-visible/);
  assert.match(styles, /@media \(max-width: 760px\)/);
  assert.match(styles, /\.training-detail-shell \.training-stats/);
  assert.match(styles, /\.member-training-class-progress/);
});

test("Member Training access comes from confirmed registrations and preserves inactive Class history", async () => {
  const service = await trainingService();
  assert.match(service, /registrations\.findByMemberIdOrderByRegisteredAtDescIdDesc\(actor\.getId\(\)\)[\s\S]*?registration\.getStatus\(\) == CourseRegistrationStatus\.CONFIRMED/);
  assert.doesNotMatch(service, /courseClass\.getStatus\(\).*ACTIVE/);
  assert.match(service, /classResults\.stream\(\)/);
});

test("Individual Training Plans are filtered by authoritative Class ID, including same-Sport Classes", async () => {
  const source = await plansPage();
  const service = await individualPlanService();
  assert.match(source, /String\(plan\.classId\) === selectedClassKey/);
  assert.match(source, /String\(detail\?\.classId\) !== selectedClassKey/);
  assert.match(service, /!plan\.getMember\(\)\.getId\(\)\.equals\(actor\.getId\(\)\)/);
  assert.match(service, /registrations\.existsByMemberIdAndCourseClassIdAndStatus/);
  assert.doesNotMatch(source, /sportName|sportId.*filter|filter.*sportId/);
});

test("Member Training ignores stale class-list requests and preserves retry feedback", async () => {
  const source = await memberPage();
  assert.match(source, /\+\+latestRequest\.current/);
  assert.match(source, /requestId !== latestRequest\.current/);
  assert.match(source, /latestRequest\.current \+= 1/);
  assert.match(source, /setError\(requestError\?\.message/);
});

test("individual plans filter by class and hide stale details and history", async () => {
  const source = await plansPage();
  assert.match(source, /visiblePlans = useMemo/);
  assert.match(source, /String\(plan\.classId\) === selectedClassKey/);
  assert.match(source, /visibleSelected/);
  assert.match(source, /detailRequests\.isCurrent\(requestId\)/);
  assert.match(source, /loadRequests\.isCurrent\(requestId\)/);
  assert.match(source, /historyPlanId === visibleSelected\.id/);
});

test("Coach plan creation still requires verified attendance eligibility", async () => {
  const source = await plansPage();
  assert.match(source, /Promise\.allSettled/);
  assert.match(source, /canCreateIndividualPlan\(member, eligibilityLoaded\)/);
  assert.match(source, /individualPlanEligibilityMessage\(member, eligibilityLoaded, eligibilityError\)/);
  assert.match(source, /disabled=\{!canCreate\}/);
  assert.match(source, /getTrainingProgressApi\(token, classId\)/);
  assert.match(source, /plansResult\.status === "rejected"/);
});

test("Coach create, edit, revision history, and conflict recovery remain wired", async () => {
  const source = await plansPage();
  assert.match(source, /createIndividualTrainingPlanApi\(token, classId, memberId, payload\)/);
  assert.match(source, /updateIndividualTrainingPlanApi\(token, planId, payload\)/);
  assert.match(source, /getIndividualTrainingPlanRevisionsApi\(token, planId\)/);
  assert.match(source, /requestError\?\.status === 409\) await load\(\)/);
});
