package com.hanifedma.waterline

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hanifedma.waterline.core.ActiveFast
import com.hanifedma.waterline.core.HOUR_MS
import com.hanifedma.waterline.data.Prefs
import com.hanifedma.waterline.data.Runtime
import com.hanifedma.waterline.notify.AlarmReceiver
import com.hanifedma.waterline.notify.Channels
import com.hanifedma.waterline.notify.FastingCoordinator
import com.hanifedma.waterline.notify.Ids
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notification promises, tested on a real device.
 *
 * These are the behaviours a user notices when they are broken, and every one
 * of them happens with the app closed — no screen, no coroutine scope, nothing
 * to watch. They are asserted here rather than trusted:
 *
 *   - a fast that is running puts an ongoing notification in the shade;
 *   - ending it takes that notification away;
 *   - the "start a fast?" nudge comes back after it is swiped away;
 *   - a milestone that has passed is announced exactly once.
 */
@RunWith(AndroidJUnit4::class)
class NotifyFlowTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nm = context.getSystemService(NotificationManager::class.java)
    private lateinit var runtime: Runtime
    private lateinit var prefs: Prefs

    private fun posted(id: Int) = nm.activeNotifications.firstOrNull { it.id == id }

    private fun deliver(action: String) {
        // The receiver is not exported, so it is invoked directly — which is
        // also what AlarmManager does, on this same process.
        AlarmReceiver().onReceive(context, Intent(context, AlarmReceiver::class.java).setAction(action))
        Thread.sleep(400)
    }

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName, "android.permission.POST_NOTIFICATIONS",
        )
        runtime = Runtime(context)
        prefs = Prefs(context)
        prefs.remindersOn = true
        prefs.quietHours = false
        prefs.milestoneAlerts = true
        runtime.resetMilestones(null)
        nm.cancelAll()
        Channels.ensure(context)
        Thread.sleep(300)
    }

    @After
    fun tearDown() {
        runtime.resetMilestones(null)
        runtime.reminderDueAt = 0L
        WaterlineApp.repo(context).setHideTimes(false)
        nm.cancelAll()
        FastingCoordinator.sync(context)
    }

    /*
     * Both helpers drive the repository rather than the mirror, deliberately.
     *
     * The mirror is a cache of what the store said; when the two disagree the
     * store wins — publish() overwrites it from the next snapshot — so a test
     * that writes only the mirror is testing a state the app is designed never
     * to be in, and watching it get corrected a second later.
     */
    private fun hideTheClock(on: Boolean) {
        WaterlineApp.repo(context).setHideTimes(on)
        Thread.sleep(1500)
    }

    private fun beginFast(hoursAgo: Int) {
        val repo = WaterlineApp.repo(context)
        repo.cancelFast()
        Thread.sleep(300)
        repo.startFast(16, at = System.currentTimeMillis() - hoursAgo * HOUR_MS)
        Thread.sleep(1500)
    }

    @Test
    fun aRunningFastPutsTheClockInTheShade() {
        beginFast(hoursAgo = 5)

        val timer = posted(Ids.TIMER)
        assertNotNull("the running fast should be in the shade", timer)
        assertTrue("it must not be swipeable away", timer!!.isOngoing)
        // The elapsed time is drawn by SystemUI from these two, which is what
        // keeps it ticking with no process of ours alive.
        assertTrue(timer.notification.extras.getBoolean("android.showChronometer"))
        assertEquals(runtime.active!!.start, timer.notification.`when`)
    }

    /**
     * Focus mode's hardest promise.
     *
     * Every other surface can be reworded, but the elapsed time in the shade is
     * drawn by SystemUI from these two flags — leave them on and a user who
     * asked not to see a clock gets a live, ticking one on their lock screen,
     * which is the most visible place the setting could possibly leak.
     */
    @Test
    fun hidingTheClockTakesItOffTheLockScreenToo() {
        hideTheClock(true)
        beginFast(hoursAgo = 5)

        val timer = posted(Ids.TIMER)
        assertNotNull("the fast should still be in the shade", timer)
        val extras = timer!!.notification.extras
        assertFalse(
            "SystemUI must not be asked to draw a chronometer",
            extras.getBoolean("android.showChronometer"),
        )
        assertFalse(
            "nor the timestamp the fast started at",
            extras.getBoolean("android.showWhen", true),
        )
        // Still ongoing, still the same notification — only quieter about time.
        assertTrue("it must not become swipeable", timer.isOngoing)

        val shown = extras.getString("android.subText").orEmpty()
        assertTrue("the sub-text should be a percentage, was \"$shown\"", shown.endsWith("%"))
    }

    @Test
    fun showingTheClockAgainPutsItBack() {
        hideTheClock(true)
        beginFast(hoursAgo = 3)
        assertFalse(posted(Ids.TIMER)!!.notification.extras.getBoolean("android.showChronometer"))

        // Exactly what the switch does — and what a flip in the browser does
        // once its snapshot lands here: write the setting, and let the mirror
        // wake the coordinator.
        hideTheClock(false)

        val timer = posted(Ids.TIMER)
        assertNotNull("the fast is still running, so the clock is still there", timer)
        assertTrue(
            "the chronometer should be back",
            timer!!.notification.extras.getBoolean("android.showChronometer"),
        )
        assertEquals(runtime.active!!.start, timer.notification.`when`)
    }

    @Test
    fun endingAFastTakesTheClockAway() {
        beginFast(hoursAgo = 2)
        assertNotNull(posted(Ids.TIMER))

        WaterlineApp.repo(context).cancelFast()
        Thread.sleep(1500)
        assertNull("the clock should be gone once the fast is", posted(Ids.TIMER))
    }

    /**
     * The one the whole reminder feature rests on: swiping the nudge away
     * means "not now", not "never".
     */
    @Test
    fun theNudgeComesBackAfterItIsSwipedAway() {
        prefs.reminderSnoozeHours = 3
        deliver(AlarmReceiver.ACTION_REMINDER)

        assertNotNull("a nudge should have been posted", posted(Ids.REMINDER))
        val firstDue = runtime.reminderDueAt
        assertTrue("the next nudge should already be booked", firstDue > System.currentTimeMillis())

        // Exactly what the system does when the user swipes it off the screen.
        deliver(AlarmReceiver.ACTION_REMINDER_DISMISSED)

        val snoozedDue = runtime.reminderDueAt
        val expected = System.currentTimeMillis() + 3 * HOUR_MS
        assertTrue(
            "dismissing must re-arm the nudge, not end it (due in ${(snoozedDue - System.currentTimeMillis()) / 60000} min)",
            kotlin.math.abs(snoozedDue - expected) < 5 * 60_000L,
        )
    }

    @Test
    fun theNudgeStaysAwayWhileAFastIsRunning() {
        runtime.resetMilestones(ActiveFast(System.currentTimeMillis() - HOUR_MS, 16))
        deliver(AlarmReceiver.ACTION_REMINDER)
        assertNull("no one needs reminding to fast while they are fasting", posted(Ids.REMINDER))
    }

    @Test
    fun aPassedMilestoneIsAnnouncedOnceAndOnlyOnce() {
        // Five hours in: stage 1 (insulin falling) has been passed. Pretend
        // only stage 0 has been announced so far.
        runtime.resetMilestones(ActiveFast(System.currentTimeMillis() - 5 * HOUR_MS, 16))
        runtime.lastStageIndex = 0

        deliver(AlarmReceiver.ACTION_MILESTONE)
        assertNotNull("the passed milestone should be announced", posted(Ids.MILESTONE))
        assertEquals(1, runtime.lastStageIndex)

        nm.cancel(Ids.MILESTONE)
        Thread.sleep(400)

        deliver(AlarmReceiver.ACTION_MILESTONE)
        assertNull("the same milestone must not be announced twice", posted(Ids.MILESTONE))
    }

    @Test
    fun reachingTheGoalIsAnnouncedOnce() {
        runtime.resetMilestones(ActiveFast(System.currentTimeMillis() - 17 * HOUR_MS, 16))
        // Everything up to here is already known about; only the goal is new.
        runtime.lastStageIndex = 4
        runtime.goalAnnounced = false

        deliver(AlarmReceiver.ACTION_MILESTONE)
        assertNotNull(posted(Ids.GOAL))
        assertTrue(runtime.goalAnnounced)

        nm.cancel(Ids.GOAL)
        Thread.sleep(400)
        deliver(AlarmReceiver.ACTION_MILESTONE)
        assertNull("a goal is only reached once", posted(Ids.GOAL))
    }
}
