package com.hanifedma.waterline.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hanifedma.waterline.R
import com.hanifedma.waterline.core.Fast
import com.hanifedma.waterline.core.Fasting
import com.hanifedma.waterline.core.Format
import com.hanifedma.waterline.data.SyncStatus
import com.hanifedma.waterline.i18n.Lang
import com.hanifedma.waterline.i18n.Strings
import com.hanifedma.waterline.ui.components.InitialAvatar
import com.hanifedma.waterline.ui.screens.BodyScreen
import com.hanifedma.waterline.ui.screens.CompletionDialog
import com.hanifedma.waterline.ui.screens.ConfirmDialog
import com.hanifedma.waterline.ui.screens.Editor
import com.hanifedma.waterline.ui.screens.EditorDialog
import com.hanifedma.waterline.ui.screens.LogScreen
import com.hanifedma.waterline.ui.screens.SettingsScreen
import com.hanifedma.waterline.ui.screens.TimerScreen
import com.hanifedma.waterline.ui.theme.Waterline
import com.hanifedma.waterline.ui.theme.WaterlineTheme
import com.hanifedma.waterline.MainActivity

private enum class Tab(val labelKey: String, val icon: ImageVector) {
    TIMER("tab.timer", Icons.Rounded.Timer),
    LOG("tab.log", Icons.Outlined.CalendarMonth),
    BODY("tab.body", Icons.Rounded.Science),
    SETTINGS("tab.settings", Icons.Outlined.Settings),
}

/**
 * The adaptive shell.
 *
 * The window's width decides the layout, not "phone or tablet":
 *
 *   < 600dp   bottom navigation, one pane
 *   600–839   navigation rail, one pane
 *   ≥ 840dp   navigation rail, and the timer and the log side by side
 *
 * which is also the right answer for a landscape phone, a foldable and
 * split-screen, where the device is the wrong thing to ask about.
 */
@Composable
fun WaterlineRoot(
    vm: WaterlineViewModel,
    widthDp: Int,
    openRequest: String?,
    onOpenHandled: () -> Unit,
) {
    WaterlineTheme(darkTheme = vm.dark) {
        val c = Waterline.colors

        val state by vm.state.collectAsStateWithLifecycle()
        val account by vm.account.collectAsStateWithLifecycle()
        val status by vm.status.collectAsStateWithLifecycle()

        val lang = vm.lang
        val fmt = remember(lang) { Format(lang) }
        val stats = remember(state.fasts) { Fasting.computeStats(state.fasts) }

        val snackbar = remember { SnackbarHostState() }
        var tab by rememberSaveable { mutableStateOf(Tab.TIMER) }
        var editor by remember { mutableStateOf<Editor?>(null) }
        var confirmDiscard by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf<Fast?>(null) }

        val wide = widthDp >= 840
        val rail = widthDp >= 600

        // The lock screen is the whole feature; without this permission there
        // is nothing to show there. Asked once, on the first launch, rather
        // than at the moment the first fast starts — being interrupted by a
        // system dialog while tapping "Begin" is the wrong first impression.
        val askNotifications = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { vm.markNotificationsAsked() }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !vm.notificationsAsked()) {
                vm.markNotificationsAsked()
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // A tap on the notification's "End fast" lands here.
        LaunchedEffect(openRequest, state.active) {
            when (openRequest) {
                MainActivity.OPEN_END -> {
                    tab = Tab.TIMER
                    if (state.active != null) editor = Editor.End
                    onOpenHandled()
                }
                MainActivity.OPEN_BODY -> {
                    tab = Tab.BODY
                    onOpenHandled()
                }
                else -> Unit
            }
        }

        LaunchedEffect(Unit) {
            vm.messages.collect { snackbar.showSnackbar(it) }
        }

        BackHandler(enabled = tab != Tab.TIMER) { tab = Tab.TIMER }

        Scaffold(
            containerColor = c.bg,
            topBar = {
                TopBar(
                    lang = lang,
                    dark = vm.dark,
                    accountName = account?.name ?: account?.email,
                    status = status,
                    signedIn = account != null,
                    onToggleTheme = vm::toggleTheme,
                    onToggleLang = vm::toggleLang,
                    onAccount = { tab = Tab.SETTINGS },
                )
            },
            bottomBar = {
                if (!rail) {
                    BottomBar(lang, tab, wide) { tab = it }
                }
            },
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = c.surface3,
                        contentColor = c.text,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            },
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                if (rail) {
                    Rail(lang, tab, wide) { tab = it }
                }

                val timerPane: @Composable (Modifier) -> Unit = { m ->
                    TimerScreen(
                        lang = lang,
                        fmt = fmt,
                        active = state.active,
                        goalHours = state.settings.goalHours,
                        goalChoices = vm.goalChoices(),
                        stats = stats,
                        onSetGoal = vm::setGoal,
                        onBegin = vm::begin,
                        onEnd = { editor = Editor.End },
                        onEditStart = { editor = Editor.Start },
                        onDiscard = { confirmDiscard = true },
                        modifier = m,
                    )
                }
                val logPane: @Composable (Modifier) -> Unit = { m ->
                    LogScreen(
                        lang = lang,
                        fmt = fmt,
                        fasts = state.fasts,
                        onEdit = { editor = Editor.Entry(it) },
                        onDelete = { confirmDelete = it },
                        modifier = m,
                    )
                }

                when {
                    wide && (tab == Tab.TIMER || tab == Tab.LOG) -> {
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Pane(Modifier.weight(1f)) { timerPane(Modifier.fillMaxWidth()) }
                            Pane(Modifier.weight(1f)) { logPane(Modifier.fillMaxWidth()) }
                        }
                    }
                    tab == Tab.TIMER -> Pane { timerPane(Modifier.fillMaxWidth()) }
                    tab == Tab.LOG -> Pane { logPane(Modifier.fillMaxWidth()) }
                    tab == Tab.BODY -> Pane {
                        BodyScreen(
                            lang = lang,
                            active = state.active,
                            now = System.currentTimeMillis(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> Pane {
                        SettingsScreen(
                            vm = vm,
                            lang = lang,
                            fmt = fmt,
                            account = account,
                            status = status,
                            goalHours = state.settings.goalHours,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        editor?.let { current ->
            EditorDialog(
                editor = current,
                lang = lang,
                fmt = fmt,
                activeStart = state.active?.start,
                onDismiss = { editor = null },
                onSaveStart = vm::setStart,
                onSaveEnd = { vm.end(it) },
                onSaveEntry = { fast, start, end -> vm.updateFast(fast.id, start, end) },
            )
        }

        vm.completion?.let { record ->
            CompletionDialog(record, lang, fmt) { vm.completion = null }
        }

        if (confirmDiscard) {
            ConfirmDialog(
                title = Strings.t(lang, "toast.discardTitle"),
                message = Strings.t(lang, "toast.discardConfirm"),
                confirmLabel = Strings.t(lang, "btn.discard"),
                lang = lang,
                onConfirm = vm::discard,
                onDismiss = { confirmDiscard = false },
            )
        }

        confirmDelete?.let { fast ->
            ConfirmDialog(
                title = Strings.t(lang, "entry.deleteTitle"),
                message = Strings.t(lang, "entry.deleteConfirm"),
                confirmLabel = Strings.t(lang, "common.delete"),
                lang = lang,
                onConfirm = { vm.deleteFast(fast.id) },
                onDismiss = { confirmDelete = null },
            )
        }
    }
}

/** One scrolling column, never wider than the web app's 640px measure. */
@Composable
private fun Pane(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .widthIn(max = 640.dp)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 28.dp),
        ) { content() }
    }
}

@Composable
private fun TopBar(
    lang: Lang,
    dark: Boolean,
    accountName: String?,
    status: SyncStatus,
    signedIn: Boolean,
    onToggleTheme: () -> Unit,
    onToggleLang: () -> Unit,
    onAccount: () -> Unit,
) {
    val c = Waterline.colors
    Surface(color = c.bg) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_droplet),
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                Strings.t(lang, "app.name"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = c.text,
            )

            if (signedIn) {
                Spacer(Modifier.width(9.dp))
                StatusDot(status, lang)
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleLang)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    if (lang == Lang.KO) "한" else "EN",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.muted,
                )
            }
            IconButton(onClick = onToggleTheme, modifier = Modifier.size(38.dp)) {
                Icon(
                    if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = Strings.t(lang, "a11y.theme"),
                    tint = c.muted,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
            Box(Modifier.clip(CircleShape).clickable(onClick = onAccount)) {
                InitialAvatar(accountName, 30)
            }
        }
    }
}

/** Live, offline, or nothing at all — the web app's footer badge, condensed. */
@Composable
private fun StatusDot(status: SyncStatus, lang: Lang) {
    val c = Waterline.colors
    val (color, key) = when (status) {
        SyncStatus.LIVE -> c.accent to "status.live"
        SyncStatus.OFFLINE -> c.win to "status.offline"
        SyncStatus.LOCAL -> return
    }
    // A coloured dot says nothing out loud, so it carries the label itself.
    val label = Strings.t(lang, key)
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun BottomBar(lang: Lang, tab: Tab, wide: Boolean, onSelect: (Tab) -> Unit) {
    val c = Waterline.colors
    NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
        tabsFor(wide).forEach { entry ->
            NavigationBarItem(
                selected = tab == entry || (wide && entry == Tab.TIMER && tab == Tab.LOG),
                onClick = { onSelect(entry) },
                icon = { Icon(entry.icon, contentDescription = null, modifier = Modifier.size(21.dp)) },
                label = { Text(Strings.t(lang, entry.labelKey), fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.accentContrast,
                    selectedTextColor = c.accent,
                    indicatorColor = c.accent,
                    unselectedIconColor = c.faint,
                    unselectedTextColor = c.faint,
                ),
            )
        }
    }
}

@Composable
private fun Rail(lang: Lang, tab: Tab, wide: Boolean, onSelect: (Tab) -> Unit) {
    val c = Waterline.colors
    NavigationRail(containerColor = c.bg) {
        Spacer(Modifier.height(8.dp))
        tabsFor(wide).forEach { entry ->
            NavigationRailItem(
                selected = tab == entry || (wide && entry == Tab.TIMER && tab == Tab.LOG),
                onClick = { onSelect(entry) },
                icon = { Icon(entry.icon, contentDescription = null, modifier = Modifier.size(21.dp)) },
                label = { Text(Strings.t(lang, entry.labelKey), fontSize = 11.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = c.accentContrast,
                    selectedTextColor = c.accent,
                    indicatorColor = c.accent,
                    unselectedIconColor = c.faint,
                    unselectedTextColor = c.faint,
                ),
            )
        }
    }
}

/** At ≥840dp the timer and the log share one screen, so they share one tab. */
private fun tabsFor(wide: Boolean): List<Tab> =
    if (wide) listOf(Tab.TIMER, Tab.BODY, Tab.SETTINGS) else Tab.entries.toList()
