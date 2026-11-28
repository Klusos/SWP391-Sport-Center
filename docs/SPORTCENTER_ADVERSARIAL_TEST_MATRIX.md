# Ma trận adversarial và bằng chứng kiểm thử SportCenter

**Snapshot:** nhánh `qa/team-integration-20261122`, HEAD `b2bbde7d737873f57029919db0ec921300861921`, 28/11/2026. Ma trận này là inventory và kế hoạch tấn công hợp lệ có kiểm soát, không phải tuyên bố đã chạy mọi trường hợp. Không chạy test ghi DB5, gọi cổng thanh toán thật, load test hay vulnerability scan trong lượt tài liệu này.

## Quy ước trạng thái

- **PASS-2026-11-28**: chạy trong ba class backend tập trung, H2; chỉ bao phủ assertion của test đó.
- **RECORDED-PASS**: có evidence acceptance trước đó trong report/test artifact, nhưng không chạy lại lượt này.
- **NOT_RUN**: test hiện có hoặc rủi ro đã xác định nhưng chưa có bằng chứng thực thi được ghi nhận trong lượt này.
- **MISSING-COVERAGE**: chưa xác định test cụ thể bao phủ trường hợp; cần bổ sung trước khi tuyên bố test acceptance.
- H2 không chứng minh SQL Server locking/dialect/schema. “Có test class” không đồng nghĩa class đã pass.

## Bổ sung sau phê duyệt hardening ngày 28-11-2026

Các ca dưới đây bổ sung cho ma trận hiện có; không thay thế các ca trước. `PASS-2026-11-28` chỉ áp dụng cho đúng test/method được nêu. Các ca ghi `MISSING-COVERAGE` chưa được chứng minh bằng test trực tiếp.

| Test ID | Ưu tiên | Điều kiện | Actor | Hành động adversarial | Kết quả mong đợi | DB effects mong đợi | Bằng chứng | Trạng thái |
|---|---|---|---|---|---|---|---|---|
| HARD-AUTH-01 | P0 | Admin caller cập nhật chính mình | Admin | Hạ role hoặc deactivate qua `PUT /api/users/{id}` | 409/403, không đổi User | Không đổi role/status | `UserController.updateUser` truyền JWT; `UserService.updateUser` guard; test trực tiếp chưa có | MISSING-COVERAGE |
| HARD-AUTH-02 | P0 | Chỉ còn một Admin ACTIVE | Admin | Deactivate/demote qua mọi route | Từ chối; vẫn còn Admin dùng được | Không đổi | `UserService` last-Admin guard; direct API tests chưa có | MISSING-COVERAGE |
| HARD-AUTH-03 | P0 | Có Coach assignments/obligations | Admin | Deactivate qua staff, Coach và generic user routes | Conflict có giải thích; không tự chuyển việc | Không đổi | `CoachDeactivationGuard`, `StaffManagementService`, `CoachService`, `UserService`; chưa chạy test | MISSING-COVERAGE |
| HARD-AUTH-04 | P0 | Member có paid entitlement/booking và token cũ | Admin/Member | Deactivate rồi sử dụng JWT cũ/reactivate | Access bị từ chối khi inactive; thương mại/lịch sử giữ nguyên | Không xóa/cancel/refund/extend | `MemberRegistrationService` status-only + khóa User; chưa có test mới | MISSING-COVERAGE |
| HARD-FIN-01 | P0 | SUCCESS purchase, Upgrade, Renewal; pending/refund khác | Admin | Xem tổng Membership revenue theo ngày | Tách loại, cộng tổng không trùng; pending không tính | Không đổi | `PaymentRepository.membershipRevenueByCategory`, `PaymentReportService`; test Renewal/refund riêng chưa có | MISSING-COVERAGE |
| HARD-PLAN-01 | P0 | Plan price 0/âm trực tiếp | Admin | POST/PUT plan | 400; không lưu | Plan không đổi | `MembershipPlanIntegrationTest` thêm zero create/update; thuộc 59-test run nhưng cần xác nhận phương thức trong báo cáo surefire | PASS-2026-11-28 class |
| HARD-PLAN-02 | P0 | Active Upgrade Paths vào/ra Plan | Admin | Sửa giá bằng hoặc thấp hơn Plan tương ứng | Conflict chỉ với active path; inactive path không chặn | Không đổi path/quote | `MembershipPlanService.updatePlan`; matrix cases chưa có | MISSING-COVERAGE |
| HARD-PLAN-03 | P0 | Đồng thời sửa Plan và activate path | Admin A/B | Chạy song song | Chỉ trạng thái giá/path hợp lệ được commit | Không có path active target<=source | Locking source inspected; SQL Server concurrency chưa chạy | MISSING-COVERAGE |
| HARD-PASS-01 | P1 | Tạo/đổi mật khẩu 6, 7, 8 ký tự | Admin/Member/Receptionist | Gọi API trực tiếp | <8 bị 400, 8 được xử lý theo rules còn lại | Không lưu password không hợp lệ | DTO `@Size(min=8)`; direct boundary tests chưa có | MISSING-COVERAGE |
| HARD-CONS-01 | P0 | Member Course Consultation | Member | Mở form/gửi identity tampering | Contact fields read-only; backend dùng account | User không đổi | `CourseConsultationModal`, `ConsultationRequestService`; Consultation suite 59-test run | PASS-2026-11-28 class; modal browser NOT_RUN |
| HARD-CONS-02 | P1 | Guest DOB hôm nay/ngày mai | Guest | Gửi form/UI/API | UI giới hạn hôm qua; backend từ chối hôm nay/tương lai | Không tạo request sai DOB | `ConsultForm`, `ConsultationRequestService`; browser boundary chưa chạy | PARTIAL |
| HARD-SUPPORT-01 | P1 | viewer ID hoặc assigned ID null | Staff | Mở ticket chưa claim | Không hiện quyền reply do `null===null` | Không đổi | `SupportTickets.jsx`; frontend test/full suite 611/611 | PASS-2026-11-28 |
| HARD-SUPPORT-02 | P1 | Member/staff reply không đổi status | Member/Staff | Gửi reply | `updatedAt` mới, ticket được đưa lên đầu danh sách | Message + timestamp cùng transaction | `SupportTicketIntegrationTest` 7/7 trong 59-test run | PASS-2026-11-28 |
| HARD-PT-01 | P1 | Pending Payment hold còn hạn/expired | Member | Hủy booking hoặc callback muộn | UI chỉ expose cancellation backend cho phép; late collection giữ safeguards | Không tạo quyền refund mới | `PtBooking.jsx`; frontend suite 611/611; late callback chưa rerun | PARTIAL |
| HARD-ASSESS-01 | P1 | Coach được gán và Coach khác | Coach A/B | Đọc assignment | A thấy tên Member/Sport; B vẫn bị từ chối | Không lộ dữ liệu ngoài assignment | `SkillAssessmentResponse`, repository EntityGraph; `SkillAssessmentIntegrationTest` 21/21 | PASS-2026-11-28 class |
| HARD-SPORT-01 | P1 | Sport chỉ có request Member-owned PENDING | Member | Mở Sport overview | Sport hiển thị nhưng không cấp course/PT eligibility | Không đổi | `memberSportEligibility.js`; frontend suite 611/611 | PASS-2026-11-28 |
| HARD-TRAIN-01 | P2 | Search khóa học không khớp tên lớp | Coach | Chọn course rồi tìm lớp/quay lại | Tìm lớp độc lập; course query được giữ khi back | Không đổi | `CoachTraining.jsx`; frontend suite 611/611 | PASS-2026-11-28 |
| HARD-CANCEL-01 | P1 | Consultation ở bất kỳ trạng thái nào | Receptionist/Admin | Tìm action cancellation trên queue UI | UI cố ý không cung cấp action theo yêu cầu owner; backend API contract vẫn giữ nguyên | Không đổi | Owner instruction 2026-11-28; frontend regression asserts absence; API/backend unchanged | WITHDRAWN_FROM_UI |
| HARD-PARENT-01 | P0 | Parent có dependent unfinished obligations | Admin | Deactivate Sport/Course/Offering/Class | Conflict, không cascade | Không đổi descendant/financial/history | Guard hiện chưa được implement | MISSING-COVERAGE / NOT_IMPLEMENTED |

| Test ID | Ưu tiên | Điều kiện | Actor | Hành động adversarial | Kết quả HTTP/domain mong đợi | DB effects mong đợi | Bằng chứng hiện có | Trạng thái thực thi |
|---|---|---|---|---|---|---|---|---|
| AUTH-01 | P0 | Không JWT | anonymous | GET private profile | 401 | Không đổi | `ConsultationRequestIntegrationTest.httpSecurityAllowsPublicOrMemberSubmissionAndRestrictsConsultationRoutes` | PASS-2026-11-28 class |
| AUTH-02 | P0 | JWT role sai | Coach | Đọc consultation staff queue | 403 | Không đổi | `ConsultationRequestIntegrationTest.memberAndCoachCannotReadStaffQueueOrAnotherMembersRequest` | PASS-2026-11-28 class |
| AUTH-03 | P0 | Member A/B tồn tại | Member A | GET Member B consultation/profile/payment by ID | 403/404 theo endpoint; không có dữ liệu B | Không đổi | Consultation ownership test; Payment role/ownership cases | PASS-2026-11-28 class đối với Consultation; payment chưa chạy |
| AUTH-04 | P0 | Coach không được gán lớp | Coach | Đọc/ghi class training/attendance ngoài assignment | 403 | Không đổi | `TrainingIntegrationTest.adminReadsTrainingButCannotWriteThroughHttpOrDirectServiceCalls` và assignment tests | NOT_RUN |
| AUTH-05 | P0 | Sai role | Member | Ghi attendance hoặc training result | 403 | Không đổi | `TrainingIntegrationTest.unauthenticatedAccessMemberWritesAndInvalidReferencesAreRejected` | NOT_RUN |
| AUTH-06 | P0 | Admin đọc-only Training | Admin | PUT General plan | 403 | Không đổi | `TrainingIntegrationTest.adminReadsTrainingButCannotWriteThroughHttpOrDirectServiceCalls` | NOT_RUN |
| AUTH-07 | P0 | JWT hết hạn/thu hồi | authenticated | Dùng token hết hạn/revoked | 401 | Không đổi | Security/JWT tests; xác nhận bằng test inventory trước run | NOT_RUN |
| AUTH-08 | P0 | Sai role | Receptionist | Dùng Admin-only endpoint | 403 | Không đổi | `PaymentIntegrationTest.httpRolesAreEnforcedOnPaymentAndReportEndpoints` (payment route) | NOT_RUN |
| CONS-01 | P0 | Member JWT; body chứa name/email/phone/memberId khác | Member | POST Consultation đã sửa identity | thành công bằng identity từ principal/account; payload giả bị bỏ qua | Consultation gắn đúng Member, không lộ data người khác | `ConsultationRequestIntegrationTest.memberApiIgnoresTamperedIdentityAndMemberIdFields` | PASS-2026-11-28 class |
| CONS-02 | P0 | Guest nhập email đã có account | Guest | POST Consultation | validation/conflict generic, không tiết lộ account | Không tạo row | `ConsultationRequestIntegrationTest.httpGuestSubmissionRejectsEmailAlreadyOwnedByAnAccountWithoutDisclosingAccount` | PASS-2026-11-28 class |
| CONS-03 | P1 | Legacy consultation thiếu DOB | Receptionist | Tạo Member với DOB hợp lệ | cho phép theo fallback hiện tại | Link/account tạo atomically | `legacyConsultationWithoutDobUsesValidatedSubmittedDob` | PASS-2026-11-28 class |
| CONS-04 | P1 | Consultation có DOB | Receptionist/Admin | Gửi DOB khác trong payload create-member | DOB persisted của request thắng | Member lấy đúng DOB authoritative | DOB tests trong class | PASS-2026-11-28 class |
| CONS-05 | P0 | Phone sai (thiếu số, ký tự, Unicode digit) | Guest/Member | POST API trực tiếp | 400 | Không tạo consultation | `httpConsultationRejectsInvalidPhoneWithoutPersisting(String)`; live DB5 smoke invalid phone=400 | PASS-2026-11-28 class + live HTTP |
| CONS-06 | P1 | Hai request cùng email Guest | Guest | Tạo liên tiếp | Email account conflict vẫn kín; cùng email Consultation có rule riêng | Không account duplicate | `serviceRejectsMissingOrInvalidEmailAndAllowsRepeatedConsultationsForOneEmail` | PASS-2026-11-28 class |
| CONS-07 | P0 | Hai Receptionist cùng claim request | Receptionist A/B | Claim đồng thời | một winner; loser conflict | Một assignee/audit transition | `concurrentTakeRequestsHaveExactlyOneWinner` | PASS-2026-11-28 class |
| MEM-01 | P0 | Email đã tồn tại | Admin/Receptionist | Tạo Member trùng email | conflict/validation | Không duplicate users | account API tests in Consultation class; unique index in SQL | PASS-2026-11-28 class |
| MEM-02 | P1 | Phone hợp lệ trùng | Staff | Tạo/cập nhật hai record cùng phone | được phép | hai giá trị giữ nguyên | `consultationPhoneIsTrimmedAndDuplicatePhoneNumbersRemainAllowed` | PASS-2026-11-28 class |
| MEM-03 | P0 | Phone sai gửi trực tiếp | Staff | Tạo account qua API bỏ frontend | 400 | Không tạo account | `memberAccountApiRejectsInvalidPhoneWithoutCreatingAccount(String)` | PASS-2026-11-28 class |
| FIN-01 | P0 | Member không sở hữu Payment ID | Member A | Pay/cancel Membership/PT của Member B | 403/404 | Không đổi | `PaymentIntegrationTest.memberCannotPayForAnotherMembersRegistration` (registration); other ownership tests | NOT_RUN |
| FIN-02 | P0 | Target price/amount bị sửa ở client | Member | POST Payment với amount thấp/khác quote | reject hoặc server dùng quote authority | Không thu/ghi sai tiền | Payment/upgrade frozen quote tests exist | NOT_RUN |
| FIN-03 | P0 | Amount provider khác Payment | VNPay callback | gửi amount mismatch | reject/reconciliation, không SUCCESS | Payment stays protected state; no entitlement | `VnpayPaymentGatewayTest`, `PaymentIntegrationTest`, `Phase5BFinalRenewalHttpIT` | NOT_RUN |
| FIN-04 | P0 | Signature sai | VNPay IPN | sửa hash/query | reject | no settlement, no Membership | VNPay gateway test inventory | NOT_RUN |
| FIN-05 | P0 | Callback success gửi lặp | same txn | replay IPN | idempotent | one successor/one audit | Renewal settlement and Payment integration suites | NOT_RUN this task; prior acceptance recorded |
| FIN-06 | P0 | Callback sau deadline/cancel | Payment expired/cancelled | gửi success sau cutoff | reconciliation/refund obligation, no revived entitlement | preserve original state/lineage | `PaymentIntegrationTest.successAfterMemberCancelledIsRoutedToRefund`; delayed course callback tests | NOT_RUN |
| FIN-07 | P0 | Dùng transaction reference trùng Payment khác | provider | replay ref on another order | reject/reconcile | no cross-association | Payment signature/transaction tests | NOT_RUN |
| FIN-08 | P0 | Payment writes side effect fails | any financial flow | induce audit/refund persistence failure | whole transaction rollback | no partial obligation/state | `PaymentIntegrationTest.cancellationRollsBackIfRefundRequiredPaymentWriteFails` | NOT_RUN |
| FIN-09 | P0 | Hai xác nhận cùng Payment | staff/provider | concurrent/repeated confirm | one transition | one entitlement/confirmation | `PaymentIntegrationTest.queueConfirmationActivatesOnceAndCancellationNeverActivates`; `PaymentIntegrationTest.replayedSuccessfulPtPaymentCallbackDoesNotDuplicateConfirmationNotices` | NOT_RUN |
| FIN-10 | P0 | Refund confirmation lặp | staff | confirm already refunded obligation | conflict/idempotent without second refund | no duplicate refund evidence | `PaymentIntegrationTest.duplicateRefundConfirmationAndDelayedCallbacksDoNotReviveCancelledRegistration` | NOT_RUN |
| MEMB-01 | P0 | Plan benefits edited after purchase | Admin edits plan | Member reads purchased entitlement | snapshot unchanged | Membership snapshot untouched | `MembershipSnapshotFoundationTest`, Payment tests | NOT_RUN |
| MEMB-02 | P1 | Duplicate plan purchase request | Member | submit twice concurrently | second blocked by eligibility/payment guard | max one actionable purchase | Membership/Payment integration tests | NOT_RUN |
| MEMB-03 | P1 | Renewal preference off | Member | invoke lifecycle outside window | no Intent | no Intent/Payment | Renewal lifecycle tests | NOT_RUN |
| MEMB-04 | P0 | Eligible source, exact window | scheduler lifecycle | repeated/concurrent generation | exactly one OPEN Intent | one valid active Intent | `MembershipRenewalLifecycleServiceTest`, `MembershipRenewalIntentServiceTest` | NOT_RUN this task |
| MEMB-05 | P0 | Renewal payment amount/association tampered | Member/staff | create attempt with other amount/paymentId | server frozen quote and persisted association win | no swapped Payment lineage | Renewal attempt/settlement suites | NOT_RUN |
| MEMB-06 | P0 | Duplicate successful Renewal settlement | provider | replay callback | one SETTLED Intent, one successor | one Membership + success audit | `Phase5BFinalRenewalHttpIT`, settlement suites | Prior acceptance recorded; not rerun |
| MEMB-07 | P1 | Disable Renewal after successful payment | Member | turn off future preference | successor remains | no cancellation/refund | owner policy + Renewal tests | NOT_RUN |
| UP-01 | P0 | No configured directed path | Member | initiate upgrade | conflict | no Upgrade/Payment | `MembershipUpgradeIntegrationTest.optionsListOnlyAdminConfiguredPathsWithVerifiedCreditQuote` | NOT_RUN |
| UP-02 | P0 | Reverse path absent | Member | request reverse target | not offered/rejected | no write | `onlyAdminCanConfigureDirectedPathsAndDuplicateOrSelfLinksAreRejected` | NOT_RUN |
| UP-03 | P0 | Less than 3 unused days | Member | request quote | conflict | no quote/payment | `exactThreeUnusedDaysAreEligibleAndZeroPayableQuoteIsRejected` plus eligibility method | NOT_RUN |
| UP-04 | P0 | Unknown paid basis | Member | quote from unverifiable legacy purchase | blocked | source Membership preserved | `unknownSourcePaidValueAndUnconfiguredTargetBlockQuote` | NOT_RUN |
| UP-05 | P0 | Edit Plan/Path after quote | Admin + Member | settle saved quote | frozen quote applies or safe reconciliation | no recalculation drift | `capturedTargetTermsSurvivePlanEditsAndChainedBasisUsesAppliedCreditPlusPayment` | NOT_RUN |
| UP-06 | P0 | Concurrent Upgrade requests | same Member | request two | serialized; no duplicate quote | one operation survives | `concurrentUpgradeRequestsSerializeOnTheMemberRow` | NOT_RUN |
| UP-07 | P0 | Existing Renewal Intent with no Payment | Member | initiate Upgrade | blocked per conflict rule | no Upgrade Payment | `upgradeQuoteIsBlockedByOpenRenewalIntentWithoutAnyPaymentAttempt` | NOT_RUN |
| CAN-01 | P0 | Exactly 72h before upcoming Membership starts | Member | request ordinary cancellation | equality closed by `now < deadline` | no request at equality | `MembershipCancellationIntegrationTest` boundary cases | NOT_RUN |
| CAN-02 | P1 | Admin exceptional cancellation with split lineage | Admin | cancel Membership with several original Payments | separate refund obligation each | no aggregate record; audit per Payment | cancellation/payment tests | NOT_RUN |
| CAN-03 | P0 | Duplicate refund confirmation | staff | confirm same Payment twice | second rejected/idempotent | one refunded state | `PaymentIntegrationTest.duplicateRefundConfirmationAndDelayedCallbacksDoNotReviveCancelledRegistration` | NOT_RUN |
| REG-01 | P0 | Before registration opening | Member | POST class registration | conflict (not yet open) | no registration/payment | `CourseRegistrationIntegrationTest` lifecycle tests | PASS-2026-11-28 class |
| REG-02 | P1 | Exactly registrationDeadline−14 days | Member | register with other gates true | opening predicate passes | PENDING + frozen prices/hold | source `CourseRegistrationService.register`; class suite | PASS-2026-11-28 class |
| REG-03 | P0 | Member has active PT overlap | Member | register Class | 409 conflict | no CourseRegistration | `preventsRegistrationWhenScheduledClassOverlapsMembersActivePtBooking` | PASS-2026-11-28 class |
| REG-04 | P0 | Member confirmed in class overlap | Member | create PT | 409 conflict | no PT booking/Payment | `existingRegisteredClassSessionBlocksMemberPtBookingButCancelledRegistrationsDoNot` | PASS-2026-11-28 class |
| REG-05 | P1 | Prior registration cancelled | Member | create overlapping PT | allowed if all other eligibility holds | booking pending and independent | same named PT test | PASS-2026-11-28 class |
| REG-06 | P0 | Last seat, two Members concurrent | Member A/B | register simultaneously | one succeeds, other capacity conflict | capacity not exceeded | Course registration capacity/concurrency tests | PASS-2026-11-28 class (suite; inspect method inventory) |
| REG-07 | P1 | Client changes registration price | Member | edit final price payload | ignored/rejected; server snapshots calculated terms | trusted amount only | registration DTO/service; payment test `courseRegistrationPaymentChargesSnapshottedFinalPriceOnce` | NOT_RUN |
| REG-08 | P1 | Payment at/after deadline | Member | settle hold at equality/after | cannot reclaim seat; late collection reconciled | no confirmed registration revival | `PaymentIntegrationTest.paymentAtTheRegistrationDeadlineCannotReclaimExpiredSeat`, `paymentJustAfterTheRegistrationDeadlineCannotReviveTheHold` | NOT_RUN |
| SCHED-01 | P0 | Coach has overlapping session/PT | Admin | schedule class | conflict | no schedule mutation | `ClassSessionServiceTest`, Coach schedule conflict tests | NOT_RUN |
| SCHED-02 | P0 | Active confirmed/valid pending enrollment | Admin | create/reschedule session | schedule mutation rejected | session unchanged | `ClassSessionService.rejectScheduleMutationForActiveRegistrations` | Source-confirmed; test execution not run |
| SCHED-03 | P1 | Exact adjacency | Coach/Admin | schedule end equals next start | allowed for interval overlap rule | both persist if facility/other checks pass | ClassSession schedule tests | NOT_RUN |
| SCHED-04 | P1 | Elapsed scheduled row on startup | scheduler | run catch-up | SCHEDULED→COMPLETED once | audit; no attendance/results/payment | `ClassSessionCompletionServiceTest`, `TrainingIntegrationTest` | NOT_RUN |
| PT-01 | P0 | Coach booking exists at requested interval | Member | select/book Coach | excluded or 409 | no overlapping booking | `PersonalTrainingBookingIntegrationTest` schedule tests | PASS-2026-11-28 class |
| PT-02 | P1 | Member confirmed booking, <24h | Member | cancel | reject | unchanged | `membersCannotCancelAConfirmedSessionWithin24HoursOnlyAdminCanAndReceptionistsNever` | NOT_RUN |
| PT-03 | P1 | PT ends before outcome boundary | Coach | record outcome early/unassigned | reject | no outcome/audit transition | `coachCannotRecordBeforeEndAndOnlyAssignedActiveCoachMayRecord` | NOT_RUN |
| PT-04 | P0 | Coach records outcome then repeat | assigned Coach | repeat COMPLETED/NO_SHOW | conflict/idempotent rules | one outcome audit; Payment unchanged | `concurrentIdenticalCoachOutcomeRequestsProduceOneAuditEvent` | NOT_RUN |
| ATT-01 | P0 | Eligible roster has missing selection | Coach/Admin | initial submit partial set | 400/409, entire batch rejected | no attendance/history/audit partial writes | `AttendanceIntegrationTest`, attendance methods in `TrainingIntegrationTest` | NOT_RUN this task |
| ATT-02 | P0 | Duplicate/unexpected learner ID | Coach/Admin | submit malformed roster | reject entire operation | no partial rows | Attendance integration suite | NOT_RUN |
| ATT-03 | P0 | New EXCUSED selection | Coach/Admin | POST/PUT EXCUSED | reject | no row/history | `AttendanceService` validation tests | NOT_RUN |
| ATT-04 | P1 | Historical EXCUSED in roster | Coach | complete remaining roster | retain EXCUSED; count recorded | unchanged existing row | Attendance/Training tests | NOT_RUN |
| ATT-05 | P0 | PRESENT + TrainingResult | Coach/Admin | correct to ABSENT or EXCUSED | domain conflict | attendance/history/audit unchanged | `TrainingIntegrationTest.sharedSessionLockPreventsConcurrentAbsentCorrectionAndResultCreationFromCommittingInconsistentState` | NOT_RUN |
| ATT-06 | P0 | ABSENT learner | Coach | create TrainingResult | reject | no result | `TrainingIntegrationTest.resultsRequirePresentAttendanceAndUpdateInPlace` | NOT_RUN |
| ATT-07 | P0 | Concurrent result and absence correction | Coach/Admin | race both transactions | serialized; never inconsistent | one valid outcome only | shared-lock test above | NOT_RUN |
| ATT-08 | P1 | no recorded outcomes | Member | view attendance rate | unavailable state, no false 0/100% | read only | Attendance integration tests | NOT_RUN |
| ATT-09 | P1 | successful ABSENT submission then retry identical | Coach | resubmit unchanged status | no duplicate notification | one attendance outcome/notice | Attendance notification tests | NOT_RUN |
| SUP-01 | P0 | Unassigned OPEN ticket | Receptionist A | claim-and-start | assigned + IN_PROGRESS atomically | one assignment/audit transition | `claimStartsOpenTicketAtomicallyAndCompetingReceptionistCannotTakeItOver` | NOT_RUN this task |
| SUP-02 | P0 | Two Receptionists compete | A/B | claim same ticket concurrently | one winner; other cannot overwrite | single assignee | same Support test | NOT_RUN this task |
| SUP-03 | P0 | Unassigned legacy IN_PROGRESS | Receptionist | claim ticket | claim allowed, state remains IN_PROGRESS | assigned once | `legacyUnassignedInProgressTicketCanBeClaimedWithoutChangingItsStatus` | NOT_RUN |
| SUP-04 | P0 | Assigned to Receptionist A | Receptionist B | reply | 403/conflict | no message | Support authorization tests | NOT_RUN |
| SUP-05 | P1 | Assigned Receptionist + Admin | Admin | reply/participate | allowed without ownership transfer | assignee remains Receptionist | `adminMayParticipateOnlyAfterClaimWithoutReplacingReceptionistOwnership` | NOT_RUN |
| SUP-06 | P1 | RESOLVED ticket | Member | reply under allowed lifecycle | reopens according to rules | state/message/audit consistent | `memberReplyReopensResolvedTicketAndCoachAndAnonymousAreDenied` | NOT_RUN |
| SUP-07 | P1 | CLOSED ticket | any user | read/write | read-only; write denied | unchanged | Support lifecycle tests | NOT_RUN |
| PLAN-01 | P0 | General plan has duplicate week number | Coach | whole-plan save | 400/409 | no partial edits | `TrainingIntegrationTest.weeklyGeneralPlanSavePreservesLegacyExerciseIdentityAndEnforcesPlanOwnership` and validation tests | NOT_RUN |
| PLAN-02 | P0 | Week ID belongs to other plan | Coach | attach/move exercise | reject | both plans unchanged | same Training test | NOT_RUN |
| PLAN-03 | P0 | expectedUpdatedAt stale | Coach A/B | save older whole draft | 409 conflict; keep client draft | latest server plan unchanged | weekly plan stale draft tests | NOT_RUN |
| PLAN-04 | P1 | Copy week, edit copy | Coach | save both | exercises independent; IDs not reused | distinct persisted rows | `TrainingIntegrationTest` weekly save test | NOT_RUN |
| PLAN-05 | P1 | Legacy exercise week_id NULL | Member/Coach | read then explicit move | visible before; same identity after move | no implicit Week 1 assignment | weekly save test | NOT_RUN |
| PLAN-06 | P0 | Admin attempts General plan write | Admin | PUT weekly plan | 403 | no plan/audit change | Training admin read-only test | NOT_RUN |
| PLAN-07 | P1 | Offering shortened | Admin | change end date | content retained; out-of-range warning | no weekly/exercise delete | date derivation tests / source | NOT_RUN |
| PLAN-08 | P0 | Individual week number 53 | Coach | save Individual plan | reject (existing 1–52) | no revision | `IndividualTrainingPlanIntegrationTest` validation | NOT_RUN |
| PLAN-09 | P0 | Plan save fails midway (constraint/audit) | Coach | invalid full draft | rollback all | no partial week/exercise/audit mutation | whole-plan transactional tests | NOT_RUN |
| PLAN-10 | P0 | Concurrent conflicting General plan changes | Coach duplicate tabs | save same expectedUpdatedAt concurrently | one accepted, other stale conflict | no lost update | lock/expectedUpdatedAt tests | NOT_RUN |
| REF-01 | P0 | Required audit write fails | authorized actor | business mutation with audit fault | error and rollback | business row unchanged | tests exist for some Payment/Support flows | NOT_RUN |
| REF-02 | P0 | Notification recipient altered in client | Member | submit event/request | recipient derived from server-side persisted identity | no arbitrary recipient | Notification event tests | NOT_RUN |
| REF-03 | P1 | Invalid image bytes, MIME spoof, oversize | Admin/staff | upload | reject safe 4xx | no unsafe file/path | Local image storage tests / code | NOT_RUN |

## Existing test inventory and run evidence

| Test class | Domain | Evidence in source | Run status for this checkpoint |
|---|---|---|---|
| `ConsultationRequestIntegrationTest` | Consultation identity, DOB, phone, auth/ownership, claim, validation | 44 tests including API-level phone cases | **PASS 44/44**, 28/11/2026, H2 |
| `PersonalTrainingBookingIntegrationTest` | PT eligibility, class/PT overlap, cancellation, outcomes, auth, fallback | 29 tests | **PASS 29/29**, 28/11/2026, H2 |
| `CourseRegistrationIntegrationTest` | registration opening/eligibility/capacity/age/conflict | 38 tests | **PASS 38/38**, 28/11/2026, H2 |
| `PaymentIntegrationTest` | Payment holds, duplicate settlement/refund, ownership, queues | named tests cited in matrix | Not rerun this checkpoint |
| `MembershipUpgradeIntegrationTest` | Upgrade path, quote, settlement, concurrency | named tests cited in handbook/matrix | Not rerun |
| Renewal service/persistence/HTTP suites | intent, attempts, evidence, settlement | includes `Phase5BFinalRenewalHttpIT` | Previously accepted evidence; not rerun |
| `AttendanceIntegrationTest`, `TrainingIntegrationTest` | attendance completeness, results, class plan | methods cited above | Not rerun |
| `SupportTicketIntegrationTest` | claims, ownership, statuses | methods cited above | Not rerun |
| `IndividualTrainingPlanIntegrationTest` | individual revisions and access | suite present | Not rerun |
| `ClassSessionServiceTest`, `ClassSessionCompletionServiceTest` | scheduling lifecycle and completion | suites present | Not rerun |
| Frontend `tests/**/*.test.mjs` | validators, UI source contracts, components | see `frontend/package.json` | Existing accepted baseline; not rerun |
| Playwright `e2e/**/*.spec.js` | authenticated browser flows | requires running app/session | No browser suite run this checkpoint |

### How to execute safely

Run focused H2 classes via Surefire, e.g. `mvn -Dtest=ConsultationRequestIntegrationTest,PersonalTrainingBookingIntegrationTest,CourseRegistrationIntegrationTest test`. Do not select SQL Server `*IT` suites accidentally. SQL Server mutation suites require explicit clone identity guards and isolated test accounts. Never point integration fixtures at DB5. Provider callbacks should use signed synthetic evidence, never a real transaction.

## Gaps to close before claiming exhaustive adversarial acceptance

1. The three 2026-11-28 class runs give focused coverage but do not execute payment, renewal, upgrade, attendance, support, or plan suites in this checkpoint.
2. SQL Server concurrency tests are required to substantiate row-lock guarantees beyond H2.
3. The matrix marks tests as NOT_RUN when this checkpoint did not execute them; repository presence is not a pass.
4. No direct DB5 write was used to induce the PT conflict response. H2 integration tests prove both service directions for their test fixtures; live DB5 request rejected invalid phone only.
5. No destructive testing, vulnerability scan, or load test was performed.
