import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  activeOfferingRegistration,
  courseRegistrationStatus,
  courseRegistrationStatusLabel,
} from "../src/memberCourseRegistration.js";

test("course badge uses DTO courseId and pending payment takes priority across offerings", () => {
  const registrations = [
    { courseId: 7, courseOfferingId: 21, status: "CONFIRMED" },
    { courseId: 7, courseOfferingId: 22, status: "PENDING_PAYMENT" },
    { courseId: 8, courseOfferingId: 23, status: "CONFIRMED" },
  ];
  assert.equal(courseRegistrationStatus(registrations, 7), "PENDING_PAYMENT");
  assert.equal(courseRegistrationStatus(registrations, 8), "CONFIRMED");
  assert.equal(courseRegistrationStatus(registrations, 9), null);
});

test("expired and cancelled records do not produce an active catalog badge", () => {
  const registrations = [
    { courseId: 7, courseOfferingId: 21, status: "EXPIRED" },
    { courseId: 7, courseOfferingId: 22, status: "CANCELLED" },
  ];
  assert.equal(courseRegistrationStatus(registrations, 7), null);
  assert.equal(activeOfferingRegistration(registrations, 21), null);
});

test("records without a course relationship do not invent a catalog badge", () => {
  assert.equal(courseRegistrationStatus([{ courseOfferingId: 21, status: "CONFIRMED" }], 7), null);
  assert.equal(courseRegistrationStatus(null, 7), null);
  assert.equal(courseRegistrationStatus([{ courseId: 0, status: "CONFIRMED" }], null), null);
});

test("detail offering prefers pending payment and exposes localized status labels and actions", async () => {
  const detail = await readFile(new URL("../src/public-site/pages/CourseDetail.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/public-site/pages/CourseDetail.css", import.meta.url), "utf8");
  const registrations = [
    { id: 3, courseOfferingId: 11, status: "CONFIRMED" },
    { id: 4, courseOfferingId: 11, status: "PENDING_PAYMENT" },
  ];
  assert.equal(activeOfferingRegistration(registrations, 11).id, 4);
  assert.equal(courseRegistrationStatusLabel("CONFIRMED"), "Đã xác nhận");
  assert.equal(courseRegistrationStatusLabel("PENDING_PAYMENT"), "Chờ thanh toán");
  assert.match(detail, /member-course-registration-card/);
  assert.match(detail, /memberRoute\s*\?\s*activeOfferingRegistration\(registrations, selectedOfferingId\)\s*:\s*null/);
  assert.match(detail, /Tiếp tục thanh toán/);
  assert.match(detail, /Xem lịch học/);
  assert.match(detail, /Xem chi tiết đăng ký/);
  assert.match(detail, /classes\.map\(\(item\)/);
  assert.match(css, /\.member-course-detail \.member-course-registration-card/);
  assert.doesNotMatch(css, /(^|\n)\.member-course-registration-card\s*\{/);
});

test("public Course Detail does not render Member registration status UI", async () => {
  const detail = await readFile(new URL("../src/public-site/pages/CourseDetail.jsx", import.meta.url), "utf8");
  assert.match(detail, /memberRoute\s*\?\s*activeOfferingRegistration/);
  assert.match(detail, /\{isMember \? <section className="course-registration-panel"/);
  assert.match(detail, /memberRoute && <section className="member-course-consultation-card"/);
});
