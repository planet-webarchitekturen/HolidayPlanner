# CQRS and Composition Queries — organization-service

## Section 1: Composition Queries

### What is a Composition Query?

A composition query combines data from multiple services into one response so the frontend does not have to orchestrate several API calls on its own.

### Candidate Chosen

**Candidate — Organization overview**

`GET /api/organizations/{organizationId}/overview`

This query is useful for an organization dashboard. The organization-service already owns the organization itself, the sponsor list, and the team-member assignments. But the authoritative user account data for each team member lives in identity-service. Without composition, the frontend would have to:

1. Call organization-service for the organization and team members.
2. Call identity-service once per `userId` to fetch phone number and user role.
3. Merge everything in the UI.

With composition, organization-service performs the join internally and returns one overview response.

| Field | Source |
|---|---|
| `organization`, `sponsors`, `teamMemberCount`, `sponsorCount`, `totalSponsorAmount` | organization-service DB |
| `teamMembers[].phoneNumber`, `teamMembers[].userRole` | identity-service `GET /api/identity/users/{userId}` |

### Failure Handling

The composition query degrades gracefully. If identity-service is unavailable for one specific team member, organization-service still returns the organization, sponsors, and local team-member data. In that case:

- `identityDataAvailable` is `false`
- `phoneNumber` and `userRole` stay `null`
- the rest of the overview still works

That makes the dashboard usable even if cross-service enrichment temporarily fails.

### Why This Is a Good Candidate

- The frontend wants a dashboard-style read model, not raw normalized data.
- Team-member identity details belong to another bounded context.
- Reads are a better place for composition than writes because they can tolerate graceful degradation.

---

## Section 2: CQRS

### Where CQRS Helps in organization-service

organization-service has a natural split between write operations and read operations:

| Operation | Type | Notes |
|---|---|---|
| `createOrganization` | Command | creates aggregate root |
| `updateOrganization` | Command | changes organization settings |
| `addTeamMember` | Command | writes assignment data |
| `removeTeamMember` | Command | removes assignment |
| `addSponsor` | Command | writes sponsor data |
| `removeSponsor` | Command | removes sponsor data |
| `getOrganization` | Query | read-only |
| `getAllOrganizations` | Query | read-only |
| `getTeamMembers` | Query | read-only |
| `getSponsors` | Query | read-only |
| `getOrganizationOverview` | Query | read-only + composition |

### Why the Split Is Beneficial

- Commands stay focused on validation and persistence.
- Queries can evolve into dashboard-oriented read models without polluting write logic.
- Composition concerns stay on the read side, where partial results are acceptable.
- Future optimizations such as caching identity lookups or introducing a dedicated read projection can happen without rewriting command flows.

### How It Was Implemented

- `OrganizationCommandService` contains all write operations.
- `OrganizationQueryService` contains all read operations.
- `IdentityServiceClient` is used only by the query side.
- `OrganizationController` routes `GET` endpoints to the query service and write endpoints to the command service.

This is application-level CQRS. Reads and writes still use the same PostgreSQL database, but their responsibilities are separated in code.

---

## Section 3: Findings and Open Questions

1. **Potential N+1 problem**
   The overview query currently calls identity-service once per team member. If an organization has many members, this can become slow. A batch endpoint in identity-service or short-lived caching would improve this.

2. **Duplicate team-member profile fields**
   `TeamMember` currently stores `firstName`, `lastName`, and `email`, while identity-service also owns user account data. We should clarify whether the organization-service copy is a snapshot for convenience or whether identity-service should be the sole source of truth for more fields.

3. **Should commands validate user existence?**
   `addTeamMember` currently accepts a `userId` and stores it directly. For stricter consistency, the command side could call identity-service to verify that the referenced user exists before persisting the team-member assignment.

4. **Is code-level CQRS enough for the assignment?**
   For this project, splitting command and query services is likely sufficient. A stricter CQRS version would add a separate read model or projection database for organization dashboards.

---

## Section 4: Kafka Integration

To align organization-service with the rest of the project, Kafka was added in the same implementation style as booking-service, event-service, and payment-service.

### Produced Event

**Topic:** `holiday-planner.organization.created`

Published by `OrganizationCommandService` after a new organization is persisted.

Payload:
- `organizationId`
- `name`
- `bankAccount`
- `bookingStartTime`

### Consumed Event

**Topic:** `holiday-planner.identity.user-registered`

Published by identity-service after a user account is registered. organization-service consumes this event and checks whether the referenced `organizationId` exists locally.

If the organization does not exist, a warning is logged. This is a lightweight but meaningful consumer: it validates cross-service references and provides a place for future onboarding or synchronization logic.
