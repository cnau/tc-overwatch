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

RUN groupadd --system app && useradd --system --gid app --no-create-home app
USER app:app

COPY --chown=app:app server/build/libs/server-*-SNAPSHOT.jar /app/server.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/server.jar"]
