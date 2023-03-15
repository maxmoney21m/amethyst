package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nostr.postr.Utils
import java.net.URI
import java.util.Date

@Serializable
class RecommendRelayEvent(
    override val id: HexKey,
    @SerialName("pubkey")
    override val pubKey: HexKey,
    @SerialName("created_at")
    override val createdAt: Long,
    override val tags: List<List<String>>,
    override val content: String,
    override val sig: HexKey,
    val lenient: Boolean = false
) : Event() {
    override val kind: Int = RecommendRelayEvent.kind

    fun relay() = if (lenient) {
        URI.create(content.trim())
    } else {
        URI.create(content)
    }

    companion object {
        const val kind = 2

        fun create(relay: URI, privateKey: ByteArray, createdAt: Long = Date().time / 1000): RecommendRelayEvent {
            val content = relay.toString()
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = listOf<List<String>>()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return RecommendRelayEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
