package com.hanifedma.waterline.ui.screens

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hanifedma.waterline.BuildConfig
import com.hanifedma.waterline.core.Fasting
import com.hanifedma.waterline.core.Format
import com.hanifedma.waterline.data.Account
import com.hanifedma.waterline.data.SyncStatus
import com.hanifedma.waterline.i18n.Lang
import com.hanifedma.waterline.i18n.Strings
import com.hanifedma.waterline.notify.Alarms
import com.hanifedma.waterline.ui.WaterlineViewModel
import com.hanifedma.waterline.ui.components.Divider
import com.hanifedma.waterline.ui.components.InitialAvatar
import com.hanifedma.waterline.ui.components.SectionHead
import com.hanifedma.waterline.ui.components.SettingRow
import com.hanifedma.waterline.ui.components.WCard
import com.hanifedma.waterline.ui.theme.Waterline

@Composable
fun SettingsScreen(
    vm: WaterlineViewModel,
    lang: Lang,
    fmt: Format,
    account: Account?,
    status: SyncStatus,
    goalHours: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = Waterline.colors

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // ---- Account -------------------------------------------------
        WCard {
            SectionHead("👤", Strings.t(lang, "settings.account"), null)
            Spacer(Modifier.height(6.dp))

            if (account != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    InitialAvatar(account.name ?: account.email, 38)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            account.name ?: account.email.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.text,
                        )
                        Text(
                            when (status) {
                                SyncStatus.OFFLINE -> Strings.t(lang, "status.offline")
                                else -> Strings.t(lang, "status.syncingAs", "name" to (account.email ?: ""))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status == SyncStatus.OFFLINE) c.muted else c.accent,
                        )
                    }
                    TextButton(onClick = { vm.signOut(context) }) {
                        Text(Strings.t(lang, "nav.signOut"), color = c.muted)
                    }
                }
            } else {
                Text(
                    if (vm.canSignIn) Strings.t(lang, "settings.signInHint") else Strings.t(lang, "setup.needConfig"),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.faint,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.signIn(context) },
                    enabled = vm.canSignIn,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.surface2,
                        contentColor = c.text,
                        disabledContainerColor = c.surface2,
                        disabledContentColor = c.faint,
                    ),
                ) {
                    Text("G", color = c.accent, fontSize = 15.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(Strings.t(lang, "nav.signIn"))
                }
            }
        }

        // ---- Appearance ----------------------------------------------
        WCard {
            SectionHead("🎨", Strings.t(lang, "settings.appearance"), null)
            SettingRow(
                title = Strings.t(lang, "settings.theme"),
                trailing = {
                    Segmented(
                        options = listOf(
                            Strings.t(lang, "settings.themeDark"),
                            Strings.t(lang, "settings.themeLight"),
                        ),
                        selected = if (vm.dark) 0 else 1,
                        onSelect = { if ((it == 0) != vm.dark) vm.toggleTheme() },
                    )
                },
            )
            Divider()
            SettingRow(
                title = Strings.t(lang, "settings.language"),
                trailing = {
                    Segmented(
                        options = listOf("English", "한국어"),
                        selected = if (lang == Lang.EN) 0 else 1,
                        onSelect = { vm.chooseLang(if (it == 0) Lang.EN else Lang.KO) },
                    )
                },
            )
            Divider()
            SettingRow(
                title = Strings.t(lang, "settings.defaultGoal"),
                hint = Strings.t(lang, "settings.defaultGoalHint"),
                trailing = {
                    NumberPicker(
                        value = goalHours,
                        options = Fasting.GOAL_CHOICES,
                        label = { Strings.t(lang, "goal.option", "h" to it) },
                        onSelect = vm::setGoal,
                    )
                },
            )
        }

        // ---- Notifications -------------------------------------------
        WCard {
            SectionHead("🔔", Strings.t(lang, "settings.notifications"), null)
            SettingRow(
                title = Strings.t(lang, "settings.milestones"),
                hint = Strings.t(lang, "settings.milestonesHint"),
                trailing = { WSwitch(vm.milestoneAlerts, vm::toggleMilestones) },
            )
            Divider()
            SettingRow(
                title = Strings.t(lang, "settings.reminders"),
                hint = Strings.t(lang, "settings.remindersHint"),
                trailing = { WSwitch(vm.remindersOn, vm::setReminders) },
            )
            if (vm.remindersOn) {
                Divider()
                SettingRow(
                    title = Strings.t(lang, "settings.reminderEvery"),
                    trailing = {
                        NumberPicker(
                            value = vm.reminderEveryHours,
                            options = listOf(1, 2, 3, 4, 6, 8, 12, 24),
                            label = { Strings.tCount(lang, "settings.hours", it) },
                            onSelect = vm::setReminderEvery,
                        )
                    },
                )
                Divider()
                SettingRow(
                    title = Strings.t(lang, "settings.reminderAfterDismiss"),
                    hint = Strings.t(lang, "settings.reminderAfterDismissHint"),
                    trailing = {
                        NumberPicker(
                            value = vm.reminderSnoozeHours,
                            options = listOf(1, 2, 3, 4, 6, 8, 12),
                            label = { Strings.tCount(lang, "settings.hours", it) },
                            onSelect = vm::setReminderSnooze,
                        )
                    },
                )
                Divider()
                SettingRow(
                    title = Strings.t(lang, "settings.quiet"),
                    hint = Strings.t(lang, "settings.quietHint"),
                    trailing = { WSwitch(vm.quietHours, vm::toggleQuietHours) },
                )
                if (vm.quietHours) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HourPicker(
                            label = Strings.t(lang, "settings.quietFrom"),
                            value = vm.quietFrom,
                            onSelect = { vm.setQuietWindow(it, vm.quietTo) },
                            fmt = fmt,
                            modifier = Modifier.weight(1f),
                        )
                        HourPicker(
                            label = Strings.t(lang, "settings.quietTo"),
                            value = vm.quietTo,
                            onSelect = { vm.setQuietWindow(vm.quietFrom, it) },
                            fmt = fmt,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ---- Keeping the timer alive ---------------------------------
        ReliabilityCard(lang)

        // ---- About ----------------------------------------------------
        WCard {
            SectionHead("💧", Strings.t(lang, "settings.about"), Strings.t(lang, "app.tagline"))
            SettingRow(
                title = Strings.t(lang, "settings.version"),
                trailing = {
                    Text(
                        BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.faint,
                    )
                },
            )
            Divider()
            SettingRow(
                title = Strings.t(lang, "settings.openWeb"),
                hint = Strings.t(lang, "settings.sourceWeb"),
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://hanifedma.com/waterline/")),
                        )
                    }
                },
            )
            Divider()
            Spacer(Modifier.height(10.dp))
            Text(
                Strings.t(lang, "settings.disclaimerTitle"),
                style = MaterialTheme.typography.titleMedium,
                color = c.text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                Strings.t(lang, "settings.disclaimer"),
                style = MaterialTheme.typography.bodySmall,
                color = c.faint,
            )
        }
    }
}

/**
 * The three switches Android hides in three different places, gathered up.
 *
 * Every one of these is something the *system* controls and the app can only
 * ask for, so the row shows the truth (re-read on every resume, because the
 * user may have changed it in Settings and come back) rather than what the app
 * would like to be true.
 */
@Composable
private fun ReliabilityCard(lang: Lang) {
    val context = LocalContext.current
    val c = Waterline.colors

    // Re-checked on resume: these are granted in system Settings, in another
    // task, and the user comes back expecting the row to have changed.
    var epoch by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) epoch++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val checks = remember(epoch) { readPermissions(context) }
    val allGood = checks.all { it.granted }

    WCard {
        SectionHead(
            "🛡️",
            Strings.t(lang, "settings.reliability"),
            if (allGood) Strings.t(lang, "settings.allGood") else Strings.t(lang, "settings.reliabilityHint"),
        )
        checks.forEachIndexed { index, check ->
            if (index > 0) Divider()
            SettingRow(
                title = Strings.t(lang, check.titleKey),
                hint = Strings.t(lang, check.hintKey),
                trailing = {
                    if (check.granted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = c.accent,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                Strings.t(lang, "settings.ok"),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.accent,
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = c.win,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            TextButton(onClick = {
                                runCatching { context.startActivity(check.intent(context)) }
                                epoch++
                            }) {
                                Text(Strings.t(lang, "settings.grant"), color = c.accent)
                            }
                        }
                    }
                },
            )
        }
    }
}

private data class PermissionCheck(
    val titleKey: String,
    val hintKey: String,
    val granted: Boolean,
    val intent: (Context) -> Intent,
)

private fun readPermissions(context: Context): List<PermissionCheck> {
    val nm = context.getSystemService(NotificationManager::class.java)
    val power = context.getSystemService(PowerManager::class.java)

    return listOf(
        PermissionCheck(
            titleKey = "settings.permNotifications",
            hintKey = "settings.permNotificationsHint",
            granted = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            intent = {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, it.packageName)
            },
        ),
        PermissionCheck(
            titleKey = "settings.permExact",
            hintKey = "settings.permExactHint",
            granted = Alarms.canBeExact(context),
            intent = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${it.packageName}"))
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${it.packageName}"))
                }
            },
        ),
        PermissionCheck(
            titleKey = "settings.permBattery",
            hintKey = "settings.permBatteryHint",
            granted = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
            intent = {
                // The targeted dialog rather than the settings list: one tap,
                // and it names the app so the choice is unambiguous.
                @Suppress("BatteryLife")
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${it.packageName}"),
                )
            },
        ),
        PermissionCheck(
            titleKey = "settings.permDnd",
            hintKey = "settings.permDndHint",
            granted = nm?.isNotificationPolicyAccessGranted == true,
            intent = { Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
        ),
    )
}

@Composable
private fun WSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Waterline.colors
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.accentContrast,
            checkedTrackColor = c.accent,
            uncheckedThumbColor = c.muted,
            uncheckedTrackColor = c.surface2,
            uncheckedBorderColor = c.border,
        ),
    )
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Waterline.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) c.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) c.text else c.faint,
                )
            }
        }
    }
}

@Composable
private fun <T> NumberPicker(
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val c = Waterline.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface2)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(label(value), style = MaterialTheme.typography.labelMedium, color = c.text)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun HourPicker(
    label: String,
    value: Int,
    onSelect: (Int) -> Unit,
    fmt: Format,
    modifier: Modifier = Modifier,
) {
    val c = Waterline.colors
    var open by remember { mutableStateOf(false) }
    // Rendered through the same formatter as everything else, so a Korean UI
    // reads "오후 11:00" and an English one "23:00".
    val hourLabel = { h: Int -> fmt.time(midnightPlusHours(h)) }

    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface2)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.faint, fontSize = 9.sp)
            Text(hourLabel(value), style = MaterialTheme.typography.bodyMedium, color = c.text)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (0..23).forEach { h ->
                DropdownMenuItem(
                    text = { Text(hourLabel(h)) },
                    onClick = {
                        open = false
                        onSelect(h)
                    },
                )
            }
        }
    }
}

/** An epoch time at today's local midnight plus `h` hours, for formatting only. */
private fun midnightPlusHours(h: Int): Long =
    java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
        .plusHours(h.toLong())
        .toInstant()
        .toEpochMilli()
