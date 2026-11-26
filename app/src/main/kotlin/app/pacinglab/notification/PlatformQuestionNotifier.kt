package app.pacinglab.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.pacinglab.R
import app.pacinglab.core.data.reminder.EffortQuestion
import app.pacinglab.core.data.reminder.QuestionNotifier
import app.pacinglab.core.designsystem.format.Formats
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import app.pacinglab.core.designsystem.R as DesignR

/**
 * Posts the questions as notifications that are answered on the notification itself: a tap on an answer saves
 * it ([QuestionAnswerReceiver]), shows it as saved for a moment and then lets the notification go. A tap
 * elsewhere opens the app on Today, which asks the same. The channels are silent; Android's notification
 * settings can give them a sound.
 */
@Singleton
class PlatformQuestionNotifier @Inject constructor(@param:ApplicationContext private val context: Context, private val clock: Clock) :
    QuestionNotifier {
    private val manager: NotificationManager? get() = context.getSystemService(NotificationManager::class.java)

    override fun askRested(morning: LocalDate) = postRested(morning, chosen = null)

    /** Shows [rating] as the saved answer for a moment. */
    fun confirmRested(morning: LocalDate, rating: Int) = postRested(morning, chosen = rating)

    override fun closeRested(morning: LocalDate) {
        manager?.cancel(morning.toString(), RESTED_ID)
    }

    override fun askEffort(question: EffortQuestion) = postEffort(question, chosen = null)

    /** Shows [effort] as the saved rating for a moment. */
    fun confirmEffort(question: EffortQuestion, effort: Int) = postEffort(question, chosen = effort)

    override fun closeEffort(workoutId: String) {
        val manager = manager ?: return
        manager.activeNotifications
            .filter { it.id == EFFORT_ID && workoutId in it.notification.extras.getStringArray(EXTRA_WORKOUTS).orEmpty() }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun postRested(morning: LocalDate, chosen: Int?) {
        val manager = ready() ?: return
        val title = context.getString(R.string.notification_rested_title)
        val words = RESTED_WORDS.map(context::getString)
        val answer = { rating: Int -> QuestionAnswerReceiver.restedIntent(context, morning, rating) }
        val notification = builder(RESTED_CHANNEL, title, chosen)
            .setCustomContentView(QuestionViews.build(context, title, null, words, compact = true, chosen, answer))
            .setCustomBigContentView(QuestionViews.build(context, title, null, words, compact = false, chosen, answer))
            .apply { if (chosen == null) setTimeoutAfter(millisUntilMidnightAfter(morning)) }
            .build()
        manager.notify(morning.toString(), RESTED_ID, notification)
    }

    private fun postEffort(question: EffortQuestion, chosen: Int?) {
        val manager = ready() ?: return
        val title = context.getString(R.string.notification_effort_title)
        val start = question.start.atZone(question.zoneOffset ?: clock.zone)
        val day = if (start.toLocalDate() == LocalDate.now(clock)) R.string.notification_today else R.string.notification_yesterday
        val detail = context.getString(
            R.string.notification_effort_when,
            context.getString(day),
            TIME.format(start),
            Formats.duration(question.durationMillis),
        )
        val words = EFFORT_WORDS.map(context::getString)
        val answer = { effort: Int -> QuestionAnswerReceiver.effortIntent(context, question, effort) }
        val notification = builder(EFFORT_CHANNEL, title, chosen)
            .setCustomContentView(QuestionViews.build(context, title, detail, words, compact = true, chosen, answer))
            .setCustomBigContentView(QuestionViews.build(context, title, detail, words, compact = false, chosen, answer))
            .addExtras(Bundle().apply { putStringArray(EXTRA_WORKOUTS, question.workoutIds.toTypedArray()) })
            // Today asks until the end of the day after the session; so does the notification.
            .apply { if (chosen == null) setTimeoutAfter(millisUntilMidnightAfter(start.toLocalDate().plusDays(1))) }
            .build()
        manager.notify(null, EFFORT_ID, notification)
    }

    /** What both questions share; an answered one leaves after [SAVED_MILLIS]. */
    private fun builder(channel: String, title: String, chosen: Int?): Notification.Builder = Notification.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setStyle(Notification.DecoratedCustomViewStyle())
        .setCategory(Notification.CATEGORY_REMINDER)
        // Nothing private in the question, and the answers should work from the lock screen.
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setOnlyAlertOnce(true)
        .setAutoCancel(true)
        .setShowWhen(chosen == null)
        .apply {
            openApp()?.let(::setContentIntent)
            if (chosen != null) setTimeoutAfter(SAVED_MILLIS)
        }

    private fun openApp(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The manager with both channels in place, or null when the athlete turned notifications off. */
    private fun ready(): NotificationManager? {
        val manager = manager?.takeIf { it.areNotificationsEnabled() } ?: return null
        manager.deleteNotificationChannel(LEGACY_CHANNEL)
        manager.createNotificationChannels(
            listOf(
                silentChannel(RESTED_CHANNEL, R.string.notification_rested_channel, R.string.notification_rested_channel_description),
                silentChannel(EFFORT_CHANNEL, R.string.notification_effort_channel, R.string.notification_effort_channel_description),
            ),
        )
        return manager
    }

    /** Shown like any notification, on the lock screen too, but without sound or vibration (the athlete's wish). */
    private fun silentChannel(id: String, name: Int, description: Int) =
        NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            this.description = context.getString(description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }

    private fun millisUntilMidnightAfter(day: LocalDate): Long {
        val end = day.plusDays(1).atStartOfDay(clock.zone).toInstant()
        return (end.toEpochMilli() - Instant.now(clock).toEpochMilli()).coerceAtLeast(SAVED_MILLIS)
    }

    internal companion object {
        const val RESTED_CHANNEL = "morning_question"
        const val EFFORT_CHANNEL = "gym_effort"

        /** The first reminder's channel, which had a sound; its replacement is silent. */
        const val LEGACY_CHANNEL = "morning-question"
        const val RESTED_ID = 1
        const val EFFORT_ID = 2
        const val EXTRA_WORKOUTS = "app.pacinglab.extra.WORKOUTS"

        /** How long an answer shows as saved before the notification leaves. */
        const val SAVED_MILLIS = 4_000L

        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val RESTED_WORDS = listOf(
            DesignR.string.rested_1,
            DesignR.string.rested_2,
            DesignR.string.rested_3,
            DesignR.string.rested_4,
            DesignR.string.rested_5,
        )

        val EFFORT_WORDS = listOf(
            DesignR.string.effort_1,
            DesignR.string.effort_2,
            DesignR.string.effort_3,
            DesignR.string.effort_4,
            DesignR.string.effort_5,
            DesignR.string.effort_6,
            DesignR.string.effort_7,
            DesignR.string.effort_8,
            DesignR.string.effort_9,
            DesignR.string.effort_10,
        )
    }
}

/** The zone offset of a question carried through an intent; null when the session had none. */
internal fun ZoneOffset?.seconds(): Int = this?.totalSeconds ?: Int.MIN_VALUE

internal fun offsetOf(seconds: Int): ZoneOffset? = seconds.takeIf { it != Int.MIN_VALUE }?.let(ZoneOffset::ofTotalSeconds)
