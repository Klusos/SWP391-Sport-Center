package group5.example.sportCenter.entity;

/**
 * MEMBERSHIP: referenceId = MembershipPlan.id.
 * COURSE_REGISTRATION: referenceId = CourseRegistration.id.
 * PT_BOOKING: referenceId = PersonalTrainingBooking.id.
 */
public enum PaymentPurpose { MEMBERSHIP, COURSE_REGISTRATION, PT_BOOKING, UPGRADE }
