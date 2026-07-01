package dev.jorisjonkers.openapi.client

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class OpenApiClientGenerationTest : OpenApiTestProjectFixture() {
    @Test
    fun `generates and compiles yaml client with selected api and mappings`() {
        writeSettings()
        writeResource("specs/sample.yml", tempDir.resolve("specs/sample.yml"))
        writeBuildFile(yamlClientBuildFile())

        val first = runGradle("build")

        assertEquals(TaskOutcome.SUCCESS, first.task(":generate")?.outcome)
        assertTrue(
            Files.exists(tempDir.resolve("build/generated/openapi/src/main/java/com/example/pet/api/PetsApi.java")),
            "generated API source should be part of the main source tree",
        )

        val second = runGradle("generate")

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generate")?.outcome)
    }

    @Test
    fun `kotlin spring rest client mode generates and compiles kotlin sources`() {
        writeSettings()
        writeResource("specs/sample.yml", tempDir.resolve("specs/sample.yml"))
        writeBuildFile(kotlinSpringRestClientBuildFile())

        val result = runGradle("build")

        assertEquals(TaskOutcome.SUCCESS, result.task(":generate")?.outcome)
        assertTrue(hasKotlinSource(tempDir.resolve("build/generated/openapi/src/main/kotlin")))

        val serializer = generatedSerializerText()
        assertFalse(serializer.contains(".setSerializationInclusion("))
        assertTrue(serializer.contains(".setDefaultPropertyInclusion(JsonInclude.Include.NON_ABSENT)"))
    }

    @Test
    fun `runs consumer owned preparation before validating and generating`() {
        writeSettings()
        writeBuildFile(preparedSpecBuildFile())

        val result = runGradle("build")

        assertEquals(TaskOutcome.SUCCESS, result.task(":prepareSpec")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generate")?.outcome)
        assertTrue(
            Files.exists(
                tempDir.resolve("build/generated/openapi/src/main/java/com/example/prepared/api/PreparedApi.java"),
            ),
            "prepared spec should generate the selected API",
        )
    }

    @Test
    fun `fails clearly when required configuration is missing`() {
        writeSettings()
        writeBuildFile(
            """
            plugins {
                id("dev.jorisjonkers.openapi-client")
            }
            """.trimIndent(),
        )

        val result = runGradleAndFail("generate")

        assertTrue(result.output.contains("openApiClient.specPath is required"))
    }

    @Test
    fun `fails clearly when spec path cannot be read`() {
        writeSettings()
        writeBuildFile(buildFileFor(specPath = "specs/missing.yml"))

        val result = runGradleAndFail("generate")

        assertTrue(result.output.contains("OpenAPI spec file does not exist"))
    }

    @Test
    fun `fails clearly when spec is not an openapi document`() {
        writeSettings()
        writeResource("specs/invalid-shape.yml", tempDir.resolve("specs/invalid-shape.yml"))
        writeBuildFile(buildFileFor(specPath = "specs/invalid-shape.yml"))

        val result = runGradleAndFail("generate")

        assertTrue(result.output.contains("OpenAPI spec must be an object with an 'openapi' field"))
    }

    @Test
    fun `fails clearly when selected api tag is absent`() {
        writeSettings()
        writeResource("specs/sample.yml", tempDir.resolve("specs/sample.yml"))
        writeBuildFile(buildFileFor(specPath = "specs/sample.yml", apis = listOf("Missing")))

        val result = runGradleAndFail("generate")

        assertTrue(result.output.contains("Selected OpenAPI API/tag(s) are not present"))
        assertTrue(result.output.contains("Missing"))
    }

    @Test
    fun `fails clearly when mappings contain blanks`() {
        writeSettings()
        writeResource("specs/sample.yml", tempDir.resolve("specs/sample.yml"))
        writeBuildFile(blankSchemaMappingBuildFile())

        val result = runGradleAndFail("generate")

        assertTrue(result.output.contains("openApiClient.schemaMappings must not contain blank keys or values"))
    }

    private fun yamlClientBuildFile(): String =
        """
        plugins {
            id("dev.jorisjonkers.openapi-client")
        }

        openApiClient {
            specPath.set("specs/sample.yml")
            apiPackage.set("com.example.pet.api")
            modelPackage.set("com.example.pet.model")
            packageName.set("com.example.pet.invoker")
            apis.set(listOf("Pets"))
            schemaMappings.set(mapOf("UnusedInlineSchema" to "java.lang.Object"))
            typeMappings.set(mapOf("unused-format" to "java.lang.String"))
        }
        """.trimIndent()

    private fun kotlinSpringRestClientBuildFile(): String =
        """
        plugins {
            id("dev.jorisjonkers.openapi-client")
        }

        openApiClient {
            useKotlinSpringRestClient()
            specPath.set("specs/sample.yml")
            apiPackage.set("com.example.pet.api")
            modelPackage.set("com.example.pet.model")
            packageName.set("com.example.pet.invoker")
            apis.set(listOf("Pets"))
        }
        """.trimIndent()

    private fun preparedSpecBuildFile(): String =
        """
        plugins {
            id("dev.jorisjonkers.openapi-client")
        }

        val preparedSpec = layout.buildDirectory.file("prepared/spec.yml")

        tasks.register("prepareSpec") {
            outputs.file(preparedSpec)
            doLast {
                val target = preparedSpec.get().asFile
                target.parentFile.mkdirs()
                target.writeText(
                    ${preparedSpecText.toKotlinString()}
                )
            }
        }

        tasks.named("generate") {
            dependsOn("prepareSpec")
        }

        openApiClient {
            specPath.set("build/prepared/spec.yml")
            apiPackage.set("com.example.prepared.api")
            modelPackage.set("com.example.prepared.model")
            packageName.set("com.example.prepared.invoker")
            apis.set(listOf("Prepared"))
        }
        """.trimIndent()

    private fun blankSchemaMappingBuildFile(): String =
        """
        plugins {
            id("dev.jorisjonkers.openapi-client")
        }

        openApiClient {
            specPath.set("specs/sample.yml")
            apiPackage.set("com.example.pet.api")
            modelPackage.set("com.example.pet.model")
            packageName.set("com.example.pet.invoker")
            schemaMappings.set(mapOf("" to "java.lang.String"))
        }
        """.trimIndent()
}
