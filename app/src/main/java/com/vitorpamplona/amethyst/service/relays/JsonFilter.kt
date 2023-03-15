package com.vitorpamplona.amethyst.service.relays

import com.vitorpamplona.amethyst.service.model.Event
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

interface Filter {
    fun match(event: Event): Boolean
    fun toShortString(): String
}

object JsonFilterSerializer : JsonTransformingSerializer<JsonFilter>(JsonFilter.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject.toMutableMap()

        obj["tags"] = JsonObject(
            obj.entries
                .filter { it.key.startsWith("#") }
                .associate { it.key.substring(1) to it.value }
        )

        return JsonObject(obj)
    }
}

@Serializable
class JsonFilter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    val tags: Map<String, List<String>>? = null,
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
    val search: String? = null
) : Filter {
    override fun match(event: Event): Boolean {
        if (ids?.any { event.id == it } == false) return false
        if (kinds?.any { event.kind == it } == false) return false
        if (authors?.any { event.pubKey == it } == false) return false
        tags?.forEach { tag ->
            if (!event.tags.any { it.first() == tag.key && it[1] in tag.value }) return false
        }
        if (event.createdAt !in (since ?: Long.MIN_VALUE)..(until ?: Long.MAX_VALUE)) {
            return false
        }
        return true
    }

//    override fun toString(): String = "JsonFilter${toJson()}"

    override fun toShortString(): String {
        val list = ArrayList<String>()
        ids?.run {
            list.add("ids")
        }
        authors?.run {
            list.add("authors")
        }
        kinds?.run {
            list.add("kinds[${kinds.joinToString()}]")
        }
        tags?.run {
            list.add("tags")
        }
        since?.run {
            list.add("since")
        }
        until?.run {
            list.add("until")
        }
        limit?.run {
            list.add("limit")
        }
        search?.run {
            list.add("search")
        }
        return list.joinToString()
    }

    fun toJsonString() = Json.encodeToString(JsonFilterSerializer, this)
}
