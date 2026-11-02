package app.pacinglab.core.designsystem.math

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import kotlin.math.max

/**
 * A laid-out piece of a formula, in pixels: [ascent] above the baseline, [descent] below, drawn by
 * [draw] at a pen position on the baseline.
 */
class MathBox(
    val width: Float,
    val ascent: Float,
    val descent: Float,
    val kind: AtomKind = AtomKind.ORD,
    val draw: DrawScope.(x: Float, baseline: Float, color: Color) -> Unit = { _, _, _ -> },
) {
    val height: Float get() = ascent + descent
}

/** TeX's four styles: display and text at full size, then script and scriptscript. */
enum class MathStyle(val scale: Float) {
    DISPLAY(1f),
    TEXT(1f),
    SCRIPT(0.7f),
    SCRIPT_SCRIPT(0.5f),
    ;

    val script: MathStyle get() = if (this == DISPLAY || this == TEXT) SCRIPT else SCRIPT_SCRIPT

    val fraction: MathStyle get() = when (this) {
        DISPLAY -> TEXT
        TEXT -> SCRIPT
        else -> SCRIPT_SCRIPT
    }

    val cramped: Boolean get() = this == SCRIPT || this == SCRIPT_SCRIPT
}

/**
 * Lays out [MathNode]s the way TeX does, in simplified form: atoms spaced by their kinds, scripts
 * shifted and shrunk, fractions on the maths axis, radicals, accents, and delimiters drawn to the height
 * of what they hold. Glyph heights come from the shapes of their characters rather than the font's line
 * box, so that scripts and fractions sit as close as they do in print.
 *
 * @param emPx the size of the type, in pixels.
 */
class MathLayout(
    private val measurer: TextMeasurer,
    private val density: Density,
    private val emPx: Float,
    private val family: FontFamily,
) {
    fun layout(node: MathNode, style: MathStyle = MathStyle.DISPLAY): MathBox = when (node) {
        is MathNode.Symbol -> symbol(node, style)
        is MathNode.Row -> row(node.children, style)
        is MathNode.Scripts -> scripts(node, style)
        is MathNode.Fraction -> fraction(node, style)
        is MathNode.Root -> root(node, style)
        is MathNode.Accent -> accent(node, style)
        is MathNode.Delimited -> delimited(node, style)
        is MathNode.Cases -> cases(node, style)
        is MathNode.Text -> text(node.text, style)
        is MathNode.Space -> MathBox(node.em * em(style), 0f, 0f, AtomKind.ORD)
    }

    private fun em(style: MathStyle) = emPx * style.scale

    private fun axis(style: MathStyle) = AXIS * em(style)

    private fun measure(text: String, sizePx: Float, font: MathFont): TextLayoutResult = measurer.measure(
        AnnotatedString(text),
        TextStyle(
            fontFamily = family,
            fontSize = with(density) { sizePx.toSp() },
            fontStyle = if (font == MathFont.ITALIC) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (font == MathFont.BOLD) FontWeight.Bold else FontWeight.Normal,
        ),
        softWrap = false,
    )

    /** A run of glyphs drawn at its baseline, with heights estimated from its characters. */
    private fun glyphs(text: String, sizePx: Float, font: MathFont, kind: AtomKind, extraWidth: Float = 0f): MathBox {
        val measured = measure(text, sizePx, font)
        val ascent = text.maxOf { ascentOf(it) } * sizePx
        val descent = text.maxOf { descentOf(it) } * sizePx
        val italic = if (font == MathFont.ITALIC) ITALIC_CORRECTION * sizePx else 0f
        return MathBox(measured.size.width + italic + extraWidth, ascent, descent, kind) { x, baseline, color ->
            drawText(measured, color = color, topLeft = Offset(x, baseline - measured.firstBaseline))
        }
    }

    private fun symbol(node: MathNode.Symbol, style: MathStyle): MathBox {
        if (!node.large) return glyphs(node.text, em(style), node.font, node.kind)
        if (node.text == "Σ" || node.text == "Π") return drawnOperator(node.text == "Σ", style)
        // A big operator is drawn larger in display and centred on the maths axis.
        val size = em(style) * if (style == MathStyle.DISPLAY) LARGE_DISPLAY else LARGE_TEXT
        val measured = measure(node.text, size, node.font)
        // The glyph's own centre, between its height and depth, goes on the axis.
        val drop = (OPERATOR_HEIGHT - OPERATOR_DEPTH) / 2 * size - axis(style)
        return MathBox(measured.size.width.toFloat(), OPERATOR_HEIGHT * size - drop, OPERATOR_DEPTH * size + drop, AtomKind.OP) {
                x,
                baseline,
                color,
            ->
            drawText(measured, color = color, topLeft = Offset(x, baseline + drop - measured.firstBaseline))
        }
    }

    /**
     * A sum or product sign drawn as strokes, centred on the maths axis: fonts disagree on the size of these
     * signs and some lack them, so drawing keeps them the same everywhere.
     */
    private fun drawnOperator(sum: Boolean, style: MathStyle): MathBox {
        val em = em(style)
        val height = (if (style == MathStyle.DISPLAY) DRAWN_DISPLAY else DRAWN_TEXT) * em
        val width = height * if (sum) 0.82f else 0.9f
        val axis = axis(style)
        val thick = max(0.1f * em, 1.5f)
        val thin = max(0.04f * em, 1f)
        val side = 0.06f * em
        return MathBox(width + 2 * side, height / 2 + axis, height / 2 - axis, AtomKind.OP) { x, baseline, color ->
            val top = baseline - axis - height / 2
            val bottom = baseline - axis + height / 2
            val left = x + side
            val right = x + side + width
            if (sum) {
                val middle = baseline - axis
                drawLine(color, Offset(left, top + thin / 2), Offset(right, top + thin / 2), thin * 1.6f)
                drawLine(color, Offset(left + thick * 0.2f, top), Offset(left + width * 0.5f, middle), thick, StrokeCap.Butt)
                drawLine(color, Offset(left + width * 0.5f, middle), Offset(left, bottom - thin), thin)
                drawLine(color, Offset(left, bottom - thin / 2), Offset(right, bottom - thin / 2), thin * 1.8f)
                drawLine(color, Offset(right - thin / 2, top), Offset(right - thin / 2, top + height * 0.14f), thin)
                drawLine(color, Offset(right - thin / 2, bottom), Offset(right - thin / 2, bottom - height * 0.14f), thin)
            } else {
                drawLine(color, Offset(left, top + thin), Offset(right, top + thin), thin * 2f)
                drawLine(color, Offset(left + width * 0.18f, top), Offset(left + width * 0.18f, bottom), thick, StrokeCap.Butt)
                drawLine(color, Offset(right - width * 0.18f, top), Offset(right - width * 0.18f, bottom), thick, StrokeCap.Butt)
                drawLine(color, Offset(left + width * 0.04f, bottom - thin / 2), Offset(left + width * 0.32f, bottom - thin / 2), thin)
                drawLine(color, Offset(right - width * 0.32f, bottom - thin / 2), Offset(right - width * 0.04f, bottom - thin / 2), thin)
            }
        }
    }

    private fun text(text: String, style: MathStyle): MathBox {
        val measured = measure(text, em(style), MathFont.UPRIGHT)
        val shape = text.filterNot { it == ' ' }.ifEmpty { "x" }
        return MathBox(
            measured.size.width.toFloat(),
            shape.maxOf { ascentOf(it) } * em(style),
            shape.maxOf { descentOf(it) } * em(style),
        ) { x, baseline, color -> drawText(measured, color = color, topLeft = Offset(x, baseline - measured.firstBaseline)) }
    }

    private fun row(children: List<MathNode>, style: MathStyle): MathBox {
        val boxes = mutableListOf<MathBox>()
        val gaps = mutableListOf<Float>()
        var previous: AtomKind? = null
        val laid = children.map { it to layout(it, style) }
        for ((index, pair) in laid.withIndex()) {
            val (node, box) = pair
            if (node is MathNode.Space) {
                gaps += 0f
                boxes += box
                continue
            }
            var kind = box.kind
            // A binary operator with nothing to its left, or after another operator, is a sign.
            if (kind == AtomKind.BIN && (previous == null || previous in UNARY_AFTER)) kind = AtomKind.ORD
            if (kind == AtomKind.BIN) {
                val next = laid.drop(index + 1).firstOrNull { it.first !is MathNode.Space }?.second?.kind
                if (next == null || next in listOf(AtomKind.REL, AtomKind.CLOSE, AtomKind.PUNCT)) kind = AtomKind.ORD
            }
            gaps += if (previous == null) 0f else spacing(previous, kind, style) * em(style)
            boxes += box
            previous = kind
        }
        val width = boxes.indices.sumOf { (gaps[it] + boxes[it].width).toDouble() }.toFloat()
        val kind = if (boxes.size == 1) boxes[0].kind else AtomKind.ORD
        return MathBox(width, boxes.maxOfOrNull { it.ascent } ?: 0f, boxes.maxOfOrNull { it.descent } ?: 0f, kind) { x, baseline, color ->
            var pen = x
            for (i in boxes.indices) {
                pen += gaps[i]
                boxes[i].draw(this, pen, baseline, color)
                pen += boxes[i].width
            }
        }
    }

    private fun scripts(node: MathNode.Scripts, style: MathStyle): MathBox {
        val base = layout(node.base, style)
        val small = style.script
        val sup = node.sup?.let { layout(it, small) }
        val sub = node.sub?.let { layout(it, small) }
        val symbol = node.base as? MathNode.Symbol
        val em = em(style)
        if (symbol != null && symbol.limits && style == MathStyle.DISPLAY) return limits(base, sub, sup, style)

        val glyph = node.base is MathNode.Symbol && !(symbol?.large ?: false)
        var up = if (glyph) supShift(style) * em else max(supShift(style) * em, base.ascent - SUP_DROP * em(small))
        var down = if (glyph) SUB_ALONE * em else max(SUB_ALONE * em, base.descent + SUB_DROP * em(small))
        if (sup != null) up = max(up, sup.descent + 0.25f * X_HEIGHT * em)
        if (sub != null && sup == null) down = max(down, sub.ascent - 0.8f * X_HEIGHT * em)
        if (sub != null && sup != null) {
            down = max(down, SUB_WITH_SUP * em)
            val gap = (up - sup.descent) - (sub.ascent - down)
            if (gap < SCRIPT_GAP * em) down += SCRIPT_GAP * em - gap
        }
        val kick = if (symbol?.font == MathFont.ITALIC) ITALIC_CORRECTION * em else 0f
        val scriptWidth = max((sup?.width ?: 0f) + kick, sub?.width ?: 0f)
        return MathBox(
            width = base.width + scriptWidth + SCRIPT_SPACE * em,
            ascent = max(base.ascent, sup?.let { up + it.ascent } ?: 0f),
            descent = max(base.descent, sub?.let { down + it.descent } ?: 0f),
            kind = base.kind,
        ) { x, baseline, color ->
            base.draw(this, x, baseline, color)
            sup?.draw(this, x + base.width + kick, baseline - up, color)
            sub?.draw(this, x + base.width, baseline + down, color)
        }
    }

    private fun limits(base: MathBox, sub: MathBox?, sup: MathBox?, style: MathStyle): MathBox {
        val gap = LIMIT_GAP * em(style)
        val width = maxOf(base.width, sub?.width ?: 0f, sup?.width ?: 0f)
        return MathBox(
            width = width,
            ascent = base.ascent + (sup?.let { gap + it.height } ?: 0f),
            descent = base.descent + (sub?.let { gap + it.height } ?: 0f),
            kind = AtomKind.OP,
        ) { x, baseline, color ->
            base.draw(this, x + (width - base.width) / 2, baseline, color)
            sup?.let { it.draw(this, x + (width - it.width) / 2, baseline - base.ascent - gap - it.descent, color) }
            sub?.let { it.draw(this, x + (width - it.width) / 2, baseline + base.descent + gap + it.ascent, color) }
        }
    }

    private fun fraction(node: MathNode.Fraction, style: MathStyle): MathBox {
        val inner = if (node.display) MathStyle.TEXT else style.fraction
        val numerator = layout(node.numerator, inner)
        val denominator = layout(node.denominator, inner)
        val em = em(style)
        val rule = max(RULE * em, 1f)
        val gap = (if (style == MathStyle.DISPLAY || node.display) FRACTION_GAP_DISPLAY else FRACTION_GAP) * em
        val pad = FRACTION_PAD * em
        val width = max(numerator.width, denominator.width) + 2 * pad
        val axis = axis(style)
        val up = axis + rule / 2 + gap + numerator.descent
        val down = -axis + rule / 2 + gap + denominator.ascent
        return MathBox(width, up + numerator.ascent, down + denominator.descent, AtomKind.INNER) { x, baseline, color ->
            numerator.draw(this, x + (width - numerator.width) / 2, baseline - up, color)
            denominator.draw(this, x + (width - denominator.width) / 2, baseline + down, color)
            drawLine(color, Offset(x + pad / 2, baseline - axis), Offset(x + width - pad / 2, baseline - axis), rule)
        }
    }

    private fun root(node: MathNode.Root, style: MathStyle): MathBox {
        val body = layout(node.body, style)
        val em = em(style)
        val rule = max(RULE * em, 1f)
        val gap = ROOT_GAP * em
        val top = max(body.ascent, X_HEIGHT * em) + gap + rule
        val bottom = max(body.descent, 0f) + ROOT_DEPTH * em
        val sign = ROOT_WIDTH * em
        return MathBox(sign + body.width + ROOT_PAD * em, top, bottom) { x, baseline, color ->
            val path = Path().apply {
                moveTo(x + 0.02f * em, baseline - 0.28f * em)
                lineTo(x + 0.14f * em, baseline - 0.36f * em)
                lineTo(x + 0.34f * em, baseline + bottom - rule)
                lineTo(x + sign - 0.06f * em, baseline - top + rule / 2)
                lineTo(x + sign + body.width + ROOT_PAD * em, baseline - top + rule / 2)
            }
            drawPath(path, color, style = Stroke(width = rule * 1.15f, join = StrokeJoin.Miter, cap = StrokeCap.Butt))
            body.draw(this, x + sign, baseline, color)
        }
    }

    private fun accent(node: MathNode.Accent, style: MathStyle): MathBox {
        val body = layout(node.body, style)
        val em = em(style)
        val rule = max(RULE * em, 1f)
        val lift = body.ascent + ACCENT_GAP * em
        val italic = (node.body as? MathNode.Symbol)?.font == MathFont.ITALIC
        val shift = if (italic) ACCENT_ITALIC_SHIFT * em else 0f
        val accentHeight = if (node.accent == AccentKind.BAR) rule else ACCENT_HEIGHT * em
        return MathBox(body.width, lift + accentHeight + rule, body.descent, body.kind) { x, baseline, color ->
            body.draw(this, x, baseline, color)
            val centre = x + body.width / 2 + shift
            val half = minOf(body.width * 0.45f, ACCENT_HALF_WIDTH * em).coerceAtLeast(0.18f * em)
            val y = baseline - lift
            when (node.accent) {
                AccentKind.BAR -> drawLine(color, Offset(centre - half, y - rule), Offset(centre + half, y - rule), rule)

                AccentKind.HAT -> {
                    val path = Path().apply {
                        moveTo(centre - half, y)
                        lineTo(centre, y - accentHeight)
                        lineTo(centre + half, y)
                    }
                    drawPath(path, color, style = Stroke(width = rule, join = StrokeJoin.Miter))
                }

                AccentKind.TILDE -> {
                    val path = Path().apply {
                        moveTo(centre - half, y - accentHeight * 0.3f)
                        cubicTo(
                            centre - half / 2,
                            y - accentHeight * 1.1f,
                            centre + half / 2,
                            y + accentHeight * 0.3f,
                            centre + half,
                            y - accentHeight * 0.7f,
                        )
                    }
                    drawPath(path, color, style = Stroke(width = rule, cap = StrokeCap.Round))
                }
            }
        }
    }

    private fun delimited(node: MathNode.Delimited, style: MathStyle): MathBox {
        val body = layout(node.body, style)
        val em = em(style)
        if (body.height <= NATURAL_DELIMITER * em) {
            val parts = listOfNotNull(
                node.left.takeIf { it != "." }?.let { glyphs(it, em, MathFont.UPRIGHT, AtomKind.OPEN) },
                body,
                node.right.takeIf { it != "." }?.let { glyphs(it, em, MathFont.UPRIGHT, AtomKind.CLOSE) },
            )
            return stack(parts, AtomKind.INNER)
        }
        val axis = axis(style)
        val half = max(body.ascent - axis, body.descent + axis) + DELIMITER_OVERSHOOT * em
        val left = delimiter(node.left, half, axis, em, opening = true)
        val right = delimiter(node.right, half, axis, em, opening = false)
        return stack(listOf(left, body, right), AtomKind.INNER)
    }

    private fun stack(parts: List<MathBox>, kind: AtomKind): MathBox {
        val width = parts.sumOf { it.width.toDouble() }.toFloat()
        return MathBox(width, parts.maxOf { it.ascent }, parts.maxOf { it.descent }, kind) { x, baseline, color ->
            var pen = x
            for (part in parts) {
                part.draw(this, pen, baseline, color)
                pen += part.width
            }
        }
    }

    /** A delimiter drawn [half] above and below the maths axis. */
    private fun delimiter(symbol: String, half: Float, axis: Float, em: Float, opening: Boolean): MathBox {
        if (symbol == ".") return MathBox(0f, 0f, 0f)
        val rule = max(RULE * em, 1f)
        val tall = 2 * half
        val width = when (symbol) {
            "(", ")" -> (0.36f + 0.06f * tall / em) * em
            "[", "]", "⌊", "⌋", "⌈", "⌉" -> 0.36f * em
            "{", "}" -> 0.5f * em
            "⟨", "⟩" -> 0.4f * em
            else -> 0.28f * em
        }
        return MathBox(width, half + axis, half - axis, if (opening) AtomKind.OPEN else AtomKind.CLOSE) { x, baseline, color ->
            val top = baseline - axis - half
            val bottom = baseline - axis + half
            val middle = baseline - axis
            val inner = if (opening) x + width * 0.82f else x + width * 0.18f
            val outer = if (opening) x + width * 0.2f else x + width * 0.8f
            val path = Path()
            when (symbol) {
                "(", ")" -> {
                    // A crescent: thin at the ends, thicker at the middle, bowed by about a quarter em.
                    val end = if (opening) x + width * 0.85f else x + width * 0.15f
                    val apex = if (opening) x + width * 0.12f else x + width * 0.88f
                    val thick = if (opening) 1.7f * rule else -1.7f * rule
                    path.moveTo(end, top)
                    path.quadraticTo(2 * apex - end, middle, end, bottom)
                    path.quadraticTo(2 * (apex + thick) - end, middle, end, top)
                    path.close()
                    drawPath(path, color)
                }

                "[", "]", "⌊", "⌋", "⌈", "⌉" -> {
                    val hasTop = symbol in setOf("[", "]", "⌈", "⌉")
                    val hasBottom = symbol in setOf("[", "]", "⌊", "⌋")
                    if (hasTop) path.moveTo(inner, top + rule / 2) else path.moveTo(outer, top)
                    path.lineTo(outer, top + rule / 2)
                    path.lineTo(outer, bottom - rule / 2)
                    if (hasBottom) path.lineTo(inner, bottom - rule / 2)
                    drawPath(path, color, style = Stroke(width = rule, join = StrokeJoin.Miter))
                }

                "{", "}" -> {
                    val stem = x + width / 2
                    val tip = if (opening) x + width * 0.1f else x + width * 0.9f
                    val end = if (opening) x + width * 0.9f else x + width * 0.1f
                    val r = minOf(0.18f * em, half / 3)
                    path.moveTo(end, top + rule / 2)
                    path.quadraticTo(stem, top, stem, top + r)
                    path.lineTo(stem, middle - r)
                    path.quadraticTo(stem, middle, tip, middle)
                    path.quadraticTo(stem, middle, stem, middle + r)
                    path.lineTo(stem, bottom - r)
                    path.quadraticTo(stem, bottom, end, bottom - rule / 2)
                    drawPath(path, color, style = Stroke(width = rule * 1.1f, join = StrokeJoin.Round, cap = StrokeCap.Round))
                }

                "⟨", "⟩" -> {
                    path.moveTo(inner, top)
                    path.lineTo(outer, middle)
                    path.lineTo(inner, bottom)
                    drawPath(path, color, style = Stroke(width = rule, join = StrokeJoin.Miter))
                }

                "‖" -> {
                    drawLine(color, Offset(x + width * 0.3f, top), Offset(x + width * 0.3f, bottom), rule)
                    drawLine(color, Offset(x + width * 0.7f, top), Offset(x + width * 0.7f, bottom), rule)
                }

                else -> drawLine(color, Offset(x + width / 2, top), Offset(x + width / 2, bottom), rule)
            }
        }
    }

    private fun cases(node: MathNode.Cases, style: MathStyle): MathBox {
        val inner = if (style == MathStyle.DISPLAY) MathStyle.TEXT else style
        val cells = node.rows.map { row -> row.map { layout(it, inner) } }
        val em = em(style)
        val columns = cells.maxOfOrNull { it.size } ?: 0
        val widths = FloatArray(columns) { c -> cells.maxOf { it.getOrNull(c)?.width ?: 0f } }
        val rowGap = CASES_ROW_GAP * em
        val ascents = cells.map { row -> row.maxOfOrNull { it.ascent } ?: 0f }
        val descents = cells.map { row -> row.maxOfOrNull { it.descent } ?: 0f }
        val total = ascents.sum() + descents.sum() + rowGap * (cells.size - 1).coerceAtLeast(0)
        val axis = axis(style)
        val brace = delimiter("{", total / 2 + DELIMITER_OVERSHOOT * em, axis, em, opening = true)
        val gap = CASES_GAP * em
        val columnGap = CASES_COLUMN_GAP * em
        val width = brace.width + gap + widths.sum() + columnGap * (columns - 1).coerceAtLeast(0)
        return MathBox(width, max(total / 2 + axis, brace.ascent), max(total / 2 - axis, brace.descent), AtomKind.INNER) {
                x,
                baseline,
                color,
            ->
            brace.draw(this, x, baseline, color)
            var y = baseline - axis - total / 2
            for ((r, row) in cells.withIndex()) {
                y += ascents[r]
                var pen = x + brace.width + gap
                for ((c, cell) in row.withIndex()) {
                    cell.draw(this, pen, y, color)
                    pen += widths[c] + columnGap
                }
                y += descents[r] + rowGap
            }
        }
    }

    private fun supShift(style: MathStyle) = when (style) {
        MathStyle.DISPLAY -> 0.413f
        MathStyle.TEXT -> 0.363f
        else -> 0.289f
    }

    private fun spacing(left: AtomKind, right: AtomKind, style: MathStyle): Float {
        val mu = SPACING[left.ordinal][right.ordinal]
        // Negative entries apply only in display and text styles.
        return when {
            mu >= 0 -> mu / 18f
            style.cramped -> 0f
            else -> -mu / 18f
        }
    }

    private companion object {
        const val AXIS = 0.25f
        const val X_HEIGHT = 0.5f
        const val RULE = 0.05f
        const val ITALIC_CORRECTION = 0.04f
        const val LARGE_DISPLAY = 1.6f
        const val DRAWN_DISPLAY = 1.45f
        const val DRAWN_TEXT = 0.9f
        const val LARGE_TEXT = 1.15f
        const val OPERATOR_HEIGHT = 0.71f
        const val OPERATOR_DEPTH = 0.0f
        const val SUP_DROP = 0.386f
        const val SUB_DROP = 0.05f
        const val SUB_ALONE = 0.15f
        const val SUB_WITH_SUP = 0.247f
        const val SCRIPT_GAP = 0.16f
        const val SCRIPT_SPACE = 0.05f
        const val LIMIT_GAP = 0.14f
        const val FRACTION_GAP_DISPLAY = 0.14f
        const val FRACTION_GAP = 0.09f
        const val FRACTION_PAD = 0.14f
        const val ROOT_GAP = 0.12f
        const val ROOT_DEPTH = 0.06f
        const val ROOT_WIDTH = 0.62f
        const val ROOT_PAD = 0.06f
        const val ACCENT_GAP = 0.1f
        const val ACCENT_HEIGHT = 0.16f
        const val ACCENT_HALF_WIDTH = 0.24f
        const val ACCENT_ITALIC_SHIFT = 0.07f
        const val NATURAL_DELIMITER = 1.05f
        const val DELIMITER_OVERSHOOT = 0.1f
        const val CASES_ROW_GAP = 0.3f
        const val CASES_GAP = 0.25f
        const val CASES_COLUMN_GAP = 1f

        val UNARY_AFTER = setOf(AtomKind.BIN, AtomKind.OP, AtomKind.REL, AtomKind.OPEN, AtomKind.PUNCT)

        /**
         * Space between atoms in eighteenths of an em (The TeXbook, chapter 18): rows are the left atom,
         * columns the right, in the order of [AtomKind]. Negative values apply only in display and text.
         */
        val SPACING = arrayOf(
            //           ORD OP BIN REL OPEN CLOSE PUNCT INNER
            intArrayOf(0, 3, -4, -5, 0, 0, 0, -3), // ORD
            intArrayOf(3, 3, 0, -5, 0, 0, 0, -3), // OP
            intArrayOf(-4, -4, 0, 0, -4, 0, 0, -4), // BIN
            intArrayOf(-5, -5, 0, 0, -5, 0, 0, -5), // REL
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0), // OPEN
            intArrayOf(0, 3, -4, -5, 0, 0, 0, -3), // CLOSE
            intArrayOf(-3, -3, 0, -3, -3, -3, -3, -3), // PUNCT
            intArrayOf(-3, 3, -4, -5, -3, 0, -3, -3), // INNER
        )

        private const val TALL = "bdfhijklt0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZβδζθλξΓΔΘΛΞΠΣΦΨΩ∂∇()[]{}|/‖∫∑∏√?!%#ℝℕℙℤℚℂ′⌊⌋⌈⌉⟨⟩∀∃ℓ"
        private const val DEEP = "gjpqyβγζημξρςφϕχψ()[]{}|/‖,;⌊⌋⌈⌉⟨⟩∫"

        fun ascentOf(c: Char): Float = when (c) {
            in TALL -> 0.72f
            '+', '−', '±', '∓', '×', '=', '≠', '≈', '∼', '<', '>', '≤', '≥', '→', '←', '∝', '∈' -> 0.58f
            '·', '∗', '∘', '⊙' -> 0.4f
            ',', '.', ';', ':' -> 0.45f
            '…', '⋯' -> 0.3f
            else -> 0.5f
        }

        fun descentOf(c: Char): Float = when (c) {
            in DEEP -> if (c in "()[]{}|/‖⌊⌋⌈⌉⟨⟩∫") 0.24f else 0.21f
            '+', '−', '±', '∓', '=', '≠', '≤', '≥' -> 0.06f
            else -> 0f
        }
    }
}
