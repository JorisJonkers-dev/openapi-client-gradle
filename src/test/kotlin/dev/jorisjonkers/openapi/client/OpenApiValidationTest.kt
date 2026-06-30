package dev.jorisjonkers.openapi.client

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class OpenApiValidationTest : OpenApiTestProjectFixture() {
    @Test
    fun `validates yaml and json specs directly`() {
        val yaml = writeSpec("specs/direct.yml", directYamlFixture)
        validateConfiguration(
            specFile = yaml,
            apis = listOf("Pets"),
            schemaMappings = mapOf("PetEnvelope" to "com.example.PetEnvelope"),
            typeMappings = mapOf("uuid" to "java.util.UUID"),
        )

        val json = writeSpec("specs/direct.json", directJsonFixture)
        validateConfiguration(specFile = json, apis = emptyList())
    }

    @Test
    fun `rejects invalid direct configuration`() {
        writeResource("specs/sample.yml", tempDir.resolve("specs/sample.yml"))
        val validSpec = tempDir.resolve("specs/sample.yml")

        assertValidationFails("openApiClient.apiPackage is required", specFile = validSpec, apiPackage = "")
        assertValidationFails(
            "openApiClient.apis must not contain blank values",
            specFile = validSpec,
            apis = listOf(" "),
        )
        assertValidationFails(
            "openApiClient.typeMappings must not contain blank keys or values",
            specFile = validSpec,
            typeMappings =
                mapOf(
                    "uuid" to "",
                ),
        )
        assertValidationFails("openApiClient.specPath is required", specPath = "specs/missing.yml", specFile = null)
        assertValidationFails("OpenAPI spec file does not exist", specFile = tempDir.resolve("specs/missing.yml"))
        assertValidationFails("OpenAPI spec file is not readable", specFile = tempDir)

        assertValidationFails("OpenAPI spec file is empty", specFile = writeSpec("specs/empty.yml", ""))
        assertValidationFails("OpenAPI spec must be valid JSON or YAML", specFile = writeSpec("specs/broken.json", "{"))
        assertValidationFails(
            "OpenAPI spec must contain a 'paths' object",
            specFile = writeSpec("specs/missing-paths.yml", missingPathsYamlFixture),
        )

        writeResource("specs/untagged.yml", tempDir.resolve("specs/untagged.yml"))
        assertValidationFails(
            "Available tags: (none)",
            specFile = tempDir.resolve("specs/untagged.yml"),
            apis = listOf("Missing"),
        )
    }

    private fun validateConfiguration(
        specFile: Path?,
        specPath: String? = specFile?.toString() ?: "spec.yml",
        apiPackage: String? = "com.example.pet.api",
        modelPackage: String? = "com.example.pet.model",
        packageName: String? = "com.example.pet.invoker",
        apis: List<String> = listOf("Pets"),
        schemaMappings: Map<String, String> = emptyMap(),
        typeMappings: Map<String, String> = emptyMap(),
    ) {
        OpenApiClientConfigurationValidator.validate(
            specPath = specPath,
            specFile = specFile?.toFile(),
            apiPackage = apiPackage,
            modelPackage = modelPackage,
            packageName = packageName,
            apis = apis,
            schemaMappings = schemaMappings,
            typeMappings = typeMappings,
        )
    }

    private fun assertValidationFails(
        expectedMessage: String,
        specFile: Path?,
        specPath: String? = specFile?.toString() ?: "spec.yml",
        apiPackage: String? = "com.example.pet.api",
        modelPackage: String? = "com.example.pet.model",
        packageName: String? = "com.example.pet.invoker",
        apis: List<String> = listOf("Pets"),
        schemaMappings: Map<String, String> = emptyMap(),
        typeMappings: Map<String, String> = emptyMap(),
    ) {
        val error =
            assertThrows(GradleException::class.java) {
                validateConfiguration(
                    specPath = specPath,
                    specFile = specFile,
                    apiPackage = apiPackage,
                    modelPackage = modelPackage,
                    packageName = packageName,
                    apis = apis,
                    schemaMappings = schemaMappings,
                    typeMappings = typeMappings,
                )
            }
        assertTrue(
            error.message?.contains(expectedMessage) == true,
            "Expected '${error.message}' to contain '$expectedMessage'",
        )
    }
}

private val directYamlFixture =
    """
    openapi: 3.0.3
    info:
      title: Direct API
      version: 1.0.0
    paths:
      /pets:
        get:
          tags:
            - Pets
          responses:
            '204':
              description: No content.
      /metadata:
        parameters: []
    """.trimIndent()

private val directJsonFixture =
    """
    {
      "openapi": "3.0.3",
      "info": {"title": "Direct JSON API", "version": "1.0.0"},
      "paths": {}
    }
    """.trimIndent()

private val missingPathsYamlFixture =
    """
    openapi: 3.0.3
    info:
      title: Missing paths
      version: 1.0.0
    """.trimIndent()
