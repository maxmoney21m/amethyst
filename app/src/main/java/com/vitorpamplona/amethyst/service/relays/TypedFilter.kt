package com.vitorpamplona.amethyst.service.relays

import kotlinx.serialization.Serializable

@Serializable
class TypedFilter(
    val types: Set<FeedType>,
    val filter: JsonFilter
)
