package app.pacinglab.core.data.repository

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.data.testDataStore
import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.store.DailyVitalsStore
import app.pacinglab.core.model.DailyVitals
import app.pacinglab.core.model.source.SourceAuthorizationException
import app.pacinglab.core.model.source.VitalsSource
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class GoogleHealthRepositoryTest {
    private val now = Instant.parse("2026-10-29T07:00:00Z")
    private val today = LocalDate.of(2026, 10, 29)
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PacingLabDatabase::class.java)
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val store = DailyVitalsStore(database)

    private class FakeSource : VitalsSource {
        val asked = mutableListOf<LocalDate>()
        val askedVo2Max = mutableListOf<LocalDate>()
        var failure: Exception? = null
        var days = listOf<DailyVitals>()

        override suspend fun readDailyVitals(since: LocalDate, vo2maxSince: LocalDate): List<DailyVitals> {
            asked += since
            askedVo2Max += vo2maxSince
            failure?.let { throw it }
            return days.filter { it.date >= since }
        }
    }

    private val source = FakeSource()
    private val repository = GoogleHealthRepository(source, store, testDataStore(), Clock.fixed(now, ZoneOffset.UTC))

    @After
    fun tearDown() = database.close()

    @Test
    fun `nothing is read before the athlete connects`() = runTest {
        repository.sync()

        assertThat(source.asked).isEqualTo(emptyList<LocalDate>())
        assertThat(repository.status.first().connected).isEqualTo(false)
    }

    @Test
    fun `the first read goes back months, later ones only a few days before the newest morning`() = runTest {
        source.days = listOf(DailyVitals(today.minusDays(1), hrvMs = 61.4), DailyVitals(today, hrvMs = 52.1))

        repository.connect()
        repository.sync()

        assertThat(source.asked).containsExactly(today.minusDays(GoogleHealthRepository.HISTORY_DAYS), today.minusDays(3))
        val status = repository.status.first()
        assertThat(status).isEqualTo(GoogleHealthStatus(connected = true, lastSync = now, days = 2, newest = today))
    }

    @Test
    fun `VO2max is read years back once, then from its newest day, and is not counted as a morning`() = runTest {
        source.days = listOf(
            DailyVitals(today.minusDays(10), vo2max = 52.0, vo2maxVariance = 1.2),
            DailyVitals(today.minusDays(1), hrvMs = 61.4),
        )

        repository.connect()
        repository.sync()

        assertThat(source.askedVo2Max).containsExactly(today.minusDays(GoogleHealthRepository.VO2MAX_HISTORY_DAYS), today.minusDays(13))
        assertThat(repository.status.first().days).isEqualTo(1)
    }

    @Test
    fun `failures are recorded until a read succeeds again`() = runTest {
        repository.connect()
        source.failure = SourceAuthorizationException("Sign-in needed")
        repository.sync()
        assertThat(repository.status.first().problem).isEqualTo(GoogleHealthProblem.SIGN_IN_NEEDED)

        source.failure = IOException("offline")
        repository.sync()
        assertThat(repository.status.first().problem).isEqualTo(GoogleHealthProblem.UNREACHABLE)

        source.failure = null
        repository.sync()
        assertThat(repository.status.first().problem).isNull()
    }

    @Test
    fun `disconnecting forgets the values`() = runTest {
        source.days = listOf(DailyVitals(today, hrvMs = 52.1))
        repository.connect()

        repository.disconnect()

        assertThat(repository.status.first()).isEqualTo(GoogleHealthStatus())
        assertThat(store.observe().first()).isEqualTo(emptyList<DailyVitals>())
    }
}
