# JWT Implementation - Identity Service

## Overview
Added JWT-based authentication to the identity service. Users receive a JWT token on login that includes their ID, organization, and roles.

## Changes Made

### 1. Dependencies
- Added JJWT 0.12.3 (jjwt-api, jjwt-impl, jjwt-jackson) to `identity-service/pom.xml`

### 2. Core Components
- **JwtTokenProvider**: Generates and validates JWT tokens
- **JwtAuthenticationFilter**: Extracts JWT from Authorization header and sets SecurityContext
- **LoginResponse DTO**: Returns user info + token to client

### 3. Endpoints
| Method | Path | Auth Required | Returns |
|--------|------|---------------|---------|
| POST | `/api/auth/register` | No | UserResponse |
| POST | `/api/auth/login` | No | LoginResponse (with token) |
| Other | `/api/identity/**` | Yes | — |

### 4. Configuration
- **Public endpoints**: `/api/identity/health`, `/api/auth/register`, `/api/auth/login`
- **Session policy**: STATELESS
- **JWT secret**: Configurable via `jwt.secret` property (app.yml)
- **Expiration**: Configurable via `jwt.expiration` property (default: 1 hour)

## JWT Token Payload

```json
{
  "sub": "<user-uuid>",
  "organizationId": "<org-uuid>",
  "roles": ["USER|ADMIN|EVENT_OWNER|ACCOUNTANT|ORGANIZATION_TEAM_MEMBER"],
  "iat": <issued-at-timestamp>,
  "exp": <expiration-timestamp>
}
```

## Usage

**Login Request:**
```bash
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```

**Login Response:**
```json
{
  "id": "<user-uuid>",
  "email": "user@example.com",
  "phoneNumber": "+1234567890",
  "organizationId": "<org-uuid>",
  "role": "USER",
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer"
}
```

**Authenticated Request:**
```bash
GET /api/identity/users/{userId}
Authorization: Bearer <token>
```
