package com.hanifedma.waterline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The rules both clients have to agree on.
 *
 * These are the web app's, ported: what a streak is, when a fast counts, how
 * the ring is eased. Whenever the two disagree about a number the user can
 * see, one of them is wrong — and this is the file that says which.
 *
 * Several run in an explicit time zone, because the interesting bugs in date
 * arithmetic only appear away from UTC.
 */
class FastingTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val newYork = ZoneId.of("America/New_York")

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0, zone: ZoneId = seoul): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun fast(
        startDay: Int,
        hours: Int = 16,
        goal: Int = 16,
        month: Int = 8,
        zone: ZoneId = seoul,
    ): Fast {
        val start = at(2026, month, startDay, 20, 0, zone)
        return Fast("f$startDay-$month", start, start + hours * HOUR_MS, goal)
    }

    // ---------- day boundaries ----------

    @Test
    fun `a day is a local calendar day, not a UTC one`() {
        // 08:00 in Seoul is 23:00 the previous day in UTC. Numbering days by
        // UTC would file this fast on the 11th and quietly break the streak.
        val morning = at(2026, 8, 12, 8, 0, seoul)
        assertEquals(
            LocalDate.of(2026, 8, 12).toEpochDay(),
            Fasting.dayIndex(morning, seoul),
        )
    }

    @Test
    fun `consecutive local midnights are one day apart across a DST change`() {
        // 2026-11-01 is the US autumn transition: that local day is 25 hours
        // long. Subtracting timestamps would call it 1.04 days and drop the
        // streak; comparing ordinals cannot.
        val before = at(2026, 10, 31, 23, 0, newYork)
        val after = at(2026, 11, 1, 23, 0, newYork)
        assertEquals(1L, Fasting.dayIndex(after, newYork) - Fasting.dayIndex(before, newYork))
    }

    // ---------- streaks ----------

    @Test
    fun `no fasts means no statistics`() {
        val stats = Fasting.computeStats(emptyList())
        assertEquals(0, stats.streak)
        assertEquals(0, stats.total)
        assertEquals(0L, stats.longest)
        assertEquals(0.0, stats.hours, 0.0001)
    }

    @Test
    fun `a streak counts the days a fast ended on`() {
        // Started 20:00, 16 hours long: each of these ends the *next* day.
        val fasts = listOf(fast(10), fast(11), fast(12))
        val now = at(2026, 8, 13, 18, 0)
        assertEquals(3, Fasting.computeStats(fasts, now, seoul).streak)
    }

    @Test
    fun `today not being logged yet does not break the streak`() {
        val fasts = listOf(fast(10), fast(11))
        // Yesterday's fast ended this morning; nothing logged today yet.
        val now = at(2026, 8, 12, 23, 0)
        assertEquals(2, Fasting.computeStats(fasts, now, seoul).streak)
    }

    @Test
    fun `a two-day gap ends the streak`() {
        val fasts = listOf(fast(5), fast(10), fast(11))
        val now = at(2026, 8, 12, 18, 0)
        assertEquals(2, Fasting.computeStats(fasts, now, seoul).streak)
    }

    @Test
    fun `an old streak does not count as current`() {
        val fasts = listOf(fast(1), fast(2), fast(3))
        val now = at(2026, 8, 20, 12, 0)
        assertEquals(0, Fasting.computeStats(fasts, now, seoul).streak)
    }

    @Test
    fun `two fasts on the same day count once`() {
        val start = at(2026, 8, 11, 20, 0)
        val fasts = listOf(
            Fast("a", start, start + 16 * HOUR_MS, 16),
            Fast("b", start + 17 * HOUR_MS, start + 20 * HOUR_MS, 16),
        )
        val now = at(2026, 8, 12, 23, 0)
        val stats = Fasting.computeStats(fasts, now, seoul)
        assertEquals(1, stats.streak)
        assertEquals(2, stats.total)
    }

    // ---------- totals ----------

    @Test
    fun `longest and total hours add up`() {
        val fasts = listOf(fast(10, hours = 16), fast(11, hours = 24), fast(12, hours = 12))
        val stats = Fasting.computeStats(fasts, at(2026, 8, 13, 18, 0), seoul)
        assertEquals(24 * HOUR_MS, stats.longest)
        assertEquals(52.0, stats.hours, 0.0001)
        assertEquals(3, stats.total)
    }

    // ---------- validity ----------

    @Test
    fun `a fast must end after it starts`() {
        assertTrue(Fasting.isValidFast(1_000L, 2_000L, 16))
        assertFalse(Fasting.isValidFast(2_000L, 2_000L, 16))
        assertFalse(Fasting.isValidFast(2_000L, 1_000L, 16))
        assertFalse(Fasting.isValidFast(null, 2_000L, 16))
        assertFalse(Fasting.isValidFast(1_000L, 2_000L, 0))
    }

    @Test
    fun `ending is clamped into the past and past the start`() {
        val now = 10_000_000L
        val active = ActiveFast(now - HOUR_MS, 16)

        // A future end is pulled back to now.
        assertEquals(now, Fasting.clampEnd(active, now + 500_000L, now))
        // An end before the start is pushed to start + 1ms, which is the least
        // firestore.rules will accept (it demands end > start).
        assertEquals(active.start + 1, Fasting.clampEnd(active, active.start - 5_000L, now))
        // Anything sensible is left alone.
        val sensible = now - 60_000L
        assertEquals(sensible, Fasting.clampEnd(active, sensible, now))
        // No value at all means "right now".
        assertEquals(now, Fasting.clampEnd(active, null, now))
    }

    // ---------- the ring ----------

    @Test
    fun `the ring is empty at zero and exactly full at the goal`() {
        assertEquals(0f, Fasting.easeProgress(0f), 0.0001f)
        assertEquals(1f, Fasting.easeProgress(1f), 0.0001f)
    }

    @Test
    fun `the ring runs ahead of the truth, but never backwards`() {
        // 2% of the way in should read as roughly 12%, or the first hour of a
        // 16-hour fast looks like nothing happened at all.
        assertTrue(Fasting.easeProgress(0.02f) > 0.10f)
        assertTrue(Fasting.easeProgress(0.5f) > 0.6f)
        var previous = 0f
        for (i in 0..100) {
            val value = Fasting.easeProgress(i / 100f)
            assertTrue("eased progress must be monotonic", value >= previous)
            previous = value
        }
    }

    @Test
    fun `progress is clamped to the goal even when the fast runs long`() {
        val now = 5_000_000L
        val active = ActiveFast(now - 20 * HOUR_MS, 16)
        assertEquals(1f, active.progress(now), 0.0001f)
        assertTrue(active.reachedGoal(now))
    }

    // ---------- stages ----------

    @Test
    fun `the stage is the last one whose hour has passed`() {
        assertEquals(0, stageAt(0.0).index)
        assertEquals(0, stageAt(3.9).index)
        assertEquals(1, stageAt(4.0).index)
        assertEquals(4, stageAt(16.0).index)
        assertEquals(4, stageAt(17.99).index)
        assertEquals(5, stageAt(18.0).index)
        assertEquals(STAGES.lastIndex, stageAt(500.0).index)
    }

    @Test
    fun `the last stage has nothing after it`() {
        assertNull(stageAt(500.0).next)
        assertEquals(STAGES[1], stageAt(0.0).next)
    }

    @Test
    fun `stage hours only ever increase`() {
        STAGES.zipWithNext { a, b -> assertTrue(b.hour > a.hour) }
    }

    // ---------- completion copy ----------

    @Test
    fun `completion copy is chosen by what was achieved`() {
        assertEquals("done.msg.goal", completionKey(16.0, 16))
        assertEquals("done.msg.24", completionKey(25.0, 24))
        assertEquals("done.msg.48", completionKey(49.0, 48))
        assertEquals("done.msg.72", completionKey(73.0, 72))
        assertEquals("done.msg.80", completionKey(13.0, 16))
        assertEquals("done.msg.50", completionKey(9.0, 16))
        assertEquals("done.msg.low", completionKey(3.0, 16))
    }

    @Test
    fun `a long fast against a short goal still reads as goal reached`() {
        // 30 hours against a 16-hour goal is not "24h deep ketosis" copy by
        // accident — it is, and that is deliberate: the message tracks the
        // hours fasted, not the goal.
        assertEquals("done.msg.24", completionKey(30.0, 16))
    }

    @Test
    fun `the quote rotates hourly and stays in range`() {
        val keys = (0 until 24).map { quoteKeyOfTheHour(it * HOUR_MS) }
        assertEquals(QUOTE_COUNT, keys.distinct().size)
        keys.forEach { key ->
            val index = key.removePrefix("quote.").toInt()
            assertTrue(index in 0 until QUOTE_COUNT)
        }
    }
}
