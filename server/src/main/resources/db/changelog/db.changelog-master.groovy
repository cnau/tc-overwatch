package db.changelog

databaseChangeLog {
    // Functions/views (runOnChange) first so they exist when later changesets call them.
    include file: 'fn-tenant-isolation.sql', relativeToChangelogFile: true

    include file: 'changelog-001.groovy', relativeToChangelogFile: true
}
