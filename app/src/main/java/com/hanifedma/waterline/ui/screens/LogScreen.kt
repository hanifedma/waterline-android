package com.hanifedma.waterline.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.waterline.core.Fast
import com.hanifedma.waterline.core.Fasting
import com.hanifedma.waterline.core.Format
import com.hanifedma.waterline.i18n.Lang
import com.hanifedma.waterline.i18n.Strings
import com.hanifedma.waterline.ui.components.EmptyNote
import com.hanifedma.waterline.ui.components.Pill
import com.hanifedma.waterline.ui.components.SectionHead
import com.hanifedma.waterline.ui.components.WCard
import com.hanifedma.waterline.ui.theme.Waterline
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun LogScreen(
    lang: Lang,
    fmt: Format,
    fasts: List<Fast>,
    onEdit: (Fast) -> Unit,
    onDelete: (Fast) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CalendarCard(lang, fmt, fasts)
        HistoryCard(lang, fmt, fasts, onEdit, onDelete)
    }
}

@Composable
private fun CalendarCard(lang: Lang, fmt: Format, fasts: List<Fast>) {
    val c = Waterline.colors
    var cursor by remember { mutableStateOf(YearMonth.now()) }
    val hits = remember(fasts) { Fasting.fastedDays(fasts) }
    val today = remember(fasts) { LocalDate.now() }
    val stats = remember(fasts) { Fasting.computeStats(fasts) }

    val first = cursor.atDay(1)
    val daysInMonth = cursor.lengthOfMonth()
    // 7 = Sunday in java.time; the grid runs Sun→Sat like the web app's.
    val leading = first.dayOfWeek.value % 7
    val monthHits = (1..daysInMonth).count { hits.contains(cursor.atDay(it).toEpochDay()) }

    WCard {
        SectionHead(
            icon = "📅",
            title = Strings.t(lang, "cal.heading"),
            hint = if (fasts.isEmpty()) {
                Strings.t(lang, "cal.emptyHint")
            } else {
                Strings.tCount(
                    lang, "cal.summary", monthHits,
                    "streak" to stats.streak,
                    "days" to monthHits,
                    "month" to fmt.monthName(first),
                )
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { cursor = cursor.minusMonths(1) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.ChevronLeft,
                        contentDescription = Strings.t(lang, "a11y.prevMonth"),
                        tint = c.muted,
                    )
                }
                Text(
                    fmt.monthTitle(first),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.text,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                val canGoForward = cursor < YearMonth.now()
                IconButton(
                    onClick = { cursor = cursor.plusMonths(1) },
                    enabled = canGoForward,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = Strings.t(lang, "a11y.nextMonth"),
                        // Nothing to see in the future.
                        tint = if (canGoForward) c.muted else c.surface3,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth()) {
            fmt.narrowWeekdays().forEach { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.faint,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        val cells = leading + daysInMonth
        val rows = (cells + 6) / 7
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNumber = row * 7 + col - leading + 1
                    // A fixed row height rather than a square cell: in the
                    // two-pane layout the grid is twice as wide, and square
                    // cells would stretch the month to the height of a page.
                    Box(Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.Center) {
                        if (dayNumber in 1..daysInMonth) {
                            val date = cursor.atDay(dayNumber)
                            DayCell(
                                day = dayNumber,
                                hit = hits.contains(date.toEpochDay()),
                                isToday = date == today,
                                future = date > today,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun DayCell(day: Int, hit: Boolean, isToday: Boolean, future: Boolean) {
    val c = Waterline.colors
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (hit) c.accent else c.surface2)
            .then(if (isToday) Modifier.border(1.5.dp, c.accent, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (hit || isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                hit -> c.accentContrast
                future -> c.surface3
                isToday -> c.accent
                else -> c.muted
            },
        )
    }
}

@Composable
private fun HistoryCard(
    lang: Lang,
    fmt: Format,
    fasts: List<Fast>,
    onEdit: (Fast) -> Unit,
    onDelete: (Fast) -> Unit,
) {
    WCard {
        SectionHead(
            icon = "📋",
            title = Strings.t(lang, "history.heading"),
            hint = if (fasts.isEmpty()) {
                Strings.t(lang, "history.emptyHint")
            } else {
                Strings.tCount(lang, "history.count", fasts.size)
            },
        )
        Spacer(Modifier.height(8.dp))

        if (fasts.isEmpty()) {
            EmptyNote("💧", Strings.t(lang, "history.empty"))
        } else {
            // Sorted here rather than trusted from the store: Firestore's
            // snapshot order and the local file's differ, and a list that
            // reorders itself on sign-in looks like data loss.
            fasts.sortedByDescending { it.start }.forEach { fast ->
                HistoryRow(lang, fmt, fast, onEdit, onDelete)
            }
        }
    }
}

@Composable
private fun HistoryRow(
    lang: Lang,
    fmt: Format,
    fast: Fast,
    onEdit: (Fast) -> Unit,
    onDelete: (Fast) -> Unit,
) {
    val c = Waterline.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onEdit(fast) }
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (fast.hitGoal) "🏆" else "💧", fontSize = 16.sp)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                fmt.duration(fast.duration),
                style = MaterialTheme.typography.titleMedium,
                color = if (fast.hitGoal) c.win else c.text,
            )
            Text(
                "${fmt.date(fast.start)}, ${fmt.time(fast.start)} → ${fmt.time(fast.end)}",
                style = MaterialTheme.typography.bodySmall,
                color = c.faint,
            )
        }
        Pill(Strings.t(lang, "history.goalTag", "goal" to fast.goalHours))
        IconButton(onClick = { onEdit(fast) }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = Strings.t(lang, "entry.edit"),
                tint = c.muted,
                modifier = Modifier.size(17.dp),
            )
        }
        IconButton(onClick = { onDelete(fast) }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = Strings.t(lang, "entry.delete"),
                tint = c.muted,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
