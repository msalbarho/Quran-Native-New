package com.quransunah.app.core

/**
 * Daily wird / khatma planner. Pure logic, no Android types, so it is unit tested.
 *
 * The plan is sequential: today's wird is always the next unfinished portion.
 * Calendar days only decide whether the reader is behind or ahead.
 */
object WirdPlan {
    const val MIN_DAYS = 1
    const val MAX_DAYS = AppConstants.TOTAL_PAGES
    val PRESET_DAYS = listOf(15, 30, 60)

    /** Splits [totalPages] into [days] contiguous, near-equal ranges covering every page once. */
    fun split(days: Int, totalPages: Int = AppConstants.TOTAL_PAGES): List<IntRange> {
        val n = days.coerceIn(MIN_DAYS, totalPages)
        return List(n) { i ->
            val start = (i.toLong() * totalPages / n).toInt() + 1
            val end = ((i + 1).toLong() * totalPages / n).toInt()
            start..end
        }
    }

    data class State(
        val days: Int,
        val completed: Int,
        val startEpochDay: Long,
    ) {
        val portions: List<IntRange> get() = split(days)
        val finished: Boolean get() = completed >= days
        val current: IntRange? get() = portions.getOrNull(completed)
        val progress: Float get() = (completed.toFloat() / days).coerceIn(0f, 1f)
        val portionsLeft: Int get() = (days - completed).coerceAtLeast(0)

        /** Calendar days left in the plan including today (never negative). */
        fun daysLeft(todayEpochDay: Long): Int =
            (days - (todayEpochDay - startEpochDay)).toInt().coerceIn(0, days)

        /** Positive = portions behind schedule, negative = ahead, 0 = on track. */
        fun scheduleDelta(todayEpochDay: Long): Int {
            val dayIndex = (todayEpochDay - startEpochDay).toInt().coerceIn(0, days - 1)
            return when {
                completed < dayIndex -> dayIndex - completed
                completed > dayIndex + 1 -> -(completed - dayIndex - 1)
                else -> 0
            }
        }

        /** True when today's share of the plan is already read. */
        fun todayDone(todayEpochDay: Long): Boolean {
            val dayIndex = (todayEpochDay - startEpochDay).toInt().coerceAtLeast(0)
            return finished || completed > dayIndex
        }
    }

    /**
     * Returns the new completed count after the reader lands on [page].
     * A portion completes when its last page (or the facing page right after it)
     * is reached, so jumping far into the Mushaf never ticks off unread days.
     */
    fun completedAfterVisit(state: State, page: Int): Int {
        val current = state.current ?: return state.completed
        return if (page == current.last || page == current.last + 1) state.completed + 1 else state.completed
    }
}
