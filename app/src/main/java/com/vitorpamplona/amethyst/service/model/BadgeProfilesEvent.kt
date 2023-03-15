package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class BadgeProfilesEvent(
    override val id: HexKey,
    @SerialName("pubkey")
    override val pubKey: HexKey,
    @SerialName("created_at")
    override val createdAt: Long,
    override val tags: List<List<String>>,
    override val content: String,
    override val sig: HexKey
) : Event() {
    override val kind: Int = BadgeProfilesEvent.kind

    fun badgeAwardEvents() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }

    fun badgeAwardDefinitions() = tags.filter { it.firstOrNull() == "a" }.mapNotNull {
        val aTagValue = it.getOrNull(1)
        val relay = it.getOrNull(2)

        if (aTagValue != null) ATag.parse(aTagValue, relay) else null
    }

    private fun dTag() = tags.filter { it.firstOrNull() == "d" }.mapNotNull { it.getOrNull(1) }.firstOrNull() ?: ""

    fun address() = ATag(kind, pubKey, dTag(), null)

    companion object {
        const val kind = 30008
    }
}
