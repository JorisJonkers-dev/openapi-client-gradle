# External OpenAPI Spec Pipeline Specification

## Overview

This feature adds reusable Gradle tasks for acquiring and preparing third-party OpenAPI specifications before client generation. It complements the existing `dev.jorisjonkers.openapi-client` client-generation plugin by moving common download, normalization, filtering, and compatibility rewrites out of consumer build scripts.

The reference implementation is the `JorisJonkers-dev/website` external spec flow for Discord and Brevo. Vendor URLs, application-specific allow lists, and post-generation workarounds remain in the consuming repository.

## Functional Requirements

- FR-1: The plugin MUST expose an opt-in external spec DSL where consumers configure a central spec directory and a named set of upstream specs.
- FR-2: The plugin MUST register a download task that fetches each configured upstream spec to the central spec directory using pinned, overridable URLs.
- FR-3: The plugin MUST register a normalize task that converts configured JSON or YAML specs into deterministic minified JSON output with a `.json` filename for parsers that choose behavior by extension.
- FR-4: Download and normalize tasks MUST fail with clear Gradle errors for missing URLs, missing source files, invalid JSON/YAML, or output filenames that are not JSON where normalization requires JSON.
- FR-5: The plugin MUST expose a reusable filter/preprocess task type that supports a path/method allow-list and injected operation tag.
- FR-6: The filter task MUST optionally prune `components.schemas` to schemas reachable from retained operations and retained component references.
- FR-7: The filter task MUST optionally rewrite OpenAPI 3.1 `type: "null"` schema fragments to `type: "boolean"` for generators that cannot emit a Java null marker type.
- FR-8: The filter task MUST optionally collapse redundant `{ allOf: [{ "$ref": ... }], enum: [...] }` schema fragments to the referenced schema.
- FR-9: The existing client generation behavior MUST remain compatible with consumer-owned preparation tasks that produce the configured spec before `generate`.
- FR-10: Consumer-specific post-generation fixups, vendor URLs, and allow lists MUST stay in the consumer configuration rather than being hardcoded in the plugin.

## Success Criteria

- SC-1: A TestKit consumer configures file-backed external specs, runs `downloadExternalOpenApiSpecs` and `normalizeExternalOpenApiSpecs`, and receives deterministic minified JSON under a central spec directory.
- SC-2: A fixture equivalent to the website Discord filter keeps only allowed operations, injects a configured tag, prunes unreachable schemas, rewrites null marker schemas, and collapses redundant enum `allOf` shapes.
- SC-3: Existing client generation tests continue to pass unchanged except for additive task registration.
- SC-4: CI runs build and coverage checks through a terminal job named exactly `Pipeline Complete`.

## Out of Scope

- Scheduling upstream spec refreshes.
- Hardcoding Discord, Brevo, Hornet, or personal-stack URLs.
- Frontend TypeScript generation and `@hey-api/openapi-ts` configuration.
- Consumer-specific generated-file patches such as required-field reinjection.
