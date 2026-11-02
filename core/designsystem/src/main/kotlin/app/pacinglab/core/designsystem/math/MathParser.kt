package app.pacinglab.core.designsystem.math

/** How TeX spaces an atom against its neighbours (The TeXbook, chapter 18). */
enum class AtomKind { ORD, OP, BIN, REL, OPEN, CLOSE, PUNCT, INNER }

enum class MathFont { ITALIC, UPRIGHT, BOLD }

enum class AccentKind { HAT, BAR, TILDE }

/** A parsed formula: a tree of the few structures the info texts need. */
sealed interface MathNode {
    /** A glyph or run of glyphs. [large] marks a big operator such as a sum, which takes its scripts above and below in display. */
    data class Symbol(val text: String, val kind: AtomKind, val font: MathFont, val large: Boolean = false, val limits: Boolean = false) :
        MathNode

    data class Row(val children: List<MathNode>) : MathNode

    data class Scripts(val base: MathNode, val sub: MathNode?, val sup: MathNode?) : MathNode

    /** [display] keeps the parts at full size, as \dfrac does inside cases or text. */
    data class Fraction(val numerator: MathNode, val denominator: MathNode, val display: Boolean = false) : MathNode

    data class Root(val body: MathNode) : MathNode

    data class Accent(val body: MathNode, val accent: AccentKind) : MathNode

    /** Brackets that grow with what they hold; "." is an invisible one. */
    data class Delimited(val left: String, val body: MathNode, val right: String) : MathNode

    /** Rows of a value and its condition, behind a brace. */
    data class Cases(val rows: List<List<MathNode>>) : MathNode

    /** Upright text with its spaces, as in \text{if }. */
    data class Text(val text: String) : MathNode

    data class Space(val em: Float) : MathNode
}

/**
 * Reads the TeX subset of the info texts: letters (italic), digits, operators, Greek letters, \frac,
 * \sqrt, ^ and _, \hat, \bar, \tilde, \sum and \prod, function names (\exp, \ln, \max, \operatorname),
 * \text, \mathrm, \mathbf, \left ... \right, \begin{cases} ... \end{cases} and the usual spaces. An
 * unknown command is kept as its name in upright type, so a typo shows rather than vanishing.
 */
class MathParser private constructor(private val source: String) {
    private var pos = 0

    companion object {
        fun parse(source: String): MathNode = MathParser(source).run { rowOf(row(emptySet())) }

        private fun rowOf(children: List<MathNode>): MathNode = children.singleOrNull() ?: MathNode.Row(children)

        private val GREEK = mapOf(
            "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ϵ", "varepsilon" to "ε",
            "zeta" to "ζ", "eta" to "η", "theta" to "θ", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν",
            "xi" to "ξ", "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "phi" to "ϕ", "varphi" to "φ",
            "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        )
        private val CAPITAL_GREEK = mapOf(
            "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π",
            "Sigma" to "Σ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        )
        private val RELATIONS = mapOf(
            "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "ne" to "≠", "neq" to "≠", "approx" to "≈",
            "sim" to "∼", "propto" to "∝", "in" to "∈", "notin" to "∉", "to" to "→", "gets" to "←", "mapsto" to "↦",
            "equiv" to "≡", "lt" to "<", "gt" to ">", "ll" to "≪", "gg" to "≫", "mid" to "|", "leftarrow" to "←",
            "rightarrow" to "→", "Rightarrow" to "⇒",
        )
        private val BINARIES = mapOf(
            "cdot" to "·",
            "times" to "×",
            "pm" to "±",
            "mp" to "∓",
            "circ" to "∘",
            "odot" to "⊙",
            "setminus" to "∖",
        )
        private val ORDINARIES = mapOf(
            "infty" to "∞", "partial" to "∂", "nabla" to "∇", "ldots" to "…", "dots" to "…", "cdots" to "⋯", "prime" to "′",
            "top" to "T", "emptyset" to "∅", "forall" to "∀", "exists" to "∃", "ell" to "ℓ", "%" to "%",
            "#" to "#", "{" to "{", "}" to "}", "|" to "‖", "lfloor" to "⌊", "rfloor" to "⌋", "lceil" to "⌈",
            "rceil" to "⌉", "langle" to "⟨", "rangle" to "⟩",
        )
        private val FUNCTIONS = setOf("exp", "ln", "log", "det", "Pr", "sin", "cos", "tanh", "sgn", "var", "median")
        private val LIMIT_FUNCTIONS = setOf("max", "min", "lim", "argmax", "argmin", "sup", "inf")

        // The text font's own capitals, enlarged: the n-ary symbols fall back to other fonts with smaller proportions.
        private val LARGE = mapOf("sum" to "Σ", "prod" to "Π", "int" to "∫")
        private val SPACES = mapOf(
            "," to 3 / 18f,
            ":" to 4 / 18f,
            ";" to 5 / 18f,
            "!" to -3 / 18f,
            " " to 1 / 3f,
            "quad" to 1f,
            "qquad" to 2f,
        )
        private val DOUBLE_STRUCK = mapOf("R" to "ℝ", "N" to "ℕ", "P" to "ℙ", "Z" to "ℤ", "Q" to "ℚ", "C" to "ℂ")
    }

    private fun peek(): Char? = source.getOrNull(pos)

    private fun skipSpaces() {
        while (peek()?.isWhitespace() == true) pos++
    }

    /** Atoms up to the end, a closing brace, or one of [stops] (commands such as "right" or "end", or "&" and "\\"). */
    private fun row(stops: Set<String>): List<MathNode> {
        val nodes = mutableListOf<MathNode>()
        while (true) {
            skipSpaces()
            val c = peek() ?: break
            if (c == '}') break
            if (c == '&' && "&" in stops) break
            if (c == '\\') {
                val name = lookCommand()
                if (name in stops) break
            }
            val atom = atom() ?: continue
            nodes += scripts(atom)
        }
        return nodes
    }

    private fun scripts(base: MathNode): MathNode {
        var sub: MathNode? = null
        var sup: MathNode? = null
        var primes = 0
        while (true) {
            skipSpaces()
            when (peek()) {
                '_' -> {
                    pos++
                    sub = argument()
                }

                '^' -> {
                    pos++
                    sup = argument()
                }

                '\'' -> {
                    pos++
                    primes++
                }

                else -> break
            }
        }
        if (primes > 0) {
            val prime = MathNode.Symbol("′".repeat(primes), AtomKind.ORD, MathFont.UPRIGHT)
            sup = if (sup == null) prime else MathNode.Row(listOf(prime, sup))
        }
        return if (sub == null && sup == null) base else MathNode.Scripts(base, sub, sup)
    }

    /** A script or command argument: a braced group or a single atom. */
    private fun argument(): MathNode {
        skipSpaces()
        if (peek() == '{') return group()
        return atom() ?: MathNode.Row(emptyList())
    }

    private fun group(): MathNode {
        pos++ // {
        val children = row(emptySet())
        if (peek() == '}') pos++
        return rowOf(children)
    }

    /** The raw text of a braced argument, for \text and \operatorname. */
    private fun rawArgument(): String {
        skipSpaces()
        if (peek() != '{') return peek()?.also { pos++ }?.toString().orEmpty()
        pos++
        val start = pos
        var depth = 1
        while (pos < source.length) {
            when (source[pos]) {
                '{' -> depth++
                '}' -> if (--depth == 0) break
            }
            pos++
        }
        val text = source.substring(start, pos)
        if (peek() == '}') pos++
        return text
    }

    private fun lookCommand(): String {
        var end = pos + 1
        if (end < source.length && source[end].isLetter()) {
            while (end < source.length && source[end].isLetter()) end++
            return source.substring(pos + 1, end)
        }
        return source.getOrNull(pos + 1)?.toString().orEmpty()
    }

    private fun command(): String {
        val name = lookCommand()
        pos += 1 + name.length
        return name
    }

    private fun atom(): MathNode? {
        val c = peek() ?: return null
        return when {
            c == '{' -> group()

            c == '\\' -> command(command())

            c.isLetter() -> {
                pos++
                MathNode.Symbol(c.toString(), AtomKind.ORD, MathFont.ITALIC)
            }

            c.isDigit() || (c == '.' && source.getOrNull(pos + 1)?.isDigit() == true) -> {
                val start = pos
                while (peek()?.let { it.isDigit() || (it == '.' && source.getOrNull(pos + 1)?.isDigit() == true) } == true) pos++
                MathNode.Symbol(source.substring(start, pos), AtomKind.ORD, MathFont.UPRIGHT)
            }

            else -> {
                pos++
                when (c) {
                    '+' -> MathNode.Symbol("+", AtomKind.BIN, MathFont.UPRIGHT)
                    '-' -> MathNode.Symbol("−", AtomKind.BIN, MathFont.UPRIGHT)
                    '*' -> MathNode.Symbol("∗", AtomKind.BIN, MathFont.UPRIGHT)
                    '=', '<', '>' -> MathNode.Symbol(c.toString(), AtomKind.REL, MathFont.UPRIGHT)
                    ',', ';' -> MathNode.Symbol(c.toString(), AtomKind.PUNCT, MathFont.UPRIGHT)
                    '(', '[' -> MathNode.Symbol(c.toString(), AtomKind.OPEN, MathFont.UPRIGHT)
                    ')', ']', '!' -> MathNode.Symbol(c.toString(), AtomKind.CLOSE, MathFont.UPRIGHT)
                    ':' -> MathNode.Symbol(":", AtomKind.REL, MathFont.UPRIGHT)
                    '~' -> MathNode.Space(1 / 3f)
                    '&' -> null
                    else -> MathNode.Symbol(c.toString(), AtomKind.ORD, MathFont.UPRIGHT)
                }
            }
        }
    }

    private fun command(name: String): MathNode? {
        GREEK[name]?.let { return MathNode.Symbol(it, AtomKind.ORD, MathFont.ITALIC) }
        CAPITAL_GREEK[name]?.let { return MathNode.Symbol(it, AtomKind.ORD, MathFont.UPRIGHT) }
        RELATIONS[name]?.let { return MathNode.Symbol(it, AtomKind.REL, MathFont.UPRIGHT) }
        BINARIES[name]?.let { return MathNode.Symbol(it, AtomKind.BIN, MathFont.UPRIGHT) }
        ORDINARIES[name]?.let { return MathNode.Symbol(it, AtomKind.ORD, MathFont.UPRIGHT) }
        LARGE[name]?.let { return MathNode.Symbol(it, AtomKind.OP, MathFont.UPRIGHT, large = true, limits = name != "int") }
        SPACES[name]?.let { return MathNode.Space(it) }
        if (name in FUNCTIONS) return MathNode.Symbol(name, AtomKind.OP, MathFont.UPRIGHT)
        if (name in LIMIT_FUNCTIONS) {
            val shown = when (name) {
                "argmax" -> "arg max"
                "argmin" -> "arg min"
                else -> name
            }
            return MathNode.Symbol(shown, AtomKind.OP, MathFont.UPRIGHT, limits = true)
        }
        return when (name) {
            "frac", "tfrac" -> MathNode.Fraction(argument(), argument())

            "dfrac" -> MathNode.Fraction(argument(), argument(), display = true)

            "sqrt" -> MathNode.Root(argument())

            "hat", "widehat" -> MathNode.Accent(argument(), AccentKind.HAT)

            "bar", "overline" -> MathNode.Accent(argument(), AccentKind.BAR)

            "tilde", "widetilde" -> MathNode.Accent(argument(), AccentKind.TILDE)

            "text", "textrm", "mbox" -> MathNode.Text(rawArgument())

            "operatorname" -> MathNode.Symbol(rawArgument(), AtomKind.OP, MathFont.UPRIGHT)

            "mathrm" -> MathNode.Symbol(rawArgument(), AtomKind.ORD, MathFont.UPRIGHT)

            "mathbf", "boldsymbol" -> MathNode.Symbol(
                rawArgument().let {
                    CAPITAL_GREEK[it.removePrefix("\\")] ?: it
                },
                AtomKind.ORD,
                MathFont.BOLD,
            )

            "mathbb" -> rawArgument().let {
                MathNode.Symbol(
                    DOUBLE_STRUCK[it] ?: it,
                    AtomKind.ORD,
                    if (it in
                        DOUBLE_STRUCK
                    ) {
                        MathFont.UPRIGHT
                    } else {
                        MathFont.BOLD
                    },
                )
            }

            "left" -> {
                val left = delimiter()
                val body = rowOf(row(setOf("right")))
                if (lookCommand() == "right") command()
                MathNode.Delimited(left, body, delimiter())
            }

            "right" -> null

            "begin" -> {
                val environment = rawArgument()
                val rows = mutableListOf<List<MathNode>>()
                var cells = mutableListOf<MathNode>()
                while (pos < source.length) {
                    cells += rowOf(row(setOf("&", "\\", "end")))
                    skipSpaces()
                    when {
                        peek() == '&' -> pos++

                        peek() == '\\' && lookCommand() == "\\" -> {
                            command()
                            rows += cells
                            cells = mutableListOf()
                        }

                        else -> {
                            if (lookCommand() == "end") {
                                command()
                                rawArgument()
                            }
                            break
                        }
                    }
                }
                if (cells.any { it != MathNode.Row(emptyList()) }) rows += cells
                if (environment == "cases") MathNode.Cases(rows) else MathNode.Row(rows.flatten())
            }

            "end" -> {
                rawArgument()
                null
            }

            "\\" -> MathNode.Space(0f)

            else -> MathNode.Symbol(name, AtomKind.ORD, MathFont.UPRIGHT)
        }
    }

    private fun delimiter(): String {
        skipSpaces()
        val c = peek() ?: return "."
        if (c == '\\') {
            return when (val name = command()) {
                "{", "lbrace" -> "{"
                "}", "rbrace" -> "}"
                "|", "Vert" -> "‖"
                "langle" -> "⟨"
                "rangle" -> "⟩"
                "lfloor" -> "⌊"
                "rfloor" -> "⌋"
                else -> name
            }
        }
        pos++
        return c.toString()
    }
}
