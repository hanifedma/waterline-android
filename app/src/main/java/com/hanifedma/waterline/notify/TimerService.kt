package com.hanifedma.waterline.notify

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import com.hanifedma.waterline.R
import com.hanifedma.waterline.WaterlineApp
import com.hanifedma.waterline.data.Prefs
import com.hanifedma.waterline.data.Runtime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The fasting clock, as a foreground service.
 *
 * What it is actually for is subtle. The *elapsed time* does not need this
 * service at all — `setUsesChronometer` hands that to SystemUI, which keeps
 * counting whether or not Waterline is running. The service exists so that:
 *
 *   - the notification is un-dismissable while a fast is running, and Android
 *     stops treating the app as idle (no Doze, no battery-saver freeze);
 *   - the countdown, the stage and the progress bar stay current, once a
 *     minute rather than once a second;
 *   - milestones fire even if the alarm behind them was delayed.
 *
 * It is declared `stopWithTask="false"`: swiping Waterline out of recents must
 * not stop a fast that is still running.
 */
class TimerService : LifecycleService() {

    private var ticker: Job? = null
    private var watcher: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val active = Runtime(this).active
        if (active == null) {
            // Started for a fast that has since ended — possible when the
            // start and the end race each other. The contract still has to be
            // honoured: anything launched with startForegroundService() must
            // call startForeground() or the system kills the app.
            enterForeground(placeholder())
            stopEverything()
            return START_NOT_STICKY
        }

        enterForeground(Notifications.timer(this, active))
        startTicking()
        startWatching()

        // START_STICKY: if the system reclaims the process, it recreates the
        // service with a null intent, and onStartCommand reads the running
        // fast back out of the mirror.
        return START_STICKY
    }

    private fun enterForeground(notification: Notification) {
        try {
            ServiceCompat.startForeground(
                this,
                Ids.TIMER,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            // Android 12+ can refuse a background start. The notification was
            // already posted by the coordinator, so the user still sees the
            // clock; this service simply doesn't get to exist right now.
            Log.w(TAG, "Couldn't enter the foreground", e)
            stopSelf()
        }
    }

    /**
     * Redraws once a minute, on the minute.
     *
     * Aligned to the wall clock rather than "every 60s from whenever we
     * started", so "3h 20m left" changes at the same moment the user's own
     * clock does.
     */
    private fun startTicking() {
        if (ticker?.isActive == true) return
        ticker = lifecycleScope.launch {
            val prefs = Prefs(this@TimerService)
            val runtime = Runtime(this@TimerService)
            while (isActive) {
                val active = runtime.active ?: break
                Notifications.post(this@TimerService, Ids.TIMER, Notifications.timer(this@TimerService, active))

                // A belt to the alarms' braces: if an alarm was delayed or
                // dropped, the milestone still lands within a minute.
                FastingCoordinator.checkMilestones(this@TimerService, prefs, runtime, active)
                Alarms.nextMilestoneAt(active, runtime)?.let {
                    Alarms.scheduleMilestone(this@TimerService, it)
                }

                delay(msToNextMinute())
            }
        }
    }

    /** Reacts the instant the fast ends, or its start time is corrected. */
    private fun startWatching() {
        if (watcher?.isActive == true) return
        watcher = lifecycleScope.launch {
            val repo = WaterlineApp.repo(this@TimerService)
            // Never stop on a state the store has not actually loaded yet —
            // on a cold start that state is empty for a second, and this
            // service would delete the very notification it was started for.
            repo.ready.first { it }
            repo.state.collect { state ->
                val active = state.active
                if (active == null) {
                    stopEverything()
                } else {
                    Notifications.post(
                        this@TimerService, Ids.TIMER,
                        Notifications.timer(this@TimerService, active),
                    )
                }
            }
        }
    }

    private fun stopEverything() {
        ticker?.cancel()
        watcher?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Notifications.cancel(this, Ids.TIMER)
        stopSelf()
    }

    override fun onDestroy() {
        // If a fast is still running when the service goes away — a system
        // reclaim, an OEM task killer — the notification is DETACHED rather
        // than removed, so the lock screen keeps its clock (the chronometer
        // needs no process to keep ticking) until the watchdog or the next
        // alarm brings the service back.
        if (Runtime(this).active != null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        }
        super.onDestroy()
    }

    private fun placeholder(): Notification =
        NotificationCompat.Builder(this, Channels.TIMER)
            .setSmallIcon(R.drawable.ic_stat_waterline)
            .setColor(ContextCompat.getColor(this, R.color.accent))
            .setContentTitle(getString(R.string.app_name))
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun msToNextMinute(): Long {
        val now = System.currentTimeMillis()
        return 60_000L - (now % 60_000L) + 250L // a hair past the boundary
    }

    private companion object { const val TAG = "TimerService" }
}
