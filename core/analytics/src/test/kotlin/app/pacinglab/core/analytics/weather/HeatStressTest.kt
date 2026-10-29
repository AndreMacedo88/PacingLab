package app.pacinglab.core.analytics.weather

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class HeatStressTest {
    @Test
    fun `wet-bulb globe temperature follows the Bureau of Meteorology approximation`() {
        // 30 °C at 50 % humidity: vapour pressure 21.2 hPa, WBGT 0.567 x 30 + 0.393 x 21.2 + 3.94 = 29.3.
        assertThat(HeatStress.wbgt(30.0, 50.0)).isCloseTo(29.3, 0.1)
        assertThat(HeatStress.wbgt(10.0, 80.0)).isCloseTo(13.5, 0.1)
    }

    @Test
    fun `levels follow the ACSM bands`() {
        assertThat(HeatStress.level(HeatStress.wbgt(12.0, 70.0))).isEqualTo(HeatStressLevel.LOW)
        assertThat(HeatStress.level(HeatStress.wbgt(22.0, 60.0))).isEqualTo(HeatStressLevel.MODERATE)
        assertThat(HeatStress.level(HeatStress.wbgt(27.0, 60.0))).isEqualTo(HeatStressLevel.HIGH)
        assertThat(HeatStress.level(HeatStress.wbgt(33.0, 60.0))).isEqualTo(HeatStressLevel.EXTREME)
    }
}
