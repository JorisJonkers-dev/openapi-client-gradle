# Requirements Checklist: OpenAPI Client Gradle

## Spec Quality

- [x] Overview states the user-facing capability and why it exists.
- [x] User scenarios cover website generation, large-spec preparation, personal-stack consumption, and separation from internal contract validation.
- [x] Functional requirements are testable and use numbered FR entries.
- [x] Success criteria are measurable and use numbered SC entries.
- [x] Assumptions are explicit and do not define hidden implementation choices.
- [x] Edge cases cover invalid specs, large specs, vendor-specific normalization, and multi-client builds.
- [x] Key entities define the domain language used by the spec.
- [x] Out of Scope explicitly excludes spec fetching, frontend generation, internal contract validation, consumer deploy behavior, and personal-stack versioning.

## Prompt Coverage

- [x] Specifies a published Gradle plugin for typed clients from external OpenAPI specs.
- [x] References the website Brevo and Discord client cases without modifying the reference repo.
- [x] Requires consumption by both `JorisJonkers-dev/website` and `JorisJonkers-dev/personal-stack` as a versioned artifact.
- [x] Requires short coordinates and excludes doubled plugin-marker names.
- [x] Requires Renovate-pinned consumption and states that `personal-stack` is not itself versioned.
- [x] Distinguishes this feature from `api-contract-checks`.
- [x] Uses no clarification markers because all scope-critical decisions have reasonable defaults in the prompt or reference repos.
