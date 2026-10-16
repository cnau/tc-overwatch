-- Local-dev roles (docs/architecture.md § Multi-tenancy) and the `tco` schema, once at
-- first Postgres start. `public` is left for extensions. Passwords are local-only.

CREATE ROLE tco_app     LOGIN PASSWORD 'tco_app_local_password';
CREATE ROLE tco_admin   LOGIN PASSWORD 'tco_admin_local_password' BYPASSRLS;
CREATE ROLE tco_migrate LOGIN PASSWORD 'tco_migrate_local_password' SUPERUSER;

GRANT CONNECT ON DATABASE tco TO tco_app, tco_admin, tco_migrate;

-- App data lives in `tco`. Owned by tco_migrate (the role that runs migrations in prod).
CREATE SCHEMA tco AUTHORIZATION tco_migrate;
GRANT USAGE, CREATE ON SCHEMA tco TO tco_app, tco_admin, tco_migrate;

-- public stays for Postgres extensions. App + admin get USAGE so they can call
-- extension functions; only migrate can CREATE there (future extension installs).
GRANT USAGE ON SCHEMA public TO tco_app, tco_admin;
GRANT USAGE, CREATE ON SCHEMA public TO tco_migrate;

-- search_path: `tco` first so unqualified names resolve to app tables; `public`
-- second so extension operators / functions remain accessible.
ALTER ROLE tco_app     SET search_path = tco, public;
ALTER ROLE tco_admin   SET search_path = tco, public;
ALTER ROLE tco_migrate SET search_path = tco, public;

-- Default privileges so future migration-created objects in `tco` are usable
-- by the app role. Run AS tco_migrate so future objects it creates inherit these.
ALTER DEFAULT PRIVILEGES FOR ROLE tco_migrate IN SCHEMA tco
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO tco_app, tco_admin;
ALTER DEFAULT PRIVILEGES FOR ROLE tco_migrate IN SCHEMA tco
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO tco_app, tco_admin;

-- Extensions live in `public`. Installed once per database.
CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA public;
