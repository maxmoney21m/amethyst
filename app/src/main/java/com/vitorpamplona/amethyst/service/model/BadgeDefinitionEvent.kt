package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class BadgeDefinitionEvent(
    override val id: HexKey,
    @SerialName("pubkey")
    override val pubKey: HexKey,
    @SerialName("created_at")
    override val createdAt: Long,
    override val tags: List<List<String>>,
    override val content: String,
    override val sig: HexKey
) : Event() {
    override val kind: Int = BadgeDefinitionEvent.kind

    private fun dTag() = tags.filter { it.firstOrNull() == "d" }.mapNotNull { it.getOrNull(1) }.firstOrNull() ?: ""

    fun address() = ATag(kind, pubKey, dTag(), null)

    fun name() = tags.filter { it.firstOrNull() == "name" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

    fun thumb() = tags.filter { it.firstOrNull() == "thumb" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

    fun image() = tags.filter { it.firstOrNull() == "image" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

    fun description() = tags.filter { it.firstOrNull() == "description" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

    companion object {
        const val kind = 30009
    }
}
