package com.vitorpamplona.amethyst.service.model

import androidx.lifecycle.LiveData
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.pow.PowWorker
import fr.acinq.secp256k1.Hex
import fr.acinq.secp256k1.Secp256k1
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.reflect.Type
import java.security.MessageDigest
import java.util.Date
import nostr.postr.Utils
import nostr.postr.toHex

open class Event(
    val id: HexKey,
    @SerializedName("pubkey") val pubKey: HexKey,
    @SerializedName("created_at") val createdAt: Long,
    val kind: Int,
    var tags: List<List<String>>,
    val content: String,
    val sig: HexKey
) {
    fun toJson(): String = gson.toJson(this)

    fun generateId(): String {
        val rawEvent = listOf(
            0,
            pubKey,
            createdAt,
            kind,
            tags,
            content
        )

        // GSON decided to hardcode these replacements.
        // They break Nostr's hash check.
        // These lines revert their code.
        // https://github.com/google/gson/issues/2295
        val rawEventJson = gson.toJson(rawEvent)
            .replace("\\u2028", "\u2028")
            .replace("\\u2029", "\u2029")

        return sha256.digest(rawEventJson.toByteArray()).toHexKey()
    }

    /**
     * Checks if the ID is correct and then if the pubKey's secret key signed the event.
     */
    fun checkSignature() {
        if (!id.contentEquals(generateId())) {
            throw Exception(
                """|Unexpected ID.
                   |  Event: ${toJson()}
                   |  Actual ID: ${id}
                   |  Generated: ${generateId()}""".trimIndent()
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
        if (!Secp256k1.get().verifySchnorr(Hex.decode(sig), Hex.decode(id), Hex.decode(pubKey))) {
            return false
        }

        return true
    }

    class EventDeserializer : JsonDeserializer<Event> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Event {
            val jsonObject = json.asJsonObject
            return Event(
                id = jsonObject.get("id").asString,
                pubKey = jsonObject.get("pubkey").asString,
                createdAt = jsonObject.get("created_at").asLong,
                kind = jsonObject.get("kind").asInt,
                tags = jsonObject.get("tags").asJsonArray.map {
                    it.asJsonArray.map { s -> s.asString }
                },
                content = jsonObject.get("content").asString,
                sig = jsonObject.get("sig").asString
            )
        }
    }

    class EventSerializer : JsonSerializer<Event> {
        override fun serialize(
            src: Event,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return JsonObject().apply {
                addProperty("id", src.id)
                addProperty("pubkey", src.pubKey)
                addProperty("created_at", src.createdAt)
                addProperty("kind", src.kind)
                add("tags", JsonArray().also { jsonTags ->
                    src.tags.forEach { tag ->
                        jsonTags.add(JsonArray().also { jsonTagElement ->
                            tag.forEach { tagElement ->
                                jsonTagElement.add(tagElement)
                            }
                        })
                    }
                })
                addProperty("content", src.content)
                addProperty("sig", src.sig)
            }
        }
    }

    class ByteArrayDeserializer : JsonDeserializer<ByteArray> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): ByteArray = Hex.decode(json.asString)
    }

    class ByteArraySerializer : JsonSerializer<ByteArray> {
        override fun serialize(
            src: ByteArray,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ) = JsonPrimitive(src.toHex())
    }

    companion object {
        private val miner: WorkManager = WorkManager.getInstance(Amethyst.instance)

        private val secp256k1 = Secp256k1.get()

        val sha256: MessageDigest = MessageDigest.getInstance("SHA-256")
        val gson: Gson = GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(Event::class.java, EventSerializer())
            .registerTypeAdapter(Event::class.java, EventDeserializer())
            .registerTypeAdapter(ByteArray::class.java, ByteArraySerializer())
            .registerTypeAdapter(ByteArray::class.java, ByteArrayDeserializer())
            .create()

        fun fromJson(json: String, lenient: Boolean = false): Event =
            gson.fromJson(json, Event::class.java).getRefinedEvent(lenient)

        fun fromJson(json: JsonElement, lenient: Boolean = false): Event =
            gson.fromJson(json, Event::class.java).getRefinedEvent(lenient)

        fun Event.getRefinedEvent(lenient: Boolean = false): Event = when (kind) {
            BadgeAwardEvent.kind -> BadgeAwardEvent(id, pubKey, createdAt, tags, content, sig)
            BadgeDefinitionEvent.kind -> BadgeDefinitionEvent(
                id,
                pubKey,
                createdAt,
                tags,
                content,
                sig
            )

            BadgeProfilesEvent.kind -> BadgeProfilesEvent(id, pubKey, createdAt, tags, content, sig)

            ChannelCreateEvent.kind -> ChannelCreateEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelHideMessageEvent.kind -> ChannelHideMessageEvent(
                id,
                pubKey,
                createdAt,
                tags,
                content,
                sig
            )

            ChannelMessageEvent.kind -> ChannelMessageEvent(
                id,
                pubKey,
                createdAt,
                tags,
                content,
                sig
            )

            ChannelMetadataEvent.kind -> ChannelMetadataEvent(
                id,
                pubKey,
                createdAt,
                tags,
                content,
                sig
            )

            ChannelMuteUserEvent.kind -> ChannelMuteUserEvent(
                id,
                pubKey,
                createdAt,
                tags,
                content,
                sig
            )

            ContactListEvent.kind -> ContactListEvent(id, pubKey, createdAt, tags, content, sig)
            DeletionEvent.kind -> DeletionEvent(id, pubKey, createdAt, tags, content, sig)

            LnZapEvent.kind -> LnZapEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapRequestEvent.kind -> LnZapRequestEvent(id, pubKey, createdAt, tags, content, sig)
            LongTextNoteEvent.kind -> LongTextNoteEvent(id, pubKey, createdAt, tags, content, sig)
            MetadataEvent.kind -> MetadataEvent(id, pubKey, createdAt, tags, content, sig)
            PrivateDmEvent.kind -> PrivateDmEvent(id, pubKey, createdAt, tags, content, sig)
            ReactionEvent.kind -> ReactionEvent(id, pubKey, createdAt, tags, content, sig)
            RecommendRelayEvent.kind -> RecommendRelayEvent(
                id,
                pubKey,
                createdAt,
                tags,
                content,
                sig,
                lenient
            )

            ReportEvent.kind -> ReportEvent(id, pubKey, createdAt, tags, content, sig)
            RepostEvent.kind -> RepostEvent(id, pubKey, createdAt, tags, content, sig)
            TextNoteEvent.kind -> TextNoteEvent(id, pubKey, createdAt, tags, content, sig)
            else -> this
        }

        fun generateId(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: List<List<String>>,
            content: String,
        ): ByteArray {
            val rawEvent = listOf(
                0,
                pubKey,
                createdAt,
                kind,
                tags,
                content
            )
            val rawEventJson = gson.toJson(rawEvent)
            return sha256.digest(rawEventJson.toByteArray())
        }

        fun generateIdWithPow(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: List<List<String>>,
            content: String,
            work: Int = 0,
        ) {
            val event = Event(
                id = HexKey(),
                pubKey = pubKey,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = HexKey(),
            )
            val eventJson = event.toJson()

            val inputData = Data.Builder()
                .putInt("WORK_TARGET", work)
                .putString("RAW_EVENT", eventJson)
                .build()
            val request = OneTimeWorkRequest.Builder(PowWorker::class.java)
                .addTag("POW_OUTPUT")
                .setInputData(inputData)
                .build()

            miner.enqueue(request)
        }

        fun create(
            privateKey: ByteArray,
            kind: Int,
            tags: List<List<String>> = emptyList(),
            content: String = "",
            createdAt: Long = Date().time / 1000
        ): Event {
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val id = Companion.generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey).toHexKey()
            return Event(id.toHexKey(), pubKey, createdAt, kind, tags, content, sig)
        }
    }
}
