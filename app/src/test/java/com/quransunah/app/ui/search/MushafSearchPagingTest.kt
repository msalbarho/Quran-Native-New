package com.quransunah.app.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MushafSearchPagingTest {
    @Test
    fun loadsEveryMatchPastTheOldFortyResultCap() {
        val matches = (1..89).map { index -> "$index:1" } + "114:6"
        var loaded = emptyList<String>()
        var hasMore = true
        var pages = 0

        while (hasMore) {
            val fetched = matches.drop(loaded.size).take(MushafSearchPaging.PAGE_SIZE + 1)
            val page = MushafSearchPaging.append(loaded, fetched)
            loaded = page.items
            hasMore = page.hasMore
            pages++
            check(pages < 10) { "paging did not finish" }
        }

        assertTrue(loaded.size > 40)
        assertEquals(matches, loaded)
        assertEquals(loaded.size, loaded.distinct().size)
        assertEquals("114:6", loaded.last())
        assertFalse(hasMore)
    }

    @Test
    fun fortyMatchesDoNotRequestAnotherPage() {
        val matches = (1..40).map { "2:$it" }
        val page = MushafSearchPaging.append(
            emptyList(),
            matches.drop(0).take(MushafSearchPaging.PAGE_SIZE + 1),
        )

        assertEquals(matches, page.items)
        assertFalse(page.hasMore)
    }

    @Test
    fun theFortyFirstMatchOpensTheNextPage() {
        val matches = (1..41).map { "114:$it" }
        val first = MushafSearchPaging.append(
            emptyList(),
            matches.take(MushafSearchPaging.PAGE_SIZE + 1),
        )

        assertEquals(40, first.items.size)
        assertTrue(first.hasMore)

        val second = MushafSearchPaging.append(
            first.items,
            matches.drop(first.items.size).take(MushafSearchPaging.PAGE_SIZE + 1),
        )

        assertEquals(matches, second.items)
        assertEquals(second.items.size, second.items.distinct().size)
        assertFalse(second.hasMore)
    }
}
