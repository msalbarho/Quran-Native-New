package com.quransunah.app.ui.mushaf

import com.quransunah.app.domain.model.DivisionInfo
import org.junit.Assert.*
import org.junit.Test

class HeaderQuarterTest {
    private fun boundary(number: Int, page: Int) =
        DivisionInfo(number, "quarter", number, "", page, 1, 1)

    @Test fun keepsQuarterAcrossPagesWithoutBoundary() {
        val quarters = listOf(boundary(1, 1), boundary(2, 5), boundary(3, 9))
        assertEquals(2, currentHeaderQuarter(8, quarters)?.number)
        assertEquals(3, currentHeaderQuarter(9, quarters)?.number)
        assertEquals(2, currentHeaderQuarter(5, quarters)?.number)
    }

    @Test fun supportsReverseNavigationAndUnsortedData() {
        val quarters = listOf(boundary(240, 599), boundary(2, 5), boundary(1, 1))
        assertEquals(240, currentHeaderQuarter(604, quarters)?.number)
        assertEquals(1, currentHeaderQuarter(2, quarters)?.number)
    }

    @Test fun usesLastBoundaryWhenSeveralStartOnSamePage() {
        assertEquals(3, currentHeaderQuarter(10,
            listOf(boundary(3, 10), boundary(2, 10)))?.number)
    }

    @Test fun handlesUnloadedDataAndFutureBoundaries() {
        assertNull(currentHeaderQuarter(1, emptyList()))
        assertNull(currentHeaderQuarter(1, listOf(boundary(2, 5))))
    }
}
