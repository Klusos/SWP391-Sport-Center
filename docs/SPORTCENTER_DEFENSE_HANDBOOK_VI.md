# Sổ tay bảo vệ dự án SportCenter

> Tài liệu giải thích **hệ thống đang chạy trong mã nguồn**, không phải bản SRS thứ hai. Khi tài liệu giai đoạn cũ, test fixture và source khác nhau, hãy nêu rõ nguồn; code cho biết hành vi triển khai, còn chính sách chỉ được coi là đã duyệt khi có quyết định của chủ sản phẩm. Snapshot được đối chiếu đến nhánh `qa/team-integration-20261122`, HEAD `b2bbde7d737873f57029919db0ec921300861921` và smoke evidence ngày 28/11/2026.

## Cheat sheet demo (5 phút)

1. Mở frontend Vite và đăng nhập bằng tài khoản demo được cấp riêng; không ghi mật khẩu/token lên slide.
2. Khách xem Bộ môn/Khóa học công khai rồi gửi yêu cầu tư vấn. Lễ tân xử lý yêu cầu và có thể tạo tài khoản Hội viên; Hội viên tự hoàn thiện hồ sơ.
3. Admin cấu hình Gói hội viên, Upgrade Path, khóa học/đợt/lớp và lịch buổi. Lễ tân/Hội viên tạo thanh toán phù hợp; chỉ xác nhận tiền quầy sau khi thực thu. VNPay dùng sandbox theo cấu hình hiện tại.
4. Coach mở Attendance để xem hàng đợi cần điểm danh, gửi một roster đầy đủ, sau đó mở Training để cập nhật giáo án/kết quả.
5. Hội viên xem lịch, gói quyền lợi đã chụp tại thời điểm mua, bài tập theo tuần và lịch sử của chính mình.
6. Support dùng claim-and-start. Audit Log cho thấy ai thực hiện hành động.
7. Swagger UI: `/swagger-ui/index.html`; nhập JWT trong **Authorize** dưới dạng chuỗi token, không thêm tiền tố `Bearer ` vì scheme tự thêm. Chỉ gọi API đọc trong demo.

**Không demo** giao dịch VNPay thật, API tác động hàng loạt, scheduler thủ công trên DB5, hay dữ liệu riêng của Hội viên khác. Normal configuration bật scheduler; QA/test phải tắt bằng process-local overrides.

## 1. Công nghệ và vai trò của từng phần

| Công nghệ | Dùng để làm gì trong dự án | Vị trí | Cách trình bày/demo |
|---|---|---|---|
| Java 21 | Ngôn ngữ backend, kiểu dữ liệu và domain services | `backend/pom.xml`, `backend/src/main/java` | Mở một Service như `CourseRegistrationService`, giải thích business flow |
| Spring Boot 4.1.1 | Tự cấu hình web, dependency injection, lifecycle và cấu hình | `backend/pom.xml`, `application.properties` | Chạy backend, xem startup và Hibernate validate |
| Spring MVC | REST controller, JSON binding, status code, multipart | `controller/`, `dto/` | Gọi endpoint từ Swagger |
| Spring Security | Filter chain, JWT resource-server, CORS và role gates | `config/SecurityConfig.java`, `security/` | So sánh 401 (chưa xác thực) và 403 (sai role) |
| JWT / OAuth2 Resource Server | Bearer token; backend xác minh chữ ký và lấy subject/role | `AuthService`, `JwtDecoder`, Security config | Login lấy token, dùng Authorize gọi API được phép |
| BCrypt | Băm mật khẩu, không lưu password rõ | `AuthService`, password encoder config | Chỉ giải thích hash; không in hash hoặc mật khẩu |
| Jakarta Bean Validation | Kiểm tra DTO trước Service qua `@Valid`, ví dụ `@NotBlank`, `@Email`, custom phone | DTOs, `validation/`, controllers | Gửi input sai từ API trực tiếp và quan sát 400 |
| Spring Data JPA | Repository abstraction, query methods, transactions | `repository/`, `service/` | Theo dõi repository method từ Service |
| Hibernate ORM | Ánh xạ Entity ↔ bảng/cột và schema validation | `entity/`, `spring.jpa.hibernate.ddl-auto=validate` | Startup thành công chứng minh mapping tương thích, không chứng minh mọi query |
| JDBC / Microsoft JDBC Driver | Kết nối SQL Server qua JDBC URL | Maven dependency, datasource properties | Xem connection đã cấu hình; che host/credential nhạy cảm |
| Microsoft SQL Server | RDBMS, FK/check/unique/index/transaction | SQL scripts, `submission/SQL/` | Đọc-only query schema/identity; không demo ghi lịch sử |
| React 19 | Component UI, state và composition | `frontend/src/` | Theo dõi page → component |
| Vite 8 | Dev server, bundling và build frontend | `frontend/vite.config.js`, `package.json` | `npm run dev`, `npm run build` |
| React Router 6 | Route tree và role-aware pages | `frontend/src/App.jsx` | Hiện route Admin/Coach/Member |
| Bootstrap 5 + Bootstrap Icons | Grid, utilities và biểu tượng | `frontend/package.json`, CSS | Responsive portal controls; không giả định mọi style global |
| Fetch/API client | HTTP JSON, bearer token và error translation | `frontend/src/api.js` | Inspect request URL/headers mà không log JWT |
| Maven | Dependency resolution, compile/test/package, profile lifecycle | `backend/pom.xml`, `mvnw*` | Chạy focused test; cache riêng không xóa cache nhóm |
| Lombok | Sinh getter/setter/constructor để giảm boilerplate | Entity/DTO annotations, Maven compiler config | So sánh annotation với compiled accessors |
| Springdoc OpenAPI 3.2.1 | Sinh OpenAPI từ Controller/DTO và cung cấp Swagger UI | pom, `config/OpenApiConfiguration.java` | UI/docs endpoints; Authorize bằng JWT |
| VNPay Sandbox | Luồng thanh toán redirect/return/IPN có chữ ký; không phải mock | `payment/`, `PaymentService`, properties | Chỉ mô tả hoặc dùng sandbox đã phê duyệt; không tạo giao dịch thật |
| Mock payment gateway | Cổng mô phỏng chỉ khi bật rõ trong môi trường QA | payment adapter/config | Test sandbox cô lập; mặc định hiện tại mock tắt |
| H2 | In-memory database cho service/integration tests | test profile/dependency | Test nhanh; không chứng minh SQL Server lock/schema behavior |
| Playwright | Browser E2E riêng Node test suite | `frontend/e2e/`, `playwright.config.js` | Chạy khi app/browser/session được chuẩn bị; không tự seed |
| Node test runner + ESLint | Frontend regression/source tests và static checks | `frontend/package.json` | `npm test`, `npm run lint` |
| SQL Server scripts | Migration thủ công có guard, validation và rollback notes | `backend/docs/sql/` | Review identity guard; script không tự chứng minh đã áp dụng |

### Tích hợp ngoài

- **VNPay**: PaymentService tạo giao dịch/URL theo Payment đã lưu; browser được chuyển đến provider. Return URL phục vụ trải nghiệm trình duyệt, còn IPN có chữ ký là đường server xác minh kết quả collection. Transaction reference, amount, currency, time và trạng thái được kiểm tra; callback trùng phải idempotent. Lỗi/mismatch đi vào trạng thái đối soát như `REFUND_REQUIRED`, không tự ghi nhận SUCCESS.
- **Filesystem ảnh**: `LocalImageStorageService` lưu file cục bộ; public image endpoint phục vụ đường dẫn đã quản lý. Không phải SQL blob. Upload cần kiểm tra loại/nội dung/kích thước từ code.
- **Không có** bằng chứng đang dùng message broker, WebSocket, Redis hay cloud storage; không tuyên bố chúng tồn tại.

## 2. Kiến trúc và cách đọc source

### Request lifecycle

```text
React page
  → feature API helper / frontend/src/api.js
  → HTTP + JSON (Authorization: Bearer JWT nếu cần)
  → Spring Security filter chain: CORS, JWT signature/expiry, role gate
  → Controller: route, parameter/body binding, @Valid
  → DTO validation / custom validators
  → Service: ownership, business preconditions, transaction/locking
  → Repository (Spring Data JPA)
  → Entity mapping / Hibernate
  → JDBC driver / SQL Server constraint + transaction
  ← response DTO → HTTP status/JSON → API client → UI state/message
```

1. Vite không thực thi business authorization; sửa request thủ công vẫn bị backend kiểm tra.
2. JWT Decoder xác minh token. Controller có thể lấy `Jwt`, nhưng Service vẫn xác định actor và quyền theo domain.
3. `@Valid` kiểm tra hình thức. Các invariant nhiều bảng nằm trong Service và có thể lặp lại ngay trước persist.
4. `@Transactional` gắn các thay đổi liên quan cùng transaction. Database constraints là lớp cuối cho unique/FK/check, không thay được kiểm tra nghiệp vụ.
5. Pessimistic row locks serialize các hành động cùng Member/class/ticket/session; optimistic `expectedUpdatedAt` phát hiện draft cũ.
6. Exception translation trả 400 cho dữ liệu/điều kiện sai, 401/403 cho xác thực/quyền, 404 thiếu tài nguyên, 409 xung đột; lỗi không dự kiến không nên phơi stack trace.
7. Audit bắt buộc nằm trong transaction của thao tác quan trọng. Notification event được phát sau business commit trong một số luồng; đây là best-effort, không phải durable outbox.
8. Schedulers chỉ chạy khi global switch bật. Không dùng startup defaults để chạy QA trên DB5.
9. Hibernate `validate` chỉ so mapping/schema; nó không migrate. SQL Server bản hiện tại đã pass Hibernate validation 38 entity ngày 28/11/2026.

### Source navigation matrix

| Tính năng | Frontend | API / Controller method | Service method | Repository / Entity / bảng | Test evidence |
|---|---|---|---|---|---|
| Public discovery | `frontend/src/public-site/pages/` | `PublicSportController`, `PublicCourseController`, `PublicMembershipPlanController` GET | corresponding public service methods | Sport, Course, Offering, MembershipPlan | public route tests trong frontend/backend |
| Login/profile | auth pages, profile pages | `AuthController.login/logout`; `UserController.me`; `MemberProfileController.*Me` | `AuthService.login`, `MemberProfileService` | users, member_profile | `ConsultationRequestIntegrationTest` auth/security cases |
| Consultation | Guest/Member forms; staff consultation pages | `ConsultationRequestController.create/mine/all/createMember` | `ConsultationRequestService.create`, account-link methods | consultation_request, users | `ConsultationRequestIntegrationTest` |
| Membership plans/sales | Admin/Receptionist sales; Member Membership | `MembershipPlanController`, `MembershipController.create`, `PaymentController` | `MembershipService`, `PaymentService` | membership_plan, membership, payment | `PaymentIntegrationTest` |
| Upgrade | Member membership; Admin Upgrade Path | `MembershipController.upgrade-options`, `MembershipUpgradePathController`, Payment endpoints | `MembershipUpgradeService`, `MembershipUpgradePathService` | membership_upgrade_path, membership_upgrade, payment, membership | `MembershipUpgradeIntegrationTest` |
| Renewal | Member pending section; staff payment queues | `MemberRenewalController`, `ReceptionistRenewalController`, Membership/Payment controllers | `MembershipRenewalIntentService`, `MembershipRenewalPaymentAttemptService`, `MembershipRenewalSettlementService`, lifecycle | renewal_intent, membership, payment | `Phase5BFinalRenewalHttpIT`, Renewal integration tests |
| Cancellation/refund | Member membership, staff queues | `MembershipController` cancellation routes; `PaymentController.*refund` | `MembershipCancellationRequestService`, `PaymentService` | membership_cancellation_request, payment | `PaymentIntegrationTest`, cancellation tests |
| Catalog/schedule | Admin catalog and attendance selection | `SportController`, `CourseController`, `CourseOfferingController`, `CourseClassController`, `ClassSessionController` | respective services, `ClassSessionService` | sport, course, course_offering, class, class_session, facility | `CourseRegistrationIntegrationTest`, schedule tests |
| Course registration | Member Courses; Receptionist payment | `CourseRegistrationController.register/mine/myCourseSessions` | `CourseRegistrationService.register` | course_registration, payment | `CourseRegistrationIntegrationTest`, `PaymentIntegrationTest` |
| Attendance | Coach/Admin Attendance; Member history | `AttendanceController` | `AttendanceService.submit/update/history/memberOverview` | attendance, attendance_history | `TrainingIntegrationTest`; attendance tests |
| PT bookings | Member PT, Coach assigned bookings, Admin outcomes | `PersonalTrainingBookingController`; `AdminPtBookingOutcomeController` | `PersonalTrainingBookingService`, `PtBookingOutcomeService` | personal_training_booking, payment, audit_log | `PersonalTrainingBookingIntegrationTest`, `PaymentIntegrationTest` |
| General/Individual plans | Coach Training, Admin read-only, Member view | `TrainingController` | `TrainingService`, `IndividualTrainingPlanService` | training_plan/week/exercise; individual plan/revision/week/exercise | `TrainingIntegrationTest`, `IndividualTrainingPlanIntegrationTest` |
| Results/progress | Coach workspace; Member results | `TrainingController` result/progress routes | `TrainingService` | training_result, progress_evaluation | `TrainingIntegrationTest` |
| Skill assessment/levels | Member overview; staff/Coach assessment | `SkillAssessmentController`, `MemberSportLevelController` | `SkillAssessmentService`, `MemberSportLevelService` | skill_assessment, member_sport_level | `ConsultationRequestIntegrationTest` and skill tests |
| Support | Member/Receptionist/Admin Support | `SupportTicketController` | `SupportTicketService` | support_ticket, support_ticket_message, audit_log | `SupportTicketIntegrationTest` |
| Notifications/audit | Shared bell, Admin logs | `NotificationController`, `AdminNotificationController`, `AdminAuditLogController` | `NotificationService`, event delivery, `AuditLogService` | notification, audit_log | notification/audit integration tests |

> This matrix names stable source entry points. Confirm individual routes and permission matchers in each Controller and `SecurityConfig.java`; Swagger operation visibility is not authorization.

### Dependency injection and transaction behavior

Spring creates Controller/Service/Repository beans and injects constructor dependencies. Service owns orchestration so Controller remains HTTP-thin. Repositories hide query mechanics while Entity relationships describe persistence. For multi-row operations—attendance batch, Renewal settlement, support claim, plan whole-draft save—transaction boundaries are part of correctness. A domain exception must roll the whole unit back, including mandatory audit entries.

Pessimistic locks are used where concurrent operations could consume a single Member entitlement, take the same Support ticket, alter schedule facts or conflict attendance/results. Locks only protect the rows actually selected; SQL Server integration tests are needed for production locking semantics. H2 concurrency tests are useful but not equivalent.

## 3. Actors and business flows

| Actor | Typical allowed work | Deliberately unavailable |
|---|---|---|
| Khách (Guest) | Public discovery, submit Consultation with contact fields | Self-register/login as Member without staff account creation |
| Hội viên (Member) | Own profile, Membership/payment, course registration, PT, own schedule/history/support, eligible plans | Read/modify another Member's private records; Coach/Admin writes |
| Lễ tân (Receptionist) | Consultation, staff-assisted registration and counter payments, support ownership/reply, operational queues | Coach-only class training edits; unauthorized ticket reply |
| Huấn luyện viên (Coach) | Assigned classes, roster attendance, results/plans, assigned PT outcomes | Unassigned-class changes; Admin-only correction/configuration |
| Quản trị viên (Admin) | System/catalog oversight, staff, reports, exceptions, read-only training supervision, audited exceptional actions | Bypass payment evidence or silently rewrite historic audit/business rows |

### Guest discovery and Consultation

Guest opens public catalogs, then submits `POST /api/consultation-requests` with required contact name/phone/email/sport and optional age/experience/note/DOB per DTO. It creates a PENDING request and notifies active Receptionists through the internal notification event. Invalid email/phone/age or missing required values returns validation error; duplicate account email is rejected without account disclosure. A guest cannot create an account directly.

An authenticated Member uses the same conceptual service but name/email/phone/member association come from the authenticated user record, not client identity fields. Sport, experience and notes are legitimate request data. Consultation DOB remains the existing Member DOB behavior. Staff claim/complete/cancel actions check role, assignment and legal state. Staff create/link-Member is transactional; an existing request DOB is authoritative and read-only, while a legacy missing DOB uses the validated staff-entered fallback.

### Account and profile

Admin/Receptionist creates a Member account; backend selects MEMBER role and enforces account email uniqueness. Password is hashed with BCrypt. Member logs in for JWT, then creates/updates only their own MemberProfile using `/me` routes. DOB is a date-only value; age is calculated at decision time and not stored as a second source of truth. Required profile data and optional height/weight follow current DTO/service validation; do not infer undocumented health limits.

### Membership, Payment and benefits

Admin maintains Membership Plans. Member chooses a plan and a Payment is created through the permitted method. Payment stays PENDING until a trusted settlement/counter-confirmation path succeeds. A successful payment creates/activates the corresponding Membership once. Purchased benefits are snapshots so a future plan edit does not change an existing entitlement. For overlapping active memberships, the selection logic uses the maximum eligible applicable discount in the current Course/PT flow; quote paths snapshot the selected terms.

Failure paths include cancelled/expired payment, duplicate settlement, amount/signature mismatch and reconciliation. A Payment is evidence of collection; a Membership is entitlement. Never treat a pending intent as received money.

### Renewal

Member's Renewal preference is opt-in. When source Membership is the sole effective current Membership, active/eligible, has no conflicting future entitlement/financial state and is within `[endDate - 3 days at 00:00, endDate at 00:00)` Vietnam local time, lifecycle creates one OPEN Intent. It freezes plan/price/duration/benefits and successor dates. No payment is collected by the scheduler. Member may create a Payment Attempt while intent is actionable. A pending attempt associates through its persisted Payment ID. Expired/cancelled attempts may permit retry according to lifecycle state. Successful verified settlement atomically marks Intent settled and creates one successor Membership starting on the prior exclusive end date. Duplicate callback cannot create a second successor. Failed/ambiguous evidence remains in reconciliation.

Turning off future Renewal does not reverse a settled payment or cancel a paid successor. Cancelling an unpaid attempt and disabling the preference are separate operations. A paid successor follows ordinary strict 72-hour upcoming cancellation eligibility; no Renewal exception.

### Upgrade

Admin configures directed Upgrade Path edges; a path A→B does not imply B→A. Member must have one current eligible Membership, verified paid-value basis, no blocking queue/payment/renewal and at least three unused days. Quote uses source paid basis × unused days ÷ source total days, rounded down to a whole VND; credit is capped at target plan price. Payable = target price − credit and must be positive. On settlement, the target Membership receives the target plan's full duration starting at settlement date (as implemented), and saved quote terms remain frozen. Multiple upgrades form separate lineage records; the next source paid-value basis follows the implemented applied-credit + collected-payment chain. Expired quote rules route late money to reconciliation/refund handling; they do not rewrite the quote.

### Cancellation and refund

Upcoming ordinary Member cancellation request has deadline `membership.startDate at 00:00 - 72 hours`, Vietnam local time. It is accepted only before the deadline; equality is closed. Running Membership cannot use this upcoming cancellation request. There may be a queued future Membership that must be handled in sequence. Admin exceptional cancellation remains a separate audited action. Refund obligation is recorded against original Payment, with separate confirmation per Payment and renewal/upgrade lineage boundaries. `REFUND_REQUIRED` is an obligation/reconciliation state, not proof money has been returned. No automatic provider refund is implied.

For exceptional Admin cancellation, Service walks backwards through the target Membership's Upgrade lineage to that entitlement cycle's root purchase. It verifies same Member, target/source links, Payment purposes, successful collection evidence, no cycles/duplicate Payments, and Renewal boundary. It stops at a Renewal successor boundary; it does not cross into the prior Membership cycle. Linked refundable Payments are locked in stable ID order. Each SUCCESS Payment becomes its own `REFUND_REQUIRED`; pre-existing refund obligations remain and already-refunded Payments do not get duplicated. The audit records per-Payment actual amount/disposition. Any broken or ambiguous lineage rejects the exceptional cancellation rather than guessing an aggregate. The total refund obligation is a sum for display/audit only; there is no aggregate refund record or automatic disbursement.

### Catalog, sessions and Course registration

Admin defines Sport → Course → Offering → Class → ClassSession, plus Facility/Coach assignments. Session creation/update validates time range, Offering duration/date, facility/sport compatibility, Coach schedule and resource conflicts. Existing occupied registrations lock schedule mutation; there is no Member schedule-change workflow. Elapsed scheduled sessions are auto-completed by scheduler, but this does not mark attendance complete.

Registration is Member or authorized staff-assisted. Backend eligibility includes Offering OPEN/registration deadline, the 14-day opening instant before registration deadline, class schedule completeness, capacity, age policy, sport level and active Member class/PT overlap. New registration is PENDING with frozen price/discount and payment deadline `min(now + 24h, offering.registrationDeadline)`. Confirmed/cancelled/expired status is distinct from Payment state. Cancellation deadlines and refund obligations are server-side; a cancelled registration does not reappear from delayed payment callbacks.

### PT booking and outcomes

PT session length is 90 minutes and the initial payment hold is 15 minutes (both constants in `PersonalTrainingBookingService`). Member needs eligible Membership benefit and active qualified Coach. Coach/Member schedule conflict and existing bookings are checked; active class registration blocks a Member PT overlap, and active Member PT blocks class registration. Overlap is strict: `leftStart < rightEnd && leftEnd > rightStart`; exact adjacency is allowed. A successful payment confirms the booking. Confirmed Member cancellation needs 24h notice; at exactly 24 hours the comparison permits cancellation, while later requests fail. Admin may cancel under existing rules and creates per-Payment refund obligations. Coach records COMPLETED/NO_SHOW only when eligible/assigned and after the allowed time. Admin correction needs reason and audit. Outcome correction does not change payment/refund/entitlement.

### Attendance

Coach sees assigned sessions and server-derived roster. Initial official submission must include exactly one valid selection for every authoritative eligible learner, then persist all atomically. New statuses are PRESENT and ABSENT; historical EXCUSED is retained/readable and counted as recorded nonparticipation. Incomplete roster is NOT_RECORDED/PARTIAL/COMPLETE derived from eligible roster and persisted attendance, not UI draft. Future sessions are not due. Cancelled/empty rosters are not applicable under backend semantics.

Present attendance rate is `PRESENT / recorded eligible outcomes`; absent and historical excused are in denominator, unrecorded/future/cancelled/ineligible are not. With zero outcomes, rate is unavailable, not 0% or 100%. Incomplete elapsed attendance makes the shown rate provisional. PRESENT→ABSENT/EXCUSED is rejected when a TrainingResult exists. Corrections need reason, authorization, history and audit. Notifications only follow successful persisted initial ABSENT or genuine status changes; no unsaved/failed/unchanged notification.

### Training and assessment

General TrainingPlan is one per class, with overall goal/description, ordered positive unique numbered weeks, and exercises. Week date ranges derive from Offering: week 1 starts on Offering start, each ordinal week is seven days, in-range final end is clamped to Offering end. Session reschedule does not change curriculum numbering; shortening Offering does not delete weeks. Legacy exercise with null `week_id` remains visible until a Coach explicitly moves it. Coach weekly whole-plan save is transactional, assignment-restricted and stale-draft protected by `expectedUpdatedAt`; Admin reads only; eligible Members see saved content including future weeks.

Individual Training Plan is per eligible Member and class; revision history is a separate immutable sequence of revisions/weeks/exercises. Existing Individual range is 1–52; General weeks have no arbitrary 52 cap. Exercise sets/reps/minutes are optional when absent and validate against current range constraints when supplied. TrainingResult session score is integer 1–10 and requires PRESENT. Mean score is arithmetic average rounded to one decimal. Progress evaluation is a separate Coach scale 1–5 with comments/date; it is not a conversion from session score. Skill assessment is its own workflow and updates verified Member sport level under access rules.

### Support, notifications, audit, dashboards

Member creates and reads own Support ticket and replies under lifecycle rules. An unassigned OPEN ticket must be claimed; Receptionist claim-and-start assigns and sets IN_PROGRESS atomically. An unassigned legacy IN_PROGRESS ticket remains claimable. Other Receptionists cannot reply to someone else's assigned ticket. Admin can participate after assignment without replacing assignee. Resolved ticket may be reopened by allowed reply/action; CLOSED is read-only under current flow. Messages align based on persisted user IDs, not names.

Notifications are personal records and unread counts are per recipient. Business events notify affected users/roles after successful commit; delivery is best-effort in some flows (not durable outbox). Audit rows capture actor/action/target/time/description and are mandatory for key mutations. Dashboards combine API-backed counts and bounded previews; they do not replace underlying history.

## 4. Business rules, formulas and boundary examples

### Time conventions

- Business timestamps use `Asia/Ho_Chi_Minh` where service explicitly applies `BUSINESS_ZONE`.
- Database LocalDate fields are date-only. Do not timezone-shift DOB or Membership dates.
- Offering dates and Registration deadlines are separate concepts.
- Offering validation requires `startDate < endDate`; registration and cancellation deadlines are not after the Offering start-date midnight and registration deadline is on/before cancellation deadline. When editing a schedule, expired deadlines are rejected by the update validation path. Individual session endpoints additionally validate session within Offering boundaries/duration.
- Always translate a service predicate into boundary behavior; never infer from wording.

### Exact known boundary predicates

| Rule | Source predicate | Immediately before | Exactly at | Immediately after |
|---|---|---|---|---|
| Renewal window | `now >= endDate.minusDays(3).atStartOfDay()` and `now < endDate.atStartOfDay()` | no Intent yet | opens at first instant of local start date minus 3 days; closes at end-date midnight | after end-date midnight closed |
| Course registration opening | rejects `now < registrationDeadline.minusDays(14)` | rejected | allowed by opening predicate; other gates still apply | allowed by opening predicate |
| Course payment hold | `min(now + 24h, registrationDeadline)`; expiry comparison is strict | hold active before deadline | expired/equality cannot reclaim seat/pay | expired |
| Membership cancellation request | `now < startDate.atStartOfDay().minusHours(72)` | eligible if other rules pass | rejected | rejected |
| PT booking cancellation | rejects if `booking.start - 24h < now` | eligible before cutoff | equality allowed by this comparison | rejected after cutoff |
| PT overlap | `startA < endB && endA > startB` | overlapping interior conflicts | exact end/start adjacency does not conflict | non-overlap |
| Training result timing | assignment, session state/end-time and PRESENT attendance checks | preconditions fail before eligible time | follow current method comparisons | not assumed from UI |

**Month-end:** Java `LocalDate.plusMonths(n)` adjusts an invalid target day to the last valid day (e.g., Jan 31 + 1 month becomes Feb 28/29). Current Membership and Renewal code uses plusMonths; state this adjustment when explaining a date. **Membership end semantics:** renewal successor starts at source `endDate`; do not add a day without source evidence.

### Upgrade worked example

Suppose verified source paid-value basis = 1,000,000 VND; source total days = 30; unused days = 12; target price = 1,500,000 VND.

`rawCredit = floor(1,000,000 × 12 / 30) = 400,000 VND`

`credit = min(rawCredit, targetPrice) = 400,000 VND`

`payable = 1,500,000 - 400,000 = 1,100,000 VND`

All calculations preserve VND precision; credit quotient uses scale 0, RoundingMode.DOWN. If credit equals target price, payable becomes zero and quote is rejected. Target duration is the entire target plan duration, not remaining source duration. Quote snapshots protect the offer from later plan/path edits.

### Course discount example

Offering 1,000,000 VND with applicable maximum Membership discount 15%:
`discount = 1,000,000 × 15% = 150,000 VND`; final = 850,000 VND. Backend stores original, discount and final price at registration; settlement uses that snapshot, not a later edited plan.

### Attendance example

For 8 elapsed eligible sessions, 5 recorded (4 PRESENT, 1 ABSENT), and 3 unrecorded:
rate = 4 / 5 = 80% **provisional**. Display 5/8 recorded and explain the 3 missing records. With 0 recorded outcomes show no rate. Historical EXCUSED is recorded nonparticipation and therefore contributes to denominator but not numerator.

### Scheduler configuration

| Job | Current normal behavior | QA safety |
|---|---|---|
| Global scheduling | Enabled by normal config | `sportcenter.scheduling.enabled=false` disables `@Scheduled` and startup catch-up |
| Renewal lifecycle | Cron every 15 minutes, Vietnam zone, separately controlled enable flag | Disable both global and Renewal switch |
| Membership lifecycle/expiry | Cron configured in properties, Vietnam zone | Disable global switch |
| Class session completion | On ApplicationReady catch-up, then 300,000 ms fixed delay | Disable global switch |
| Course/PT pending expiration | Fixed intervals from application properties | Disable global switch |
| PT outcome fallback | Disabled by default; 60,000 ms interval if explicitly enabled | Keep disabled |

Never activate schedulers against DB5 during QA. A local startup may catch up eligible elapsed rows if global scheduling is enabled.

## 5. Validation and constraints catalog

The table distinguishes UI, API/service and database layers; a blank DB rule means no equivalent database check was confirmed. Exact SQL types/index names are in FullSchema and reviewed migrations.

| Module/input | Required/type/format or range | Frontend | Backend | Database protection / failure |
|---|---|---|---|---|
| Login email/password | Required strings; email identity | required fields | `@Valid`, account lookup and BCrypt; 401 generic failure | `users.email` unique; no password plaintext |
| Account name | Required, trimmed; DTO max 100 for relevant account path | required/length | Bean/service validation | nonblank/column size; invalid API 400 |
| Account email | Required valid email, max by DTO, normalized | email input | `@Email`, uniqueness precheck | unique `users.email`; conflict response |
| Account phone | Optional where existing contract allows null/blank; nonblank `^0[0-9]{9}$`, max column | shared validator/type=tel | `@VietnamesePhone` + service persistence guard | trusted CHECK on users; duplicates permitted |
| Consultation name/phone/email | Required, trim; name ≤100, phone ≤20 and exact format, email ≤150 and valid | Guest form validation | @Valid plus service checks; authenticated identity authoritative | consultation phone CHECK, non-null/length, FK sport/member; 400/409 |
| DOB | Member profile date-only; Consultation optional/legacy fallback | native date field | Past check where required; account DOB authoritative on link | date column nullable per mapping |
| Sport ID | Required positive reference | selection | @NotNull/@Positive plus service lookup | FK; 400/404 |
| MemberProfile height/weight | Optional decimal, cm/kg | 50.00–250.00 cm and 10.00–500.00 kg; up to 3 integer + 2 fractional digits | `@DecimalMin/@DecimalMax/@Digits` on create/update DTO; controllers use `@Valid` | numeric precision/scale; no matching CHECK constraint confirmed; null allowed |
| Membership Plan price | Required VND decimal, nonnegative/precision bounded by DTO/entity | min/step | Bean/service checks | decimal precision/scale; check only where present |
| Plan duration | positive integer months | numeric minimum | Bean/service validation | integer/check if declared |
| Discount percentages | 0–100 inclusive | min/max | DTO/service; quote filters invalid values | SQL check where snapshot declares; out of range reject |
| Course age policy | enum REQUIRED/RECOMMENDED; optional nonnegative min/max; min≤max; REQUIRED needs both | conditional fields | `CourseAgePolicyEvaluator`; under age 10 requires complete safe range | checks/FK; registration blocked if ineligible |
| Offering price/sessions/duration/dates | price ≥0, max 10 integer/2 fraction digits; integral 1–60 planned sessions; integral 30–180 minute duration; `startDate < endDate`; registration and cancellation deadlines ≤ start-date midnight and registration ≤ cancellation | form constraints | DTO + service integer/date-order/future checks; 14-day registration gate is separate | decimal, FK, check where declared; calendar relations are service checks |
| Class capacity | positive integer (≥1); name max 100 | numeric/text fields | `@Valid`; service validates active Coach/Offering and schedule | integer/FK; no claim of a DB positive CHECK unless present in snapshot |
| Session start/end | required local timestamp, end after start, within Offering and duration | date/time inputs | service conflicts, facility and Coach checks; active registration freezes schedule edits | FKs, no DB exclusion constraint |
| Registration classId | required positive ID | selection | Member actor from JWT, eligibility/capacity/age/level/conflict | FK and status; duplicates checked transactionally |
| PT coach/sport/start | positive IDs, future time; fixed 90-minute duration | time/date and eligibility list | @Valid, entitlement, Coach qualification, Member/Coach/class overlap | FK; time conflict is service invariant |
| Training week number | General positive unique per plan; Individual 1–52 | contextual validation | full-draft validation | unique + check; stale General draft 409 |
| Exercise | name required; sets 1–100, reps 1–1000, minutes 1–240 when supplied, order bounded | inline form | request validation | check constraints in snapshot; API 400 |
| TrainingResult score | integer 1–10; only eligible PRESENT Member/session | numeric form | service role/assignment/presence/time | check and unique(session,member) |
| Progress evaluation | Coach 1–5 with comment/date semantics | explicit scale | Coach assigned-class authorization | FK/unique/history according to mapping |
| Attendance initial | exact authoritative roster, one P/A each; no new EXCUSED | per-row P/A, no per-row initial save | complete set validation + transaction + locks | unique session/member; enum/check allows historical EXCUSED |
| Attendance correction reason | required for correction | required text | backend enforces role/reason/result conflict | history FK/length; failure rolls back |
| Support subject/category/message | required, trimmed/length DTO | required and length | ticket ownership/state/reply permission | FK, nonblank message check; 400/403/409 |
| Notification/audit description | system-generated; user broadcast required and bounded | form-level | role and target validation | FK + column lengths; not arbitrary user SQL |
| Coach/Sport qualification | required sport ID; experience years integer ≥0; certification ≤255; Coach bio ≤1000 | staff form | DTO + role/service checks | FK/column bounds; no higher experience maximum documented |
| Progress evaluation / session result | progress rating 1–5 with nonblank comment ≤1000; session score integer 1–10; optional notes ≤1000 | Coach forms | `@Valid` plus assignment, session and PRESENT-attendance rules | score CHECK and unique session/member; evaluation history relation |
| Skill assessment | positive Member/Sport/Coach IDs; optional Consultation ID positive; notes ≤1000 | staff form | DTO plus role, relationship and status checks | FKs; assessment status/ownership is a service invariant |
| Image upload | multipart, allowed MIME/extension/content and max size from service/config | accept filter | storage validates content/size/path | filesystem, not SQL constraint |

**Do not claim the frontend is security:** direct API tests bypass forms. Email uniqueness is required for account identity and backed by unique constraint. Phone is contact information; owner explicitly allows duplicate valid phone values, so no unique phone index exists. The strict DB phone checks enforce shape while nullability remains unchanged.

### Known limitations / questions

- Database CHECK constraints cannot express every cross-row business rule (Member overlap, entitlement, status transitions); transaction/locking Service logic owns these.
- The submission schema is a snapshot, not an upgrade script. Existing DB deployments need individually reviewed migrations.
- Historical data can predate current UI rules. Do not rewrite EXCUSED attendance, audit identity, payment evidence or plans just to make screens uniform.
- Test-level H2 results do not prove SQL Server isolation/locking. Use guarded SQL Server-specific suites for those claims.
- VNPay Sandbox is not a certification of production merchant credentials, external callback reachability or settlement operations.

## 6. Lecturer defense Q&A

Answers are short speaking points; expand from source rather than memorizing claims.

### Requirements and domain

1. **What problem does SportCenter solve?** It coordinates sports courses, Membership entitlements, staff workflows and individual training while keeping payments and operational records linked.
2. **Who are the actors?** Guest, Member, Receptionist, Coach and Admin; authorization is role plus resource ownership/assignment.
3. **Why is there no Guest self-registration?** Account creation is staff-managed so account role and Consultation linkage are server-controlled.
4. **What is a CourseOffering versus Class?** Offering is a dated commercial/scheduling run of a Course; Class is a capacity/Coach group inside it.
5. **What is a ClassSession?** One scheduled meeting of a Class. Attendance and session result attach to this identity.
6. **Is a Payment the same as Membership?** No. Payment is collection evidence; Membership is entitlement.
7. **Why keep Renewal Intent separate from Payment?** Intent expresses opt-in and frozen renewal quote; Payment records an actual collection attempt.
8. **Why keep Upgrade Paths directed?** The owner configures permitted source→target transitions; reverse path is not implied.
9. **Why snapshot Membership benefits?** Plan changes must not retroactively change a purchased entitlement.
10. **Why can duplicate phones exist?** Phone is not account identity and duplicate valid phone numbers are allowed; email has a uniqueness constraint.
11. **Why preserve historical EXCUSED?** History is evidence. New UI no longer selects it, but rewriting old rows would falsify attendance history.
12. **Why do scheduled completion and attendance completion differ?** A session can have ended while Coach has not submitted a complete roster.

### Architecture and implementation

13. **Where does a request begin?** React page calls shared API client; HTTP reaches Spring filter chain and Controller.
14. **What does Controller do?** Bind route/body, apply @Valid and return response/status; domain workflow belongs in Service.
15. **What does Repository do?** Encapsulate JPA queries/locking; it does not decide user-facing workflow.
16. **What is an Entity?** ORM mapping between Java object and relational table, not the same thing as API DTO.
17. **Why use DTOs?** They constrain external request/response shape and avoid exposing entity internals.
18. **How does dependency injection work?** Spring constructs beans and supplies constructor dependencies, making services composable/testable.
19. **Why use transactions?** Multiple writes (business row, history, audit, payment association) must commit or roll back together.
20. **How does a lock help?** It serializes conflicting operations on shared Member/session/ticket rows so concurrent requests see updated state.
21. **What is optimistic stale-draft protection?** Client supplies expectedUpdatedAt; server rejects replacement if another save changed the plan.
22. **Does Hibernate validate create tables?** No. validate checks mappings against existing schema and fails on mismatch; it does not migrate.
23. **Why SQL Server if H2 tests pass?** SQL Server is the target database; H2 makes fast tests but differs in dialect, constraints and locks.
24. **What is Spring Security's role?** Verify authentication and apply request authorization before Controller; Service still checks ownership and assignment.
25. **What does JWT contain/use?** Signed claims identify subject/role and expiry; the server validates signature and expiry for each protected request.
26. **Why BCrypt?** It is a one-way adaptive password hash; app never needs to recover plaintext.
27. **What is OpenAPI?** Machine-readable contract generated from routes/DTO metadata; Swagger UI is a client, not a security bypass.
28. **How do you test protected Swagger calls?** Use a valid Bearer JWT in Authorize; endpoint still enforces role. Open docs do not make business routes public.
29. **Where are images stored?** Local configured filesystem through LocalImageStorageService, not SQL blobs.
30. **Does the system use microservices?** The source is one Spring Boot backend and one React SPA; do not describe it as microservices.
31. **Does it use WebSockets?** No current evidence; Support conversation is request/response HTTP.
32. **Why use React Router?** It maps client navigation to role/page views without full-page server rendering.
33. **Why use Vite?** Fast local dev server and production asset bundling.
34. **What does Lombok do?** Generates boilerplate accessors/constructors at compile time; it does not alter business rules.
35. **Why Maven?** Reproducible Java dependency/build/test configuration and profiles.

### Security and data integrity

36. **What is 401 vs 403?** 401 means unauthenticated/invalid token; 403 means authenticated but forbidden.
37. **Can a Member change memberId in request JSON?** Ownership comes from JWT/service actor; payload IDs are checked/ignored as appropriate.
38. **Can a Coach edit any class?** No. Backend verifies assigned Coach/class, not just UI route.
39. **Can an Admin edit training plans?** Admin class-plan supervision is read-only; separate operational admin permissions do not grant Coach writes.
40. **How prevent Support takeovers?** Ticket row lock and state/assignee checks make claim atomic; a competitor cannot overwrite assignment silently.
41. **How prevent cross-plan week links?** FK to week plus composite (week_id, plan_id) ownership constraint and Service validation.
42. **How prevent duplicate emails?** Service precheck for helpful response plus database unique constraint for race-safe final enforcement.
43. **Why no unique phone constraint?** Owner permits duplicates; check constraint validates shape only.
44. **How prevent invalid phone API writes?** DTO/custom validator and service guard, plus SQL CHECK on key phone columns.
45. **What happens if audit insert fails?** For mandatory transactional audit, the business write rolls back.
46. **Are notifications exactly once?** No general durable outbox; event code suppresses unchanged retries, but some delivery is best effort after commit.
47. **How prevent a partial attendance batch?** Service resolves server roster, validates exact IDs/statuses, persists in one transaction and shares locking with TrainingResult writes.
48. **Can members edit attendance?** No; Member routes are read-only, Coach/Admin have scoped correction/submit permissions.
49. **What if Member lacks recorded attendance?** No misleading percentage; unrecorded sessions are completeness gaps, not absences.
50. **Can history be deleted by UI?** Normal workflows preserve records; no historical repair is implied by the UI.

### Financial and date rules

51. **How is Upgrade credit computed?** Verified paid basis × unused days ÷ total days, round down to VND, capped at target price.
52. **Why freeze an Upgrade quote?** Provider settlement can be delayed; recomputing after plan edit would change what the Member accepted.
53. **Does Upgrade preserve remaining duration?** No, target gets its full plan duration after successful settlement under current implementation.
54. **What does a Renewal scheduler do?** It creates eligible Intent/quote; it does not automatically collect money.
55. **When can a Renewal Intent be generated?** From start of local date three days before Membership end up to (excluding) end-date midnight, if all eligibility gates pass.
56. **When does Renewal successor begin?** At source Membership end date, with target plan duration.
57. **What is the ordinary upcoming Membership cancellation cutoff?** At least 72 hours before start-of-day; equality is too late.
58. **Can paid Renewal bypass cancellation?** No. It uses the same ordinary policy; disabling future Renewal does not cancel a paid successor.
59. **When does Course registration open?** 14 days before registrationDeadline; this is distinct from Offering start date.
60. **How long is a course payment hold?** Up to 24 hours, capped at registration deadline.
61. **How do session overlaps work?** Strict interval overlap; adjacent end/start timestamps are allowed.
62. **Can PT overlap a registered class?** No while the registration/session state counts as active; opposite direction also rejects registration against active PT.
63. **What is PT length/cancel notice?** 90 minutes; the initial payment hold is 15 minutes; confirmed Member cancellation uses 24 hours.
64. **How is average TrainingResult computed?** Arithmetic mean of scores, rounded to one decimal.
65. **Is progress scale the same as session score?** No. Session score is 1–10; progress assessment is an independent Coach scale 1–5.

### Testing, operations and limitations

66. **What does a unit test prove?** Only the exercised behavior in that test environment, not all roles/database/browser behavior.
67. **What did the final backend smoke run?** Three focused H2 integration classes, 111/111, plus actual DB5 Hibernate validate and authenticated invalid-phone HTTP 400.
68. **Did that smoke prove PT conflict on SQL Server?** No. Both directions passed H2 integration tests; no DB5 conflict write was induced.
69. **How are DB integration tests guarded?** Explicit opt-in profile, identity checks, environment-supplied connection and fixture guard; read test configuration before invocation.
70. **Why not use DB5 for arbitrary tests?** It is the original demo DB; historical data must not be treated as a disposable fixture.
71. **What happens after server restart with scheduling enabled?** Application-ready catch-up can complete elapsed scheduled sessions; use QA scheduler overrides.
72. **Is automatic session completion attendance submission?** No. It changes only session status and audit; no attendance/result is synthesized.
73. **Can a payment callback be trusted by its HTTP status alone?** No; signature and amount/reference/status must be validated.
74. **Why can a valid provider collection still reconcile?** The collection evidence may conflict with Payment/Intent/quote state; money must not be silently attached to an invalid successor.
75. **What does REFUND_REQUIRED mean?** There is an identified refund/reconciliation obligation; it is not confirmation that money has been returned.
76. **Does Swagger Authorize grant role access?** No. It only attaches a token; Spring Security and service rules still decide.
77. **What is a key known limitation?** Some asynchronous notification delivery is best-effort rather than a durable transactional outbox.
78. **What schema caution applies to FullSchema.sql?** It is for fresh setup, not repeatable deployment; existing DB uses guarded migrations.
79. **Is the SQL snapshot proven by Hibernate validate alone?** No; validate proves source-to-current-DB compatibility. Snapshot installation itself is a separate check.
80. **What future improvements are reasonable?** Durable outbox, richer explicit approval register, SQL Server concurrency coverage, updated SRD/ERD governance and operational monitoring—only after owner prioritization.
