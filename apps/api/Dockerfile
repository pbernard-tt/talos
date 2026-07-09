FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN ./gradlew --no-daemon dependencies
COPY src/ src/
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home talos
WORKDIR /app
COPY --from=build /build/build/libs/talos-api-*.jar app.jar
USER talos
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
