# API Composition and CQRS Evaluation

## Reasoning / Findings

I consider BookletService a good fit for API Composition. The booklet is created per organization and needs data from multiple services: organization contact data and sponsors from OrganizationService, and events with event terms from EventService. If the caller had to send all this data to BookletService, the caller would become the composer. I prefer BookletService as the composer because it keeps the booklet generation consistent and reusable.

I do not consider NotificationService a good API Composition candidate. It needs rich information for emails, but it reacts to events and sends notifications. For this use case, I would rather use enriched events that already contain the relevant notification facts, such as event name, meeting point, payment information, and recipient email.

I do not consider BookletService or NotificationService strong CQRS candidates. BookletService mainly generates PDFs and does not own meaningful mutable business state. NotificationService mainly sends emails and does not have a complex read model or query workload. Splitting either service into commands and queries would feel artificial.

## Questions

None
