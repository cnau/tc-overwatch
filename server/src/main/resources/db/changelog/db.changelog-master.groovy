package db.changelog

databaseChangeLog {
    // Table-independent helper functions (runOnChange) FIRST so the table-creating
    // changesets can call them. Table-dependent functions go AFTER the changelogs
    // that create their referenced tables.
    include file: 'fn-tenant-isolation.sql', relativeToChangelogFile: true

    include file: 'changelog-001.groovy', relativeToChangelogFile: true

    // Functions that reference specific tables (auth lookups depend on tco.app_user).
    include file: 'fn-auth-lookups.sql', relativeToChangelogFile: true
}
