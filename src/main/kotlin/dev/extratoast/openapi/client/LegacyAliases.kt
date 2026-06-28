package dev.extratoast.openapi.client

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@Deprecated("Use dev.jorisjonkers.openapi.client.OpenApiClientExtension.")
abstract class OpenApiClientExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) : dev.jorisjonkers.openapi.client.OpenApiClientExtension(objects)

@Deprecated("Use dev.jorisjonkers.openapi.client.OpenApiClientPlugin.")
class OpenApiClientPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        dev.jorisjonkers.openapi.client
            .OpenApiClientPlugin()
            .apply(project)
    }
}

@Deprecated("Use dev.jorisjonkers.openapi.client.OpenApiExternalSpecsExtension.")
abstract class OpenApiExternalSpecsExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) : dev.jorisjonkers.openapi.client.OpenApiExternalSpecsExtension(objects)

@Deprecated("Use dev.jorisjonkers.openapi.client.ExternalOpenApiSpec.")
abstract class ExternalOpenApiSpec
    @Inject
    constructor(
        specName: String,
        objects: ObjectFactory,
    ) : dev.jorisjonkers.openapi.client.ExternalOpenApiSpec(specName, objects)

@Deprecated("Use dev.jorisjonkers.openapi.client.ExternalOpenApiSpecFilter.")
abstract class ExternalOpenApiSpecFilter
    @Inject
    constructor(
        filterName: String,
        objects: ObjectFactory,
    ) : dev.jorisjonkers.openapi.client.ExternalOpenApiSpecFilter(filterName, objects)

@Deprecated("Use dev.jorisjonkers.openapi.client.DownloadExternalOpenApiSpecsTask.")
@DisableCachingByDefault(because = "Downloads from remote URLs; output not reproducible from inputs")
abstract class DownloadExternalOpenApiSpecsTask : dev.jorisjonkers.openapi.client.DownloadExternalOpenApiSpecsTask()

@Deprecated("Use dev.jorisjonkers.openapi.client.NormalizeExternalOpenApiSpecsTask.")
@CacheableTask
abstract class NormalizeExternalOpenApiSpecsTask :
    dev.jorisjonkers.openapi.client.NormalizeExternalOpenApiSpecsTask()

@Deprecated("Use dev.jorisjonkers.openapi.client.OpenApiFilterSpecTask.")
@CacheableTask
abstract class OpenApiFilterSpecTask : dev.jorisjonkers.openapi.client.OpenApiFilterSpecTask()

@Deprecated("Use dev.jorisjonkers.openapi.client.OpenApiProvenanceBannerTask.")
@CacheableTask
abstract class OpenApiProvenanceBannerTask : dev.jorisjonkers.openapi.client.OpenApiProvenanceBannerTask()

@Deprecated("Use dev.jorisjonkers.openapi.client.OpenApiDriftCheckTask.")
@DisableCachingByDefault(because = "Verification task compares generated files and has no outputs")
abstract class OpenApiDriftCheckTask : dev.jorisjonkers.openapi.client.OpenApiDriftCheckTask()
