package app.pacinglab.core.designsystem.math

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The formula typesetting of the info dialogs: scripts, fractions, roots, sums, accents, brackets and cases. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h1400dp-xxhdpi")
class MathEquationScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val samples = listOf(
        "y_i = \\alpha + \\mathbf{x}_i^\\top \\beta + \\varepsilon_i, \\quad \\varepsilon_i \\mid \\omega_i \\sim N\\left(0, \\frac{\\sigma^2}{\\omega_i}\\right)",
        "\\Lambda_\\tau = \\tilde{X}^\\top W \\tilde{X} + \\tau^{-2} I, \\qquad m_\\tau = \\Lambda_\\tau^{-1} \\tilde{X}^\\top W \\tilde{y}",
        "p(\\tau \\mid y) \\propto p(\\tau)\\, \\tau^{-p}\\, |\\Lambda_\\tau|^{-1/2}\\, b_\\tau^{-(n-1)/2}",
        "\\omega_i = \\frac{\\nu + 1}{\\nu + r_i^2 / \\hat{\\sigma}^2}",
        "P = \\sum_{k=1}^{K} w_k \\left[1 - F_{n-1}\\!\\left(\\frac{\\delta - m_{k}}{s_{k}}\\right)\\right]",
        "\\kappa_j = \\sqrt{\\frac{1 + \\rho_e \\rho_j}{1 - \\rho_e \\rho_j}}",
        "g(v) = \\begin{cases} 0.6 + 0.4\\,\\dfrac{v - t}{b - t} & \\text{if } v \\text{ beats } t \\\\ 0.6\\,\\dfrac{v - w}{t - w} & \\text{otherwise} \\end{cases}",
        "C_d = C_{d-1} + k_{42}\\, (L_d - C_{d-1}), \\quad k_\\tau = 1 - e^{-1/\\tau}",
        "\\bar{x} = \\frac{1}{n} \\sum_{i=1}^{n} x_i, \\quad \\hat{\\beta} = \\max_{k} \\, e^{-x^2}",
    )

    @Test
    fun formulas() {
        compose.setContent {
            PacingLabTheme {
                Surface {
                    Column(Modifier.width(312.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (sample in samples) MathEquation(sample)
                        Text(
                            inlineMath(
                                "Each night \$i\$ has a score \$y_i\$ and factors \$x_{ij}\$; the effect \$\\beta_j\$ starts at " +
                                    "\$N(0, \\tau^2\\sigma^2)\$, with \$\\hat{\\sigma}^2\$, \$e^{-1/42}\$, \$z_w < -1\$ and \$\\frac{a}{b+c}\$.",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
