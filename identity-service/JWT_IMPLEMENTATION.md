# JWT Implementation - Identity Service

## Overview
Added JWT-based authentication to the identity service. Users receive a JWT token on login that includes their ID, organization, and roles.

## Changes Made

###  Dependencies
- Added JJWT 0.12.3 (jjwt-api, jjwt-impl, jjwt-jackson) to `identity-service/pom.xml`

###  Core Components
- **JwtTokenProvider**: Generates and validates JWT tokens
- **JwtAuthenticationFilter**: Extracts JWT from Authorization header and sets SecurityContext
- **LoginResponse DTO**: Returns user info + token to client


### Configuration
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
curl -X POST "http://localhost:8083/api/auth/register?email=test@example.com&password=test123&phoneNumber=+43664123456&organizationId=a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
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
