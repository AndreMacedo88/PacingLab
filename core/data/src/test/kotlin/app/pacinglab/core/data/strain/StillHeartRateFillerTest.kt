package app.pacinglab.core.data.strain

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.data.sync.AppVisibility
import app.pacinglab.core.data.sync.FakeHealthDataSource
import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.StillHeartRateStore
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.StepCount
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class StillHeartRateFillerTest {
    private val zone = ZoneOffset.ofHours(1)
    private val today = LocalDate.of(2026, 11, 3)
    private val clock = Clock.fixed(today.atTime(12, 0).atZone(zone).toInstant(), zone)
    private val source = FakeHealthDataSource()
    private var foreground = true
    private val visibility = object : AppVisibility {
        override val isForeground: Boolean get() = foreground
    }
    private lateinit var database: PacingLabDatabase
    private lateinit var store: StillHeartRateStore
    private lateinit var filler: StillHeartRateFiller

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PacingLabDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()
        store = StillHeartRateStore(database, clock)
        val reader = StillHeartRateReader(source, MeasurementStore(database), WorkoutStore(database), clock)
        filler = StillHeartRateFiller(reader, store, visibility, clock)
        // Yesterday from 09:00 to 11:00 at 60 bpm every 5 seconds, with a walk from 10:00 to 10:10.
        val start = today.minusDays(1).atTime(9, 0).atZone(zone).toInstant()
        val times = LongArray(2 * 720) { start.toEpochMilli() + it * 5_000L }
        source.heartRate["com.fitbit.FitbitMobile"] = HeartRateSeries(times, IntArray(times.size) { 60 })
        source.steps += (0 until 10).map { StepCount(start.plusSeconds(3_600L + 60L * it), start.plusSeconds(3_660L + 60L * it), 90) }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `past days are summarised once and kept, newest first`() = runTest {
        filler.run()

        val days = store.observeAll().first()
        assertThat(days.size).isEqualTo(StillHeartRateFiller.BACKFILL_DAYS.toInt())
        val yesterday = days.single { it.date == today.minusDays(1) }
        assertThat(yesterday.minutes[9]).isEqualTo(60)
        assertThat(yesterday.minutes[10]).isEqualTo(45)
        assertThat(days.none { it.date == today }).isEqualTo(true)
    }

    @Test
    fun `nothing is read in the background`() = runTest {
        foreground = false

        filler.run()

        assertThat(store.observeAll().first()).isEmpty()
    }

    @Test
    fun `the last days are read again for heart rate passed on late`() = runTest {
        filler.run()
        val later = today.minusDays(2).atTime(15, 0).atZone(zone).toInstant()
        val times = LongArray(720) { later.toEpochMilli() + it * 5_000L }
        source.heartRate["com.fitbit.FitbitMobile"] = HeartRateSeries(times, IntArray(times.size) { 58 })

        filler.run()

        val twoDaysAgo = store.observeAll().first().single { it.date == today.minusDays(2) }
        assertThat(twoDaysAgo.minutes[15]).isEqualTo(60)
    }
}
