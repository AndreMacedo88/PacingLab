package app.pacinglab.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.pacinglab.MainActivity
import app.pacinglab.R
import app.pacinglab.core.analytics.load.TrainingStatusKind
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.ReadinessLevel
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.analytics.recovery.Vitals
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.repository.TrainingStatusRepository
import app.pacinglab.core.data.sync.AfterSync
import app.pacinglab.core.ui.label
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/** What the widget shows; null values show as a dash. */
data class WidgetSnapshot(
    val readiness: Int?,
    val readinessLevel: ReadinessLevel?,
    val sleepScore: Int?,
    val sleepRating: SleepRating?,
    val form: Int?,
    val vitalsOutside: Int?,
    val status: TrainingStatusKind?,
)

/** Gathers the widget's values from the same repositories the screens use. */
class WidgetData @Inject constructor(
    private val nights: NightRepository,
    private val trainingLoad: TrainingLoadRepository,
    private val training: TrainingStatusRepository,
    private val clock: Clock,
) {
    suspend fun load(): WidgetSnapshot {
        val today = LocalDate.now(clock)
        val sleep = nights.observeSleep().first()
        val form = trainingLoad.observeFitness().first()
        val readiness = Readiness.of(sleep.nights, form.lastOrNull { it.date == today }, today, sleep.scores)
        val score = sleep.scores[today]
        val vitals = Vitals.of(sleep.nights, today)
        return WidgetSnapshot(
            readiness = readiness.score,
            readinessLevel = readiness.level,
            sleepScore = score?.score,
            sleepRating = score?.rating,
            form = form.lastOrNull()?.form?.roundToInt(),
            vitalsOutside = vitals?.takeIf { it.judged > 0 }?.outside?.size,
            status = training.observeStatus().first()?.kind,
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetData(): WidgetData
}

/**
 * Today at a glance on the home screen: readiness, last night's sleep score and form, with the training
 * status and the vitals underneath. It is redrawn after every sync; a tap opens the app.
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).widgetData().load()
        provideContent { GlanceTheme { Content(snapshot) } }
    }

    @Composable
    private fun Content(snapshot: WidgetSnapshot) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Figure(
                    label = context.getString(R.string.widget_readiness),
                    value = snapshot.readiness?.toString() ?: DASH,
                    word = snapshot.readinessLevel?.let { context.getString(it.word()) },
                    modifier = GlanceModifier.defaultWeight(),
                )
                Figure(
                    label = context.getString(R.string.widget_sleep),
                    value = snapshot.sleepScore?.toString() ?: DASH,
                    word = snapshot.sleepRating?.let { context.getString(it.word()) },
                    modifier = GlanceModifier.defaultWeight(),
                )
                Figure(
                    label = context.getString(R.string.widget_form),
                    value = snapshot.form?.let { if (it > 0) "+$it" else it.toString() } ?: DASH,
                    word = null,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = listOfNotNull(
                    snapshot.status?.let { context.getString(it.label()) },
                    snapshot.vitalsOutside?.let {
                        if (it == 0) {
                            context.getString(R.string.widget_vitals_typical)
                        } else {
                            context.resources.getQuantityString(R.plurals.widget_vitals_outside, it, it)
                        }
                    },
                ).joinToString(" · "),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun Figure(label: String, value: String, word: String?, modifier: GlanceModifier = GlanceModifier) {
        Column(modifier = modifier) {
            Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
            Text(value, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Medium))
            if (word != null) Text(word, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
    }

    private fun ReadinessLevel.word(): Int = when (this) {
        ReadinessLevel.READY -> R.string.widget_ready
        ReadinessLevel.MODERATE -> R.string.widget_moderate
        ReadinessLevel.LOW -> R.string.widget_low
    }

    private fun SleepRating.word(): Int = when (this) {
        SleepRating.GOOD -> R.string.widget_good
        SleepRating.FAIR -> R.string.widget_fair
        SleepRating.POOR -> R.string.widget_poor
    }

    private companion object {
        const val DASH = "–"
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** Redraws the widget after every sync with the new values. */
class WidgetRefresher @Inject constructor(@param:ApplicationContext private val context: Context) : AfterSync {
    override suspend fun run() = TodayWidget().updateAll(context)
}
