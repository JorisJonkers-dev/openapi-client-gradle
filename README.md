# openapi-client-gradle

Gradle plugin for generating typed JVM clients from local OpenAPI specs owned by the consuming repo.

## Coordinates

Published Maven artifact:

```text
dev.jorisjonkers:openapi-client-gradle:<version>
```

Plugin id:

```text
dev.jorisjonkers.openapi-client
```

Consumers resolve the plugin from GitHub Packages in `settings.gradle.kts`:

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

Then apply the pinned plugin version in a client module:

```kotlin
plugins {
    id("dev.jorisjonkers.openapi-client") version "0.2.0"
}
```

## Configuration

```kotlin
openApiClient {
    specPath.set("libs/openapi-specs/vendor.yml")
    apiPackage.set("com.example.clients.vendor.api")
    modelPackage.set("com.example.clients.vendor.model")
    packageName.set("com.example.clients.vendor.invoker")
    apis.set(listOf("Messages", "Contacts"))
    schemaMappings.set(
        mapOf(
            "external_identifier_parameter" to "java.lang.String",
        ),
    )
    typeMappings.set(emptyMap())

    // Optional overrides; defaults match the extracted Java restclient convention.
    generatorName.set("java")
    library.set("restclient")
    sourceFolder.set("src/main/java")
    serializationLibrary.set("jackson")
    dateLibrary.set("java8")
    useJakartaEe.set(true)
    useBeanValidation.set(true)
    useJackson3.set(true)
    useSpringBoot4.set(true)
    enumPropertyNaming.set("MACRO_CASE")
    inlineSchemaOptions.set(mapOf("RESOLVE_INLINE_ENUMS" to "true"))
}
```

For Kotlin clients backed by Spring `RestClient`, use the built-in mode:

```kotlin
openApiClient {
    useKotlinSpringRestClient()
    specPath.set("libs/openapi-specs/vendor.yml")
    apiPackage.set("dev.jorisjonkers.vendor.client.api")
    modelPackage.set("dev.jorisjonkers.vendor.client.model")
    packageName.set("dev.jorisjonkers.vendor.client")
}
```

Required fields:

- `specPath`: local JSON/YAML OpenAPI document, absolute or relative to the consuming root project.
- `apiPackage`: package for generated API classes.
- `modelPackage`: package for generated model classes.
- `packageName`: invoker/support package.

Optional fields:

- `apis`: selected OpenAPI tags/API groups. Empty means generate all APIs.
- `models`: selected generated models. Empty keeps generator default behavior.
- `supportingFiles`: selected supporting files. Empty keeps generator default behavior.
- `schemaMappings`: OpenAPI Generator schema mappings.
- `typeMappings`: OpenAPI Generator type mappings.
- `configOptions` and `inlineSchemaOptions`: additional OpenAPI Generator options.

The plugin registers `build/generated/openapi/<sourceFolder>` as main JVM source and makes compilation depend on `generate`. Generated clients use OpenAPI Generator `java` + `restclient`, Jackson 3, Jakarta annotations/validation, and Spring 7 client dependencies by default. Dependency versions are exposed as extension properties for consumers that need a different Spring, Jackson, or Jakarta stack.

## External Specs

Spec acquisition, refresh, filtering, and vendor-specific values stay in the consuming repo. The plugin provides opt-in generic helpers for common external-spec lifecycle steps:

```kotlin
openApiExternalSpecs {
    specDirectory.set(rootProject.layout.projectDirectory.dir("libs/openapi-specs"))

    specs {
        create("vendor") {
            sourceUrl.set(providers.gradleProperty("vendorOpenApiUrl"))
            rawFileName.set("vendor.raw.json")
            normalizedFileName.set("vendor.json")
        }
    }

    filters {
        create("vendorClient") {
            inputSpec.set("libs/openapi-specs/vendor.json")
            outputSpec.set("services/api/clients/vendor/build/vendor-filtered.json")
            allowedOperations.set(
                mapOf(
                    "/accounts/{account_id}" to listOf("get"),
                    "/messages" to listOf("post"),
                ),
            )
            injectedTag.set("Vendor")
            pruneUnreachableSchemas.set(true)
            rewriteNullTypes.set(true)
            collapseRedundantEnumAllOf.set(true)
        }
    }
}

tasks.named("generate") {
    dependsOn("filterVendorClientOpenApiSpec")
}

openApiClient {
    specPath.set("services/api/clients/vendor/build/vendor-filtered.json")
    apiPackage.set("com.example.clients.vendor.api")
    modelPackage.set("com.example.clients.vendor.model")
    packageName.set("com.example.clients.vendor.invoker")
    apis.set(listOf("Vendor"))
}
```

`downloadExternalOpenApiSpecs` downloads configured sources to the central directory. `normalizeExternalOpenApiSpecs` converts configured JSON/YAML raw files to deterministic minified JSON. Named filters register tasks named `filter<Name>OpenApiSpec`.

Validation runs after `generate` dependencies, so prepared specs are checked after the prep task writes them. Vendor URLs, operation allow lists, and package names remain consumer-owned configuration.

## Provenance and Drift

The plugin also exposes generic task types for generated text artifacts:

```kotlin
tasks.register<dev.jorisjonkers.openapi.client.OpenApiProvenanceBannerTask>("bannerGeneratedApi") {
    inputFile.set(layout.buildDirectory.file("generated/api.tmp.ts"))
    outputFile.set(layout.projectDirectory.file("src/api/generated.ts"))
    bannerText.set(
        """
        /**
         * AUTO-GENERATED. Do not edit by hand.
         * Source: services/api/openapi.json
         * Regenerate with: pnpm contract:generate
         * Drift gate: pnpm contract:check
         */
        """.trimIndent(),
    )
}

tasks.register<dev.jorisjonkers.openapi.client.OpenApiDriftCheckTask>("checkGeneratedApiDrift") {
    expectedFile.set(layout.projectDirectory.file("src/api/generated.ts"))
    actualFile.set(layout.buildDirectory.file("generated/api.tmp.ts"))
    failureMessage.set("Generated API client drift detected. Regenerate the client.")
}
```

These helpers do exact text processing only. They do not choose or invoke a frontend TypeScript generator.

## Boundary

The `generate` task only reads local OpenAPI documents and generates typed JVM clients. External download, normalization, filtering, provenance, and drift tasks are explicit opt-in helpers. The plugin does not hardcode vendor endpoints, publish generated client libraries, force a frontend TypeScript generator, or replace `api-contract-checks`.

## Publishing

Releases are created by release-please. Tag publishing runs:

```bash
./gradlew publish --no-daemon --no-parallel --max-workers=1
```

GitHub Packages may mark a brand-new package private by default on this account; the package owner must set it public once after the first publish.
