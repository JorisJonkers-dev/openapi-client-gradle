# Agents

Guidance for any coding agent working in this repository.

- Agent contract: [CLAUDE.md](CLAUDE.md), which points at [the estate contract](https://github.com/JorisJonkers-dev/workspace/blob/main/CLAUDE.md).
- What this repository holds: the `dev.jorisjonkers.openapi-client` Gradle plugin (`src/main`, `src/test`). See [README.md](README.md).

## Build and test

```bash
./gradlew test
./gradlew detektMain detektTest ktlintCheck validatePlugins verifyPublishingCoordinates
./gradlew test jacocoTestCoverageVerification
```

The first is in the README; the others mirror the CI lint and test steps (`.github/workflows/ci.yml`).
