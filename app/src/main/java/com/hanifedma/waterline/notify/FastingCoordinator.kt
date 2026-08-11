package com.hanifedma.waterline.notify

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.hanifedma.waterline.core.ActiveFast
import com.hanifedma.waterline.core.STAGES
import com.hanifedma.waterline.core.stageAt
import com.hanifedma.waterline.data.Prefs
import com.hanifedma.waterline.data.Runtime

/**
 * Reconciles the world with the state.
 *
 * Everything that can change what the notification shade should look like —
 * the app writing a fast, an alarm firing, a reboot, the 15-minute watchdog —
 * calls [sync] and nothing else. There is exactly one description of "what
 * should be true right now", so the shade cannot end up half-updated by two
 * code paths disagreeing.
 *
 * [sync] is idempotent by construction: posting the same notification id with
 * the same content is a no-op the user never sees, and rescheduling an alarm
 * for the same instant replaces it.
 */
object FastingCoordinator {

    private const val TAG = "FastingCoordinator"

    fun sync(context: Context) {
        val app = context.applicationContext
        Channels.ensure(app)

        val prefs = Prefs(app)
        val runtime = Runtime(app)
        val active = runtime.active

        if (active != null) {
            onRunning(app, prefs, runtime, active)
        } else {
            onIdle(app, prefs, runtime)
        }

        WatchdogWorker.ensureScheduled(app)
    }

    private fun onRunning(context: Context, prefs: Prefs, runtime: Runtime, active: ActiveFast) {
        // The nudge is for people who aren't fasting.
        Alarms.cancelReminder(context)
        Notifications.cancel(context, Ids.REMINDER)

        // Post first, start the service second.
        //
        // The notification is what the user actually asked for, and posting it
        // directly always works. Starting a foreground service does not: from
        // the background Android can refuse outright. Doing it in this order
        // means the lock screen has the clock either way, and the service —
        // when it is allowed to start — simply adopts the notification that is
        // already there, by id.
        Notifications.post(context, Ids.TIMER, Notifications.timer(context, active))
        startService(context)

        checkMilestones(context, prefs, runtime, active)

        Alarms.nextMilestoneAt(active, runtime)?.let { Alarms.scheduleMilestone(context, it) }
            ?: Alarms.cancelMilestone(context)
    }

    private fun onIdle(context: Context, prefs: Prefs, runtime: Runtime) {
        stopService(context)
        Notifications.cancel(context, Ids.TIMER)
        Alarms.cancelMilestone(context)

        if (!prefs.remindersOn) {
            Alarms.cancelReminder(context)
            runtime.reminderDueAt = 0L
            return
        }

        val now = System.currentTimeMillis()
        // Keep an existing appointment rather than pushing it back every time
        // something calls sync() — otherwise opening the app would postpone the
        // nudge forever, which is the one thing it must not do.
        val due = runtime.reminderDueAt
        val at = if (due > now) due else Alarms.applyQuietHours(prefs, now + prefs.reminderEveryHours * 3_600_000L)
        runtime.reminderDueAt = at
        Alarms.scheduleReminder(context, at)
    }

    /**
     * Announces anything the fast has passed since the last look.
     *
     * Only the *highest* new stage is announced. Coming back from a killed
     * process eight hours later must not fire four notifications at once, and
     * the ones in between were never going to be read anyway.
     */
    fun checkMilestones(context: Context, prefs: Prefs, runtime: Runtime, active: ActiveFast) {
        val now = System.currentTimeMillis()
        if (!prefs.milestoneAlerts) {
            // Still record where we are, so switching alerts back on doesn't
            // immediately fire everything that happened while they were off.
            runtime.lastStageIndex = stageAt(active.elapsed(now) / 3.6e6).index
            runtime.goalAnnounced = runtime.goalAnnounced || active.reachedGoal(now)
            return
        }

        val index = stageAt(active.elapsed(now) / 3.6e6).index
        if (index > runtime.lastStageIndex) {
            // Stage 0 is "the clock has started" — the user is looking at the
            // app at that exact moment, so it would be noise.
            if (runtime.lastStageIndex >= 0 || index > 0) {
                Notifications.post(context, Ids.MILESTONE, Notifications.milestone(context, STAGES[index]))
            }
            runtime.lastStageIndex = index
        }

        if (active.reachedGoal(now) && !runtime.goalAnnounced) {
            runtime.goalAnnounced = true
            Notifications.post(context, Ids.GOAL, Notifications.goalReached(context, active))
        }
    }

    private fun startService(context: Context) {
        val intent = Intent(context, TimerService::class.java)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException on Android 12+ when the
            // app is in the background with no exemption. Not fatal: the
            // notification is already posted, the chronometer in it ticks
            // without us, and the next alarm or watchdog pass tries again.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.w(TAG, "Foreground service refused; notification stands alone", e)
            }
        }
    }

    private fun stopService(context: Context) {
        runCatching { context.stopService(Intent(context, TimerService::class.java)) }
    }
}
