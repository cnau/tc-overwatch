--liquibase formatted sql

-- Helper functions for the project-standard tenant_isolation RLS policy. Called from
-- every tenant-scoped table's create / drop changesets so the policy text lives in
-- exactly one place. Per docs/claude/liquibase.md: SQL-heavy changesets (functions,
-- procedures, views, triggers) use Liquibase formatted SQL — Groovy DSL is for table
-- manipulations and basic updates.

--changeset Christian Nau:fn-tenant-isolation runOnChange:true splitStatements:false
--comment: enable_tenant_isolation(regclass) + disable_tenant_isolation(regclass) — apply / remove the tenant_isolation RLS policy on a tenant-scoped table.

CREATE OR REPLACE FUNCTION tco.enable_tenant_isolation(target_table regclass)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', target_table);
    EXECUTE format(
        'CREATE POLICY tenant_isolation ON %s USING (tenant_id = current_setting(''app.tenant_id'', true)::uuid)',
        target_table
    );
END;
$$;

CREATE OR REPLACE FUNCTION tco.disable_tenant_isolation(target_table regclass)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %s', target_table);
    EXECUTE format('ALTER TABLE %s DISABLE ROW LEVEL SECURITY', target_table);
END;
$$;

--rollback DROP FUNCTION IF EXISTS tco.enable_tenant_isolation(regclass);
--rollback DROP FUNCTION IF EXISTS tco.disable_tenant_isolation(regclass);
