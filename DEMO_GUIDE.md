# SportCenter — Hướng dẫn chạy demo hiện tại

**Cập nhật:** 2026-11-22
**Phạm vi:** Bản tích hợp hiện tại, dùng SQL Server clone đã xác minh cho demo/test.
**Không phải hướng dẫn triển khai production hoặc migration.**

## 1. Trước khi chạy

- Mở đúng repository `SWP391-Sport-Center-integration` và giữ nguyên branch/worktree đã được nhóm phê duyệt. Không chạy `git fetch`, `git switch`, `git reset` hay dọn worktree như một bước chuẩn bị demo.
- Clone đã được xác minh gần nhất: SQL Server `LAPTOP-416VBDLK`, database `SportCenterDemo_Phase5B4B2C1B_Rehearsal_20261121`. Clone này là môi trường kiểm thử tách biệt, không phải database phát triển mặc định.
- Kiểm tra lại identity và schema clone trước mỗi lần chạy có database. Tên database không tự chứng minh server hoặc database đang kết nối.
- Database gốc `SportCenterDemo` chưa có sáu cột Renewal collection. Không chạy ứng dụng Phase 5B vào database gốc; không chạy migration, seed hoặc script SQL trong buổi demo.
- Dùng tài khoản MEMBER, RECEPTIONIST, COACH và ADMIN được cấp qua kênh riêng của nhóm. Tài liệu này không chứa password. Không dùng lại Playwright storage state cũ nếu đã hết hạn.
- Backend cần JDK 21 và Maven Wrapper (`backend/mvnw.cmd`); frontend cần Node.js/npm và dependencies đã có sẵn. Không cần cài package hay thay cấu hình SQL Server trong lúc chuẩn bị demo.

## 2. Xác minh clone và Hibernate

Chạy từ `backend/`. Lệnh này là preflight chỉ đọc, dùng `CloneGuard`, kiểm tra đúng SQL Server/database, schema Renewal, và Hibernate `ddl-auto=validate`:

```powershell
.\mvnw.cmd -Pe2e-sqlserver test-compile `
  "-Dit.test=Phase5B4B2C1BHibernateValidationIT" `
  failsafe:integration-test failsafe:verify
```

Chỉ tiếp tục nếu kết quả `BUILD SUCCESS` và báo đúng clone. Kiểm tra gần nhất ngày 2026-11-22 cho thấy database `ONLINE`, Hibernate validation thành công, các bảng lịch sử có 146 dòng tổng cộng (Membership 27, Payment 97, Upgrade 10, Cancellation Request 12), không có fixture Phase 5B4B2C2V còn sót. Đây là kết quả tại thời điểm kiểm tra, không thay thế preflight hiện thời.

Nếu clone không truy cập được, identity sai, schema validation lỗi hoặc có bất thường, dừng. Không chuyển datasource sang `SportCenterDemo`, không backup/restore, không tạo database và không chạy migration để sửa lỗi.

## 3. Chạy backend an toàn

Mở PowerShell mới và chạy từ `backend/`. `application.properties` import `.env` tương đối với working directory này. Giữ `.env` hiện có; không chép đè bằng `.env.example`, không in hoặc đưa credentials vào command/log/Git.

Đặt datasource override chỉ trong process demo để không dùng giá trị mặc định `SportCenterDemo`:

```powershell
Set-Location D:\5School\Springboot\SWP391-Sport-Center-integration\backend
$env:SPRING_DATASOURCE_URL = 'jdbc:sqlserver://127.0.0.1:1433;databaseName=SportCenterDemo_Phase5B4B2C1B_Rehearsal_20261121;encrypt=true;trustServerCertificate=true'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:SPRING_JPA_DATABASE_PLATFORM = 'org.hibernate.dialect.SQLServerDialect'
$env:SPORTCENTER_SCHEDULING_ENABLED = 'false'
$env:SPORTCENTER_RENEWAL_SCHEDULER_ENABLED = 'false'
$env:PAYMENT_PROVIDER = 'mock'
$env:PAYMENT_MOCK_GATEWAY_ENABLED = 'true'
.\mvnw.cmd spring-boot:run
```

Chờ Hibernate validation và backend startup thành công trước khi mở frontend. Nếu URL, credentials, identity hoặc schema không xác minh được, dừng và sửa cấu hình cục bộ; không thử database khác. Khi kết thúc demo, dừng backend bằng `Ctrl+C` và xóa các process override trong terminal:

```powershell
Remove-Item Env:SPRING_DATASOURCE_URL, Env:SPRING_JPA_HIBERNATE_DDL_AUTO, Env:SPRING_JPA_DATABASE_PLATFORM, Env:SPORTCENTER_SCHEDULING_ENABLED, Env:SPORTCENTER_RENEWAL_SCHEDULER_ENABLED, Env:PAYMENT_PROVIDER, Env:PAYMENT_MOCK_GATEWAY_ENABLED -ErrorAction SilentlyContinue
```

Hai scheduler được tắt trong cấu hình demo: scheduler Renewal riêng và scheduler Membership chung (scheduler chung còn xử lý hết hạn/các legacy task). Không bật scheduler trong buổi demo thông thường. Không dùng `clean` để khởi chạy nếu chưa có lý do cụ thể.

## 4. Chạy frontend và đăng nhập

Mở terminal thứ hai:

```powershell
Set-Location D:\5School\Springboot\SWP391-Sport-Center-integration\frontend
npm run dev -- --host 127.0.0.1
```

Mở `http://127.0.0.1:5173`. Dùng luồng login bình thường với tài khoản được cấp riêng. Tạo/kiểm tra Playwright role state cục bộ bằng các script hiện có:

```powershell
npm run auth:setup -- --role ADMIN
npm run auth:setup -- --role RECEPTIONIST
npm run auth:setup -- --role MEMBER
npm run auth:setup -- --role COACH
npm run auth:check -- --role ADMIN
```

Lặp `auth:check` cho từng role cần dùng. Nhập credentials trực tiếp trong browser login; không lưu chúng trong repository. State nằm trong `frontend/.playwright/auth/`, có bearer token nên không chia sẻ hoặc commit. Nếu state hết hạn, đăng nhập lại; không sửa token, forge JWT hoặc bỏ qua role/API validation.

## 5. Renewal demo

Renewal Intent chỉ được tạo bởi scheduler cho Membership `autoRenew=true` đủ điều kiện trong cửa sổ ba ngày trước `endDate` độc quyền, với Plan đang ACTIVE và không có conflict. Không có thao tác Member/Receptionist tạo Intent thủ công. Do scheduler bị tắt trong cấu hình demo, cần chuẩn bị trước một Intent hợp lệ trong clone bằng quy trình test/kiểm soát đã được nhóm duyệt; nếu không có Intent phù hợp, chỉ trình bày các màn hình hiện có và không tạo dữ liệu trực tiếp trong demo.

### Member — ONLINE

1. Đăng nhập bằng Member đã có Intent hợp lệ và mở **Membership**.
2. Kiểm tra Plan snapshot, số tiền, deadline và trạng thái.
3. Tạo ONLINE attempt. Payment dùng mock/simulator; không cấu hình VNPay thật và không nhập thẻ thanh toán.
4. Browser Return không phải bằng chứng thanh toán: trạng thái phải còn pending cho tới khi backend nhận IPN hợp lệ.
5. Muốn trình bày trạng thái đã settle, dùng test harness callback giả lập có chữ ký hợp lệ trên clone hoặc fixture đã được chuẩn bị. Không tự gọi provider thật, không sửa database bằng tay.
6. Refresh Membership để đọc trạng thái backend. `REFUND_REQUIRED` là cần đối soát/hoàn tiền thủ công, không có nghĩa là đã hoàn tiền.

### Receptionist — COUNTER

1. Đăng nhập bằng Receptionist được phép và mở **Membership Sales**.
2. Chọn Member có Renewal Intent hợp lệ; xem quote, deadline và các attempts.
3. Tạo COUNTER attempt và xác nhận tại quầy bằng tài khoản staff đã xác thực.
4. paidAt do server tạo. Gửi lại xác nhận cho cùng Payment phải giữ nguyên một kết quả settlement/successor.
5. Dùng fixture có quyền sở hữu rõ ràng; không tạo giao dịch tiền mặt thật.

## 6. Kịch bản bốn luồng chính

Chỉ dùng tài khoản và dữ liệu demo hiện có trong clone. Kiểm tra trạng thái/điều kiện trước thao tác; không tự chạy seed SQL.

1. **User và Membership Management:** Admin/Receptionist xem hoặc quản lý tài khoản theo quyền; Member xem Membership history và snapshot đã mua. Ordinary Purchase bị chặn khi Membership hiệu lực/tương lai hoặc operation khác đang conflict; dùng Member đủ điều kiện sau expiry để trình bày Purchase.
2. **Course Registration và Scheduling:** Member xem course/class đang mở và lịch; kiểm tra Membership discount từ entitlement snapshot. Dùng dữ liệu lớp có chỗ và lịch phù hợp.
3. **Payment:** trình bày mock ONLINE hoặc COUNTER cho Purchase/Upgrade/Renewal. Upgrade chỉ khả dụng qua directed Plan Path được Admin cấu hình; quote dùng verified credit và target full duration. Không dùng VNPay thật hoặc tuyên bố Return URL đã settle giao dịch.
4. **Personal Training:** Member xem eligibility, discount và lịch/slot; chỉ chọn thời gian phù hợp với Coach/capacity. Không thực hiện giao dịch ngoài test data trong lúc demo.

## 7. Chuẩn bị tài khoản và dữ liệu

- Tài khoản demo cũ trong seed/guide có thể không tồn tại hoặc không phù hợp với schema clone hiện tại. Xác nhận trước từng role account qua API/login; lấy mật khẩu từ kênh riêng có thẩm quyền.
- Member Renewal cần Intent OPEN, quote chưa hết hạn, attempt chưa settle và không có collected evidence đang chờ reconciliation.
- COUNTER cần Receptionist/Admin ACTIVE. Upgrade cần path directed đang bật, source paid-value provenance verified, đủ ngày còn lại, và không có conflict.
- Course cần offering/session còn đăng ký; PT cần Membership entitlement, Coach và capacity. Nếu dữ liệu không sẵn sàng, đổi kịch bản demo thay vì sửa lịch sử hoặc thêm SQL fixture.
- Hãy chuẩn bị sẵn một tài khoản cho mỗi role và một tài khoản dự phòng. Không đưa email cá nhân, password hoặc token lên slide/chụp màn hình.

## 8. Giới hạn và xử lý sự cố

- Phase 7D (2026-11-22) đã hoàn tất hai Renewal browser journey: Member ONLINE và Receptionist COUNTER đều PASS. Các giới hạn Phase 7/7C nói hai journey này chưa được xác minh đã lỗi thời; xem mục 10–11 để biết kết quả chi tiết.
- Playwright xác thực đủ ADMIN, RECEPTIONIST, MEMBER và COACH; Coach Attendance PASS 3/3. Dùng `auth:setup` để đăng nhập lại qua UI nếu state hết hạn; không đoán mật khẩu hoặc forge JWT.
- Các Renewal fixture Phase 7D đều đã được cleanup. Không còn fixture Intent/Member/Plan/Payment để tái sử dụng. Intent đã settle không thể mở lại hoặc dùng cho lần thanh toán thành công khác; tạo bộ fixture test-owned mới theo mục 11 nếu cần chạy lại demo.
- Database gốc `SportCenterDemo` chưa được migrate sáu Renewal collection columns. Không chạy phiên bản ứng dụng này trên database gốc. Chỉ dùng clone rehearsal đã xác minh và clone guard; không tự thay datasource mặc định.
- Clone là môi trường rehearsal, không phải nơi lưu demo lâu dài. Giữ datasource override trong process demo riêng; không sửa `application.properties`, IDE run config hoặc `.env` để mặc định trỏ clone.
- Renewal scheduler phải tắt mặc định. Chỉ tạo fixture Intent qua test-only fixture harness đã xác minh; không bật scheduler hoặc tạo Intent thủ công qua UI/API.
- VNPay trong browser verification là giả lập; không có giao dịch provider thật. Browser Return không settle Renewal; chỉ callback giả lập đã ký trong test harness mới xác nhận settlement.
- Hibernate `validate` thất bại nghĩa là phải dừng và xử lý schema/configuration. Không đổi sang `update`, `create`, `create-drop`; không chạy migration/seed trong buổi demo.
- `REFUND_REQUIRED` có nghĩa là cần đối soát/hoàn tiền thủ công, không có nghĩa là tiền đã được hoàn. Không mô tả trạng thái này là “refunded”.
- Không có hướng dẫn backup/restore hoặc migration trong file này. Không ghi đè backup/database và không chạy SQL migration proposal.

## 9. Thứ tự demo đề xuất

1. Xác minh clone và chạy Hibernate `validate`.
2. Mở public catalogue, sau đó đăng nhập Member để xem Membership history và Course/PT eligibility.
3. Trình bày Purchase đủ điều kiện hoặc Upgrade có path/paid-value evidence sẵn.
4. Trình bày Renewal ONLINE pending/Return và giải thích settlement chỉ đến qua IPN được xác minh; nếu dùng COUNTER, xác nhận bằng tài khoản Receptionist.
5. Kết thúc bằng Payment history và trạng thái reconciliation chính xác; dừng các ứng dụng demo.

Nếu role account, dữ liệu hoặc Intent chưa được chuẩn bị, bỏ kịch bản đó và nêu rõ giới hạn; không tạo hoặc sửa dữ liệu lịch sử để kịp demo.

## 10. Phase 7C browser QA update — historical, superseded (2026-11-22)

> Historical snapshot only. Its statements that Member/Receptionist Renewal journeys were unverified and that there were no eligible intents were superseded by the successful Phase 7D browser runs in section 11. Use section 11 for the final Renewal journey result and fixture status.

- Role authentication was checked through the normal saved Playwright login state and authenticated APIs: ADMIN, RECEPTIONIST, MEMBER, and COACH all passed. Credentials and tokens remain private.
- Authenticated browser smoke passed for all four roles (4/4). Admin pages, Receptionist Membership Sales, Member Membership/Renewal and Courses, and Coach Dashboard/Attendance/Training/PT Sessions loaded without JavaScript page errors. Coach Attendance also passed at 1366px, 1440px, and 1920px (3/3). Two blocked external resources produced browser network warnings; authenticated API requests had no failed responses in the Coach test.
- The authenticated Member account currently has zero Renewal Intents. The Receptionist can select a Member and load the Renewal panel, but the available test data did not provide an eligible open Intent for a payment journey.
- Read-only clone inspection found the existing `autoRenew=true` Memberships end on 2026-12-12 or later, outside the approved three-day creation window as of 2026-11-22. No Membership dates or historical rows were changed to manufacture eligibility. No retained demo Renewal fixtures were created; the Member ONLINE and Receptionist COUNTER settlement journeys remain unverified in this browser run.
- Phase 7C did not create database fixtures or perform SQL writes. The original `SportCenterDemo` was not targeted. Run the authorized clone identity/Hibernate guard before any later fixture preparation.
- Demo readiness remains CONDITIONAL until a naturally eligible, test-owned Renewal Intent is available and the Member ONLINE and Receptionist COUNTER browser journeys are exercised against the clone. Existing route smoke is not payment settlement evidence.
- Cập nhật này thay thế trạng thái browser/auth cũ ở đoạn đầu mục 8. Có thể tạo lại state cho từng role qua `npm run auth:setup -- --role ADMIN|RECEPTIONIST|MEMBER|COACH`; nhập thông tin qua login UI, không lưu credentials trong repo.

## 11. Phase 7D Renewal browser verification (2026-11-22)

- Phần này cập nhật và thay thế kết luận Phase 7C bên trên về hai Renewal browser journey chưa được xác minh.
- Trên clone `LAPTOP-416VBDLK / SportCenterDemo_Phase5B4B2C1B_Rehearsal_20261121`, hai journey browser đã PASS: Member ONLINE và Receptionist COUNTER (2/2). Hibernate `ddl-auto=validate` và clone guard cũng PASS trước khi tạo fixture.
- Member flow đã xác minh quote/deadline, checkout giả lập cục bộ, Return vẫn `PENDING`, signed IPN settle một lần, IPN lặp được nhận diện là duplicate, chữ ký sai bị từ chối, Member khác không đọc được Intent, và Intent đã settle không nhận thêm attempt. Receptionist flow đã xác minh COUNTER confirmation bằng staff login, settlement và xác nhận lặp không tạo successor thứ hai.
- Không gọi VNPay thật. Playwright chặn URL sandbox và hiển thị trang giả lập cục bộ; IPN ký bằng khóa test chỉ tồn tại trong process kiểm thử. Không dùng khóa đó cho môi trường thật.
- Fixture tạm được tạo bằng test-only `backend/src/test/java/group5/example/sportCenter/e2e/Phase7DRenewalFixtureIT.java`, sau khi clone guard xác minh đúng clone. Fixture tạo Member/Plan/ACTIVE source Membership có `endDate = ngày chạy + 2` rồi gọi `MembershipRenewalIntentService` để tạo Intent; không sửa Membership lịch sử và không bỏ qua validation của production service. Tài khoản test và baseline được lưu tạm trong `frontend/.playwright/phase7d-fixtures.json` (ignored, chứa mật khẩu ngẫu nhiên); không mở, in, commit hoặc chia sẻ file này.
- Lệnh chuẩn bị fixture, chạy từ `backend/` sau khi clone datasource, `.env`, `ddl-auto=validate` và scheduler-off đã được xác minh:

  ```powershell
  .\mvnw.cmd -Pe2e-sqlserver test-compile `
    "-Dit.test=Phase7DRenewalFixtureIT" `
    "-Dphase7d.operation=setup" `
    "-Dphase7d.fixture.manifest=../frontend/.playwright/phase7d-fixtures.json" `
    failsafe:integration-test failsafe:verify
  ```

- Chạy browser test từ `frontend/` với backend/frontend ở `localhost`, Playwright đã cài và test-only VNPay key được truyền qua process environment (không ghi vào repo):

  ```powershell
  $env:PHASE7D_API_BASE_URL = 'http://localhost:8081'
  $env:PHASE7D_TEST_HASH_SECRET = '<ephemeral test-only key>'
  npx.cmd playwright test e2e/phase7d-renewal-browser.spec.js
  ```

- Sau test, chạy lại lệnh Maven với `-Dphase7d.operation=cleanup`. Cleanup xác minh ownership marker và fixture dependency trước xóa; sau đó so sánh toàn bộ persisted columns của `membership`, `payment`, `membership_upgrade`, `membership_cancellation_request` với baseline fixture. Kết quả lần này: fixture đã xóa, toàn bộ hàng/cột lịch sử khớp baseline, không có fixture còn sót.
- Không giữ lại Member, Plan, Membership, Intent hay Payment demo nào. Intent đã settle không tái sử dụng được; fixture QA kết thúc với `sourceEndDate=2026-11-24` và exclusive Renewal deadline `2026-11-24T00:00` theo `Asia/Ho_Chi_Minh`, nên cũng không còn hữu dụng cho demo sau đó. Muốn trình diễn lại, xác minh clone trước rồi tạo bộ fixture mới bằng test builder; không sửa hoặc mở lại Intent đã settle.
- Lần kiểm tra này không ghi vào `SportCenterDemo`, không đổi schema, và không dùng dữ liệu/fixtures của Phase 3D hoặc Phase 4C clone.

## 12. Checklist chuẩn bị demo Renewal

- [ ] **Clone và schema:** xác minh đúng `LAPTOP-416VBDLK / SportCenterDemo_Phase5B4B2C1B_Rehearsal_20261121`, database ONLINE, Renewal schema hiện diện và Hibernate `ddl-auto=validate` PASS. Không trỏ vào `SportCenterDemo`.
- [ ] **Khởi chạy:** backend từ `backend/` với `.env` hiện có, process datasource override trỏ clone, `ddl-auto=validate`, provider mock và cả Renewal/general scheduling tắt; frontend dùng `npm run dev -- --host 127.0.0.1`. Không ghi credentials vào lệnh/log/tài liệu.
- [ ] **Bốn role:** xác minh ADMIN, RECEPTIONIST, MEMBER, COACH qua `npm run auth:check -- --role <ROLE>`; nếu state cũ, đăng nhập lại bằng `npm run auth:setup -- --role <ROLE>` và login UI bình thường.
- [ ] **Fixture mới:** do fixture Phase 7D đã cleanup, chạy test-only `Phase7DRenewalFixtureIT` setup theo lệnh ở mục 11. Lưu manifest cục bộ, không mở/in/chia sẻ/commit.
- [ ] **Fixture hợp lệ:** xác nhận owner/role, Intent OPEN, không có pending attempt hay unresolved collection, quote frozen, Plan ACTIVE tại Intent creation, source `autoRenew=true`, và source `endDate` nằm trong cửa sổ ba ngày; Intent deadline là exclusive midnight của `endDate` theo `Asia/Ho_Chi_Minh`. Không kéo dài deadline.
- [ ] **Member ONLINE:** login Member fixture, kiểm tra quote/deadline, tạo ONLINE attempt, xác nhận Return vẫn PENDING, dùng simulated IPN test-only, refresh và kiểm tra settlement đúng một lần.
- [ ] **Receptionist COUNTER:** login Receptionist, mở Member fixture, kiểm tra quote/deadline, tạo COUNTER attempt, xác nhận bằng staff login và kiểm tra server paidAt cùng một successor; lặp xác nhận để minh họa idempotency.
- [ ] **Cleanup:** sau demo, chạy fixture harness với `-Dphase7d.operation=cleanup`; kiểm tra ownership và fixture absence, rồi xác minh protected historical rows không đổi. Không cleanup dữ liệu không thuộc manifest/fixture run.

Settled Intent không thể tái sử dụng. Mỗi lần demo settlement cần một fixture mới; không tạo fixtures trong tác vụ handoff này.