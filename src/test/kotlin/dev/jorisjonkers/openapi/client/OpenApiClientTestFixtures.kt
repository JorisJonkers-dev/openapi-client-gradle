package dev.jorisjonkers.openapi.client

internal val preparedSpecText =
    """
    openapi: 3.0.3
    info:
      title: Prepared API
      version: 1.0.0
    paths:
      /status:
        get:
          tags:
            - Prepared
          operationId: getStatus
          responses:
            '204':
              description: No content.
    """.trimIndent()

internal val minimalFilterFixture =
    """
    {
      "openapi": "3.0.3",
      "info": {"title": "Filter Valid", "version": "1.0.0"},
      "paths": {
        "/pets": {
          "get": {
            "responses": {"204": {"description": "No content"}}
          }
        }
      }
    }
    """.trimIndent()

internal val transformToggleFixture =
    """
    {
      "openapi": "3.1.0",
      "info": {"title": "Transform Toggles", "version": "1.0.0"},
      "paths": {
        "/kept": {
          "get": {
            "responses": {
              "200": {
                "description": "ok",
                "content": {
                  "application/json": {
                    "schema": {"${'$'}ref": "#/components/schemas/Kept"}
                  }
                }
              }
            }
          },
          "delete": {
            "responses": {"204": {"description": "deleted"}}
          }
        },
        "/method-missing": {
          "get": {
            "responses": {"204": {"description": "No content"}}
          }
        },
        "/dropped": {
          "get": {
            "responses": {
              "200": {
                "description": "unused",
                "content": {
                  "application/json": {
                    "schema": {"${'$'}ref": "#/components/schemas/UnusedFromDropped"}
                  }
                }
              }
            }
          }
        }
      },
      "components": {
        "schemas": {
          "Kept": {
            "type": "object",
            "properties": {
              "nullable": {"${'$'}ref": "#/components/schemas/NullableMarker"},
              "enumWrapper": {"${'$'}ref": "#/components/schemas/EnumWrapper"}
            }
          },
          "NullableMarker": {"type": "null"},
          "EnumWrapper": {
            "allOf": [{"${'$'}ref": "#/components/schemas/EnumValue"}],
            "enum": ["one"]
          },
          "EnumValue": {"type": "string", "enum": ["one", "two"]},
          "UnusedFromDropped": {"type": "object"}
        }
      }
    }
    """.trimIndent()

internal val escapedPointerFixture =
    """
    {
      "openapi": "3.1.0",
      "info": {"title": "Escaped Pointers", "version": "1.0.0"},
      "paths": {
        "/escaped": {
          "get": {
            "responses": {
              "200": {
                "description": "ok",
                "content": {
                  "application/json": {
                    "schema": {"${'$'}ref": "#/components/schemas/Escaped~1Name~0Thing"}
                  }
                }
              }
            }
          }
        }
      },
      "components": {
        "schemas": {
          "Escaped/Name~Thing": {
            "type": "object",
            "properties": {
              "child": {"${'$'}ref": "#/components/schemas/Nested~1Child"}
            }
          },
          "Nested/Child": {
            "type": "object",
            "properties": {
              "items": {
                "type": "array",
                "items": [
                  {"${'$'}ref": "#/components/schemas/ArrayLeaf"}
                ]
              }
            }
          },
          "ArrayLeaf": {"type": "string"},
          "Unused/Schema": {"type": "object"}
        }
      }
    }
    """.trimIndent()

internal val discordLikeFixture =
    """
    {
      "openapi": "3.1.0",
      "info": {"title": "Discord-like Fixture", "version": "1.0.0"},
      "paths": {
        "/users/@me": {
          "get": {
            "operationId": "getCurrentUser",
            "responses": {
              "200": {
                "description": "ok",
                "content": {
                  "application/json": {
                    "schema": {"${'$'}ref": "#/components/schemas/CurrentUserResponse"}
                  }
                }
              },
              "4XX": {"${'$'}ref": "#/components/responses/ClientErrorResponse"}
            }
          }
        },
        "/guilds/{guild_id}": {
          "parameters": [{"${'$'}ref": "#/components/parameters/GuildId"}],
          "get": {
            "operationId": "getGuild",
            "responses": {
              "200": {"${'$'}ref": "#/components/responses/GuildResponse"}
            }
          }
        },
        "/guilds/{guild_id}/members/{user_id}": {
          "get": {
            "operationId": "getGuildMember",
            "responses": {
              "200": {
                "description": "ok",
                "content": {
                  "application/json": {
                    "schema": {"${'$'}ref": "#/components/schemas/GuildMemberResponse"}
                  }
                }
              }
            }
          },
          "patch": {
            "operationId": "updateGuildMember",
            "requestBody": {
              "content": {
                "application/json": {
                  "schema": {"${'$'}ref": "#/components/schemas/GuildMemberResponse"}
                }
              }
            },
            "responses": {"204": {"description": "updated"}}
          },
          "delete": {
            "operationId": "deleteGuildMember",
            "responses": {"204": {"description": "deleted"}}
          }
        },
        "/unused": {
          "get": {
            "operationId": "unused",
            "responses": {
              "200": {
                "description": "unused",
                "content": {
                  "application/json": {
                    "schema": {"${'$'}ref": "#/components/schemas/UnusedSchema"}
                  }
                }
              }
            }
          }
        }
      },
      "components": {
        "parameters": {
          "GuildId": {
            "name": "guild_id",
            "in": "path",
            "required": true,
            "schema": {"${'$'}ref": "#/components/schemas/SnowflakeType"}
          }
        },
        "responses": {
          "GuildResponse": {
            "description": "ok",
            "content": {
              "application/json": {
                "schema": {"${'$'}ref": "#/components/schemas/GuildResponse"}
              }
            }
          },
          "ClientErrorResponse": {
            "description": "error",
            "content": {
              "application/json": {
                "schema": {"${'$'}ref": "#/components/schemas/ErrorResponse"}
              }
            }
          },
          "UnusedResponse": {
            "description": "unused",
            "content": {
              "application/json": {
                "schema": {"${'$'}ref": "#/components/schemas/UnusedComponentSchema"}
              }
            }
          }
        },
        "schemas": {
          "CurrentUserResponse": {
            "type": "object",
            "properties": {
              "id": {"${'$'}ref": "#/components/schemas/SnowflakeType"},
              "role_marker": {"${'$'}ref": "#/components/schemas/NullableRoleMarker"}
            }
          },
          "GuildResponse": {
            "type": "object",
            "properties": {
              "id": {"${'$'}ref": "#/components/schemas/SnowflakeType"},
              "sticker": {"${'$'}ref": "#/components/schemas/Sticker"}
            }
          },
          "GuildMemberResponse": {
            "type": "object",
            "properties": {
              "user": {"${'$'}ref": "#/components/schemas/CurrentUserResponse"}
            }
          },
          "SnowflakeType": {"type": "string"},
          "NullableRoleMarker": {"type": "null"},
          "Sticker": {
            "type": "object",
            "properties": {
              "type": {
                "allOf": [{"${'$'}ref": "#/components/schemas/StickerType"}],
                "enum": [1, 2]
              }
            }
          },
          "StickerType": {"type": "integer", "enum": [1, 2, 3]},
          "ErrorResponse": {"type": "object", "properties": {"message": {"type": "string"}}},
          "UnusedSchema": {"type": "object"},
          "UnusedComponentSchema": {"type": "object"}
        }
      }
    }
    """.trimIndent()

internal const val EXTERNAL_VALID_JSON_FIXTURE =
    """{"openapi":"3.0.3","info":{"title":"Valid","version":"1.0.0"},"paths":{}}"""

internal const val NORMALIZED_EXTERNAL_VALID_JSON =
    """{"info":{"title":"Valid","version":"1.0.0"},"openapi":"3.0.3","paths":{}}"""
