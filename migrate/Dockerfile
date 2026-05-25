# Liquibase one-shot migration image. Independent of the Spring Boot server:
# this is the only place Liquibase lives.
#
# Build locally (from repo root):
#   docker build -t tc-overwatch-migrate:dev -f migrate/Dockerfile .
#
# To bump the base image: `docker pull liquibase/liquibase:<new-tag>` then copy
# the printed digest into the FROM line below.

FROM liquibase/liquibase:4.32@sha256:fbee3db36feece76da4207e9cca12873efecc057aba074232338bff600c19246

LABEL org.opencontainers.image.source="https://github.com/cnau/tc-overwatch"
LABEL org.opencontainers.image.description="tc-overwatch Liquibase migrations"

# Groovy DSL extension — our changelogs are *.groovy. Not in lpm's catalog,
# so pulled directly from Maven Central with SHA-256 pins (BuildKit verifies
# before COPY). Groovy 3.0.17 matches what liquibase-groovy-dsl 4.0.0 resolves
# transitively. To bump a version: update the URL + checksum together (fetch
# the new checksum from Maven Central's `<jar>.sha256` sibling file).
ADD --chown=liquibase:liquibase \
    --checksum=sha256:881525567caae594701228279857d90953b77bf3f48cf4925f844487dc61d749 \
    https://repo1.maven.org/maven2/org/liquibase/liquibase-groovy-dsl/4.0.0/liquibase-groovy-dsl-4.0.0.jar \
    /liquibase/lib/
ADD --chown=liquibase:liquibase \
    --checksum=sha256:9ed2a565a8592a9f63f410afed892d0ca6bec1ff96277ce4cc7f3e2e366e794f \
    https://repo1.maven.org/maven2/org/codehaus/groovy/groovy/3.0.17/groovy-3.0.17.jar \
    /liquibase/lib/
ADD --chown=liquibase:liquibase \
    --checksum=sha256:b78710a77fb7c7973d613448db99a08d2f880e040e82981900fb645f726bfe31 \
    https://repo1.maven.org/maven2/org/codehaus/groovy/groovy-sql/3.0.17/groovy-sql-3.0.17.jar \
    /liquibase/lib/

COPY --chown=liquibase:liquibase \
    server/src/main/resources/db/changelog \
    /liquibase/changelog

CMD ["--search-path=/liquibase/changelog", \
     "--changelog-file=db.changelog-master.groovy", \
     "--default-schema-name=tco", \
     "--liquibase-schema-name=public", \
     "update"]
