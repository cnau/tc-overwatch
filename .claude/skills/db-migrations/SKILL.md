---
name: db-migrations
description: Use when writing or changing a Liquibase migration under server/src/main/resources/db/changelog/ — creating or altering a table, adding a column or index or constraint, backfilling data, writing a Postgres function/view/trigger, or wiring row-level security on a new tenant-scoped table. Also covers changeset naming, rollback requirements, and the RLS gotchas that produce confusing runtime errors.
---

# Database migrations

Migrations run as the `tco_migrate` role from the **dedicated `migrate` Docker image**
(`migrate/Dockerfile`), which bakes the changelogs in at build time — so the deployed changelog
version is locked to the image SHA. Liquibase is deliberately not on the server's classpath.
Apply locally with:

```
docker compose -f docker-compose.local.yml run --rm migrate
```

Deploy ordering (postgres healthy → migrate completes → backend starts) is in
`docs/architecture.md` § CI/CD.

## Which file format

- **Groovy DSL (`.groovy`)** for table manipulation and basic data changes: `createTable`,
  `addColumn`, `addUniqueConstraint`, `createIndex`, `addForeignKeyConstraint`, INSERT/UPDATE.
  Most changesets.
- **Liquibase formatted SQL (`.sql` with `--changeset` headers)** for functions, procedures,
  views, triggers, and complex expression-based migrations. Native Postgres syntax means no
  double-escaping through Groovy strings, working IDE highlighting, and `$$ … $$` bodies that
  don't surprise you.

**One changelog file accumulates many changesets** — do not create a file per migration. Rotate
to the next `changelog-NNN.groovy` only when the current one gets unwieldy (soft target ~5k
lines). The number is a monotonic counter, not a description.

Register every new changelog file in `db.changelog-master.groovy` with an explicit
`include file:` entry. **No `includeAll`** — the master is an ordered list, and order is the
contract.

## Changeset naming

Descriptive verb-noun, kebab-case `id` — not a counter:

```groovy
changeSet(id: 'create-foo-table',     author: 'Christian Nau') { … }
changeSet(id: 'add-tenant-id-to-foo', author: 'Christian Nau') { … }
changeSet(id: 'index-foo-by-name',    author: 'Christian Nau') { … }
```

A counter is justified **only** when one logical step is chunked across several changesets — same
operation, split. Then the base name is identical and a `-NNN` suffix is appended
(`backfill-contact-tenant-id-001`, `-002`, `-003`). The identical prefix is what marks them as a
group. If the names differ they are *not* a counted group: they're independent changesets that
each deserve a descriptive name (`add-foo-status-column`, `backfill-foo-status`,
`add-foo-status-not-null`), and execution order is carried by file order alone — changesets run
top-to-bottom within a file, files run in the master's `include` order.

**`id` + `author` is Liquibase's checksum key. Once a changeset is committed and applied, never
change either** — not the id, not the author, not the comment, not the rollback. New change → new
changeset. (Pre-ship, against a throwaway local DB, editing in place is fine; that happened
twice during scaffolding.)

## Rules

1. Every `changeSet` has a `rollback {}` block. Empty plus a comment if rollback is genuinely
   impossible (lossy conversion). Standard inverses: `DROP TABLE IF EXISTS`,
   `DROP COLUMN IF EXISTS`, `DROP INDEX IF EXISTS`, `DROP POLICY IF EXISTS`, `REVOKE`. For
   `ALTER COLUMN TYPE`, include `USING col::<original_type>`.
2. One changeset per logical change.
3. **No database enum types.** Use `VARCHAR` and enforce values in app code — `ALTER TYPE ADD
   VALUE` can't run inside a transaction, and values can never be removed.
4. Functions, views, and policies that change in place get `runOnChange: true` and their own
   `.sql` file. **When the body contains semicolons inside a `$$ … $$` block, the changeset
   header needs `splitStatements:false`** or Liquibase's default parser splits the
   dollar-quoted body and the migration fails confusingly.
5. **A tenant-scoped table declares its RLS in the same changeset that creates it.** No interim
   state where the table exists unprotected.
6. **No role or grant statements in changesets.** Role provisioning is `scripts/db-init/` locally
   and Cloud SQL IAM in prod.
7. Never add `NOT NULL` to an existing column without a default or a backfill step in the same
   changeset.

## Tenant-scoped table template

`changelog-001.groovy` has live examples (`tenant`, `app_user`, `invitation`). The shape:

```groovy
changeSet(id: 'create-foo-table', author: 'Christian Nau') {
    comment 'Foo — tenant-scoped, RLS-protected.'

    createTable(tableName: 'foo') {
        column(name: 'id', type: 'UUID', defaultValueComputed: 'gen_random_uuid()') {
            constraints(primaryKey: true, nullable: false)
        }
        column(name: 'tenant_id', type: 'UUID') { constraints(nullable: false) }
        column(name: 'name', type: 'TEXT')      { constraints(nullable: false) }
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

    sql "SELECT tco.enable_tenant_isolation('foo');"

    rollback {
        sql "SELECT tco.disable_tenant_isolation('foo');"
        dropTable(tableName: 'foo')
    }
}
```

The helpers in `fn-tenant-isolation.sql` expand to:

```sql
ALTER TABLE foo ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON foo
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

## RLS gotchas — the expensive ones

- **`NULLIF(…, '')` is load-bearing.** `current_setting('app.tenant_id', true)` returns `''`, not
  `NULL`, when the setting is unset. Casting `''` to `uuid` throws
  `invalid input syntax for type uuid` — and that error surfaces from the *first* query a
  connection runs without a tenant context, e.g. the auth gate's `findByEmail` lookup before any
  tenant exists. `NULLIF` turns empty into NULL, the predicate becomes UNKNOWN, the row is
  filtered. Quiet and correct. Never drop it.
- **Set the tenant with `SET LOCAL` inside a transaction, never plain `SET`.** `SET LOCAL` is
  scoped to the transaction and cleared at commit or rollback; plain `SET` leaks across a pooled
  connection and is the classic source of cross-tenant bugs.
- **The `tenant_id` index is not optional decoration** — the planner uses it to filter rows before
  applying the policy in many plans.
- **Compound indexes on tenant-scoped tables lead with `tenant_id`** — e.g.
  `(tenant_id, created_at DESC)` for a list query. Postgres can sometimes drop the leading
  `tenant_id` predicate at plan time under RLS; tenant-first ordering keeps the plan obvious and
  avoids surprise full scans.
