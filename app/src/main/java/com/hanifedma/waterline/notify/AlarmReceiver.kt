package com.hanifedma.waterline.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hanifedma.waterline.WaterlineApp
import com.hanifedma.waterline.data.Prefs
import com.hanifedma.waterline.data.Runtime

/**
 * Everything that happens while the app is not on screen.
 *
 * Alarms land here, and so do the two buttons on the notifications: "start a
 * fast" on the nudge, and the swipe that dismisses it.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MILESTONE = "com.hanifedma.waterline.action.MILESTONE"
        const val ACTION_REMINDER = "com.hanifedma.waterline.action.REMINDER"
        const val ACTION_REMINDER_DISMISSED = "com.hanifedma.waterline.action.REMINDER_DISMISSED"
        const val ACTION_START_FAST = "com.hanifedma.waterline.action.START_FAST"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        Channels.ensure(app)
        val prefs = Prefs(app)
        val runtime = Runtime(app)
        val now = System.currentTimeMillis()

        when (intent.action) {
            // A stage boundary or the goal came due. sync() re-reads the clock
            // rather than trusting the alarm's timing, announces whatever is
            // genuinely new, and books the next one.
            ACTION_MILESTONE -> FastingCoordinator.sync(app)

            ACTION_REMINDER -> {
                if (runtime.active != null) {
                    // A fast started since this was booked. Nothing to nudge.
                    FastingCoordinator.sync(app)
                    return
                }
                if (!prefs.remindersOn) return

                Notifications.post(app, Ids.REMINDER, Notifications.reminder(app))
                // Rotate the wording only after posting, so the next nudge
                // reads differently and this one reads as intended.
                runtime.reminderVariant = (runtime.reminderVariant + 1) % 4

                val next = Alarms.applyQuietHours(prefs, now + prefs.reminderEveryHours * 3_600_000L)
                runtime.reminderDueAt = next
                Alarms.scheduleReminder(app, next)
            }

            /*
             * The user swiped the nudge away.
             *
             * This is the whole reason the reminder has a deleteIntent:
             * clearing it means "not now", and it must come back. Without this,
             * one swipe would silence reminders permanently and look exactly
             * like the feature being broken. Turning them off for good is a
             * switch in Settings, where the user can see what they chose.
             */
            ACTION_REMINDER_DISMISSED -> {
                if (!prefs.remindersOn || runtime.active != null) return
                val next = Alarms.applyQuietHours(prefs, now + prefs.reminderSnoozeHours * 3_600_000L)
                runtime.reminderDueAt = next
                Alarms.scheduleReminder(app, next)
            }

            // "Start 16h fast" straight from the nudge. Acting on a
            // notification is one of the ways an app is allowed to start a
            // foreground service from the background, so the clock appears
            // immediately rather than at the next launch.
            ACTION_START_FAST -> {
                Notifications.cancel(app, Ids.REMINDER)
                val repo = WaterlineApp.repo(app)
                repo.startFast(repo.defaultGoal())
                FastingCoordinator.sync(app)
            }
        }
    }
}
