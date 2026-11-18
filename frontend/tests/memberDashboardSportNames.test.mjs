import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolveMemberLevelSportName } from "../src/memberDashboard.js";

test("Member Dashboard resolves sportId through the live Sport catalog", () => {
  assert.equal(
    resolveMemberLevelSportName({ sportId: 12 }, [{ id: 12, name: "Cầu lông" }]),
    "Cầu lông"
  );
});

test("embedded authoritative sport names take precedence and unknown IDs do not invent labels", () => {
  assert.equal(resolveMemberLevelSportName({ sportId: 12, sportName: "Tennis" }, [{ id: 12, name: "Cầu lông" }]), "Tennis");
  assert.equal(resolveMemberLevelSportName({ sportId: 99 }, [{ id: 12, name: "Cầu lông" }]), null);
  assert.equal(resolveMemberLevelSportName({ sportId: null }, []), null);
});

test("Member Dashboard loads the Sport API and uses a safe unavailable-data label", async () => {
  const source = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  assert.match(source, /MEMBER:\s*\{[\s\S]*?levels: getMyMemberSportLevelsApi, sports: getSportsApi/);
  assert.match(source, /resolveMemberLevelSportName\(l, d\.sports\)/);
  assert.match(source, /errors\.sports \? "Không tải được tên bộ môn" : "Chưa có thông tin bộ môn"/);
  assert.doesNotMatch(source, /Bộ môn #\s*['"]\s*\+\s*l\.sportId/);
});
