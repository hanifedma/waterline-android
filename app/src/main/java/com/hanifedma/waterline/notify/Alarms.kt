package com.hanifedma.waterline.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import com.hanifedma.waterline.core.ActiveFast
import com.hanifedma.waterline.core.HOUR_MS
import com.hanifedma.waterline.core.STAGES
import com.hanifedma.waterline.data.Prefs
import com.hanifedma.waterline.data.Runtime
import java.time.Instant
import java.time.ZoneId

/**
 * Alarms are the app's memory when its process is gone.
 *
 * A foreground service keeps Waterline alive in the normal case, but "normal"
 * is not a guarantee on Android: the system, a battery saver, or an
 * enthusiastic OEM task killer can all take the process away. Every event that
 * must still happen — a milestone, the goal, the next nudge — therefore has an
 * alarm behind it that can rebuild the notification from scratch.
 */
object Alarms {

    private const val RC_MILESTONE = 10
    private const val RC_REMINDER = 11
    private const val TAG = "Alarms"

    private fun pending(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Whether the user has allowed alarms that land on the exact minute. */
    fun canBeExact(context: Context): Boolean {
        val am = context.getSystemService<AlarmManager>() ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
    }

    /**
     * Exact if we are allowed to be, inexact-but-idle-proof if not.
     *
     * `setAndAllowWhileIdle` still fires in Doze; it just reserves the right to
     * be a few minutes late. That is the correct trade for a fasting app —
     * being nine minutes late with "you have reached ketosis" is fine, never
     * arriving is not.
     */
    private fun setWakeup(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService<AlarmManager>() ?: return
        try {
            if (canBeExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            // The permission can be revoked between the check and the call.
            Log.w(TAG, "Exact alarm refused, falling back", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun scheduleMilestone(context: Context, at: Long) {
        setWakeup(context, at, pending(context, AlarmReceiver.ACTION_MILESTONE, RC_MILESTONE))
    }

    fun cancelMilestone(context: Context) {
        context.getSystemService<AlarmManager>()
            ?.cancel(pending(context, AlarmReceiver.ACTION_MILESTONE, RC_MILESTONE))
    }

    fun scheduleReminder(context: Context, at: Long) {
        setWakeup(context, at, pending(context, AlarmReceiver.ACTION_REMINDER, RC_REMINDER))
    }

    fun cancelReminder(context: Context) {
        context.getSystemService<AlarmManager>()
            ?.cancel(pending(context, AlarmReceiver.ACTION_REMINDER, RC_REMINDER))
    }

    /**
     * When the next thing worth waking up for happens: the next stage
     * boundary, or the goal, whichever comes first. Null once a fast is past
     * both — the watchdog still checks in, but nothing needs an alarm.
     */
    fun nextMilestoneAt(active: ActiveFast, runtime: Runtime, now: Long = System.currentTimeMillis()): Long? {
        val elapsed = now - active.start
        val candidates = ArrayList<Long>(2)
        STAGES.firstOrNull { it.hour * HOUR_MS > elapsed }
            ?.let { candidates += active.start + it.hour * HOUR_MS }
        if (!runtime.goalAnnounced && active.goalAt() > now) candidates += active.goalAt()
        return candidates.minOrNull()
    }

    /**
     * Pushes a reminder out of the user's quiet hours.
     *
     * Reminders exist to be persistent, so the window shifts them rather than
     * cancelling them: a nudge that came due at 02:00 arrives at 07:00 instead
     * of being lost.
     */
    fun applyQuietHours(prefs: Prefs, at: Long): Long {
        if (!prefs.quietHours) return at
        val from = prefs.quietFrom
        val to = prefs.quietTo
        if (from == to) return at // an empty window, not a 24-hour one

        val zone = ZoneId.systemDefault()
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val inWindow =
            if (from < to) t.hour in from until to else (t.hour >= from || t.hour < to)
        if (!inWindow) return at

        var end = t.withHour(to).withMinute(0).withSecond(0).withNano(0)
        if (!end.isAfter(t)) end = end.plusDays(1)
        return end.toInstant().toEpochMilli()
    }
}
