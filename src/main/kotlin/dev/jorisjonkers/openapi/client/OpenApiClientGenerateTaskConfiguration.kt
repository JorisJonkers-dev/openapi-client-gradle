package dev.jorisjonkers.openapi.client

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.io.File

internal fun Project.generatedSourceDirectory(
    generatedRoot: Provider<Directory>,
    extension: OpenApiClientExtension,
): Provider<Directory> =
    providers.provider {
        generatedRoot.get().dir(extension.sourceFolder.get())
    }

internal fun Project.registerGenerateTask(
    extension: OpenApiClientExtension,
    generatedRoot: Provider<Directory>,
): TaskProvider<GenerateTask> =
    tasks.register(
        "generate",
        GenerateTask::class.java,
        Action<GenerateTask> {
            group = "openapi"
            description = "Generates the JVM client from the configured OpenAPI spec."
            configureGenerator(this@registerGenerateTask, extension, generatedRoot)
            configureGenerateInputs(this@registerGenerateTask, extension, generatedRoot)
            configureValidation(this@registerGenerateTask, extension)
            configureKotlinSerializerPatch(extension, generatedRoot)
        },
    )

internal fun GenerateTask.configureGenerator(
    project: Project,
    extension: OpenApiClientExtension,
    generatedRoot: Provider<Directory>,
) {
    validateSpec.set(false)
    generatorName.set(extension.generatorName)
    library.set(extension.library)
    inputSpec.set(project.openApiInputSpecFile(extension))
    outputDir.set(generatedRoot)
    configOptions.set(project.openApiConfigOptions(extension))
    apiPackage.set(extension.apiPackage.orElse(""))
    modelPackage.set(extension.modelPackage.orElse(""))
    packageName.set(extension.packageName.orElse(""))
    generateModelTests.set(extension.generateModelTests)
    generateApiTests.set(extension.generateApiTests)
    generateApiDocumentation.set(extension.generateApiDocumentation)
    generateModelDocumentation.set(extension.generateModelDocumentation)
    inlineSchemaOptions.set(extension.inlineSchemaOptions)
    schemaMappings.set(extension.schemaMappings)
    typeMappings.set(extension.typeMappings)
    globalProperties.set(project.openApiGlobalProperties(extension))
}

internal fun Project.openApiInputSpecFile(extension: OpenApiClientExtension): Provider<RegularFile> =
    layout.file(
        providers.provider {
            val configuredSpec = configuredSpecFile(extension)
            if (configuredSpec != null && configuredSpec.exists() && configuredSpec.isFile) {
                configuredSpec
            } else {
                buildFile
            }
        },
    )

internal fun Project.openApiConfigOptions(extension: OpenApiClientExtension): Provider<Map<String, String>> =
    providers.provider {
        linkedMapOf(
            "sourceFolder" to extension.sourceFolder.get(),
            "serializationLibrary" to extension.serializationLibrary.get(),
            "dateLibrary" to extension.dateLibrary.get(),
            "useJakartaEe" to extension.useJakartaEe.get().toString(),
            "useBeanValidation" to extension.useBeanValidation.get().toString(),
            "useJackson3" to extension.useJackson3.get().toString(),
            "useSpringBoot4" to extension.useSpringBoot4.get().toString(),
            "enumPropertyNaming" to extension.enumPropertyNaming.get(),
        ).apply {
            putAll(extension.configOptions.get())
        }
    }

internal fun Project.openApiGlobalProperties(extension: OpenApiClientExtension): Provider<Map<String, String>> =
    providers.provider {
        mapOf(
            "apis" to extension.apis.get().joinToString(","),
            "models" to extension.models.get().joinToString(","),
            "supportingFiles" to extension.supportingFiles.get().joinToString(","),
        )
    }

internal fun GenerateTask.configureGenerateInputs(
    project: Project,
    extension: OpenApiClientExtension,
    generatedRoot: Provider<Directory>,
) {
    inputs
        .files(project.configuredExistingSpecFiles(extension))
        .withPropertyName("specFile")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("specPath", extension.specPath.orElse(""))
    inputs.property("apiPackage", extension.apiPackage.orElse(""))
    inputs.property("modelPackage", extension.modelPackage.orElse(""))
    inputs.property("packageName", extension.packageName.orElse(""))
    inputs.property("generatorName", extension.generatorName)
    inputs.property("library", extension.library)
    inputs.property("sourceFolder", extension.sourceFolder)
    inputs.property("serializationLibrary", extension.serializationLibrary)
    inputs.property("dateLibrary", extension.dateLibrary)
    inputs.property("useJakartaEe", extension.useJakartaEe)
    inputs.property("useBeanValidation", extension.useBeanValidation)
    inputs.property("useJackson3", extension.useJackson3)
    inputs.property("useSpringBoot4", extension.useSpringBoot4)
    inputs.property("enumPropertyNaming", extension.enumPropertyNaming)
    inputs.property("generateModelTests", extension.generateModelTests)
    inputs.property("generateApiTests", extension.generateApiTests)
    inputs.property("generateApiDocumentation", extension.generateApiDocumentation)
    inputs.property("generateModelDocumentation", extension.generateModelDocumentation)
    inputs.property("apis", extension.apis)
    inputs.property("models", extension.models)
    inputs.property("supportingFiles", extension.supportingFiles)
    inputs.property("schemaMappings", extension.schemaMappings)
    inputs.property("typeMappings", extension.typeMappings)
    inputs.property("inlineSchemaOptions", extension.inlineSchemaOptions)
    inputs.property("configOptions", extension.configOptions)
    outputs.dir(generatedRoot)
    outputs.cacheIf { true }
}

internal fun Project.configuredExistingSpecFiles(extension: OpenApiClientExtension): Provider<List<File>> =
    providers.provider {
        configuredSpecFile(extension)
            ?.takeIf { it.exists() && it.isFile }
            ?.let { listOf(it) }
            .orEmpty()
    }

internal fun Project.configuredSpecFile(extension: OpenApiClientExtension): File? =
    extension.specPath.orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { resolveSpecFile(rootProject.projectDir, it) }

internal fun GenerateTask.configureValidation(
    project: Project,
    extension: OpenApiClientExtension,
) {
    doFirst {
        OpenApiClientConfigurationValidator.validate(
            OpenApiClientValidationConfiguration(
                spec =
                    OpenApiSpecConfiguration(
                        path = extension.specPath.orNull,
                        file = project.configuredSpecFile(extension),
                    ),
                packages =
                    OpenApiPackageConfiguration(
                        apiPackage = extension.apiPackage.orNull,
                        modelPackage = extension.modelPackage.orNull,
                        packageName = extension.packageName.orNull,
                    ),
                generator =
                    OpenApiGeneratorSettings(
                        codegen =
                            OpenApiCodegenConfiguration(
                                generatorName = extension.generatorName.orNull,
                                library = extension.library.orNull,
                                sourceFolder = extension.sourceFolder.orNull,
                            ),
                        serialization =
                            OpenApiSerializationConfiguration(
                                serializationLibrary = extension.serializationLibrary.orNull,
                                dateLibrary = extension.dateLibrary.orNull,
                                enumPropertyNaming = extension.enumPropertyNaming.orNull,
                            ),
                    ),
                selection =
                    OpenApiSelectionConfiguration(
                        apis = extension.apis.getOrElse(emptyList()),
                        models = extension.models.getOrElse(emptyList()),
                        supportingFiles = extension.supportingFiles.getOrElse(emptyList()),
                    ),
                mappings =
                    OpenApiMappingConfiguration(
                        schemaMappings = extension.schemaMappings.getOrElse(emptyMap()),
                        typeMappings = extension.typeMappings.getOrElse(emptyMap()),
                        inlineSchemaOptions = extension.inlineSchemaOptions.getOrElse(emptyMap()),
                        configOptions = extension.configOptions.getOrElse(emptyMap()),
                    ),
            ),
        )
    }
}

internal fun GenerateTask.configureKotlinSerializerPatch(
    extension: OpenApiClientExtension,
    generatedRoot: Provider<Directory>,
) {
    doLast {
        if (extension.generatorName.get() == "kotlin" && extension.serializationLibrary.get() == "jackson") {
            patchGeneratedKotlinJacksonSerializers(
                generatedRoot.get().asFile,
                extension.sourceFolder.get(),
            )
        }
    }
}
