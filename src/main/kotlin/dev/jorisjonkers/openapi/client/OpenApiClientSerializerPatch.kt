package dev.jorisjonkers.openapi.client

import java.io.File

private const val DEPRECATED_JACKSON_SERIALIZATION_INCLUSION =
    ".setSerializationInclusion(JsonInclude.Include.NON_ABSENT)"
private const val JACKSON_DEFAULT_PROPERTY_INCLUSION =
    ".setDefaultPropertyInclusion(JsonInclude.Include.NON_ABSENT)"

internal fun patchGeneratedKotlinJacksonSerializers(
    generatedRoot: File,
    sourceFolder: String,
) {
    val sourceRoot = generatedRoot.resolve(sourceFolder)
    if (!sourceRoot.isDirectory) {
        return
    }

    sourceRoot
        .walkTopDown()
        .filter { it.isFile && it.name == "Serializer.kt" }
        .forEach { serializer ->
            val original = serializer.readText()
            val patched =
                original.replace(
                    DEPRECATED_JACKSON_SERIALIZATION_INCLUSION,
                    JACKSON_DEFAULT_PROPERTY_INCLUSION,
                )
            if (patched != original) {
                serializer.writeText(patched)
            }
        }
}
