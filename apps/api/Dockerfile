# Build context must be the repository root, e.g.:
#   docker build -f apps/api/Dockerfile -t talos-api .
# (Needed because build.gradle.kts pulls packages/project-config-schema/talos.schema.json
# onto the classpath from its canonical location — see Section 14.)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY apps/api/gradle/ apps/api/gradle/
COPY apps/api/gradlew apps/api/build.gradle.kts apps/api/settings.gradle.kts apps/api/
COPY packages/project-config-schema/ packages/project-config-schema/
WORKDIR /build/apps/api
RUN ./gradlew --no-daemon dependencies
COPY apps/api/src/ /build/apps/api/src/
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home talos
WORKDIR /app
COPY --from=build /build/apps/api/build/libs/talos-api-*.jar app.jar
USER talos
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
