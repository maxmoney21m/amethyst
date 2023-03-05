package com.vitorpamplona.amethyst.ui.dal

import android.util.Log
import com.vitorpamplona.amethyst.model.Account
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

abstract class FeedFilter<T>() {
  lateinit var account: Account

  @OptIn(ExperimentalTime::class)
  fun loadTop(): List<T> {
    val (feed, elapsed) = measureTimedValue {
      feed().take(1000)
    }

    Log.d("Time", "${this.javaClass.simpleName} Feed in ${elapsed} with ${feed.size} objects")
    return feed
  }

  abstract fun feed(): List<T>
}
