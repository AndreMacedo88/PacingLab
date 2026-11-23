package app.pacinglab.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DashboardRepositoryTest {
    private val catalog = listOf(CardSpec("a"), CardSpec("b"), CardSpec("c"), CardSpec("x", hiddenByDefault = true))

    private fun repository(): DashboardRepository {
        val directory = Files.createTempDirectory("dash").toFile()
        return DashboardRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
                File(directory, "d.preferences_pb")
            },
        )
    }

    @Test
    fun `an untouched dashboard shows the catalogue in its default order`() {
        assertThat(DashboardLayout().arrange(catalog)).containsExactly(
            PlacedCard("a", true),
            PlacedCard("b", true),
            PlacedCard("c", true),
            PlacedCard("x", false),
        )
    }

    @Test
    fun `a saved arrangement comes back, and a card added later takes its default place`() = runTest {
        val repository = repository()
        repository.save(Dashboard.TODAY, listOf(PlacedCard("c", true), PlacedCard("a", false), PlacedCard("x", true)))

        val arranged = repository.layout(Dashboard.TODAY).first().arrange(catalog)

        // "b" was not in the catalogue when the athlete arranged it: it follows "a", where the catalogue puts it.
        assertThat(arranged).containsExactly(
            PlacedCard("c", true),
            PlacedCard("a", false),
            PlacedCard("b", true),
            PlacedCard("x", true),
        )
    }

    @Test
    fun `reset brings the default back and cards no longer in the catalogue are dropped`() = runTest {
        val repository = repository()
        repository.save(Dashboard.SLEEP, listOf(PlacedCard("gone", true), PlacedCard("b", true)))
        assertThat(repository.layout(Dashboard.SLEEP).first().arrange(catalog).map { it.id }).containsExactly("a", "b", "c", "x")

        repository.reset(Dashboard.SLEEP)

        assertThat(repository.layout(Dashboard.SLEEP).first().arrange(catalog).map { it.id }).containsExactly("a", "b", "c", "x")
    }

    @Test
    fun `pinning shows a card in its catalogue place and leaves the others alone`() = runTest {
        val repository = repository()

        repository.setVisible(Dashboard.TODAY, catalog, "x", visible = true)

        assertThat(repository.layout(Dashboard.TODAY).first().arrange(catalog)).containsExactly(
            PlacedCard("a", true),
            PlacedCard("b", true),
            PlacedCard("c", true),
            PlacedCard("x", true),
        )

        repository.setVisible(Dashboard.TODAY, catalog, "b", visible = false)

        assertThat(repository.layout(Dashboard.TODAY).first().arrange(catalog).filter { it.visible }.map { it.id })
            .containsExactly("a", "c", "x")
    }

    @Test
    fun `the reorganisation carries Trends and Sleep over and gives Today its new default`() = runTest {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("dashboard_trends_order") to "calendar,weeks,strain,status,recovery,form,focus,running",
            stringSetPreferencesKey("dashboard_trends_hidden") to setOf("strain", "focus"),
            stringPreferencesKey("dashboard_sleep_order") to "tiles,factors,stages",
            stringSetPreferencesKey("dashboard_sleep_hidden") to setOf("factors"),
            stringPreferencesKey("dashboard_today_order") to "weather,readiness,hrv",
        )

        DashboardMigration.upgrade(prefs)

        assertThat(prefs[stringPreferencesKey("dashboard_load_order")]).isEqualTo("weeks,status,form,focus,running")
        assertThat(prefs[stringSetPreferencesKey("dashboard_load_hidden")]).isEqualTo(setOf("focus"))
        assertThat(prefs[stringPreferencesKey("dashboard_body_order")]).isEqualTo("strain,recovery")
        assertThat(prefs[stringSetPreferencesKey("dashboard_body_hidden")]).isEqualTo(setOf("strain"))
        assertThat(prefs[stringPreferencesKey("dashboard_sleep_patterns_order")]).isEqualTo("tiles,factors,stages")
        assertThat(prefs[stringSetPreferencesKey("dashboard_sleep_patterns_hidden")]).isEqualTo(setOf("factors"))
        assertThat(prefs[stringPreferencesKey("dashboard_trends_order")]).isEqualTo(null)
        assertThat(prefs[stringPreferencesKey("dashboard_today_order")]).isEqualTo(null)
        assertThat(prefs[DashboardMigration.VERSION]).isEqualTo(DashboardMigration.CURRENT)

        // Once done, it leaves the store alone: an athlete's later Today stays.
        prefs[stringPreferencesKey("dashboard_today_order")] = "weather"
        DashboardMigration.upgrade(prefs)
        assertThat(prefs[stringPreferencesKey("dashboard_today_order")]).isEqualTo("weather")
    }
}
