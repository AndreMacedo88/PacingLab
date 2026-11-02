package app.pacinglab.core.designsystem.math

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class MathParserTest {
    @Test
    fun sumTakesItsScriptsAsLimits() {
        val row = MathParser.parse("P = \\sum_\\tau w_\\tau") as MathNode.Row
        val sum = row.children[2] as MathNode.Scripts
        assertThat((sum.base as MathNode.Symbol).large).isEqualTo(true)
        assertThat(sum.sub).isEqualTo(MathNode.Symbol("τ", AtomKind.ORD, MathFont.ITALIC))
    }

    @Test
    fun unknownCommandsShowTheirName() {
        assertThat(MathParser.parse("\\foo")).isEqualTo(MathNode.Symbol("foo", AtomKind.ORD, MathFont.UPRIGHT))
    }

    @Test
    fun starredOperatorNamesTakeLimits() {
        val node = MathParser.parse("\\operatorname*{median}_{d} x") as MathNode.Row
        val median = (node.children[0] as MathNode.Scripts).base as MathNode.Symbol
        assertThat(median).isEqualTo(MathNode.Symbol("median", AtomKind.OP, MathFont.UPRIGHT, limits = true))
    }

    @Test
    fun textKeepsItsSpaces() {
        assertThat(MathParser.parse("\\text{if }")).isEqualTo(MathNode.Text("if "))
    }

    @Test
    fun fractionsScriptsAndCases() {
        assertThat(MathParser.parse("\\frac{a}{b}")).isEqualTo(
            MathNode.Fraction(MathNode.Symbol("a", AtomKind.ORD, MathFont.ITALIC), MathNode.Symbol("b", AtomKind.ORD, MathFont.ITALIC)),
        )
        val cases = MathParser.parse("\\begin{cases} 1 & x \\\\ 2 & y \\end{cases}") as MathNode.Cases
        assertThat(cases.rows.size).isEqualTo(2)
        assertThat(cases.rows[1].size).isEqualTo(2)
    }
}
