-- Local-dev DB role setup. Runs once at first Postgres container start.
--
-- Mirrors the three-role pattern documented in docs/architecture.md § Multi-tenancy:
--   tco_app     — no BYPASSRLS, used by the application for normal request-scoped work
--   tco_admin   — has BYPASSRLS, used only via the explicit withAdminConnection { ... }
--                 marker for tenant provisioning, admin RPCs, system-wide jobs
--   tco_migrate — superuser-equivalent for DDL (Liquibase migrations)
--
-- Production roles are provisioned through Cloud SQL IAM / Secret Manager, not this script.
-- Passwords here are for LOCAL DEV ONLY.

CREATE ROLE tco_app     LOGIN PASSWORD 'tco_app_local_password';
CREATE ROLE tco_admin   LOGIN PASSWORD 'tco_admin_local_password' BYPASSRLS;
CREATE ROLE tco_migrate LOGIN PASSWORD 'tco_migrate_local_password' SUPERUSER;

GRANT CONNECT ON DATABASE tco TO tco_app, tco_admin, tco_migrate;
GRANT USAGE, CREATE ON SCHEMA public TO tco_app, tco_admin, tco_migrate;

-- Default privileges so future migration-created objects are usable by the app role.
-- Run AS tco_migrate so future objects it creates are visible to tco_app by default.
ALTER DEFAULT PRIVILEGES FOR ROLE tco_migrate IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO tco_app, tco_admin;
ALTER DEFAULT PRIVILEGES FOR ROLE tco_migrate IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO tco_app, tco_admin;

-- Extensions used by future migrations (fuzzy text matching for address / name normalization).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
