package com.quransunah.app.ui.mushaf

import android.content.res.Resources
import com.quransunah.app.R
import com.quransunah.app.core.ArabicOrdinals

/** Header divisions are written out; Quran text and page/ayah numbering are untouched. */
internal fun headerNumberWords(value: Int, languageTag: String, resources: Resources): String {
    require(value in 1..60)
    if (languageTag == "ar") return ArabicOrdinals.masculine(value)
    return resources.getStringArray(R.array.mushaf_header_numbers)[value - 1]
}
