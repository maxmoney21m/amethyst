package com.vitorpamplona.amethyst.service.model

import java.math.BigDecimal

sealed class LnZapEventInterface : Event() {
    abstract fun zappedPost(): List<String>

    abstract fun zappedAuthor(): List<String>

    abstract fun taggedAddresses(): List<ATag>

    abstract fun amount(): BigDecimal?

    abstract fun containedPost(): Event?
}
