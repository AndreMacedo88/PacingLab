package app.pacinglab.feature.metrics

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.recovery.HrvVariation
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.NightlyRange
import app.pacinglab.core.analytics.recovery.RecoveryDay
import app.pacinglab.core.analytics.recovery.RecoveryTrend
import app.pacinglab.core.analytics.recovery.VitalSign
import app.pacinglab.core.analytics.recovery.Vitals
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.data.repository.CardSpec
import app.pacinglab.core.data.repository.Dashboard
import app.pacinglab.core.data.repository.DashboardLayout
import app.pacinglab.core.data.repository.DashboardRepository
import app.pacinglab.core.data.repository.DayHeartRateRepository
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.strain.DaytimeStrainRepository
import app.pacinglab.core.data.strain.StrainHistory
import app.pacinglab.core.data.sync.SyncScheduler
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.core.model.ZoneSet
import app.pacinglab.core.ui.NightlyTileData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import app.pacinglab.core.ui.R as UiR

/**
 * The cards of Body, in the default order. [todayId] is the Today card each can be pinned as.
 *
 * @property hero the card that answers "how is my body": the vitals over the nightly tiles.
 */
enum class BodyCardKind(
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val todayId: String? = null,
    val hero: Boolean = false,
) {
    VITALS("vitals", UiR.string.vitals_title, R.string.card_body_vitals, todayId = "vitals", hero = true),
    RECOVERY("recovery", R.string.recovery_title, R.string.card_recovery),
    HEART_RATE("heart_rate", UiR.string.heart_rate_title, R.string.card_body_heart_rate, todayId = "heart_rate"),
    STRAIN("strain", UiR.string.strain_title, R.string.card_strain, todayId = "strain"),
    ;

    companion object {
        val catalog: List<CardSpec> = entries.map { CardSpec(it.id) }

        fun of(id: String): BodyCardKind? = entries.firstOrNull { it.id == id }
    }
}

/** The nightly measures Body shows as tiles, with the Today card each can be pinned as. */
internal val BODY_TILES = mapOf(
    NightlyMetric.HRV to "hrv",
    NightlyMetric.RESTING_HR to "resting_hr",
    NightlyMetric.BREATHING_RATE to "breathing",
    NightlyMetric.SLEEPING_HR to "sleeping_hr",
    NightlyMetric.OXYGEN_SATURATION to "spo2",
    NightlyMetric.SKIN_TEMPERATURE to "skin_temperature",
)

sealed interface BodyUiState {
    data object Loading : BodyUiState

    /**
     * @property vitals last night's measures against their ranges; null without a night ending today.
     * @property tiles a tile per nightly measure with data; SpO2 and skin temperature only once the Google Health API supplied them.
     * @property recovery HRV and resting heart rate day by day, for the trend card.
     * @property heartRate today's heart rate so far; null until read.
     * @property zones today's heart rate zones; null without heart rate values.
     * @property restingHr last night's resting heart rate.
     * @property strain daytime strain up to today; null until first read.
     * @property cards the athlete's arrangement of Body, hidden cards included.
     */
    data class Loaded(
        val today: LocalDate,
        val vitals: Vitals?,
        val tiles: List<NightlyTileData>,
        val recovery: List<RecoveryDay> = emptyList(),
        val variation: HrvVariation? = null,
        val heartRate: HeartRateSeries? = null,
        val zones: ZoneSet? = null,
        val restingHr: Double? = null,
        val strain: StrainHistory? = null,
        val cards: List<PlacedCard> = DashboardLayout().arrange(BodyCardKind.catalog),
    ) : BodyUiState
}

@HiltViewModel
class BodyViewModel @Inject constructor(
    nights: NightRepository,
    dayHeartRate: DayHeartRateRepository,
    trainingLoad: TrainingLoadRepository,
    strain: DaytimeStrainRepository,
    private val dashboards: DashboardRepository,
    private val scheduler: SyncScheduler,
    private val clock: Clock,
) : ViewModel() {
    private val nightly = nights.observeSleep().map { sleep ->
        val today = LocalDate.now(clock)
        BodyUiState.Loaded(
            today = today,
            vitals = vitalsOf(sleep.nights, today),
            tiles = tilesOf(sleep.nights, today),
            recovery = RecoveryTrend.of(sleep.nights, today, sleep.scores),
            variation = HrvVariation.of(sleep.nights, today),
            restingHr = sleep.nights.lastOrNull()?.takeIf { it.date == today }?.restingHr,
        )
    }.flowOn(Dispatchers.Default)

    private val live = combine(
        dayHeartRate.observeToday().map<_, HeartRateSeries?> { it.series }.onStart { emit(null) },
        trainingLoad.anchors.map { anchors -> anchors.at(LocalDate.now(clock))?.let { Zones.of(ZoneModel.LTHR_FIVE, it) } },
        strain.observe().map<StrainHistory, StrainHistory?> { it }.onStart { emit(null) },
        ::Triple,
    )

    val state: StateFlow<BodyUiState> = combine(nightly, live, dashboards.layout(Dashboard.BODY)) {
            body,
            (heartRate, zones, strainHistory),
            layout,
        ->
        body.copy(heartRate = heartRate, zones = zones, strain = strainHistory, cards = layout.arrange(BodyCardKind.catalog))
    }.stateIn<BodyUiState>(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BodyUiState.Loading)

    val refreshing: StateFlow<Boolean> =
        scheduler.refreshing.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun refresh() = scheduler.refresh()

    fun arrange(cards: List<PlacedCard>) {
        viewModelScope.launch { dashboards.save(Dashboard.BODY, cards) }
    }

    fun resetArrangement() {
        viewModelScope.launch { dashboards.reset(Dashboard.BODY) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Last night's vitals, judged as the Sleep screen and Today judge them. */
internal fun vitalsOf(nights: List<Night>, today: LocalDate): Vitals? {
    val night = nights.lastOrNull()?.takeIf { it.date == today } ?: return null
    return Vitals(today, Vitals.METRICS.map { VitalSign(it, it.value(night), NightlyRange.of(nights, it, today)) })
}

internal fun tilesOf(nights: List<Night>, today: LocalDate): List<NightlyTileData> = BODY_TILES.keys
    .filter { metric ->
        // Only the Google Health API supplies these two.
        when (metric) {
            NightlyMetric.OXYGEN_SATURATION -> nights.any { it.oxygenSaturation != null }
            NightlyMetric.SKIN_TEMPERATURE -> nights.any { it.skinTemperature != null }
            else -> true
        }
    }
    .map { metric ->
        val ranges = (NightlyTileData.DAYS - 1 downTo 0).map { back ->
            NightlyRange.of(nights, metric, today.minusDays(back.toLong()))
        }
        NightlyTileData.of(nights, metric, today, ranges, NightlyRange.nightsBefore(nights, metric, today))
    }
