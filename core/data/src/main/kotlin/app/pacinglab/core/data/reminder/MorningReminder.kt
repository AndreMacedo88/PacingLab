package app.pacinglab.core.data.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pacinglab.core.data.R
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.data.repository.WellnessRepository
import app.pacinglab.core.data.sync.AfterSync
import app.pacinglab.core.data.sync.AppVisibility
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Shows the reminder; a seam so that tests need no notification system. */
fun interface ReminderNotifier {
    fun notifyMorningQuestion()
}

/**
 * The morning question as a notification, for an athlete who asked for it: once a morning, after last
 * night has synced (Fitbit hands the night over when its app syncs with the watch), while the question is
 * still open, before [LATEST] and only when the app is not on screen, where Today asks anyway.
 */
@Singleton
class MorningReminder @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val nights: NightRepository,
    private val wellness: WellnessRepository,
    private val visibility: AppVisibility,
    private val dataStore: DataStore<Preferences>,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) : AfterSync {
    override suspend fun run() {
        if (!preferences.preferences.first().morningReminder || visibility.isForeground) return
        val today = LocalDate.now(clock)
        if (LocalTime.now(clock) >= LATEST || dataStore.data.first()[REMINDED] == today.toString()) return
        if (nights.observeNights().first().none { it.date == today }) return
        if (wellness.observeRested().first()[today] != null) return
        notifier.notifyMorningQuestion()
        dataStore.edit { it[REMINDED] = today.toString() }
    }

    companion object {
        /** No reminder from this time on: by the afternoon the question has lost its point. */
        val LATEST: LocalTime = LocalTime.of(13, 0)

        private val REMINDED = stringPreferencesKey("morning_reminder_last")
    }
}

/** Posts the reminder through the platform; tapping it opens the app on Today, where the question waits. */
class PlatformReminderNotifier @Inject constructor(@param:ApplicationContext private val context: Context) : ReminderNotifier {
    override fun notifyMorningQuestion() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.reminder_channel_description)
            },
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: return
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL = "morning-question"
        const val NOTIFICATION_ID = 1
    }
}
