package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nostr.postr.Utils
import java.util.Date

@Serializable
class ChannelMetadataEvent(
    override val id: HexKey,
    @SerialName("pubkey")
    override val pubKey: HexKey,
    @SerialName("created_at")
    override val createdAt: Long,
    override val tags: List<List<String>>,
    override val content: String,
    override val sig: HexKey
) : Event() {
    override val kind: Int = ChannelMessageEvent.kind

    fun channel() = tags.firstOrNull { it.firstOrNull() == "e" }?.getOrNull(1)
    fun channelInfo() =
        try {
            Json.decodeFromString(ChannelCreateEvent.ChannelData.serializer(), content)
        } catch (e: Exception) {
            Log.e("ChannelMetadataEvent", "Can't parse channel info $content", e)
            ChannelCreateEvent.ChannelData(null, null, null)
        }

    companion object {
        const val kind = 41

        fun create(newChannelInfo: ChannelCreateEvent.ChannelData?, originalChannelIdHex: String, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ChannelMetadataEvent {
            val content =
                if (newChannelInfo != null) {
                    Json.encodeToString(ChannelCreateEvent.ChannelData.serializer(), newChannelInfo)
                } else {
                    ""
                }

            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = listOf(listOf("e", originalChannelIdHex, "", "root"))
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return ChannelMetadataEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
