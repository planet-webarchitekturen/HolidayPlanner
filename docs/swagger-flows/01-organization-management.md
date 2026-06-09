# Organization, Sponsors, Team Members

## Goal

Create or inspect organizations, add sponsors, and add team members.

## Required Role

For create/update/delete use `ADMIN` or `ORGANIZATION_OWNER`.

For team-member creation use `ADMIN` or `ORGANIZATION_TEAM_MEMBER`.

After changing role in SQL, log in again and authorize Organization Swagger.

```text
http://localhost:8084/swagger-ui.html
```

## 1. List Organizations

Endpoint:

```http
GET /api/organizations
```

Save:

```text
organizationId = ...
```

Seeded fallback:

```text
11111111-1111-1111-1111-111111111111
```

## 2. Create Organization

Endpoint:

```http
POST /api/organizations
```

Query parameters:

```text
name = Summer Club
bankAccount = AT611904300234573201
bookingStartTime = 2026-06-01T08:00:00
```

Save the returned `id`.

## 3. Get Organization Overview

Endpoint:

```http
GET /api/organizations/{organizationId}/overview
```

This composes organization, team members, and sponsors.

## 4. Add Sponsor

Endpoint:

```http
POST /api/organizations/{organizationId}/sponsors
```

Query parameters:

```text
name = Demo Sponsor
amount = 500.00
```

## 5. List Sponsors

Endpoint:

```http
GET /api/organizations/{organizationId}/sponsors
```

## 6. Add Team Member

You need a real `userId` from Identity login/register.

Endpoint:

```http
POST /api/organizations/{organizationId}/team-members
```

Query parameters:

```text
userId = <userId>
firstName = Eva
lastName = Owner
email = demo@example.com
role = TEAM_MEMBER
```

## 7. List Team Members

Endpoint:

```http
GET /api/organizations/{organizationId}/team-members
```
