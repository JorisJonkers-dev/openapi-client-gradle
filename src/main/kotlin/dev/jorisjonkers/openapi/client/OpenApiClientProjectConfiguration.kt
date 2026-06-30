package dev.jorisjonkers.openapi.client

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

internal fun Project.applyOpenApiBasePlugins() {
    pluginManager.apply("java-library")
    pluginManager.apply("org.jetbrains.kotlin.jvm")
    pluginManager.apply("org.openapi.generator")
}

internal fun Project.createOpenApiClientExtension(): OpenApiClientExtension =
    extensions.create("openApiClient", OpenApiClientExtension::class.java)

internal fun Project.createExternalSpecsExtension(): OpenApiExternalSpecsExtension =
    extensions
        .create("openApiExternalSpecs", OpenApiExternalSpecsExtension::class.java)
        .also { extension ->
            extension.specDirectory.convention(layout.projectDirectory.dir("openapi-specs"))
        }

internal fun Project.registerGenerateOpenApiClientAlias(generate: TaskProvider<GenerateTask>) {
    tasks.register(
        "generateOpenApiClient",
        Action<Task> {
            group = "openapi"
            description = "Alias for generate."
            dependsOn(generate)
        },
    )
}

internal fun Project.registerExternalSpecTasks(externalSpecs: OpenApiExternalSpecsExtension) {
    tasks.register(
        "downloadExternalOpenApiSpecs",
        DownloadExternalOpenApiSpecsTask::class.java,
        Action<DownloadExternalOpenApiSpecsTask> {
            group = "openapi"
            description = "Downloads configured external OpenAPI specs to the central spec directory."
            specDirectory.set(externalSpecs.specDirectory)
            configuredSpecs = externalSpecs.specs
        },
    )
    registerNormalizeExternalSpecsTask(externalSpecs)
    externalSpecs.filters.all {
        registerFilterSpecTask(this)
    }
}

internal fun Project.registerNormalizeExternalSpecsTask(externalSpecs: OpenApiExternalSpecsExtension) {
    tasks.register(
        "normalizeExternalOpenApiSpecs",
        NormalizeExternalOpenApiSpecsTask::class.java,
        Action<NormalizeExternalOpenApiSpecsTask> {
            group = "openapi"
            description = "Normalizes configured external OpenAPI specs to deterministic minified JSON."
            dependsOn("downloadExternalOpenApiSpecs")
            specDirectory.set(externalSpecs.specDirectory)
            configuredSpecs = externalSpecs.specs
        },
    )
}

internal fun Project.registerFilterSpecTask(filterSpec: ExternalOpenApiSpecFilter) {
    tasks.register(
        filterOpenApiSpecTaskName(filterSpec.name),
        OpenApiFilterSpecTask::class.java,
        Action<OpenApiFilterSpecTask> {
            group = "openapi"
            description = "Filters the '${filterSpec.name}' OpenAPI spec."
            inputSpec.set(
                layout.file(
                    providers.provider { resolveSpecFile(rootProject.projectDir, filterSpec.inputSpec.get()) },
                ),
            )
            outputSpec.set(
                layout.file(
                    providers.provider { resolveSpecFile(rootProject.projectDir, filterSpec.outputSpec.get()) },
                ),
            )
            allowedOperations.set(filterSpec.allowedOperations)
            injectedTag.set(filterSpec.injectedTag)
            pruneUnreachableSchemas.set(filterSpec.pruneUnreachableSchemas)
            rewriteNullTypes.set(filterSpec.rewriteNullTypes)
            collapseRedundantEnumAllOf.set(filterSpec.collapseRedundantEnumAllOf)
        },
    )
}

internal fun Project.configureGeneratedSources(generatedSourceDir: Provider<Directory>) {
    extensions.configure(
        JavaPluginExtension::class.java,
        Action<JavaPluginExtension> {
            sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).java.srcDir(generatedSourceDir)
        },
    )
    extensions.configure(
        KotlinJvmProjectExtension::class.java,
        Action<KotlinJvmProjectExtension> {
            sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).kotlin.srcDir(generatedSourceDir)
        },
    )
}

internal fun Project.configureJavaToolchain(extension: OpenApiClientExtension) {
    afterEvaluate(
        Action<Project> {
            val configuredJava = extension.javaLanguageVersion.orNull
            if (configuredJava != null) {
                extensions.configure(
                    JavaPluginExtension::class.java,
                    Action<JavaPluginExtension> {
                        toolchain.languageVersion.set(
                            org.gradle.jvm.toolchain.JavaLanguageVersion
                                .of(configuredJava),
                        )
                    },
                )
            }
        },
    )
}

internal fun Project.configureGeneratedSourceDependencies(generate: TaskProvider<GenerateTask>) {
    tasks.withType(JavaCompile::class.java).configureEach(
        Action<JavaCompile> {
            dependsOn(generate)
        },
    )
    tasks.matching { it.name == "compileKotlin" }.configureEach {
        dependsOn(generate)
    }
}

internal fun Project.addGeneratedClientDependencies(extension: OpenApiClientExtension) {
    dependencies.addProvider(
        "implementation",
        extension.springVersion.map { "org.springframework:spring-web:$it" },
    )
    dependencies.addProvider(
        "implementation",
        extension.springVersion.map { "org.springframework:spring-context:$it" },
    )
    dependencies.addProvider(
        "implementation",
        extension.jacksonBomVersion.map { dependencies.platform("tools.jackson:jackson-bom:$it") },
    )
    dependencies.addProvider(
        "implementation",
        extension.jackson2Version.map { dependencies.platform("com.fasterxml.jackson:jackson-bom:$it") },
    )
    dependencies.addProvider(
        "implementation",
        extension.jacksonAnnotationsVersion.map { "com.fasterxml.jackson.core:jackson-annotations:$it" },
    )
    dependencies.addProvider(
        "implementation",
        extension.useJackson3.flatMap { useJackson3 ->
            if (useJackson3) {
                providers.provider { "tools.jackson.core:jackson-core" }
            } else {
                extension.jackson2Version.map { "com.fasterxml.jackson.core:jackson-core:$it" }
            }
        },
    )
    dependencies.addProvider(
        "implementation",
        extension.useJackson3.flatMap { useJackson3 ->
            if (useJackson3) {
                providers.provider { "tools.jackson.core:jackson-databind" }
            } else {
                extension.jackson2Version.map { "com.fasterxml.jackson.core:jackson-databind:$it" }
            }
        },
    )
    dependencies.addProvider(
        "implementation",
        extension.jackson2Version.map { "com.fasterxml.jackson.module:jackson-module-kotlin:$it" },
    )
    dependencies.addProvider(
        "implementation",
        extension.jacksonDatabindNullableVersion.map { "org.openapitools:jackson-databind-nullable:$it" },
    )
    dependencies.addProvider(
        "compileOnly",
        extension.jakartaValidationVersion.map { "jakarta.validation:jakarta.validation-api:$it" },
    )
    dependencies.addProvider(
        "compileOnly",
        extension.jakartaAnnotationVersion.map { "jakarta.annotation:jakarta.annotation-api:$it" },
    )
}
