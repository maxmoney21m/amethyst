package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nostr.postr.Utils
import java.util.Date

@Serializable
class ChannelHideMessageEvent(
    override val id: HexKey,
    @SerialName("pubkey")
    override val pubKey: HexKey,
    @SerialName("created_at")
    override val createdAt: Long,
    override val tags: List<List<String>>,
    override val content: String,
    override val sig: HexKey
) : Event() {
    override val kind: Int = ChannelHideMessageEvent.kind

    fun eventsToHide() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }

    companion object {
        const val kind = 43

        fun create(reason: String, messagesToHide: List<String>?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ChannelHideMessageEvent {
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags =
                messagesToHide?.map {
                    listOf("e", it)
                } ?: emptyList()

            val id = generateId(pubKey, createdAt, kind, tags, reason)
            val sig = Utils.sign(id, privateKey)
            return ChannelHideMessageEvent(id.toHexKey(), pubKey, createdAt, tags, reason, sig.toHexKey())
        }
    }
}
