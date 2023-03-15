package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nostr.postr.Utils
import java.util.Date

@Serializable
class ChannelMessageEvent(
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

    fun channel() = tags.firstOrNull { it[0] == "e" && it.size > 3 && it[3] == "root" }?.getOrNull(1) ?: tags.firstOrNull { it.firstOrNull() == "e" }?.getOrNull(1)

    fun replyTos() = tags.filter { it.getOrNull(1) != channel() }.mapNotNull { it.getOrNull(1) }

    fun mentions() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }

    companion object {
        const val kind = 42

        fun create(content: String, channel: String, replyTos: List<String>? = null, mentions: List<String>? = null, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ChannelMessageEvent {
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf(
                listOf("e", channel, "", "root")
            )
            replyTos?.forEach {
                tags.add(listOf("e", it))
            }
            mentions?.forEach {
                tags.add(listOf("p", it))
            }

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return ChannelMessageEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
