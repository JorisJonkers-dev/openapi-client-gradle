# openapi-client-gradle

Gradle plugin for generating typed JVM clients from local OpenAPI specs owned by
the consuming repository.

## What It Is

`openapi-client-gradle` provides the `dev.jorisjonkers.openapi-client` Gradle
plugin. It wraps OpenAPI Generator defaults used by JorisJonkers-dev JVM
services, adds local-spec validation, and exposes helper tasks for external spec
download, normalization, filtering, provenance banners, and drift checks.

## Local Use

```bash
./gradlew test
```

## Plugin

```kotlin
plugins {
    id("dev.jorisjonkers.openapi-client") version "<version>"
}
```

The plugin artifact is published as:

```text
dev.jorisjonkers:openapi-client-gradle:<version>
```

Consumers resolve it from GitHub Packages:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "JorisJonkersOpenApiClientGradle"
            url = uri("https://maven.pkg.github.com/JorisJonkers-dev/openapi-client-gradle")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orNull
                password = providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orNull
            }
        }
    }
}
```

## Configuration

```kotlin
openApiClient {
    specPath.set("libs/openapi-specs/service.yml")
    apiPackage.set("dev.jorisjonkers.service.client.api")
    modelPackage.set("dev.jorisjonkers.service.client.model")
    packageName.set("dev.jorisjonkers.service.client")
    apis.set(listOf("Service"))
}
```

Required fields are `specPath`, `apiPackage`, `modelPackage`, and `packageName`.
By default the plugin generates a Java `restclient` client using Jackson,
Jakarta validation, and Spring client dependencies. Kotlin Spring `RestClient`
generation is available through:

```kotlin
openApiClient {
    useKotlinSpringRestClient()
}
```

Additional OpenAPI Generator settings can be passed through `configOptions`,
`inlineSchemaOptions`, `schemaMappings`, `typeMappings`, `apis`, `models`, and
`supportingFiles`.

## External Specs

`openApiExternalSpecs` can download raw upstream specs, normalize JSON/YAML to
deterministic minified JSON, and register named filter tasks. The consuming repo
owns vendor URLs, operation allow lists, package names, and when those helper
tasks run.

## Boundary

The plugin only reads local OpenAPI documents and generates JVM clients.
It does not publish generated client libraries, choose frontend TypeScript
generators, apply API compatibility policy, or replace `api-contract-checks`.

## Links

- [Organization profile](https://github.com/JorisJonkers-dev)
- [Security policy](https://github.com/JorisJonkers-dev/.github/security/policy)
- [Changelog](./CHANGELOG.md)
- [License](./LICENSE)

Copyright (c) Joris Jonkers. Source available for viewing only; use, copying,
modification, redistribution, deployment, or reuse is not licensed. See
[LICENSE](./LICENSE).
