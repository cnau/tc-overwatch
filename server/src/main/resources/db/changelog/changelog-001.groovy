package db.changelog

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

    changeSet(id: 'create-tenant-table', author: 'Christian Nau') {
        comment 'Tenant — control-plane parent of multi-tenancy. No RLS (tenants ARE the tenancy boundary, not subject to it). Audit columns populated by Spring JPA auditing; no FK on created_by/updated_by because tenants exist before their first user.'

        createTable(tableName: 'tenant') {
            column(name: 'id', type: 'UUID', defaultValueComputed: 'gen_random_uuid()') {
                constraints(primaryKey: true, nullable: false)
            }
            column(name: 'created_by', type: 'UUID') {
                constraints(nullable: true)
            }
            column(name: 'created_at', type: 'TIMESTAMPTZ', defaultValueComputed: 'now()') {
                constraints(nullable: false)
            }
            column(name: 'updated_by', type: 'UUID') {
                constraints(nullable: true)
            }
            column(name: 'updated_at', type: 'TIMESTAMPTZ', defaultValueComputed: 'now()') {
                constraints(nullable: false)
            }
        }

        rollback {
            dropTable(tableName: 'tenant')
        }
    }

    changeSet(id: 'create-app-user-table', author: 'Christian Nau') {
        comment 'AppUser — tenant-scoped, RLS-protected. One user per tenant in v0; multi-user-per-tenant is BACKLOG. Audit columns populated by Spring JPA auditing; no FK on created_by/updated_by (a user can be the creator of itself at invitation acceptance).'

        createTable(tableName: 'app_user') {
            column(name: 'id', type: 'UUID', defaultValueComputed: 'gen_random_uuid()') {
                constraints(primaryKey: true, nullable: false)
            }
            column(name: 'created_by', type: 'UUID') {
                constraints(nullable: true)
            }
            column(name: 'created_at', type: 'TIMESTAMPTZ', defaultValueComputed: 'now()') {
                constraints(nullable: false)
            }
            column(name: 'updated_by', type: 'UUID') {
                constraints(nullable: true)
            }
            column(name: 'updated_at', type: 'TIMESTAMPTZ', defaultValueComputed: 'now()') {
                constraints(nullable: false)
            }
            column(name: 'tenant_id', type: 'UUID') {
                constraints(nullable: false, foreignKeyName: 'fk_app_user_tenant', references: 'tenant(id)')
            }
            column(name: 'email', type: 'TEXT') {
                constraints(nullable: false)
            }
        }

        addUniqueConstraint(
            tableName: 'app_user',
            columnNames: 'tenant_id, email',
            constraintName: 'uq_app_user_tenant_email',
        )

        createIndex(tableName: 'app_user', indexName: 'idx_app_user_tenant_id') {
            column(name: 'tenant_id')
        }

        sql "SELECT tco.enable_tenant_isolation('app_user');"

        rollback {
            sql "SELECT tco.disable_tenant_isolation('app_user');"
            dropTable(tableName: 'app_user')
        }
    }

    changeSet(id: 'create-invitation-table', author: 'Christian Nau') {
        comment 'Invitation — control-plane (NOT tenant-scoped, NO RLS). Pre-acceptance: tenant_id and accepted_at are NULL. v0: admin creates by direct DB insert; auth gate matches by email pre-acceptance. Audit columns populated by Spring JPA auditing; created_by keeps its FK to app_user(id).'

        createTable(tableName: 'invitation') {
            column(name: 'id', type: 'UUID', defaultValueComputed: 'gen_random_uuid()') {
                constraints(primaryKey: true, nullable: false)
            }
            column(name: 'created_by', type: 'UUID') {
                constraints(nullable: true, foreignKeyName: 'fk_invitation_created_by', references: 'app_user(id)')
            }
            column(name: 'created_at', type: 'TIMESTAMPTZ', defaultValueComputed: 'now()') {
                constraints(nullable: false)
            }
            column(name: 'updated_by', type: 'UUID') {
                constraints(nullable: true)
            }
            column(name: 'updated_at', type: 'TIMESTAMPTZ', defaultValueComputed: 'now()') {
                constraints(nullable: false)
            }
            column(name: 'email', type: 'TEXT') {
                constraints(nullable: false)
            }
            column(name: 'token', type: 'UUID', defaultValueComputed: 'gen_random_uuid()') {
                constraints(nullable: false)
            }
            column(name: 'expires_at', type: 'TIMESTAMPTZ') {
                constraints(nullable: true)
            }
            column(name: 'accepted_at', type: 'TIMESTAMPTZ') {
                constraints(nullable: true)
            }
            column(name: 'tenant_id', type: 'UUID') {
                constraints(nullable: true, foreignKeyName: 'fk_invitation_tenant', references: 'tenant(id)')
            }
        }

        addUniqueConstraint(
            tableName: 'invitation',
            columnNames: 'token',
            constraintName: 'uq_invitation_token',
        )

        // Partial index — only pending invitations get looked up by email at the auth gate.
        sql 'CREATE INDEX idx_invitation_pending_email ON invitation (email) WHERE accepted_at IS NULL;'

        rollback {
            sql 'DROP INDEX IF EXISTS idx_invitation_pending_email;'
            dropTable(tableName: 'invitation')
        }
    }
}
