import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const sourceUrl = new URL("../src/pages/CoachAttendance.jsx", import.meta.url);

test("Coach attendance stores a selected status under the session-scoped row key", async () => {
  const source = await readFile(sourceUrl, "utf8");

  assert.match(
    source,
    /setRowValue\(setSelectedStatuses,\s*rowKey,\s*event\.target\.value\)/,
  );
  assert.doesNotMatch(
    source,
    /setRowValue\(setSelectedStatuses,\s*memberId,\s*event\.target\.value\)/,
  );
});
