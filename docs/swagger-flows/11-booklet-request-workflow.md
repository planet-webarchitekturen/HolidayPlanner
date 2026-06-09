# Booklet Request Workflow

## Goal

Create and move a booklet request through its lifecycle.

## Swagger

```text
http://localhost:8088/swagger-ui.html
```

## 1. Create Request

Endpoint:

```http
POST /api/booklet-requests
```

Body:

```json
{
  "organizationId": "11111111-1111-1111-1111-111111111111",
  "requestedCopies": 25,
  "note": "Please print before the parent evening."
}
```

Save:

```text
bookletRequestId = ...
```

Initial status:

```text
REQUESTED
```

## 2. Get Request

Endpoint:

```http
GET /api/booklet-requests/{id}
```

## 3. List Requests For Organization

Endpoint:

```http
GET /api/booklet-requests?organizationId={organizationId}
```

## 4. Summary

Endpoint:

```http
GET /api/booklet-requests/summary?organizationId={organizationId}
```

## 5. Mark Printed

Endpoint:

```http
POST /api/booklet-requests/{id}/mark-printed
```

Status:

```text
PRINTED
```

## 6. Mark Distributed

Endpoint:

```http
POST /api/booklet-requests/{id}/mark-distributed
```

Status:

```text
DISTRIBUTED
```

## 7. Cancel Request

Endpoint:

```http
DELETE /api/booklet-requests/{id}
```

This is only valid while the request is in a cancellable state. If not, expect `409 Conflict`.
