package app.pacinglab.core.designsystem.math

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pacinglab.core.designsystem.R

/** The typeface of formulas: the system serif, whose italic tells variables from words. */
val MathFamily: FontFamily = FontFamily.Serif

/**
 * A displayed formula written in the TeX subset [MathParser] reads, typeset by [MathLayout] and centred.
 * A formula wider than the space scrolls sideways rather than shrinking.
 */
@Composable
fun MathEquation(
    source: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    color: Color = LocalContentColor.current,
    family: FontFamily = MathFamily,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val node = remember(source) { MathParser.parse(source) }
    val box = remember(node, fontSize, density, family) {
        MathLayout(measurer, density, with(density) { fontSize.toPx() }, family).layout(node, MathStyle.DISPLAY)
    }
    val words = SpokenWords(
        over = stringResource(R.string.math_over),
        sub = stringResource(R.string.math_sub),
        power = stringResource(R.string.math_power),
        root = stringResource(R.string.math_root),
    )
    val spoken = remember(node, words) { spoken(node, words) }
    val pad = with(density) { fontSize.toPx() } * VERTICAL_PAD
    val side = with(density) { fontSize.toPx() } * SIDE_PAD
    with(density) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Canvas(
                    Modifier
                        .size((box.width + 2 * side).toDp(), (box.height + 2 * pad).toDp())
                        .semantics { contentDescription = spoken },
                ) { box.draw(this, side, pad + box.ascent, color) }
            }
        }
    }
}

/**
 * [text] with each `$...$` span set as inline maths: italic variables, Greek letters, smaller raised and
 * lowered scripts, fractions written with a slash. Text without a dollar sign comes back unchanged.
 */
fun inlineMath(text: String, family: FontFamily = MathFamily): AnnotatedString = buildAnnotatedString {
    var rest = text
    while (true) {
        val open = rest.indexOf('$')
        val close = if (open < 0) -1 else rest.indexOf('$', open + 1)
        if (open < 0 || close < 0) {
            append(rest)
            break
        }
        append(rest.substring(0, open))
        inline(MathParser.parse(rest.substring(open + 1, close)), family, script = false)
        rest = rest.substring(close + 1)
    }
}

/** Appends [node]; inside scripts ([script]) operators are not spaced, as in TeX's script styles. */
private fun AnnotatedString.Builder.inline(node: MathNode, family: FontFamily, script: Boolean) {
    when (node) {
        is MathNode.Symbol -> withStyle(
            SpanStyle(
                fontFamily = family,
                fontStyle = if (node.font == MathFont.ITALIC) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (node.font == MathFont.BOLD) FontWeight.Bold else null,
            ),
        ) { append(node.text) }

        is MathNode.Row -> {
            var previous: AtomKind? = null
            for (child in node.children) {
                val kind = (child as? MathNode.Symbol)?.kind
                // As in TeX, a binary operator at the start or after another operator is a sign and stays close.
                val sign = previous == null || previous in setOf(AtomKind.BIN, AtomKind.REL, AtomKind.OP, AtomKind.OPEN, AtomKind.PUNCT)
                val spaced = !script && (kind == AtomKind.REL || (kind == AtomKind.BIN && !sign))
                val nextToOperator = (previous == AtomKind.OP && kind != AtomKind.OPEN) ||
                    (kind == AtomKind.OP && (previous == AtomKind.ORD || previous == AtomKind.CLOSE))
                if (nextToOperator && child !is MathNode.Space) append(THIN_SPACE)
                if (spaced) append(MEDIUM_SPACE)
                inline(child, family, script)
                if (spaced) append(MEDIUM_SPACE)
                if (kind == AtomKind.PUNCT && !script) append(THIN_SPACE)
                if (child !is MathNode.Space) previous = kind ?: AtomKind.ORD
            }
        }

        is MathNode.Scripts -> {
            inline(node.base, family, script)
            node.sub?.let {
                withStyle(SpanStyle(fontSize = SCRIPT_SIZE.em, baselineShift = BaselineShift(SUB_SHIFT))) {
                    inline(it, family, script = true)
                }
            }
            node.sup?.let {
                withStyle(SpanStyle(fontSize = SCRIPT_SIZE.em, baselineShift = BaselineShift(SUP_SHIFT))) {
                    inline(it, family, script = true)
                }
            }
        }

        is MathNode.Fraction -> {
            wrapped(node.numerator, family, script)
            append("/")
            wrapped(node.denominator, family, script)
        }

        is MathNode.Root -> {
            append("√")
            wrapped(node.body, family, script)
        }

        is MathNode.Accent -> {
            inline(node.body, family, script)
            append(
                when (node.accent) {
                    AccentKind.HAT -> "̂"
                    AccentKind.BAR -> "̄"
                    AccentKind.TILDE -> "̃"
                },
            )
        }

        is MathNode.Delimited -> {
            if (node.left != ".") append(node.left)
            inline(node.body, family, script)
            if (node.right != ".") append(node.right)
        }

        is MathNode.Cases -> node.rows.forEachIndexed { i, row ->
            if (i > 0) append("; ")
            row.forEach { inline(it, family, script) }
        }

        is MathNode.Text -> append(node.text)

        is MathNode.Space -> append(
            when {
                node.em <= 0f -> ""
                node.em < 0.2f -> THIN_SPACE
                node.em < 0.5f -> MEDIUM_SPACE
                else -> " ".repeat(node.em.toInt().coerceAtLeast(1))
            },
        )
    }
}

/** [node] in parentheses when it is more than one atom, for a slash between numerator and denominator. */
private fun AnnotatedString.Builder.wrapped(node: MathNode, family: FontFamily, script: Boolean) {
    val compound = node is MathNode.Row && node.children.count { it !is MathNode.Space } > 1
    if (compound) append("(")
    inline(node, family, script)
    if (compound) append(")")
}

/** Words for reading a formula aloud. */
data class SpokenWords(val over: String, val sub: String, val power: String, val root: String)

/** A plain reading of [node] for screen readers. */
fun spoken(node: MathNode, words: SpokenWords): String = when (node) {
    is MathNode.Symbol -> node.text

    is MathNode.Row -> node.children.joinToString(" ") { spoken(it, words) }.replace(Regex(" +"), " ").trim()

    is MathNode.Scripts -> buildString {
        append(spoken(node.base, words))
        node.sub?.let { append(" ${words.sub} ${spoken(it, words)}") }
        node.sup?.let { append(" ${words.power} ${spoken(it, words)}") }
    }

    is MathNode.Fraction -> "${spoken(node.numerator, words)} ${words.over} ${spoken(node.denominator, words)}"

    is MathNode.Root -> "${words.root} ${spoken(node.body, words)}"

    is MathNode.Accent -> spoken(node.body, words)

    is MathNode.Delimited -> listOf(node.left, spoken(node.body, words), node.right).filter { it != "." }.joinToString(" ")

    is MathNode.Cases -> node.rows.joinToString("; ") { row -> row.joinToString(" ") { spoken(it, words) } }

    is MathNode.Text -> node.text.trim()

    is MathNode.Space -> ""
}

private const val VERTICAL_PAD = 0.3f
private const val SIDE_PAD = 0.15f
private const val SCRIPT_SIZE = 0.72f
private const val SUB_SHIFT = -0.22f
private const val SUP_SHIFT = 0.5f

// No-break spaces, so that a formula in running text is never split between lines.
private const val THIN_SPACE = "\u202F"
private const val MEDIUM_SPACE = "\u202F"
