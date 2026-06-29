#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE booking_db;
    CREATE DATABASE event_db;
    CREATE DATABASE identity_db;
    CREATE DATABASE organization_db;
    CREATE DATABASE payment_db;
    CREATE DATABASE notification_db;
    CREATE DATABASE booklet_db;
EOSQL
