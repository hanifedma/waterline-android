package com.hanifedma.waterline.data

import com.hanifedma.waterline.core.ActiveFast
import com.hanifedma.waterline.core.Fast
import com.hanifedma.waterline.core.FastingState
import kotlinx.coroutines.flow.Flow

/**
 * One interface, two backends — the shape of the web app's `store.js`.
 *
 *   LOCAL   no account. Everything lives in a JSON file on the device.
 *           Fully functional, fully offline.
 *
 *   CLOUD   signed in with Google. Every read is a Firestore snapshot
 *           listener, so a fast begun on the laptop lands on the phone without
 *           a refresh, and Firestore's own cache keeps writes working offline.
 */
interface FastStore {

    val mode: String

    fun snapshots(): Flow<StoreSnapshot>

    /** Begins a fast and remembers its goal as the new default, in one write. */
    fun startFast(active: ActiveFast)

    /**
     * Replaces the running fast — used when its start time is corrected.
     * Takes the whole record rather than just the new start, because a
     * Firestore merge replaces a map field wholesale: writing
     * `activeFast: { start }` alone would drop the goal.
     */
    fun setActive(active: ActiveFast)

    /** Sets the goal for the *next* fast. */
    fun setGoal(goalHours: Int)

    /**
     * Turns focus mode on or off.
     *
     * Unlike the goal this may change mid-fast: it changes what the timer card
     * and the notification are willing to say, never what is recorded.
     */
    fun setHideTimes(hideTimes: Boolean)

    /** Files the running fast in history and clears it, atomically. */
    fun endFast(record: Fast)

    /** Throws the running fast away without logging it. */
    fun cancelFast()

    fun updateFast(id: String, start: Long, end: Long)

    fun deleteFast(id: String)
}

/**
 * @param fromCache true when Firestore answered from its offline cache rather
 *        than the server. Only meaningful in cloud mode.
 */
data class StoreSnapshot(
    val state: FastingState,
    val fromCache: Boolean = false,
)
