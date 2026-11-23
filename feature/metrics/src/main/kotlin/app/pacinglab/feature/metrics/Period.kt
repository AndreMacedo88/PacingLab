package app.pacinglab.feature.metrics

import app.pacinglab.core.designsystem.component.Span
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The spans a detail page offers: each a fixed number of days, so that the arrows step through history. */
val PERIOD_SPANS = listOf(Span.WEEK, Span.MONTH, Span.THREE_MONTHS, Span.YEAR)

/** The [span] of days ending with [end], both ends included; [span] is one of [PERIOD_SPANS]. */
data class Period(val span: Span, val end: LocalDate) {
    private val length: Long get() = requireNotNull(span.days)

    val start: LocalDate get() = end.minusDays(length - 1)

    val days: List<LocalDate> get() = (0 until length).map { start.plusDays(it) }

    fun previous(): Period = copy(end = end.minusDays(length))

    /** The next period, but never past [today]. */
    fun next(today: LocalDate): Period = copy(end = minOf(end.plusDays(length), today))

    fun isCurrent(today: LocalDate): Boolean = !end.isBefore(today)

    /** "22–28 Oct", "29 Sep – 28 Oct", "30 Oct 2025 – 28 Oct 2026". */
    fun label(locale: Locale): String {
        val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
        val full = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        return when {
            start.year != end.year -> "${full.format(start)} – ${full.format(end)}"
            start.month == end.month -> "${start.dayOfMonth}–${dayMonth.format(end)}"
            else -> "${dayMonth.format(start)} – ${dayMonth.format(end)}"
        }
    }

    /** Labels under the chart: each weekday for a week, then sparser marks as the span grows. */
    fun axisLabels(locale: Locale): List<Pair<Int, String>> = when (span) {
        Span.WEEK -> days.mapIndexed { i, day -> i to DateTimeFormatter.ofPattern("EEEEE", locale).format(day) }

        Span.MONTH -> days.indices.filter { (length - 1 - it) % 7 == 0L }
            .map { it to DateTimeFormatter.ofPattern("d MMM", locale).format(days[it]) }

        else -> days.indices.filter { days[it].dayOfMonth == 1 }
            .filterIndexed { n, _ -> span != Span.YEAR || n % 2 == 0 }
            .map { it to DateTimeFormatter.ofPattern("MMM", locale).format(days[it]) }
    }

    fun indexOf(date: LocalDate): Int = ChronoUnit.DAYS.between(start, date).toInt()
}
