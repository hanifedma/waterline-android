package com.hanifedma.waterline.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.waterline.core.Fast
import com.hanifedma.waterline.core.Format
import com.hanifedma.waterline.core.completionKey
import com.hanifedma.waterline.core.completionPercent
import com.hanifedma.waterline.i18n.Lang
import com.hanifedma.waterline.i18n.Strings
import com.hanifedma.waterline.ui.theme.Waterline
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * What the editor is being opened for. Three modes, exactly as on the web:
 * move a running fast's start, end it at a chosen time, or correct one that is
 * already logged.
 */
sealed interface Editor {
    data object Start : Editor
    data object End : Editor
    data class Entry(val fast: Fast) : Editor
}

/**
 * The one dialog behind all three.
 *
 * Validation is re-run against the clock on every save rather than pinned when
 * the dialog opened: "not in the future" is the one bound that moves while a
 * dialog sits open, and a max fixed at 10:30 would reject 10:32 two minutes
 * later even though 10:32 is now safely in the past.
 */
@Composable
fun EditorDialog(
    editor: Editor,
    lang: Lang,
    fmt: Format,
    activeStart: Long?,
    onDismiss: () -> Unit,
    onSaveStart: (Long) -> Unit,
    onSaveEnd: (Long) -> Unit,
    onSaveEntry: (Fast, Long, Long) -> Unit,
) {
    val c = Waterline.colors
    val now = remember { System.currentTimeMillis() }

    val showsStart = editor !is Editor.End
    val showsEnd = editor !is Editor.Start

    var start by remember {
        mutableLongStateOf(
            when (editor) {
                is Editor.Start -> activeStart ?: now
                is Editor.Entry -> editor.fast.start
                Editor.End -> activeStart ?: now
            },
        )
    }
    var end by remember {
        mutableLongStateOf(if (editor is Editor.Entry) editor.fast.end else now)
    }
    var error by remember { mutableStateOf<String?>(null) }

    val titleKey = when (editor) {
        Editor.Start -> "editor.activeTitle"
        Editor.End -> "editor.endTitle"
        is Editor.Entry -> "editor.fastTitle"
    }
    val hintKey = when (editor) {
        Editor.Start -> "editor.activeHint"
        Editor.End -> "editor.endHint"
        is Editor.Entry -> "editor.fastHint"
    }
    val saveKey = if (editor is Editor.End) "editor.saveEnd" else "editor.save"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        titleContentColor = c.text,
        textContentColor = c.muted,
        title = { Text(Strings.t(lang, titleKey), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    Strings.t(lang, hintKey),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.faint,
                )
                Spacer(Modifier.height(14.dp))

                if (showsStart) {
                    DateTimeField(
                        label = Strings.t(lang, "modal.startTime"),
                        value = start,
                        fmt = fmt,
                        lang = lang,
                        onChange = { start = it; error = null },
                    )
                }
                if (showsStart && showsEnd) Spacer(Modifier.height(10.dp))
                if (showsEnd) {
                    DateTimeField(
                        label = Strings.t(lang, "modal.endTime"),
                        value = end,
                        fmt = fmt,
                        lang = lang,
                        onChange = { end = it; error = null },
                    )
                }

                // The number the user is actually deciding about.
                val previewFrom = if (editor is Editor.End) (activeStart ?: start) else start
                if (showsEnd && end > previewFrom) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        Strings.t(lang, "editor.duration", "time" to fmt.duration(end - previewFrom)),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.accent,
                    )
                }

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.danger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = validate(editor, start, end, activeStart, System.currentTimeMillis())
                if (problem != null) {
                    error = Strings.t(lang, problem)
                    return@TextButton
                }
                when (editor) {
                    Editor.Start -> onSaveStart(start)
                    Editor.End -> onSaveEnd(end)
                    is Editor.Entry -> onSaveEntry(editor.fast, start, end)
                }
                onDismiss()
            }) {
                Text(Strings.t(lang, saveKey), color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.t(lang, "common.cancel"), color = c.muted)
            }
        },
    )
}

/** @return a Strings key when the form can't be saved, otherwise null. */
private fun validate(editor: Editor, start: Long, end: Long, activeStart: Long?, now: Long): String? =
    when (editor) {
        Editor.End -> when {
            activeStart == null -> "valid.noLongerRunning"
            end > now -> "valid.endFuture"
            end < activeStart -> "valid.endBeforeStart"
            else -> null
        }
        Editor.Start -> when {
            start > now -> "valid.startFuture"
            now - start > 30L * 86_400_000L -> "valid.tooOld"
            else -> null
        }
        is Editor.Entry -> when {
            start > now -> "valid.startFuture"
            end > now -> "valid.endFuture"
            end <= start -> "valid.endAfterStart"
            else -> null
        }
    }

/**
 * A date button and a time button.
 *
 * Two pickers rather than one free-text field: a fasting app is used at 6am
 * with one hand, and a keyboard entry that has to be parsed and validated is
 * the wrong shape for that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(
    label: String,
    value: Long,
    fmt: Format,
    lang: Lang,
    onChange: (Long) -> Unit,
) {
    val c = Waterline.colors
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val local = remember(value) { Instant.ofEpochMilli(value).atZone(zone).toLocalDateTime() }

    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.faint)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldButton(fmt.date(value), Modifier.weight(1.4f)) { pickingDate = true }
            FieldButton(fmt.time(value), Modifier.weight(1f)) { pickingTime = true }
            TextButton(onClick = { onChange(System.currentTimeMillis()) }) {
                Text(Strings.t(lang, "editor.setNow"), color = c.accent, fontSize = 13.sp)
            }
        }
    }

    if (pickingDate) {
        // DatePicker speaks UTC midnight, always — the date the user tapped has
        // to be lifted out in UTC and re-planted in the local zone, or a tap on
        // the 12th becomes the 11th anywhere east of Greenwich.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(combine(date, local.toLocalTime(), zone))
                    }
                    pickingDate = false
                }) { Text(Strings.t(lang, "common.ok"), color = c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) {
                    Text(Strings.t(lang, "common.cancel"), color = c.muted)
                }
            },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = c.surface),
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(
            initialHour = local.hour,
            initialMinute = local.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            containerColor = c.surface,
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { TimePicker(state = state) }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(
                        combine(
                            local.toLocalDate(),
                            LocalTime.of(state.hour, state.minute),
                            zone,
                        ),
                    )
                    pickingTime = false
                }) { Text(Strings.t(lang, "common.ok"), color = c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { pickingTime = false }) {
                    Text(Strings.t(lang, "common.cancel"), color = c.muted)
                }
            },
        )
    }
}

private fun combine(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
    LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

@Composable
private fun FieldButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Waterline.colors
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1)
    }
}

/** The end-of-fast sheet: what you did, and what it means. */
@Composable
fun CompletionDialog(record: Fast, lang: Lang, fmt: Format, onDismiss: () -> Unit) {
    val c = Waterline.colors
    val hours = record.duration / 3.6e6
    val hit = hours >= record.goalHours

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (hit) "🏆" else "💧", fontSize = 30.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    Strings.t(lang, if (hit) "done.goalReached" else "done.fastLogged"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.text,
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    fmt.duration(record.duration),
                    style = MaterialTheme.typography.displayLarge,
                    fontSize = 32.sp,
                    color = if (hit) c.win else c.accent,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    Strings.t(
                        lang,
                        completionKey(hours, record.goalHours),
                        "pct" to completionPercent(hours, record.goalHours),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onDismiss) {
                    Text(Strings.t(lang, "done.close"), color = c.accent)
                }
            }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    lang: Lang,
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Waterline.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = c.text) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = c.muted) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(confirmLabel, color = if (destructive) c.danger else c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.t(lang, "common.cancel"), color = c.muted)
            }
        },
    )
}
