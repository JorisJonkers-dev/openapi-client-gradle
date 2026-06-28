# Tasks: OpenAPI Client Gradle

## Phase 1: Project Bootstrap

- [x] T001 [FR-1, FR-2] Add Gradle 9.5.1 wrapper files and root `settings.gradle.kts` for `openapi-client-gradle`.
- [x] T002 [FR-1, FR-2, FR-3] Add root `build.gradle.kts` with Kotlin DSL plugin development, OpenAPI Generator dependency, Maven publishing, GitHub Packages repository, and marker-publication suppression.
- [x] T003 [FR-2, FR-4] Add `.gitignore`, `release-please-config.json`, and `.release-please-manifest.json`.

## Phase 2: Acceptance Tests Before Implementation

- [x] T004 [FR-5, FR-6, FR-7, FR-8, FR-10, SC-1, SC-5] Add TestKit fixture spec `src/test/resources/specs/sample.yml` and a test that generates, compiles, and reruns a YAML client with selected APIs and schema/type mappings.
- [x] T005 [FR-9, FR-11, SC-2] Add a TestKit test where a consumer-owned prep task writes the spec before `generate` runs.
- [x] T006 [FR-12] Add TestKit failure tests for missing required config, unreadable spec path, invalid OpenAPI shape, absent selected API/tag, and blank mappings.
- [x] T007 [FR-3, SC-4] Add build verification that new and legacy marker publications are present and the Maven coordinates are `dev.jorisjonkers:openapi-client-gradle`.

## Phase 3: Plugin Implementation

- [x] T008 [FR-1, FR-6] Implement `OpenApiClientExtension` with `specPath`, `apiPackage`, `modelPackage`, `packageName`, `apis`, `schemaMappings`, and `typeMappings`.
- [x] T009 [FR-1, FR-5, FR-7, FR-8, FR-10, FR-11] Implement `OpenApiClientPlugin` to apply `java-library` and `org.openapi.generator`, configure generated source sets, add default generated-client dependencies, and register `generate`.
- [x] T010 [FR-8] Declare the spec file, package fields, selected APIs, schema mappings, and type mappings as `generate` inputs; declare the generated output directory.
- [x] T011 [FR-9] Ensure validation runs inside `generate` after consumer-owned dependencies so prepared specs are supported.
- [x] T012 [FR-12] Implement configuration/spec validation with clear Gradle exceptions for missing required config, unreadable/invalid local specs, absent selected tags, and blank mappings.
- [x] T013 [FR-13, FR-14] Confirm implementation contains no external spec fetching and no `api-contract-checks` integration.

## Phase 4: Documentation and Workflows

- [x] T014 [FR-15, SC-3, SC-8] Replace `README.md` with the consumer contract, plugin-management mapping, example configuration, prep-task example, and boundary with `api-contract-checks`.
- [x] T015 [FR-2] Replace `.github/workflows/ci.yml` with a real Gradle build/test workflow ending in `Pipeline Complete`.
- [x] T016 [FR-2, FR-4] Add `.github/workflows/release.yml` for release-please and `.github/workflows/publish-on-tag.yml` for serial Maven publish to GitHub Packages.

## Phase 5: Verification and Landing

- [x] T017 Run `./gradlew build --no-daemon` locally and fix failures.
- [x] T018 Run a local publish verification (`./gradlew publishToMavenLocal --no-daemon --no-parallel --max-workers=1`) and fix publication issues.
- [x] T019 Run a final Spec Kit consistency check across `spec.md`, `plan.md`, and `tasks.md`; update minimally if implementation changed scope.
- [ ] T020 Commit all changes on `impl/initial`.
- [ ] T021 Push `impl/initial`, open a PR, poll `Pipeline Complete`, fix once if red, and squash-merge/delete branch when green.

## Dependencies

- T001-T003 must complete before tests or implementation can run.
- T004-T007 define acceptance coverage before T008-T012.
- T008-T012 must complete before T014-T018 can pass.
- T015-T016 can run after T001-T003 but before landing.
- T020-T021 require T017-T019.

## Parallelizable Tasks

- T003, T014, T015, and T016 touch independent files and can be performed in parallel after the implementation decisions are stable.
- T004, T005, and T006 share the same test file and should be implemented sequentially.
