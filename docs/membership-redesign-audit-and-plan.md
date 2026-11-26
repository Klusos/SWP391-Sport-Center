# Membership Redesign, Upgrade Pricing Audit & Receptionist UX

> **Superseded scope note (2026-11-19):** The current owner direction removes ordinary Purchase Queue, replaces the previous remaining-time Upgrade proposal with a full target term funded by evidence-based unused paid-value credit, and approves scheduled Renewal that creates a successor Membership only after successful payment. The older audit below records source behavior at audit time. Do not implement its former recommendation to preserve the Upgrade end date or treat its Queue description as desired behavior. The current phase plan, retry/deadline design, migration gates, refresh UX, and unresolved decisions are in [`membership-final-redesign-implementation-plan.md`](membership-final-redesign-implementation-plan.md).

> **Current design update:** The owner’s latest direction supersedes the earlier Option B/C comparison and the remaining-day/end-date-preserving Upgrade proposal in this report. Renewal is now the scheduled, non-auto-charged Option C with one Renewal Intent and multiple payment attempts; Upgrade buys a full target term using verified unused paid-value credit. Follow [`membership-final-redesign-implementation-plan.md`](membership-final-redesign-implementation-plan.md) for current rules, schema proposal, gates and unresolved decisions. The source audit below remains evidence of prior implementation, not a statement that the new behavior is already implemented.

**Trạng thái:** Audit source và đề xuất thiết kế; chưa implement.
**Quyết định Queue:** yêu cầu hiện tại phê duyệt hướng bỏ Membership Queue cho giao dịch mới. Không đồng nghĩa phê duyệt sửa/huỷ các Queue đã có.
**Bằng chứng:** Đã đọc source backend/frontend, test liên quan, `docs/membership-benefit-snapshot-design.md` và `docs/post-defense-audit.md`. Không kết nối lại database trong lượt này. Số liệu database bên dưới chỉ là snapshot read-only ngày 2026-11-19 ghi trong audit trước đó.

## Tóm tắt

- Backend hiện cho một Member đang có Membership còn hiệu lực tạo Payment mua thêm; khi settlement, Membership mới bắt đầu ở `endDate` của period hiện tại. Đây là nguồn tạo Queue. Endpoint Membership trực tiếp cho ADMIN cũng nhận `startDate` trong tương lai.
- Upgrade pricing chạy ở backend. Ví dụ 60.000 VND và 1.630.000 VND khớp khi source/target mỗi Plan có duration một tháng. Code tổng quát so sánh giá theo ngày dựa trên `durationMonths`, dùng 30 ngày/tháng và làm tròn **lên** bậc 1.000 VND. UI lấy quote từ API; backend tính lại quote khi POST tạo Payment, nên giá Payment là authoritative, nhưng thay đổi Plan giữa lần lấy quote và tạo Payment có thể làm số trong dialog cũ khác số Payment trả về.
- Option B ít rủi ro và ít thay đổi hơn. Option C cần quyết định riêng về deadline/grace period, thanh toán sau expiry, cạnh tranh Upgrade/renewal và lịch sử entitlement. Payment COUNTER deadline tới nửa đêm cùng ngày không tương thích tự nhiên với yêu cầu tạo renewal request trước expiry 3 ngày.
- Nếu chọn Option C và gia hạn bằng cách kéo dài cùng Membership row, một bộ entitlement snapshot trên Membership sẽ bị ghi đè hoặc áp nhầm vào toàn bộ kỳ cũ. Thiết kế cần giữ snapshot từng kỳ đã mua; bảng terms/period riêng là phương án gọn và kiểm chứng được hơn việc nhân đôi nhiều cột trên Payment, Membership và Upgrade.
- Nút Refresh trang Membership Sales hiện refetch Membership data, pending counter payments, cancellation requests và member history. Refund queue có state/fetch độc lập và chỉ được refresh qua nút riêng của component, nên nút trang không refresh tất cả.

## 1. Membership Queue — hướng đã được duyệt, hành vi hiện tại

### Hành vi đang có trong source

1. `PaymentService.createPayment` với purpose `MEMBERSHIP` chặn period có `startDate > today` và Payment `MEMBERSHIP` đang `PENDING`; nó không chặn Member đang có Membership effective hôm nay.
2. `MembershipService.activateOrRenewFromPayment` tìm period ACTIVE cuối chuỗi và đặt Membership mới bắt đầu tại `endDate` đó, dùng duration của Plan ở thời điểm settlement. Vì vậy giao dịch paid khi period hiện tại còn hạn tạo period tương lai/Queue.
3. `MembershipUpgradeService.checkEligibility` chỉ cho một Membership đang effective; chặn nếu đã có Membership bắt đầu trong tương lai, Payment Membership/Upgrade PENDING, hoặc còn dưới 3 ngày. Upgrade thành công giữ `endDate` hiện tại.
4. Member purchase và Upgrade cùng kiểm tra pending Membership payments; auto-renew job cũng dùng member-row lock và bỏ qua khi có pending Membership/Upgrade payment. Callback settlement có kiểm tra conflict lần nữa; không được bỏ các kiểm tra này khi đổi quy tắc.
5. `MembershipService.createMembership` là đường ADMIN assignment không tạo Payment, nhưng hiện cho nhập `startDate` tương lai và không kiểm tra Plan ACTIVE. Nó cần được xem riêng khi cấm tạo period tương lai.

### Quy tắc mới được yêu cầu

- Không tạo purchase mới khi Member có Membership còn hiệu lực.
- Member đang active chỉ đi Upgrade qua Upgrade path hợp lệ.
- Không tạo Membership/purchase period có start trong tương lai để chờ kích hoạt.
- Membership đã hết hạn mới được mua mới.
- Chặn các Payment PENDING gây xung đột; giữ member lock, tính idempotent và settlement revalidation.
- Không tự xoá hoặc cập nhật các Queue cũ.

### Tác động và migration/data risk

Đây là thay đổi backend behavior, không chỉ ẩn nút. Cần một guard nghiệp vụ tập trung cho Member purchase, Admin assignment, renewal và Upgrade; phân biệt Membership `ACTIVE` có `endDate > today` với `EXPIRED`, `UPGRADED`, `CANCELLED` và các trạng thái thực tế khác. Với purchased Queue cũ, chưa xác định chính sách cho phép nó chạy hết hạn, hoàn/huỷ riêng, hay chặn mua/Upgrade cho Member đó đến khi hết Queue. Không thay status/dates/payment khi chưa có phê duyệt riêng.

Audit ngày 2026-11-19 từng ghi nhận ba future-start ACTIVE period: Membership #5007 (Payment #11023), #5014 (#11038) và #4003 (#9004). Đây không phải dữ liệu hiện tại. Owner đã xử lý một số Queue thủ công nhưng chưa có bằng chứng rằng cả ba hoặc Queue phát sinh sau đó đã hoàn tất. Trước triển khai cần truy vấn read-only đúng database để lập danh sách mới, gồm cả Payment status/refund, Member, khoảng ngày, auto-renew, Upgrade source/result và cancellation. Không chạy truy vấn ghi.

## 2. Upgrade pricing — xác minh code

### Công thức và làm tròn

`MembershipUpgradeService.proratedAmount(from, to, remainingDays)` dùng:

```text
raw = (targetPrice × sourceDurationMonths − sourcePrice × targetDurationMonths)
      × remainingDays
      ÷ (30 × sourceDurationMonths × targetDurationMonths)
amount = ceil(raw / 1,000) × 1,000 VND
```

Tương đương lấy chênh lệch giá mỗi ngày theo duration của từng Plan, nhân số ngày còn lại, rồi làm tròn **lên** bậc 1.000. `DAYS_PER_MONTH=30`, `ROUNDING_STEP=1000`. Không dùng phép trừ đơn giản theo giá niêm yết nếu duration khác nhau.

Ví dụ nêu trong yêu cầu:

- Premium 1.299.000/tháng → test 1.599.000/tháng: chênh lệch 300.000/30 = 10.000/ngày. 6 ngày = **60.000**; 163 ngày = **1.630.000**. Cả hai khớp source khi duration hai Plan đều là 1 tháng.
- Test 1.599.000 VND cao hơn giá niêm yết một tháng 1.599.000 khi còn 163 ngày. Code không cap Upgrade price ở giá target. Với policy giá theo ngày, số tiền này không nhất thiết là lỗi tính toán; owner cần chấp thuận rõ rằng trả chênh lệch cho toàn bộ remaining days có thể vượt giá một lần mua Plan đích.

### Remaining days, duration và kỳ Membership

- `remainingDays = ChronoUnit.DAYS.between(today_Vietnam, endDate)`. `endDate` là mốc exclusive. Upgrade chỉ có current Membership ACTIVE, `startDate <= today`, `endDate > today`, và `remainingDays >= 3`; vì vậy amount bằng 0 không thể đi qua quote path bình thường. Không có upgrade trong 0–2 ngày cuối.
- Thời gian thực tế của Membership đến từ ngày start/end; giá/ngày trong công thức dùng `durationMonths` của source và target Plan, không dùng `remainingDays / duration` suy ra từ ngày thực tế.
- `durationMonths` phải dương theo Plan validation đang có. Existing legacy Membership dài nhiều tháng không bị thêm validation khi đọc; remaining days tính theo ngày thực của record, còn source unit-price vẫn lấy duration của Plan **hiện tại**. Nếu Plan đã bị sửa kể từ lúc mua, báo giá legacy có thể không phản ánh giá/ngày gốc.
- Upgrade kết quả bắt đầu `today` nhưng giữ `endDate` cũ, auto-renew cũng được kế thừa. Không cộng target duration và không kéo dài thời hạn.
- Target phải ACTIVE, có giá/ngày cao hơn và không giảm course discount, PT discount hoặc hai entitlement boolean so với source Plan hiện tại. Upgrade source/target đều dùng current Plan mutable; chưa đọc snapshot nào.

### UI so với Payment và rủi ro

- `MemberMembership.jsx` hiển thị `option.amount` trong dialog từ `GET .../upgrade-options` và chỉ gửi method + target Plan ID khi tạo Payment.
- `PaymentService` gọi lại `MembershipUpgradeService.quote` tại thời điểm POST, lưu quote amount vào Payment và ghi `MembershipUpgrade`. Frontend hiển thị amount trả về sau khi tạo intent. Frontend không tự quyết định amount, nhưng dialog quote không được khóa version/quote token: nếu Plan/source thay đổi giữa GET và POST, Payment amount có thể khác số người dùng vừa thấy.
- Đề xuất trước khi Payment: POST quote tạo intent phải trả về số tiền server chốt; dialog hiển thị xác nhận cuối cùng từ response trước khi chuyển sang VNPay/Counter. Nếu cần quote hai bước riêng, thêm quote ID/version có expiry và server xác minh, tránh tin amount frontend.
- Existing Upgrade Integration Tests xác nhận 20 ngày với 500.000/30 ngày → 1.500.000/30 ngày ra 667.000; duration 3 tháng ra 134.000 cho 10 ngày; kiểm tra target benefit superset, min 3 days, giữ endDate, expiry và refund-required. Đây là integration tests, chưa chạy trong lượt audit này. Test giá 1.630.000 cần thêm test rõ ràng.

## 3. Renewal Option B và Option C

| | Option B — nhắc gia hạn | Option C — tạo payment request |
|---|---|---|
| Luồng | Đổi “Auto-renew” thành “Nhắc tôi gia hạn”; gửi reminder trước expiry; không tạo Payment. Sau khi hết hạn Member mua mới. | Scheduler tạo intent/payment request trước expiry; thông báo Member; chỉ gia hạn khi Payment thành công. Không thu tự động và không tạo Queue. |
| Fit với Queue removal | Tốt: không cần period future. Chặn mua cho đến khi endDate hết hạn; sau đó purchase bắt đầu theo quy tắc mua mới. | Khả thi nếu Payment settlement cập nhật kỳ đang có, không tạo Membership future-start mới. |
| Deadline | Dùng deadline mua mới sau expiry theo policy Payment chung. Không có pending renewal tồn tại trước expiry. | Mâu thuẫn cần giải quyết: generic COUNTER deadline đến nửa đêm cùng ngày, trong khi request phát sinh 3 ngày trước expiry. Nếu giữ generic deadline, intent thường hết hạn trước khi kỳ Membership kết thúc. Cần renewal-specific `valid_until` (ví dụ mốc expiry/grace window) hoặc cơ chế tái phát hành; lựa chọn/grace period cần owner phê duyệt. ONLINE cũng phải lấy min giữa deadline renewal và VNPay giới hạn. |
| Paid at/sau ngày hết hạn | Không có auto-renew request. Sau expiry Member có thể mua theo giá/terms hiện tại. | Chính sách chưa rõ: cho phép verified payment sau expiry trong grace window và nối từ endDate cũ, hay từ thời điểm trả tiền, hay từ chối/refund. Source hiện từ chối auto-renew `paidAt >= source.endDate.atStartOfDay()`; vì vậy hiện **không** cho thanh toán hợp lệ sau expiry. Cần quyết định có gap entitlement hay không. |
| Plan bị deactivated | Không có pending quote; reminder hướng Member tới Plan ACTIVE hiện tại. | Intent tạo khi Plan ACTIVE phải giữ snapshot và còn hiệu lực đến deadline đã chốt, dù Plan bị inactive sau đó. Scheduler hiện không tạo renewal nếu source Plan inactive. |
| Benefits & audit | Mỗi purchase sau expiry là Payment/Membership mới; snapshot theo mỗi purchase. | Renewal snapshot mới có thể khác snapshot kỳ cũ. Không ghi đè quyền lợi lịch sử. Cần lưu kỳ benefit và Payment liên kết một cách append-only. |
| Conflicts với Upgrade | Hết renewal scheduler nên bớt xung đột; active Member vẫn chỉ Upgrade. | Scheduler tạo request khi còn 3 ngày, đúng lúc Upgrade cho phép từ 3 ngày trở lên. Hai request hiện mutual-exclusion bằng Member lock/pending checks; bên tạo trước sẽ block bên kia. Cần owner chọn ưu tiên hoặc loại trừ một path theo trạng thái.
| Độ phức tạp/rủi ro | Thấp–vừa: bỏ scheduler Payment flow, đổi nhãn/state thành reminder, tạo notification và hướng dẫn mua lại sau expiry. Cần giữ audit các Payment đã tồn tại. | Cao: deadline/grace, renewal identity, late callback, concurrent Upgrade, plan deactivation, manual COUNTER receipt time, extending dates và per-period entitlement history. Dễ tạo duplicate/late settlement nếu không có idempotency và lock test.

**Khuyến nghị thiết kế:** ưu tiên B nếu mục tiêu là tối giản và loại queue/pending conflicts. Chọn C chỉ khi business xác nhận trải nghiệm thanh toán trước expiry có giá trị hơn chi phí/rủi ro và quyết định cụ thể deadline, grace, thời điểm hiệu lực, Upgrade priority, và lịch sử terms. Không tự chọn B hoặc C trong audit này.

## 4. Snapshot schema — minimal change analysis

### Chỉ lưu trên Membership đủ khi nào?

Một bộ cột trên `Membership` đủ nếu quyền lợi của Membership không đổi suốt toàn bộ record, Membership không bị kéo dài bằng lần mua có terms khác, và mọi pending intent chỉ cần giữ snapshot tạm ở một nơi khác. Nó phù hợp cho first purchase và Upgrade result (tạo Membership kết quả mới), nhưng **không đủ cho Option C** nếu renewal mở rộng cùng Membership row và snapshot quyền lợi renewal khác source.

### Phương án gọn, nhất quán cho cả B/C

Khuyến nghị một bảng append-only `MembershipTermsSnapshot`/`MembershipBenefitPeriod` với một hàng cho mỗi paid entitlement period hoặc Admin assignment:

- FK `payment_id` unique nullable; FK `membership_id` nullable khi intent pending, điền khi áp dụng; không unique theo membership để cho nhiều period nếu C.
- `plan_id` provenance only, Plan name, catalog price, durationMonths, course/PT discounts, `allowPersonalTrainingPlan`, `allowOneOnOneCoaching`, capturedAt, source, status/evidence.
- `period_start`, `period_end` lưu khoảng entitlement được quote (start inclusive/end exclusive) khi xác định được; lưu riêng với Membership tổng endDate. Upgrade period dùng remaining endDate hiện tại; renewal period bắt đầu theo chính sách được duyệt.
- `valid_until` trên snapshot hoặc Payment làm deadline intent; chỉ có một nguồn deadline chuẩn.
- Unique `payment_id`, unique theo `(membership_id, period_start)` khi có; index hiệu lực theo Membership và khoảng ngày. Check amounts/discount/duration cho VERIFIED, FK, at least one Payment/Membership reference. Immutable terms; linkage/status là metadata riêng.
- Payment vẫn là nguồn amount/status/paidAt/refund. Membership vẫn là nguồn trạng thái và tổng current endDate. CourseRegistration/PT booking giữ price snapshots riêng.

Bảng riêng hơi vượt “thêm cột vào Membership”, nhưng tránh duplicate snapshot fields trên Payment + Membership + Upgrade và giữ từng renewal benefit period. Nếu owner bắt buộc không có table mới, phương án thay thế phải thêm full terms vào Payment cho intent, Membership cho lần mua ban đầu/assignment, và period-history table hoặc equivalent append-only records cho C; thêm cột nhiều bảng mà không lưu history không giải quyết đúng quyền lợi lịch sử.

### Legacy UNKNOWN

Không suy ra benefit gốc từ Plan hiện tại. Backfill theo evidence: `LEGACY_VERIFIED` nếu có snapshot/backup/receipt/catalog version chứng minh toàn bộ terms; còn lại `LEGACY_UNKNOWN` với terms NULL. Demo baseline từ giá trị quan sát thời điểm cutover chỉ sau khi owner duyệt per-record preview và gắn `LEGACY_FROZEN_UNVERIFIED`; không gọi đây là quyền lợi gốc. Migration snapshot không đổi Membership start/end/status, Payment amount/status, Upgrade remainingDays, CourseRegistration hoặc PT booking giá đã lưu.

## 5. Receptionist Membership Sales — Refresh audit

### Hiện trạng

- Nút “Làm mới” của page gọi `load()` (Member + active Plans), `loadQueue()` (pending membership counter payments), `loadReviewQueues()` (cancellation requests), đồng thời tăng `historyVersion` để reload Membership/Payment của Member đang chọn.
- Refund queue là `ReceptionistRefundQueue` con, tự fetch `getRefundRequiredPaymentsApi` khi mount. Nó có nút refresh riêng. Nút cha không thay token/prop khiến component refetch, nên refund list không được làm mới từ nút page.
- Sau khi refund được xác nhận, child tự gọi lại `load`; sau xử lý Counter payment, parent refresh pending queue và history. Các path này không tạo full-page reload.
- Các phần có loading/error rời nhau; `loadReviewQueues` dùng error state chung. Nút cha disabled theo `loading || saving || queueLoading`, không có trạng thái chờ tổng hợp việc refresh refunds/history/review queue.

### Đề xuất UX tối thiểu

Giữ một nút page-level refresh và cho component refund nhận `refreshKey`/callback từ parent. Một lần refresh refetch Member/catalog, pending counter payments, cancellation requests, current member history/payments và Membership refunds; mỗi phần giữ loading/error riêng, không xóa dữ liệu cũ khi request thất bại, chống stale response, giữ page/filter/member selection, disable nút trong lúc refresh. Sau khi counter payment hoặc cancellation decision thành công, invalidate các nguồn tương ứng; refund confirmation vẫn chạy workflow hiện tại và refetch refunds. Không full page reload.

Regression test ở cấp component/API calls nên khẳng định page refresh làm GET cho năm feeds, giữ lựa chọn Member, trạng thái error/load riêng, và sau refund thành công danh sách cập nhật. Không test bằng cách thực sự xác nhận refund trong browser với dữ liệu shared.

## 6. Phases, dependencies and completion criteria

1. **A — Close pricing/policy decisions:** owner chọn Upgrade pricing basis (giữ current Plan hay source purchase snapshot), có cap hay không, quote confirmation UX, và giá trị cho legacy Plan duration. Done khi rule được ghi rõ; không source changes.
2. **B — Receptionist refresh UX:** prop-based refresh trigger, coordinated page refresh, local loading/errors and safe regression tests. Independent and low risk; can implement before Membership backend redesign.
3. **C — Queue removal:** fresh read-only database preflight; define legacy Queue policy; update backend purchase/assignment eligibility and API error contracts, then Member/Admin UI. Tests for active blocks purchase, expired allows purchase, active permits eligible Upgrade, pending purchase/Upgrade mutual exclusion, legacy future period behavior, and race/settlement. Done when no new ordinary purchase creates future-start Membership; existing rows remain byte-for-byte unchanged by this phase.
4. **D — Renewal choice (B or C):** dependency on Queue policy. B: reminder-only and remove scheduler Payment generation; test reminders/no Payment/no auto renewal. C: first approve deadline/grace/late-payment/Upgrade conflict and per-period history architecture; test scheduler idempotency, callback retries, Plan deactivation, payment near/after expiration, and exactly-once extension. Done when paid amount, expiry and period entitlements have one unambiguous audit path.
5. **E — Snapshot persistence and readers:** resolve legacy UNKNOWN runtime policy. Introduce chosen snapshot schema; transactional capture for purchase, Upgrade, renewal and assignment; then migrate Course discount, PT eligibility/discount, Individual Training Plan, membership history/UI. Tests per reader and no fallback to mutable Plan.
6. **F — Legacy reconciliation/migration:** exact target, read-only inventory/preview, evidence review, backup/restore rehearsal, approved verified rows only; demo baseline separately approved. Done when pre/post comparisons show Membership dates/status and all Payment/course/PT amounts unchanged.
7. **G — Regression and documentation:** update SRD/business-rule docs/UI labels after owner-approved B/C and field contracts; run backend tests (using safe configured DB), frontend test/lint/build, and authenticated role E2E where available. No phase may include data writes outside its separately approved migration.

## 7. Approval/questions required before implementation

1. For existing paid Queue rows not yet resolved: preserve to start/end naturally, block all transactions until their completion, or approve individual cancellation/refund review? No automatic action is proposed.
2. Does “no future Membership” apply to ADMIN assignment too? Current endpoint allows Admin-specified future start; require ACTIVE Plan and immediate start or disable that path?
3. Choose renewal B or C. If C: exact COUNTER deadline (same-day midnight or source expiry/grace), grace length, whether verified payment after Membership expiry is accepted, entitlement start/end for the gap, and handling if source Plan was deactivated.
4. Under C, should renewal beat Upgrade or should eligible Upgrade suppress scheduler intent? Exactly three days remaining currently qualifies for both paths.
5. Under C, approve append-only per-renewal entitlement periods and whether one Membership's total `endDate` is extended while each paid period keeps separate terms.
6. Confirm Upgrade pricing: keep current Plan source price/duration or use purchase snapshot; decide whether amount may exceed target Plan list price. Decide if server-created Payment response must be reconfirmed when it differs from displayed pre-intent quote.
7. Confirm legacy UNKNOWN entitlement behavior; no current Plan fallback is safe by default.
8. Confirm snapshot data model: approve one period table (recommended) versus multiple-column duplication across Payment/Membership/Upgrade.
9. Confirm Admin assignment remains a feature, allowed Plan status, reason requirement and audit representation.
10. Refresh behavior: should one button refresh all feeds, including refunds, and should a partial feed failure keep old data visible with a per-section error?

## Safety and verification boundary

This is source/test/document audit only. No SQL was run, no database rows were touched, no service/browser workflow was exercised, and no tests were executed in this turn. The previous audit's 3 queued Membership examples are dated evidence, not a current preflight. No application code, payment/refund logic or policy has been changed.
