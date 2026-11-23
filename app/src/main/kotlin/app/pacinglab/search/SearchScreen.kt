package app.pacinglab.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import app.pacinglab.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale

/** The search over every card, page and setting, opened from the magnifier in each top-level app bar. */
@Serializable
data object SearchKey : NavKey

/** A search result as shown: the entry with its words resolved. */
private class Shown(val index: Int, val entry: SearchEntry, val title: String, val place: String)

/**
 * The search: a field at the top and, under it, what matches as the athlete types, best first; with nothing typed,
 * everything the search knows, grouped by tab, which doubles as a map of the app. A result opens its card (scrolled
 * into view and flashing), page or setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpen: (NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    entries: List<SearchEntry> = SearchIndex.entries,
    autoFocus: Boolean = true,
) {
    val text = rememberTextFieldState(initialQuery)
    val query = text.text.toString()
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    val separator = " › "
    val shown = entries.mapIndexed { index, entry ->
        Shown(
            index = index,
            entry = entry,
            title = stringResource(entry.title),
            place = (listOf(entry.group.label) + entry.place).map { stringResource(it) }.joinToString(separator),
        )
    }
    val results = remember(query, shown) {
        shown.mapNotNull { item -> rank(query, item.title, item.entry.keywords, item.place)?.let { it to item } }
            .sortedBy { it.first }
            .map { it.second }
    }
    val open: (Shown) -> Unit = { onOpen(it.entry.target) }
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                state = text,
                onSearch = { results.firstOrNull()?.let(open) },
                expanded = true,
                onExpandedChange = { if (!it) onBack() },
                modifier = Modifier.focusRequester(focus),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(PacingLabIcons.ArrowBack), contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { text.clearText() }) {
                            Icon(painterResource(PacingLabIcons.Close), contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
            )
        },
        expanded = true,
        onExpandedChange = { if (!it) onBack() },
        modifier = modifier,
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            when {
                query.isBlank() -> for (group in SearchGroup.entries) {
                    val inGroup = shown.filter { it.entry.group == group }
                    if (inGroup.isEmpty()) continue
                    item(key = "group-${group.name}") {
                        Text(
                            text = stringResource(group.label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
                        )
                    }
                    items(inGroup, key = { it.index }) { ResultRow(it, showPlace = false, onClick = { open(it) }) }
                }

                results.isEmpty() -> item(key = "none") {
                    Text(
                        text = stringResource(R.string.search_none, query.trim()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }

                else -> items(results, key = { it.index }) { ResultRow(it, showPlace = true, onClick = { open(it) }) }
            }
        }
    }
}

@Composable
private fun ResultRow(item: Shown, showPlace: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = if (showPlace) ({ Text(item.place) }) else null,
        trailingContent = { Icon(painterResource(PacingLabIcons.ChevronRight), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * How well [query] matches an entry, lower is better, or null when it does not: every word of the query must
 * start a word of the [title], of the [keywords] or of the [place] (or, from three letters, appear inside the
 * title). An entry whose title starts with the query comes first, then those matched by title words, then by
 * keywords, then by place.
 */
internal fun rank(query: String, title: String, keywords: List<String>, place: String): Int? {
    val q = normalize(query)
    if (q.isBlank()) return null
    val words = q.split(' ').filter(String::isNotEmpty)
    val t = normalize(title)
    val titleWords = t.split(' ')
    val keywordText = keywords.joinToString(" ") { normalize(it) }
    val keywordWords = keywordText.split(' ')
    val placeWords = normalize(place).split(' ')
    fun inTitle(word: String) = titleWords.any { it.startsWith(word) } || (word.length >= MIN_INFIX && word in t)
    fun inKeywords(word: String) = keywordWords.any { it.startsWith(word) }
    if (!words.all { inTitle(it) || inKeywords(it) || placeWords.any { p -> p.startsWith(it) } }) return null
    return when {
        t.startsWith(q) -> 0
        words.all(::inTitle) -> 1
        keywords.any { normalize(it).startsWith(q) } -> 2
        words.all { inTitle(it) || inKeywords(it) } -> 3
        else -> 4
    }
}

/** Lower case, without accents, with every run of other characters than letters and digits as one space. */
private fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

private const val MIN_INFIX = 3
