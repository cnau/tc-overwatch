# Database Migrations

Liquibase, two file formats — match the format to the kind of change:

- **Groovy DSL** (`.groovy`) for table manipulations and basic data updates: `createTable`, `addColumn`, `addUniqueConstraint`, `createIndex`, `addForeignKeyConstraint`, INSERT/UPDATE data. Most changesets live here.
- **Liquibase formatted SQL** (`.sql` with `--changeset` headers) for SQL-heavy migrations: functions, procedures, views, triggers, complex expression-based migrations. Native PostgreSQL syntax — no double-escaping through Groovy strings, IDE picks up SQL highlighting, `$$ ... $$` function bodies work without surprise.

Layout:

```
server/src/main/resources/db/changelog/
├── db.changelog-master.groovy        # list of include file: entries, in order
├── fn-<name>.sql                     # function / procedure / view, runOnChange (own file per rule 5)
├── changelog-001.groovy              # accumulates schema-change changesets
├── changelog-002.groovy              # next file once the previous gets large
└── ...
```

**One changelog file accumulates many changesets.** Don't create a new file per migration. Rotate to the next `changelog-NNN.groovy` only when the current one gets unwieldy — soft target ~5k lines. The numbered file suffix is just a monotonic counter; it doesn't describe content.

Register each new changelog file in the master with an explicit `include file:` entry — no `includeAll`. The master file is just an ordered list of includes, one per changelog file.

Migrations run as the `tco_migrate` role. Production: one-shot `migrate` container completes before backend starts. Local: Spring Boot runs them on startup as a dev shortcut.

## Changeset naming

Each `changeSet` has a descriptive `id` (verb-noun, kebab-case), not a counter:

```groovy
changeSet(id: 'create-foo-table',     author: 'Christian Nau') { ... }
changeSet(id: 'add-tenant-id-to-foo', author: 'Christian Nau') { ... }
changeSet(id: 'index-foo-by-name',    author: 'Christian Nau') { ... }
```

A counter is only justified when a single logical step has to be split across multiple changesets — the same operation, chunked. In that case the base name stays the same and a `-NNN` suffix is appended:

```groovy
changeSet(id: 'backfill-contact-tenant-id-001', author: 'Christian Nau') { ... }
changeSet(id: 'backfill-contact-tenant-id-002', author: 'Christian Nau') { ... }
changeSet(id: 'backfill-contact-tenant-id-003', author: 'Christian Nau') { ... }
```

The counter is a suffix, not a prefix, and the prefix is identical across the group — that's what marks them as the same operation. If the names differ, they aren't a counted group: they're independent changesets that should each get their own descriptive name (`add-foo-status-column`, `backfill-foo-status`, `add-foo-status-not-null`), and execution order is carried by file order alone (changesets execute top-to-bottom within a changelog file, files execute in master's `include` order).

`id` + `author` is Liquibase's checksum key — once a changeset is committed and applied, never change either. Pre-ship, while running against a throwaway local DB, you can edit in place (we did this twice during scaffolding).

## Rules

1. Every `changeSet` has a `rollback {}` block. Empty + comment if a rollback is genuinely impossible (lossy conversion).
2. Never modify a committed changeset (`id` + `author` is the checksum key). New change → new changeset.
3. One changeset per logical change.
4. **No database enum types** — use `VARCHAR` and enforce values in app code. `ALTER TYPE ADD VALUE` can't run in a transaction and values can't be removed.
5. Functions/views/policies that change in place: `runOnChange: true`, own `.sql` file (Liquibase formatted SQL — see Layout). When the body contains semicolons inside a `$$ ... $$` block, the changeset header needs `splitStatements:false` or Liquibase's default parser will break the dollar-quoted body.
6. Tenant-scoped tables declare RLS in the same changeset as the table. No interim state.

## Tenant-scoped table template

```groovy
changeSet(id: 'create-foo-table', author: 'Christian Nau') {
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
