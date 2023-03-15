package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nostr.postr.Utils
import java.util.Date

@Serializable
data class ContactMetaData(
    val name: String,
    val picture: String,
    val about: String,
    val nip05: String?
)

abstract class IdentityClaim(
    var identity: String,
    var proof: String
) {
    abstract fun toProofUrl(): String
    abstract fun toIcon(): Int
    abstract fun toDescriptor(): Int
    abstract fun platform(): String

    fun platformIdentity() = "${platform()}:$identity"

    companion object {
        fun create(platformIdentity: String, proof: String): IdentityClaim? {
            val (platform, identity) = platformIdentity.split(':')

            return when (platform.lowercase()) {
                GitHubIdentity.platform -> GitHubIdentity(identity, proof)
                TwitterIdentity.platform -> TwitterIdentity(identity, proof)
                TelegramIdentity.platform -> TelegramIdentity(identity, proof)
                MastodonIdentity.platform -> MastodonIdentity(identity, proof)
                else -> throw IllegalArgumentException("Platform $platform not supported")
            }
        }
    }
}

class GitHubIdentity(
    identity: String,
    proof: String
) : IdentityClaim(identity, proof) {
    override fun toProofUrl() = "https://gist.github.com/$identity/$proof"

    override fun platform() = platform
    override fun toIcon() = R.drawable.github
    override fun toDescriptor() = R.string.github

    companion object {
        val platform = "github"

        fun parseProofUrl(proofUrl: String): GitHubIdentity? {
            return try {
                if (proofUrl.isBlank()) return null
                val path = proofUrl.removePrefix("https://gist.github.com/").split("?")[0].split("/")

                GitHubIdentity(path[0], path[1])
            } catch (e: Exception) {
                null
            }
        }
    }
}

class TwitterIdentity(
    identity: String,
    proof: String
) : IdentityClaim(identity, proof) {
    override fun toProofUrl() = "https://twitter.com/$identity/status/$proof"

    override fun platform() = platform
    override fun toIcon() = R.drawable.twitter
    override fun toDescriptor() = R.string.twitter

    companion object {
        val platform = "twitter"

        fun parseProofUrl(proofUrl: String): TwitterIdentity? {
            return try {
                if (proofUrl.isBlank()) return null
                val path = proofUrl.removePrefix("https://twitter.com/").split("?")[0].split("/")

                TwitterIdentity(path[0], path[2])
            } catch (e: Exception) {
                null
            }
        }
    }
}

class TelegramIdentity(
    identity: String,
    proof: String
) : IdentityClaim(identity, proof) {
    override fun toProofUrl() = "https://t.me/$proof"

    override fun platform() = platform
    override fun toIcon() = R.drawable.telegram
    override fun toDescriptor() = R.string.telegram

    companion object {
        val platform = "telegram"
    }
}

class MastodonIdentity(
    identity: String,
    proof: String
) : IdentityClaim(identity, proof) {
    override fun toProofUrl() = "https://$identity/$proof"

    override fun platform() = platform
    override fun toIcon() = R.drawable.mastodon
    override fun toDescriptor() = R.string.mastodon

    companion object {
        val platform = "mastodon"

        fun parseProofUrl(proofUrl: String): MastodonIdentity? {
            return try {
                if (proofUrl.isBlank()) return null
                val path = proofUrl.removePrefix("https://").split("?")[0].split("/")

                return MastodonIdentity("${path[0]}/${path[1]}", path[2])
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Serializable
class MetadataEvent(
    override val id: HexKey,
    @SerialName("pubkey")
    override val pubKey: HexKey,
    @SerialName("created_at")
    override val createdAt: Long,
    override val tags: List<List<String>>,
    override val content: String,
    override val sig: HexKey
) : Event() {
    override val kind: Int = MetadataEvent.kind

    fun contactMetaData() = try {
        Json.decodeFromString(ContactMetaData.serializer(), content)
    } catch (e: Exception) {
        Log.e("MetadataEvent", "Can't parse $content", e)
        null
    }

    fun identityClaims() = tags.filter { it.firstOrNull() == "i" }.mapNotNull {
        try {
            IdentityClaim.create(it.get(1), it.get(2))
        } catch (e: Exception) {
            Log.e("MetadataEvent", "Can't parse identity [${it.joinToString { "," }}]", e)
            null
        }
    }

    companion object {
        const val kind = 0

        fun create(contactMetaData: ContactMetaData, identities: List<IdentityClaim>, privateKey: ByteArray, createdAt: Long = Date().time / 1000): MetadataEvent {
            return create(Json.encodeToString(ContactMetaData.serializer(), contactMetaData), identities, privateKey, createdAt = createdAt)
        }

        fun create(contactMetaData: String, identities: List<IdentityClaim>, privateKey: ByteArray, createdAt: Long = Date().time / 1000): MetadataEvent {
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf<List<String>>()

            identities.forEach {
                tags.add(listOf("i", it.platformIdentity(), it.proof))
            }

            val id = generateId(pubKey, createdAt, kind, tags, contactMetaData)
            val sig = Utils.sign(id, privateKey)
            return MetadataEvent(id.toHexKey(), pubKey, createdAt, tags, contactMetaData, sig.toHexKey())
        }
    }
}
