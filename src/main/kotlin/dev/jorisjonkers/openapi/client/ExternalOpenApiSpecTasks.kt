package dev.jorisjonkers.openapi.client

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import javax.inject.Inject

open class OpenApiExternalSpecsExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        val specDirectory = objects.directoryProperty()
        val specs: NamedDomainObjectContainer<ExternalOpenApiSpec> =
            objects.domainObjectContainer(ExternalOpenApiSpec::class.java)
        val filters: NamedDomainObjectContainer<ExternalOpenApiSpecFilter> =
            objects.domainObjectContainer(ExternalOpenApiSpecFilter::class.java)

        init {
            specs.all {
                rawFileName.convention("$name.raw")
                normalizedFileName.convention("$name.json")
            }
        }

        fun specs(action: Action<NamedDomainObjectContainer<ExternalOpenApiSpec>>) {
            action.execute(specs)
        }

        fun filters(action: Action<NamedDomainObjectContainer<ExternalOpenApiSpecFilter>>) {
            action.execute(filters)
        }
    }

open class ExternalOpenApiSpec
    @Inject
    constructor(
        private val specName: String,
        objects: ObjectFactory,
    ) : Named {
        override fun getName(): String = specName

        val sourceUrl: Property<String> = objects.property(String::class.java)
        val rawFileName: Property<String> = objects.property(String::class.java)
        val normalizedFileName: Property<String> = objects.property(String::class.java)
    }

open class ExternalOpenApiSpecFilter
    @Inject
    constructor(
        private val filterName: String,
        objects: ObjectFactory,
    ) : Named {
        override fun getName(): String = filterName

        val inputSpec: Property<String> =
            objects.property(String::class.java)
        val outputSpec: Property<String> = objects.property(String::class.java)
        val allowedOperations: MapProperty<String, List<String>> =
            objects.mapProperty(String::class.java, listOfStringsType())
        val injectedTag: Property<String> = objects.property(String::class.java)
        val pruneUnreachableSchemas: Property<Boolean> =
            objects
                .property(
                    Boolean::class.javaObjectType,
                ).convention(true)
        val rewriteNullTypes: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(true)
        val collapseRedundantEnumAllOf: Property<Boolean> =
            objects.property(Boolean::class.javaObjectType).convention(true)
    }

@DisableCachingByDefault(because = "Downloads from remote URLs; output not reproducible from inputs")
abstract class DownloadExternalOpenApiSpecsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val specDirectory: org.gradle.api.file.DirectoryProperty

    @get:Internal
    lateinit var configuredSpecs: NamedDomainObjectContainer<ExternalOpenApiSpec>

    @get:Input
    val configuredSourceUrls: Map<String, String>
        get() = configuredSpecs.toList().associate { spec -> spec.name to spec.sourceUrl.orNull.orEmpty() }

    @get:Input
    val configuredRawFileNames: Map<String, String>
        get() = configuredSpecs.toList().associate { spec -> spec.name to spec.rawFileName.get() }

    @TaskAction
    fun download() {
        val specs = configuredSpecs.toList()
        if (specs.isEmpty()) {
            externalSpecsFailure("openApiExternalSpecs.specs must contain at least one configured spec.")
        }

        val outputDir = specDirectory.get().asFile
        outputDir.mkdirs()
        specs.forEach { spec ->
            val source =
                spec.sourceUrl.orNull?.takeIf { it.isNotBlank() }
                    ?: externalSpecsFailure("openApiExternalSpecs.specs.${spec.name}.sourceUrl is required.")
            val target = safeChild(outputDir, spec.rawFileName.get(), "rawFileName")
            target.parentFile.mkdirs()
            val uri = parseSourceUri(spec.name, source)

            try {
                uri.toURL().openStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (exc: IOException) {
                externalSpecsFailure("Failed to download OpenAPI spec '${spec.name}' from $source", exc)
            }
        }
    }
}

@CacheableTask
abstract class NormalizeExternalOpenApiSpecsTask : DefaultTask() {
    @get:Internal
    abstract val specDirectory: org.gradle.api.file.DirectoryProperty

    @get:Internal
    lateinit var configuredSpecs: NamedDomainObjectContainer<ExternalOpenApiSpec>

    @get:Input
    val configuredRawFileNames: Map<String, String>
        get() = configuredSpecs.toList().associate { spec -> spec.name to spec.rawFileName.get() }

    @get:Input
    val configuredNormalizedFileNames: Map<String, String>
        get() = configuredSpecs.toList().associate { spec -> spec.name to normalizedJsonFileName(spec) }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val rawSpecFiles: org.gradle.api.file.FileCollection
        get() =
            project.files(
                configuredSpecs.toList().map { spec ->
                    safeChild(specDirectory.get().asFile, spec.rawFileName.get(), "rawFileName")
                },
            )

    @get:OutputFiles
    val normalizedSpecFiles: org.gradle.api.file.FileCollection
        get() =
            project.files(
                configuredSpecs.toList().map { spec ->
                    safeChild(specDirectory.get().asFile, normalizedJsonFileName(spec), "normalizedFileName")
                },
            )

    @TaskAction
    fun normalize() {
        val specs = configuredSpecs.toList()
        if (specs.isEmpty()) {
            externalSpecsFailure("openApiExternalSpecs.specs must contain at least one configured spec.")
        }

        val outputDir = specDirectory.get().asFile
        specs.forEach { spec ->
            val normalizedName = normalizedJsonFileName(spec)
            val source = safeChild(outputDir, spec.rawFileName.get(), "rawFileName")
            if (!source.exists() || !source.isFile) {
                externalSpecsFailure("Raw OpenAPI spec for '${spec.name}' does not exist: ${source.absolutePath}")
            }

            val target = safeChild(outputDir, normalizedName, "normalizedFileName")
            target.parentFile.mkdirs()
            val root = OpenApiSpecJson.read(source)
            OpenApiSpecJson.writeMinified(root, target)
        }
    }
}

@CacheableTask
abstract class OpenApiFilterSpecTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputSpec: org.gradle.api.file.RegularFileProperty

    @get:OutputFile
    abstract val outputSpec: org.gradle.api.file.RegularFileProperty

    @get:Input
    abstract val allowedOperations: MapProperty<String, List<String>>

    @get:Input
    abstract val injectedTag: Property<String>

    @get:Input
    abstract val pruneUnreachableSchemas: Property<Boolean>

    @get:Input
    abstract val rewriteNullTypes: Property<Boolean>

    @get:Input
    abstract val collapseRedundantEnumAllOf: Property<Boolean>

    init {
        pruneUnreachableSchemas.convention(true)
        rewriteNullTypes.convention(true)
        collapseRedundantEnumAllOf.convention(true)
    }

    @TaskAction
    fun filter() {
        val allowList = allowedOperations.get()
        if (allowList.isEmpty()) {
            externalSpecsFailure("allowedOperations must contain at least one path.")
        }
        val tag =
            injectedTag.orNull?.takeIf { it.isNotBlank() }
                ?: externalSpecsFailure("injectedTag is required and must not be blank.")

        val parsedRoot = OpenApiSpecJson.read(inputSpec.get().asFile)
        if (parsedRoot !is ObjectNode) {
            externalSpecsFailure("OpenAPI spec must be a JSON/YAML object: ${inputSpec.get().asFile.absolutePath}")
        }
        val root = parsedRoot.deepCopy<ObjectNode>()
        val paths =
            root.path("paths") as? ObjectNode
                ?: externalSpecsFailure(
                    "OpenAPI spec must contain a 'paths' object: ${inputSpec.get().asFile.absolutePath}",
                )
        val output = outputSpec.get().asFile
        if (!output.name.endsWith(".json", ignoreCase = true)) {
            externalSpecsFailure("OpenApiFilterSpecTask outputSpec must end with .json: ${output.absolutePath}")
        }

        filterPaths(paths, allowList, tag)

        val schemas = root.path("components").path("schemas")
        if (pruneUnreachableSchemas.get() && schemas is ObjectNode) {
            pruneSchemas(root, schemas)
        }
        if (rewriteNullTypes.get()) {
            rewriteNullTypeProperties(root)
        }
        if (collapseRedundantEnumAllOf.get()) {
            collapseRedundantEnumAllOf(root)
        }

        outputSpec
            .get()
            .asFile.parentFile
            .mkdirs()
        OpenApiSpecJson.writeMinified(root, outputSpec.get().asFile)
    }

    private fun filterPaths(
        paths: ObjectNode,
        allowList: Map<String, List<String>>,
        tag: String,
    ) {
        val methods = setOf("get", "post", "put", "patch", "delete", "head", "options", "trace")
        val missingPaths = allowList.keys.filterNot { paths.has(it) }
        if (missingPaths.isNotEmpty()) {
            externalSpecsFailure(
                "allowedOperations references path(s) not present in the OpenAPI spec: ${missingPaths.joinToString(
                    ", ",
                )}",
            )
        }
        allowList.forEach { (path, allowedMethods) ->
            if (allowedMethods.isEmpty()) {
                externalSpecsFailure("allowedOperations[$path] must contain at least one HTTP method.")
            }
            val invalidMethods = allowedMethods.map { it.lowercase() }.filterNot(methods::contains)
            if (invalidMethods.isNotEmpty()) {
                externalSpecsFailure(
                    "allowedOperations[$path] contains unsupported HTTP method(s): ${invalidMethods.joinToString(
                        ", ",
                    )}",
                )
            }
        }

        paths.fieldNames().asSequence().toList().forEach { path ->
            val allowed = allowList[path]?.map { it.lowercase() }?.toSet()
            if (allowed == null) {
                paths.remove(path)
                return@forEach
            }
            val pathItem = paths.path(path) as? ObjectNode ?: return@forEach
            pathItem.fieldNames().asSequence().toList().forEach { key ->
                if (key.lowercase() !in methods) return@forEach
                if (key.lowercase() !in allowed) {
                    pathItem.remove(key)
                } else {
                    val operation = pathItem.path(key) as? ObjectNode
                    operation?.replace("tags", operation.arrayNode().add(tag))
                }
            }
            if (pathItem.fieldNames().asSequence().none { it.lowercase() in methods }) {
                paths.remove(path)
            }
        }
    }

    private fun pruneSchemas(
        root: ObjectNode,
        schemas: ObjectNode,
    ) {
        val seeds = linkedSetOf<String>()
        val queuedComponents = ArrayDeque<ComponentRef>()
        val seenComponents = linkedSetOf<ComponentRef>()
        collectRefs(root.path("paths"), seeds, queuedComponents)

        val components = root.path("components")
        if (components is ObjectNode) {
            while (queuedComponents.isNotEmpty()) {
                val ref = queuedComponents.removeFirst()
                if (!seenComponents.add(ref)) continue
                val component = components.path(ref.section).path(ref.name)
                if (!component.isMissingNode) {
                    collectRefs(component, seeds, queuedComponents)
                }
            }
        }

        val reachable = linkedSetOf<String>()
        val queue = ArrayDeque(seeds)
        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            if (!reachable.add(name)) continue
            val schema = schemas.path(name)
            if (!schema.isMissingNode) {
                val nested = linkedSetOf<String>()
                collectRefs(schema, nested, ArrayDeque())
                queue.addAll(nested.filterNot(reachable::contains))
            }
        }

        schemas.fieldNames().asSequence().toList().forEach { name ->
            if (name !in reachable) schemas.remove(name)
        }
    }

    private fun collectRefs(
        node: JsonNode,
        schemaRefs: MutableSet<String>,
        componentRefs: ArrayDeque<ComponentRef>,
    ) {
        if (node.isObject) {
            val ref = node.path("\$ref")
            if (ref.isTextual) {
                val componentRef = parseComponentRef(ref.asText())
                if (componentRef != null) {
                    if (componentRef.section == "schemas") {
                        schemaRefs.add(componentRef.name)
                    } else {
                        componentRefs.add(componentRef)
                    }
                }
                return
            }
            node.properties().forEach { collectRefs(it.value, schemaRefs, componentRefs) }
        } else if (node.isArray) {
            node.forEach { collectRefs(it, schemaRefs, componentRefs) }
        }
    }

    private fun rewriteNullTypeProperties(node: JsonNode) {
        if (node is ObjectNode) {
            if (node.path("type").asText(null) == "null") {
                node.put("type", "boolean")
            }
            node.properties().forEach { rewriteNullTypeProperties(it.value) }
        } else if (node.isArray) {
            node.forEach { rewriteNullTypeProperties(it) }
        }
    }

    private fun collapseRedundantEnumAllOf(node: JsonNode) {
        if (node is ObjectNode) {
            val allOf = node.path("allOf")
            val ref = if (allOf.isArray && allOf.size() == 1) allOf[0].path("\$ref").asText(null) else null
            if (ref != null && node.has("enum")) {
                node.removeAll()
                node.put("\$ref", ref)
                return
            }
            node.properties().forEach { collapseRedundantEnumAllOf(it.value) }
        } else if (node.isArray) {
            node.forEach { collapseRedundantEnumAllOf(it) }
        }
    }
}

@CacheableTask
abstract class OpenApiProvenanceBannerTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: org.gradle.api.file.RegularFileProperty

    @get:OutputFile
    abstract val outputFile: org.gradle.api.file.RegularFileProperty

    @get:Input
    abstract val bannerText: Property<String>

    @TaskAction
    fun applyBanner() {
        val banner =
            bannerText.orNull?.takeIf { it.isNotBlank() }
                ?: throw GradleException("bannerText is required and must not be blank.")
        val normalizedBanner = if (banner.endsWith("\n")) banner else "$banner\n"
        val input = inputFile.get().asFile
        if (!input.exists() || !input.isFile) {
            externalSpecsFailure("inputFile does not exist: ${input.absolutePath}")
        }
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        val existing = input.readText()
        output.writeText(if (existing.startsWith(normalizedBanner)) existing else normalizedBanner + existing)
    }
}

@DisableCachingByDefault(because = "Verification task compares generated files and has no outputs")
abstract class OpenApiDriftCheckTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val expectedFile: org.gradle.api.file.RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val actualFile: org.gradle.api.file.RegularFileProperty

    @get:Input
    abstract val failureMessage: Property<String>

    init {
        failureMessage.convention("Generated OpenAPI artifact drift detected.")
    }

    @TaskAction
    fun checkDrift() {
        val expected = expectedFile.get().asFile
        val actual = actualFile.get().asFile
        if (!expected.exists() || !expected.isFile) {
            externalSpecsFailure("expectedFile does not exist: ${expected.absolutePath}")
        }
        if (!actual.exists() || !actual.isFile) {
            externalSpecsFailure("actualFile does not exist: ${actual.absolutePath}")
        }
        if (expected.readText() != actual.readText()) {
            externalSpecsFailure(failureMessage.get())
        }
    }
}

private data class ComponentRef(
    val section: String,
    val name: String,
)

internal fun filterOpenApiSpecTaskName(name: String): String =
    "filter${name.replaceFirstChar { first ->
        if (first.isLowerCase()) first.titlecase(Locale.ROOT) else first.toString()
    }}OpenApiSpec"

internal object OpenApiSpecJson {
    private val jsonMapper = ObjectMapper()
    private val yamlMapper = ObjectMapper(YAMLFactory())

    fun read(file: File): JsonNode {
        val mapper =
            if (file.extension.equals("yaml", ignoreCase = true) ||
                file.extension.equals("yml", ignoreCase = true)
            ) {
                yamlMapper
            } else {
                jsonMapper
            }
        if (file.length() == 0L) {
            externalSpecsFailure("OpenAPI spec must not be empty: ${file.absolutePath}")
        }
        return try {
            val root = mapper.readTree(file)
            if (root == null || root.isMissingNode) {
                externalSpecsFailure("OpenAPI spec must not be empty: ${file.absolutePath}")
            }
            root
        } catch (exc: JsonProcessingException) {
            externalSpecsFailure("OpenAPI spec must be valid JSON or YAML: ${file.absolutePath}", exc)
        }
    }

    fun writeMinified(
        root: JsonNode,
        target: File,
    ) {
        target.writeText(jsonMapper.writeValueAsString(sortObjects(root)))
    }

    private fun sortObjects(node: JsonNode): JsonNode =
        when (node) {
            is ObjectNode -> {
                val sorted = node.objectNode()
                node.fieldNames().asSequence().toList().sorted().forEach { field ->
                    sorted.set<JsonNode>(field, sortObjects(node.get(field)))
                }
                sorted
            }
            is ArrayNode -> {
                val sorted = node.arrayNode()
                node.forEach { sorted.add(sortObjects(it)) }
                sorted
            }
            else -> node
        }
}

private fun parseSourceUri(
    specName: String,
    source: String,
): URI {
    val uri =
        try {
            URI(source)
        } catch (exc: URISyntaxException) {
            externalSpecsFailure("openApiExternalSpecs.specs.$specName.sourceUrl must be a valid URI: $source", exc)
        }
    if (uri.scheme.isNullOrBlank()) {
        externalSpecsFailure("openApiExternalSpecs.specs.$specName.sourceUrl must be an absolute URI: $source")
    }
    return uri
}

private fun normalizedJsonFileName(spec: ExternalOpenApiSpec): String {
    val normalizedName = spec.normalizedFileName.get()
    if (!normalizedName.endsWith(".json", ignoreCase = true)) {
        externalSpecsFailure(
            "openApiExternalSpecs.specs.${spec.name}.normalizedFileName must end with .json.",
        )
    }
    return normalizedName
}

private fun parseComponentRef(ref: String): ComponentRef? =
    if (!ref.startsWith("#/components/")) {
        null
    } else {
        val segments = ref.removePrefix("#/components/").split("/")
        if (segments.size < 2) {
            null
        } else {
            ComponentRef(segments[0].decodeJsonPointer(), segments.drop(1).joinToString("/").decodeJsonPointer())
        }
    }

private fun String.decodeJsonPointer(): String = replace("~1", "/").replace("~0", "~")

private fun safeChild(
    directory: File,
    relativePath: String,
    propertyName: String,
): File {
    if (relativePath.isBlank()) {
        externalSpecsFailure("openApiExternalSpecs $propertyName must not be blank.")
    }
    val child = File(relativePath)
    if (child.isAbsolute) {
        externalSpecsFailure("openApiExternalSpecs $propertyName must be relative: $relativePath")
    }
    val base = directory.canonicalFile
    val target = base.resolve(relativePath).canonicalFile
    if (!target.path.startsWith(base.path + File.separator) && target != base) {
        externalSpecsFailure("openApiExternalSpecs $propertyName must stay inside ${base.absolutePath}: $relativePath")
    }
    return target
}

private fun externalSpecsFailure(message: String): Nothing = throw GradleException(message)

private fun externalSpecsFailure(
    message: String,
    cause: Throwable,
): Nothing = throw GradleException(message, cause)

@Suppress("UNCHECKED_CAST")
private fun listOfStringsType(): Class<List<String>> = List::class.java as Class<List<String>>
