package dev.jorisjonkers.openapi.client

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.readText

class OpenApiExternalSpecsTest : OpenApiTestProjectFixture() {
    @Test
    fun `downloads and normalizes configured external specs with deterministic json output`() {
        writeSettings()
        writeSpec("fixtures/upstream.json", upstreamJsonFixture)
        writeSpec("fixtures/upstream.yml", upstreamYamlFixture)
        writeBuildFile(configuredExternalSpecsBuildFile())

        val result = runGradle("downloadExternalOpenApiSpecs", "normalizeExternalOpenApiSpecs")

        assertEquals(TaskOutcome.SUCCESS, result.task(":downloadExternalOpenApiSpecs")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":normalizeExternalOpenApiSpecs")?.outcome)
        assertTrue(Files.exists(tempDir.resolve("build/central-openapi-specs/json-fixture.raw")))
        assertEquals(
            """{"info":{"title":"JSON Fixture","version":"1.0.0"},"openapi":"3.0.3","paths":{}}""",
            tempDir.resolve("build/central-openapi-specs/json-fixture.json").readText(),
        )
        assertEquals(
            """{"info":{"title":"YAML Fixture","version":"1.0.0"},"openapi":"3.0.3","paths":{}}""",
            tempDir.resolve("build/central-openapi-specs/yaml-fixture.json").readText(),
        )
    }

    @Test
    fun `fails clearly for missing external spec source urls`() {
        writeSettings()
        writeBuildFile(missingExternalSourceUrlBuildFile())

        val result = runGradleAndFail("downloadExternalOpenApiSpecs")

        assertTrue(result.output.contains("openApiExternalSpecs.specs.missingUrl.sourceUrl is required"))
    }

    @Test
    fun `fails clearly for malformed external specs`() {
        writeSettings()
        writeSpec("fixtures/broken.yml", "openapi: [")
        writeBuildFile(brokenExternalSpecBuildFile())

        val result = runGradleAndFail("normalizeExternalOpenApiSpecs")

        assertTrue(result.output.contains("OpenAPI spec must be valid JSON or YAML"))
    }

    @Test
    fun `fails clearly for external spec normalized filenames without json extension`() {
        writeSettings()
        writeSpec("fixtures/valid.json", EXTERNAL_VALID_JSON_FIXTURE)
        writeBuildFile(badExternalOutputExtensionBuildFile())

        val result = runGradleAndFail("normalizeExternalOpenApiSpecs")

        assertTrue(
            result.output.contains("openApiExternalSpecs.specs.badOutput.normalizedFileName must end with .json"),
        )
    }

    @Test
    fun `external spec tasks expose configured inputs directly`() {
        val source = writeSpec("fixtures/external-valid.json", EXTERNAL_VALID_JSON_FIXTURE)
        val sourceUri = source.toUri().toString()
        val harness = externalSpecHarness()

        harness.extension.specs.create("configured") {
            sourceUrl.set(sourceUri)
            rawFileName.set("nested/raw.yml")
            normalizedFileName.set("nested/out.JSON")
        }

        assertEquals(mapOf("configured" to sourceUri), harness.download.configuredSourceUrls)
        assertEquals(mapOf("configured" to "nested/raw.yml"), harness.download.configuredRawFileNames)
        assertEquals(mapOf("configured" to "nested/raw.yml"), harness.normalize.configuredRawFileNames)
        assertEquals(mapOf("configured" to "nested/out.JSON"), harness.normalize.configuredNormalizedFileNames)
        assertTrue(
            harness.normalize.rawSpecFiles.files
                .single()
                .toPath()
                .endsWith("openapi-specs/nested/raw.yml"),
        )
        assertTrue(
            harness.normalize.normalizedSpecFiles.files
                .single()
                .toPath()
                .endsWith("openapi-specs/nested/out.JSON"),
        )
    }

    @Test
    fun `external spec tasks download and normalize direct configurations`() {
        val source = writeSpec("fixtures/external-valid.json", EXTERNAL_VALID_JSON_FIXTURE)
        val harness = externalSpecHarness()

        harness.extension.specs.create("success") {
            sourceUrl.set(source.toUri().toString())
            rawFileName.set("downloads/source.raw")
            normalizedFileName.set("downloads/source.json")
        }

        harness.download.download()
        assertEquals(source.readText(), tempDir.resolve("openapi-specs/downloads/source.raw").readText())

        harness.normalize.normalize()
        assertEquals(NORMALIZED_EXTERNAL_VALID_JSON, tempDir.resolve("openapi-specs/downloads/source.json").readText())
    }

    @Test
    fun `external spec tasks reject empty direct configurations`() {
        val harness = externalSpecHarness()

        assertGradleFails("openApiExternalSpecs.specs must contain at least one configured spec") {
            harness.download.download()
        }
        assertGradleFails("openApiExternalSpecs.specs must contain at least one configured spec") {
            harness.normalize.normalize()
        }
    }

    @Test
    fun `external spec download rejects invalid source uris`() {
        assertExternalDownloadFails("sourceUrl must be an absolute URI", "fixtures/external-valid.json")
        assertExternalDownloadFails("sourceUrl must be a valid URI", "http://[invalid")
    }

    @Test
    fun `external spec download rejects unsafe raw file names`() {
        val source = writeSpec("fixtures/external-valid.json", EXTERNAL_VALID_JSON_FIXTURE)
        val sourceUri = source.toUri().toString()

        assertExternalDownloadFails("rawFileName must not be blank", sourceUri, rawFileName = " ")
        assertExternalDownloadFails(
            "rawFileName must be relative",
            sourceUri,
            rawFileName = tempDir.resolve("outside.raw").toString(),
        )
        assertExternalDownloadFails("rawFileName must stay inside", sourceUri, rawFileName = "../outside.raw")
    }

    @Test
    fun `external spec normalize rejects missing and unsafe outputs`() {
        val source = writeSpec("fixtures/external-valid.json", EXTERNAL_VALID_JSON_FIXTURE)
        val sourceUri = source.toUri().toString()

        externalSpecHarness().let { harness ->
            harness.extension.specs.create("missingRaw") {
                rawFileName.set("missing.yml")
                normalizedFileName.set("missing.json")
            }
            assertGradleFails("Raw OpenAPI spec for 'missingRaw' does not exist") {
                harness.normalize.normalize()
            }
        }

        writeSpec("openapi-specs/raw.json", source.readText())
        externalSpecHarness().let { harness ->
            harness.extension.specs.create("traversalNormalized") {
                sourceUrl.set(sourceUri)
                rawFileName.set("raw.json")
                normalizedFileName.set("../outside.json")
            }
            assertGradleFails("normalizedFileName must stay inside") {
                harness.normalize.normalize()
            }
        }
    }

    private fun assertExternalDownloadFails(
        expectedMessage: String,
        sourceUrl: String,
        rawFileName: String? = null,
    ) {
        externalSpecHarness().let { harness ->
            harness.extension.specs.create("invalid") {
                this.sourceUrl.set(sourceUrl)
                rawFileName?.let { this.rawFileName.set(it) }
            }
            assertGradleFails(expectedMessage) {
                harness.download.download()
            }
        }
    }

    private fun configuredExternalSpecsBuildFile(): String =
        """
        plugins {
            id("dev.jorisjonkers.openapi-client")
        }

        openApiExternalSpecs {
            specDirectory.set(layout.buildDirectory.dir("central-openapi-specs"))
            specs {
                create("jsonFixture") {
                    sourceUrl.set(file("fixtures/upstream.json").toURI().toString())
                    rawFileName.set("json-fixture.raw")
                    normalizedFileName.set("json-fixture.json")
                }
                create("yamlFixture") {
                    sourceUrl.set(file("fixtures/upstream.yml").toURI().toString())
                    rawFileName.set("yaml-fixture.yml")
                    normalizedFileName.set("yaml-fixture.json")
                }
            }
        }
        """.trimIndent()

    private fun missingExternalSourceUrlBuildFile(): String =
        """
        plugins {
            id("dev.jorisjonkers.openapi-client")
        }

        openApiExternalSpecs {
            specs {
                create("missingUrl")
            }
        }
        """.trimIndent()

    private fun brokenExternalSpecBuildFile(): String =
        """
        plugins {
            id("dev.jorisjonkers.openapi-client")
        }

        openApiExternalSpecs {
            specs {
                create("broken") {
                    sourceUrl.set(file("fixtures/broken.yml").toURI().toString())
                    rawFileName.set("broken.yml")
                    normalizedFileName.set("broken.json")
                }
            }
        }
        """.trimIndent()

    private fun badExternalOutputExtensionBuildFile(): String =
        """
        plugins {
            id("dev.jorisjonkers.openapi-client")
        }

        openApiExternalSpecs {
            specs {
                create("badOutput") {
                    sourceUrl.set(file("fixtures/valid.json").toURI().toString())
                    rawFileName.set("valid.json")
                    normalizedFileName.set("valid.txt")
                }
            }
        }
        """.trimIndent()
}

private val upstreamJsonFixture =
    """
    {
      "paths": {},
      "openapi": "3.0.3",
      "info": {
        "version": "1.0.0",
        "title": "JSON Fixture"
      }
    }
    """.trimIndent()

private val upstreamYamlFixture =
    """
    paths: {}
    openapi: 3.0.3
    info:
      version: 1.0.0
      title: YAML Fixture
    """.trimIndent()
