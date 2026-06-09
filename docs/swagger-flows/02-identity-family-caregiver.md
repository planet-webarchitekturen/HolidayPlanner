# Identity, Family Members, Caregivers

## Goal

Create data needed for bookings and event term caregiver assignment.

## Swagger

```text
http://localhost:8083/swagger-ui.html
```

Authorize with the login token.

## 1. Create Family Member

Endpoint:

```http
POST /api/identity/users/{userId}/family-members
```

Use the `userId` from login/register.

Query parameters:

```text
firstName = Max
lastName = Muster
birthDate = 2015-05-20
zip = 6850
```

Save:

```text
familyMemberId = ...
```

## 2. List Family Members

Endpoint:

```http
GET /api/identity/users/{userId}/family-members
```

## 3. Create Caregiver

Endpoint:

```http
POST /api/identity/caregivers
```

Query parameters:

```text
firstName = Anna
lastName = Betreuer
email = caregiver@example.com
phoneNumber = +436601112223
```

Save:

```text
caregiverId = ...
```

## 4. List Caregivers

Endpoint:

```http
GET /api/identity/caregivers
```

## 5. Check Owner Email For Family Member

Endpoint:

```http
GET /api/identity/family-members/{memberId}/owner-email
```

This endpoint is used by Booking Service when it creates booking/payment events.
