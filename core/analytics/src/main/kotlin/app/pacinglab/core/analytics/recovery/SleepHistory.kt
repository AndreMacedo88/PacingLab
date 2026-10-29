package app.pacinglab.core.analytics.recovery

import java.time.LocalDate

/**
 * Every night with its sleep score and need, and every morning's sleep health and sleep debt, computed
 * once so that every screen shows the same values.
 *
 * @property nights oldest first.
 */
data class SleepHistory(
    val nights: List<Night>,
    val scores: Map<LocalDate, SleepScore>,
    val needs: Map<LocalDate, Long>,
    val health: Map<LocalDate, SleepHealth> = emptyMap(),
    val debts: Map<LocalDate, SleepDebt> = emptyMap(),
) {
    companion object {
        val EMPTY = SleepHistory(emptyList(), emptyMap(), emptyMap())

        /** The history of [nights]; with [fixedNeedMillis], every night is judged against that need. */
        fun of(nights: List<Night>, fixedNeedMillis: Long? = null): SleepHistory {
            val sorted = nights.sortedBy { it.date }
            val needs = sorted.associate { it.date to (fixedNeedMillis ?: SleepScore.need(sorted, it.date)) }
            return SleepHistory(
                nights = sorted,
                scores = sorted.associate { it.date to SleepScore.of(it, needs.getValue(it.date)) },
                needs = needs,
                health = SleepHealth.all(sorted, needs),
                debts = SleepDebt.all(sorted, needs),
            )
        }
    }
}
