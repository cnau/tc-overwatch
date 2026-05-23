// changelog-001 — accumulates v0 schema changesets. Append new changesets below;
// see docs/claude/liquibase.md for naming + rotation rules.

databaseChangeLog {

    changeSet(id: 'create-ping-log-table', author: 'Christian Nau') {
        comment 'Smoke-test table for the scaffold; not part of the domain model. ' +
                'Intentionally NOT tenant-scoped and has NO RLS policy — proves the ' +
                'persistence layer end-to-end. Real domain tables follow the multi-tenancy ' +
                'pattern in docs/architecture.md § Multi-tenancy.'

        createTable(tableName: 'ping_log') {
            column(name: 'id', type: 'UUID', defaultValueComputed: 'gen_random_uuid()') {
                constraints(primaryKey: true, nullable: false)
            }
            column(name: 'message', type: 'TEXT') {
                constraints(nullable: false)
            }
            column(name: 'received_at', type: 'TIMESTAMPTZ', defaultValueComputed: 'now()') {
                constraints(nullable: false)
            }
        }

        rollback {
            dropTable(tableName: 'ping_log')
        }
    }
}
