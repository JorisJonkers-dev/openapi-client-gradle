package dev.jorisjonkers.openapi.client

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class OpenApiUtilityTaskTest : OpenApiTestProjectFixture() {
    @Test
    fun `provenance banner task writes a generic banner without duplicating it`() {
        val input = writeSpec("generated/raw.ts", "export type Pet = { id: string }\n")
        val output = tempDir.resolve("generated/with-banner.ts")
        val task = provenanceBannerTask(input, output)

        task.applyBanner()
        task.inputFile.set(
            task.project.layout.projectDirectory
                .file(output.relativeToTempDir()),
        )
        task.applyBanner()

        val text = output.readText()
        assertTrue(text.startsWith("/**\n * AUTO-GENERATED."))
        assertEquals(1, Regex("AUTO-GENERATED").findAll(text).count())
        assertTrue(text.contains("export type Pet"))
    }

    @Test
    fun `drift check task compares generated artifacts exactly`() {
        val expected = writeSpec("generated/expected.ts", "same\n")
        val actual = writeSpec("generated/actual.ts", "same\n")
        val task = driftCheckTask(expected, actual)

        task.checkDrift()

        actual.writeText("different\n")
        assertGradleFails("contract drift") {
            task.checkDrift()
        }
    }

    @Test
    fun `openapi spec json rejects empty files and sorts nested objects in arrays`() {
        val empty = writeSpec("specs/empty-openapi-json.json", "")
        assertGradleFails("OpenAPI spec must not be empty") {
            OpenApiSpecJson.read(empty.toFile())
        }

        val source = writeSpec("specs/unsorted.json", unsortedJsonFixture)
        val target = tempDir.resolve("build/normalized/sorted.json")
        target.parent.createDirectories()

        OpenApiSpecJson.writeMinified(OpenApiSpecJson.read(source.toFile()), target.toFile())

        assertEquals("""{"a":{"c":3,"d":4},"z":[{"a":1,"b":2}]}""", target.readText())
    }

    private fun provenanceBannerTask(
        input: java.nio.file.Path,
        output: java.nio.file.Path,
    ): OpenApiProvenanceBannerTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        return project.tasks
            .register("banner", OpenApiProvenanceBannerTask::class.java) {
                inputFile.set(project.layout.projectDirectory.file(input.relativeToTempDir()))
                outputFile.set(project.layout.projectDirectory.file(output.relativeToTempDir()))
                bannerText.set(provenanceBannerText)
            }.get()
    }

    private fun driftCheckTask(
        expected: java.nio.file.Path,
        actual: java.nio.file.Path,
    ): OpenApiDriftCheckTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        return project.tasks
            .register("drift", OpenApiDriftCheckTask::class.java) {
                expectedFile.set(project.layout.projectDirectory.file(expected.relativeToTempDir()))
                actualFile.set(project.layout.projectDirectory.file(actual.relativeToTempDir()))
                failureMessage.set("contract drift")
            }.get()
    }
}

private val provenanceBannerText =
    """
    /**
     * AUTO-GENERATED.
     * Source: api/openapi.json
     * Regenerate with: ./gradlew generate
     */
    """.trimIndent()

private val unsortedJsonFixture =
    """
    {
      "z": [{"b": 2, "a": 1}],
      "a": {"d": 4, "c": 3}
    }
    """.trimIndent()
