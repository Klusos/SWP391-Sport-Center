# Hướng dẫn chạy demo SportCenter (Flow 1–4)

Dành cho thành viên nhóm đã có `SportCenterDB` trên SQL Server local, đang ở trạng thái của nhánh `main` (đã chạy các migration cũ và `demo-seed.sql`). Repo không có script tạo schema gốc, nên máy chưa có DB cần xin bản backup từ nhóm.

Thời gian: khoảng 15 phút.

## 1. Chuẩn bị

- JDK 21, Node.js 20 trở lên, SQL Server 2016 trở lên (đặt giờ máy theo giờ Việt Nam).
- `sqlcmd` (đi kèm SQL Server) hoặc SSMS.
- Lấy code:
  ```powershell
  git fetch
  git switch demo/flow4-and-fixes
  ```

## 2. Backup DB trước khi làm

Các migration bên dưới thay đổi schema và không có lệnh hoàn tác. Chạy trong SSMS (đổi đường dẫn cho phù hợp):

```sql
BACKUP DATABASE SportCenterDB TO DISK = N'C:\backup\SportCenterDB_before_demo.bak' WITH COPY_ONLY, INIT, CHECKSUM;
```

Tắt backend nếu đang chạy.

## 3. Chạy migration và dữ liệu demo

Mở PowerShell ở thư mục `backend\docs\sql`. Chạy **đúng thứ tự**, mỗi file một lần; dừng lại nếu có lỗi. Thay `<user>` bằng tài khoản SQL Server (sqlcmd sẽ hỏi mật khẩu):

```powershell
$files = @(
  "phase8-7b-facility-sports.sql",
  "phase8-7c-course-registration-holds.sql",
  "phase9-1-support-tickets.sql",
  "phase9-2a-membership-purchase-refactor.sql",
  "phase9-2a1-membership-renewal-foundation.sql",
  "phase9-2a1-membership-renewal-scheduler.sql",
  "phase9-2a1-c-renewal-checkout-attempts.sql",
  "phase9-2a1-c1-applied-membership-link.sql",
  "phase9-3-membership-upgrade-payment-drafts.sql",
  "phase9-4-membership-upgrade-settlement.sql",
  "phase9-5-membership-upgrade-checkout-integration.sql",
  "phase10-1-flow4-training.sql"
)
foreach ($f in $files) {
  sqlcmd -S localhost -d SportCenterDB -U <user> -C -b -I -f 65001 -i $f
  if ($LASTEXITCODE -ne 0) { Write-Host "Loi o $f - dung lai"; break }
  Write-Host "OK $f"
}
```

- Nếu DB **chưa từng chạy** `demo-seed.sql` (không có tài khoản `demo.*`): mở file, đổi `@ConfirmLocalDemoDatabase BIT = 0` thành `1`, chạy nó **trước** vòng lặp trên.
- Dữ liệu demo cho Flow 2–4: mở `demo-seed-flow2-4.sql`, đổi `@ConfirmLocalDemoDatabase BIT = 0` thành `1` (không commit thay đổi này), rồi chạy:
  ```powershell
  sqlcmd -S localhost -d SportCenterDB -U <user> -C -b -I -f 65001 -i demo-seed-flow2-4.sql
  ```
  Ngày giờ trong dữ liệu tính theo ngày chạy script. Chạy lại lần hai sẽ báo "already exists" và không thay đổi gì.
- Dùng SSMS cũng được: mở từng file theo thứ tự rồi Execute.

## 4. Cấu hình backend

```powershell
cd backend
copy .env.example .env
```

Điền `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `PAYMENT_CALLBACK_SECRET` (hướng dẫn tạo nằm ngay trong file). Muốn thanh toán thật qua VNPay sandbox thì đặt `PAYMENT_PROVIDER=vnpay` và điền `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` (xin riêng trong nhóm, không đưa lên Git). Để `mock` thì vẫn demo được, chỉ là cổng thanh toán giả lập.

Không dùng `run.bat` cho bản demo này: file đó chỉ bật cổng giả lập và ghi đè thông tin đăng nhập DB.

## 5. Chạy

```powershell
# Terminal 1
cd backend
.\mvnw.cmd spring-boot:run

# Terminal 2
cd frontend
npm install
npm run dev
```

Backend: http://localhost:8080 — Frontend: http://localhost:5173. Backend lên được nghĩa là schema đã đúng (ứng dụng chạy `ddl-auto=validate`).

## 6. Tài khoản

Mật khẩu chung ghi trong `backend/docs/sql/README.md` (mục demo seed). Email đều có đuôi `@sportcenter.example.test`.

| Vai trò | Email | Dùng để |
|---|---|---|
| Admin | `demo.admin` | Quản lý khóa học, lớp, lịch; báo cáo doanh thu |
| Lễ tân | `demo.reception1` | Xác nhận thanh toán tại quầy |
| Coach | `demo.coach1` | Điểm danh, giáo án, kết quả, đánh giá (lớp "Demo Seed Lớp tối T3-T5") |
| Member | `demo.member01` | Đăng ký lớp mới và thanh toán |
| Member | `demo.member02`, `demo.member03` | Đã học lớp tối T3-T5, có sẵn kết quả |

## 7. Kịch bản demo

**Flow 2 – Đặt lớp và lịch học** (`demo.member01`)
1. Khóa học → Demo Badminton → "Demo Seed Cầu lông cơ bản".
2. Chọn "Demo Seed Đợt sắp khai giảng" → xem lịch 8 buổi của "Demo Seed Lớp sáng T7-CN" → Đăng ký.

**Flow 3 – Thanh toán**
1. Vẫn `demo.member01`: tại đăng ký vừa tạo, chọn thanh toán VNPay (hoặc tại quầy).
2. VNPay sandbox, thẻ test NCB: số thẻ `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`.
3. Nếu chọn tại quầy: đăng nhập `demo.reception1` → "Thanh toán khóa học" → xác nhận.
4. `demo.admin` → Tổng quan → "Mở báo cáo doanh thu".

**Flow 4 – Huấn luyện và điểm danh** (`demo.coach1`)
1. "Giáo án & kết quả" → lớp "Demo Seed Lớp tối T3-T5": xem mục tiêu và 4 bài tập; thử thêm/sửa một bài.
2. Tab "Buổi học & kết quả": buổi 3 ngày trước đang "Đã lên lịch" và có nút "Hoàn thành".
3. Sang mục "Điểm danh" → điểm danh buổi đó (member02 có mặt).
4. Quay lại "Giáo án & kết quả" → bấm "Hoàn thành" → "Ghi kết quả" → nhập điểm cho member02. Member chưa có mặt sẽ không nhập được.
5. Tab "Đánh giá tiến độ" → ghi đánh giá.
6. Đăng nhập `demo.member02` → "Kết quả tập luyện": thấy giáo án, chuyên cần, điểm từng buổi và đánh giá vừa ghi.

## 8. Lỗi thường gặp

| Lỗi | Cách xử lý |
|---|---|
| Backend báo `Schema validation: missing table/column ...` | Còn migration chưa chạy; xem lại bước 3 |
| `Existing directory '...\Temp\...' is not owned by ...` khi khởi động | Chạy `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Djava.io.tmpdir=C:\tmp\sportcenter"` (tạo thư mục trước) |
| `ClassFormatError` khi khởi động | `.\mvnw.cmd clean spring-boot:run` |
| Tiếng Việt thành `?` sau khi chạy seed | Thiếu `-f 65001` khi chạy sqlcmd; khôi phục backup rồi chạy lại |
| Seed báo thiếu `demo.coach1`… | Chạy `demo-seed.sql` trước |
| Muốn quay lại như cũ | Khôi phục file backup ở bước 2 |
