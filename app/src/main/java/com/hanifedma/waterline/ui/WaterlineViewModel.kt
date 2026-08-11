package com.hanifedma.waterline.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanifedma.waterline.WaterlineApp
import com.hanifedma.waterline.core.Fast
import com.hanifedma.waterline.core.Fasting
import com.hanifedma.waterline.data.Prefs
import com.hanifedma.waterline.data.RepoEvent
import com.hanifedma.waterline.i18n.Lang
import com.hanifedma.waterline.i18n.Strings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * The screen's view of the app: the repository's state, the device's
 * preferences, and the handful of one-shot things (a snackbar, the celebration
 * sheet) that are neither.
 */
class WaterlineViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WaterlineApp.repo(app)
    private val prefs = Prefs(app)

    val ready = repo.ready
    val state = repo.state
    val account = repo.account
    val status = repo.status
    val canSignIn = repo.canSignIn

    var lang by mutableStateOf(prefs.lang); private set
    var dark by mutableStateOf(prefs.dark); private set

    var milestoneAlerts by mutableStateOf(prefs.milestoneAlerts); private set
    var remindersOn by mutableStateOf(prefs.remindersOn); private set
    var reminderEveryHours by mutableStateOf(prefs.reminderEveryHours); private set
    var reminderSnoozeHours by mutableStateOf(prefs.reminderSnoozeHours); private set
    var quietHours by mutableStateOf(prefs.quietHours); private set
    var quietFrom by mutableStateOf(prefs.quietFrom); private set
    var quietTo by mutableStateOf(prefs.quietTo); private set

    /** Set when a fast is filed, cleared when the sheet is dismissed. */
    var completion by mutableStateOf<Fast?>(null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            repo.events.collect { event ->
                when (event) {
                    is RepoEvent.Merged -> say(Strings.tCount(lang, "toast.merged", event.count, "count" to event.count))
                    is RepoEvent.Error -> say(Strings.t(lang, event.key))
                }
            }
        }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    fun t(key: String, vararg params: Pair<String, Any>) = Strings.t(lang, key, *params)

    // ------------------------------------------------------------
    //  The fast
    // ------------------------------------------------------------

    fun begin() {
        val goal = state.value.settings.goalHours.takeIf { it > 0 } ?: prefs.goalHours
        repo.startFast(goal)
        say(t("toast.started"))
    }

    /** @param at null means "right now". */
    fun end(at: Long? = null) {
        val record = repo.endFast(at) ?: return
        completion = record
    }

    fun discard() {
        repo.cancelFast()
        say(t("toast.discarded"))
    }

    fun setStart(start: Long) {
        repo.setStart(start)
        say(t("toast.startUpdated"))
    }

    fun setGoal(hours: Int) {
        repo.setGoal(hours)
        prefs.goalHours = hours
    }

    fun updateFast(id: String, start: Long, end: Long) {
        repo.updateFast(id, start, end)
        say(t("toast.fastUpdated"))
    }

    fun deleteFast(id: String) {
        repo.deleteFast(id)
        say(t("toast.deleted"))
    }

    /** The goals the picker offers: the standard list plus whatever is saved. */
    fun goalChoices(): List<Int> {
        val saved = state.value.active?.goalHours ?: state.value.settings.goalHours
        return (Fasting.GOAL_CHOICES + saved).distinct().sorted()
    }

    // ------------------------------------------------------------
    //  Account
    // ------------------------------------------------------------

    fun signIn(context: Context) {
        viewModelScope.launch {
            val error = repo.signIn(context)
            // Cancelling is a decision, not a failure; saying so is noise.
            if (error != null && error != "err.auth.cancelled") say(t(error))
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            repo.signOut(context)
            say(t("toast.signedOut"))
        }
    }

    // ------------------------------------------------------------
    //  Preferences
    // ------------------------------------------------------------

    fun toggleTheme() {
        dark = !dark
        prefs.dark = dark
    }

    fun chooseLang(next: Lang) {
        lang = next
        prefs.lang = next
    }

    fun toggleLang() = chooseLang(if (lang == Lang.KO) Lang.EN else Lang.KO)

    fun toggleMilestones(on: Boolean) {
        milestoneAlerts = on
        prefs.milestoneAlerts = on
    }

    fun setReminders(on: Boolean) {
        remindersOn = on
        prefs.remindersOn = on
    }

    fun setReminderEvery(hours: Int) {
        reminderEveryHours = hours
        prefs.reminderEveryHours = hours
    }

    fun setReminderSnooze(hours: Int) {
        reminderSnoozeHours = hours
        prefs.reminderSnoozeHours = hours
    }

    fun toggleQuietHours(on: Boolean) {
        quietHours = on
        prefs.quietHours = on
    }

    fun setQuietWindow(from: Int, to: Int) {
        quietFrom = from
        quietTo = to
        prefs.quietFrom = from
        prefs.quietTo = to
    }

    fun markNotificationsAsked() {
        prefs.askedForNotifications = true
    }

    fun notificationsAsked(): Boolean = prefs.askedForNotifications
}
