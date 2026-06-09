-- Basic demo data for local Swagger testing.
-- Run this after the services have started once, so Hibernate has created the tables.
--
-- Example:
--   docker exec -i holidayplanner-db psql -U postgres < docker/seed-basic-data.sql

\connect organization_db

INSERT INTO organizations (id, name, bank_account, booking_start_time)
VALUES (
  '11111111-1111-1111-1111-111111111111',
  'Holiday Planner Demo Organization',
  'AT611904300234573201',
  '2026-06-01 08:00:00'
)
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    bank_account = EXCLUDED.bank_account,
    booking_start_time = EXCLUDED.booking_start_time;

INSERT INTO sponsors (id, organization_id, name, amount)
VALUES (
  '22222222-2222-2222-2222-222222222222',
  '11111111-1111-1111-1111-111111111111',
  'Demo Sponsor',
  500.00
)
ON CONFLICT (id) DO UPDATE
SET organization_id = EXCLUDED.organization_id,
    name = EXCLUDED.name,
    amount = EXCLUDED.amount;

\connect event_db

INSERT INTO events (
  id,
  organization_id,
  event_owner_id,
  short_title,
  description,
  picture_url,
  location,
  meeting_point,
  price,
  payment_method,
  minimal_age,
  maximal_age
)
VALUES (
  '33333333-3333-3333-3333-333333333333',
  '11111111-1111-1111-1111-111111111111',
  '44444444-4444-4444-4444-444444444444',
  'Bike Tour',
  'Demo holiday program event for Swagger testing.',
  'https://example.com/bike-tour.jpg',
  'Dornbirn',
  'Main station',
  25.00,
  'BANK_TRANSFER',
  8,
  14
)
ON CONFLICT (id) DO UPDATE
SET organization_id = EXCLUDED.organization_id,
    event_owner_id = EXCLUDED.event_owner_id,
    short_title = EXCLUDED.short_title,
    description = EXCLUDED.description,
    picture_url = EXCLUDED.picture_url,
    location = EXCLUDED.location,
    meeting_point = EXCLUDED.meeting_point,
    price = EXCLUDED.price,
    payment_method = EXCLUDED.payment_method,
    minimal_age = EXCLUDED.minimal_age,
    maximal_age = EXCLUDED.maximal_age;

INSERT INTO event_terms (
  id,
  event_id,
  start_date_time,
  end_date_time,
  min_participants,
  max_participants,
  status
)
VALUES (
  '55555555-5555-5555-5555-555555555555',
  '33333333-3333-3333-3333-333333333333',
  '2026-06-10 09:00:00',
  '2026-06-10 15:00:00',
  1,
  10,
  'ACTIVE'
)
ON CONFLICT (id) DO UPDATE
SET event_id = EXCLUDED.event_id,
    start_date_time = EXCLUDED.start_date_time,
    end_date_time = EXCLUDED.end_date_time,
    min_participants = EXCLUDED.min_participants,
    max_participants = EXCLUDED.max_participants,
    status = EXCLUDED.status;
