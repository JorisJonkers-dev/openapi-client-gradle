# Implementation Plan: OpenAPI Client Gradle

## Technical Context

- Product: published Gradle plugin for JVM OpenAPI client generation from consumer-owned local specs.
- Build stack: Gradle 9.5.1 wrapper, Kotlin DSL plugin implementation, Java 21 local/CI runtime, plugin bytecode targeted to Java 21 to match the current JorisJonkers-dev Gradle baseline.
- Generator stack: `org.openapitools:openapi-generator-gradle-plugin:7.22.0`, matching the current website build-logic convention.
- Generated client target: Java source using OpenAPI Generator `java` + `restclient`, Jackson 3 serialization, Jakarta annotations/validation, and Spring 7 client dependencies.
- Publication: GitHub Packages Maven artifact `dev.jorisjonkers:openapi-client-gradle`. The Gradle plugin id is `dev.jorisjonkers.openapi-client`; consumers map that id to the Maven artifact with `pluginManagement.resolutionStrategy.eachPlugin`. Automatic plugin-new and legacy marker publications are enabled to avoid doubled marker/module names.

## Design Decisions

1. Package the current website `openapi-client-conventions` behavior as a binary Gradle plugin instead of a precompiled script plugin.
   - Rationale: both `website` and `personal-stack` can pin a versioned artifact without `includeBuild`.
2. Keep the website extension shape and task name compatible.
   - Extension: `openApiClient { specPath, apiPackage, modelPackage, packageName, apis, schemaMappings, typeMappings }`.
   - Generation task: `generate`, with `generateOpenApiClient` as a readable alias that depends on it.
   - Rationale: the current Discord prep task can keep `tasks.named("generate").configure { dependsOn(filterDiscordSpec) }`.
3. Validate inside the `generate` task `doFirst` action.
   - Rationale: consumer-owned preparation dependencies on `generate` run before validation, so prepared specs are validated after they exist.
4. Resolve `specPath` against the consuming root project unless it is absolute.
   - Rationale: current website clients configure root-relative paths such as `libs/openapi-specs/brevo.yml` and `services/api/clients/discord/build/discord-filtered.json`.
5. Validate only the configuration consistency this plugin can determine before generation.
   - Required package/spec fields must be present and nonblank.
   - The spec must exist, be readable, parse as JSON/YAML, include `openapi`, and include a `paths` object.
   - Selected `apis` must match at least one operation tag in the prepared spec.
   - Mapping keys and values must be nonblank.
   - Full OpenAPI semantic validation and generator-specific inline schema naming remain delegated to OpenAPI Generator.

## Project Structure

```text
.
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/wrapper/*
├── src/main/kotlin/dev/extratoast/openapi/client/
│   └── OpenApiClientPlugin.kt
├── src/test/kotlin/dev/extratoast/openapi/client/
│   └── OpenApiClientPluginTest.kt
├── src/test/resources/specs/
│   ├── sample.yml
│   └── untagged.yml
├── .github/workflows/
│   ├── ci.yml
│   ├── release.yml
│   └── publish-on-tag.yml
├── release-please-config.json
├── .release-please-manifest.json
└── README.md
```

## Functional Requirement Mapping

- FR-1: Binary Gradle plugin applies Java/OpenAPI generator behavior for external client generation.
- FR-2, FR-4: Maven publication to GitHub Packages with release-please versioning and exact consumer pinning.
- FR-3: `dev.jorisjonkers.openapi-client` maps to `dev.jorisjonkers:openapi-client-gradle`; new and legacy marker publications are enabled.
- FR-5: `specPath` supports root-relative and absolute local JSON/YAML files.
- FR-6: `openApiClient` exposes package names, selected APIs/tags, schema mappings, and type mappings.
- FR-7: Generated `src/main/java` is registered in the main Java source set; Java compilation depends on generation.
- FR-8: `generate` declares spec file and configuration properties as Gradle inputs and generated output as an output directory.
- FR-9: Consumers can attach preparation tasks to `generate`; validation executes after those dependencies.
- FR-10: Brevo YAML needs are covered by root-relative spec path, packages, selected APIs, and schema mappings.
- FR-11: Discord prepared JSON needs are covered by generated spec path, selected tag, and consumer-owned prep dependency.
- FR-12: Validation produces Gradle exceptions for missing required fields, unreadable/invalid specs, absent selected tags, and blank mappings.
- FR-13: The plugin reads only local files and performs no upstream spec fetch.
- FR-14: No `api-contract-checks` integration or invocation is added.
- FR-15: README documents plugin declaration, configuration fields, generated-source behavior, preparation-task hook, and boundary with internal contract validation.

## Success Criteria Mapping

- SC-1: TestKit builds a consumer project from a bundled YAML spec and compiles the generated client.
- SC-2: TestKit builds a consumer project where a prep task writes the selected-tag JSON/YAML spec before generation.
- SC-3: README and release workflows document exact version pinning for `website` and `personal-stack`.
- SC-4: Build verification checks that only the normal Maven publication is present and new and legacy marker publications are present.
- SC-5: TestKit invokes `generate` twice and asserts the second run is `UP_TO_DATE` for unchanged inputs.
- SC-6: Implementation has no network fetch code; tests use local fixture specs.
- SC-7: No code path references or invokes `api-contract-checks`.
- SC-8: README includes a complete consumer configuration example for spec path, packages, APIs, and schema/type mappings.

## CI and Release

- CI workflow runs `./gradlew build --no-daemon` on pull requests and pushes to `main`; terminal job is named exactly `Pipeline Complete` and depends on the build/test gate.
- Release workflow runs release-please on `main`.
- Publish workflow runs on `v*` tags and executes `./gradlew publish --no-daemon --no-parallel --max-workers=1` with GitHub Packages credentials.

## Risks and Constraints

- OpenAPI Generator output can change across generator versions, so the generator version is pinned.
- Some vendor-specific Discord normalization remains consumer-owned by design; this plugin only consumes the prepared local spec.
- Schema mapping names can refer to generator-created inline schema names. The plugin validates blank mappings but does not reject mappings absent from `components.schemas`.
