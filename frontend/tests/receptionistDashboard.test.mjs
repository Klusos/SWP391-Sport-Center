import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { activeMembershipPlanRows, pendingConsultationRows, receptionistDashboardMetrics, searchReceptionistMembers } from "../src/receptionistDashboard.js";

test("Receptionist dashboard metrics count only supplied real data and report unavailable sources as null", () => {
  assert.deepEqual(receptionistDashboardMetrics({
    members: [{ id: 1 }, { id: 2 }],
    sports: { content: [{ id: 1 }], totalElements: 7 },
    plans: [{ status: "ACTIVE" }, { status: "INACTIVE" }, {}],
    pendingRequests: { content: [{ id: 1 }], totalElements: 6 },
    inProgressRequests: { content: [{ id: 2 }], totalElements: 9 },
  }), { members: 2, sports: 7, activePlans: 1, pendingConsultations: 6, inProgressConsultations: 9 });
  assert.deepEqual(receptionistDashboardMetrics({ members: null, sports: null, plans: null, pendingRequests: null }), {
    members: null, sports: null, activePlans: null, pendingConsultations: null, inProgressConsultations: null,
  });
  assert.equal(receptionistDashboardMetrics({ pendingRequests: { content: [{ id: 1 }] } }).pendingConsultations, null);
});

test("member quick search and dashboard lists use supplied active and pending records", () => {
  const members = [{ id: 7, name: "An Nguyễn", phone: "0901" }, { id: 8, name: "Bình Trần" }];
  assert.deepEqual(searchReceptionistMembers(members, "nguyễn"), [members[0]]);
  assert.deepEqual(searchReceptionistMembers(members, "8"), [members[1]]);
  assert.deepEqual(searchReceptionistMembers(members, " "), []);
  assert.deepEqual(activeMembershipPlanRows([{ id: 1, status: "ACTIVE" }, { id: 2, status: "INACTIVE" }]), [{ id: 1, status: "ACTIVE" }]);
  assert.deepEqual(pendingConsultationRows({ content: [
    { id: 2, status: "PENDING", createdAt: "2026-01-02" },
    { id: 1, status: "PENDING", createdAt: "2026-01-01" },
    { id: 3, status: "IN_PROGRESS", createdAt: "2025-01-01" },
  ] }).map((row) => row.id), [1, 2]);
});

test("dashboard requests separate paginated status totals and links each panel to its actual filter", async () => {
  const source = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/ReceptionistDashboard.css", import.meta.url), "utf8");
  assert.match(source, /getConsultationRequestsApi\(t, \{ status: "PENDING", page: 0, size: 5 \}\)/);
  assert.match(source, /getConsultationRequestsApi\(t, \{ status: "IN_PROGRESS", page: 0, size: 5 \}\)/);
  assert.match(source, /metrics\.inProgressConsultations/);
  assert.match(source, /metrics\.pendingConsultations/);
  assert.match(source, /to="\/staff\/consultations\?status=IN_PROGRESS"/);
  assert.match(source, /to="\/staff\/consultations\?status=PENDING"/);
  assert.match(source, /errors\.inProgressRequests/);
  assert.match(source, /errors\.pendingRequests/);
  const queue = await readFile(new URL("../src/pages/ConsultationQueue.jsx", import.meta.url), "utf8");
  assert.match(queue, /useSearchParams/);
  assert.match(queue, /STATUSES\.includes\(searchParams\.get\("status"\)\)/);
  assert.match(queue, /setSearchParams\(value \? \{ status: value \} : \{\}\)/);
  assert.match(source, /onNavigate\("membership-sales"\)/);
  assert.match(source, /onNavigate\("members"\)/);
  assert.match(source, /role="status"/);
  assert.match(source, /role="alert"/);
  assert.match(css, /@media \(max-width: 1100px\)/);
  assert.match(css, /@media \(max-width: 640px\)/);
});
