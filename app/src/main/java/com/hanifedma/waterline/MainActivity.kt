package com.hanifedma.waterline

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hanifedma.waterline.ui.WaterlineRoot
import com.hanifedma.waterline.ui.WaterlineViewModel

class MainActivity : ComponentActivity() {

    private val vm: WaterlineViewModel by viewModels()

    /** Set by a notification tap; consumed once the UI has acted on it. */
    private var pendingScreen by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Holds the system splash until the store has loaded, so the ring is
        // never drawn empty for a frame and then jumped to the real time.
        val splash = installSplashScreen()
        // With a deadline: if the store is somehow slow to answer, a splash
        // that never goes away is far worse than a ring that fills in late.
        val deadline = android.os.SystemClock.uptimeMillis() + 1_200L
        splash.setKeepOnScreenCondition {
            !vm.ready.value && android.os.SystemClock.uptimeMillis() < deadline
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingScreen = intent?.getStringExtra(EXTRA_OPEN)

        setContent {
            // The *window's* width, not the device's — the right input for
            // landscape phones, foldables and split-screen, where "is this a
            // tablet" is the wrong question. containerSize is measured from
            // the actual window, so a freeform window adapts as it is dragged.
            val windowInfo = LocalWindowInfo.current
            val density = LocalDensity.current
            val widthDp = with(density) { windowInfo.containerSize.width.toDp().value.toInt() }

            WaterlineRoot(
                vm = vm,
                widthDp = widthDp,
                openRequest = pendingScreen,
                onOpenHandled = { pendingScreen = null },
            )
        }
    }

    /** singleTask: a second tap on the notification lands here, not in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingScreen = intent.getStringExtra(EXTRA_OPEN)
    }

    companion object {
        const val EXTRA_OPEN = "waterline.open"

        /** Open straight onto the end-of-fast sheet. */
        const val OPEN_END = "end"

        /** Open the metabolic timeline. */
        const val OPEN_BODY = "body"
    }
}
