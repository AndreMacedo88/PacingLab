package app.pacinglab.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import assertk.assertThat
import assertk.assertions.containsExactly
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
}
