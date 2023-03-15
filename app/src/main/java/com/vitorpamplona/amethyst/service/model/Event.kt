package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import fr.acinq.secp256k1.Hex
import fr.acinq.secp256k1.Secp256k1
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

object EventSerializer : JsonContentPolymorphicSerializer<Event>(Event::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out Event> {
        if ("kind" in element.jsonObject) {
            val serializer = when (Json.decodeFromJsonElement(Int.serializer(), element.jsonObject["kind"]!!)) {
                BadgeAwardEvent.kind -> BadgeAwardEvent.serializer()
                BadgeDefinitionEvent.kind -> BadgeDefinitionEvent.serializer()
                BadgeProfilesEvent.kind -> BadgeProfilesEvent.serializer()
                ChannelCreateEvent.kind -> ChannelCreateEvent.serializer()
                ChannelHideMessageEvent.kind -> ChannelHideMessageEvent.serializer()
                ChannelMessageEvent.kind -> ChannelMessageEvent.serializer()
                ChannelMetadataEvent.kind -> ChannelMetadataEvent.serializer()
                ChannelMuteUserEvent.kind -> ChannelMuteUserEvent.serializer()
                ContactListEvent.kind -> ContactListEvent.serializer()
                DeletionEvent.kind -> DeletionEvent.serializer()
                LnZapEvent.kind -> LnZapEvent.serializer()
                LnZapRequestEvent.kind -> LnZapRequestEvent.serializer()
                LongTextNoteEvent.kind -> LongTextNoteEvent.serializer()
                MetadataEvent.kind -> MetadataEvent.serializer()
                PrivateDmEvent.kind -> PrivateDmEvent.serializer()
                ReactionEvent.kind -> ReactionEvent.serializer()
                RecommendRelayEvent.kind -> RecommendRelayEvent.serializer()
                ReportEvent.kind -> ReportEvent.serializer()
                RepostEvent.kind -> RepostEvent.serializer()
                TextNoteEvent.kind -> TextNoteEvent.serializer()
                else -> Event.serializer()
            }
            return serializer
        }
        return Event.serializer()
    }
}

@Serializable(with = EventSerializer::class)
sealed class Event {
    abstract val id: HexKey

    @SerialName("pubkey")
    abstract val pubKey: HexKey

    @SerialName("created_at")
    abstract val createdAt: Long
    abstract val kind: Int
    abstract val tags: List<List<String>>
    abstract val content: String
    abstract val sig: HexKey

//    override fun toJson(): String = gson.toJson(this)
    fun toJson(): String = Json.encodeToString(Event.serializer(), this)

    fun taggedUsers() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }

    fun isTaggedUser(idHex: String) = tags.any { it.getOrNull(0) == "p" && it.getOrNull(1) == idHex }

    /**
     * Checks if the ID is correct and then if the pubKey's secret key signed the event.
     */
    fun checkSignature() {
        if (!id.contentEquals(generateId())) {
            throw Exception(
                """|Unexpected ID.
                   |  Event: ${Json.encodeToString(Event.serializer(), this)}
                   |  Actual ID: $id
                   |  Generated: ${generateId()}
                """.trimIndent()
            )
        }
        if (!secp256k1.verifySchnorr(Hex.decode(sig), Hex.decode(id), Hex.decode(pubKey))) {
            throw Exception("""Bad signature!""")
        }
    }

    fun hasValidSignature(): Boolean {
        if (!id.contentEquals(generateId())) {
            return false
        }

        return secp256k1.verifySchnorr(Hex.decode(sig), Hex.decode(id), Hex.decode(pubKey))
    }

    private fun generateId(): String {
        val jsonString = rawJsonStringForId(pubKey, createdAt, kind, tags, content)

        // GSON decided to hardcode these replacements.
        // They break Nostr's hash check.
        // These lines revert their code.
        // https://github.com/google/gson/issues/2295
//        val rawEventJson = gson.toJson(rawEvent)
//            .replace("\\u2028", "\u2028")
//            .replace("\\u2029", "\u2029")

        return sha256.digest(jsonString.toByteArray()).toHexKey()
    }

    companion object {
        private val secp256k1 = Secp256k1.get()

        val sha256: MessageDigest = MessageDigest.getInstance("SHA-256")

        fun fromJson(json: String, lenient: Boolean = false): Event =
            Json.decodeFromString(serializer(), json)

        fun fromJson(json: JsonElement, lenient: Boolean = false): Event =
            Json.decodeFromJsonElement(serializer(), json)

        fun generateId(pubKey: HexKey, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): ByteArray {
            val jsonString = rawJsonStringForId(pubKey, createdAt, kind, tags, content)
            return sha256.digest(jsonString.toByteArray())
        }

        private fun rawJsonStringForId(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: List<List<String>>,
            content: String
        ): String = Json.encodeToString(
            JsonArray.serializer(),
            buildJsonArray {
                add(JsonPrimitive(0))
                add(JsonPrimitive(pubKey))
                add(JsonPrimitive(createdAt))
                add(JsonPrimitive(kind))
                addJsonArray {
                    for (tag in tags) {
                        addJsonArray {
                            for (item in tag) {
                                add(JsonPrimitive(item))
                            }
                        }
                    }
                }
                add(JsonPrimitive(content))
            }
        )
    }
}
