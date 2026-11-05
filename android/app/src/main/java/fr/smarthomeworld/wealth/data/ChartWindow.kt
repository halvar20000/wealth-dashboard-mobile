package fr.smarthomeworld.wealth.data

import java.time.LocalDate

/**
 * The eight windows the dashboard measures a return over, and how a
 * chart draws each of them.
 *
 * The line comes from `net_worth_history`, which knows fewer windows
 * (1m, 3m, 6m, ytd, 1y, all): a day and a week are cut from the month,
 * three years from everything. Its points are daily for a month and
 * thin out beyond, so a day is the last two of them.
 */
object ChartWindow {
    /** Shown first, as before the windows could be picked. */
    const val DEFAULT = "1y"

    /** The history the dashboard is asked for to draw a window. */
    fun serverPeriod(key: String): String = when (key) {
        "1d", "1w", "1m" -> "1m"
        "3m" -> "3m"
        "ytd" -> "ytd"
        "1y" -> "1y"
        else -> "all"
    }

    /** The first day a window covers, where the history asked for is
     *  wider than the window; null where it is the window itself. */
    fun cutoff(key: String, today: LocalDate = LocalDate.now()): String? = when (key) {
        "1d" -> today.minusDays(1)
        "1w" -> today.minusDays(7)
        "3y" -> today.minusYears(3)
        else -> null
    }?.toString()

    /** The points of a window, never fewer than two while there are
     *  two: a line needs both ends. */
    fun trim(points: List<HistoryPoint>, key: String, today: LocalDate = LocalDate.now()): List<HistoryPoint> {
        val from = cutoff(key, today) ?: return points
        val kept = points.filter { it.date >= from }
        return if (kept.size >= 2 || points.size < 2) kept else points.takeLast(2)
    }
}
