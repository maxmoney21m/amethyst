package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.service.lnurl.LnInvoiceUtil
import com.vitorpamplona.amethyst.service.relays.Client
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
class LnZapEvent(
    override val id: HexKey,
    @SerialName("pubkey")
    override val pubKey: HexKey,
    @SerialName("created_at")
    override val createdAt: Long,
    override val tags: List<List<String>>,
    override val content: String,
    override val sig: HexKey
) : LnZapEventInterface() {
    override val kind: Int = LnZapEvent.kind

    override fun zappedPost() = tags
        .filter { it.firstOrNull() == "e" }
        .mapNotNull { it.getOrNull(1) }

    override fun zappedAuthor() = tags
        .filter { it.firstOrNull() == "p" }
        .mapNotNull { it.getOrNull(1) }

    override fun taggedAddresses(): List<ATag> = tags
        .filter { it.firstOrNull() == "a" }
        .mapNotNull {
            val aTagValue = it.getOrNull(1)
            val relay = it.getOrNull(2)

            if (aTagValue != null) ATag.parse(aTagValue, relay) else null
        }

    override fun amount(): BigDecimal? {
        return amount
    }

    // Keeps this as a field because it's a heavier function used everywhere.
    val amount by lazy {
        try {
            lnInvoice()?.let { LnInvoiceUtil.getAmountInSats(it) }
        } catch (e: Exception) {
            Log.e("LnZapEvent", "Failed to Parse LnInvoice ${description()}", e)
            null
        }
    }

    override fun containedPost(): Event? = try {
        description()?.let {
            fromJson(it, Client.lenient)
        }
    } catch (e: Exception) {
        Log.e("LnZapEvent", "Failed to Parse Contained Post ${description()}", e)
        null
    }

    private fun lnInvoice(): String? = tags
        .filter { it.firstOrNull() == "bolt11" }
        .mapNotNull { it.getOrNull(1) }
        .firstOrNull()

    private fun description(): String? = tags
        .filter { it.firstOrNull() == "description" }
        .mapNotNull { it.getOrNull(1) }
        .firstOrNull()

    companion object {
        const val kind = 9735
    }
}
