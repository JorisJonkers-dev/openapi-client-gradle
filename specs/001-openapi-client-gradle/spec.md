# OpenAPI Client Gradle Specification

## Overview

This feature defines a published Gradle plugin for generating typed JVM clients from external OpenAPI specifications. The plugin serves consumer modules that already own or vendor an external spec file, then need a repeatable Gradle-facing way to generate client source from that spec and a small project-specific configuration.

The immediate reference cases are `JorisJonkers-dev/website` external specs under `/workspace/website/libs/openapi-specs` and generated client modules under `/workspace/website/services/api/clients`, especially `discord` and `brevo`. The new repo must replace repo-local convention duplication with a versioned artifact that can be pinned and upgraded by `JorisJonkers-dev/website` and `JorisJonkers-dev/personal-stack`.

This feature is distinct from `api-contract-checks`. That separate capability validates internal API contracts. This feature only covers client generation from external OpenAPI documents.

## User Scenarios

- Scenario 1: A website maintainer adds an external OpenAPI spec such as Brevo to a client subproject, applies the published plugin, supplies the spec path and package configuration, and receives generated typed client source that participates in the normal Gradle build.
- Scenario 2: A website maintainer constrains a large external spec such as Discord before generation, points the plugin at the prepared spec, selects the desired API surface, and avoids generating unused endpoints.
- Scenario 3: A personal-stack maintainer pins the same plugin artifact version used by other JorisJonkers-dev repos, applies it to a client module that consumes an external spec, and upgrades the plugin through normal dependency update automation.
- Scenario 4: A platform maintainer compares this plugin with `api-contract-checks` and can determine that external client generation and internal contract validation remain separate responsibilities.

## Functional Requirements (FR-n)

- FR-1: The repo MUST define a Gradle plugin product whose purpose is generation of typed clients from external OpenAPI documents supplied by consuming repos.
- FR-2: The plugin MUST be publishable as a versioned artifact consumable by both `JorisJonkers-dev/website` and `JorisJonkers-dev/personal-stack` without requiring either repo to include this repo as local build logic.
- FR-3: The published plugin identity MUST use short JorisJonkers-dev coordinates suitable for Gradle plugin declarations and version catalogs, and MUST avoid doubled plugin-marker or repeated plugin-id artifact names.
- FR-4: Consuming repos MUST be able to pin the plugin to an explicit version and allow dependency update tooling to propose version bumps; `personal-stack` MUST NOT need its own release version or tag to consume the plugin.
- FR-5: The plugin MUST accept a consumer-provided spec file path for local JSON or YAML OpenAPI documents, including files equivalent to `libs/openapi-specs/brevo.yml` and generated or prepared files equivalent to `services/api/clients/discord/build/discord-filtered.json`.
- FR-6: The plugin MUST expose consumer configuration for generated API package, model package, invoker or support package, selected API groups or tags, and schema or type mappings.
- FR-7: The plugin MUST generate typed client source that is visible to the consuming module's normal compile, test, and packaged output workflows.
- FR-8: The plugin MUST make generation inputs observable to Gradle so changes to the spec file or generation configuration cause regeneration, while unchanged inputs can be treated as up to date or cacheable.
- FR-9: The plugin MUST allow a consumer-owned preparation task to produce or modify the spec before generation, so vendor-specific filtering or normalization remains in the consuming repo when needed.
- FR-10: The plugin MUST support the current Brevo reference needs: a YAML external spec, package configuration, selected API groups, and schema mappings.
- FR-11: The plugin MUST support the current Discord reference needs: a prepared JSON external spec, package configuration, selected generated API surface, and compatibility with consumer-owned filtering before generation.
- FR-12: The plugin MUST fail with a clear Gradle error when required configuration is absent, the spec path cannot be read, or the configured generated-client surface is internally inconsistent.
- FR-13: The plugin MUST NOT fetch external specs from upstream services as part of client generation; spec acquisition and drift detection remain consumer-repo responsibilities.
- FR-14: The plugin MUST NOT replace, invoke, or change `api-contract-checks`; internal API contract validation remains a separate capability.
- FR-15: The plugin MUST document the minimum consumer contract: plugin declaration shape, required configuration fields, expected generated source behavior, and the boundary between external client generation and internal contract validation.

## Success Criteria (SC-n, measurable)

- SC-1: A consumer module equivalent to `/workspace/website/services/api/clients/brevo` can generate and compile its typed client from `/workspace/website/libs/openapi-specs/brevo.yml` using only the published plugin artifact and consumer configuration.
- SC-2: A consumer module equivalent to `/workspace/website/services/api/clients/discord` can run a consumer-owned spec preparation step, generate only the selected Discord client surface, and compile the generated typed client using only the published plugin artifact and consumer configuration.
- SC-3: The same released plugin version can be pinned in both `JorisJonkers-dev/website` and `JorisJonkers-dev/personal-stack`; dependency update tooling can detect a newer version without either repo using `includeBuild` for this plugin.
- SC-4: The published plugin id and module coordinates can be written without repeated plugin-id tokens or doubled plugin-marker names; a reviewer can verify this from the generated publication metadata and the consumer declaration.
- SC-5: Changing only the spec file or one configured generation option causes the generation task to run on the next Gradle invocation; running the same invocation twice with no relevant changes reports the generation task as up to date or loaded from cache.
- SC-6: Generation from local spec files performs no upstream spec download; any network access needed to refresh Discord or Brevo specs remains in the consumer repo's existing spec-refresh workflow.
- SC-7: A build that validates `api-contract-checks` continues to pass or fail independently of this plugin; disabling this plugin does not remove internal contract validation, and disabling `api-contract-checks` does not remove external client generation.
- SC-8: The plugin documentation allows a new client module to be configured with spec path, package names, selected APIs, and schema mappings without consulting the website build-logic source.

## Assumptions

- External OpenAPI spec files are owned, vendored, or generated by the consuming repo before this plugin's generation task runs.
- The initial typed-client target is JVM source for Gradle builds used by `JorisJonkers-dev/website` and `JorisJonkers-dev/personal-stack`.
- The current Brevo and Discord website client modules are acceptance references for the minimum useful configuration surface.
- Publishing occurs to an JorisJonkers-dev-accessible package repository that both consuming repos can resolve with their existing package credentials and dependency update policies.
- `personal-stack` remains continuously auto-deployed from its normal branch workflow and is not assigned a repo release version for the purpose of consuming this plugin.

## Edge Cases

- The configured spec file is missing, unreadable, empty, or not valid OpenAPI JSON/YAML.
- A large external spec would generate an unusably broad client unless the consumer selects APIs or prepares a reduced spec first.
- An external spec contains vendor-specific OpenAPI shapes that require consumer-owned normalization before generation.
- Multiple client modules in the same build use different external specs and package names.
- Two client modules accidentally configure overlapping generated packages or output locations.
- A schema mapping refers to a schema name that is absent after spec preparation.
- A selected API group or tag is absent from the prepared spec.
- Upstream spec drift changes generated type names or removes operations expected by application code.

## Key Entities

- Published Gradle Plugin: Versioned build artifact that exposes external OpenAPI client generation to consuming repos.
- Consuming Repo: `JorisJonkers-dev/website` or `JorisJonkers-dev/personal-stack`, each pinning the plugin version and owning local specs and client modules.
- External OpenAPI Spec: JSON or YAML document sourced from a third-party API and stored or prepared by the consuming repo.
- Client Module: Gradle subproject that applies the plugin and compiles generated typed client source.
- Generation Configuration: Consumer-provided values such as spec path, package names, selected APIs, and schema mappings.
- Generated Client Source: Typed JVM source produced from the external spec and included in the consuming module's normal build.
- Spec Preparation Task: Optional consumer-owned Gradle task that filters or normalizes a vendor spec before client generation.
- Internal Contract Validation: Separate `api-contract-checks` capability for validating internal contracts, outside this feature's responsibility.

## Out of Scope

- Downloading, refreshing, or scheduling updates for external OpenAPI specs.
- Replacing or modifying `api-contract-checks` or any other internal contract validation workflow.
- Generating frontend TypeScript clients or replacing existing frontend OpenAPI tooling.
- Owning vendor-specific Discord, Brevo, or other third-party spec rewrites inside this repo.
- Publishing generated client libraries from consumer repos.
- Creating deployment workflows, service images, or runtime auto-deploy behavior.
- Adding release versions to `personal-stack` itself.
- Managing package credentials, repository secrets, or dependency update bot configuration.
