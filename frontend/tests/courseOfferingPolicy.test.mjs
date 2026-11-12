import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { isOfferingCommercialEditLocked, scheduledOpeningInstant } from "../src/courseOfferingPolicy.js";

test("commercial edit lock begins exactly 14 days before the Vietnam-local deadline", () => {
  const offering = { registrationDeadline: "2027-01-10T14:30:00" };
  const opening = scheduledOpeningInstant(offering.registrationDeadline);
  assert.equal(opening.toISOString(), "2026-12-27T07:30:00.000Z");
  assert.equal(isOfferingCommercialEditLocked(offering, new Date(opening.getTime() - 1)), false);
  assert.equal(isOfferingCommercialEditLocked(offering, opening), true);
});

test("the displayed lock follows the saved deadline, so draft deadline changes cannot move its boundary", async () => {
  const offering = { registrationDeadline: "2027-01-10T14:30:00" };
  const originalOpening = scheduledOpeningInstant(offering.registrationDeadline);
  const draft = { ...offering, registrationDeadline: "2027-02-10T14:30:00" };
  assert.equal(isOfferingCommercialEditLocked(offering, originalOpening), true);
  const pages = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
  assert.match(pages, /isOfferingCommercialEditLocked\(editing\)/);
  assert.match(pages, /isOfferingCommercialEditLocked\(entity\)/);
  assert.equal(isOfferingCommercialEditLocked(draft, originalOpening), false,
    "the draft itself would be a bypass; the rendered form must use the persisted entity");
});
