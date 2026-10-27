package group5.example.sportCenter.entity;

/**
 * MEMBERSHIP: referenceId = MembershipPlan.id.
 * COURSE_REGISTRATION: referenceId = CourseRegistration.id.
 * PT_BOOKING: referenceId = PersonalTrainingBooking.id.
 * UPGRADE: referenceId = MembershipPlan.id của gói muốn nâng lên; chi tiết báo giá nằm ở MembershipUpgrade.
 */
public enum PaymentPurpose { MEMBERSHIP, COURSE_REGISTRATION, PT_BOOKING, UPGRADE }
