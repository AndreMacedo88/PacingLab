package app.pacinglab.core.data.home

import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.strain.DaytimeStrain
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.PerformanceRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.repository.TrainingStatusRepository
import app.pacinglab.core.database.store.StillHeartRateStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlin.math.round

/** The JSON PacingLab sends to Home Assistant's webhook. */
fun interface HomeAssistantValues {
    suspend fun build(): String
}

/**
 * The values PacingLab sends to Home Assistant: today's readiness and last night, the latest complete
 * day's strain, form and fitness, VO2max and training status. Keys match [HomeAssistantYaml.VALUES];
 * a value not known yet is sent as null, which leaves its sensor unavailable.
 */
class HomeAssistantPayload @Inject constructor(
    private val nights: NightRepository,
    private val loads: TrainingLoadRepository,
    private val status: TrainingStatusRepository,
    private val performance: PerformanceRepository,
    private val still: StillHeartRateStore,
    private val clock: Clock,
) : HomeAssistantValues {
    override suspend fun build(): String {
        val today = LocalDate.now(clock)
        val sleep = nights.observeSleep().first()
        val form = loads.observeFitness().first()
        val formToday = form.lastOrNull { it.date == today }
        val readiness = Readiness.of(sleep.nights, formToday, today, sleep.scores)
        val lastNight = sleep.nights.lastOrNull()?.takeIf { it.date == today }
        val score = sleep.scores[today]
        val strain = DaytimeStrain.days(still.observeAll().first()).lastOrNull { it.score != null }
        val vo2max = performance.observeVo2Max().first().trend?.current?.value
        val training = status.observeStatus().first()
        return JSONObject()
            .put("readiness", readiness.score)
            .put("readiness_level", readiness.level?.name?.lowercase(Locale.ROOT))
            .put("sleep_score", score?.score)
            .put("sleep_rating", score?.rating?.name?.lowercase(Locale.ROOT))
            .put("asleep_hours", lastNight?.let { tenths(it.asleepMillis / HOUR) })
            .put("sleep_health", sleep.health[today]?.score)
            .put("sleep_debt_hours", sleep.debts[today]?.let { tenths(it.millis / HOUR) })
            .put("hrv", lastNight?.hrvMs?.let { round(it) })
            .put("resting_hr", lastNight?.restingHr?.let { round(it) })
            .put("strain", strain?.score)
            .put("strain_level", strain?.level?.name?.lowercase(Locale.ROOT))
            .put("form", formToday?.let { round(it.form) })
            .put("fitness", formToday?.let { round(it.fitness) })
            .put("fatigue", formToday?.let { round(it.fatigue) })
            .put("vo2max", vo2max?.let(::tenths))
            .put("training_status", training?.kind?.name?.lowercase(Locale.ROOT))
            .put("updated", clock.instant().toString())
            .toString()
    }

    private fun tenths(value: Double) = round(value * 10) / 10

    private companion object {
        const val HOUR = 3_600_000.0
    }
}
