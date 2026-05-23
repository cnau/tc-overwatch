# Database Migrations

Liquibase (Groovy DSL) governs all schema changes. Imported by `server/CLAUDE.md`.

## Location

- Master changelog: `server/src/main/resources/db/changelog/db.changelog-master.groovy`
- Individual changesets: `server/src/main/resources/db/changelog/changes/NNN-<slug>.groovy`
- Register each new changeset in the master file with an explicit `include file: '...', relativeToChangelogFile: true` entry. **No `includeAll` directories.** Explicit registration makes ordering reviewable.

## Who runs migrations

Per `docs/architecture.md` § Multi-tenancy, migrations run as the `tco_migrate` Postgres role — never the app role. In production they execute via a one-shot `migrate` container that completes successfully *before* the backend container starts; never on app startup. Local dev currently runs Liquibase via Spring Boot at startup against the same role for convenience — that's a local-only shortcut, not a production pattern.

## Rules

1. **Always include a `rollback {}` block** in every `changeSet`. Required even for DDL Postgres can reverse automatically — Liquibase tracks rollback as part of the changeset. If a rollback is genuinely impossible (lossy data conversion), include an empty `rollback {}` with a comment explaining why.
2. **Never modify a previously committed changeset.** Liquibase keys changesets by `id` + `author`; altering a deployed changeset causes checksum failures on every later run. New change → new changeset.
3. **One changeset per logical change.** Don't bundle a table create with an unrelated index on another table. Bundling makes rollback brittle.
4. **No database enum types.** Use `VARCHAR` columns. Enumeration values are enforced in application code (Kotlin enum + `AttributeConverter` if needed). Postgres enum types are painful to evolve — `ALTER TYPE ADD VALUE` can't run inside a transaction, and values can't be removed.
5. **Functions, views, and policies that legitimately change in place** use `runOnChange: true` and live in their own files so a definition update is one diff, not a new changeset per revision.
6. **Tenant-scoped tables declare RLS in the same changeset as the table.** Don't ship a tenant-scoped table without its policy — there's no safe interim state.

## Tenant-scoped table template

```groovy
changeSet(id: 'NNN-create-foo', author: 'tco') {
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
- The `tenant_id` index supports query planning under RLS — the optimizer uses it to filter rows before applying the policy in many cases.

## Rollback patterns

- `CREATE TABLE` → `DROP TABLE IF EXISTS`
- `ALTER TABLE ADD COLUMN` → `ALTER TABLE DROP COLUMN IF EXISTS`
- `ALTER COLUMN TYPE X` → `ALTER COLUMN TYPE <original> USING col::<original>`
- `CREATE INDEX` → `DROP INDEX IF EXISTS`
- `CREATE POLICY` → `DROP POLICY IF EXISTS` + `ALTER TABLE ... DISABLE ROW LEVEL SECURITY` if appropriate
- `GRANT ...` → `REVOKE ...`

## Validation

```
./gradlew :server:bootRun --args='--spring.profiles.active=local'
```

Local dev runs migrations on startup; failures surface immediately. A dedicated offline-validation task is `[BACKLOG]` — add it when a CI pipeline needs to validate migrations without a live DB.

## Anti-patterns to avoid

- `dropAll`, `executeSql` for destructive operations without a tested rollback.
- Adding `NOT NULL` to an existing column without a default or a backfill step in the same changeset.
- Modifying a deployed changeset (re-numbering, re-wording the comment, changing the rollback) — create a new changeset instead.
- Tenant-scoped tables shipped without an RLS policy in the same changeset.
- Hand-coded role/grant statements in changesets — role provisioning lives in `scripts/db-init/` for local and in Cloud SQL IAM for production.
