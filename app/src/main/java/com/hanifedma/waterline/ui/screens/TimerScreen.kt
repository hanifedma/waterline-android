package com.hanifedma.waterline.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.waterline.core.ActiveFast
import com.hanifedma.waterline.core.Fasting
import com.hanifedma.waterline.core.Format
import com.hanifedma.waterline.core.Stats
import com.hanifedma.waterline.core.quoteKeyOfTheHour
import com.hanifedma.waterline.core.stageAt
import com.hanifedma.waterline.i18n.Lang
import com.hanifedma.waterline.i18n.Strings
import com.hanifedma.waterline.ui.components.ProgressRing
import com.hanifedma.waterline.ui.components.StatTile
import com.hanifedma.waterline.ui.components.WCard
import com.hanifedma.waterline.ui.theme.Waterline
import kotlinx.coroutines.delay

/**
 * A clock that only reticks while it is on screen.
 *
 * The seconds in the ring cost a recomposition each; when the app is in the
 * background Compose stops the effect, and the notification's chronometer —
 * drawn by SystemUI — carries the time instead.
 */
@Composable
private fun rememberNow(running: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            // Land just after the next whole second so the display never
            // skips one by drifting a millisecond behind.
            delay(1_000L - (System.currentTimeMillis() % 1_000L) + 15L)
        }
        now = System.currentTimeMillis()
    }
    return now
}

/** How long a peek uncovers the real numbers for. Matches the web app. */
private const val PEEK_MS = 8_000L

@Composable
fun TimerScreen(
    lang: Lang,
    fmt: Format,
    active: ActiveFast?,
    goalHours: Int,
    goalChoices: List<Int>,
    stats: Stats,
    hideTimes: Boolean,
    onSetGoal: (Int) -> Unit,
    onBegin: () -> Unit,
    onEnd: () -> Unit,
    onEditStart: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Waterline.colors
    val now = rememberNow(active != null)

    /*
     * Focus mode, and the escape hatch from it.
     *
     * `peekUntil` is keyed on the fast itself, so beginning one — or correcting
     * its start — starts covered again rather than inheriting a peek from the
     * fast before it. Expiry needs no timer of its own: `now` reticks every
     * second while a fast is running, so the comparison simply stops being true.
     */
    var peekUntil by remember(active?.start) { mutableLongStateOf(0L) }
    val peeking = peekUntil > now
    val covered = active != null && hideTimes && !peeking

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {

        WCard(padding = 22) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(6.dp))

                BoxWithConstraints {
                    val ringSize = if (maxWidth * 0.72f < 248.dp) maxWidth * 0.72f else 248.dp
                    ProgressRing(
                        progress = active?.progress(now) ?: 0f,
                        complete = active?.reachedGoal(now) == true,
                        modifier = Modifier.size(ringSize).aspectRatio(1f),
                    ) {
                        RingFace(lang, fmt, active, goalHours, now, covered)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    coachLine(lang, fmt, active, now, covered),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                Spacer(Modifier.height(18.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The picker reads "16 hours" out loud, so in focus mode it
                    // is put away rather than greyed, and the button takes the
                    // whole row. It is locked during a fast either way.
                    if (!covered) {
                        GoalPicker(
                            lang = lang,
                            value = active?.goalHours ?: goalHours,
                            choices = goalChoices,
                            // A goal already in progress is fixed; the picker
                            // shows it, greyed, rather than hiding what the
                            // fast is for.
                            enabled = active == null,
                            onPick = onSetGoal,
                        )
                    }
                    Button(
                        onClick = if (active == null) onBegin else onEnd,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.accent,
                            contentColor = c.accentContrast,
                        ),
                    ) {
                        Text(
                            Strings.t(lang, if (active == null) "btn.begin" else "btn.end"),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 15.sp,
                        )
                    }
                }

                if (active != null) {
                    Spacer(Modifier.height(14.dp))
                    // "Started … · 16h goal at …" is three of the four numbers
                    // focus mode exists to hide, so the whole line goes. Edit
                    // start and Discard stay reachable.
                    if (!covered) {
                        Text(
                            Strings.t(
                                lang, "controls.startedAt",
                                "start" to fmt.dateTime(active.start),
                                "goal" to active.goalHours,
                                "goalAt" to goalAtLabel(fmt, active),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.faint,
                            textAlign = TextAlign.Center,
                        )
                    }
                    // Three buttons in Korean overflow a 320dp phone; wrapping
                    // is cheaper than shortening any of the labels.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Offered whenever focus mode is armed — including
                        // while peeking, where it becomes the way back under.
                        if (hideTimes) {
                            TextButton(onClick = {
                                peekUntil = if (peeking) 0L else System.currentTimeMillis() + PEEK_MS
                            }) {
                                Text(
                                    Strings.t(lang, if (covered) "btn.peek" else "btn.hideAgain"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.accent,
                                )
                            }
                        }
                        TextButton(onClick = onEditStart) {
                            Text(
                                Strings.t(lang, "btn.editStart"),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.muted,
                            )
                        }
                        TextButton(onClick = onDiscard) {
                            Text(
                                Strings.t(lang, "btn.discard"),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.danger,
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        StatsRow(lang, fmt, stats)
    }
}

@Composable
private fun RingFace(
    lang: Lang,
    fmt: Format,
    active: ActiveFast?,
    goalHours: Int,
    now: Long,
    covered: Boolean,
) {
    val c = Waterline.colors
    val elapsed = active?.elapsed(now) ?: 0L
    val stage = active?.let { stageAt(elapsed / 3.6e6).current }
    val meta = ringMeta(lang, fmt, active, goalHours, now, covered)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 34.dp),
    ) {
        Text(
            text = if (stage != null) Strings.t(lang, stage.titleKey) else Strings.t(lang, "ring.ready"),
            style = MaterialTheme.typography.labelSmall,
            color = c.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            if (covered && active != null) {
                Strings.t(lang, "ring.percent", "pct" to active.percent(now))
            } else {
                fmt.clock(elapsed)
            },
            style = MaterialTheme.typography.displayLarge,
            fontSize = 36.sp,
            color = c.text,
            maxLines = 1,
        )
        // Covered, the only line left worth printing is the one with no number
        // in it — and there isn't one until the goal is met.
        if (meta != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = if (covered) c.win else c.faint,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun ringMeta(
    lang: Lang,
    fmt: Format,
    active: ActiveFast?,
    goalHours: Int,
    now: Long,
    covered: Boolean,
): String? {
    if (active == null) return Strings.t(lang, "ring.readyMeta", "goal" to goalHours)
    val reached = active.reachedGoal(now)
    if (covered) return if (reached) Strings.t(lang, "ring.goalMet") else null
    val elapsed = active.elapsed(now)
    return if (reached) {
        Strings.t(
            lang, "ring.past",
            "time" to fmt.duration(elapsed - active.goalMs), "goal" to active.goalHours,
        )
    } else {
        Strings.t(lang, "ring.left", "time" to fmt.countdown(active.goalMs - elapsed))
    }
}

private fun coachLine(lang: Lang, fmt: Format, active: ActiveFast?, now: Long, covered: Boolean): String {
    if (active == null) return Strings.t(lang, quoteKeyOfTheHour(now))
    val elapsed = active.elapsed(now)
    val next = stageAt(elapsed / 3.6e6).next ?: return Strings.t(lang, "coach.pastAll")
    val stage = Strings.t(lang, next.titleKey)
    return if (covered) {
        Strings.t(lang, "coach.next", "stage" to stage)
    } else {
        Strings.t(
            lang, "coach.until",
            "time" to fmt.countdown(next.hour * 3_600_000L - elapsed),
            "stage" to stage,
        )
    }
}

/** "14:30" on the same day, "Tue, 12 Aug 14:30" when the goal is tomorrow. */
private fun goalAtLabel(fmt: Format, active: ActiveFast): String {
    val goalAt = active.goalAt()
    val sameDay = Fasting.dayIndex(goalAt) == Fasting.dayIndex(active.start)
    return if (sameDay) fmt.time(goalAt) else "${fmt.date(goalAt)} ${fmt.time(goalAt)}"
}

@Composable
private fun GoalPicker(
    lang: Lang,
    value: Int,
    choices: List<Int>,
    enabled: Boolean,
    onPick: (Int) -> Unit,
) {
    val c = Waterline.colors
    var open by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = c.surface2,
            border = BorderStroke(1.dp, c.border),
            modifier = Modifier
                .height(50.dp)
                .clickable(enabled = enabled) { open = true },
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        Strings.t(lang, "goal.label"),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.faint,
                        fontSize = 9.sp,
                    )
                    Text(
                        Strings.t(lang, "goal.option", "h" to value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) c.text else c.faint,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = if (enabled) c.muted else c.surface3,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { h ->
                DropdownMenuItem(
                    text = { Text(Strings.t(lang, "goal.option", "h" to h)) },
                    onClick = {
                        open = false
                        onPick(h)
                    },
                )
            }
        }
    }
}

@Composable
fun StatsRow(lang: Lang, fmt: Format, stats: Stats) {
    BoxWithConstraints {
        val tiles = listOf(
            Triple("🔥", stats.streak.toString(), Strings.t(lang, "stats.streak")),
            Triple("🏆", if (stats.longest > 0) fmt.duration(stats.longest) else fmt.hours(0), Strings.t(lang, "stats.longest")),
            Triple("✅", stats.total.toString(), Strings.t(lang, "stats.total")),
            Triple("⏳", fmt.hours(Math.round(stats.hours)), Strings.t(lang, "stats.hours")),
        )
        // Four across needs about 90dp each before the labels start wrapping
        // into three lines; below that they pair up instead.
        val perRow = if (maxWidth < 330.dp) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (icon, value, label) ->
                        StatTile(icon, value, label, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
