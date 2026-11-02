package app.pacinglab.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pacinglab.core.designsystem.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.math.MathEquation
import app.pacinglab.core.designsystem.math.inlineMath

/**
 * The title row of a card, with an info button at the end when [info] is given. The row is always
 * 48 dp tall, the size of the button's touch target, so cards line up with and without one; cards
 * using it should leave only a small top padding.
 *
 * @param info the explanation shown in a dialog, in the light structure [InfoText] understands.
 * @param advanced the mathematics behind it, folded away under "Advanced info" at the end of the dialog.
 */
@Composable
fun CardHeader(title: String, modifier: Modifier = Modifier, info: String? = null, advanced: String? = null) {
    Row(modifier = modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        // Shifted outwards so that the glyph, not its touch target, lines up with the card's padding.
        if (info != null) InfoButton(title = title, text = info, advanced = advanced, modifier = Modifier.offset(x = 12.dp))
    }
}

/** A small "i" that opens a dialog with [text] under [title], and [advanced] folded away at its end. */
@Composable
fun InfoButton(title: String, text: String, modifier: Modifier = Modifier, advanced: String? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(
            painter = painterResource(PacingLabIcons.Info),
            contentDescription = stringResource(R.string.info_about, title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
    if (open) InfoDialog(title = title, text = text, advanced = advanced, onDismiss = { open = false })
}

@Composable
fun InfoDialog(title: String, text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, advanced: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.info_close)) } },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InfoText(text)
                if (advanced != null) AdvancedInfo(advanced)
            }
        },
        modifier = modifier,
    )
}

/** The mathematics of a card, closed until asked for, so that the plain explanation stays short. */
@Composable
fun AdvancedInfo(text: String, modifier: Modifier = Modifier, initiallyOpen: Boolean = false) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Column(modifier.padding(top = 12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(if (open) R.string.info_advanced_hide else R.string.info_advanced_show),
                ) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.info_advanced),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(PacingLabIcons.ChevronRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp).rotate(if (open) -90f else 90f),
            )
        }
        if (open) InfoText(text)
    }
}

/**
 * An explanation laid out in blocks separated by blank lines. A block starting with "# " is a small
 * heading; lines starting with "- " are bullet points; a line between "$$" marks is a displayed formula;
 * anything else is a paragraph. Text between single dollar signs is set as inline mathematics. Formulas
 * are written in the TeX subset [MathParser] reads.
 */
@Composable
fun InfoText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { InfoBlock.parse(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (block in blocks) {
            when (block) {
                is InfoBlock.Heading -> Text(
                    text = inlineMath(block.text),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp),
                )

                is InfoBlock.Paragraph -> Text(inlineMath(block.text), style = MaterialTheme.typography.bodyMedium)

                is InfoBlock.Equation -> MathEquation(block.source, fontSize = EQUATION_SIZE)

                is InfoBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (item in block.items) {
                        Row {
                            Text("\u2022", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
                            Text(inlineMath(item), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** The pieces of an [InfoText]. */
sealed interface InfoBlock {
    data class Heading(val text: String) : InfoBlock

    data class Paragraph(val text: String) : InfoBlock

    data class Bullets(val items: List<String>) : InfoBlock

    /** A displayed formula in TeX notation. */
    data class Equation(val source: String) : InfoBlock

    companion object {
        /** Blocks are separated by blank lines; within a block, a paragraph may lead into bullet points. */
        fun parse(text: String): List<InfoBlock> {
            val blocks = mutableListOf<InfoBlock>()
            val paragraph = mutableListOf<String>()
            val bullets = mutableListOf<String>()
            fun flush() {
                if (paragraph.isNotEmpty()) blocks += Paragraph(paragraph.joinToString(" "))
                if (bullets.isNotEmpty()) blocks += Bullets(bullets.toList())
                paragraph.clear()
                bullets.clear()
            }
            for (block in text.trim().split(Regex("\\n\\s*\\n"))) {
                for (line in block.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
                    when {
                        line.startsWith("$$") && line.endsWith("$$") && line.length >= 4 -> {
                            flush()
                            blocks += Equation(line.removePrefix("$$").removeSuffix("$$").trim())
                        }

                        line.startsWith("# ") -> {
                            flush()
                            blocks += Heading(line.removePrefix("# "))
                        }

                        line.startsWith("- ") -> {
                            if (paragraph.isNotEmpty()) blocks += Paragraph(paragraph.joinToString(" ")).also { paragraph.clear() }
                            bullets += line.removePrefix("- ")
                        }

                        else -> {
                            if (bullets.isNotEmpty()) blocks += Bullets(bullets.toList()).also { bullets.clear() }
                            paragraph += line
                        }
                    }
                }
                flush()
            }
            return blocks
        }
    }
}

private val EQUATION_SIZE = 15.sp
