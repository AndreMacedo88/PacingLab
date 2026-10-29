package app.pacinglab

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The app merges the string resources of every module into one set, and where two modules use the same
 * name only one text survives: the other screen then silently shows the wrong words (the cadence card
 * once carried the fitness card's title). Names may repeat only with the same text.
 */
class ResourceNamesTest {
    private val definition = Regex("""<(string|plurals|string-array) name="([^"]+)"[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)

    @Test
    fun `no two modules give one string name different texts`() {
        val root = File("..").canonicalFile
        val files = root.walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            .filter { it.name == "strings.xml" && it.parentFile?.name == "values" && it.path.contains("/src/main/res/") }
            .toList()
        val texts = mutableMapOf<Pair<String, String>, MutableMap<String, String>>()
        for (file in files) {
            val module = file.relativeTo(root).path.substringBefore("/src/")
            for (match in definition.findAll(file.readText())) {
                val (kind, name, text) = match.destructured
                texts.getOrPut(kind to name) { mutableMapOf() }[module] = text.trim()
            }
        }
        val clashes = texts.filterValues { byModule -> byModule.values.toSet().size > 1 }
            .map { (key, byModule) -> "${key.second}: ${byModule.keys.sorted().joinToString()}" }
            .sorted()

        assertEquals(emptyList<String>(), clashes)
    }
}
