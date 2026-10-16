#!/usr/bin/env bash
# Provisions the three roles from docs/architecture.md § Multi-tenancy at first Postgres
# start. Passwords come from /mnt/user/tco/.env and must not contain a single quote —
# they're interpolated into single-quoted SQL literals.

set -euo pipefail

: "${TCO_APP_PASSWORD:?TCO_APP_PASSWORD must be set in .env}"
: "${TCO_ADMIN_PASSWORD:?TCO_ADMIN_PASSWORD must be set in .env}"
: "${TCO_MIGRATE_PASSWORD:?TCO_MIGRATE_PASSWORD must be set in .env}"

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<-SQL
    CREATE ROLE tco_app     LOGIN PASSWORD '${TCO_APP_PASSWORD}';
    CREATE ROLE tco_admin   LOGIN PASSWORD '${TCO_ADMIN_PASSWORD}' BYPASSRLS;
    CREATE ROLE tco_migrate LOGIN PASSWORD '${TCO_MIGRATE_PASSWORD}' SUPERUSER;

    GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO tco_app, tco_admin, tco_migrate;

    CREATE SCHEMA tco AUTHORIZATION tco_migrate;
    GRANT USAGE, CREATE ON SCHEMA tco TO tco_app, tco_admin, tco_migrate;

    GRANT USAGE ON SCHEMA public TO tco_app, tco_admin;
    GRANT USAGE, CREATE ON SCHEMA public TO tco_migrate;

    ALTER ROLE tco_app     SET search_path = tco, public;
    ALTER ROLE tco_admin   SET search_path = tco, public;
    ALTER ROLE tco_migrate SET search_path = tco, public;

    ALTER DEFAULT PRIVILEGES FOR ROLE tco_migrate IN SCHEMA tco
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO tco_app, tco_admin;
    ALTER DEFAULT PRIVILEGES FOR ROLE tco_migrate IN SCHEMA tco
        GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO tco_app, tco_admin;

    CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA public;
SQL
