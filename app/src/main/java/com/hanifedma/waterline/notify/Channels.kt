package com.hanifedma.waterline.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.hanifedma.waterline.R

/**
 * The three ways Waterline is allowed to speak.
 *
 * A channel's importance, sound and vibration are frozen at creation — the
 * user owns them from then on. That is why the Do Not Disturb variant is a
 * *separate* channel rather than a flag flipped later: `setBypassDnd(true)` on
 * an existing channel is silently ignored, so granting notification-policy
 * access after the fact would appear to do nothing.
 */
object Channels {

    const val TIMER = "waterline.fasting"
    const val MILESTONE = "waterline.milestone"
    const val MILESTONE_DND = "waterline.milestone.dnd"
    const val REMINDER = "waterline.reminder"

    /** The IMPORTANCE_LOW channel this app shipped with before v1. */
    private const val LEGACY_TIMER = "waterline.timer"

    fun ensure(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return

        /*
         * The running clock: IMPORTANCE_DEFAULT, with no sound and no
         * vibration of its own.
         *
         * IMPORTANCE_LOW would be the obvious choice for something that must
         * never make a noise, and it is wrong. Android files a LOW channel
         * under "Silent", and from Android 15 the lock screen *minimises*
         * silent notifications down to an icon in a chip — so the fasting
         * clock, the one thing this app exists to put on the lock screen,
         * disappeared from it. DEFAULT with a null sound is quiet in exactly
         * the same way and stays visible.
         *
         * The channel cannot be edited after creation, so the id changed with
         * the importance; the old one is deleted rather than left behind in
         * the user's notification settings.
         */
        nm.deleteNotificationChannel(LEGACY_TIMER)
        nm.createNotificationChannel(
            NotificationChannel(
                TIMER,
                context.getString(R.string.channel_timer),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_timer_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(
                MILESTONE,
                context.getString(R.string.channel_milestone),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_milestone_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(
                REMINDER,
                context.getString(R.string.channel_reminder),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_reminder_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
    }

    /**
     * Which channel a milestone should go out on.
     *
     * With notification-policy access granted, the user has explicitly said
     * these may interrupt Do Not Disturb, so they move to a channel created
     * with that permission. Without it, the ordinary channel is used and DND
     * silences them like anything else.
     */
    fun milestoneChannel(context: Context): String {
        val nm = context.getSystemService<NotificationManager>() ?: return MILESTONE
        if (!nm.isNotificationPolicyAccessGranted) return MILESTONE

        if (nm.getNotificationChannel(MILESTONE_DND) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    MILESTONE_DND,
                    context.getString(R.string.channel_milestone),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_milestone_desc)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                },
            )
        }
        return MILESTONE_DND
    }

    fun canPost(context: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** Every notification the app can post, numbered once. */
object Ids {
    const val TIMER = 1001
    const val MILESTONE = 1002
    const val GOAL = 1003
    const val REMINDER = 1004
}
