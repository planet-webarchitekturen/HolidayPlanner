# CQRS and Composition in `organization-service`

## Reasoning

For `organization-service`, a useful composition query is an organization overview. The service already owns organizations, team members, and sponsors, but some user-related information belongs to `identity-service`. Because of that, it makes sense to combine both sources in one read operation and return one enriched response.

CQRS is also useful here because writes and reads have different goals. Write operations create or update organizations, team members, and sponsors. Read operations are better suited for aggregated and enriched views such as an overview for the frontend. Splitting commands and queries keeps these responsibilities separate.

## Findings

CQRS was implemented by separating the logic into:

- `OrganizationCommandService` for write operations
- `OrganizationQueryService` for read operations

The composition query that was added is:

- `GET /api/organizations/{organizationId}/overview`

This endpoint combines:

- local data from `organization-service`
  - organization information
  - team members
  - sponsors
- external data from `identity-service`
  - phone number
  - user role

This gives the client one enriched response instead of multiple separate calls.

## Questions / Open Points

- The composition query currently calls `identity-service` for each team member. If an organization has many team members, this may become slower.
- `organization-service` stores team-member data, while `identity-service` stores user account data. It should be clarified which fields are the main source of truth.
- This solution uses code-level CQRS with separate command and query services. A stricter CQRS approach could use a separate read model or read database.
