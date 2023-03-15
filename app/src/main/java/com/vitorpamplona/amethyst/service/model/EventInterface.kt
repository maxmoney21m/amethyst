package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface EventInterface {
    val id: HexKey

    @SerialName("pubkey")
    val pubKey: HexKey

    @SerialName("created_at")
    val createdAt: Long
    val kind: Int
    val tags: List<List<String>>
    val content: String
    val sig: HexKey

    fun toJson(): String

    fun checkSignature()

    fun hasValidSignature(): Boolean

    fun isTaggedUser(loggedInUser: String): Boolean
}
