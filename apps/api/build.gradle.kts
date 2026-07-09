plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.talos"
version = "0.0.1-SNAPSHOT"
description = "Talos control API"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

// talos.schema.json's canonical location is packages/project-config-schema (Section 14) — pulled
// onto the classpath at build time rather than duplicating the file into src/main/resources.
// Copied into a generated-resources dir (rather than adding packages/project-config-schema
// directly as a srcDir with include()) because SourceDirectorySet.include() filters the whole
// resources set, not just the one srcDir it's declared next to — that silently dropped
// application.yml and db/migration/*.sql the first time this was tried.
val copyTalosSchema by tasks.registering(Copy::class) {
	from("../../packages/project-config-schema/talos.schema.json")
	into(layout.buildDirectory.dir("generated-resources/talos-schema"))
}

sourceSets {
	main {
		resources {
			srcDir(copyTalosSchema.map { it.destinationDir })
		}
	}
}

tasks.named("processResources") {
	dependsOn(copyTalosSchema)
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-amqp")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	// UUID v7 (application-generated primary keys, Section 9.1)
	implementation("com.github.f4b6a3:uuid-creator:6.1.1")

	// JWT signing/parsing (Section 12.2) — not covered by any Spring Boot starter
	implementation("io.jsonwebtoken:jjwt-api:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

	// talos.yaml parsing + validation against packages/project-config-schema/talos.schema.json (Section 14)
	implementation("tools.jackson.dataformat:jackson-dataformat-yaml:3.2.0")
	implementation("com.networknt:json-schema-validator:3.0.6")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-amqp-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	// spring-boot-dependencies doesn't manage bare Testcontainers versions; pinned to the
	// release spring-boot-testcontainers:4.1.0 itself was built against. Testcontainers 2.x
	// renamed its module artifacts with a "testcontainers-" prefix (was "junit-jupiter"/"postgresql").
	testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
	testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<Jar>("jar") {
	enabled = false
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-Xlint:deprecation")
}
