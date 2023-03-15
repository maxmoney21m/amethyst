package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.decodePublicKey
import com.vitorpamplona.amethyst.model.toHexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import nostr.postr.Utils
import java.util.Date

data class Contact(val pubKeyHex: String, val relayUri: String?)

@Serializable
class ContactListEvent(
    override val id: HexKey,
    @SerialName("pubkey")
    override val pubKey: HexKey,
    @SerialName("created_at")
    override val createdAt: Long,
    override val tags: List<List<String>>,
    override val content: String,
    override val sig: HexKey
) : Event() {
    override val kind: Int = ContactListEvent.kind

    // This function is only used by the user logged in
    // But it is used all the time.
    val verifiedFollowKeySet: Set<HexKey> by lazy {
        tags.filter { it[0] == "p" }.mapNotNull {
            it.getOrNull(1)?.let { unverifiedHex: String ->
                try {
                    decodePublicKey(unverifiedHex).toHexKey()
                } catch (e: Exception) {
                    Log.w("ContactListEvent", "Can't parse tags as a follows: ${it[1]}", e)
                    null
                }
            }
        }.toSet()
    }

    val verifiedFollowKeySetAndMe: Set<HexKey> by lazy {
        verifiedFollowKeySet + pubKey
    }

    fun unverifiedFollowKeySet() = tags.filter { it[0] == "p" }.mapNotNull { it.getOrNull(1) }

    fun follows() = tags.filter { it[0] == "p" }.mapNotNull {
        try {
            Contact(decodePublicKey(it[1]).toHexKey(), it.getOrNull(2))
        } catch (e: Exception) {
            Log.w("ContactListEvent", "Can't parse tags as a follows: ${it[1]}", e)
            null
        }
    }

    fun relays(): Map<String, ReadWrite>? = try {
        if (content.isNotEmpty()) {
            Json.decodeFromString(MapSerializer(String.serializer(), ReadWrite.serializer()), content)
        } else {
            null
        }
    } catch (e: Exception) {
        Log.w("ContactListEvent", "Can't parse content as relay lists: $content", e)
        null
    }

    companion object {
        const val kind = 3

        fun create(follows: List<Contact>, relayUse: Map<String, ReadWrite>?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ContactListEvent {
            val content = if (relayUse != null) {
                Json.encodeToString(MapSerializer(String.serializer(), ReadWrite.serializer()), relayUse)
            } else {
                ""
            }
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = follows.map {
                if (it.relayUri != null) {
                    listOf("p", it.pubKeyHex, it.relayUri)
                } else {
                    listOf("p", it.pubKeyHex)
                }
            }
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return ContactListEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }

    @Serializable
    data class ReadWrite(val read: Boolean, val write: Boolean)
}
