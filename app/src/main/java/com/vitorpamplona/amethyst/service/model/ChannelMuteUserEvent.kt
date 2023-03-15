package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nostr.postr.Utils
import java.util.Date

@Serializable
class ChannelMuteUserEvent(
    override val id: HexKey,
    @SerialName("pubkey")
    override val pubKey: HexKey,
    @SerialName("created_at")
    override val createdAt: Long,
    override val tags: List<List<String>>,
    override val content: String,
    override val sig: HexKey
) : Event() {
    override val kind: Int = ChannelMuteUserEvent.kind

    fun usersToMute() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }

    companion object {
        const val kind = 44

        fun create(reason: String, usersToMute: List<String>?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ChannelMuteUserEvent {
            val content = reason
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags =
                usersToMute?.map {
                    listOf("p", it)
                } ?: emptyList()

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return ChannelMuteUserEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
