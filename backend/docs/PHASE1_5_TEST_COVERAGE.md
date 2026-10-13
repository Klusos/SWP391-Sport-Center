# Phase 1.5 Consultation Request — ma trận kiểm thử

Tài liệu này ánh xạ TC01–TC53 theo các nhóm và phạm vi được nêu trong yêu cầu hiện tại. Nội dung chi tiết gốc của 53 test case từ cuộc trò chuyện trước không có trong workspace; vì vậy tên dưới đây là cách diễn giải bám theo nhóm, source code và hợp đồng DTO/controller hiện tại, không khẳng định là nguyên văn danh sách trước đó.

**Trạng thái:** JUnit chạy trên profile phase1 dùng H2 in-memory (MODE=MSSQLServer, create-drop), độc lập với SQL Server. Postman collection được tạo nhưng chưa chạy. “Một phần” có nghĩa phần HTTP/H2 được tự động hóa, còn hành vi SQL Server thật cần database kiểm thử riêng.

| TC | Kịch bản | JUnit coverage | Postman | Trạng thái |
|---|---|---|---|---|
| TC01 | Guest tạo request, trạng thái PENDING, memberId null | guestAndMemberSubmissionsLinkOnlyTheAuthenticatedMember; HTTP creation test | TC01 | Tự động JUnit; Postman chưa chạy |
| TC02 | MEMBER tạo request; danh tính lấy từ JWT, bỏ qua memberId tùy ý | guestAndMemberSubmissionsLinkOnlyTheAuthenticatedMember; httpCreationCoversAgeEmailLengthSportAndJwtIdentityValidation | TC02 | Tự động JUnit; Postman chưa chạy |
| TC03 | COACH không được gửi request bằng token đã xác thực | guestAndMemberSubmissionsLinkOnlyTheAuthenticatedMember; httpSecurityAllowsPublicOrMemberSubmissionAndRestrictsConsultationRoutes | TC03 | Tự động JUnit; Postman chưa chạy |
| TC04 | Thiếu trường bắt buộc trả 400 | httpSecurityAllowsPublicOrMemberSubmissionAndRestrictsConsultationRoutes; HTTP creation test | TC04 | Tự động JUnit; Postman chưa chạy |
| TC05 | Email sai định dạng trả 400 | httpCreationCoversAgeEmailLengthSportAndJwtIdentityValidation | TC05 | Tự động JUnit; Postman chưa chạy |
| TC06 | Tuổi ngoài 1–120 trả 400; biên 1 và 120 hợp lệ | HTTP creation test | TC06 | Tự động JUnit; Postman chưa chạy |
| TC07 | Sport không tồn tại trả 404; Sport INACTIVE trả 409 | HTTP creation test | TC07 (INACTIVE chưa có request riêng) | Một phần; Postman case INACTIVE cần bổ sung fixture/assert |
| TC08 | ADMIN/RECEPTIONIST liệt kê danh sách | httpSecurityAllowsPublicOrMemberSubmissionAndRestrictsConsultationRoutes; HTTP list test | TC08 | Tự động JUnit; Postman chưa chạy |
| TC09 | Lọc danh sách theo status | memberAndCoachCannotReadStaffQueueOrAnotherMembersRequest; HTTP list test | TC09 | Tự động JUnit; Postman chưa chạy |
| TC10 | Phân trang với page/size hợp lệ | HTTP list test | TC10 | Tự động JUnit; Postman chưa chạy |
| TC11 | Status hoặc pagination không hợp lệ trả 400 | Existing service test; HTTP list test | TC11 (pagination) | Tự động JUnit; Postman chưa chạy |
| TC12 | MEMBER/COACH bị từ chối danh sách staff | memberAndCoachCannotReadStaffQueueOrAnotherMembersRequest; HTTP role test | TC12 | Tự động JUnit; Postman chưa chạy |
| TC13 | MEMBER đọc request liên kết với chính mình | memberAndCoachCannotReadStaffQueueOrAnotherMembersRequest; HTTP list/detail test | TC13 | Tự động JUnit; Postman chưa chạy |
| TC14 | MEMBER không đọc request của MEMBER khác | Existing ownership test; HTTP list/detail test | TC14 | Tự động JUnit; Postman chưa chạy |
| TC15 | RECEPTIONIST không được đọc request chưa được giao cho mình | HTTP list/detail test | TC15 | Tự động JUnit; Postman chưa chạy |
| TC16 | COACH không đọc detail ConsultationRequest | Existing ownership test; HTTP list/detail test | TC16 | Tự động JUnit; Postman chưa chạy |
| TC17 | Detail thiếu JWT trả 401 | HTTP list/detail test | TC17 | Tự động JUnit; Postman chưa chạy |
| TC18 | RECEPTIONIST nhận PENDING, ghi handler/handledAt | pendingRequestCanBeTakenThenCompletedByItsHandler; HTTP lifecycle integration hiện có | TC18 | Tự động JUnit; Postman chưa chạy |
| TC19 | Take lại request không còn PENDING trả 409 | Lifecycle/concurrency integration tests | TC19 | Tự động JUnit; Postman chưa chạy |
| TC20 | Assigned handler đọc được request | HTTP list/detail test | TC20 | Tự động JUnit; Postman chưa chạy |
| TC21 | Không complete trước khi request được take | terminalStatesCannotTransitionAndOtherReceptionistsCannotModifyAssignedWork | TC21 | Tự động JUnit; Postman chưa chạy |
| TC22 | Tạo fixture riêng cho luồng cancel | HTTP creation test | TC22 | Tự động JUnit; Postman chưa chạy |
| TC23 | Take fixture trước khi cancel | Lifecycle tests | TC23 | Tự động JUnit; Postman chưa chạy |
| TC24 | Assigned handler hủy IN_PROGRESS, lưu lý do/thời điểm | pendingRequestCanBeTakenThenCancelledWithoutChangingPhaseOneRecords | TC24 | Tự động JUnit; Postman chưa chạy |
| TC25 | CANCELLED là trạng thái cuối | Terminal-state tests | TC25 | Tự động JUnit; Postman chưa chạy |
| TC26 | ADMIN override handler để complete/cancel IN_PROGRESS | adminCanOverrideAssignedHandlerForInProgressCompletionAndCancellation | TC26 | Tự động JUnit; Postman chưa chạy |
| TC27 | RECEPTIONIST khác không sửa request đã được giao | Existing handler ownership test | TC27 | Tự động JUnit; Postman chưa chạy |
| TC28 | MEMBER không take request | HTTP role tests | TC28 | Tự động JUnit; Postman chưa chạy |
| TC29 | Hai take thật sự đồng thời; chỉ một bên thắng | concurrentTakeRequestsHaveExactlyOneWinner chạy hai lời gọi trên thread/transaction riêng | TC29 (chỉ gọi tuần tự) | JUnit tự động; Postman chỉ kiểm tra xung đột tuần tự |
| TC30 | Tạo fixture có email cho luồng create-member | Create-member integration tests | TC30 | Tự động JUnit; Postman chưa chạy |
| TC31 | Staff assigned take trước khi tạo tài khoản | Create-member integration tests | TC31 | Tự động JUnit; Postman chưa chạy |
| TC32 | Tạo User role MEMBER và link trong cùng transaction; không trả credential | assignedHandlerCreatesMemberAndLinksInOneOperationWithoutReturningCredentials | TC32 | Tự động JUnit; Postman chưa chạy |
| TC33 | Tạo tài khoản lần hai cho consultation đã link bị từ chối | Existing create-member integration tests | TC33 | Tự động JUnit; Postman chưa chạy |
| TC34 | Handler không được giao không tạo MEMBER | Existing handler ownership tests; HTTP create-member test | TC34 | Tự động JUnit; Postman chưa chạy |
| TC35 | Password/dateOfBirth sai validation trả 400 | HTTP create-member test | TC35 | Tự động JUnit; Postman chưa chạy |
| TC36 | Tạo fixture để link MEMBER hiện có | Link integration tests | TC36 | Tự động JUnit; Postman chưa chạy |
| TC37 | Take fixture trước khi link | Link integration tests | TC37 | Tự động JUnit; Postman chưa chạy |
| TC38 | Link tài khoản MEMBER ACTIVE được chỉ định rõ | linksOnlyAnExplicitActiveMemberAndRejectsDuplicateOrInvalidTargets; HTTP link test | TC38 | Tự động JUnit; Postman chưa chạy |
| TC39 | Từ chối target không hợp lệ/khác member đã link | Existing link integration tests | TC39 | Tự động JUnit; Postman chưa chạy |
| TC40 | Tạo SkillAssessment đúng consultation/member/sport | skillAssessmentCanLinkToInProgressConsultationAndDoesNotCompleteIt | TC40 | Tự động JUnit; Postman chưa chạy |
| TC41 | Member khác consultation member trả 409 | skillAssessmentConsultationMustMatchMemberSportAndRemainInProgress | TC41 | Tự động JUnit; Postman chưa chạy |
| TC42 | Sport khác consultation sport trả 409 | Existing consultation/assessment integration test | TC42 | Tự động JUnit; Postman chưa chạy |
| TC43 | Consultation không IN_PROGRESS không nhận assessment; standalone vẫn được | Existing consultation/assessment integration test | TC43 | Tự động JUnit; Postman chưa chạy |
| TC44 | Assessment độc lập không cần consultationRequestId | Existing consultation/assessment integration test; assessment API tests | TC44 | Tự động JUnit; Postman chưa chạy |
| TC45 | Đọc assessment có liên kết | SkillAssessmentIntegrationTest HTTP/API coverage | TC45 | Tự động JUnit; Postman chưa chạy |
| TC46 | MEMBER khác không đọc assessment | SkillAssessmentIntegrationTest ownership coverage | TC46 | Tự động JUnit; Postman chưa chạy |
| TC47 | MEMBER không tạo assessment | SkillAssessmentIntegrationTest role matrix | TC47 | Tự động JUnit; Postman chưa chạy |
| TC48 | COACH hoàn thành assessment độc lập với consultation | Existing assessment lifecycle test; consultation remains IN_PROGRESS assertion | TC48 | Tự động JUnit; Postman chưa chạy |
| TC49 | SQL Server tạo đúng cột, kiểu, nullability, IDENTITY | H2 kiểm tra mapping qua Hibernate create-drop; migration chưa chạy trong test | Folder TC49, manual only | Một phần; SQL Server integration chưa chạy |
| TC50 | SQL Server FK: member, sport, handler và assessment consultation | H2 mapping/integration gián tiếp; không xác minh migration SQL Server | Folder TC50, manual only | Một phần; SQL Server integration chưa chạy |
| TC51 | SQL Server CHECK constraints cho status, age, name, phone | Không được chứng minh bởi H2 entity mapping | Folder TC51, manual only | Chưa tự động hóa SQL Server |
| TC52 | SQL Server indexes tồn tại và đúng cấu trúc | Không có assertion catalog SQL Server | Folder TC52, manual only | Chưa tự động hóa SQL Server |
| TC53 | Persistence/re-read và integrity sau thao tác | H2 repository/service integration; SQL Server persistence riêng chưa chạy | Folder TC53, manual only | Một phần; SQL Server integration chưa chạy |

## Chạy JUnit

ConsultationRequestIntegrationTest và các test hiện có dùng @ActiveProfiles("phase1"); test integration xác nhận JDBC URL bắt đầu bằng jdbc:h2:mem:phase1; src/test/resources/application-phase1.properties cấu hình jdbc:h2:mem:phase1;MODE=MSSQLServer;DB_CLOSE_DELAY=-1 và ddl-auto=create-drop.

    .\mvnw.cmd -Dspring.profiles.active=phase1 test

Không đổi test datasource sang SQL Server. Kết quả lần chạy Maven Wrapper cuối cùng: **73 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS; exit code 0**.

## Chạy Postman / Newman

Collection: docs/postman/SportCenter-Phase1.5-Consultation.postman_collection.json
Environment mẫu: docs/postman/SportCenter-Phase1.5-Consultation.postman_environment.json

Environment template để baseUrl ở http://127.0.0.1:1 và token trống để tránh vô tình gửi request vào ứng dụng đang trỏ tới SportCenterDB. Chỉ thay bằng URL của ứng dụng cấu hình với **database thử nghiệm tách biệt**, rồi điền token của ADMIN, hai RECEPTIONIST, hai MEMBER và COACH, cùng ID Sport/Coach. Các user cần là fixture riêng cho test; coachId phải được qualify với cả sportId và differentSportId. Hai Sport cần thiết cho TC42 đều phải ACTIVE. Environment có thêm receptionistUserId để kiểm tra handledBy. Token được đánh dấu secret, không lưu token/password vào collection. Collection tự tạo member mới với password sinh động ở runtime; ID mới được lưu tạm ở collection variables để các case sau dùng.

Collection Runner/Newman chạy theo thứ tự TC01–TC48 vì các request sau dùng ID từ request trước. TC29 là request tuần tự nên không thay thế được concurrency test JUnit. TC49–TC53 là ghi chú kiểm tra database, không gửi HTTP.

    newman run docs/postman/SportCenter-Phase1.5-Consultation.postman_collection.json --environment .\SportCenter-Phase1.5-Consultation.local.postman_environment.json

Tạo bản environment local riêng từ template, không commit bản chứa token. Chạy Newman/Postman chỉ sau khi xác nhận baseUrl trỏ tới app dùng disposable database, không phải SportCenterDB.

## Giới hạn

Danh sách mô tả nguyên văn TC01–TC53 không có trong workspace; ma trận dùng diễn giải rõ ràng từ category ranges, business rules và source hiện tại. H2 không xác minh SQL Server catalog/migration behavior. Việc test bằng SQL Server yêu cầu một database disposable tách biệt; không có kết nối hoặc thao tác SQL Server trong lượt kiểm thử này.


