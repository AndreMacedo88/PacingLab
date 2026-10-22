package app.pacinglab.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Starts the app and opens every tab, so that start-up and the first frame of each screen are compiled ahead of time. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "app.pacinglab", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        for (tab in listOf("Workouts", "Performance", "Trends", "Sleep", "Today")) {
            device.wait(Until.findObject(By.text(tab)), TIMEOUT_MILLIS)?.click()
            device.waitForIdle()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
