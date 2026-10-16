package db.changelog

databaseChangeLog {
    // Table-independent functions first so changesets can call them; functions that
    // reference tables go after the changelog that creates those tables.
    include file: 'fn-tenant-isolation.sql', relativeToChangelogFile: true

    include file: 'changelog-001.groovy', relativeToChangelogFile: true

    // Functions that reference specific tables (auth lookups depend on tco.app_user).
    include file: 'fn-auth-lookups.sql', relativeToChangelogFile: true
}
