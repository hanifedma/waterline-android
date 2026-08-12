package com.hanifedma.waterline.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.hanifedma.waterline.core.ActiveFast
import com.hanifedma.waterline.core.Fast
import com.hanifedma.waterline.core.Fasting
import com.hanifedma.waterline.core.FastingSettings
import com.hanifedma.waterline.core.FastingState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed storage, reading and writing the very same documents as the
 * web app:
 *
 *   users/{uid}                 { activeFast: { start, goalHours } | null,
 *                                 settings: { goalHours, hideTimes } }
 *   users/{uid}/fasts/{fastId}  { start, end, goalHours }
 *
 * Real-time by construction: both reads are snapshot listeners, so a fast
 * begun in the browser appears here about a second later with no refresh.
 *
 * The document shape is not ours to change. firestore.rules rejects any write
 * to a fast whose keys are not exactly [start, end, goalHours], and the web app
 * reads these fields positionally — an extra "source: android" field would be
 * rejected by the server long after the UI had moved on.
 */
class CloudStore(
    private val db: FirebaseFirestore,
    private val uid: String,
    private val onError: (String) -> Unit = {},
) : FastStore {

    override val mode = "cloud"

    private fun userRef() = db.collection("users").document(uid)
    private fun fastsCol() = userRef().collection("fasts")

    override fun snapshots(): Flow<StoreSnapshot> = callbackFlow {
        // Two listeners, one state. Each keeps its own half and re-emits the
        // whole thing, so a change to either lands in the UI immediately
        // instead of waiting for the other to fire.
        var active: ActiveFast? = null
        var settings = FastingSettings()
        var fasts: List<Fast> = emptyList()
        var userCached = true
        var fastsCached = true

        /*
         * Nothing is published until the user document has answered once.
         *
         * The running fast and the settings live on that document; the fasts
         * collection knows nothing about either. These are two independent
         * listeners with no ordering guarantee, so if the collection answers
         * first an ungated emit says "no fast is running, focus mode is off" —
         * and the repository acts on it immediately, tearing down the ongoing
         * notification, stopping the foreground service and arming the idle
         * nudge, all for a fast that never stopped. Milliseconds later the user
         * document lands and it is all built again.
         *
         * The flag is set even when the document errors, so a listener that can
         * never answer degrades to showing the fasts rather than hanging on the
         * splash for ever.
         */
        var userAnswered = false

        fun emit() {
            if (!userAnswered) return
            trySend(StoreSnapshot(FastingState(active, fasts, settings), userCached || fastsCached))
        }

        val userReg = userRef().addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
            if (err != null) {
                Log.e(TAG, "user listener failed", err)
                onError("err.auth.network")
                userAnswered = true
                emit()
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            active = readActive(snap)
            settings = readSettings(snap)
            userCached = snap.metadata.isFromCache
            userAnswered = true
            emit()
        }

        // Deliberately unordered. A Firestore orderBy silently DROPS documents
        // that lack the field, so a fast written without `start` — which the
        // rules forbid, but a hand-edited console entry would not — would
        // simply vanish. Sorting happens client-side, where a missing value is
        // just a default.
        val fastsReg = fastsCol().addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
            if (err != null) {
                Log.e(TAG, "fasts listener failed", err)
                onError("err.auth.network")
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            fasts = snap.documents.mapNotNull { toFast(it) }.sortedByDescending { it.start }
            fastsCached = snap.metadata.isFromCache
            emit()
        }

        awaitClose {
            userReg.remove()
            fastsReg.remove()
        }
    }

    /*
     * Firestore writes are deliberately NOT awaited.
     *
     * The Task they return only completes once the *server* acknowledges, so
     * awaiting it hangs indefinitely while offline — ending a fast would look
     * like it did nothing even though it is already saved locally and queued.
     * Firestore applies every write to its cache immediately and replays the
     * queue on reconnect; we only surface an error if one actually arrives.
     */
    private fun fail(key: String, e: Exception) {
        Log.e(TAG, "Firestore write failed", e)
        onError(key)
    }

    override fun startFast(active: ActiveFast) {
        userRef().set(
            mapOf(
                "activeFast" to mapOf("start" to active.start, "goalHours" to active.goalHours),
                "settings" to mapOf("goalHours" to active.goalHours),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).addOnFailureListener { fail("err.auth.network", it) }
    }

    override fun setActive(active: ActiveFast) {
        userRef().set(
            mapOf("activeFast" to mapOf("start" to active.start, "goalHours" to active.goalHours)),
            com.google.firebase.firestore.SetOptions.merge(),
        ).addOnFailureListener { fail("err.auth.network", it) }
    }

    /*
     * Both settings writes name one key each, and leave the other alone.
     *
     * SetOptions.merge() merges *recursively*: keys omitted from a nested map
     * stay as they are, so writing settings.goalHours cannot disturb
     * settings.hideTimes, or the other way round. That is what makes it safe
     * for the phone and the browser to be editing different settings at the
     * same moment.
     */
    override fun setGoal(goalHours: Int) {
        userRef().set(
            mapOf("settings" to mapOf("goalHours" to goalHours)),
            com.google.firebase.firestore.SetOptions.merge(),
        ).addOnFailureListener { fail("err.auth.network", it) }
    }

    override fun setHideTimes(hideTimes: Boolean) {
        userRef().set(
            mapOf("settings" to mapOf("hideTimes" to hideTimes)),
            com.google.firebase.firestore.SetOptions.merge(),
        ).addOnFailureListener { fail("err.auth.network", it) }
    }

    override fun endFast(record: Fast) {
        val batch = db.batch()
        batch.set(
            fastsCol().document(),
            mapOf("start" to record.start, "end" to record.end, "goalHours" to record.goalHours),
        )
        batch.set(
            userRef(),
            mapOf("activeFast" to null),
            com.google.firebase.firestore.SetOptions.merge(),
        )
        batch.commit().addOnFailureListener { fail("err.auth.network", it) }
    }

    override fun cancelFast() {
        userRef().set(
            mapOf("activeFast" to null),
            com.google.firebase.firestore.SetOptions.merge(),
        ).addOnFailureListener { fail("err.auth.network", it) }
    }

    override fun updateFast(id: String, start: Long, end: Long) {
        fastsCol().document(id).update(mapOf("start" to start, "end" to end))
            .addOnFailureListener { fail("err.auth.network", it) }
    }

    override fun deleteFast(id: String) {
        fastsCol().document(id).delete().addOnFailureListener { fail("err.auth.network", it) }
    }

    /**
     * Moves a guest's records into this account on first sign-in.
     *
     * @return how many records moved, for the message the UI shows.
     */
    suspend fun mergeGuest(guest: FastingState, onDone: () -> Unit): Int {
        if (guest.fasts.isEmpty() && guest.active == null) {
            onDone()
            return 0
        }

        // Signing in on a second device would otherwise duplicate every fast
        // that is already up there. A minute of slack absorbs the difference
        // between two clients' clocks.
        val cloudStarts = fastsCol().get().await().documents.mapNotNull {
            (it.get("start") as? Number)?.toLong()
        }
        fun isDuplicate(start: Long) = cloudStarts.any { kotlin.math.abs(it - start) < 60_000 }

        var count = 0
        val batch = db.batch()
        for (f in guest.fasts) {
            // firestore.rules would reject a malformed record, and the
            // rejection would arrive long after the guest store was cleared.
            if (!Fasting.isValidFast(f.start, f.end, f.goalHours)) continue
            if (isDuplicate(f.start)) continue
            batch.set(
                fastsCol().document(),
                mapOf("start" to f.start, "end" to f.end, "goalHours" to f.goalHours),
            )
            count++
        }
        // Queued durably; awaiting the ack would block sign-in while offline.
        if (count > 0) batch.commit().addOnFailureListener { fail("err.auth.network", it) }

        val userDoc = runCatching { userRef().get().await() }.getOrNull()
        val patch = HashMap<String, Any?>()

        // Only adopt the guest's running fast if the account isn't already fasting.
        val cloudActive = userDoc?.let { readActive(it) }
        if (guest.active != null && cloudActive == null) {
            patch["activeFast"] = mapOf(
                "start" to guest.active.start,
                "goalHours" to guest.active.goalHours,
            )
            count++
        }
        // Carry the guest's settings over to a brand-new account. An account
        // that already has settings keeps them: those followed the user here
        // from another device, and are the more deliberate choice.
        if (userDoc == null || !userDoc.exists() || userDoc.get("settings") == null) {
            patch["settings"] = mapOf(
                "goalHours" to guest.settings.goalHours,
                "hideTimes" to guest.settings.hideTimes,
            )
        }
        if (patch.isNotEmpty()) {
            userRef().set(patch, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener { fail("err.auth.network", it) }
        }

        onDone()
        return count
    }

    private companion object {
        const val TAG = "CloudStore"

        /** The web app writes JS numbers, which arrive as Double or Long. */
        fun readActive(snap: DocumentSnapshot): ActiveFast? {
            @Suppress("UNCHECKED_CAST")
            val map = snap.get("activeFast") as? Map<String, Any?> ?: return null
            val start = (map["start"] as? Number)?.toLong() ?: return null
            val goal = (map["goalHours"] as? Number)?.toInt() ?: return null
            return if (start > 0 && goal > 0) ActiveFast(start, goal) else null
        }

        fun readSettings(snap: DocumentSnapshot): FastingSettings {
            @Suppress("UNCHECKED_CAST")
            val map = snap.get("settings") as? Map<String, Any?> ?: return FastingSettings()
            return FastingSettings.of(
                goalHours = (map["goalHours"] as? Number)?.toInt(),
                hideTimes = map["hideTimes"] as? Boolean,
            )
        }

        fun toFast(d: DocumentSnapshot): Fast? {
            val start = (d.get("start") as? Number)?.toLong()
            val end = (d.get("end") as? Number)?.toLong()
            val goal = (d.get("goalHours") as? Number)?.toInt()
            if (!Fasting.isValidFast(start, end, goal)) return null
            return Fast(d.id, start!!, end!!, goal!!)
        }
    }
}
