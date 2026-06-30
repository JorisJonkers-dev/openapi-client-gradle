package dev.jorisjonkers.openapi.client

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
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
        project.applyOpenApiBasePlugins()
        val extension = project.createOpenApiClientExtension()
        val externalSpecs = project.createExternalSpecsExtension()
        project.repositories.mavenCentral()
        project.addGeneratedClientDependencies(extension)

        val generatedRoot = project.layout.buildDirectory.dir("generated/openapi")
        val generatedSourceDir = project.generatedSourceDirectory(generatedRoot, extension)
        val generate = project.registerGenerateTask(extension, generatedRoot)

        project.registerGenerateOpenApiClientAlias(generate)
        project.registerExternalSpecTasks(externalSpecs)
        project.configureGeneratedSources(generatedSourceDir)
        project.configureJavaToolchain(extension)
        project.configureGeneratedSourceDependencies(generate)
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

        val file = specFile ?: configurationFailure("openApiClient.specPath is required.")
        if (!file.exists()) {
            configurationFailure("OpenAPI spec file does not exist: ${file.absolutePath}")
        }
        if (!file.isFile || !file.canRead()) {
            configurationFailure("OpenAPI spec file is not readable: ${file.absolutePath}")
        }
        if (file.length() == 0L) {
            configurationFailure("OpenAPI spec file is empty: ${file.absolutePath}")
        }

        val root = parseSpec(file)
        if (!root.isObject || root.path("openapi").isMissingNode) {
            configurationFailure("OpenAPI spec must be an object with an 'openapi' field: ${file.absolutePath}")
        }
        val paths = root.path("paths")
        if (!paths.isObject) {
            configurationFailure("OpenAPI spec must contain a 'paths' object: ${file.absolutePath}")
        }

        if (apis.isNotEmpty()) {
            val operationTags = collectOperationTags(paths)
            val missingApis = apis.filterNot(operationTags::contains)
            if (missingApis.isNotEmpty()) {
                val available = operationTags.sorted().joinToString(", ").ifBlank { "(none)" }
                configurationFailure(
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
            configurationFailure("openApiClient.$name is required and must not be blank.")
        }
    }

    private fun validateMappings(
        name: String,
        mappings: Map<String, String>,
    ) {
        mappings.forEach { (key, value) ->
            if (key.isBlank() || value.isBlank()) {
                configurationFailure("openApiClient.$name must not contain blank keys or values.")
            }
        }
    }

    private fun validateList(
        name: String,
        values: List<String>,
    ) {
        values.forEach { value ->
            if (value.isBlank()) {
                configurationFailure("openApiClient.$name must not contain blank values.")
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
            configurationFailure("OpenAPI spec must be valid JSON or YAML: ${file.absolutePath}", exc)
        }
    }

    private fun collectOperationTags(paths: JsonNode): Set<String> {
        val tags = linkedSetOf<String>()
        paths.properties().asSequence().map { it.value }.filter { it.isObject }.forEach { pathItem ->
            pathItem
                .properties()
                .asSequence()
                .filter { operation -> operation.key.lowercase() in httpMethods && operation.value.isObject }
                .map { operation -> operation.value.path("tags") }
                .filter { operationTags -> operationTags.isArray }
                .forEach { operationTags ->
                    operationTags.forEach { tag ->
                        if (tag.isTextual) tags.add(tag.asText())
                    }
                }
        }
        return tags
    }

    private fun configurationFailure(message: String): Nothing = throw GradleException(message)

    private fun configurationFailure(
        message: String,
        cause: Throwable,
    ): Nothing = throw GradleException(message, cause)
}

internal fun resolveSpecFile(
    rootProjectDir: File,
    specPath: String,
): File {
    val configured = File(specPath)
    return if (configured.isAbsolute) configured else rootProjectDir.resolve(specPath)
}
