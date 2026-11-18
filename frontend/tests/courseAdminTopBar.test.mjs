import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("Course list and create/edit pages use the authenticated Admin Top Bar and retain existing forms", async () => {
  const [list, form, app, css] = await Promise.all([
    read("../src/pages/CourseList.jsx"),
    read("../src/pages/CourseForm.jsx"),
    read("../src/App.jsx"),
    read("../src/pages/CourseManagement.css"),
  ]);

  for (const page of [list, form]) {
    assert.match(page, /import AdminTopBar from "\.\.\/components\/AdminTopBar"/);
    assert.match(page, /currentUserName/);
    assert.match(page, /pageLabel="QUẢN LÝ KHÓA HỌC" displayName=\{currentUserName\} avatarName=\{currentUserName\}/);
    assert.equal([...page.matchAll(/<AdminTopBar\b/g)].length, 1);
    assert.doesNotMatch(page, /NotificationBell/);
  }
  assert.match(list, /<h1>Quản lý khóa học<\/h1>/);
  assert.match(list, /breadcrumbItems=\{\[\{ label: "Khóa học", current: true \}\]\}/);
  assert.match(list, /filterCourses\(courses/);
  assert.match(list, /getCoursesApi\(token\)/);
  assert.match(list, /navigate\("\/staff\/courses\/new"\)/);
  assert.match(form, /buildCoursePayload\(form, editing\)/);
  assert.ok(form.includes("saveCourseApi(token, editing ? courseId : null, payload)"));
  assert.match(form, /course-age-policy/);
  assert.match(form, /aria-invalid=\{ageRangeInvalid\}/);
  assert.match(form, /navigate\(editing \? "\/staff\/courses\/" \+ courseId : "\/staff\/courses"\)/);
  const guard = app.indexOf('<Route element={<AdminRoute auth={auth} />}>');
  const listRoute = app.indexOf('path="/staff/courses"');
  assert.ok(guard >= 0 && listRoute > guard, "course management routes remain inside AdminRoute");
  assert.match(css, /\.course-list-content, \.course-form-content, \.course-management-content/);
});

test("course, offering, and class hierarchy pages use the AdminTopBar for contextual breadcrumbs and parent navigation", async () => {
  const [pages, app, sidebar] = await Promise.all([
    read("../src/pages/CourseManagementPages.jsx"),
    read("../src/App.jsx"),
    read("../src/components/Sidebar.jsx"),
  ]);

  assert.match(pages, /pageLabel="QUẢN LÝ KHÓA HỌC"/);
  assert.match(pages, /displayName=\{currentUserName\}/);
  assert.equal([...pages.matchAll(/<AdminTopBar\b/g)].length, 1);
  assert.match(pages, /breadcrumbItems=\{breadcrumbItems\}/);
  assert.match(pages, /\{ label: "Khóa học", to: basePath \}/);
  assert.match(pages, /pageKind === "course" \? \{ current: true \} : \{ to: coursePath \}/);
  assert.match(pages, /pageKind === "offering" \? \{ current: true \} : \{ to: offeringPath \}/);
  assert.match(pages, /\{ label: courseClass\.name\.trim\(\), current: true \}/);
  assert.doesNotMatch(pages, /course-management-breadcrumbs|function Breadcrumbs\(/);
  assert.match(pages, /const parentPath = pageKind === "class" \? offeringPath : pageKind === "offering" \? coursePath : basePath/);
  assert.match(pages, /onClick=\{\(\) => navigate\(parentPath\)\}/);
  assert.match(pages, /typeof course\?\.name === "string"[\s\S]*typeof offering\?\.name === "string"[\s\S]*typeof courseClass\?\.name === "string"/);
  assert.match(pages, /String\(course\?\.id \?\? ""\) === String\(courseId \?\? ""\)/);
  assert.match(pages, /String\(offering\?\.id \?\? ""\) === String\(offeringId \?\? ""\)/);
  assert.match(pages, /String\(courseClass\?\.id \?\? ""\) === String\(classId \?\? ""\)/);
  assert.match(pages, /onView=\{\(item\) => navigate\(offeringPathFor\(item\.id\)\)\}/);
  assert.match(pages, /onView=\{\(item\) => navigate\(classPathFor\(item\.id\)\)\}/);
  assert.match(app, /path="\/staff\/courses\/\:courseId\/offerings\/\:offeringId\/classes\/\:classId"/);
  const adminClasses = app.indexOf('path="/admin/classes"');
  assert.ok(adminClasses >= 0);
  assert.match(app.slice(adminClasses, adminClasses + 160), /Navigate to="\/staff\/courses" replace/);
  const adminGroups = sidebar.match(/const adminGroups\s*=\s*\[([\s\S]*?)\n\];/)?.[1];
  assert.ok(adminGroups);
  assert.match(adminGroups, /\["courses",/);
  assert.doesNotMatch(adminGroups, /\["classes",/);
});

test("AdminTopBar optional breadcrumb items use router links and preserve the pageLabel fallback", async () => {
  const [topBar, css] = await Promise.all([
    read("../src/components/AdminTopBar.jsx"),
    read("../src/components/AdminTopBar.css"),
  ]);
  assert.match(topBar, /import \{ Link \} from "react-router-dom"/);
  assert.match(topBar, /breadcrumbItems,/);
  assert.match(topBar, /const hasCustomBreadcrumb = Array\.isArray\(breadcrumbItems\) && breadcrumbItems\.length > 0/);
  assert.match(topBar, /<Link className="admin-topbar-breadcrumb-item" to=\{item\.to\} title=\{item\.label\}>\{item\.label\}<\/Link>/);
  assert.match(topBar, /aria-current=\{item\.current \? "page" : undefined\}/);
  assert.match(topBar, /<b>\{pageLabel\}<\/b>/);
  assert.match(css, /\.admin-topbar-breadcrumb-item\s*\{[^}]*overflow: hidden;[^}]*text-overflow: ellipsis;[^}]*white-space: nowrap/);
  assert.match(css, /\.admin-topbar-breadcrumb a:focus-visible/);
});

test("course Top Bars do not create Notification Bells or change App notification ownership", async () => {
  const [app, list, form, pages] = await Promise.all([
    read("../src/App.jsx"),
    read("../src/pages/CourseList.jsx"),
    read("../src/pages/CourseForm.jsx"),
    read("../src/pages/CourseManagementPages.jsx"),
  ]);
  assert.equal([...app.matchAll(/<NotificationBell\b/g)].length, 1);
  assert.match(app, /<NotificationBell\s+key=\{notificationIdentityKey\(auth\)\}/);
  for (const page of [list, form, pages]) assert.doesNotMatch(page, /NotificationBell/);
});
