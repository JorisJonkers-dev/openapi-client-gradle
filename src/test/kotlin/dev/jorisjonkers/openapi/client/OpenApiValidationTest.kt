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
            validationConfiguration(
                specFile = yaml,
                selection = validSelection.copy(apis = listOf("Pets")),
                mappings =
                    validMappings.copy(
                        schemaMappings = mapOf("PetEnvelope" to "com.example.PetEnvelope"),
                        typeMappings = mapOf("uuid" to "java.util.UUID"),
                    ),
            ),
        )

        val json = writeSpec("specs/direct.json", directJsonFixture)
        validateConfiguration(
            validationConfiguration(specFile = json, selection = validSelection.copy(apis = emptyList())),
        )
    }

    @Test
    fun `rejects invalid direct configuration`() {
        writeResource("specs/sample.yml", tempDir.resolve("specs/sample.yml"))
        val validSpec = tempDir.resolve("specs/sample.yml")

        assertValidationFails(
            "openApiClient.apiPackage is required",
            validationConfiguration(specFile = validSpec, packages = validPackages.copy(apiPackage = "")),
        )
        assertValidationFails(
            "openApiClient.apis must not contain blank values",
            validationConfiguration(specFile = validSpec, selection = validSelection.copy(apis = listOf(" "))),
        )
        assertValidationFails(
            "openApiClient.typeMappings must not contain blank keys or values",
            validationConfiguration(
                specFile = validSpec,
                mappings =
                    validMappings.copy(
                        typeMappings =
                            mapOf(
                                "uuid" to "",
                            ),
                    ),
            ),
        )
        assertValidationFails(
            "openApiClient.specPath is required",
            validationConfiguration(specPath = "specs/missing.yml", specFile = null),
        )
        assertValidationFails(
            "OpenAPI spec file does not exist",
            validationConfiguration(specFile = tempDir.resolve("specs/missing.yml")),
        )
        assertValidationFails("OpenAPI spec file is not readable", validationConfiguration(specFile = tempDir))

        assertValidationFails(
            "OpenAPI spec file is empty",
            validationConfiguration(specFile = writeSpec("specs/empty.yml", "")),
        )
        assertValidationFails(
            "OpenAPI spec must be valid JSON or YAML",
            validationConfiguration(specFile = writeSpec("specs/broken.json", "{")),
        )
        assertValidationFails(
            "OpenAPI spec must contain a 'paths' object",
            validationConfiguration(specFile = writeSpec("specs/missing-paths.yml", missingPathsYamlFixture)),
        )

        writeResource("specs/untagged.yml", tempDir.resolve("specs/untagged.yml"))
        assertValidationFails(
            "Available tags: (none)",
            validationConfiguration(
                specFile = tempDir.resolve("specs/untagged.yml"),
                selection = validSelection.copy(apis = listOf("Missing")),
            ),
        )
    }

    private fun validationConfiguration(
        specFile: Path?,
        specPath: String? = specFile?.toString() ?: "spec.yml",
        packages: OpenApiPackageConfiguration = validPackages,
        selection: OpenApiSelectionConfiguration = validSelection,
        mappings: OpenApiMappingConfiguration = validMappings,
    ): OpenApiClientValidationConfiguration =
        OpenApiClientValidationConfiguration(
            spec =
                OpenApiSpecConfiguration(
                    path = specPath,
                    file = specFile?.toFile(),
                ),
            packages = packages,
            generator = validGenerator,
            selection = selection,
            mappings = mappings,
        )

    private fun validateConfiguration(configuration: OpenApiClientValidationConfiguration) {
        OpenApiClientConfigurationValidator.validate(
            configuration,
        )
    }

    private fun assertValidationFails(
        expectedMessage: String,
        configuration: OpenApiClientValidationConfiguration,
    ) {
        val error =
            assertThrows(GradleException::class.java) {
                validateConfiguration(configuration)
            }
        assertTrue(
            error.message?.contains(expectedMessage) == true,
            "Expected '${error.message}' to contain '$expectedMessage'",
        )
    }
}

private val validPackages =
    OpenApiPackageConfiguration(
        apiPackage = "com.example.pet.api",
        modelPackage = "com.example.pet.model",
        packageName = "com.example.pet.invoker",
    )

private val validGenerator =
    OpenApiGeneratorSettings(
        codegen =
            OpenApiCodegenConfiguration(
                generatorName = "java",
                library = "restclient",
                sourceFolder = "src/main/java",
            ),
        serialization =
            OpenApiSerializationConfiguration(
                serializationLibrary = "jackson",
                dateLibrary = "java8",
                enumPropertyNaming = "MACRO_CASE",
            ),
    )

private val validSelection =
    OpenApiSelectionConfiguration(
        apis = listOf("Pets"),
        models = emptyList(),
        supportingFiles = emptyList(),
    )

private val validMappings =
    OpenApiMappingConfiguration(
        schemaMappings = emptyMap(),
        typeMappings = emptyMap(),
        inlineSchemaOptions = mapOf("RESOLVE_INLINE_ENUMS" to "true"),
        configOptions = emptyMap(),
    )

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
