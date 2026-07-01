package dev.jorisjonkers.openapi.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

open class OpenApiTestProjectFixture {
    protected val jsonMapper = ObjectMapper()

    @TempDir
    lateinit var tempDir: Path

    protected fun writeSettings() {
        tempDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"consumer\"")
    }

    protected fun writeBuildFile(contents: String) {
        tempDir.resolve("build.gradle.kts").writeText(contents)
    }

    protected fun writeResource(
        resourcePath: String,
        target: Path,
    ) {
        target.parent.createDirectories()
        val resource =
            checkNotNull(javaClass.classLoader.getResource(resourcePath)) {
                "Missing test resource: $resourcePath"
            }
        target.writeText(resource.readText())
    }

    protected fun writeSpec(
        relativePath: String,
        contents: String,
    ): Path {
        val target = tempDir.resolve(relativePath)
        target.parent.createDirectories()
        target.writeText(contents)
        return target
    }

    protected fun runGradle(vararg arguments: String): BuildResult =
        GradleRunner
            .create()
            .withProjectDir(tempDir.toFile())
            .withArguments(arguments.toList() + "--stacktrace")
            .withPluginClasspath()
            .build()

    protected fun runGradleAndFail(vararg arguments: String): BuildResult =
        GradleRunner
            .create()
            .withProjectDir(tempDir.toFile())
            .withArguments(arguments.toList() + "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()

    protected fun buildFileFor(
        specPath: String,
        apis: List<String> = listOf("Pets"),
    ): String =
        """
        plugins {
            id("dev.jorisjonkers.openapi-client")
        }

        openApiClient {
            specPath.set("$specPath")
            apiPackage.set("com.example.pet.api")
            modelPackage.set("com.example.pet.model")
            packageName.set("com.example.pet.invoker")
            apis.set(listOf(${apis.joinToString { it.toKotlinString() }}))
        }
        """.trimIndent()

    protected fun hasKotlinSource(directory: Path): Boolean =
        Files.exists(directory) &&
            Files.walk(directory).use { paths ->
                paths.anyMatch { path -> path.fileName.toString().endsWith(".kt") }
            }

    protected fun generatedSerializerText(): String =
        Files
            .walk(tempDir.resolve("build/generated/openapi/src/main/kotlin"))
            .use { paths ->
                paths
                    .filter { path -> path.fileName.toString() == "Serializer.kt" }
                    .findFirst()
                    .orElseThrow { AssertionError("Generated Serializer.kt was not found") }
                    .readText()
            }

    protected fun registerFilterTask(
        inputSpec: Path,
        outputRelativePath: String,
        allowedOperations: Map<String, List<String>>,
        injectedTag: String = "Fixture",
        configure: OpenApiFilterSpecTask.() -> Unit = {},
    ): OpenApiFilterSpecTask {
        val project =
            ProjectBuilder
                .builder()
                .withProjectDir(tempDir.toFile())
                .build()

        return project.tasks
            .register("filterSpec", OpenApiFilterSpecTask::class.java) {
                this.inputSpec.set(project.layout.projectDirectory.file(inputSpec.relativeToTempDir()))
                outputSpec.set(project.layout.projectDirectory.file(outputRelativePath))
                this.allowedOperations.set(allowedOperations)
                this.injectedTag.set(injectedTag)
                configure()
            }.get()
    }

    protected fun assertGradleFails(
        expectedMessage: String,
        action: () -> Unit,
    ) {
        val error =
            assertThrows(GradleException::class.java) {
                action()
            }
        assertTrue(
            error.message?.contains(expectedMessage) == true,
            "Expected '${error.message}' to contain '$expectedMessage'",
        )
    }

    protected fun externalSpecHarness(): ExternalSpecHarness {
        val project =
            ProjectBuilder
                .builder()
                .withProjectDir(tempDir.toFile())
                .build()
        val extension = project.objects.newInstance(OpenApiExternalSpecsExtension::class.java)
        extension.specDirectory.set(project.layout.projectDirectory.dir("openapi-specs"))

        return ExternalSpecHarness(
            extension = extension,
            download = project.registerDownloadTask(extension),
            normalize = project.registerNormalizeTask(extension),
        )
    }

    protected fun Path.relativeToTempDir(): String = tempDir.relativize(this).toString().replace("\\", "/")
}

data class ExternalSpecHarness(
    val extension: OpenApiExternalSpecsExtension,
    val download: DownloadExternalOpenApiSpecsTask,
    val normalize: NormalizeExternalOpenApiSpecsTask,
)

private fun org.gradle.api.Project.registerDownloadTask(
    extension: OpenApiExternalSpecsExtension,
): DownloadExternalOpenApiSpecsTask =
    tasks
        .register("downloadExternalOpenApiSpecs", DownloadExternalOpenApiSpecsTask::class.java) {
            specDirectory.set(extension.specDirectory)
            configuredSpecs = extension.specs
        }.get()

private fun org.gradle.api.Project.registerNormalizeTask(
    extension: OpenApiExternalSpecsExtension,
): NormalizeExternalOpenApiSpecsTask =
    tasks
        .register("normalizeExternalOpenApiSpecs", NormalizeExternalOpenApiSpecsTask::class.java) {
            specDirectory.set(extension.specDirectory)
            configuredSpecs = extension.specs
        }.get()

internal fun String.toKotlinString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
