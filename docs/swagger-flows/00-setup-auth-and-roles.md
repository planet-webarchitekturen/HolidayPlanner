# Setup, Register, Login, Roles

## Goal

Create a user, log in, copy the JWT token, and set roles for Swagger demos.

## 1. Create Basic Data

If you do not have an organization yet, run:

```bash
docker exec -i holidayplanner-db psql -U postgres < docker/seed-basic-data.sql
```

Use this organization ID:

```text
11111111-1111-1111-1111-111111111111
```

## 2. Register User

Open Identity Swagger:

```text
http://localhost:8083/swagger-ui.html
```

Endpoint:

```http
POST /api/auth/register
```

Query parameters:

```text
email = demo@example.com
password = test123
phoneNumber = +436601234567
organizationId = 11111111-1111-1111-1111-111111111111
```

Save the returned `id` as:

```text
userId = ...
```

## 3. Login

Endpoint:

```http
POST /api/auth/login
```

Body:

```json
{
  "email": "demo@example.com",
  "password": "test123"
}
```

Save:

```text
token = eyJ...
```

## 4. Authorize Swagger

Open the Swagger page of the service you want to test and click `Authorize`.

Use:

```text
Bearer eyJ...
```

If your Swagger setup expects only the raw token, use:

```text
eyJ...
```

## 5. Change Role For Demo

Users are registered as `USER`. Some flows need another role.

In `identity_db`:

```sql
UPDATE users
SET role = 'ADMIN'
WHERE email = 'demo@example.com';
```

Other useful roles:

```sql
UPDATE users SET role = 'USER' WHERE email = 'demo@example.com';
UPDATE users SET role = 'EVENT_OWNER' WHERE email = 'demo@example.com';
UPDATE users SET role = 'ORGANIZATION_TEAM_MEMBER' WHERE email = 'demo@example.com';
UPDATE users SET role = 'ORGANIZATION_OWNER' WHERE email = 'demo@example.com';
UPDATE users SET role = 'ACCOUNTANT' WHERE email = 'demo@example.com';
```

After every role change, log in again. The role is stored inside the JWT token.

## 6. Check Users

In DataGrip, open `identity_db` and run:

```sql
SELECT id, email, organization_id, role
FROM users;
```
