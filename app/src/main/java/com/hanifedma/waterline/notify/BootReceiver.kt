package com.hanifedma.waterline.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Puts the clock back after a reboot, an app update, or a change to the
 * device's clock.
 *
 * A reboot wipes every alarm and every notification. Without this, a fast
 * begun last night would have no timer on the lock screen until the app was
 * opened by hand — and the milestone alerts booked for it would never fire.
 *
 * Receiving BOOT_COMPLETED is also one of the few cases where an app is still
 * permitted to start a foreground service from the background, which is what
 * lets the timer come back on its own.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> FastingCoordinator.sync(context.applicationContext)
        }
    }
}
