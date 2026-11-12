-- SportCenter schema-only submission script.
-- Generated from SQL Server metadata: LAPTOP-416VBDLK / SportCenterDemo.
-- Source contains 37 dbo user tables, including auth_revoked_token. No row data or credentials are included.
-- Target a new empty SQL Server 2016+ database. Execute in SSMS/sqlcmd with DDL permissions.
-- Schemas/tables are created first, then keys/checks, indexes, and foreign keys.
SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

-- SCHEMAS AND TABLES
CREATE TABLE [dbo].[attendance] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [class_session_id] int NOT NULL,
    [member_id] int NOT NULL,
    [status] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [marked_by] int NOT NULL,
    [marked_at] datetime2(7) NOT NULL,
    [note] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL
);
GO

CREATE TABLE [dbo].[attendance_history] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [attendance_id] int NOT NULL,
    [old_status] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [new_status] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [changed_by] int NOT NULL,
    [changed_at] datetime2(7) NOT NULL,
    [reason] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL
);
GO

CREATE TABLE [dbo].[audit_log] (
    [id] bigint IDENTITY(1, 1) NOT NULL,
    [actor_user_id] int NULL,
    [actor_role] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [action] varchar(50) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [entity_type] varchar(60) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [entity_id] varchar(64) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [description] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [created_at] datetime2(6) NOT NULL
);
GO

CREATE TABLE [dbo].[auth_revoked_token] (
    [token_fingerprint] binary(32) NOT NULL,
    [expires_at] datetime2(7) NOT NULL,
    [revoked_at] datetime2(7) NOT NULL
);
GO

CREATE TABLE [dbo].[class] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [capacity] int NOT NULL,
    [name] nvarchar(100) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [status] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [coach_id] int NOT NULL,
    [course_offering_id] int NOT NULL
);
GO

CREATE TABLE [dbo].[class_session] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [class_id] int NOT NULL,
    [facility_id] int NULL,
    [start_time] datetime NOT NULL,
    [end_time] datetime NOT NULL,
    [status] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL
);
GO

CREATE TABLE [dbo].[coach] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [status] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [user_id] int NOT NULL,
    [image_url] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [bio] nvarchar(1000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL
);
GO

CREATE TABLE [dbo].[coach_sport] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [certification] nvarchar(255) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [experience_years] int NOT NULL,
    [coach_id] int NOT NULL,
    [sport_id] int NOT NULL
);
GO

CREATE TABLE [dbo].[consultation_request] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [member_id] int NULL,
    [name] nvarchar(100) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [phone] nvarchar(20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [email] nvarchar(150) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [sport_id] int NOT NULL,
    [age] int NULL,
    [experience] nvarchar(MAX) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [note] nvarchar(MAX) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [status] varchar(20) COLLATE SQL_Latin1_General_CP1_CI_AS CONSTRAINT [df_consultation_request_status] DEFAULT ('PENDING') NOT NULL,
    [created_at] datetime2(6) CONSTRAINT [df_consultation_request_created] DEFAULT (sysdatetime()) NOT NULL,
    [handled_by] int NULL,
    [handled_at] datetime2(6) NULL,
    [resolution_note] nvarchar(1000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [completed_at] datetime2(6) NULL,
    [cancelled_at] datetime2(6) NULL
);
GO

CREATE TABLE [dbo].[course] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [description] nvarchar(MAX) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [learning_outcome] nvarchar(MAX) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [level] varchar(50) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [name] nvarchar(255) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [status] varchar(50) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [target_age_max] int NULL,
    [target_age_min] int NULL,
    [sport_id] int NOT NULL,
    [age_policy] varchar(20) COLLATE SQL_Latin1_General_CP1_CI_AS CONSTRAINT [df_course_age_policy] DEFAULT ('RECOMMENDED') NOT NULL
);
GO

CREATE TABLE [dbo].[course_offering] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [cancellation_deadline] datetime2(7) NOT NULL,
    [end_date] date NOT NULL,
    [name] nvarchar(150) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [planned_sessions] int NULL,
    [price] numeric(12,2) NOT NULL,
    [registration_deadline] datetime2(7) NOT NULL,
    [session_duration] int NOT NULL,
    [start_date] date NOT NULL,
    [status] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [course_id] int NOT NULL
);
GO

CREATE TABLE [dbo].[course_registration] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [member_id] int NOT NULL,
    [class_id] int NOT NULL,
    [status] varchar(20) COLLATE SQL_Latin1_General_CP1_CI_AS CONSTRAINT [df_course_registration_status] DEFAULT ('CONFIRMED') NOT NULL,
    [registered_at] datetime2(7) CONSTRAINT [df_course_registration_registered_at] DEFAULT (sysdatetime()) NOT NULL,
    [cancelled_at] datetime2(7) NULL,
    [original_price] decimal(12,2) NOT NULL,
    [membership_discount] decimal(12,2) NOT NULL,
    [final_price] decimal(12,2) NOT NULL,
    [payment_deadline] datetime2(6) NULL
);
GO

CREATE TABLE [dbo].[facility] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [name] nvarchar(100) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [status] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [type] nvarchar(50) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [sport_id] int NOT NULL,
    [image_url] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL
);
GO

CREATE TABLE [dbo].[facility_sport] (
    [facility_id] int NOT NULL,
    [sport_id] int NOT NULL
);
GO

CREATE TABLE [dbo].[individual_training_plan] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [class_id] int NOT NULL,
    [member_id] int NOT NULL,
    [created_by] int NOT NULL,
    [created_at] datetime2(7) NOT NULL,
    [updated_at] datetime2(7) NOT NULL
);
GO

CREATE TABLE [dbo].[individual_training_plan_exercise] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [week_id] int NOT NULL,
    [name] nvarchar(150) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [sets] int NULL,
    [reps] int NULL,
    [duration_minutes] int NULL,
    [notes] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [sort_order] int NOT NULL
);
GO

CREATE TABLE [dbo].[individual_training_plan_revision] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [plan_id] int NOT NULL,
    [revision_number] int NOT NULL,
    [goal] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [description] nvarchar(2000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [revised_by] int NOT NULL,
    [created_at] datetime2(7) NOT NULL
);
GO

CREATE TABLE [dbo].[individual_training_plan_week] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [revision_id] int NOT NULL,
    [week_number] int NOT NULL,
    [title] nvarchar(150) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [description] nvarchar(1000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL
);
GO

CREATE TABLE [dbo].[member_profile] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [goal] nvarchar(MAX) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [height] numeric(5,2) NULL,
    [updated_at] datetime2(7) NOT NULL,
    [weight] numeric(5,2) NULL,
    [member_id] int NOT NULL
);
GO

CREATE TABLE [dbo].[member_sport_level] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [member_id] int NOT NULL,
    [sport_id] int NOT NULL,
    [level] varchar(20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [verified_by_coach_id] int NULL,
    [verified_at] datetime2(6) NULL,
    [updated_at] datetime2(6) NOT NULL
);
GO

CREATE TABLE [dbo].[membership] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [auto_renew] bit NOT NULL,
    [end_date] date NOT NULL,
    [start_date] date NOT NULL,
    [status] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [member_id] int NOT NULL,
    [plan_id] int NOT NULL,
    [purchase_payment_id] int NULL,
    [cancelled_at] datetime2(6) NULL,
    [cancel_reason] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL
);
GO

CREATE TABLE [dbo].[membership_cancellation_request] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [membership_id] int NOT NULL,
    [member_id] int NOT NULL,
    [reason] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [status] varchar(20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [requested_at] datetime2(6) NOT NULL,
    [decided_by] int NULL,
    [decided_at] datetime2(6) NULL,
    [decision_note] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL
);
GO

CREATE TABLE [dbo].[membership_plan] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [allow_one_on_one_coaching] bit NOT NULL,
    [allow_personal_training_plan] bit NOT NULL,
    [course_discount_percent] numeric(5,2) NOT NULL,
    [description] nvarchar(MAX) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [duration_months] int NOT NULL,
    [name] nvarchar(100) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [personal_training_discount_percent] numeric(5,2) NOT NULL,
    [price] numeric(12,2) NOT NULL,
    [status] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL
);
GO

CREATE TABLE [dbo].[membership_upgrade] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [payment_id] int NOT NULL,
    [source_membership_id] int NOT NULL,
    [target_plan_id] int NOT NULL,
    [remaining_days] int NOT NULL,
    [expires_at] datetime2(6) NOT NULL,
    [created_at] datetime2(6) NOT NULL,
    [result_membership_id] int NULL
);
GO

CREATE TABLE [dbo].[notification] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [user_id] int NOT NULL,
    [title] nvarchar(150) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [message] nvarchar(MAX) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [is_read] bit CONSTRAINT [df_notification_is_read] DEFAULT ((0)) NOT NULL,
    [created_at] datetime2(6) NOT NULL
);
GO

CREATE TABLE [dbo].[payment] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [member_id] int NOT NULL,
    [amount] decimal(12,2) NOT NULL,
    [method] varchar(20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [purpose] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [reference_id] int NOT NULL,
    [auto_renew] bit NULL,
    [status] varchar(20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [transaction_code] varchar(40) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [processed_by] int NULL,
    [note] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [created_at] datetime2(6) NOT NULL,
    [paid_at] datetime2(6) NULL,
    [refunded_at] datetime2(6) NULL,
    [refunded_by] int NULL
);
GO

CREATE TABLE [dbo].[personal_training_booking] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [member_id] int NOT NULL,
    [coach_id] int NOT NULL,
    [sport_id] int NOT NULL,
    [start_time] datetime NOT NULL,
    [end_time] datetime NOT NULL,
    [original_price] decimal(12,2) NOT NULL,
    [discount] decimal(12,2) NOT NULL,
    [final_price] decimal(12,2) NOT NULL,
    [status] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [created_at] datetime CONSTRAINT [df_personal_training_booking_created_at] DEFAULT (getdate()) NOT NULL,
    [payment_expires_at] datetime NULL
);
GO

CREATE TABLE [dbo].[progress_evaluation] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [class_id] int NOT NULL,
    [member_id] int NOT NULL,
    [rating] int NOT NULL,
    [comment] nvarchar(1000) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [evaluated_by] int NOT NULL,
    [evaluated_at] datetime2(7) NOT NULL
);
GO

CREATE TABLE [dbo].[skill_assessment] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [member_id] int NOT NULL,
    [sport_id] int NOT NULL,
    [assigned_coach_id] int NOT NULL,
    [requested_by_user_id] int NOT NULL,
    [status] varchar(20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [assessed_level] varchar(20) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [notes] nvarchar(1000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [created_at] datetime2(6) NOT NULL,
    [assessed_at] datetime2(6) NULL,
    [cancelled_at] datetime2(6) NULL,
    [consultation_request_id] int NULL
);
GO

CREATE TABLE [dbo].[sport] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [description] nvarchar(MAX) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [name] nvarchar(100) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [status] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [image_url] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL
);
GO

CREATE TABLE [dbo].[support_ticket] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [member_id] int NOT NULL,
    [subject] nvarchar(160) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [category] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [description] nvarchar(MAX) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [status] varchar(20) COLLATE SQL_Latin1_General_CP1_CI_AS CONSTRAINT [df_support_ticket_status] DEFAULT ('OPEN') NOT NULL,
    [assigned_staff_id] int NULL,
    [created_at] datetime2(6) CONSTRAINT [df_support_ticket_created_at] DEFAULT (sysdatetime()) NOT NULL,
    [updated_at] datetime2(6) CONSTRAINT [df_support_ticket_updated_at] DEFAULT (sysdatetime()) NOT NULL,
    [resolved_at] datetime2(6) NULL,
    [closed_at] datetime2(6) NULL
);
GO

CREATE TABLE [dbo].[support_ticket_message] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [ticket_id] int NOT NULL,
    [sender_id] int NOT NULL,
    [message] nvarchar(MAX) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [created_at] datetime2(6) CONSTRAINT [df_support_ticket_message_created_at] DEFAULT (sysdatetime()) NOT NULL
);
GO

CREATE TABLE [dbo].[sysdiagrams] (
    [name] sysname COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [principal_id] int NOT NULL,
    [diagram_id] int IDENTITY(1, 1) NOT NULL,
    [version] int NULL,
    [definition] varbinary(MAX) NULL
);
GO

CREATE TABLE [dbo].[training_exercise] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [plan_id] int NOT NULL,
    [name] nvarchar(150) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [sets] int NULL,
    [reps] int NULL,
    [duration_minutes] int NULL,
    [notes] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [sort_order] int NOT NULL
);
GO

CREATE TABLE [dbo].[training_plan] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [class_id] int NOT NULL,
    [goal] nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [description] nvarchar(2000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [updated_by] int NOT NULL,
    [created_at] datetime2(7) NOT NULL,
    [updated_at] datetime2(7) NOT NULL
);
GO

CREATE TABLE [dbo].[training_result] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [class_session_id] int NOT NULL,
    [member_id] int NOT NULL,
    [score] int NOT NULL,
    [notes] nvarchar(1000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [recorded_by] int NOT NULL,
    [recorded_at] datetime2(7) NOT NULL
);
GO

CREATE TABLE [dbo].[users] (
    [id] int IDENTITY(1, 1) NOT NULL,
    [created_at] datetime2(7) NOT NULL,
    [date_of_birth] date NULL,
    [email] varchar(150) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [gender] varchar(1) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [name] nvarchar(100) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [password_hash] varchar(255) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [phone] varchar(20) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [role] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [status] varchar(30) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL
);
GO

-- PRIMARY AND UNIQUE CONSTRAINTS
ALTER TABLE [dbo].[attendance] ADD CONSTRAINT [pk_attendance] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[attendance] ADD CONSTRAINT [uq_attendance_session_member] UNIQUE NONCLUSTERED ([class_session_id] ASC, [member_id] ASC);
GO
ALTER TABLE [dbo].[attendance_history] ADD CONSTRAINT [pk_attendance_history] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[audit_log] ADD CONSTRAINT [pk_audit_log] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[auth_revoked_token] ADD CONSTRAINT [pk_auth_revoked_token] PRIMARY KEY CLUSTERED ([token_fingerprint] ASC);
GO
ALTER TABLE [dbo].[class] ADD CONSTRAINT [pk_class] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[class_session] ADD CONSTRAINT [pk_class_session] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[coach] ADD CONSTRAINT [pk_coach] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[coach] ADD CONSTRAINT [uq_coach_user] UNIQUE NONCLUSTERED ([user_id] ASC);
GO
ALTER TABLE [dbo].[coach_sport] ADD CONSTRAINT [pk_coach_sport] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[coach_sport] ADD CONSTRAINT [uq_coach_sport_pair] UNIQUE NONCLUSTERED ([coach_id] ASC, [sport_id] ASC);
GO
ALTER TABLE [dbo].[consultation_request] ADD CONSTRAINT [pk_consultation_request] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[course] ADD CONSTRAINT [pk_course] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[course_offering] ADD CONSTRAINT [pk_course_offering] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[course_registration] ADD CONSTRAINT [pk_course_registration] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[facility] ADD CONSTRAINT [pk_facility] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[facility_sport] ADD CONSTRAINT [pk_facility_sport] PRIMARY KEY CLUSTERED ([facility_id] ASC, [sport_id] ASC);
GO
ALTER TABLE [dbo].[individual_training_plan] ADD CONSTRAINT [pk_individual_training_plan] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[individual_training_plan] ADD CONSTRAINT [uq_individual_training_plan_class_member] UNIQUE NONCLUSTERED ([class_id] ASC, [member_id] ASC);
GO
ALTER TABLE [dbo].[individual_training_plan_exercise] ADD CONSTRAINT [pk_individual_training_plan_exercise] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[individual_training_plan_exercise] ADD CONSTRAINT [uq_individual_training_exercise_week_order] UNIQUE NONCLUSTERED ([week_id] ASC, [sort_order] ASC);
GO
ALTER TABLE [dbo].[individual_training_plan_revision] ADD CONSTRAINT [pk_individual_training_plan_revision] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[individual_training_plan_revision] ADD CONSTRAINT [uq_individual_training_revision_number] UNIQUE NONCLUSTERED ([plan_id] ASC, [revision_number] ASC);
GO
ALTER TABLE [dbo].[individual_training_plan_week] ADD CONSTRAINT [pk_individual_training_plan_week] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[individual_training_plan_week] ADD CONSTRAINT [uq_individual_training_week_number] UNIQUE NONCLUSTERED ([revision_id] ASC, [week_number] ASC);
GO
ALTER TABLE [dbo].[member_profile] ADD CONSTRAINT [pk_member_profile] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[member_profile] ADD CONSTRAINT [uq_member_profile_member] UNIQUE NONCLUSTERED ([member_id] ASC);
GO
ALTER TABLE [dbo].[member_sport_level] ADD CONSTRAINT [PK__member_s__3213E83FC3EB1772] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[membership] ADD CONSTRAINT [pk_membership] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[membership_cancellation_request] ADD CONSTRAINT [pk_membership_cancellation_request] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[membership_plan] ADD CONSTRAINT [pk_membership_plan] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[membership_upgrade] ADD CONSTRAINT [pk_membership_upgrade] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[membership_upgrade] ADD CONSTRAINT [uq_membership_upgrade_payment] UNIQUE NONCLUSTERED ([payment_id] ASC);
GO
ALTER TABLE [dbo].[notification] ADD CONSTRAINT [pk_notification] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[payment] ADD CONSTRAINT [pk_payment] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[payment] ADD CONSTRAINT [uq_payment_txn] UNIQUE NONCLUSTERED ([transaction_code] ASC);
GO
ALTER TABLE [dbo].[personal_training_booking] ADD CONSTRAINT [pk_personal_training_booking] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[progress_evaluation] ADD CONSTRAINT [pk_progress_evaluation] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[skill_assessment] ADD CONSTRAINT [PK__skill_as__3213E83FA03A4818] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[sport] ADD CONSTRAINT [pk_sport] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[sport] ADD CONSTRAINT [uq_sport_name] UNIQUE NONCLUSTERED ([name] ASC);
GO
ALTER TABLE [dbo].[support_ticket] ADD CONSTRAINT [pk_support_ticket] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[support_ticket_message] ADD CONSTRAINT [pk_support_ticket_message] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[sysdiagrams] ADD CONSTRAINT [PK__sysdiagr__C2B05B613953AEB2] PRIMARY KEY CLUSTERED ([diagram_id] ASC);
GO
ALTER TABLE [dbo].[sysdiagrams] ADD CONSTRAINT [UK_principal_name] UNIQUE NONCLUSTERED ([principal_id] ASC, [name] ASC);
GO
ALTER TABLE [dbo].[training_exercise] ADD CONSTRAINT [pk_training_exercise] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[training_plan] ADD CONSTRAINT [pk_training_plan] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[training_plan] ADD CONSTRAINT [uq_training_plan_class] UNIQUE NONCLUSTERED ([class_id] ASC);
GO
ALTER TABLE [dbo].[training_result] ADD CONSTRAINT [pk_training_result] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[training_result] ADD CONSTRAINT [uq_training_result_session_member] UNIQUE NONCLUSTERED ([class_session_id] ASC, [member_id] ASC);
GO
ALTER TABLE [dbo].[users] ADD CONSTRAINT [pk_users] PRIMARY KEY CLUSTERED ([id] ASC);
GO
ALTER TABLE [dbo].[users] ADD CONSTRAINT [uq_users_email] UNIQUE NONCLUSTERED ([email] ASC);
GO

-- CHECK CONSTRAINTS
ALTER TABLE [dbo].[attendance] WITH CHECK ADD CONSTRAINT [ck_attendance_status] CHECK ([status]='EXCUSED' OR [status]='ABSENT' OR [status]='PRESENT');
GO
ALTER TABLE [dbo].[attendance_history] WITH CHECK ADD CONSTRAINT [ck_attendance_history_new_status] CHECK ([new_status]='EXCUSED' OR [new_status]='ABSENT' OR [new_status]='PRESENT');
GO
ALTER TABLE [dbo].[attendance_history] WITH CHECK ADD CONSTRAINT [ck_attendance_history_old_status] CHECK ([old_status] IS NULL OR ([old_status]='EXCUSED' OR [old_status]='ABSENT' OR [old_status]='PRESENT'));
GO
ALTER TABLE [dbo].[consultation_request] WITH CHECK ADD CONSTRAINT [ck_consultation_request_age] CHECK ([age] IS NULL OR [age]>=(1) AND [age]<=(120));
GO
ALTER TABLE [dbo].[consultation_request] WITH CHECK ADD CONSTRAINT [ck_consultation_request_name] CHECK (len(ltrim(rtrim([name])))>(0));
GO
ALTER TABLE [dbo].[consultation_request] WITH CHECK ADD CONSTRAINT [ck_consultation_request_phone] CHECK (len(ltrim(rtrim([phone])))>(0));
GO
ALTER TABLE [dbo].[consultation_request] WITH CHECK ADD CONSTRAINT [ck_consultation_request_status] CHECK ([status]='CANCELLED' OR [status]='COMPLETED' OR [status]='IN_PROGRESS' OR [status]='PENDING');
GO
ALTER TABLE [dbo].[course] WITH CHECK ADD CONSTRAINT [ck_course_age_policy_required_bounds] CHECK ([age_policy]<>'REQUIRED' OR [target_age_min] IS NOT NULL AND [target_age_max] IS NOT NULL);
GO
ALTER TABLE [dbo].[course] WITH CHECK ADD CONSTRAINT [ck_course_age_policy_values] CHECK ([age_policy]='REQUIRED' OR [age_policy]='RECOMMENDED');
GO
ALTER TABLE [dbo].[course] WITH CHECK ADD CONSTRAINT [ck_course_age_range] CHECK (([target_age_min] IS NULL OR [target_age_min]>=(0)) AND ([target_age_max] IS NULL OR [target_age_max]>=(0)) AND ([target_age_min] IS NULL OR [target_age_max] IS NULL OR [target_age_min]<=[target_age_max]));
GO
ALTER TABLE [dbo].[course_registration] WITH CHECK ADD CONSTRAINT [ck_course_registration_status] CHECK (([status]='EXPIRED' OR [status]='CANCELLED' OR [status]='CONFIRMED' OR [status]='PENDING_PAYMENT') AND ([status]<>'PENDING_PAYMENT' OR [payment_deadline] IS NOT NULL));
GO
ALTER TABLE [dbo].[individual_training_plan_exercise] WITH CHECK ADD CONSTRAINT [ck_individual_training_exercise_values] CHECK (len([name])>(0) AND ([sets] IS NULL OR [sets]>=(1) AND [sets]<=(100)) AND ([reps] IS NULL OR [reps]>=(1) AND [reps]<=(1000)) AND ([duration_minutes] IS NULL OR [duration_minutes]>=(1) AND [duration_minutes]<=(240)) AND [sort_order]>=(0) AND [sort_order]<=(1000));
GO
ALTER TABLE [dbo].[individual_training_plan_revision] WITH CHECK ADD CONSTRAINT [ck_individual_training_revision_values] CHECK ([revision_number]>(0) AND len([goal])>(0));
GO
ALTER TABLE [dbo].[individual_training_plan_week] WITH CHECK ADD CONSTRAINT [ck_individual_training_week_number] CHECK ([week_number]>=(1) AND [week_number]<=(52));
GO
ALTER TABLE [dbo].[member_sport_level] WITH CHECK ADD CONSTRAINT [ck_msl_level] CHECK ([level]='ADVANCED' OR [level]='INTERMEDIATE' OR [level]='BEGINNER');
GO
ALTER TABLE [dbo].[membership_cancellation_request] WITH CHECK ADD CONSTRAINT [ck_membership_cancellation_request_status] CHECK ([status]='EXPIRED' OR [status]='WITHDRAWN' OR [status]='REJECTED' OR [status]='APPROVED' OR [status]='PENDING');
GO
ALTER TABLE [dbo].[membership_upgrade] WITH CHECK ADD CONSTRAINT [ck_membership_upgrade_remaining_days] CHECK ([remaining_days]>(0));
GO
ALTER TABLE [dbo].[payment] WITH CHECK ADD CONSTRAINT [ck_payment_amount] CHECK ([amount]>=(0));
GO
ALTER TABLE [dbo].[payment] WITH CHECK ADD CONSTRAINT [ck_payment_method] CHECK ([method]='COUNTER' OR [method]='ONLINE');
GO
ALTER TABLE [dbo].[payment] WITH CHECK ADD CONSTRAINT [ck_payment_purpose] CHECK ([purpose]='UPGRADE' OR [purpose]='PT_BOOKING' OR [purpose]='COURSE_REGISTRATION' OR [purpose]='MEMBERSHIP');
GO
ALTER TABLE [dbo].[payment] WITH CHECK ADD CONSTRAINT [ck_payment_status] CHECK ([status]='REFUNDED' OR [status]='REFUND_REQUIRED' OR [status]='CANCELLED' OR [status]='SUCCESS' OR [status]='PENDING');
GO
ALTER TABLE [dbo].[personal_training_booking] WITH CHECK ADD CONSTRAINT [ck_personal_training_booking_payment_expiry] CHECK ([status]<>'PENDING_PAYMENT' OR [payment_expires_at] IS NOT NULL);
GO
ALTER TABLE [dbo].[personal_training_booking] WITH CHECK ADD CONSTRAINT [ck_personal_training_booking_prices] CHECK ([original_price]>=(0) AND [discount]>=(0) AND [final_price]>=(0));
GO
ALTER TABLE [dbo].[personal_training_booking] WITH CHECK ADD CONSTRAINT [ck_personal_training_booking_status] CHECK ([status]='EXPIRED' OR [status]='CANCELLED' OR [status]='CONFIRMED' OR [status]='PENDING_PAYMENT');
GO
ALTER TABLE [dbo].[personal_training_booking] WITH CHECK ADD CONSTRAINT [ck_personal_training_booking_times] CHECK ([end_time]>[start_time]);
GO
ALTER TABLE [dbo].[progress_evaluation] WITH CHECK ADD CONSTRAINT [ck_progress_evaluation_values] CHECK ([rating]>=(1) AND [rating]<=(5) AND len([comment])>(0));
GO
ALTER TABLE [dbo].[skill_assessment] WITH CHECK ADD CONSTRAINT [ck_sa_level] CHECK ([assessed_level] IS NULL OR ([assessed_level]='ADVANCED' OR [assessed_level]='INTERMEDIATE' OR [assessed_level]='BEGINNER'));
GO
ALTER TABLE [dbo].[skill_assessment] WITH CHECK ADD CONSTRAINT [ck_sa_status] CHECK ([status]='CANCELLED' OR [status]='COMPLETED' OR [status]='PENDING');
GO
ALTER TABLE [dbo].[support_ticket] WITH CHECK ADD CONSTRAINT [ck_support_ticket_category] CHECK ([category]='OTHER' OR [category]='PAYMENT' OR [category]='PT_BOOKING' OR [category]='COURSE' OR [category]='MEMBERSHIP' OR [category]='GENERAL');
GO
ALTER TABLE [dbo].[support_ticket] WITH CHECK ADD CONSTRAINT [ck_support_ticket_description] CHECK (len(ltrim(rtrim([description])))>(0));
GO
ALTER TABLE [dbo].[support_ticket] WITH CHECK ADD CONSTRAINT [ck_support_ticket_status] CHECK ([status]='CLOSED' OR [status]='RESOLVED' OR [status]='IN_PROGRESS' OR [status]='OPEN');
GO
ALTER TABLE [dbo].[support_ticket] WITH CHECK ADD CONSTRAINT [ck_support_ticket_subject] CHECK (len(ltrim(rtrim([subject])))>(0));
GO
ALTER TABLE [dbo].[support_ticket_message] WITH CHECK ADD CONSTRAINT [ck_support_ticket_message_nonblank] CHECK (len(ltrim(rtrim([message])))>(0));
GO
ALTER TABLE [dbo].[training_exercise] WITH CHECK ADD CONSTRAINT [ck_training_exercise_values] CHECK (len([name])>(0) AND [sort_order]>=(0) AND ([sets] IS NULL OR [sets]>=(1) AND [sets]<=(100)) AND ([reps] IS NULL OR [reps]>=(1) AND [reps]<=(1000)) AND ([duration_minutes] IS NULL OR [duration_minutes]>=(1) AND [duration_minutes]<=(240)));
GO
ALTER TABLE [dbo].[training_plan] WITH CHECK ADD CONSTRAINT [ck_training_plan_goal] CHECK (len([goal])>(0));
GO
ALTER TABLE [dbo].[training_result] WITH CHECK ADD CONSTRAINT [ck_training_result_score] CHECK ([score]>=(1) AND [score]<=(10));
GO

-- NON-CONSTRAINT INDEXES
CREATE NONCLUSTERED INDEX [ix_audit_log_actor_created] ON [dbo].[audit_log] ([actor_user_id] ASC, [created_at] DESC, [id] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_audit_log_created] ON [dbo].[audit_log] ([created_at] DESC, [id] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_audit_log_entity] ON [dbo].[audit_log] ([entity_type] ASC, [entity_id] ASC, [created_at] DESC, [id] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_auth_revoked_token_expires_at] ON [dbo].[auth_revoked_token] ([expires_at] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_consultation_handler_status] ON [dbo].[consultation_request] ([handled_by] ASC, [status] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_consultation_member_created] ON [dbo].[consultation_request] ([member_id] ASC, [created_at] DESC, [id] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_consultation_status_created] ON [dbo].[consultation_request] ([status] ASC, [created_at] DESC, [id] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_course_registration_class_deadline] ON [dbo].[course_registration] ([class_id] ASC, [status] ASC, [payment_deadline] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_course_registration_class_status] ON [dbo].[course_registration] ([class_id] ASC, [status] ASC, [registered_at] DESC, [id] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_course_registration_expiry] ON [dbo].[course_registration] ([status] ASC, [payment_deadline] ASC, [id] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_course_registration_member_status] ON [dbo].[course_registration] ([member_id] ASC, [status] ASC, [registered_at] DESC, [id] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE UNIQUE NONCLUSTERED INDEX [ux_course_registration_confirmed] ON [dbo].[course_registration] ([member_id] ASC, [class_id] ASC) WHERE ([status]='CONFIRMED') WITH (PAD_INDEX = OFF, FILLFACTOR = 0, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE UNIQUE NONCLUSTERED INDEX [ux_course_registration_pending] ON [dbo].[course_registration] ([member_id] ASC, [class_id] ASC) WHERE ([status]='PENDING_PAYMENT') WITH (PAD_INDEX = OFF, FILLFACTOR = 0, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_facility_sport_sport] ON [dbo].[facility_sport] ([sport_id] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_individual_training_plan_class] ON [dbo].[individual_training_plan] ([class_id] ASC, [id] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_individual_training_plan_member_updated] ON [dbo].[individual_training_plan] ([member_id] ASC, [updated_at] DESC, [id] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_individual_training_exercise_week_order] ON [dbo].[individual_training_plan_exercise] ([week_id] ASC, [sort_order] ASC, [id] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_individual_training_revision_plan_number] ON [dbo].[individual_training_plan_revision] ([plan_id] ASC, [revision_number] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_individual_training_week_revision_order] ON [dbo].[individual_training_plan_week] ([revision_id] ASC, [week_number] ASC, [id] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE UNIQUE NONCLUSTERED INDEX [uk_member_sport_level] ON [dbo].[member_sport_level] ([member_id] ASC, [sport_id] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_membership_cancellation_request_member] ON [dbo].[membership_cancellation_request] ([member_id] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_membership_cancellation_request_status] ON [dbo].[membership_cancellation_request] ([status] ASC, [requested_at] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE UNIQUE NONCLUSTERED INDEX [ux_membership_cancellation_request_pending] ON [dbo].[membership_cancellation_request] ([membership_id] ASC) WHERE ([status]='PENDING') WITH (PAD_INDEX = OFF, FILLFACTOR = 0, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_membership_upgrade_source] ON [dbo].[membership_upgrade] ([source_membership_id] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_notification_unread_user] ON [dbo].[notification] ([user_id] ASC) WHERE ([is_read]=(0)) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_notification_user_created] ON [dbo].[notification] ([user_id] ASC, [created_at] DESC, [id] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_payment_member] ON [dbo].[payment] ([member_id] ASC, [created_at] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_payment_paid_at] ON [dbo].[payment] ([status] ASC, [paid_at] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_payment_reference] ON [dbo].[payment] ([purpose] ASC, [reference_id] ASC, [status] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE UNIQUE NONCLUSTERED INDEX [ux_payment_member_pending_upgrade] ON [dbo].[payment] ([member_id] ASC) WHERE ([purpose]='UPGRADE' AND [status]='PENDING') WITH (PAD_INDEX = OFF, FILLFACTOR = 0, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_pt_booking_coach_time] ON [dbo].[personal_training_booking] ([coach_id] ASC, [start_time] ASC, [end_time] ASC, [status] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_pt_booking_member_time] ON [dbo].[personal_training_booking] ([member_id] ASC, [start_time] ASC, [end_time] ASC, [status] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_progress_evaluation_class_member] ON [dbo].[progress_evaluation] ([class_id] ASC, [member_id] ASC, [evaluated_at] DESC, [id] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_progress_evaluation_member] ON [dbo].[progress_evaluation] ([member_id] ASC, [evaluated_at] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_assessment_consultation] ON [dbo].[skill_assessment] ([consultation_request_id] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE UNIQUE NONCLUSTERED INDEX [uk_skill_assessment_pending] ON [dbo].[skill_assessment] ([member_id] ASC, [sport_id] ASC) WHERE ([status]='PENDING') WITH (PAD_INDEX = OFF, FILLFACTOR = 0, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_support_ticket_assigned_staff] ON [dbo].[support_ticket] ([assigned_staff_id] ASC, [status] ASC, [updated_at] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_support_ticket_category_status] ON [dbo].[support_ticket] ([category] ASC, [status] ASC, [updated_at] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_support_ticket_member_updated] ON [dbo].[support_ticket] ([member_id] ASC, [updated_at] DESC, [id] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_support_ticket_status_updated] ON [dbo].[support_ticket] ([status] ASC, [updated_at] DESC, [id] DESC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_support_ticket_message_ticket_created] ON [dbo].[support_ticket_message] ([ticket_id] ASC, [created_at] ASC, [id] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_training_exercise_plan_order] ON [dbo].[training_exercise] ([plan_id] ASC, [sort_order] ASC, [id] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO
CREATE NONCLUSTERED INDEX [ix_training_result_member] ON [dbo].[training_result] ([member_id] ASC) WITH (PAD_INDEX = OFF, FILLFACTOR = 0, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY];
GO

-- FOREIGN KEYS (added after all referenced tables and keys)
ALTER TABLE [dbo].[attendance] WITH CHECK ADD CONSTRAINT [fk_attendance_marked_by] FOREIGN KEY ([marked_by]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[attendance] WITH CHECK ADD CONSTRAINT [fk_attendance_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[attendance] WITH CHECK ADD CONSTRAINT [fk_attendance_session] FOREIGN KEY ([class_session_id]) REFERENCES [dbo].[class_session] ([id]);
GO
ALTER TABLE [dbo].[attendance_history] WITH CHECK ADD CONSTRAINT [fk_attendance_history_attendance] FOREIGN KEY ([attendance_id]) REFERENCES [dbo].[attendance] ([id]);
GO
ALTER TABLE [dbo].[attendance_history] WITH CHECK ADD CONSTRAINT [fk_attendance_history_changed_by] FOREIGN KEY ([changed_by]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[audit_log] WITH CHECK ADD CONSTRAINT [fk_audit_log_actor] FOREIGN KEY ([actor_user_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[class] WITH CHECK ADD CONSTRAINT [fk_class_coach] FOREIGN KEY ([coach_id]) REFERENCES [dbo].[coach] ([id]);
GO
ALTER TABLE [dbo].[class] WITH CHECK ADD CONSTRAINT [fk_class_course_offering] FOREIGN KEY ([course_offering_id]) REFERENCES [dbo].[course_offering] ([id]);
GO
ALTER TABLE [dbo].[class_session] WITH CHECK ADD CONSTRAINT [fk_class_session_class] FOREIGN KEY ([class_id]) REFERENCES [dbo].[class] ([id]);
GO
ALTER TABLE [dbo].[class_session] WITH CHECK ADD CONSTRAINT [fk_class_session_facility] FOREIGN KEY ([facility_id]) REFERENCES [dbo].[facility] ([id]);
GO
ALTER TABLE [dbo].[coach] WITH CHECK ADD CONSTRAINT [fk_coach_user] FOREIGN KEY ([user_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[coach_sport] WITH CHECK ADD CONSTRAINT [fk_coach_sport_coach] FOREIGN KEY ([coach_id]) REFERENCES [dbo].[coach] ([id]);
GO
ALTER TABLE [dbo].[coach_sport] WITH CHECK ADD CONSTRAINT [fk_coach_sport_sport] FOREIGN KEY ([sport_id]) REFERENCES [dbo].[sport] ([id]);
GO
ALTER TABLE [dbo].[consultation_request] WITH CHECK ADD CONSTRAINT [fk_consultation_handler] FOREIGN KEY ([handled_by]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[consultation_request] WITH CHECK ADD CONSTRAINT [fk_consultation_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[consultation_request] WITH CHECK ADD CONSTRAINT [fk_consultation_sport] FOREIGN KEY ([sport_id]) REFERENCES [dbo].[sport] ([id]);
GO
ALTER TABLE [dbo].[course] WITH CHECK ADD CONSTRAINT [fk_course_sport] FOREIGN KEY ([sport_id]) REFERENCES [dbo].[sport] ([id]);
GO
ALTER TABLE [dbo].[course_offering] WITH CHECK ADD CONSTRAINT [fk_course_offering_course] FOREIGN KEY ([course_id]) REFERENCES [dbo].[course] ([id]);
GO
ALTER TABLE [dbo].[course_registration] WITH CHECK ADD CONSTRAINT [fk_course_registration_class] FOREIGN KEY ([class_id]) REFERENCES [dbo].[class] ([id]);
GO
ALTER TABLE [dbo].[course_registration] WITH CHECK ADD CONSTRAINT [fk_course_registration_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[facility] WITH CHECK ADD CONSTRAINT [fk_facility_sport] FOREIGN KEY ([sport_id]) REFERENCES [dbo].[sport] ([id]);
GO
ALTER TABLE [dbo].[facility_sport] WITH CHECK ADD CONSTRAINT [fk_facility_sport_facility] FOREIGN KEY ([facility_id]) REFERENCES [dbo].[facility] ([id]);
GO
ALTER TABLE [dbo].[facility_sport] WITH CHECK ADD CONSTRAINT [fk_facility_sport_sport] FOREIGN KEY ([sport_id]) REFERENCES [dbo].[sport] ([id]);
GO
ALTER TABLE [dbo].[individual_training_plan] WITH CHECK ADD CONSTRAINT [fk_individual_training_plan_class] FOREIGN KEY ([class_id]) REFERENCES [dbo].[class] ([id]);
GO
ALTER TABLE [dbo].[individual_training_plan] WITH CHECK ADD CONSTRAINT [fk_individual_training_plan_created_by] FOREIGN KEY ([created_by]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[individual_training_plan] WITH CHECK ADD CONSTRAINT [fk_individual_training_plan_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[individual_training_plan_exercise] WITH CHECK ADD CONSTRAINT [fk_individual_training_exercise_week] FOREIGN KEY ([week_id]) REFERENCES [dbo].[individual_training_plan_week] ([id]);
GO
ALTER TABLE [dbo].[individual_training_plan_revision] WITH CHECK ADD CONSTRAINT [fk_individual_training_revision_plan] FOREIGN KEY ([plan_id]) REFERENCES [dbo].[individual_training_plan] ([id]);
GO
ALTER TABLE [dbo].[individual_training_plan_revision] WITH CHECK ADD CONSTRAINT [fk_individual_training_revision_user] FOREIGN KEY ([revised_by]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[individual_training_plan_week] WITH CHECK ADD CONSTRAINT [fk_individual_training_week_revision] FOREIGN KEY ([revision_id]) REFERENCES [dbo].[individual_training_plan_revision] ([id]);
GO
ALTER TABLE [dbo].[member_profile] WITH CHECK ADD CONSTRAINT [fk_member_profile_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[member_sport_level] WITH CHECK ADD CONSTRAINT [fk_msl_coach] FOREIGN KEY ([verified_by_coach_id]) REFERENCES [dbo].[coach] ([id]);
GO
ALTER TABLE [dbo].[member_sport_level] WITH CHECK ADD CONSTRAINT [fk_msl_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[member_sport_level] WITH CHECK ADD CONSTRAINT [fk_msl_sport] FOREIGN KEY ([sport_id]) REFERENCES [dbo].[sport] ([id]);
GO
ALTER TABLE [dbo].[membership] WITH CHECK ADD CONSTRAINT [fk_membership_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[membership] WITH CHECK ADD CONSTRAINT [fk_membership_plan] FOREIGN KEY ([plan_id]) REFERENCES [dbo].[membership_plan] ([id]);
GO
ALTER TABLE [dbo].[membership] WITH CHECK ADD CONSTRAINT [fk_membership_purchase_payment] FOREIGN KEY ([purchase_payment_id]) REFERENCES [dbo].[payment] ([id]);
GO
ALTER TABLE [dbo].[membership_cancellation_request] WITH CHECK ADD CONSTRAINT [fk_membership_cancellation_request_decided_by] FOREIGN KEY ([decided_by]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[membership_cancellation_request] WITH CHECK ADD CONSTRAINT [fk_membership_cancellation_request_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[membership_cancellation_request] WITH CHECK ADD CONSTRAINT [fk_membership_cancellation_request_membership] FOREIGN KEY ([membership_id]) REFERENCES [dbo].[membership] ([id]);
GO
ALTER TABLE [dbo].[membership_upgrade] WITH CHECK ADD CONSTRAINT [fk_membership_upgrade_payment] FOREIGN KEY ([payment_id]) REFERENCES [dbo].[payment] ([id]);
GO
ALTER TABLE [dbo].[membership_upgrade] WITH CHECK ADD CONSTRAINT [fk_membership_upgrade_result] FOREIGN KEY ([result_membership_id]) REFERENCES [dbo].[membership] ([id]);
GO
ALTER TABLE [dbo].[membership_upgrade] WITH CHECK ADD CONSTRAINT [fk_membership_upgrade_source] FOREIGN KEY ([source_membership_id]) REFERENCES [dbo].[membership] ([id]);
GO
ALTER TABLE [dbo].[membership_upgrade] WITH CHECK ADD CONSTRAINT [fk_membership_upgrade_target_plan] FOREIGN KEY ([target_plan_id]) REFERENCES [dbo].[membership_plan] ([id]);
GO
ALTER TABLE [dbo].[notification] WITH CHECK ADD CONSTRAINT [fk_notification_user] FOREIGN KEY ([user_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[payment] WITH CHECK ADD CONSTRAINT [fk_payment_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[payment] WITH CHECK ADD CONSTRAINT [fk_payment_processed_by] FOREIGN KEY ([processed_by]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[payment] WITH CHECK ADD CONSTRAINT [fk_payment_refunded_by] FOREIGN KEY ([refunded_by]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[personal_training_booking] WITH CHECK ADD CONSTRAINT [fk_pt_booking_coach] FOREIGN KEY ([coach_id]) REFERENCES [dbo].[coach] ([id]);
GO
ALTER TABLE [dbo].[personal_training_booking] WITH CHECK ADD CONSTRAINT [fk_pt_booking_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[personal_training_booking] WITH CHECK ADD CONSTRAINT [fk_pt_booking_sport] FOREIGN KEY ([sport_id]) REFERENCES [dbo].[sport] ([id]);
GO
ALTER TABLE [dbo].[progress_evaluation] WITH CHECK ADD CONSTRAINT [fk_progress_evaluation_class] FOREIGN KEY ([class_id]) REFERENCES [dbo].[class] ([id]);
GO
ALTER TABLE [dbo].[progress_evaluation] WITH CHECK ADD CONSTRAINT [fk_progress_evaluation_evaluated_by] FOREIGN KEY ([evaluated_by]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[progress_evaluation] WITH CHECK ADD CONSTRAINT [fk_progress_evaluation_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[skill_assessment] WITH CHECK ADD CONSTRAINT [fk_assessment_consultation] FOREIGN KEY ([consultation_request_id]) REFERENCES [dbo].[consultation_request] ([id]);
GO
ALTER TABLE [dbo].[skill_assessment] WITH CHECK ADD CONSTRAINT [fk_sa_coach] FOREIGN KEY ([assigned_coach_id]) REFERENCES [dbo].[coach] ([id]);
GO
ALTER TABLE [dbo].[skill_assessment] WITH CHECK ADD CONSTRAINT [fk_sa_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[skill_assessment] WITH CHECK ADD CONSTRAINT [fk_sa_requester] FOREIGN KEY ([requested_by_user_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[skill_assessment] WITH CHECK ADD CONSTRAINT [fk_sa_sport] FOREIGN KEY ([sport_id]) REFERENCES [dbo].[sport] ([id]);
GO
ALTER TABLE [dbo].[support_ticket] WITH CHECK ADD CONSTRAINT [fk_support_ticket_assigned_staff] FOREIGN KEY ([assigned_staff_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[support_ticket] WITH CHECK ADD CONSTRAINT [fk_support_ticket_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[support_ticket_message] WITH CHECK ADD CONSTRAINT [fk_support_ticket_message_sender] FOREIGN KEY ([sender_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[support_ticket_message] WITH CHECK ADD CONSTRAINT [fk_support_ticket_message_ticket] FOREIGN KEY ([ticket_id]) REFERENCES [dbo].[support_ticket] ([id]);
GO
ALTER TABLE [dbo].[training_exercise] WITH CHECK ADD CONSTRAINT [fk_training_exercise_plan] FOREIGN KEY ([plan_id]) REFERENCES [dbo].[training_plan] ([id]);
GO
ALTER TABLE [dbo].[training_plan] WITH CHECK ADD CONSTRAINT [fk_training_plan_class] FOREIGN KEY ([class_id]) REFERENCES [dbo].[class] ([id]);
GO
ALTER TABLE [dbo].[training_plan] WITH CHECK ADD CONSTRAINT [fk_training_plan_updated_by] FOREIGN KEY ([updated_by]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[training_result] WITH CHECK ADD CONSTRAINT [fk_training_result_member] FOREIGN KEY ([member_id]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[training_result] WITH CHECK ADD CONSTRAINT [fk_training_result_recorded_by] FOREIGN KEY ([recorded_by]) REFERENCES [dbo].[users] ([id]);
GO
ALTER TABLE [dbo].[training_result] WITH CHECK ADD CONSTRAINT [fk_training_result_session] FOREIGN KEY ([class_session_id]) REFERENCES [dbo].[class_session] ([id]);
GO
-- SQL FUNCTIONS AND STORED PROCEDURES (diagram support objects)
SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO
CREATE FUNCTION dbo.fn_diagramobjects()
	RETURNS int
	WITH EXECUTE AS N'dbo'
	AS
	BEGIN
		declare @id_upgraddiagrams		int
		declare @id_sysdiagrams			int
		declare @id_helpdiagrams		int
		declare @id_helpdiagramdefinition	int
		declare @id_creatediagram	int
		declare @id_renamediagram	int
		declare @id_alterdiagram 	int
		declare @id_dropdiagram		int
		declare @InstalledObjects	int

		select @InstalledObjects = 0

		select 	@id_upgraddiagrams = object_id(N'dbo.sp_upgraddiagrams'),
			@id_sysdiagrams = object_id(N'dbo.sysdiagrams'),
			@id_helpdiagrams = object_id(N'dbo.sp_helpdiagrams'),
			@id_helpdiagramdefinition = object_id(N'dbo.sp_helpdiagramdefinition'),
			@id_creatediagram = object_id(N'dbo.sp_creatediagram'),
			@id_renamediagram = object_id(N'dbo.sp_renamediagram'),
			@id_alterdiagram = object_id(N'dbo.sp_alterdiagram'),
			@id_dropdiagram = object_id(N'dbo.sp_dropdiagram')

		if @id_upgraddiagrams is not null
			select @InstalledObjects = @InstalledObjects + 1
		if @id_sysdiagrams is not null
			select @InstalledObjects = @InstalledObjects + 2
		if @id_helpdiagrams is not null
			select @InstalledObjects = @InstalledObjects + 4
		if @id_helpdiagramdefinition is not null
			select @InstalledObjects = @InstalledObjects + 8
		if @id_creatediagram is not null
			select @InstalledObjects = @InstalledObjects + 16
		if @id_renamediagram is not null
			select @InstalledObjects = @InstalledObjects + 32
		if @id_alterdiagram  is not null
			select @InstalledObjects = @InstalledObjects + 64
		if @id_dropdiagram is not null
			select @InstalledObjects = @InstalledObjects + 128

		return @InstalledObjects
	END
GO

SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO
CREATE PROCEDURE dbo.sp_alterdiagram
	(
		@diagramname 	sysname,
		@owner_id	int	= null,
		@version 	int,
		@definition 	varbinary(max)
	)
	WITH EXECUTE AS 'dbo'
	AS
	BEGIN
		set nocount on

		declare @theId 			int
		declare @retval 		int
		declare @IsDbo 			int

		declare @UIDFound 		int
		declare @DiagId			int
		declare @ShouldChangeUID	int

		if(@diagramname is null)
		begin
			RAISERROR ('Invalid ARG', 16, 1)
			return -1
		end

		execute as caller;
		select @theId = DATABASE_PRINCIPAL_ID();
		select @IsDbo = IS_MEMBER(N'db_owner');
		if(@owner_id is null)
			select @owner_id = @theId;
		revert;

		select @ShouldChangeUID = 0
		select @DiagId = diagram_id, @UIDFound = principal_id from dbo.sysdiagrams where principal_id = @owner_id and name = @diagramname

		if(@DiagId IS NULL or (@IsDbo = 0 and @theId <> @UIDFound))
		begin
			RAISERROR ('Diagram does not exist or you do not have permission.', 16, 1);
			return -3
		end

		if(@IsDbo <> 0)
		begin
			if(@UIDFound is null or USER_NAME(@UIDFound) is null) -- invalid principal_id
			begin
				select @ShouldChangeUID = 1 ;
			end
		end

		-- update dds data
		update dbo.sysdiagrams set definition = @definition where diagram_id = @DiagId ;

		-- change owner
		if(@ShouldChangeUID = 1)
			update dbo.sysdiagrams set principal_id = @theId where diagram_id = @DiagId ;

		-- update dds version
		if(@version is not null)
			update dbo.sysdiagrams set version = @version where diagram_id = @DiagId ;

		return 0
	END
GO

SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO
CREATE PROCEDURE dbo.sp_creatediagram
	(
		@diagramname 	sysname,
		@owner_id		int	= null,
		@version 		int,
		@definition 	varbinary(max)
	)
	WITH EXECUTE AS 'dbo'
	AS
	BEGIN
		set nocount on

		declare @theId int
		declare @retval int
		declare @IsDbo	int
		declare @userName sysname
		if(@version is null or @diagramname is null)
		begin
			RAISERROR (N'E_INVALIDARG', 16, 1);
			return -1
		end

		execute as caller;
		select @theId = DATABASE_PRINCIPAL_ID();
		select @IsDbo = IS_MEMBER(N'db_owner');
		revert;

		if @owner_id is null
		begin
			select @owner_id = @theId;
		end
		else
		begin
			if @theId <> @owner_id
			begin
				if @IsDbo = 0
				begin
					RAISERROR (N'E_INVALIDARG', 16, 1);
					return -1
				end
				select @theId = @owner_id
			end
		end
		-- next 2 line only for test, will be removed after define name unique
		if EXISTS(select diagram_id from dbo.sysdiagrams where principal_id = @theId and name = @diagramname)
		begin
			RAISERROR ('The name is already used.', 16, 1);
			return -2
		end

		insert into dbo.sysdiagrams(name, principal_id , version, definition)
				VALUES(@diagramname, @theId, @version, @definition) ;

		select @retval = @@IDENTITY
		return @retval
	END
GO

SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO
CREATE PROCEDURE dbo.sp_dropdiagram
	(
		@diagramname 	sysname,
		@owner_id	int	= null
	)
	WITH EXECUTE AS 'dbo'
	AS
	BEGIN
		set nocount on
		declare @theId 			int
		declare @IsDbo 			int

		declare @UIDFound 		int
		declare @DiagId			int

		if(@diagramname is null)
		begin
			RAISERROR ('Invalid value', 16, 1);
			return -1
		end

		EXECUTE AS CALLER;
		select @theId = DATABASE_PRINCIPAL_ID();
		select @IsDbo = IS_MEMBER(N'db_owner');
		if(@owner_id is null)
			select @owner_id = @theId;
		REVERT;

		select @DiagId = diagram_id, @UIDFound = principal_id from dbo.sysdiagrams where principal_id = @owner_id and name = @diagramname
		if(@DiagId IS NULL or (@IsDbo = 0 and @UIDFound <> @theId))
		begin
			RAISERROR ('Diagram does not exist or you do not have permission.', 16, 1)
			return -3
		end

		delete from dbo.sysdiagrams where diagram_id = @DiagId;

		return 0;
	END
GO

SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO
CREATE PROCEDURE dbo.sp_helpdiagramdefinition
	(
		@diagramname 	sysname,
		@owner_id	int	= null
	)
	WITH EXECUTE AS N'dbo'
	AS
	BEGIN
		set nocount on

		declare @theId 		int
		declare @IsDbo 		int
		declare @DiagId		int
		declare @UIDFound	int

		if(@diagramname is null)
		begin
			RAISERROR (N'E_INVALIDARG', 16, 1);
			return -1
		end

		execute as caller;
		select @theId = DATABASE_PRINCIPAL_ID();
		select @IsDbo = IS_MEMBER(N'db_owner');
		if(@owner_id is null)
			select @owner_id = @theId;
		revert;

		select @DiagId = diagram_id, @UIDFound = principal_id from dbo.sysdiagrams where principal_id = @owner_id and name = @diagramname;
		if(@DiagId IS NULL or (@IsDbo = 0 and @UIDFound <> @theId ))
		begin
			RAISERROR ('Diagram does not exist or you do not have permission.', 16, 1);
			return -3
		end

		select version, definition FROM dbo.sysdiagrams where diagram_id = @DiagId ;
		return 0
	END
GO

SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO
CREATE PROCEDURE dbo.sp_helpdiagrams
	(
		@diagramname sysname = NULL,
		@owner_id int = NULL
	)
	WITH EXECUTE AS N'dbo'
	AS
	BEGIN
		DECLARE @user sysname
		DECLARE @dboLogin bit
		EXECUTE AS CALLER;
			SET @user = USER_NAME();
			SET @dboLogin = CONVERT(bit,IS_MEMBER('db_owner'));
		REVERT;
		SELECT
			[Database] = DB_NAME(),
			[Name] = name,
			[ID] = diagram_id,
			[Owner] = USER_NAME(principal_id),
			[OwnerID] = principal_id
		FROM
			sysdiagrams
		WHERE
			(@dboLogin = 1 OR USER_NAME(principal_id) = @user) AND
			(@diagramname IS NULL OR name = @diagramname) AND
			(@owner_id IS NULL OR principal_id = @owner_id)
		ORDER BY
			4, 5, 1
	END
GO

SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO
CREATE PROCEDURE dbo.sp_renamediagram
	(
		@diagramname 		sysname,
		@owner_id		int	= null,
		@new_diagramname	sysname

	)
	WITH EXECUTE AS 'dbo'
	AS
	BEGIN
		set nocount on
		declare @theId 			int
		declare @IsDbo 			int

		declare @UIDFound 		int
		declare @DiagId			int
		declare @DiagIdTarg		int
		declare @u_name			sysname
		if((@diagramname is null) or (@new_diagramname is null))
		begin
			RAISERROR ('Invalid value', 16, 1);
			return -1
		end

		EXECUTE AS CALLER;
		select @theId = DATABASE_PRINCIPAL_ID();
		select @IsDbo = IS_MEMBER(N'db_owner');
		if(@owner_id is null)
			select @owner_id = @theId;
		REVERT;

		select @u_name = USER_NAME(@owner_id)

		select @DiagId = diagram_id, @UIDFound = principal_id from dbo.sysdiagrams where principal_id = @owner_id and name = @diagramname
		if(@DiagId IS NULL or (@IsDbo = 0 and @UIDFound <> @theId))
		begin
			RAISERROR ('Diagram does not exist or you do not have permission.', 16, 1)
			return -3
		end

		-- if((@u_name is not null) and (@new_diagramname = @diagramname))	-- nothing will change
		--	return 0;

		if(@u_name is null)
			select @DiagIdTarg = diagram_id from dbo.sysdiagrams where principal_id = @theId and name = @new_diagramname
		else
			select @DiagIdTarg = diagram_id from dbo.sysdiagrams where principal_id = @owner_id and name = @new_diagramname

		if((@DiagIdTarg is not null) and  @DiagId <> @DiagIdTarg)
		begin
			RAISERROR ('The name is already used.', 16, 1);
			return -2
		end

		if(@u_name is null)
			update dbo.sysdiagrams set [name] = @new_diagramname, principal_id = @theId where diagram_id = @DiagId
		else
			update dbo.sysdiagrams set [name] = @new_diagramname where diagram_id = @DiagId
		return 0
	END
GO

SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO
CREATE PROCEDURE dbo.sp_upgraddiagrams
	AS
	BEGIN
		IF OBJECT_ID(N'dbo.sysdiagrams') IS NOT NULL
			return 0;

		CREATE TABLE dbo.sysdiagrams
		(
			name sysname NOT NULL,
			principal_id int NOT NULL,	-- we may change it to varbinary(85)
			diagram_id int PRIMARY KEY IDENTITY,
			version int,

			definition varbinary(max)
			CONSTRAINT UK_principal_name UNIQUE
			(
				principal_id,
				name
			)
		);


		/* Add this if we need to have some form of extended properties for diagrams */
		/*
		IF OBJECT_ID(N'dbo.sysdiagram_properties') IS NULL
		BEGIN
			CREATE TABLE dbo.sysdiagram_properties
			(
				diagram_id int,
				name sysname,
				value varbinary(max) NOT NULL
			)
		END
		*/

		IF OBJECT_ID(N'dbo.dtproperties') IS NOT NULL
		begin
			insert into dbo.sysdiagrams
			(
				[name],
				[principal_id],
				[version],
				[definition]
			)
			select
				convert(sysname, dgnm.[uvalue]),
				DATABASE_PRINCIPAL_ID(N'dbo'),			-- will change to the sid of sa
				0,							-- zero for old format, dgdef.[version],
				dgdef.[lvalue]
			from dbo.[dtproperties] dgnm
				inner join dbo.[dtproperties] dggd on dggd.[property] = 'DtgSchemaGUID' and dggd.[objectid] = dgnm.[objectid]
				inner join dbo.[dtproperties] dgdef on dgdef.[property] = 'DtgSchemaDATA' and dgdef.[objectid] = dgnm.[objectid]

			where dgnm.[property] = 'DtgSchemaNAME' and dggd.[uvalue] like N'_EA3E6268-D998-11CE-9454-00AA00A3F36E_'
			return 2;
		end
		return 1;
	END
GO
