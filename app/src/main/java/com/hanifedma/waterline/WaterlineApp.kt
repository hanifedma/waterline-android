package com.hanifedma.waterline

import android.app.Application
import android.content.Context
import com.hanifedma.waterline.data.FastingRepository
import com.hanifedma.waterline.data.Prefs
import com.hanifedma.waterline.notify.Channels
import com.hanifedma.waterline.notify.FastingCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The process, and the two things that outlive any screen: the repository, and
 * the promise that the notification shade matches it.
 *
 * There is no dependency-injection framework here on purpose. One repository,
 * created once, reachable as [repo] — a graph this small pays for a DI library
 * in indirection and gets nothing back.
 */
class WaterlineApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: FastingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        Channels.ensure(this)

        repository = FastingRepository(this, scope)
        repository.start()

        // Whenever a fast starts, ends, or has its start time corrected, the
        // world is reconciled: the notification, the foreground service, the
        // milestone alarm and the idle nudge. Keyed on the running fast rather
        // than the whole state, because Firestore emits a snapshot for every
        // metadata change too and rebuilding all of that per keystroke of sync
        // traffic would be pointless work.
        scope.launch {
            // Wait for the store that is actually in charge to answer. Acting
            // on the seeded empty state would tear down a running fast's
            // notification for the few hundred milliseconds before Firestore's
            // cache replies.
            repository.ready.first { it }
            repository.state
                .map { it.active }
                .distinctUntilChanged()
                .collect { FastingCoordinator.sync(this@WaterlineApp) }
        }

        // Settings can change what the reminders should be doing.
        scope.launch {
            Prefs(this@WaterlineApp).changes().drop(1).collect {
                FastingCoordinator.sync(this@WaterlineApp)
            }
        }
    }

    companion object {
        fun repo(context: Context): FastingRepository =
            (context.applicationContext as WaterlineApp).repository
    }
}
