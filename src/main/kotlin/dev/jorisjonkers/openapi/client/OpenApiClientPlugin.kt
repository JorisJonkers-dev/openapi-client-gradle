package dev.jorisjonkers.openapi.client

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.io.File
import javax.inject.Inject

abstract class OpenApiClientExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        val specPath: Property<String> = objects.property(String::class.java)
        val apiPackage: Property<String> = objects.property(String::class.java)
        val modelPackage: Property<String> = objects.property(String::class.java)
        val packageName: Property<String> = objects.property(String::class.java)
        val javaLanguageVersion: Property<Int> = objects.property(Int::class.javaObjectType)
        val generatorName: Property<String> = objects.property(String::class.java).convention("java")
        val library: Property<String> = objects.property(String::class.java).convention("restclient")
        val sourceFolder: Property<String> = objects.property(String::class.java).convention("src/main/java")
        val serializationLibrary: Property<String> = objects.property(String::class.java).convention("jackson")
        val dateLibrary: Property<String> = objects.property(String::class.java).convention("java8")
        val useJakartaEe: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(true)
        val useBeanValidation: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(true)
        val useJackson3: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(true)
        val useSpringBoot4: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(true)
        val enumPropertyNaming: Property<String> =
            objects
                .property(String::class.java)
                .convention("MACRO_CASE")
        val generateModelTests: Property<Boolean> =
            objects
                .property(Boolean::class.javaObjectType)
                .convention(false)
        val generateApiTests: Property<Boolean> =
            objects
                .property(Boolean::class.javaObjectType)
                .convention(false)
        val generateApiDocumentation: Property<Boolean> =
            objects
                .property(
                    Boolean::class.javaObjectType,
                ).convention(true)
        val generateModelDocumentation: Property<Boolean> =
            objects
                .property(
                    Boolean::class.javaObjectType,
                ).convention(true)
        val apis: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
        val models: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
        val supportingFiles: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
        val schemaMappings: MapProperty<String, String> =
            objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
        val typeMappings: MapProperty<String, String> =
            objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
        val inlineSchemaOptions: MapProperty<String, String> =
            objects.mapProperty(String::class.java, String::class.java).convention(
                mapOf(
                    "RESOLVE_INLINE_ENUMS" to "true",
                ),
            )
        val configOptions: MapProperty<String, String> =
            objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
        val springVersion: Property<String> = objects.property(String::class.java).convention("7.0.5")
        val jacksonBomVersion: Property<String> = objects.property(String::class.java).convention("3.1.0")
        val jackson2Version: Property<String> = objects.property(String::class.java).convention("2.21.3")
        val jacksonAnnotationsVersion: Property<String> = objects.property(String::class.java).convention("2.21")
        val jacksonDatabindNullableVersion: Property<String> =
            objects.property(String::class.java).convention("0.2.10")
        val jakartaValidationVersion: Property<String> = objects.property(String::class.java).convention("3.1.1")
        val jakartaAnnotationVersion: Property<String> = objects.property(String::class.java).convention("3.0.0")

        fun useKotlinSpringRestClient() {
            generatorName.set("kotlin")
            library.set("jvm-spring-restclient")
            sourceFolder.set("src/main/kotlin")
            serializationLibrary.set("jackson")
            dateLibrary.set("java8")
            useJackson3.set(false)
            enumPropertyNaming.set("UPPERCASE")
            springVersion.set("6.2.8")
            configOptions.put("useSpringBoot3", "true")
        }
    }

class OpenApiClientPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply("org.openapi.generator")

        val extension =
            project.extensions.create(
                "openApiClient",
                OpenApiClientExtension::class.java,
            )
        val externalSpecs =
            project.extensions.create(
                "openApiExternalSpecs",
                OpenApiExternalSpecsExtension::class.java,
            )
        externalSpecs.specDirectory.convention(project.layout.projectDirectory.dir("openapi-specs"))

        project.repositories.mavenCentral()
        project.addGeneratedClientDependencies(extension)

        val generatedRoot = project.layout.buildDirectory.dir("generated/openapi")
        val generatedSourceDir =
            project.providers.provider {
                generatedRoot.get().dir(extension.sourceFolder.get())
            }
        val taskInputSpecFile =
            project.providers.provider {
                val configuredSpec =
                    extension.specPath.orNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let { resolveSpecFile(project.rootProject.projectDir, it) }

                if (configuredSpec != null && configuredSpec.exists() && configuredSpec.isFile) {
                    configuredSpec
                } else {
                    project.buildFile
                }
            }
        val inputSpecFile = project.layout.file(taskInputSpecFile)

        val generate =
            project.tasks.register(
                "generate",
                GenerateTask::class.java,
                Action<GenerateTask> {
                    group = "openapi"
                    description = "Generates the JVM client from the configured OpenAPI spec."

                    validateSpec.set(false)
                    generatorName.set(extension.generatorName)
                    library.set(extension.library)
                    inputSpec.set(inputSpecFile)
                    outputDir.set(generatedRoot)

                    configOptions.set(
                        project.providers.provider {
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
                        },
                    )

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

                    globalProperties.set(
                        project.providers.provider {
                            mapOf(
                                "apis" to extension.apis.get().joinToString(","),
                                "models" to extension.models.get().joinToString(","),
                                "supportingFiles" to extension.supportingFiles.get().joinToString(","),
                            )
                        },
                    )

                    inputs
                        .files(
                            project.providers.provider {
                                extension.specPath.orNull
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { resolveSpecFile(project.rootProject.projectDir, it) }
                                    ?.takeIf { it.exists() && it.isFile }
                                    ?.let { listOf(it) }
                                    ?: emptyList()
                            },
                        ).withPropertyName("specFile")
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

                    doFirst {
                        OpenApiClientConfigurationValidator.validate(
                            specPath = extension.specPath.orNull,
                            specFile =
                                extension.specPath.orNull
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { resolveSpecFile(project.rootProject.projectDir, it) },
                            apiPackage = extension.apiPackage.orNull,
                            modelPackage = extension.modelPackage.orNull,
                            packageName = extension.packageName.orNull,
                            generatorName = extension.generatorName.orNull,
                            library = extension.library.orNull,
                            sourceFolder = extension.sourceFolder.orNull,
                            serializationLibrary = extension.serializationLibrary.orNull,
                            dateLibrary = extension.dateLibrary.orNull,
                            enumPropertyNaming = extension.enumPropertyNaming.orNull,
                            apis = extension.apis.getOrElse(emptyList()),
                            models = extension.models.getOrElse(emptyList()),
                            supportingFiles = extension.supportingFiles.getOrElse(emptyList()),
                            schemaMappings = extension.schemaMappings.getOrElse(emptyMap()),
                            typeMappings = extension.typeMappings.getOrElse(emptyMap()),
                            inlineSchemaOptions = extension.inlineSchemaOptions.getOrElse(emptyMap()),
                            configOptions = extension.configOptions.getOrElse(emptyMap()),
                        )
                    }
                },
            )

        project.tasks.register(
            "generateOpenApiClient",
            Action<Task> {
                group = "openapi"
                description = "Alias for generate."
                dependsOn(generate)
            },
        )

        project.tasks.register(
            "downloadExternalOpenApiSpecs",
            DownloadExternalOpenApiSpecsTask::class.java,
            Action<DownloadExternalOpenApiSpecsTask> {
                group = "openapi"
                description = "Downloads configured external OpenAPI specs to the central spec directory."
                specDirectory.set(externalSpecs.specDirectory)
                configuredSpecs = externalSpecs.specs
            },
        )

        project.tasks.register(
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

        externalSpecs.filters.all {
            val filterSpec = this
            project.tasks.register(
                filterOpenApiSpecTaskName(filterSpec.name),
                OpenApiFilterSpecTask::class.java,
                Action<OpenApiFilterSpecTask> {
                    group = "openapi"
                    description = "Filters the '${filterSpec.name}' OpenAPI spec."
                    inputSpec.set(
                        project.layout.file(
                            project.providers.provider {
                                resolveSpecFile(project.rootProject.projectDir, filterSpec.inputSpec.get())
                            },
                        ),
                    )
                    outputSpec.set(
                        project.layout.file(
                            project.providers.provider {
                                resolveSpecFile(project.rootProject.projectDir, filterSpec.outputSpec.get())
                            },
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

        project.extensions.configure(
            JavaPluginExtension::class.java,
            Action<JavaPluginExtension> {
                sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).java.srcDir(generatedSourceDir)
            },
        )

        project.extensions.configure(
            KotlinJvmProjectExtension::class.java,
            Action<KotlinJvmProjectExtension> {
                sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).kotlin.srcDir(generatedSourceDir)
            },
        )

        project.afterEvaluate(
            Action<Project> {
                val configuredJava = extension.javaLanguageVersion.orNull
                if (configuredJava != null) {
                    project.extensions.configure(
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

        project.tasks.withType(JavaCompile::class.java).configureEach(
            Action<JavaCompile> {
                dependsOn(generate)
            },
        )
        project.tasks.matching { it.name == "compileKotlin" }.configureEach {
            dependsOn(generate)
        }
    }

    private fun Project.addGeneratedClientDependencies(extension: OpenApiClientExtension) {
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
}

internal object OpenApiClientConfigurationValidator {
    private val jsonMapper = ObjectMapper()
    private val yamlMapper = ObjectMapper(YAMLFactory())
    private val httpMethods = setOf("get", "post", "put", "patch", "delete", "head", "options", "trace")

    fun validate(
        specPath: String?,
        specFile: File?,
        apiPackage: String?,
        modelPackage: String?,
        packageName: String?,
        generatorName: String? = "java",
        library: String? = "restclient",
        sourceFolder: String? = "src/main/java",
        serializationLibrary: String? = "jackson",
        dateLibrary: String? = "java8",
        enumPropertyNaming: String? = "MACRO_CASE",
        apis: List<String>,
        models: List<String> = emptyList(),
        supportingFiles: List<String> = emptyList(),
        schemaMappings: Map<String, String>,
        typeMappings: Map<String, String>,
        inlineSchemaOptions: Map<String, String> = mapOf("RESOLVE_INLINE_ENUMS" to "true"),
        configOptions: Map<String, String> = emptyMap(),
    ) {
        requireNonBlank("specPath", specPath)
        requireNonBlank("apiPackage", apiPackage)
        requireNonBlank("modelPackage", modelPackage)
        requireNonBlank("packageName", packageName)
        requireNonBlank("generatorName", generatorName)
        requireNonBlank("library", library)
        requireNonBlank("sourceFolder", sourceFolder)
        requireNonBlank("serializationLibrary", serializationLibrary)
        requireNonBlank("dateLibrary", dateLibrary)
        requireNonBlank("enumPropertyNaming", enumPropertyNaming)

        validateList("apis", apis)
        validateList("models", models)
        validateList("supportingFiles", supportingFiles)
        validateMappings("schemaMappings", schemaMappings)
        validateMappings("typeMappings", typeMappings)
        validateMappings("inlineSchemaOptions", inlineSchemaOptions)
        validateMappings("configOptions", configOptions)

        val file = specFile ?: throw GradleException("openApiClient.specPath is required.")
        if (!file.exists()) {
            throw GradleException("OpenAPI spec file does not exist: ${file.absolutePath}")
        }
        if (!file.isFile || !file.canRead()) {
            throw GradleException("OpenAPI spec file is not readable: ${file.absolutePath}")
        }
        if (file.length() == 0L) {
            throw GradleException("OpenAPI spec file is empty: ${file.absolutePath}")
        }

        val root = parseSpec(file)
        if (!root.isObject || root.path("openapi").isMissingNode) {
            throw GradleException("OpenAPI spec must be an object with an 'openapi' field: ${file.absolutePath}")
        }
        val paths = root.path("paths")
        if (!paths.isObject) {
            throw GradleException("OpenAPI spec must contain a 'paths' object: ${file.absolutePath}")
        }

        if (apis.isNotEmpty()) {
            val operationTags = collectOperationTags(paths)
            val missingApis = apis.filterNot(operationTags::contains)
            if (missingApis.isNotEmpty()) {
                val available = operationTags.sorted().joinToString(", ").ifBlank { "(none)" }
                throw GradleException(
                    "Selected OpenAPI API/tag(s) are not present in ${file.absolutePath}: " +
                        "${missingApis.joinToString(", ")}. Available tags: $available",
                )
            }
        }
    }

    private fun requireNonBlank(
        name: String,
        value: String?,
    ) {
        if (value.isNullOrBlank()) {
            throw GradleException("openApiClient.$name is required and must not be blank.")
        }
    }

    private fun validateMappings(
        name: String,
        mappings: Map<String, String>,
    ) {
        mappings.forEach { (key, value) ->
            if (key.isBlank() || value.isBlank()) {
                throw GradleException("openApiClient.$name must not contain blank keys or values.")
            }
        }
    }

    private fun validateList(
        name: String,
        values: List<String>,
    ) {
        values.forEach { value ->
            if (value.isBlank()) {
                throw GradleException("openApiClient.$name must not contain blank values.")
            }
        }
    }

    private fun parseSpec(file: File): JsonNode {
        val mapper =
            if (file.extension.equals("yaml", ignoreCase = true) ||
                file.extension.equals("yml", ignoreCase = true)
            ) {
                yamlMapper
            } else {
                jsonMapper
            }

        return try {
            mapper.readTree(file)
        } catch (exc: JsonProcessingException) {
            throw GradleException("OpenAPI spec must be valid JSON or YAML: ${file.absolutePath}", exc)
        }
    }

    private fun collectOperationTags(paths: JsonNode): Set<String> {
        val tags = linkedSetOf<String>()
        val pathItems = paths.properties().iterator()
        while (pathItems.hasNext()) {
            val pathItem = pathItems.next().value
            if (!pathItem.isObject) continue
            val operations = pathItem.properties().iterator()
            while (operations.hasNext()) {
                val operation = operations.next()
                if (operation.key.lowercase() !in httpMethods || !operation.value.isObject) continue
                val operationTags = operation.value.path("tags")
                if (!operationTags.isArray) continue
                operationTags.forEach { tag ->
                    if (tag.isTextual) tags.add(tag.asText())
                }
            }
        }
        return tags
    }
}

internal fun resolveSpecFile(
    rootProjectDir: File,
    specPath: String,
): File {
    val configured = File(specPath)
    return if (configured.isAbsolute) configured else rootProjectDir.resolve(specPath)
}
