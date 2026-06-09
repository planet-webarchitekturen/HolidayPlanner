# Booklet PDF Generation

## Goal

Generate booklet PDFs and participant-list PDFs.

## Required Role

Use `ADMIN`, `ORGANIZATION_OWNER`, or for participant list also `ORGANIZATION_TEAM_MEMBER`.

Authorize Booklet Swagger:

```text
http://localhost:8087/swagger-ui.html
```

## 1. Generate Composed Organization Booklet

Endpoint:

```http
GET /api/booklets/organizations/{organizationId}
```

This calls upstream Organization/Event services and returns a PDF.

If it fails with `502`, check whether Organization Service and Event Service are running.

## 2. Generate Manual Organization Booklet

Endpoint:

```http
POST /api/booklets/organization
```

Query parameters:

```text
organizationName = Holiday Planner Demo Organization
contactInfo = office@example.com
```

Body:

```json
{
  "eventSummaries": [
    "Bike Tour - Dornbirn - 2026-06-10",
    "Climbing Day - Feldkirch - 2026-06-12"
  ],
  "sponsorNames": [
    "Demo Sponsor"
  ]
}
```

Response is a PDF download.

## 3. Generate Participant List

Endpoint:

```http
POST /api/booklets/participant-list
```

Query parameters:

```text
eventName = Bike Tour
termDate = 2026-06-10T09:00
```

Body:

```json
[
  "Max Muster",
  "Lena Beispiel"
]
```

Response is a PDF download.
