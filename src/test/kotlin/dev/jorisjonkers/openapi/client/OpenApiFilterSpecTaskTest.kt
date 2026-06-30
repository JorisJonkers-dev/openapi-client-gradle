package dev.jorisjonkers.openapi.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class OpenApiFilterSpecTaskTest : OpenApiTestProjectFixture() {
    @Test
    fun `filters allowed operations injects tags and rewrites reachable schemas`() {
        writeSpec("specs/discord-like.json", discordLikeFixture)
        val output = tempDir.resolve("build/filtered/discord-like.json")
        val task = discordLikeFilterTask()

        task.filter()

        val root = jsonMapper.readTree(output.toFile())
        assertDiscordLikePaths(root.path("paths"))
        assertDiscordLikeSchemas(root.path("components").path("schemas"))
    }

    @Test
    fun `filter task rejects invalid configuration and malformed specs`() {
        val validSpec = writeSpec("specs/filter-valid.json", minimalFilterFixture)

        assertFilterFails("allowedOperations must contain at least one path", validSpec, emptyMap())
        assertFilterFails(
            "injectedTag is required and must not be blank",
            validSpec,
            mapOf("/pets" to listOf("get")),
            " ",
        )
        assertFilterFails(
            "OpenAPI spec must be a JSON/YAML object",
            writeSpec("specs/filter-array.json", "[]"),
            mapOf(
                "/pets" to listOf("get"),
            ),
        )
        assertMissingPathsFails()
        assertFilterFails(
            "OpenApiFilterSpecTask outputSpec must end with .json",
            validSpec,
            mapOf(
                "/pets" to listOf("get"),
            ),
            outputRelativePath = "build/filtered/not-json.txt",
        )
        assertFilterFails(
            "allowedOperations references path(s) not present",
            validSpec,
            mapOf(
                "/missing" to listOf("get"),
            ),
        )
        assertFilterFails(
            "allowedOperations[/pets] must contain at least one HTTP method",
            validSpec,
            mapOf(
                "/pets" to emptyList(),
            ),
        )
        assertFilterFails(
            "allowedOperations[/pets] contains unsupported HTTP method(s): connect",
            validSpec,
            mapOf(
                "/pets" to listOf("CONNECT"),
            ),
        )
    }

    @Test
    fun `filter task can leave schema transforms disabled and removes paths with no remaining methods`() {
        val input = writeSpec("specs/filter-toggles.json", transformToggleFixture)
        val output = tempDir.resolve("build/filtered/toggles.json")
        val task =
            registerFilterTask(
                input,
                "build/filtered/toggles.json",
                mapOf(
                    "/kept" to listOf("GET"),
                    "/method-missing" to listOf("post"),
                ),
            ) {
                pruneUnreachableSchemas.set(false)
                rewriteNullTypes.set(false)
                collapseRedundantEnumAllOf.set(false)
            }

        task.filter()

        val root = jsonMapper.readTree(output.toFile())
        assertTransformTogglePaths(root.path("paths"))
        assertTransformToggleSchemas(root.path("components").path("schemas"))
    }

    @Test
    fun `filter task prunes schemas reached through escaped json pointers`() {
        val input = writeSpec("specs/filter-escaped-pointers.json", escapedPointerFixture)
        val output = tempDir.resolve("build/filtered/escaped-pointers.json")
        val task = registerFilterTask(input, "build/filtered/escaped-pointers.json", mapOf("/escaped" to listOf("get")))

        task.filter()

        val schemas = jsonMapper.readTree(output.toFile()).path("components").path("schemas")
        assertTrue(schemas.has("Escaped/Name~Thing"))
        assertTrue(schemas.has("Nested/Child"))
        assertTrue(schemas.has("ArrayLeaf"))
        assertFalse(schemas.has("Unused/Schema"))
    }

    @Test
    fun `named external spec filters are registered from the DSL`() {
        writeSettings()
        writeSpec("specs/discord-like.json", discordLikeFixture)
        writeBuildFile(discordLikeFilterBuildFile())

        val result = runGradle("filterDiscordLikeOpenApiSpec")

        assertEquals(
            org.gradle.testkit.runner.TaskOutcome.SUCCESS,
            result.task(":filterDiscordLikeOpenApiSpec")?.outcome,
        )
        val root = jsonMapper.readTree(tempDir.resolve("build/filtered/from-dsl.json").toFile())
        assertTrue(root.path("paths").has("/users/@me"))
        assertFalse(root.path("paths").has("/unused"))
        assertEquals(
            "Fixture",
            root
                .path("paths")
                .path("/users/@me")
                .path("get")
                .path("tags")[0]
                .asText(),
        )
        assertFalse(root.path("components").path("schemas").has("UnusedSchema"))
    }

    private fun discordLikeFilterTask(): OpenApiFilterSpecTask =
        registerFilterTask(
            inputSpec = tempDir.resolve("specs/discord-like.json"),
            outputRelativePath = "build/filtered/discord-like.json",
            allowedOperations =
                mapOf(
                    "/users/@me" to listOf("get"),
                    "/guilds/{guild_id}" to listOf("get"),
                    "/guilds/{guild_id}/members/{user_id}" to listOf("get", "patch"),
                ),
        )

    private fun assertDiscordLikePaths(paths: com.fasterxml.jackson.databind.JsonNode) {
        assertTrue(paths.has("/users/@me"))
        assertTrue(paths.has("/guilds/{guild_id}"))
        assertTrue(paths.has("/guilds/{guild_id}/members/{user_id}"))
        assertFalse(paths.has("/unused"))
        assertEquals(
            "Fixture",
            paths
                .path("/users/@me")
                .path("get")
                .path("tags")[0]
                .asText(),
        )
        assertFalse(paths.path("/guilds/{guild_id}/members/{user_id}").has("delete"))
    }

    private fun assertDiscordLikeSchemas(schemas: com.fasterxml.jackson.databind.JsonNode) {
        assertTrue(schemas.has("CurrentUserResponse"))
        assertTrue(schemas.has("GuildResponse"))
        assertTrue(schemas.has("GuildMemberResponse"))
        assertTrue(schemas.has("SnowflakeType"))
        assertTrue(schemas.has("NullableRoleMarker"))
        assertTrue(schemas.has("Sticker"))
        assertTrue(schemas.has("StickerType"))
        assertTrue(schemas.has("ErrorResponse"))
        assertFalse(schemas.has("UnusedSchema"))
        assertFalse(schemas.has("UnusedComponentSchema"))
        assertEquals("boolean", schemas.path("NullableRoleMarker").path("type").asText())
        assertStickerTypeWasCollapsed(schemas)
    }

    private fun assertStickerTypeWasCollapsed(schemas: com.fasterxml.jackson.databind.JsonNode) {
        val stickerType = schemas.path("Sticker").path("properties").path("type")
        assertEquals("#/components/schemas/StickerType", stickerType.path("\$ref").asText())
        assertFalse(stickerType.has("allOf"))
        assertFalse(stickerType.has("enum"))
    }

    private fun assertMissingPathsFails() {
        assertFilterFails(
            expectedMessage = "OpenAPI spec must contain a 'paths' object",
            inputSpec =
                writeSpec(
                    "specs/filter-no-paths.json",
                    """{"openapi":"3.0.3","info":{"title":"No paths","version":"1.0.0"}}""",
                ),
            allowedOperations = mapOf("/pets" to listOf("get")),
        )
    }

    private fun assertFilterFails(
        expectedMessage: String,
        inputSpec: Path,
        allowedOperations: Map<String, List<String>>,
        injectedTag: String = "Fixture",
        outputRelativePath: String = "build/filtered/failing.json",
    ) {
        val task = registerFilterTask(inputSpec, outputRelativePath, allowedOperations, injectedTag)
        assertGradleFails(expectedMessage) {
            task.filter()
        }
    }

    private fun assertTransformTogglePaths(paths: com.fasterxml.jackson.databind.JsonNode) {
        assertTrue(paths.has("/kept"))
        assertFalse(paths.path("/kept").has("delete"))
        assertEquals(
            "Fixture",
            paths
                .path("/kept")
                .path("get")
                .path("tags")[0]
                .asText(),
        )
        assertFalse(paths.has("/method-missing"))
        assertFalse(paths.has("/dropped"))
    }

    private fun assertTransformToggleSchemas(schemas: com.fasterxml.jackson.databind.JsonNode) {
        assertTrue(schemas.has("UnusedFromDropped"))
        assertEquals("null", schemas.path("NullableMarker").path("type").asText())
        assertTrue(schemas.path("EnumWrapper").has("allOf"))
        assertTrue(schemas.path("EnumWrapper").has("enum"))
    }

    private fun discordLikeFilterBuildFile(): String =
        """
        plugins {
            id("dev.jorisjonkers.openapi-client")
        }

        openApiExternalSpecs {
            filters {
                create("discordLike") {
                    inputSpec.set("specs/discord-like.json")
                    outputSpec.set("build/filtered/from-dsl.json")
                    allowedOperations.set(
                        mapOf(
                            "/users/@me" to listOf("get"),
                            "/guilds/{guild_id}" to listOf("get"),
                            "/guilds/{guild_id}/members/{user_id}" to listOf("get", "patch"),
                        )
                    )
                    injectedTag.set("Fixture")
                }
            }
        }
        """.trimIndent()
}
