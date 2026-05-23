# Database Migrations

Liquibase (Groovy DSL). Master: `server/src/main/resources/db/changelog/db.changelog-master.groovy`. Changesets: `changes/NNN-<slug>.groovy`. Register each new file with explicit `include file:` entries — no `includeAll`.

Migrations run as the `tco_migrate` role. Production: one-shot `migrate` container completes before backend starts. Local: Spring Boot runs them on startup as a dev shortcut.

## Rules

1. Every `changeSet` has a `rollback {}` block. Empty + comment if a rollback is genuinely impossible (lossy conversion).
2. Never modify a committed changeset (`id` + `author` is the checksum key). New change → new changeset.
3. One changeset per logical change.
4. **No database enum types** — use `VARCHAR` and enforce values in app code. `ALTER TYPE ADD VALUE` can't run in a transaction and values can't be removed.
5. Functions/views/policies that change in place: `runOnChange: true`, own file.
6. Tenant-scoped tables declare RLS in the same changeset as the table. No interim state.

## Tenant-scoped table template

```groovy
changeSet(id: 'NNN-create-foo', author: 'Christian Nau') {
    comment 'Foo — tenant-scoped, RLS-protected.'

    createTable(tableName: 'foo') {
        column(name: 'id', type: 'UUID', defaultValueComputed: 'gen_random_uuid()') {
            constraints(primaryKey: true, nullable: false)
        }
        column(name: 'tenant_id', type: 'UUID') {
            constraints(nullable: false)
        }
        column(name: 'name', type: 'TEXT') {
            constraints(nullable: false)
        }
        column(name: 'created_at', type: 'TIMESTAMPTZ', defaultValueComputed: 'now()') {
            constraints(nullable: false)
        }
        column(name: 'updated_at', type: 'TIMESTAMPTZ', defaultValueComputed: 'now()') {
            constraints(nullable: false)
        }
    }

    createIndex(tableName: 'foo', indexName: 'idx_foo_tenant_id') {
        column(name: 'tenant_id')
    }

    sql '''
        ALTER TABLE foo ENABLE ROW LEVEL SECURITY;
        CREATE POLICY tenant_isolation ON foo
            USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
    '''

    rollback {
        sql '''
            DROP POLICY IF EXISTS tenant_isolation ON foo;
            ALTER TABLE foo DISABLE ROW LEVEL SECURITY;
        '''
        dropTable(tableName: 'foo')
    }
}
```

Notes:

- `current_setting('app.tenant_id', true)` returns `NULL` if the setting isn't set. The cast to `uuid` will fail in that case, which is desired — queries running without a tenant context can't read tenant-scoped data.
- **Set the tenant via `SET LOCAL` inside a transaction**, never plain `SET`. `SET LOCAL` is scoped to the transaction and cleared at commit/rollback; `SET` leaks across the pooled connection and is the classic source of cross-tenant bugs.
- The `tenant_id` index supports query planning under RLS — the optimizer uses it to filter rows before applying the policy in many cases.
- **When adding compound indexes on tenant-scoped tables, lead with `tenant_id`** (e.g. `(tenant_id, created_at DESC)` for a list query). Postgres can sometimes drop the leading `tenant_id` predicate at plan time under RLS, but writing the index in tenant-first order keeps the plan obvious and avoids surprise full scans.

## Rollback notes

Standard pattern: each DDL has an inverse (`DROP TABLE IF EXISTS`, `DROP COLUMN IF EXISTS`, `DROP INDEX IF EXISTS`, `DROP POLICY IF EXISTS`, `REVOKE`). For `ALTER COLUMN TYPE`, include `USING col::<original_type>`.

## Anti-patterns

- Adding `NOT NULL` to an existing column without a default or backfill step in the same changeset.
- Modifying a deployed changeset (renumbering, rewording the comment, changing the rollback).
- Tenant-scoped table shipped without its RLS policy.
- Role/grant statements in changesets — role provisioning lives in `scripts/db-init/` (local) and Cloud SQL IAM (prod).
