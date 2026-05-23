// First migration — creates the ping_log smoke-test table.
//
// NOTE: ping_log is intentionally NOT tenant-scoped and has NO RLS policy.
// It exists only to prove the persistence layer end-to-end during scaffolding.
// Real domain tables (Contact, Transaction, TransactionParticipation, Invitation,
// etc.) will follow the multi-tenancy pattern documented in docs/architecture.md
// § Multi-tenancy: every tenant-scoped table gets a `tenant_id UUID NOT NULL`
// column and an RLS policy `USING (tenant_id = current_setting('app.tenant_id')::uuid)`,
// declared in the same changeset as the table itself.

databaseChangeLog {

    changeSet(id: '001-create-ping-log', author: 'Christian Nau') {
        comment 'Smoke-test table for the scaffold; not part of the domain model.'

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
