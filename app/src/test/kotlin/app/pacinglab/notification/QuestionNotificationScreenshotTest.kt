package app.pacinglab.notification

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The content of the question notifications as the shade would hold it, on a card about as wide as on a 360 dp
 * phone at the owner's font scale. The system draws the icon, app name and time around it; those are left out.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class QuestionNotificationScreenshotTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val rested get() = PlatformQuestionNotifier.RESTED_WORDS.map(context::getString)
    private val efforts get() = PlatformQuestionNotifier.EFFORT_WORDS.map(context::getString)

    @Test
    fun restedLight() = capture { QuestionViews.build(it, TITLE, null, rested, compact = false) }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xxhdpi")
    fun restedDark() = capture { QuestionViews.build(it, TITLE, null, rested, compact = false) }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xxhdpi")
    fun restedSaved() = capture { QuestionViews.build(it, TITLE, null, rested, compact = false, chosen = 4) }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xxhdpi")
    fun restedCollapsed() = capture { QuestionViews.build(it, TITLE, null, rested, compact = true) }

    @Test
    fun effortLight() = capture { QuestionViews.build(it, EFFORT_TITLE, EFFORT_WHEN, efforts, compact = false) }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xxhdpi")
    fun effortDark() = capture { QuestionViews.build(it, EFFORT_TITLE, EFFORT_WHEN, efforts, compact = false) }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xxhdpi")
    fun effortCollapsed() = capture { QuestionViews.build(it, EFFORT_TITLE, EFFORT_WHEN, efforts, compact = true) }

    private fun capture(views: (Context) -> RemoteViews) {
        RuntimeEnvironment.setFontScale(FONT_SCALE)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val density = activity.resources.displayMetrics.density
        val night = activity.resources.configuration.isNightModeActive
        val card = FrameLayout(activity).apply {
            background = GradientDrawable().apply {
                cornerRadius = CORNER * density
                setColor(if (night) DARK_CARD else LIGHT_CARD)
            }
            val padding = (PADDING * density).roundToInt()
            setPadding(padding, padding, padding, padding)
        }
        card.addView(views(activity).apply(activity, card))
        val frame = FrameLayout(activity).apply {
            setBackgroundColor(if (night) DARK_SHADE else LIGHT_SHADE)
            val margin = (MARGIN * density).roundToInt()
            setPadding(margin, margin, margin, margin)
        }
        frame.addView(card, FrameLayout.LayoutParams(((SCREEN - 2 * MARGIN) * density).roundToInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
        activity.setContentView(frame, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.captureRoboImage()
    }

    private companion object {
        const val TITLE = "How rested do you feel?"
        const val EFFORT_TITLE = "How hard was the gym session?"
        const val EFFORT_WHEN = "Today 18:05, 52:10"
        const val FONT_SCALE = 0.9f
        const val SCREEN = 360f
        const val MARGIN = 8f
        const val PADDING = 16f
        const val CORNER = 24f
        const val LIGHT_CARD = 0xFFFFFFFF.toInt()
        const val DARK_CARD = 0xFF242424.toInt()
        const val LIGHT_SHADE = 0xFFE6E6E6.toInt()
        const val DARK_SHADE = 0xFF0E0E0E.toInt()
    }
}
