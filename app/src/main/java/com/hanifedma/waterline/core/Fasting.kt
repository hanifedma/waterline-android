package com.hanifedma.waterline.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The fasting model and every rule derived from it.
 *
 * A line-for-line port of the web app's `store.js` derived statistics, kept
 * separate from anything Android so it can be unit-tested on the JVM and so
 * both clients agree on what a streak *is*. `FastingTest` is the port of the
 * reasoning behind them.
 */

const val HOUR_MS = 3_600_000L
const val MINUTE_MS = 60_000L
const val DAY_MS = 86_400_000L

/** A fast that is running right now. Mirrors `{ start, goalHours }` in Firestore. */
data class ActiveFast(
    val start: Long,
    val goalHours: Int,
) {
    val goalMs: Long get() = goalHours * HOUR_MS
    fun elapsed(now: Long = System.currentTimeMillis()): Long = max(0L, now - start)
    fun goalAt(): Long = start + goalMs
    fun reachedGoal(now: Long = System.currentTimeMillis()): Boolean = elapsed(now) >= goalMs

    /** 0..1, linear. The ring eases this; nothing numeric ever does. */
    fun progress(now: Long = System.currentTimeMillis()): Float =
        if (goalMs <= 0) 0f else min(1f, max(0f, elapsed(now).toFloat() / goalMs))

    /**
     * Whole percent of the goal, for the face and the notification when the
     * clock is hidden.
     *
     * Floored, and held at 99 until the goal is genuinely met: rounding would
     * print 100% a couple of minutes early and then carry on counting, which
     * reads as a bug rather than as an achievement.
     */
    fun percent(now: Long = System.currentTimeMillis()): Int =
        if (reachedGoal(now)) 100 else min(99, (progress(now) * 100).toInt())
}

/** A finished fast. Mirrors `{ start, end, goalHours }` in Firestore. */
data class Fast(
    val id: String,
    val start: Long,
    val end: Long,
    val goalHours: Int,
) {
    val duration: Long get() = end - start
    val hitGoal: Boolean get() = duration >= goalHours * HOUR_MS
}

/**
 * The settings that sync with the account; everything else is per-device.
 *
 * @param hideTimes focus mode. While a fast is *running*, the timer card and
 *        the notification give up every number that could be turned back into
 *        a time — the elapsed clock, the countdown, the goal, the start and the
 *        projected finish — leaving the ring, the stage, and a percentage.
 */
data class FastingSettings(
    val goalHours: Int = DEFAULT_GOAL_HOURS,
    val hideTimes: Boolean = false,
) {
    companion object {
        /**
         * The one gate every settings document passes through.
         *
         * They arrive from three directions — the local JSON file, a Firestore
         * snapshot, and the guest merge — and any of them can be stale,
         * hand-edited, or written by a version that had never heard of a
         * field. A missing value lands on its default; a nonsense one is
         * ignored rather than defended against everywhere after.
         */
        fun of(goalHours: Int?, hideTimes: Boolean?): FastingSettings = FastingSettings(
            goalHours = goalHours?.takeIf { it > 0 } ?: DEFAULT_GOAL_HOURS,
            // Anything that isn't literally true is off: a document written
            // before the field existed, a null, a string.
            hideTimes = hideTimes == true,
        )
    }
}

const val DEFAULT_GOAL_HOURS = 16

/** Everything the UI draws from. */
data class FastingState(
    val active: ActiveFast? = null,
    val fasts: List<Fast> = emptyList(),
    val settings: FastingSettings = FastingSettings(),
)

data class Stats(
    val streak: Int = 0,
    val longest: Long = 0,
    val total: Int = 0,
    val hours: Double = 0.0,
)

object Fasting {

    /** Goals offered in the picker, in hours — the web app's list, verbatim. */
    val GOAL_CHOICES = listOf(12, 13, 14, 16, 18, 20, 24, 36, 48, 72)

    /**
     * The ring is an encouragement curve, not a ruler.
     *
     * Real progress is linear and, early on, invisible: twenty minutes into a
     * 16-hour fast is 2% — a sliver that reads as "you have done nothing". So
     * the ring is eased by p^0.55, which front-loads the fill (2% real → 12%
     * drawn, 50% → 68%), then slows as you approach the goal. It still starts
     * empty and lands exactly on full at the goal, so it never disagrees with
     * itself. Nothing numeric is eased.
     */
    const val RING_CURVE = 0.55f

    fun easeProgress(p: Float): Float = p.coerceIn(0f, 1f).toDouble().pow(RING_CURVE.toDouble()).toFloat()

    /**
     * A calendar day, numbered.
     *
     * Days must be compared by ordinal, never by subtracting timestamps:
     * across a daylight-saving boundary two consecutive local midnights are 23
     * or 25 hours apart, which would silently break a streak twice a year.
     */
    fun dayIndex(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

    /** The set of calendar days on which a fast was completed. */
    fun fastedDays(fasts: List<Fast>, zone: ZoneId = ZoneId.systemDefault()): Set<Long> =
        fasts.mapTo(HashSet()) { dayIndex(it.end, zone) }

    fun computeStats(
        fasts: List<Fast>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Stats {
        if (fasts.isEmpty()) return Stats()

        var longest = 0L
        var totalMs = 0L
        for (f in fasts) {
            val dur = f.end - f.start
            totalMs += dur
            if (dur > longest) longest = dur
        }

        // A streak counts consecutive calendar days on which a fast ended.
        // Today not being logged yet doesn't break it — yesterday still counts.
        val days = fastedDays(fasts, zone).sortedDescending()
        val today = dayIndex(now, zone)
        var streak = 0
        if (days[0] == today || days[0] == today - 1) {
            streak = 1
            for (i in 1 until days.size) {
                if (days[i - 1] - days[i] == 1L) streak++ else break
            }
        }

        return Stats(streak = streak, longest = longest, total = fasts.size, hours = totalMs / 3.6e6)
    }

    /**
     * A finished fast worth keeping — and the shape firestore.rules will
     * accept. Anything else is dropped on the way in rather than defended
     * against everywhere after: one malformed record would otherwise poison
     * every statistic.
     */
    fun isValidFast(start: Long?, end: Long?, goalHours: Int?): Boolean =
        start != null && end != null && goalHours != null && goalHours > 0 && end > start

    /**
     * Clamps a requested end time. A fast can never end in the future, and
     * never before it started. The lower bound also satisfies firestore.rules,
     * which demands end > start — begin-then-end inside one millisecond would
     * otherwise have its write rejected by the server.
     */
    fun clampEnd(active: ActiveFast, requested: Long?, now: Long = System.currentTimeMillis()): Long =
        max(active.start + 1, min(requested ?: now, now))
}
