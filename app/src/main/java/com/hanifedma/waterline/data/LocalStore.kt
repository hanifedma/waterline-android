package com.hanifedma.waterline.data

import android.content.Context
import android.util.Log
import com.hanifedma.waterline.core.ActiveFast
import com.hanifedma.waterline.core.Fast
import com.hanifedma.waterline.core.Fasting
import com.hanifedma.waterline.core.FastingSettings
import com.hanifedma.waterline.core.FastingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Device-only storage: one JSON file in the app's private directory, holding
 * the same object the web app keeps under `waterline:data:v1` in
 * localStorage — `{ activeFast, fasts, settings }`.
 *
 * Chosen over Room deliberately. The whole dataset is a running fast, a small
 * list of finished ones and a single setting; it is always read and written as
 * a unit, and keeping the web app's exact shape means an export from one can
 * be read by the other. A schema and migrations would be pure overhead.
 */
class LocalStore private constructor(context: Context) : FastStore {

    companion object {
        @Volatile private var instance: LocalStore? = null

        /**
         * The one instance for this process.
         *
         * This MUST be a singleton: the repository and the sign-in migration
         * both reach for local storage, and each instance holds the state in
         * memory to emit from. Two of them would diverge and silently
         * overwrite each other.
         */
        fun get(context: Context): LocalStore =
            instance ?: synchronized(this) {
                instance ?: LocalStore(context.applicationContext).also { instance = it }
            }

        private const val TAG = "LocalStore"
    }

    private val file = File(context.filesDir, "fasts.json")
    private val state = MutableStateFlow(read())

    override val mode = "local"

    override fun snapshots(): Flow<StoreSnapshot> = state.map { StoreSnapshot(it) }

    /** The current contents, for the guest → account migration. */
    fun current(): FastingState = state.value

    /*
     * A JSON file is user-visible through adb and survives across versions, so
     * one malformed record would otherwise poison every statistic with NaN.
     * Unusable entries are dropped on the way in rather than defended against
     * everywhere after.
     */
    private fun read(): FastingState {
        if (!file.exists()) return FastingState()
        return try {
            val root = JSONObject(file.readText())

            val active = root.optJSONObject("activeFast")?.let {
                val start = it.optLong("start", 0L)
                val goal = it.optInt("goalHours", 0)
                if (start > 0L && goal > 0) ActiveFast(start, goal) else null
            }

            val arr = root.optJSONArray("fasts") ?: JSONArray()
            val fasts = ArrayList<Fast>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val start = o.optLong("start", 0L)
                val end = o.optLong("end", 0L)
                val goal = o.optInt("goalHours", 0)
                if (!Fasting.isValidFast(start, end, goal)) continue
                val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
                fasts.add(Fast(id, start, end, goal))
            }

            val goal = root.optJSONObject("settings")?.optInt("goalHours", 16) ?: 16
            FastingState(
                active = active,
                fasts = fasts.sortedByDescending { it.start },
                settings = FastingSettings(if (goal > 0) goal else 16),
            )
        } catch (e: Exception) {
            // A corrupt file must not brick the app. Better an empty history
            // the user can rebuild than a crash loop on every launch.
            Log.e(TAG, "Couldn't read local fasts", e)
            FastingState()
        }
    }

    private fun write(next: FastingState) {
        val sorted = next.copy(fasts = next.fasts.sortedByDescending { it.start })
        try {
            val root = JSONObject()
            sorted.active?.let {
                root.put("activeFast", JSONObject().put("start", it.start).put("goalHours", it.goalHours))
            }
            val arr = JSONArray()
            for (f in sorted.fasts) {
                arr.put(
                    JSONObject()
                        .put("id", f.id)
                        .put("start", f.start)
                        .put("end", f.end)
                        .put("goalHours", f.goalHours),
                )
            }
            root.put("fasts", arr)
            root.put("settings", JSONObject().put("goalHours", sorted.settings.goalHours))
            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't save local fasts", e)
        }
        state.value = sorted
    }

    override fun startFast(active: ActiveFast) {
        val s = state.value
        if (s.active != null) return
        write(s.copy(active = active, settings = FastingSettings(active.goalHours)))
    }

    override fun setActive(active: ActiveFast) {
        val s = state.value
        if (s.active == null) return
        write(s.copy(active = active))
    }

    override fun setGoal(goalHours: Int) {
        val s = state.value
        if (s.active != null) return
        write(s.copy(settings = FastingSettings(goalHours)))
    }

    override fun endFast(record: Fast) {
        val s = state.value
        write(s.copy(active = null, fasts = listOf(record) + s.fasts))
    }

    override fun cancelFast() {
        write(state.value.copy(active = null))
    }

    override fun updateFast(id: String, start: Long, end: Long) {
        val s = state.value
        write(s.copy(fasts = s.fasts.map { if (it.id == id) it.copy(start = start, end = end) else it }))
    }

    override fun deleteFast(id: String) {
        val s = state.value
        write(s.copy(fasts = s.fasts.filterNot { it.id == id }))
    }

    /** Called once the guest's data has been moved into an account. */
    fun clearAll() {
        try {
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't clear local fasts", e)
        }
        state.value = FastingState()
    }
}
