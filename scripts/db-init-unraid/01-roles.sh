#!/usr/bin/env bash
# Provision the three application roles documented in docs/architecture.md
# § Multi-tenancy. Runs once at first Postgres container start (Postgres's
# docker-entrypoint executes anything in /docker-entrypoint-initdb.d/ after
# POSTGRES_USER / POSTGRES_DB are created).
#
# Passwords come from the Unraid host's /mnt/user/tco/.env via
# docker-compose env_file. No password may contain a single quote — the SQL
# below interpolates them into single-quoted literals.

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
