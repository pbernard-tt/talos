# Third-party licence review

> **Review status: preliminary, not legal advice.** This inventory was prepared from the repository
> manifests, lockfiles, installed Python package metadata, cached Maven POM metadata, and container
> definitions on 2026-07-12. Licence compatibility is an engineering assessment, not a legal
> conclusion. A qualified software-licensing attorney should review the shipped dependency graph and
> image SBOMs before public distribution.

## Scope and method

The tables list direct language dependencies, direct build/test dependencies, licensing-significant
transitive dependencies, base images, infrastructure images, and tools installed into distributed
worker images. Exact versions come from `apps/web/package-lock.json`, the five `uv.lock` files,
Gradle dependency resolution, and pinned Dockerfile arguments. A version shown as a range or tag was
not locked to an immutable artifact by the repository and therefore requires release-time review.

The npm lock contains 680 package instances and no missing `license` fields. Its reported licence
distribution is: 539 MIT, 67 ISC, 21 BSD-2-Clause, 19 Apache-2.0, 18 BlueOak-1.0.0, 8 BSD-3-Clause,
2 MIT-0, 2 CC0-1.0, and one each of CC-BY-4.0, CC-BY-3.0, 0BSD, and `(MIT OR CC0-1.0)`. Repeated
permissive transitive packages are not reproduced individually below; the unusual terms are listed.
A release process should preserve dependency notices and generate complete CycloneDX or SPDX SBOMs
for every application and final container image.

`Appears compatible` means no incompatibility was apparent for the described use and distribution.
It does not remove notice, attribution, source, patent, or other obligations.

## Java API and Gradle

| Dependency | Version | Licence | Project URL | AGPL distribution assessment | Manual legal review |
|---|---:|---|---|---|---|
| Spring Boot Gradle plugin | 4.1.0 | Apache-2.0 | https://spring.io/projects/spring-boot | Appears compatible | No |
| Spring dependency-management Gradle plugin | 1.1.7 | Apache-2.0 | https://github.com/spring-gradle-plugins/dependency-management-plugin | Appears compatible | No |
| spring-boot-starter-actuator | 4.1.0 | Apache-2.0 | https://spring.io/projects/spring-boot | Appears compatible | No |
| spring-boot-starter-amqp | 4.1.0 | Apache-2.0 | https://spring.io/projects/spring-boot | Appears compatible | No |
| spring-boot-starter-data-jpa | 4.1.0 | Apache-2.0 | https://spring.io/projects/spring-boot | Appears compatible | No |
| spring-boot-starter-data-redis | 4.1.0 | Apache-2.0 | https://spring.io/projects/spring-boot | Appears compatible; this is the Java client stack, not Redis server | No |
| spring-boot-starter-flyway | 4.1.0 | Apache-2.0 | https://spring.io/projects/spring-boot | Appears compatible | No |
| spring-boot-starter-security | 4.1.0 | Apache-2.0 | https://spring.io/projects/spring-boot | Appears compatible | No |
| spring-boot-starter-validation | 4.1.0 | Apache-2.0 | https://spring.io/projects/spring-boot | Appears compatible | No |
| spring-boot-starter-webmvc | 4.1.0 | Apache-2.0 | https://spring.io/projects/spring-boot | Appears compatible | No |
| spring-boot-configuration-processor | 4.1.0 | Apache-2.0 | https://spring.io/projects/spring-boot | Build-time only; appears compatible | No |
| flyway-core | 12.4.0 | Apache-2.0 | https://github.com/flyway/flyway | Appears compatible based on resolved parent POM | No |
| flyway-database-postgresql | 12.4.0 | Apache-2.0 | https://github.com/flyway/flyway | Appears compatible based on resolved parent POM | No |
| PostgreSQL JDBC driver | 42.7.11 | BSD-2-Clause | https://jdbc.postgresql.org/ | Appears compatible | No |
| uuid-creator | 6.1.1 | MIT | https://github.com/f4b6a3/uuid-creator | Appears compatible | No |
| jjwt-api | 0.13.0 | Apache-2.0 | https://github.com/jwtk/jjwt | Appears compatible | No |
| jjwt-impl | 0.13.0 | Apache-2.0 | https://github.com/jwtk/jjwt | Appears compatible | No |
| jjwt-jackson | 0.13.0 | Apache-2.0 | https://github.com/jwtk/jjwt | Appears compatible | No |
| jackson-dataformat-yaml | 3.2.0 | Apache-2.0 | https://github.com/FasterXML/jackson-dataformats-text | Appears compatible | No |
| json-schema-validator | 3.0.6 | Apache-2.0 | https://github.com/networknt/json-schema-validator | Appears compatible | No |
| MinIO Java SDK | 9.0.1 | Apache-2.0 | https://github.com/minio/minio-java | Appears compatible; distinct from the MinIO server image | No |
| springdoc-openapi-starter-webmvc-api | 3.0.3 | Apache-2.0 | https://springdoc.org/ | Appears compatible | No |
| Spring Boot test starters (actuator, AMQP, JPA, Redis, Flyway, Security, Validation, Web MVC) | 4.1.0 | Apache-2.0 | https://spring.io/projects/spring-boot | Test-only; appears compatible | No |
| Testcontainers JUnit Jupiter | 2.0.5 | MIT | https://java.testcontainers.org/ | Test-only; appears compatible | No |
| Testcontainers PostgreSQL | 2.0.5 | MIT | https://java.testcontainers.org/ | Test-only; appears compatible | No |
| Testcontainers RabbitMQ | 2.0.5 | MIT | https://java.testcontainers.org/ | Test-only; appears compatible | No |
| JUnit Platform Launcher | 6.0.3 | EPL-2.0 | https://junit.org/ | Test-only and not shipped in the runtime image; review EPL notice/secondary-licence handling if test artifacts are redistributed | Yes |

The Spring starters bring a larger resolved transitive graph. Their POM notices and the final JAR
inventory must be captured by an automated Gradle licence/SBOM task before a release; the table does
not represent every transitive JAR.

## Angular application and generated SDK

| Dependency | Version | Licence | Project URL | AGPL distribution assessment | Manual legal review |
|---|---:|---|---|---|---|
| @angular/cdk | 22.0.4 | MIT | https://www.npmjs.com/package/@angular/cdk/v/22.0.4 | Appears compatible | No |
| @angular/common | 22.0.6 | MIT | https://www.npmjs.com/package/@angular/common/v/22.0.6 | Appears compatible | No |
| @angular/compiler | 22.0.6 | MIT | https://www.npmjs.com/package/@angular/compiler/v/22.0.6 | Appears compatible | No |
| @angular/core | 22.0.6 | MIT | https://www.npmjs.com/package/@angular/core/v/22.0.6 | Appears compatible | No |
| @angular/forms | 22.0.6 | MIT | https://www.npmjs.com/package/@angular/forms/v/22.0.6 | Appears compatible | No |
| @angular/material | 22.0.4 | MIT | https://www.npmjs.com/package/@angular/material/v/22.0.4 | Appears compatible | No |
| @angular/platform-browser | 22.0.6 | MIT | https://www.npmjs.com/package/@angular/platform-browser/v/22.0.6 | Appears compatible | No |
| @angular/router | 22.0.6 | MIT | https://www.npmjs.com/package/@angular/router/v/22.0.6 | Appears compatible | No |
| rxjs | 7.8.2 | Apache-2.0 | https://www.npmjs.com/package/rxjs/v/7.8.2 | Appears compatible | No |
| tslib | 2.8.1 | 0BSD | https://www.npmjs.com/package/tslib/v/2.8.1 | Appears compatible | No |
| @angular/build | 22.0.6 | MIT | https://www.npmjs.com/package/@angular/build/v/22.0.6 | Build-time; appears compatible | No |
| @angular/cli | 22.0.6 | MIT | https://www.npmjs.com/package/@angular/cli/v/22.0.6 | Build-time; appears compatible | No |
| @angular/compiler-cli | 22.0.6 | MIT | https://www.npmjs.com/package/@angular/compiler-cli/v/22.0.6 | Build-time; appears compatible | No |
| @openapitools/openapi-generator-cli | 2.39.1 | Apache-2.0 | https://www.npmjs.com/package/@openapitools/openapi-generator-cli/v/2.39.1 | Build-time; appears compatible | No |
| OpenAPI Generator | 7.23.0 | Apache-2.0 | https://openapi-generator.tech/ | Generates the Angular SDK; appears compatible | No |
| jsdom | 28.1.0 | MIT | https://www.npmjs.com/package/jsdom/v/28.1.0 | Test-only; appears compatible | No |
| prettier | 3.9.4 | MIT | https://www.npmjs.com/package/prettier/v/3.9.4 | Development-only; appears compatible | No |
| TypeScript | 6.0.3 | Apache-2.0 | https://www.npmjs.com/package/typescript/v/6.0.3 | Build-time; appears compatible | No |
| Vitest | 4.1.10 | MIT | https://www.npmjs.com/package/vitest/v/4.1.10 | Test-only; appears compatible | No |

### npm transitive terms needing attention

| Dependency | Version | Licence | Project URL | AGPL distribution assessment | Manual legal review |
|---|---:|---|---|---|---|
| lru-cache | 11.5.2 | BlueOak-1.0.0 | https://www.npmjs.com/package/lru-cache/v/11.5.2 | Permissive terms, build/dev transitive; less common licence | Yes |
| isexe | 4.0.0 | BlueOak-1.0.0 | https://www.npmjs.com/package/isexe/v/4.0.0 | Permissive terms, build/dev transitive; less common licence | Yes |
| chownr | 3.0.0 | BlueOak-1.0.0 | https://www.npmjs.com/package/chownr/v/3.0.0 | Permissive terms, build/dev transitive; less common licence | Yes |
| glob | 13.0.6 | BlueOak-1.0.0 | https://www.npmjs.com/package/glob/v/13.0.6 | Permissive terms, build/dev transitive; less common licence | Yes |
| minimatch | 10.2.5 | BlueOak-1.0.0 | https://www.npmjs.com/package/minimatch/v/10.2.5 | Permissive terms, build/dev transitive; less common licence | Yes |
| minipass | 7.1.3 | BlueOak-1.0.0 | https://www.npmjs.com/package/minipass/v/7.1.3 | Permissive terms, build/dev transitive; less common licence | Yes |
| minipass-flush | 1.0.7 | BlueOak-1.0.0 | https://www.npmjs.com/package/minipass-flush/v/1.0.7 | Permissive terms, build/dev transitive; less common licence | Yes |
| path-scurry | 2.0.2 | BlueOak-1.0.0 | https://www.npmjs.com/package/path-scurry/v/2.0.2 | Permissive terms, build/dev transitive; less common licence | Yes |
| tar | 7.5.19 | BlueOak-1.0.0 | https://www.npmjs.com/package/tar/v/7.5.19 | Permissive terms, build/dev transitive; less common licence | Yes |
| yallist | 5.0.0 | BlueOak-1.0.0 | https://www.npmjs.com/package/yallist/v/5.0.0 | Permissive terms, build/dev transitive; less common licence | Yes |
| caniuse-lite data | 1.0.30001803 | CC-BY-4.0 | https://www.npmjs.com/package/caniuse-lite/v/1.0.30001803 | Build input; preserve attribution and verify whether data is reproduced in artifacts | Yes |
| spdx-exceptions data | 2.5.0 | CC-BY-3.0 | https://www.npmjs.com/package/spdx-exceptions/v/2.5.0 | Build/dev transitive; attribution review if redistributed | Yes |
| mdn-data | 2.27.1 | CC0-1.0 | https://www.npmjs.com/package/mdn-data/v/2.27.1 | Appears compatible | No |
| spdx-license-ids data | 3.0.23 | CC0-1.0 | https://www.npmjs.com/package/spdx-license-ids/v/3.0.23 | Appears compatible | No |
| type-fest | 0.21.3 | MIT OR CC0-1.0 | https://www.npmjs.com/package/type-fest/v/0.21.3 | Appears compatible under MIT | No |

## Python services and adapter library

Versions are resolved identically wherever a dependency appears unless noted.

| Dependency | Version | Licence | Project URL | AGPL distribution assessment | Manual legal review |
|---|---:|---|---|---|---|
| aio-pika | 10.0.1 | Apache-2.0 | https://pypi.org/project/aio-pika/10.0.1/ | Appears compatible | No |
| httpx | 0.28.1 | BSD-3-Clause | https://pypi.org/project/httpx/0.28.1/ | Appears compatible | No |
| redis (Python client) | 8.0.1 | MIT | https://pypi.org/project/redis/8.0.1/ | Appears compatible; distinct from Redis server | No |
| fastapi | 0.139.0 | MIT | https://pypi.org/project/fastapi/0.139.0/ | Appears compatible | No |
| uvicorn | 0.51.0 | BSD-3-Clause | https://pypi.org/project/uvicorn/0.51.0/ | Appears compatible | No |
| jsonschema | 4.26.0 | MIT | https://pypi.org/project/jsonschema/4.26.0/ | Test-only; appears compatible | No |
| pytest | 9.1.1 | MIT | https://pypi.org/project/pytest/9.1.1/ | Test-only; appears compatible | No |
| pytest-asyncio | 1.4.0 | Apache-2.0 | https://pypi.org/project/pytest-asyncio/1.4.0/ | Test-only; appears compatible | No |
| uv-build | >=0.11.28,<0.12.0 | Apache-2.0 OR MIT | https://github.com/astral-sh/uv | Build backend is range-pinned rather than lock-resolved; terms appear compatible | Yes: lock exact release used for builds |
| aiormq | 7.0.0 | Apache-2.0 | https://pypi.org/project/aiormq/7.0.0/ | Transitive; appears compatible | No |
| anyio | 4.14.1 | MIT | https://pypi.org/project/anyio/4.14.1/ | Transitive; appears compatible | No |
| certifi | 2026.6.17 | MPL-2.0 | https://pypi.org/project/certifi/2026.6.17/ | File-level copyleft certificate bundle; appears distributable with notices | Yes: confirm MPL notice handling |
| httpcore | 1.0.9 | BSD-3-Clause | https://pypi.org/project/httpcore/1.0.9/ | Transitive; appears compatible | No |
| pydantic | 2.13.4 | MIT | https://pypi.org/project/pydantic/2.13.4/ | Transitive; appears compatible | No |
| pydantic-core | 2.46.4 | MIT | https://pypi.org/project/pydantic-core/2.46.4/ | Transitive; appears compatible | No |
| starlette | 1.3.1 | BSD-3-Clause | https://pypi.org/project/starlette/1.3.1/ | Transitive; appears compatible | No |
| uvloop | 0.22.1 | MIT | https://pypi.org/project/uvloop/0.22.1/ | Optional runtime extra; appears compatible | No |
| watchfiles | 1.2.0 | MIT | https://pypi.org/project/watchfiles/1.2.0/ | Optional runtime extra; appears compatible | No |
| websockets | 16.0 / 16.1 | BSD-3-Clause | https://pypi.org/project/websockets/ | Optional runtime extra; lockfiles differ by service | No |
| annotated-types | 0.7.0 | Metadata unclear; upstream reports MIT | https://github.com/annotated-types/annotated-types | Installed `License-Expression` was empty; likely compatible but not verified from packaged metadata | Yes |
| typing-extensions | 4.16.0 | PSF-2.0 | https://pypi.org/project/typing-extensions/4.16.0/ | Transitive; appears compatible | No |
| packaging | 26.2 | Apache-2.0 OR BSD-2-Clause | https://pypi.org/project/packaging/26.2/ | Development transitive; appears compatible | No |
| PyYAML | 6.0.3 | MIT | https://pypi.org/project/PyYAML/6.0.3/ | Optional runtime transitive; appears compatible | No |
| python-dotenv | 1.2.2 | BSD-3-Clause | https://pypi.org/project/python-dotenv/1.2.2/ | Optional runtime transitive; appears compatible | No |
| Remaining locked Python transitives | Versions in `uv.lock` | MIT, BSD-2-Clause, BSD-3-Clause, Apache-2.0, or PSF-2.0 according to installed metadata | Respective PyPI project pages | No incompatible or source-available term detected in metadata | Preserve notices and re-scan release artifacts |

## Images, infrastructure, and bundled tools

| Dependency | Version | Licence | Project URL | AGPL distribution assessment | Manual legal review |
|---|---:|---|---|---|---|
| Redis server image | `redis:7` (floating) | Redis 7.4 is RSALv2 OR SSPLv1 | https://redis.io/legal/licenses/ | Source-available terms are not treated as compatible open-source terms; exact pulled version is unknown | **Yes: release blocker until version/digest and rights are reviewed** |
| MinIO server image | `minio/minio:latest` (floating, dev only) | AGPL-3.0 | https://github.com/minio/minio | AGPL service appears compatible, but `latest` prevents a reproducible assessment | **Yes: pin digest/version and review network/source obligations** |
| PostgreSQL + pgvector image | `pgvector/pgvector:pg17` (floating) | PostgreSQL | https://github.com/pgvector/pgvector | Appears compatible as a separate service | Yes: pin digest and inventory OS packages |
| RabbitMQ management image | `rabbitmq:4.1-management` (floating patch) | MPL-2.0 plus image components | https://www.rabbitmq.com/ | Separate service; appears usable with required notices | Yes: pin digest and inventory image contents |
| Claude Code apt package | unpinned `stable` | Proprietary/commercial terms | https://docs.anthropic.com/en/docs/claude-code/overview | Not covered by an open-source licence; redistribution inside the worker image is not established by this review | **Yes: release blocker; obtain written redistribution rights or change distribution model** |
| OpenCode CLI | 1.17.17 | MIT | https://github.com/anomalyco/opencode | Appears compatible | No |
| OpenAI Codex CLI and code-mode host | 0.144.0 | Apache-2.0 | https://github.com/openai/codex | Appears compatible; preserve notices | No |
| uv image/binaries | 0.11.28 | Apache-2.0 OR MIT | https://github.com/astral-sh/uv | Appears compatible | No |
| Gradle binary distribution | 9.5.1 | Apache-2.0 | https://gradle.org/ | Appears compatible | No |
| Apache Maven from Debian | repository-selected | Apache-2.0 plus dependencies | https://maven.apache.org/ | Appears compatible, version not pinned | Yes: capture worker-image SBOM |
| Node.js base/build runtime | `node:22-alpine` (floating patch) | MIT plus bundled third-party notices | https://github.com/nodejs/docker-node | Appears compatible; image contents vary | Yes: pin digest and capture SBOM/notices |
| nginx runtime image | `nginx:1.27-alpine` (floating patch) | BSD-2-Clause plus bundled components | https://github.com/nginx/docker-nginx | Appears compatible; image contents vary | Yes: pin digest and capture SBOM/notices |
| Python base images | `python:3.12-slim` (floating patch) | PSF-2.0 plus Debian components | https://github.com/docker-library/python | Appears compatible; image contents vary | Yes: pin digest and capture SBOM/notices |
| Eclipse Temurin JDK/JRE images | Java 21 floating tags | GPL-2.0-only WITH Classpath-exception-2.0 plus image components | https://adoptium.net/ | Generally designed for redistribution, but source/notice obligations apply | Yes: pin digest and confirm GPL/Classpath Exception compliance |
| Debian base image | `debian:bookworm-slim` (floating patch) | Many licences | https://hub.docker.com/_/debian | Cannot assess as one licence; image package inventory required | Yes: capture SBOM and corresponding notices/source offers |
| Docker CLI from Debian | repository-selected | Apache-2.0 plus dependencies | https://github.com/docker/cli | Appears compatible, version not pinned | Yes: capture image SBOM |

## Items requiring a human licensing decision

1. Confirm that Vulkan Technologies owns or controls sufficient rights to relicense all existing
   first-party commits from MIT to AGPL/commercial terms. The new CLA governs future accepted
   contributions and does not cure missing rights in historical contributions.
2. Decide whether the generated Angular client, interface contracts, project schema, and adapter
   specification are the complete intended Apache-2.0 SDK boundary.
3. Review or replace the `redis:7` image because Redis 7.4's RSALv2/SSPLv1 terms are source-available
   rather than standard open-source terms.
4. Obtain written confirmation that Vulkan Technologies may redistribute Claude Code in a public
   worker image, or distribute only an installer/integration that requires the operator to obtain it.
5. Pin every image and externally downloaded tool to an immutable version/digest and generate final
   SPDX or CycloneDX SBOMs. Floating tags and OS package repositories make this report non-reproducible.
6. Confirm attribution and notice handling for CC-licensed npm data, MPL-2.0 components, EPL-2.0 test
   components, BlueOak-1.0.0 packages, and all bundled image packages.
7. Replace placeholder legal and security contacts before public release.

No dependency identified in the language manifests declared Commons Clause, Business Source
License, or another custom proprietary/source-available term. Redis server and Claude Code are the
material non-standard exceptions found outside those language manifests. This conclusion must be
re-run against the exact release artifacts.
