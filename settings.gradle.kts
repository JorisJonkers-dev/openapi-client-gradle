pluginManagement {
    repositories {
        mavenLocal()
        maven {
            name = "GradleConventions"
            url = uri("https://maven.pkg.github.com/JorisJonkers-dev/gradle-conventions")
            credentials {
                username =
                    providers
                        .gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                        .orNull
                password =
                    providers
                        .gradleProperty("gpr.token")
                        .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                        .orNull
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            val pluginId = requested.id.id
            if (pluginId.startsWith("dev.jorisjonkers.")) {
                val conventionName = pluginId.removePrefix("dev.jorisjonkers.")
                useModule("dev.jorisjonkers:gradle-conventions-$conventionName:${requested.version}")
            }
        }
    }
}

rootProject.name = "openapi-client-gradle"
