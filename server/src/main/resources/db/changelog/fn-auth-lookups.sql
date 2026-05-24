--liquibase formatted sql

-- Auth-specific cross-tenant lookups. These reference specific tables (unlike
-- tco.enable_tenant_isolation which takes a regclass parameter), so this file
-- must be included AFTER the table-creating changelog in db.changelog-master.

--changeset Christian Nau:fn-find-app-user-by-email runOnChange:true splitStatements:false
--comment: tco.find_app_user_by_email(text) — SECURITY DEFINER cross-tenant lookup for the sign-in gate.

-- At sign-in we don't yet know the user's tenant, so we can't set app.tenant_id,
-- so RLS hides every app_user row. SECURITY DEFINER runs as the function owner
-- (tco_migrate, the table owner) which bypasses RLS. search_path is locked down
-- — standard defense for DEFINER fns.
CREATE OR REPLACE FUNCTION tco.find_app_user_by_email(p_email text)
RETURNS SETOF tco.app_user
LANGUAGE sql
SECURITY DEFINER
SET search_path = tco, pg_temp
AS $$
    SELECT * FROM tco.app_user WHERE email = p_email LIMIT 1;
$$;

REVOKE EXECUTE ON FUNCTION tco.find_app_user_by_email(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION tco.find_app_user_by_email(text) TO tco_app, tco_admin;

--rollback DROP FUNCTION IF EXISTS tco.find_app_user_by_email(text);
