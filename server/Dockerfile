# Backend runtime image. Assumes the bootJar is already built — CI runs
# ./gradlew :server:bootJar before docker build, and uses the repo root as the
# build context so this Dockerfile can reach server/build/libs/.
#
# Build locally:
#   ./gradlew :server:bootJar
#   docker build -t tc-overwatch-server:dev -f server/Dockerfile .

FROM eclipse-temurin:23-jre

LABEL org.opencontainers.image.source="https://github.com/cnau/tc-overwatch"
LABEL org.opencontainers.image.description="tc-overwatch backend"

WORKDIR /app

# wget is for the HEALTHCHECK below. --no-install-recommends keeps the image
# lean; the apt lists are removed in the same layer to avoid carrying ~30MB
# of package metadata into the final image.
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --no-create-home app
USER app:app

COPY --chown=app:app server/build/libs/server-*-SNAPSHOT.jar /app/server.jar

EXPOSE 8080

# 127.0.0.1, not localhost — eclipse-temurin's localhost resolves to ::1 first
# (same gotcha as nginx:alpine). Spring Actuator returns 200 only when every
# component is UP, 503 otherwise — --spider's 200-only success matches that.
HEALTHCHECK --interval=10s --timeout=3s --retries=12 --start-period=30s \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/server.jar"]
