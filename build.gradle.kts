import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    jacoco
}

group = "dev.jorisjonkers"
version =
    providers
        .gradleProperty("artifactVersion")
        .orElse(
            providers.environmentVariable("GITHUB_REF_NAME").map { ref ->
                if (ref.startsWith("v")) ref.removePrefix("v") else "0.0.0-SNAPSHOT"
            },
        ).orElse("0.0.0-SNAPSHOT")
        .get()

repositories {
    gradlePluginPortal()
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    plugins {
        create("openApiClient") {
            id = "dev.jorisjonkers.openapi-client"
            implementationClass = "dev.jorisjonkers.openapi.client.OpenApiClientPlugin"
            displayName = "JorisJonkers OpenAPI Client"
            description = "Generates typed JVM clients from consumer-owned local OpenAPI specs."
        }
    }
}

dependencies {
    implementation("org.openapitools:openapi-generator-gradle-plugin:7.22.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.3")

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("openapi-client-gradle")
            description.set("Gradle plugin for generating typed JVM clients from local OpenAPI specs.")
            url.set("https://github.com/JorisJonkers-dev/openapi-client-gradle")
            licenses {
                license {
                    name.set("Joris Jonkers Proprietary Source-Available License 1.0")
                    url.set("https://github.com/JorisJonkers-dev/openapi-client-gradle/blob/main/LICENSE")
                    distribution.set("repo")
                    comments.set("SPDX-License-Identifier: LicenseRef-JorisJonkers-Proprietary-1.0")
                }
            }
            developers {
                developer {
                    id.set("jorisjonkers-dev")
                    name.set("JorisJonkers-dev")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/JorisJonkers-dev/openapi-client-gradle.git")
                developerConnection.set("scm:git:ssh://git@github.com:JorisJonkers-dev/openapi-client-gradle.git")
                url.set("https://github.com/JorisJonkers-dev/openapi-client-gradle")
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/JorisJonkers-dev/openapi-client-gradle")
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
    }
}

tasks.register("verifyPublishingCoordinates") {
    group = "verification"
    description = "Verifies the implementation artifact and plugin marker coordinates."

    doLast {
        val expected =
            mapOf(
                "pluginMaven" to ("dev.jorisjonkers" to "openapi-client-gradle"),
                "openApiClientPluginMarkerMaven" to (
                    "dev.jorisjonkers.openapi-client" to "dev.jorisjonkers.openapi-client.gradle.plugin"
                ),
            )
        expected.forEach { (name, coordinates) ->
            val publication = publishing.publications.getByName(name) as MavenPublication
            check(publication.groupId == coordinates.first) {
                "Unexpected groupId for $name: ${publication.groupId}"
            }
            check(publication.artifactId == coordinates.second) {
                "Unexpected artifactId for $name: ${publication.artifactId}"
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn("verifyPublishingCoordinates", "jacocoTestCoverageVerification")
}
