package com.quransunah.app.ui.search

/**
 * Pages Mushaf verse search through the same offset query the index uses.
 * One extra row is requested so the caller can tell that another page exists
 * without a separate count query.
 */
internal object MushafSearchPaging {
    const val PAGE_SIZE = 40

    fun <T> append(existing: List<T>, fetched: List<T>): MushafSearchPage<T> {
        val page = fetched.take(PAGE_SIZE)
        return MushafSearchPage(
            items = existing + page,
            hasMore = fetched.size > PAGE_SIZE,
        )
    }
}

internal data class MushafSearchPage<T>(
    val items: List<T>,
    val hasMore: Boolean,
)
