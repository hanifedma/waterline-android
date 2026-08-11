package com.hanifedma.waterline.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.hanifedma.waterline.MainActivity
import com.hanifedma.waterline.R
import com.hanifedma.waterline.core.ActiveFast
import com.hanifedma.waterline.core.Fasting
import com.hanifedma.waterline.core.Format
import com.hanifedma.waterline.core.STAGES
import com.hanifedma.waterline.core.Stage
import com.hanifedma.waterline.core.stageAt
import com.hanifedma.waterline.data.Prefs
import com.hanifedma.waterline.data.Runtime
import com.hanifedma.waterline.i18n.Lang
import com.hanifedma.waterline.i18n.Strings

/**
 * Every notification Waterline posts, built in one place so the foreground
 * service, the alarm receiver and the watchdog cannot drift apart — all three
 * post the *same* notification id with the *same* content, which is what lets
 * any of them rebuild it after the others have been killed.
 */
object Notifications {

    private fun openApp(context: Context, screen: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (screen != null) putExtra(MainActivity.EXTRA_OPEN, screen)
        }
        return PendingIntent.getActivity(
            context,
            screen.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun base(context: Context, channel: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_waterline)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    /**
     * The running clock.
     *
     * The elapsed time is drawn by SystemUI from `setUsesChronometer` + `when`,
     * not by us: the seconds keep ticking on the lock screen even if this
     * process is killed the moment after posting. Everything the app *does*
     * have to redraw — the countdown, the stage, the progress bar — changes at
     * most once a minute.
     */
    fun timer(context: Context, active: ActiveFast, now: Long = System.currentTimeMillis()): Notification {
        val prefs = Prefs(context)
        val lang = prefs.lang
        val fmt = Format(lang)

        val elapsed = active.elapsed(now)
        val position = stageAt(elapsed / 3.6e6)
        val stage = position.current
        val reached = active.reachedGoal(now)

        /*
         * The countdown goes in subText, not in contentText.
         *
         * A notification carrying a progress bar gives its collapsed row to
         * [title • subText • chronometer] and then the bar — contentText is
         * only read when the notification is expanded. Putting "3h 20m left"
         * there meant the one number the user pulls down the shade for was the
         * one hidden until they expanded it.
         */
        val short = if (reached) {
            Strings.t(lang, "notif.timer.pastShort", "time" to fmt.duration(elapsed - active.goalMs))
        } else {
            Strings.t(lang, "notif.timer.leftShort", "time" to fmt.countdown(active.goalMs - elapsed))
        }
        val line = if (reached) {
            Strings.t(
                lang, "notif.timer.past",
                "time" to fmt.duration(elapsed - active.goalMs), "goal" to active.goalHours,
            )
        } else {
            Strings.t(
                lang, "notif.timer.left",
                "time" to fmt.countdown(active.goalMs - elapsed), "goal" to active.goalHours,
            )
        }
        val foot = Strings.t(
            lang, "controls.startedAt",
            "start" to fmt.time(active.start),
            "goal" to active.goalHours,
            "goalAt" to fmt.time(active.goalAt()),
        )

        val coach = position.next?.let {
            Strings.t(
                lang, "coach.until",
                "time" to fmt.countdown(it.hour * 3_600_000L - elapsed),
                "stage" to Strings.t(lang, it.titleKey),
            )
        } ?: Strings.t(lang, "coach.pastAll")

        val builder = base(context, Channels.TIMER)
            .setContentTitle("${stage.icon}  ${Strings.t(lang, stage.titleKey)}")
            .setContentText(coach)
            .setSubText(short)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setWhen(active.start)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context, null))
            .addAction(
                0,
                Strings.t(lang, "notif.action.end"),
                // Opens the app on the end-of-fast sheet rather than ending
                // outright: a mis-tap in the shade must not be able to close a
                // three-day fast with no way back.
                openApp(context, MainActivity.OPEN_END),
            )

        if (Build.VERSION.SDK_INT >= 36) {
            /*
             * Android 16's Live Updates. A promoted ongoing notification gets a
             * chip in the status bar and a full card on the lock screen instead
             * of being folded away with everything else — which is exactly what
             * a fast that runs for two days needs.
             *
             * ProgressStyle draws the goal as segments, one per metabolic
             * stage, so the bar itself shows where ketosis and autophagy sit
             * and how far along the fast is between them.
             */
            builder.setRequestPromotedOngoing(true)
                .setShortCriticalText(chipText(lang, fmt, active, now))
                .setStyle(stageProgress(context, active, now))
        } else {
            // The same easing the ring uses. A linear bar next to an eased ring
            // reads as one of the two being broken.
            builder.setProgress(1000, (Fasting.easeProgress(active.progress(now)) * 1000).toInt(), false)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$line\n$coach\n$foot"))
        }

        return builder.build()
    }

    /** The status bar chip has room for about seven characters. */
    private fun chipText(lang: Lang, fmt: Format, active: ActiveFast, now: Long): String {
        val elapsed = active.elapsed(now)
        return if (active.reachedGoal(now)) {
            Strings.t(lang, "notif.timer.pastShort", "time" to fmt.duration(elapsed - active.goalMs))
        } else {
            fmt.countdown(active.goalMs - elapsed)
        }
    }

    /**
     * The goal, divided into its metabolic stages.
     *
     * Segment lengths are minutes and must add up to the goal — ProgressStyle
     * derives its maximum from them rather than being told one. Stages beyond
     * the goal are not drawn: a 16-hour fast has no business showing a band for
     * hour 48.
     */
    @androidx.annotation.RequiresApi(36)
    private fun stageProgress(context: Context, active: ActiveFast, now: Long): NotificationCompat.ProgressStyle {
        val accent = ContextCompat.getColor(context, R.color.accent)
        val dim = ContextCompat.getColor(context, R.color.accent_dim)
        val goalMinutes = (active.goalMs / 60_000L).toInt().coerceAtLeast(1)

        val bounds = STAGES.map { it.hour * 60 }
            .filter { it in 1 until goalMinutes } + goalMinutes

        val style = NotificationCompat.ProgressStyle()
            .setProgress((active.elapsed(now) / 60_000L).toInt().coerceIn(0, goalMinutes))
            .setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_stat_waterline))

        var previous = 0
        bounds.forEachIndexed { index, boundary ->
            style.addProgressSegment(
                NotificationCompat.ProgressStyle.Segment(boundary - previous)
                    .setColor(if (index % 2 == 0) accent else dim),
            )
            previous = boundary
        }
        return style
    }

    /** A stage boundary — ketosis, autophagy, and the rest. */
    fun milestone(context: Context, stage: Stage): Notification {
        val lang = Prefs(context).lang
        return base(context, Channels.milestoneChannel(context))
            .setContentTitle("${stage.icon}  ${Strings.t(lang, stage.titleKey)}")
            .setContentText(Strings.t(lang, stage.cheerKey))
            .setStyle(NotificationCompat.BigTextStyle().bigText(Strings.t(lang, stage.textKey)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, MainActivity.OPEN_BODY))
            .build()
    }

    fun goalReached(context: Context, active: ActiveFast): Notification {
        val lang = Prefs(context).lang
        return base(context, Channels.milestoneChannel(context))
            .setContentTitle(Strings.t(lang, "notif.goal.title"))
            .setContentText(Strings.t(lang, "notif.goal.body", "goal" to active.goalHours))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, null))
            .build()
    }

    /**
     * The nudge, when nothing is running.
     *
     * `deleteIntent` is the important part: swiping this away schedules the
     * next one instead of ending them. Dismissing a reminder means "not now",
     * not "never" — turning them off entirely is a switch in Settings.
     */
    fun reminder(context: Context): Notification {
        val prefs = Prefs(context)
        val runtime = Runtime(context)
        val lang = prefs.lang
        val fmt = Format(lang)
        val goal = prefs.goalHours

        val variant = runtime.reminderVariant % 4
        val body = when {
            variant == 1 && runtime.lastFastEnd > 0 -> Strings.t(
                lang, "notif.reminder.1",
                "time" to fmt.duration(System.currentTimeMillis() - runtime.lastFastEnd),
            )
            variant == 2 -> Strings.t(
                lang, "notif.reminder.2",
                "goal" to goal,
                "at" to fmt.time(System.currentTimeMillis() + goal * 3_600_000L),
            )
            variant == 3 -> Strings.t(lang, "notif.reminder.3")
            else -> Strings.t(lang, "notif.reminder.0")
        }

        return base(context, Channels.REMINDER)
            .setContentTitle(Strings.t(lang, "notif.reminder.title"))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, null))
            .setDeleteIntent(broadcast(context, AlarmReceiver.ACTION_REMINDER_DISMISSED, 20))
            .addAction(
                0,
                Strings.t(lang, "notif.action.start", "goal" to goal),
                broadcast(context, AlarmReceiver.ACTION_START_FAST, 21),
            )
            .build()
    }

    /**
     * Posting is a no-op — not a crash — when the user has said no.
     *
     * Two different noes to respect: the runtime permission (Android 13+) and
     * the switch in system settings, which can be off even when the permission
     * was granted. Every caller here runs in the background, where an
     * exception is a silent process death rather than a visible bug.
     */
    fun post(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!Channels.canPost(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }
}
