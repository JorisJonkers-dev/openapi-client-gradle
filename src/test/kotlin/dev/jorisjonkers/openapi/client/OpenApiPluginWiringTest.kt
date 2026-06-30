package dev.jorisjonkers.openapi.client

import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

class OpenApiPluginWiringTest : OpenApiTestProjectFixture() {
    @Test
    fun `applies plugin conventions to a Gradle project`() {
        val project = projectWithPlugin()
        val extension = project.extensions.getByType(OpenApiClientExtension::class.java)
        configureJavaClient(extension)

        val generate = project.tasks.named("generate", GenerateTask::class.java).get()

        assertGenerateTaskConventions(generate)
        assertGeneratedSourceWiring(project, generate)
        assertRuntimeDependencies(project)
    }

    @Test
    fun `allows Java generator convention options to be overridden`() {
        val project = projectWithPlugin()
        val extension = project.extensions.getByType(OpenApiClientExtension::class.java)
        configureJavaOverrides(extension)

        val generate = project.tasks.named("generate", GenerateTask::class.java).get()

        assertEquals("webclient", generate.library.get())
        assertEquals(true, generate.generateModelTests.get())
        assertEquals(true, generate.generateApiTests.get())
        assertEquals(false, generate.generateApiDocumentation.get())
        assertEquals(false, generate.generateModelDocumentation.get())
        assertEquals(
            mapOf("apis" to "Pets", "models" to "Pet", "supportingFiles" to "ApiClient.java"),
            generate.globalProperties.get(),
        )
        assertEquals(mapOf("RESOLVE_INLINE_ENUMS" to "false"), generate.inlineSchemaOptions.get())
        assertEquals(javaOverrideConfigOptions, generate.configOptions.get())
    }

    @Test
    fun `kotlin spring rest client mode configures generator conventions`() {
        val project = projectWithPlugin()
        val extension = project.extensions.getByType(OpenApiClientExtension::class.java)
        extension.useKotlinSpringRestClient()

        val generate = project.tasks.named("generate", GenerateTask::class.java).get()

        assertEquals("kotlin", generate.generatorName.get())
        assertEquals("jvm-spring-restclient", generate.library.get())
        assertEquals("src/main/kotlin", generate.configOptions.get()["sourceFolder"])
        assertEquals("jackson", generate.configOptions.get()["serializationLibrary"])
        assertEquals("false", generate.configOptions.get()["useJackson3"])
        assertEquals("UPPERCASE", generate.configOptions.get()["enumPropertyNaming"])
        assertEquals("true", generate.configOptions.get()["useSpringBoot3"])
        assertEquals("6.2.8", extension.springVersion.get())
    }

    @Test
    fun `uses build file as inert generate input until a spec is configured`() {
        writeBuildFile("")
        val project =
            ProjectBuilder
                .builder()
                .withProjectDir(tempDir.toFile())
                .build()

        OpenApiClientPlugin().apply(project)

        val generate = project.tasks.named("generate", GenerateTask::class.java).get()
        assertEquals(project.buildFile, generate.inputSpec.get().asFile)
        assertTrue(
            generate.inputs.files.files
                .contains(project.buildFile),
        )
    }

    private fun projectWithPlugin(): org.gradle.api.Project {
        writeBuildFile("")
        writeResource("specs/sample.yml", tempDir.resolve("specs/sample.yml"))
        val project =
            ProjectBuilder
                .builder()
                .withProjectDir(tempDir.toFile())
                .build()
        OpenApiClientPlugin().apply(project)
        return project
    }

    private fun configureJavaClient(extension: OpenApiClientExtension) {
        extension.specPath.set("specs/sample.yml")
        extension.apiPackage.set("com.example.pet.api")
        extension.modelPackage.set("com.example.pet.model")
        extension.packageName.set("com.example.pet.invoker")
        extension.apis.set(listOf("Pets"))
        extension.schemaMappings.set(mapOf("UnusedInlineSchema" to "java.lang.Object"))
        extension.typeMappings.set(mapOf("unused-format" to "java.lang.String"))
        extension.generatorName.set("java")
        extension.library.set("restclient")
        extension.sourceFolder.set("src/main/java")
        extension.serializationLibrary.set("jackson")
        extension.dateLibrary.set("java8")
        extension.useJakartaEe.set(true)
        extension.useBeanValidation.set(true)
        extension.useJackson3.set(true)
        extension.useSpringBoot4.set(true)
        extension.enumPropertyNaming.set("MACRO_CASE")
    }

    private fun assertGenerateTaskConventions(generate: GenerateTask) {
        assertEquals("openapi", generate.group)
        assertFalse(generate.validateSpec.get())
        assertEquals("java", generate.generatorName.get())
        assertEquals("restclient", generate.library.get())
        assertEquals(tempDir.resolve("specs/sample.yml").toFile(), generate.inputSpec.get().asFile)
        assertEquals(tempDir.resolve("build/generated/openapi").toFile(), generate.outputDir.get().asFile)
        assertEquals("com.example.pet.api", generate.apiPackage.get())
        assertEquals("com.example.pet.model", generate.modelPackage.get())
        assertEquals("com.example.pet.invoker", generate.packageName.get())
        assertEquals(mapOf("apis" to "Pets", "models" to "", "supportingFiles" to ""), generate.globalProperties.get())
        assertEquals(mapOf("UnusedInlineSchema" to "java.lang.Object"), generate.schemaMappings.get())
        assertEquals(mapOf("unused-format" to "java.lang.String"), generate.typeMappings.get())
        assertEquals(javaConventionConfigOptions, generate.configOptions.get())
    }

    private fun assertGeneratedSourceWiring(
        project: org.gradle.api.Project,
        generate: GenerateTask,
    ) {
        val alias = project.tasks.named("generateOpenApiClient").get()
        assertTrue(alias.taskDependencies.getDependencies(alias).contains(generate))
        assertEquals(
            tempDir.resolve("openapi-specs").toFile(),
            project.extensions
                .getByType(OpenApiExternalSpecsExtension::class.java)
                .specDirectory
                .get()
                .asFile,
        )
        assertTrue(project.tasks.names.contains("downloadExternalOpenApiSpecs"))
        assertTrue(project.tasks.names.contains("normalizeExternalOpenApiSpecs"))

        val compileJava = project.tasks.named("compileJava", JavaCompile::class.java).get()
        assertTrue(compileJava.taskDependencies.getDependencies(compileJava).contains(generate))

        val java = project.extensions.getByType(JavaPluginExtension::class.java)
        assertTrue(
            java.sourceSets.getByName("main").java.srcDirs.any {
                it.toPath().endsWith("build/generated/openapi/src/main/java")
            },
        )
    }

    private fun assertRuntimeDependencies(project: org.gradle.api.Project) {
        val implementation = project.configurations.getByName("implementation").dependencies
        assertTrue(implementation.any { it.group == "org.springframework" && it.name == "spring-web" })
        assertTrue(implementation.any { it.group == "tools.jackson" && it.name == "jackson-bom" })
    }

    private fun configureJavaOverrides(extension: OpenApiClientExtension) {
        extension.specPath.set("specs/sample.yml")
        extension.apiPackage.set("com.example.pet.api")
        extension.modelPackage.set("com.example.pet.model")
        extension.packageName.set("com.example.pet.invoker")
        extension.generatorName.set("java")
        extension.library.set("webclient")
        extension.sourceFolder.set("generated/java")
        extension.serializationLibrary.set("gson")
        extension.dateLibrary.set("legacy")
        extension.useJakartaEe.set(false)
        extension.useBeanValidation.set(false)
        extension.useJackson3.set(false)
        extension.useSpringBoot4.set(false)
        extension.enumPropertyNaming.set("original")
        configureGeneratedArtifacts(extension)
    }

    private fun configureGeneratedArtifacts(extension: OpenApiClientExtension) {
        extension.generateModelTests.set(true)
        extension.generateApiTests.set(true)
        extension.generateApiDocumentation.set(false)
        extension.generateModelDocumentation.set(false)
        extension.apis.set(listOf("Pets"))
        extension.models.set(listOf("Pet"))
        extension.supportingFiles.set(listOf("ApiClient.java"))
        extension.inlineSchemaOptions.set(mapOf("RESOLVE_INLINE_ENUMS" to "false"))
        extension.configOptions.set(mapOf("hideGenerationTimestamp" to "true"))
    }
}

private val javaConventionConfigOptions =
    mapOf(
        "sourceFolder" to "src/main/java",
        "serializationLibrary" to "jackson",
        "dateLibrary" to "java8",
        "useJakartaEe" to "true",
        "useBeanValidation" to "true",
        "useJackson3" to "true",
        "useSpringBoot4" to "true",
        "enumPropertyNaming" to "MACRO_CASE",
    )

private val javaOverrideConfigOptions =
    mapOf(
        "sourceFolder" to "generated/java",
        "serializationLibrary" to "gson",
        "dateLibrary" to "legacy",
        "useJakartaEe" to "false",
        "useBeanValidation" to "false",
        "useJackson3" to "false",
        "useSpringBoot4" to "false",
        "enumPropertyNaming" to "original",
        "hideGenerationTimestamp" to "true",
    )
