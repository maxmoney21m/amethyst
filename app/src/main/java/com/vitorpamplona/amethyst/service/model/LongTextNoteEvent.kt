package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nostr.postr.Utils
import java.util.Date

@Serializable
class LongTextNoteEvent(
    override val id: HexKey,
    @SerialName("pubkey")
    override val pubKey: HexKey,
    @SerialName("created_at")
    override val createdAt: Long,
    override val tags: List<List<String>>,
    override val content: String,
    override val sig: HexKey
) : BaseTextNoteEvent() {
    override val kind: Int = LongTextNoteEvent.kind

    private fun dTag() = tags.filter { it.firstOrNull() == "d" }.mapNotNull { it.getOrNull(1) }.firstOrNull() ?: ""

    fun address() = ATag(kind, pubKey, dTag(), null)

    fun topics() = tags.filter { it.firstOrNull() == "t" }.mapNotNull { it.getOrNull(1) }

    fun title() = tags.filter { it.firstOrNull() == "title" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

    fun image() = tags.filter { it.firstOrNull() == "image" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

    fun summary() = tags.filter { it.firstOrNull() == "summary" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

    fun publishedAt() = try {
        tags.filter { it.firstOrNull() == "published_at" }.mapNotNull { it.getOrNull(1) }.firstOrNull()?.toLong()
    } catch (_: Exception) {
        null
    }

    companion object {
        const val kind = 30023

        fun create(msg: String, replyTos: List<String>?, mentions: List<String>?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): LongTextNoteEvent {
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf<List<String>>()
            replyTos?.forEach {
                tags.add(listOf("e", it))
            }
            mentions?.forEach {
                tags.add(listOf("p", it))
            }
            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = Utils.sign(id, privateKey)
            return LongTextNoteEvent(id.toHexKey(), pubKey, createdAt, tags, msg, sig.toHexKey())
        }
    }
}
