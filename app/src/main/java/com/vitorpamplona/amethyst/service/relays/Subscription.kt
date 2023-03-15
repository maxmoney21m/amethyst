package com.vitorpamplona.amethyst.service.relays

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

data class Subscription(
    val id: String = UUID.randomUUID().toString().substring(0, 4),
    val onEOSE: ((Long) -> Unit)? = null
) {
    var typedFilters: List<TypedFilter>? = null // Inactive when null

    fun updateEOSE(l: Long) {
        onEOSE?.let { it(l) }
    }

    fun toJsonString(): String = Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("id", JsonPrimitive(id))
            typedFilters?.let { filters ->
                put("typedFilters", JsonArray(filters.map { Json.encodeToJsonElement(TypedFilter.serializer(), it) }))
            }
        }
    )
}
