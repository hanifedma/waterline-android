package com.hanifedma.waterline.data

import android.content.Context
import androidx.core.content.edit
import com.hanifedma.waterline.core.ActiveFast
import com.hanifedma.waterline.i18n.Lang
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Device-level preferences: theme, language, and everything about how loudly
 * the app is allowed to interrupt you.
 *
 * These stay on the device rather than syncing, matching the web app — they
 * describe how *this* phone should behave, not what the fasts are.
 *
 * SharedPreferences rather than DataStore on purpose. The alarm receiver, the
 * boot receiver and the foreground service all have to read these on a
 * non-suspending code path, within milliseconds of being woken; a Flow-only
 * API would mean blocking on a coroutine in exactly the places where blocking
 * is least forgivable.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var dark: Boolean
        get() = sp.getBoolean(KEY_DARK, true) // dark by default, as on the web
        set(v) = sp.edit { putBoolean(KEY_DARK, v) }

    var lang: Lang
        get() = Lang.from(sp.getString(KEY_LANG, null))
        set(v) = sp.edit { putString(KEY_LANG, v.code) }

    /** The goal the picker starts on while nothing is running. */
    var goalHours: Int
        get() = sp.getInt(KEY_GOAL, 16)
        set(v) = sp.edit { putInt(KEY_GOAL, v) }

    var milestoneAlerts: Boolean
        get() = sp.getBoolean(KEY_MILESTONES, true)
        set(v) = sp.edit { putBoolean(KEY_MILESTONES, v) }

    var remindersOn: Boolean
        get() = sp.getBoolean(KEY_REMINDERS, true)
        set(v) = sp.edit { putBoolean(KEY_REMINDERS, v) }

    /** How often to nudge while no fast is running. */
    var reminderEveryHours: Int
        get() = sp.getInt(KEY_REMIND_EVERY, 6)
        set(v) = sp.edit { putInt(KEY_REMIND_EVERY, v.coerceIn(1, 48)) }

    /**
     * How long a swipe silences the nudge for.
     *
     * Dismissing a reminder is not the same as turning reminders off — the
     * point of the feature is that it comes back. The delete intent reschedules
     * with this, so "clear it and it never returns" can't happen by accident.
     */
    var reminderSnoozeHours: Int
        get() = sp.getInt(KEY_REMIND_SNOOZE, 3)
        set(v) = sp.edit { putInt(KEY_REMIND_SNOOZE, v.coerceIn(1, 48)) }

    var quietHours: Boolean
        get() = sp.getBoolean(KEY_QUIET, false)
        set(v) = sp.edit { putBoolean(KEY_QUIET, v) }

    /** Hour of the day, 0–23. */
    var quietFrom: Int
        get() = sp.getInt(KEY_QUIET_FROM, 23)
        set(v) = sp.edit { putInt(KEY_QUIET_FROM, v.coerceIn(0, 23)) }

    var quietTo: Int
        get() = sp.getInt(KEY_QUIET_TO, 7)
        set(v) = sp.edit { putInt(KEY_QUIET_TO, v.coerceIn(0, 23)) }

    /** Set once the notification permission has been asked for, so it is asked once. */
    var askedForNotifications: Boolean
        get() = sp.getBoolean(KEY_ASKED_NOTIF, false)
        set(v) = sp.edit { putBoolean(KEY_ASKED_NOTIF, v) }

    /** Emits on every change, so the UI can follow settings edited elsewhere. */
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(Unit)
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    private companion object {
        const val FILE = "waterline_prefs"
        const val KEY_DARK = "dark"
        const val KEY_LANG = "lang"
        const val KEY_GOAL = "goalHours"
        const val KEY_MILESTONES = "milestoneAlerts"
        const val KEY_REMINDERS = "reminders"
        const val KEY_REMIND_EVERY = "reminderEveryHours"
        const val KEY_REMIND_SNOOZE = "reminderSnoozeHours"
        const val KEY_QUIET = "quietHours"
        const val KEY_QUIET_FROM = "quietFrom"
        const val KEY_QUIET_TO = "quietTo"
        const val KEY_ASKED_NOTIF = "askedForNotifications"
    }
}

/**
 * What the background half of the app needs to know, written synchronously.
 *
 * The service, the alarm receiver and the boot receiver all wake with no
 * repository, no Firestore listener and no coroutine scope — and the first one
 * has five seconds to post a notification or Android kills it. So the running
 * fast is mirrored here on every state change, along with the bookkeeping that
 * stops a milestone being announced twice.
 *
 * This file is deliberately excluded from backup: restoring "the 16-hour
 * milestone was already announced" onto a new device would silently swallow a
 * real alert.
 */
class Runtime(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The running fast, or null. Written by the repository, read by everyone. */
    var active: ActiveFast?
        get() {
            val start = sp.getLong(KEY_START, 0L)
            val goal = sp.getInt(KEY_GOAL, 0)
            return if (start > 0L && goal > 0) ActiveFast(start, goal) else null
        }
        set(v) = sp.edit {
            if (v == null) {
                remove(KEY_START); remove(KEY_GOAL)
            } else {
                putLong(KEY_START, v.start); putInt(KEY_GOAL, v.goalHours)
            }
        }

    /** The highest stage index already announced for the running fast. */
    var lastStageIndex: Int
        get() = sp.getInt(KEY_STAGE, -1)
        set(v) = sp.edit { putInt(KEY_STAGE, v) }

    var goalAnnounced: Boolean
        get() = sp.getBoolean(KEY_GOAL_DONE, false)
        set(v) = sp.edit { putBoolean(KEY_GOAL_DONE, v) }

    /** When the most recent fast ended — the reminder quotes it. */
    var lastFastEnd: Long
        get() = sp.getLong(KEY_LAST_END, 0L)
        set(v) = sp.edit { putLong(KEY_LAST_END, v) }

    /** When the next idle nudge is due, so a re-arm doesn't move it. */
    var reminderDueAt: Long
        get() = sp.getLong(KEY_REMIND_AT, 0L)
        set(v) = sp.edit { putLong(KEY_REMIND_AT, v) }

    /** Rotates the reminder's wording so it doesn't read like a stuck record. */
    var reminderVariant: Int
        get() = sp.getInt(KEY_REMIND_VARIANT, 0)
        set(v) = sp.edit { putInt(KEY_REMIND_VARIANT, v) }

    /** Reset the per-fast bookkeeping. Called on start, and on a start-time edit. */
    fun resetMilestones(active: ActiveFast?) {
        sp.edit {
            putInt(KEY_STAGE, -1)
            putBoolean(KEY_GOAL_DONE, false)
            if (active == null) {
                remove(KEY_START); remove(KEY_GOAL)
            } else {
                putLong(KEY_START, active.start); putInt(KEY_GOAL, active.goalHours)
            }
        }
    }

    private companion object {
        const val FILE = "waterline_runtime"
        const val KEY_START = "activeStart"
        const val KEY_GOAL = "activeGoal"
        const val KEY_STAGE = "lastStageIndex"
        const val KEY_GOAL_DONE = "goalAnnounced"
        const val KEY_LAST_END = "lastFastEnd"
        const val KEY_REMIND_AT = "reminderDueAt"
        const val KEY_REMIND_VARIANT = "reminderVariant"
    }
}
