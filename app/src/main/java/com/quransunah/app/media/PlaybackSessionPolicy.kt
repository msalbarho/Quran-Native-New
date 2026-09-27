package com.quransunah.app.media

import com.quransunah.app.data.catalog.SurahReciter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSessionPolicy @Inject constructor() {
    @Volatile
    var pauseAtEndOfItems: Boolean = false

    @Volatile
    var activeReciterId: Int? = null

    @Volatile
    var activeMoshafId: Int? = null

    fun activate(reciter: SurahReciter) {
        val moshaf = reciter.preferredMoshaf() ?: return
        activeReciterId = reciter.id
        activeMoshafId = moshaf.id
        pauseAtEndOfItems = false
    }
}
