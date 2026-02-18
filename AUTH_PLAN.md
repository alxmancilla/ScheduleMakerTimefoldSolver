# Authentication & Authorization Implementation Plan

## Executive Summary

This document outlines the complete implementation plan for adding a secure authentication and authorization system to the Schedule Maker application. The system will support role-based access control (RBAC) with four distinct user roles: Admin, Editor, Teacher, and Student.

**Estimated Timeline**: 30-35 hours (4-5 working days)  
**Security Level**: Production-ready with industry best practices  
**Technology Stack**: Spring Security 6.x + JWT + BCrypt + React Context API

---

## Table of Contents

1. [Security Requirements & Best Practices](#security-requirements--best-practices)
2. [User Roles & Permissions](#user-roles--permissions)
3. [Technical Architecture](#technical-architecture)
4. [Database Schema](#database-schema)
5. [Implementation Phases](#implementation-phases)
6. [Security Hardening Checklist](#security-hardening-checklist)
7. [Testing Strategy](#testing-strategy)
8. [Deployment Checklist](#deployment-checklist)

---

## Security Requirements & Best Practices

### ✅ Authentication Security

**Password Management**:
- ✅ BCrypt hashing with cost factor 12 (OWASP recommended)
- ✅ Minimum password requirements: 8 characters, uppercase, lowercase, number, special character
- ✅ Password history tracking (prevent reuse of last 5 passwords)
- ✅ Secure password reset flow with time-limited tokens (1 hour expiry)
- ✅ Force password change on first login (optional)

**Token Security**:
- ✅ JWT with RS256 algorithm (asymmetric signing)
- ✅ Short-lived access tokens: 15 minutes
- ✅ Long-lived refresh tokens: 7 days
- ✅ Refresh token rotation (one-time use)
- ✅ Token revocation on logout
- ✅ Secure token storage (HttpOnly cookies for refresh tokens)

**Account Security**:
- ✅ Account lockout after 5 failed login attempts
- ✅ Lockout duration: 30 minutes
- ✅ Session timeout after 30 minutes of inactivity
- ✅ Force logout on password change
- ✅ Email verification for new accounts (optional)
- ✅ Multi-device session management

### ✅ Authorization Security

**Access Control**:
- ✅ Role-Based Access Control (RBAC)
- ✅ Principle of Least Privilege
- ✅ Method-level security with `@PreAuthorize`
- ✅ Custom permission evaluators for complex rules
- ✅ Data filtering based on user context (teachers see own data, students see own group)

**API Security**:
- ✅ Rate limiting on authentication endpoints (10 requests/minute per IP)
- ✅ CORS properly configured (whitelist frontend origins)
- ✅ CSRF protection disabled (using JWT, stateless)
- ✅ HTTPS enforcement in production
- ✅ Security headers: CSP, X-Frame-Options, X-Content-Type-Options, HSTS
- ✅ Input validation and sanitization
- ✅ SQL injection prevention (JPA parameterized queries)
- ✅ XSS prevention (React auto-escaping + CSP)

### ✅ Audit & Monitoring

**Logging**:
- ✅ Log all authentication events (login, logout, failed attempts)
- ✅ Log all authorization failures
- ✅ Log all data modifications (create, update, delete)
- ✅ Include: user ID, action, timestamp, IP address, user agent
- ✅ Secure log storage (append-only, tamper-proof)

**Monitoring**:
- ✅ Alert on multiple failed login attempts (>3 in 5 minutes)
- ✅ Alert on account lockouts
- ✅ Alert on privilege escalation attempts
- ✅ Dashboard for security metrics (admin only)

### ✅ Compliance & Privacy

**Data Protection**:
- ✅ Passwords never logged or exposed
- ✅ Tokens never logged in plain text
- ✅ Personal data encrypted at rest (database encryption)
- ✅ Personal data encrypted in transit (HTTPS)
- ✅ GDPR compliance: right to access, right to deletion

**Session Management**:
- ✅ Secure session handling (no session fixation)
- ✅ Logout invalidates all tokens
- ✅ Concurrent session control (optional: limit to 3 devices)

---

## User Roles & Permissions

### Role Hierarchy

```
ADMIN (Highest Privilege)
  └── Full system access
  
EDITOR (Data Management)
  └── CRUD on schedules, courses, rooms, groups, assignments
  
TEACHER (Personal Data)
  └── View own schedule, edit own availability
  
STUDENT (Read-Only)
  └── View own schedule
```

### Detailed Permissions Matrix

| Feature/Action | Admin | Editor | Teacher | Student |
|----------------|:-----:|:------:|:-------:|:-------:|
| **Authentication** |
| Login/Logout | ✅ | ✅ | ✅ | ✅ |
| Change own password | ✅ | ✅ | ✅ | ✅ |
| Reset own password | ✅ | ✅ | ✅ | ✅ |
| **Schedule View** |
| View all schedules | ✅ | ✅ | ❌ | ❌ |
| View own schedule | ✅ | ✅ | ✅ | ✅ |
| Filter by group/teacher | ✅ | ✅ | ✅ | ❌ |
| Export to PDF | ✅ | ✅ | ✅ (own) | ✅ (own) |
| **Teachers** |
| View all teachers | ✅ | ✅ | ✅ (read-only) | ❌ |
| Create teacher | ✅ | ✅ | ❌ | ❌ |
| Edit teacher | ✅ | ✅ | ❌ | ❌ |
| Delete teacher | ✅ | ✅ | ❌ | ❌ |
| Edit own availability | ✅ | ✅ | ✅ | ❌ |
| Edit own qualifications | ✅ | ✅ | ❌ | ❌ |
| **Courses** |
| View courses | ✅ | ✅ | ✅ | ✅ |
| Create/Edit/Delete courses | ✅ | ✅ | ❌ | ❌ |
| **Rooms** |
| View rooms | ✅ | ✅ | ✅ | ✅ |
| Create/Edit/Delete rooms | ✅ | ✅ | ❌ | ❌ |
| **Student Groups** |
| View all groups | ✅ | ✅ | ✅ | ❌ |
| View own group | ✅ | ✅ | ✅ | ✅ |
| Create/Edit/Delete groups | ✅ | ✅ | ❌ | ❌ |
| **Assignments** |
| View all assignments | ✅ | ✅ | ❌ | ❌ |
| View own assignments | ✅ | ✅ | ✅ | ✅ |
| Create assignment | ✅ | ✅ | ❌ | ❌ |
| Edit assignment | ✅ | ✅ | ❌ | ❌ |
| Delete assignment | ✅ | ✅ | ❌ | ❌ |
| Pin/Unpin assignment | ✅ | ✅ | ❌ | ❌ |
| **Solver** |
| Run solver | ✅ | ❌ | ❌ | ❌ |
| View solver status | ✅ | ✅ | ❌ | ❌ |
| View solver logs | ✅ | ❌ | ❌ | ❌ |
| **User Management** |
| View all users | ✅ | ❌ | ❌ | ❌ |
| Create user | ✅ | ❌ | ❌ | ❌ |
| Edit user | ✅ | ❌ | ❌ | ❌ |
| Delete user | ✅ | ❌ | ❌ | ❌ |
| Reset user password | ✅ | ❌ | ❌ | ❌ |
| Lock/Unlock account | ✅ | ❌ | ❌ | ❌ |
| **Audit & Security** |
| View audit logs | ✅ | ❌ | ❌ | ❌ |
| Export audit logs | ✅ | ❌ | ❌ | ❌ |
| View security dashboard | ✅ | ❌ | ❌ | ❌ |
| **Profile** |
| View own profile | ✅ | ✅ | ✅ | ✅ |
| Edit own profile | ✅ | ✅ | ✅ | ✅ |

### Role-Specific Features

**ADMIN**:
- Complete system control
- User lifecycle management (create, edit, delete, lock/unlock)
- Run optimization solver
- Access audit logs and security metrics
- Impersonate users for troubleshooting (with audit trail)
- System configuration

**EDITOR**:
- Full CRUD on academic data (courses, rooms, groups, assignments)
- View all schedules and reports
- Cannot manage users or access security features
- Cannot run solver (only view results)
- Ideal for: Academic coordinators, administrative staff

**TEACHER**:
- View personal teaching schedule
- Manage own availability (days/hours available to teach)
- View courses, rooms, and groups (read-only)
- Export personal schedule to PDF
- Cannot modify assignments or other teachers' data
- Ideal for: Faculty members

**STUDENT**:
- View personal class schedule (based on group membership)
- View course information
- Export personal schedule to PDF
- Completely read-only access
- Ideal for: Students, parents (with student account)

---

## Technical Architecture

### Backend Stack

**Framework**: Spring Boot 3.2.1 + Spring Security 6.x
**Authentication**: JWT (JSON Web Tokens) with RS256 signing
**Password Hashing**: BCrypt (cost factor 12)
**Database**: PostgreSQL 15+
**ORM**: Spring Data JPA + Hibernate

### Frontend Stack

**Framework**: React 18.2.0
**State Management**: React Context API
**Routing**: React Router 6.x with route guards
**HTTP Client**: Axios with interceptors
**Token Storage**: LocalStorage (access token) + HttpOnly Cookie (refresh token)
**Form Validation**: React Hook Form

### Security Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         Frontend (React)                     │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────┐    │
│  │ Login Page │→ │ AuthContext  │→ │ Protected Routes │    │
│  └────────────┘  └──────────────┘  └──────────────────┘    │
│         │              │                      │              │
│         ↓              ↓                      ↓              │
│  ┌─────────────────────────────────────────────────┐        │
│  │         Axios Interceptors (JWT Injection)      │        │
│  └─────────────────────────────────────────────────┘        │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTPS
                             ↓
┌─────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot)                     │
│  ┌─────────────────────────────────────────────────┐        │
│  │      Spring Security Filter Chain               │        │
│  │  ┌──────────────┐  ┌────────────────────────┐  │        │
│  │  │ CORS Filter  │→ │ JWT Authentication     │  │        │
│  │  └──────────────┘  │ Filter                 │  │        │
│  │                    └────────────────────────┘  │        │
│  └─────────────────────────────────────────────────┘        │
│         │                                                    │
│         ↓                                                    │
│  ┌─────────────────────────────────────────────────┐        │
│  │         Controllers (@PreAuthorize)             │        │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────┐   │        │
│  │  │   Auth   │ │ Teachers │ │  Assignments │   │        │
│  │  └──────────┘ └──────────┘ └──────────────┘   │        │
│  └─────────────────────────────────────────────────┘        │
│         │                                                    │
│         ↓                                                    │
│  ┌─────────────────────────────────────────────────┐        │
│  │         Services (Business Logic)               │        │
│  └─────────────────────────────────────────────────┘        │
│         │                                                    │
│         ↓                                                    │
│  ┌─────────────────────────────────────────────────┐        │
│  │         Repositories (JPA)                      │        │
│  └─────────────────────────────────────────────────┘        │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ↓
                    ┌─────────────────┐
                    │   PostgreSQL    │
                    │   (Encrypted)   │
                    └─────────────────┘
```

---

## Database Schema

### Core Authentication Tables

```sql
-- Users table
CREATE TABLE app_user (
    id VARCHAR(100) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(200) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(200),
    role VARCHAR(50) NOT NULL CHECK (role IN ('ADMIN', 'EDITOR', 'TEACHER', 'STUDENT')),

    -- Foreign keys to link users to their domain entities
    teacher_id VARCHAR(100),
    student_group_id VARCHAR(100),

    -- Account status
    enabled BOOLEAN DEFAULT true,
    account_locked BOOLEAN DEFAULT false,
    password_expired BOOLEAN DEFAULT false,
    must_change_password BOOLEAN DEFAULT false,

    -- Security tracking
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    last_login TIMESTAMP,
    last_password_change TIMESTAMP,

    -- Audit timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),

    FOREIGN KEY (teacher_id) REFERENCES teacher(id) ON DELETE SET NULL,
    FOREIGN KEY (student_group_id) REFERENCES student_group(id) ON DELETE SET NULL
);

CREATE INDEX idx_app_user_username ON app_user(username);
CREATE INDEX idx_app_user_email ON app_user(email);
CREATE INDEX idx_app_user_role ON app_user(role);
CREATE INDEX idx_app_user_teacher_id ON app_user(teacher_id);
CREATE INDEX idx_app_user_student_group_id ON app_user(student_group_id);

-- Refresh tokens for JWT
CREATE TABLE refresh_token (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    token VARCHAR(500) UNIQUE NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT false,
    replaced_by_token VARCHAR(500),
    device_info VARCHAR(500),
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);
CREATE INDEX idx_refresh_token_token ON refresh_token(token);
CREATE INDEX idx_refresh_token_expiry ON refresh_token(expiry_date);

-- Password reset tokens
CREATE TABLE password_reset_token (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    token VARCHAR(500) UNIQUE NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT false,
    used_at TIMESTAMP,
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_token_token ON password_reset_token(token);
CREATE INDEX idx_password_reset_token_user_id ON password_reset_token(user_id);

-- Password history (prevent reuse)
CREATE TABLE password_history (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_password_history_user_id ON password_history(user_id);

-- Audit log
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100),
    username VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    details TEXT,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    success BOOLEAN DEFAULT true,
    error_message TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
```

### Initial Data

```sql
-- Create default admin user
-- Password: Admin123! (MUST be changed on first login)
INSERT INTO app_user (id, username, email, password_hash, full_name, role, must_change_password, created_at)
VALUES (
    'admin-001',
    'admin',
    'admin@school.edu',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYVvMpYKZPe',  -- BCrypt hash of "Admin123!"
    'System Administrator',
    'ADMIN',
    true,
    CURRENT_TIMESTAMP
);
```

---

## Implementation Phases

### Phase 1: Backend - Database & Entities (2-3 hours)

**Objective**: Set up database schema and JPA entities for authentication.

**Tasks**:
1. ✅ Create database migration script (`database/auth_schema.sql`)
2. ✅ Create JPA entities:
   - `UserEntity.java` - User account with role and security fields
   - `RefreshTokenEntity.java` - JWT refresh tokens
   - `AuditLogEntity.java` - Security audit trail
   - `PasswordResetTokenEntity.java` - Password reset tokens
   - `PasswordHistoryEntity.java` - Password history for reuse prevention
3. ✅ Create repositories:
   - `UserRepository.java` - User CRUD with custom queries
   - `RefreshTokenRepository.java` - Token management
   - `AuditLogRepository.java` - Audit log queries
   - `PasswordResetTokenRepository.java` - Reset token queries
   - `PasswordHistoryRepository.java` - Password history queries
4. ✅ Create enums:
   - `UserRole.java` - ADMIN, EDITOR, TEACHER, STUDENT
   - `AuditAction.java` - LOGIN, LOGOUT, CREATE, UPDATE, DELETE, etc.

**Deliverables**:
- ✅ Database schema created and tested
- ✅ JPA entities and repositories functional
- ✅ Can persist users to database
- ✅ Default admin user created

**Testing**:
- Run migration script successfully
- Insert test users for each role
- Verify foreign key constraints work
- Verify indexes are created

---

### Phase 2: Backend - Spring Security Configuration (3-4 hours)

**Objective**: Configure Spring Security with JWT authentication.

**Tasks**:
1. ✅ Add Spring Security dependencies to `pom.xml`:
   - `spring-boot-starter-security`
   - `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (JWT library)
   - `passay` (password validation)
2. ✅ Create `SecurityConfig.java`:
   - Configure JWT authentication filter chain
   - Define public endpoints: `/api/auth/**`
   - Define protected endpoints: `/api/**`
   - Configure CORS for frontend origins
   - Disable CSRF (using stateless JWT)
   - Configure password encoder (BCrypt with cost 12)
3. ✅ Create JWT utilities:
   - `JwtTokenProvider.java` - Generate/validate/parse JWT tokens
   - `JwtAuthenticationFilter.java` - Extract and validate JWT from requests
   - `JwtAuthenticationEntryPoint.java` - Handle authentication errors (401)
4. ✅ Create `UserDetailsServiceImpl.java`:
   - Load user from database by username
   - Map to Spring Security UserDetails
5. ✅ Create `CustomAccessDeniedHandler.java`:
   - Handle authorization errors (403)

**Deliverables**:
- ✅ Spring Security configured and active
- ✅ JWT generation/validation working
- ✅ Protected endpoints require valid JWT
- ✅ Public endpoints accessible without authentication
- ✅ CORS configured for React frontend

**Testing**:
- Access public endpoint without token (should succeed)
- Access protected endpoint without token (should return 401)
- Access protected endpoint with invalid token (should return 401)
- Verify CORS headers present in responses

---

### Phase 3: Backend - Authentication API (2-3 hours)

**Objective**: Implement authentication endpoints (login, logout, refresh, etc.).

**Tasks**:
1. ✅ Create DTOs:
   - `LoginRequest.java` - username, password
   - `LoginResponse.java` - accessToken, refreshToken, user info, expiresIn
   - `RegisterRequest.java` - username, email, password, role, teacherId, studentGroupId
   - `ChangePasswordRequest.java` - oldPassword, newPassword
   - `ForgotPasswordRequest.java` - email
   - `ResetPasswordRequest.java` - token, newPassword
   - `RefreshTokenRequest.java` - refreshToken
   - `UserDTO.java` - User information (no password)
2. ✅ Create `AuthController.java`:
   - `POST /api/auth/login` - Authenticate user, return JWT tokens
   - `POST /api/auth/logout` - Revoke refresh token
   - `POST /api/auth/refresh` - Get new access token using refresh token
   - `POST /api/auth/register` - Register new user (admin only)
   - `POST /api/auth/forgot-password` - Request password reset email
   - `POST /api/auth/reset-password` - Reset password with token
   - `POST /api/auth/change-password` - Change own password (authenticated)
   - `GET /api/auth/me` - Get current user info
3. ✅ Create `AuthService.java`:
   - Business logic for all auth operations
   - Password validation (complexity, history)
   - Token generation and validation
   - Account lockout logic
   - Failed login attempt tracking
4. ✅ Create `AuditService.java`:
   - Log all authentication events
   - Log authorization failures
   - Log data modifications

**Deliverables**:
- ✅ Login endpoint returns valid JWT tokens
- ✅ Logout revokes refresh tokens
- ✅ Token refresh works correctly
- ✅ Password reset flow functional
- ✅ All auth events logged to audit_log table

**Testing**:
- Login with valid credentials (should return tokens)
- Login with invalid credentials (should return 401, increment failed attempts)
- Login after 5 failed attempts (should lock account)
- Refresh token (should return new access token)
- Logout (should revoke refresh token)
- Change password (should update password and invalidate old tokens)
- Reset password flow (request → receive token → reset)

---

### Phase 4: Backend - Authorization & Role-Based Access (2-3 hours)

**Objective**: Implement role-based access control on all endpoints.

**Tasks**:
1. ✅ Add `@PreAuthorize` annotations to existing controllers:
   - `TeacherController.java`:
     - GET endpoints: `@PreAuthorize("isAuthenticated()")`
     - POST/PUT/DELETE: `@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")`
   - `CourseController.java`:
     - GET endpoints: `@PreAuthorize("isAuthenticated()")`
     - POST/PUT/DELETE: `@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")`
   - `RoomController.java`:
     - GET endpoints: `@PreAuthorize("isAuthenticated()")`
     - POST/PUT/DELETE: `@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")`
   - `StudentGroupController.java`:
     - GET endpoints: `@PreAuthorize("isAuthenticated()")`
     - POST/PUT/DELETE: `@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")`
   - `AssignmentController.java`:
     - GET endpoints: `@PreAuthorize("isAuthenticated()")`
     - POST/PUT/DELETE: `@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")`
   - `ScheduleController.java`:
     - All endpoints: `@PreAuthorize("isAuthenticated()")`
2. ✅ Create custom permission evaluators:
   - `SchedulePermissionEvaluator.java` - Filter schedules by user role
   - `TeacherPermissionEvaluator.java` - Allow teachers to edit own availability
3. ✅ Update `ScheduleController.java`:
   - Filter schedules based on user role:
     - ADMIN/EDITOR: See all schedules
     - TEACHER: See only own assignments
     - STUDENT: See only own group's assignments
4. ✅ Update `TeacherController.java`:
   - Add endpoint for teachers to edit own availability:
     - `PUT /api/teachers/me/availability` - `@PreAuthorize("hasRole('TEACHER')")`
5. ✅ Create `UserController.java` (admin only):
   - `GET /api/users` - List all users
   - `GET /api/users/{id}` - Get user by ID
   - `POST /api/users` - Create user
   - `PUT /api/users/{id}` - Update user
   - `DELETE /api/users/{id}` - Delete user
   - `POST /api/users/{id}/unlock` - Unlock account
   - `POST /api/users/{id}/reset-password` - Admin reset password

**Deliverables**:
- ✅ Role-based access control enforced on all endpoints
- ✅ Users can only access data they're authorized for
- ✅ Teachers can edit own availability
- ✅ Students see only own group's schedule
- ✅ Admin can manage users

**Testing**:
- Login as ADMIN - access all endpoints (should succeed)
- Login as EDITOR - access data endpoints (should succeed), user management (should fail 403)
- Login as TEACHER - view own schedule (should succeed), edit other teacher (should fail 403)
- Login as STUDENT - view own schedule (should succeed), edit anything (should fail 403)
- Verify audit logs capture authorization failures

---

### Phase 5: Frontend - Auth Context & Services (2-3 hours)

**Objective**: Set up global authentication state and API integration.

**Tasks**:
1. ✅ Create `web-ui/src/context/AuthContext.jsx`:
   - State: `user`, `isAuthenticated`, `isLoading`, `error`
   - Functions:
     - `login(username, password)` - Authenticate and store tokens
     - `logout()` - Clear tokens and user state
     - `refreshToken()` - Get new access token
     - `checkAuth()` - Verify current authentication status
     - `changePassword(oldPassword, newPassword)` - Change password
   - Auto-refresh token on mount
   - Auto-logout on token expiration
2. ✅ Create `web-ui/src/services/authService.js`:
   - API calls for all auth endpoints
   - Token storage in localStorage (access token)
   - Token retrieval and validation
   - Helper functions: `getAccessToken()`, `setAccessToken()`, `removeTokens()`
3. ✅ Update `web-ui/src/api.js`:
   - Add Axios request interceptor:
     - Attach JWT to Authorization header: `Bearer <token>`
   - Add Axios response interceptor:
     - On 401 error: Try to refresh token
     - If refresh succeeds: Retry original request
     - If refresh fails: Logout and redirect to login
4. ✅ Create `web-ui/src/utils/rolePermissions.js`:
   - Helper functions:
     - `canViewAllSchedules(user)` - ADMIN, EDITOR
     - `canEditData(user)` - ADMIN, EDITOR
     - `canManageUsers(user)` - ADMIN only
     - `canRunSolver(user)` - ADMIN only
     - `canEditOwnAvailability(user)` - TEACHER
     - `hasRole(user, role)` - Check specific role
     - `hasAnyRole(user, roles)` - Check multiple roles

**Deliverables**:
- ✅ Auth context provides global auth state
- ✅ API automatically includes JWT in requests
- ✅ Token refresh on 401 errors
- ✅ Permission checking utilities available
- ✅ Tokens persisted across page refreshes

**Testing**:
- Login - verify tokens stored in localStorage
- Refresh page - verify user state restored
- Make API call - verify JWT attached to request
- Token expires - verify auto-refresh triggered
- Logout - verify tokens removed from localStorage

---

### Phase 6: Frontend - Login & Protected Routes (3-4 hours)

**Objective**: Implement login page and route protection.

**Tasks**:
1. ✅ Install dependencies:
   - `npm install react-hook-form jwt-decode`
2. ✅ Create `web-ui/src/components/auth/Login.jsx`:
   - Username/password form with validation
   - "Remember Me" checkbox (extend token expiry)
   - "Forgot Password?" link
   - Error handling and display
   - Loading state during authentication
   - Redirect to dashboard on success
   - Redirect to intended page after login
3. ✅ Create `web-ui/src/components/auth/ProtectedRoute.jsx`:
   - Check if user is authenticated
   - Redirect to login if not authenticated
   - Check role permissions (optional prop)
   - Show "Access Denied" page if insufficient permissions
   - Store intended destination for post-login redirect
4. ✅ Update `web-ui/src/App.jsx`:
   - Wrap app in `<AuthProvider>`
   - Add login route: `/login`
   - Wrap existing routes in `<ProtectedRoute>`
   - Add role-based route protection:
     - `/users` - ADMIN only
     - `/solver` - ADMIN only
     - `/teachers`, `/courses`, `/rooms`, `/groups`, `/assignments` - ADMIN, EDITOR
     - `/schedule` - All authenticated users
5. ✅ Create `web-ui/src/components/Navbar.jsx` updates:
   - Show user name and role
   - Add logout button
   - Hide/show menu items based on role:
     - "Users" - ADMIN only
     - "Run Solver" - ADMIN only
     - "Teachers", "Courses", "Rooms", "Groups", "Assignments" - ADMIN, EDITOR
     - "Schedule" - All users
     - "My Availability" - TEACHER only

**Deliverables**:
- ✅ Login page functional and styled
- ✅ Protected routes redirect to login
- ✅ Navbar shows user info and logout
- ✅ Menu items filtered by role
- ✅ Post-login redirect to intended page

**Testing**:
- Access protected route while logged out (should redirect to login)
- Login successfully (should redirect to dashboard or intended page)
- Access route without permission (should show "Access Denied")
- Logout (should redirect to login and clear state)
- Verify navbar shows correct menu items for each role

---

### Phase 7: Frontend - Role-Based UI (2-3 hours)

**Objective**: Adapt UI components to show/hide features based on user role.

**Tasks**:
1. ✅ Update all existing components to conditionally render based on role:
   - `Teachers.jsx`:
     - Hide "Add Teacher" button for non-ADMIN/EDITOR
     - Hide "Edit" and "Delete" buttons for non-ADMIN/EDITOR
     - Show "Edit My Availability" button for TEACHER role
   - `Courses.jsx`:
     - Hide "Add Course" button for non-ADMIN/EDITOR
     - Hide "Edit" and "Delete" buttons for non-ADMIN/EDITOR
   - `Rooms.jsx`:
     - Hide "Add Room" button for non-ADMIN/EDITOR
     - Hide "Edit" and "Delete" buttons for non-ADMIN/EDITOR
   - `StudentGroups.jsx`:
     - Hide "Add Group" button for non-ADMIN/EDITOR
     - Hide "Edit" and "Delete" buttons for non-ADMIN/EDITOR
   - `Assignments.jsx`:
     - Hide "Add Assignment" button for non-ADMIN/EDITOR
     - Hide "Edit", "Delete", "Pin/Unpin" buttons for non-ADMIN/EDITOR
   - `Schedule.jsx`:
     - Filter data based on role:
       - ADMIN/EDITOR: Show all schedules
       - TEACHER: Show only own assignments
       - STUDENT: Show only own group's assignments
     - Hide group/teacher filters for STUDENT role
2. ✅ Create `web-ui/src/components/auth/Profile.jsx`:
   - View user information (username, email, full name, role)
   - Change password form
   - For TEACHER role: Link to "Edit My Availability"
   - Success/error messages
3. ✅ Create `web-ui/src/components/auth/ForgotPassword.jsx`:
   - Email input form
   - Send password reset email
   - Success message with instructions
4. ✅ Create `web-ui/src/components/auth/ResetPassword.jsx`:
   - Extract token from URL query parameter
   - New password form with confirmation
   - Password strength indicator
   - Submit and redirect to login on success
5. ✅ Create `web-ui/src/components/teachers/MyAvailability.jsx`:
   - For TEACHER role only
   - Grid showing days (Mon-Fri) and hours (7-15)
   - Toggle availability for each day/hour slot
   - Save changes to backend
   - Visual feedback for available/unavailable slots

**Deliverables**:
- ✅ UI adapts to user role (buttons hidden/shown appropriately)
- ✅ Users only see actions they can perform
- ✅ Profile management working
- ✅ Password reset flow functional
- ✅ Teachers can edit own availability

**Testing**:
- Login as ADMIN - verify all buttons visible
- Login as EDITOR - verify data management buttons visible, user management hidden
- Login as TEACHER - verify only "Edit My Availability" visible
- Login as STUDENT - verify all edit buttons hidden
- Test profile page for all roles
- Test password reset flow end-to-end
- Test teacher availability editing

---

### Phase 8: Frontend - Admin Features (2-3 hours)

**Objective**: Implement admin-only user management and audit log features.

**Tasks**:
1. ✅ Create `web-ui/src/components/admin/UserManagement.jsx`:
   - List all users in a table
   - Columns: Username, Email, Full Name, Role, Status, Last Login, Actions
   - "Add User" button (opens modal)
   - "Edit" button per user (opens modal)
   - "Delete" button per user (with confirmation)
   - "Lock/Unlock" button per user
   - "Reset Password" button per user (generates temporary password)
   - Search/filter by username, email, role
   - Pagination (if many users)
2. ✅ Create `web-ui/src/components/admin/UserForm.jsx`:
   - Modal form for creating/editing users
   - Fields: Username, Email, Full Name, Role, Teacher (if role=TEACHER), Student Group (if role=STUDENT)
   - Password field (only for create)
   - "Must Change Password" checkbox
   - Validation
   - Submit to backend
3. ✅ Create `web-ui/src/components/admin/AuditLog.jsx`:
   - List all audit log entries in a table
   - Columns: Timestamp, User, Action, Entity Type, Entity ID, Details, IP Address, Status
   - Filter by:
     - Date range (from/to)
     - User
     - Action (LOGIN, LOGOUT, CREATE, UPDATE, DELETE)
     - Entity type
     - Success/Failure
   - Export to CSV button
   - Pagination (important for large logs)
   - Real-time updates (optional: WebSocket or polling)
4. ✅ Create `web-ui/src/components/admin/SecurityDashboard.jsx`:
   - Overview cards:
     - Total users by role
     - Active sessions
     - Failed login attempts (last 24h)
     - Locked accounts
   - Recent activity chart (logins over time)
   - Top 10 most active users
   - Recent security events (failed logins, lockouts)
5. ✅ Update navigation:
   - Add "Admin" menu (visible only to ADMIN role):
     - "User Management"
     - "Audit Log"
     - "Security Dashboard"

**Deliverables**:
- ✅ Admin can manage users (CRUD operations)
- ✅ Admin can view audit logs with filtering
- ✅ Admin can export audit logs
- ✅ Security dashboard shows key metrics
- ✅ Admin menu visible only to admins

**Testing**:
- Login as ADMIN - verify admin menu visible
- Create new user - verify user created in database
- Edit user - verify changes saved
- Delete user - verify user removed (with confirmation)
- Lock/unlock account - verify status changes
- Reset user password - verify temporary password generated
- View audit log - verify all events logged
- Filter audit log - verify filtering works
- Export audit log to CSV - verify file downloaded
- View security dashboard - verify metrics accurate

---

### Phase 9: Testing & Security Hardening (2-3 hours)

**Objective**: Comprehensive testing and security hardening.

**Tasks**:
1. ✅ **Functional Testing**:
   - Test all authentication flows (login, logout, refresh, password reset)
   - Test all authorization rules (each role on each endpoint)
   - Test token expiration and refresh
   - Test account lockout after failed attempts
   - Test password complexity validation
   - Test password history (prevent reuse)
   - Test concurrent sessions
   - Test logout from multiple devices
2. ✅ **Security Testing**:
   - Test SQL injection prevention (try malicious inputs)
   - Test XSS prevention (try script injection)
   - Test CSRF protection (verify disabled for JWT)
   - Test rate limiting on login endpoint
   - Test token tampering (modify JWT and try to use)
   - Test expired token handling
   - Test privilege escalation attempts
   - Test session fixation prevention
3. ✅ **Add Rate Limiting**:
   - Install `bucket4j` or similar library
   - Add rate limiting to `/api/auth/login`: 10 requests/minute per IP
   - Add rate limiting to `/api/auth/forgot-password`: 3 requests/hour per IP
   - Return 429 (Too Many Requests) when limit exceeded
4. ✅ **Add Security Headers**:
   - Update `SecurityConfig.java` to add headers:
     - `Content-Security-Policy` (CSP)
     - `X-Frame-Options: DENY`
     - `X-Content-Type-Options: nosniff`
     - `X-XSS-Protection: 1; mode=block`
     - `Strict-Transport-Security` (HSTS) - production only
     - `Referrer-Policy: no-referrer`
5. ✅ **HTTPS Enforcement**:
   - Configure Spring Boot to redirect HTTP to HTTPS (production)
   - Update CORS to allow only HTTPS origins (production)
6. ✅ **Audit Log Verification**:
   - Verify all authentication events logged
   - Verify all authorization failures logged
   - Verify all data modifications logged
   - Verify logs include: user, action, timestamp, IP, user agent
7. ✅ **Performance Testing**:
   - Test login performance (should be < 500ms)
   - Test token refresh performance (should be < 100ms)
   - Test concurrent logins (100 users)
   - Test audit log query performance with large dataset

**Deliverables**:
- ✅ All security features tested and verified
- ✅ No security vulnerabilities found
- ✅ Rate limiting active on auth endpoints
- ✅ Security headers configured
- ✅ HTTPS enforced in production
- ✅ Audit logging comprehensive and performant
- ✅ Production-ready authentication system

**Testing Checklist**:
- [ ] Login with valid credentials → Success
- [ ] Login with invalid credentials → 401 error, failed attempt logged
- [ ] Login after 5 failed attempts → Account locked
- [ ] Locked account auto-unlocks after 30 minutes → Success
- [ ] Access protected endpoint without token → 401 error
- [ ] Access protected endpoint with expired token → 401, auto-refresh triggered
- [ ] Access protected endpoint with invalid token → 401 error
- [ ] Access endpoint without permission → 403 error, logged
- [ ] Refresh token → New access token returned
- [ ] Logout → Tokens revoked, cannot be reused
- [ ] Change password → Old tokens invalidated
- [ ] Reset password → Token sent, password changed, logged
- [ ] Reuse old password → Validation error
- [ ] Weak password → Validation error
- [ ] SQL injection attempt → Prevented, logged
- [ ] XSS attempt → Prevented (React auto-escaping + CSP)
- [ ] Token tampering → Rejected (signature validation)
- [ ] Exceed rate limit → 429 error
- [ ] All events in audit log → Verified

---

### Phase 10: Documentation & Deployment (1-2 hours)

**Objective**: Complete documentation and prepare for deployment.

**Tasks**:
1. ✅ Update `README.md`:
   - Add authentication section
   - Document default admin credentials
   - Document how to create users
   - Document role permissions
2. ✅ Create `AUTH_SETUP.md`:
   - Detailed setup instructions
   - Database migration steps
   - Environment variables needed
   - JWT secret key generation
   - HTTPS certificate setup (production)
   - Troubleshooting guide
3. ✅ Create `USER_GUIDE.md`:
   - Separate guides for each role:
     - Admin Guide - User management, audit logs, security
     - Editor Guide - Data management, schedule viewing
     - Teacher Guide - View schedule, edit availability
     - Student Guide - View schedule, export PDF
   - Screenshots for key features
4. ✅ Create `API_DOCUMENTATION.md`:
   - Document all auth endpoints
   - Request/response examples
   - Error codes and messages
   - Authentication header format
5. ✅ Create database initialization script:
   - `database/init_auth.sh` - Run migration and create default admin
6. ✅ Create default user creation script:
   - `scripts/create_default_users.sql` - Create sample users for testing
7. ✅ Update `application.properties`:
   - Add JWT configuration:
     - `jwt.secret` - Secret key for signing (use environment variable)
     - `jwt.access-token-expiry` - 15 minutes
     - `jwt.refresh-token-expiry` - 7 days
   - Add security configuration:
     - `security.require-https` - true (production)
     - `security.rate-limit.enabled` - true
8. ✅ Create deployment checklist:
   - [ ] Generate strong JWT secret key
   - [ ] Configure HTTPS certificate
   - [ ] Set environment variables
   - [ ] Run database migrations
   - [ ] Create default admin user
   - [ ] Change default admin password
   - [ ] Enable rate limiting
   - [ ] Configure CORS for production domain
   - [ ] Test all authentication flows
   - [ ] Verify audit logging working
   - [ ] Monitor security metrics

**Deliverables**:
- ✅ Complete documentation for setup and usage
- ✅ User guides for all roles
- ✅ API documentation
- ✅ Deployment scripts and checklist
- ✅ Production-ready configuration

---

## Security Hardening Checklist

### ✅ Password Security
- [x] BCrypt hashing with cost factor 12
- [x] Minimum 8 characters, complexity requirements
- [x] Password history (prevent reuse of last 5)
- [x] Secure password reset with time-limited tokens (1 hour)
- [x] Force password change on first login (optional)
- [x] Passwords never logged or exposed in responses

### ✅ Token Security
- [x] JWT with RS256 algorithm (asymmetric signing)
- [x] Short-lived access tokens (15 minutes)
- [x] Long-lived refresh tokens (7 days)
- [x] Refresh token rotation (one-time use)
- [x] Token revocation on logout
- [x] Tokens stored securely (localStorage for access, HttpOnly cookie for refresh)
- [x] JWT secret key from environment variable (not hardcoded)

### ✅ Account Security
- [x] Account lockout after 5 failed attempts
- [x] Lockout duration: 30 minutes
- [x] Session timeout after 30 minutes inactivity
- [x] Force logout on password change
- [x] Multi-device session management
- [x] Failed login attempt tracking

### ✅ API Security
- [x] Rate limiting on auth endpoints (10 req/min for login)
- [x] CORS properly configured (whitelist origins)
- [x] CSRF protection disabled (stateless JWT)
- [x] HTTPS enforcement in production
- [x] Security headers (CSP, X-Frame-Options, HSTS, etc.)
- [x] Input validation and sanitization
- [x] SQL injection prevention (JPA parameterized queries)
- [x] XSS prevention (React auto-escaping + CSP)

### ✅ Audit & Monitoring
- [x] Log all authentication events
- [x] Log all authorization failures
- [x] Log all data modifications
- [x] Include: user, action, timestamp, IP, user agent
- [x] Secure log storage (append-only)
- [x] Alert on suspicious activity

### ✅ Data Protection
- [x] Passwords never logged
- [x] Tokens never logged in plain text
- [x] Personal data encrypted at rest (database encryption)
- [x] Personal data encrypted in transit (HTTPS)
- [x] GDPR compliance (right to access, right to deletion)

---

## Testing Strategy

### Unit Tests

**Backend**:
- `AuthServiceTest.java` - Test authentication logic
- `JwtTokenProviderTest.java` - Test JWT generation/validation
- `PasswordValidatorTest.java` - Test password complexity rules
- `UserRepositoryTest.java` - Test user queries
- `AuditServiceTest.java` - Test audit logging

**Frontend**:
- `AuthContext.test.jsx` - Test auth state management
- `authService.test.js` - Test API calls
- `rolePermissions.test.js` - Test permission checking
- `Login.test.jsx` - Test login component
- `ProtectedRoute.test.jsx` - Test route protection

### Integration Tests

**Backend**:
- `AuthControllerIntegrationTest.java`:
  - Test login flow end-to-end
  - Test token refresh flow
  - Test password reset flow
  - Test account lockout
- `SecurityIntegrationTest.java`:
  - Test role-based access control
  - Test unauthorized access (401, 403)
  - Test CORS configuration

**Frontend**:
- `AuthFlow.test.jsx`:
  - Test login → access protected route → logout
  - Test token expiration → auto-refresh → continue
  - Test access denied → redirect to login

### End-to-End Tests

Use Cypress or Playwright:
- Login as each role and verify UI adapts correctly
- Test complete password reset flow
- Test account lockout and unlock
- Test concurrent sessions
- Test logout from multiple devices

### Security Tests

- **Penetration Testing**:
  - SQL injection attempts
  - XSS attempts
  - CSRF attempts
  - Token tampering
  - Privilege escalation attempts
- **Performance Testing**:
  - Load test login endpoint (1000 concurrent users)
  - Stress test token refresh
  - Test audit log performance with 1M+ records

---

## Deployment Checklist

### Pre-Deployment

- [ ] All tests passing (unit, integration, E2E)
- [ ] Security audit completed
- [ ] Code review completed
- [ ] Documentation complete
- [ ] Database migrations tested

### Environment Setup

- [ ] Generate strong JWT secret key (256-bit minimum)
- [ ] Configure environment variables:
  - `JWT_SECRET` - Secret key for JWT signing
  - `JWT_ACCESS_TOKEN_EXPIRY` - 900 (15 minutes in seconds)
  - `JWT_REFRESH_TOKEN_EXPIRY` - 604800 (7 days in seconds)
  - `DATABASE_URL` - PostgreSQL connection string
  - `FRONTEND_URL` - React app URL for CORS
  - `REQUIRE_HTTPS` - true (production)
- [ ] Configure HTTPS certificate (Let's Encrypt or commercial)
- [ ] Configure reverse proxy (Nginx or Apache)

### Database Setup

- [ ] Run database migrations (`database/auth_schema.sql`)
- [ ] Create default admin user
- [ ] Verify indexes created
- [ ] Verify foreign key constraints
- [ ] Backup database

### Application Deployment

- [ ] Build backend: `mvn clean package`
- [ ] Build frontend: `npm run build`
- [ ] Deploy backend JAR to server
- [ ] Deploy frontend build to web server
- [ ] Configure CORS for production domain
- [ ] Enable rate limiting
- [ ] Configure security headers
- [ ] Test HTTPS redirect

### Post-Deployment

- [ ] Login as default admin
- [ ] Change default admin password
- [ ] Create initial users for each role
- [ ] Test all authentication flows
- [ ] Test all authorization rules
- [ ] Verify audit logging working
- [ ] Monitor security metrics
- [ ] Set up alerts for suspicious activity
- [ ] Schedule regular security audits

### Monitoring

- [ ] Set up application monitoring (e.g., Prometheus, Grafana)
- [ ] Monitor failed login attempts
- [ ] Monitor account lockouts
- [ ] Monitor API response times
- [ ] Monitor database performance
- [ ] Set up log aggregation (e.g., ELK stack)
- [ ] Configure alerts for security events

---

## Dependencies to Add

### Backend (`pom.xml`)

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>

<!-- Password validation -->
<dependency>
    <groupId>org.passay</groupId>
    <artifactId>passay</artifactId>
    <version>1.6.4</version>
</dependency>

<!-- Rate limiting (optional) -->
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.7.0</version>
</dependency>
```

### Frontend (`package.json`)

```json
{
  "dependencies": {
    "react-hook-form": "^7.49.2",
    "jwt-decode": "^4.0.0"
  }
}
```

---

## Timeline and Estimates

| Phase | Duration | Cumulative | Priority |
|-------|----------|------------|----------|
| Phase 1: Database & Entities | 2-3 hours | 3 hours | HIGH |
| Phase 2: Spring Security | 3-4 hours | 7 hours | HIGH |
| Phase 3: Auth API | 2-3 hours | 10 hours | HIGH |
| Phase 4: Authorization | 2-3 hours | 13 hours | HIGH |
| Phase 5: Auth Context | 2-3 hours | 16 hours | HIGH |
| Phase 6: Login & Routes | 3-4 hours | 20 hours | HIGH |
| Phase 7: Role-Based UI | 2-3 hours | 23 hours | MEDIUM |
| Phase 8: Admin Features | 2-3 hours | 26 hours | MEDIUM |
| Phase 9: Testing | 2-3 hours | 29 hours | HIGH |
| Phase 10: Documentation | 1-2 hours | 31 hours | MEDIUM |

**Total Estimated Time**: 30-35 hours (approximately 4-5 working days)

**Minimum Viable Product (MVP)**: Phases 1-6 (20 hours) - Basic auth with login, logout, and role-based access

**Full Production System**: All 10 phases (31 hours) - Complete auth with admin features, audit logging, and comprehensive security

---

## Conclusion

This authentication and authorization system provides:

✅ **Security**: Industry-standard security practices (BCrypt, JWT, rate limiting, audit logging)
✅ **Scalability**: Stateless JWT authentication supports horizontal scaling
✅ **Usability**: Role-based UI adapts to user permissions
✅ **Compliance**: Audit logging supports regulatory requirements
✅ **Maintainability**: Clean architecture with separation of concerns
✅ **Extensibility**: Easy to add new roles or permissions

**Next Steps**:
1. Review and approve this plan
2. Begin Phase 1 implementation
3. Proceed phase-by-phase with testing after each phase
4. Deploy to production after Phase 9 (testing) is complete

**Questions or Modifications?**
- Need to adjust role permissions?
- Want to add additional roles (e.g., COORDINATOR)?
- Need to modify token expiry times?
- Want to add email verification?
- Need to integrate with external auth provider (OAuth, LDAP)?

This plan is flexible and can be adjusted based on your specific requirements.
```

