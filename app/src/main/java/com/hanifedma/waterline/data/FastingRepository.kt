package com.hanifedma.waterline.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hanifedma.waterline.auth.AuthManager
import com.hanifedma.waterline.auth.FirebaseGate
import com.hanifedma.waterline.core.ActiveFast
import com.hanifedma.waterline.core.Fast
import com.hanifedma.waterline.core.Fasting
import com.hanifedma.waterline.core.FastingSettings
import com.hanifedma.waterline.core.FastingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class Account(val uid: String, val name: String?, val email: String?)

enum class SyncStatus { LOCAL, LIVE, OFFLINE }

/** One-off things the UI reacts to but that aren't part of the state. */
sealed interface RepoEvent {
    data class Merged(val count: Int) : RepoEvent
    data class Error(val key: String) : RepoEvent
}

/**
 * The single source of truth for fasting data, and the only place that knows
 * whether it is talking to a JSON file or to Firestore.
 *
 * Mirrors the web app's `Store`: local until you sign in, cloud after, with
 * anything recorded as a guest merged into the account on first sign-in.
 */
class FastingRepository(
    private val app: Context,
    private val scope: CoroutineScope,
) {

    private val local = LocalStore.get(app)
    private val runtime = Runtime(app)
    private val prefs = Prefs(app)

    private val auth: FirebaseAuth? =
        if (FirebaseGate.available(app)) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? =
        if (FirebaseGate.available(app)) FirebaseFirestore.getInstance() else null

    val authManager: AuthManager? =
        auth?.let { AuthManager(it, FirebaseGate.webClientId(app)) }

    val canSignIn: Boolean get() = auth != null

    private val _state = MutableStateFlow(local.current())
    val state: StateFlow<FastingState> = _state.asStateFlow()

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _status = MutableStateFlow(SyncStatus.LOCAL)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<RepoEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RepoEvent> = _events.asSharedFlow()

    /** True once the first snapshot has landed — the splash waits on this. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var store: FastStore = local
    private var collectJob: Job? = null
    private var sawServer = false
    private var lastActive: ActiveFast? = local.current().active

    fun start() {
        /*
         * Seed the UI, but do NOT touch the mirror or announce readiness yet.
         *
         * For a signed-in user the local file is empty — its contents were
         * moved into the account on the first sign-in — so publishing it here
         * would say "no fast is running" a few hundred milliseconds before
         * Firestore's cache says otherwise. That is long enough to cancel the
         * notification, stop the foreground service and wipe the mirror the
         * alarms depend on, and the whole thing would then flicker back. The
         * mirror is only ever written by a snapshot from the store that is
         * actually in charge.
         */
        _state.value = local.current()

        val a = auth
        if (a == null) {
            listenLocal()
            return
        }
        a.addAuthStateListener { fb ->
            val user = fb.currentUser
            if (user == null) {
                _account.value = null
                listenLocal()
            } else {
                _account.value = Account(user.uid, user.displayName, user.email)
                listenCloud(user.uid)
            }
        }
    }

    // ------------------------------------------------------------
    //  Mode transitions
    // ------------------------------------------------------------

    private fun listenLocal() {
        collectJob?.cancel()
        store = local
        sawServer = false
        _status.value = SyncStatus.LOCAL
        collectJob = scope.launch {
            local.snapshots().collect { publish(it.state, fromCache = false) }
        }
    }

    private fun listenCloud(uid: String) {
        collectJob?.cancel()
        val database = db ?: return
        val cloud = CloudStore(database, uid) { key ->
            _events.tryEmit(RepoEvent.Error(key))
        }
        store = cloud
        sawServer = false

        collectJob = scope.launch {
            // Move anything recorded as a guest into the account, once, before
            // the listeners start — otherwise the empty account snapshot would
            // land first and the UI would flash an empty history.
            val guest = local.current()
            val moved = runCatching { cloud.mergeGuest(guest) { local.clearAll() } }
                .onFailure { Log.w(TAG, "merge failed", it) }
                .getOrDefault(0)
            if (moved > 0) _events.tryEmit(RepoEvent.Merged(moved))

            cloud.snapshots().collect { snap ->
                if (!snap.fromCache) sawServer = true
                publish(snap.state, fromCache = snap.fromCache)
            }
        }
    }

    /**
     * `fromCache` means "this snapshot did not come from the server", which is
     * true of the very first snapshot even when perfectly online. Treating it
     * as offline flashed the wrong badge on every load, so it only counts once
     * the server has answered at least once and then stopped.
     */
    private fun publish(next: FastingState, fromCache: Boolean) {
        val active = next.active

        // Milestone bookkeeping lives outside the state so that a fast which
        // arrives from another device — or whose start time was corrected —
        // doesn't replay every milestone it has already passed.
        if (active?.start != lastActive?.start || active?.goalHours != lastActive?.goalHours) {
            runtime.resetMilestones(active)
            if (active != null) {
                // Anything already behind us is silently marked as announced,
                // exactly as the web app's `primed` flag does on boot.
                val elapsedHours = active.elapsed() / 3.6e6
                runtime.lastStageIndex = com.hanifedma.waterline.core.stageAt(elapsedHours).index
                runtime.goalAnnounced = active.reachedGoal()
            }
            lastActive = active
        }
        runtime.active = active

        next.fasts.maxOfOrNull { it.end }?.let { newest ->
            if (newest > runtime.lastFastEnd) runtime.lastFastEnd = newest
        }

        // The synced goal is mirrored into prefs so the picker still shows the
        // right default before the first snapshot of the next cold start.
        if (next.settings.goalHours > 0) prefs.goalHours = next.settings.goalHours

        _state.value = next
        _status.value = when {
            store.mode == "local" -> SyncStatus.LOCAL
            fromCache && sawServer -> SyncStatus.OFFLINE
            else -> SyncStatus.LIVE
        }
        _ready.value = true
    }

    // ------------------------------------------------------------
    //  Writes
    // ------------------------------------------------------------

    /*
     * The three writes that change whether a fast is running also write the
     * mirror themselves, before the store has echoed anything back.
     *
     * Everything in notify/ reads the mirror, and the caller is usually about
     * to start a foreground service on the strength of it — from a notification
     * action, that permission lasts seconds. Waiting for Firestore to replay
     * its own write through the snapshot listener is fast, but it is not
     * synchronous, and "fast" is not a guarantee worth betting the timer on.
     * publish() compares against lastActive, so it sees the same values and
     * doesn't reset the milestone bookkeeping a second time.
     */
    fun startFast(goalHours: Int, at: Long = System.currentTimeMillis()) {
        if (_state.value.active != null) return
        val active = ActiveFast(at, goalHours)
        store.startFast(active)
        runtime.resetMilestones(active)
        lastActive = active
    }

    fun setGoal(goalHours: Int) {
        if (_state.value.active != null) return
        store.setGoal(goalHours)
        if (store.mode == "local") {
            // Local writes update the file; keep the device default in step so
            // the notification's "start a fast" action offers the same goal.
            prefs.goalHours = goalHours
        }
    }

    fun setStart(start: Long) {
        val active = _state.value.active ?: return
        store.setActive(active.copy(start = start))
    }

    /**
     * Ends the running fast and files it in history.
     *
     * @return the record as filed, for the celebration sheet, or null if
     *         nothing was running.
     */
    fun endFast(endAt: Long? = null): Fast? {
        val active = _state.value.active ?: return null
        val end = Fasting.clampEnd(active, endAt)
        val record = Fast(UUID.randomUUID().toString(), active.start, end, active.goalHours)
        store.endFast(record)
        runtime.resetMilestones(null)
        runtime.lastFastEnd = end
        lastActive = null
        return record
    }

    fun cancelFast() {
        if (_state.value.active == null) return
        store.cancelFast()
        runtime.resetMilestones(null)
        lastActive = null
    }

    fun updateFast(id: String, start: Long, end: Long) {
        if (!Fasting.isValidFast(start, end, 1)) return
        store.updateFast(id, start, end)
    }

    fun deleteFast(id: String) = store.deleteFast(id)

    /** The goal the picker should show when nothing is running. */
    fun defaultGoal(): Int = _state.value.settings.goalHours.takeIf { it > 0 } ?: prefs.goalHours

    suspend fun signIn(context: Context): String? =
        authManager?.signIn(context) ?: "setup.needConfig"

    suspend fun signOut(context: Context) {
        authManager?.signOut(context)
    }

    private companion object { const val TAG = "FastingRepository" }
}
