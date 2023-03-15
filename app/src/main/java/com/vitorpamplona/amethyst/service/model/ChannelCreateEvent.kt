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
class ChannelCreateEvent(
    override val id: HexKey,
    @SerialName("pubkey")
    override val pubKey: HexKey,
    @SerialName("created_at")
    override val createdAt: Long,
    override val tags: List<List<String>>,
    override val content: String,
    override val sig: HexKey
) : Event() {
    override val kind = ChannelCreateEvent.kind

    fun channelInfo(): ChannelData = try {
        Json.decodeFromString(ChannelData.serializer(), content)
    } catch (e: Exception) {
        Log.e("ChannelMetadataEvent", "Can't parse channel info $content", e)
        ChannelData(null, null, null)
    }

    companion object {
        const val kind = 40

        fun create(channelInfo: ChannelData?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ChannelCreateEvent {
            val content = try {
                if (channelInfo != null) {
                    Json.encodeToString(ChannelData.serializer(), channelInfo)
                } else {
                    ""
                }
            } catch (t: Throwable) {
                Log.e("ChannelCreateEvent", "Couldn't parse channel information", t)
                ""
            }

            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = emptyList<List<String>>()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return ChannelCreateEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }

    @Serializable
    data class ChannelData(var name: String?, var about: String?, var picture: String?)
}
