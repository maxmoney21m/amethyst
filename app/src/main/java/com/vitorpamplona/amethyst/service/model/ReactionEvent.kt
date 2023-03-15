package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nostr.postr.Utils
import java.util.Date

@Serializable
class ReactionEvent(
    override val id: HexKey,
    @SerialName("pubkey")
    override val pubKey: HexKey,
    @SerialName("created_at")
    override val createdAt: Long,
    override val tags: List<List<String>>,
    override val content: String,
    override val sig: HexKey
) : Event() {
    override val kind: Int = ReactionEvent.kind

    fun originalPost() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
    fun originalAuthor() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }
    fun taggedAddresses() = tags.filter { it.firstOrNull() == "a" }.mapNotNull {
        val aTagValue = it.getOrNull(1)
        val relay = it.getOrNull(2)

        if (aTagValue != null) ATag.parse(aTagValue, relay) else null
    }

    companion object {
        const val kind = 7

        fun createWarning(originalNote: Event, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ReactionEvent {
            return create("\u26A0\uFE0F", originalNote, privateKey, createdAt)
        }

        fun createLike(originalNote: Event, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ReactionEvent {
            return create("+", originalNote, privateKey, createdAt)
        }

        fun create(content: String, originalNote: Event, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ReactionEvent {
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()

            var tags = listOf(listOf("e", originalNote.id), listOf("p", originalNote.pubKey))
            if (originalNote is LongTextNoteEvent) {
                tags = tags + listOf(listOf("a", originalNote.address().toTag()))
            }

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return ReactionEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
