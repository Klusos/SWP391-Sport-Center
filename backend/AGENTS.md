# SportCenter — Codex Project Instructions

## Technology
- Backend: Java, Spring Boot 4.1.1, Spring Security, Spring Data JPA
- Database: Microsoft SQL Server
- Authentication: JWT with Spring Security OAuth2 Resource Server
- Password hashing: BCrypt
- Work with the existing codebase; do not recreate classes that already exist.

## Actors
- ADMIN: System and staff management
- RECEPTIONIST: Consult customers and create MEMBER accounts
- MEMBER: Manage personal information and training profile
- COACH: Manage assigned coaching activities

## Business Rules
- There is no public self-registration.
- ADMIN and RECEPTIONIST can create MEMBER accounts.
- Accounts created through member registration must receive the MEMBER role from the backend.
- ADMIN user management may assign only ADMIN, RECEPTIONIST, MEMBER, or COACH roles.
- MEMBER completes their own MemberProfile after account creation.
- Each MEMBER has at most one MemberProfile.
- Use the authenticated JWT subject to identify the current user.
- Do not allow users to modify another member's profile.
- Date of birth is stored; age should be calculated rather than stored.
- Course registration, membership purchase, and payment are separate operations.

## Current Development
- Login with JWT is implemented.
- Basic role-based authorization is implemented.
- GET /api/users/me, receptionist-managed MEMBER registration, and MemberProfile management endpoints are implemented in source.
- Check docs/IMPLEMENTATION_STATUS.md for the current build and Postman verification status; do not infer successful verification from code alone.

## Coding Rules
- Inspect existing entities, DTOs, repositories, services, controllers, and security configuration before writing code.
- Reuse existing methods where appropriate.
- Do not change database schema without explaining the migration.
- Never expose passwordHash in API responses.
- Explain proposed changes before making large modifications.
- Prefer small changes that can be tested with Postman.
- Do not overwrite teammates' work or unrelated files.
