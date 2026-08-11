package com.hanifedma.waterline.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.waterline.core.Fasting
import com.hanifedma.waterline.ui.theme.Waterline

/** The one card shape the whole app uses: flat, bordered, 18dp. */
@Composable
fun WCard(
    modifier: Modifier = Modifier,
    padding: Int = 18,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = Waterline.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
    ) {
        Column(Modifier.padding(padding.dp), content = content)
    }
}

/** A section heading with the web app's emoji-and-hint pairing. */
@Composable
fun SectionHead(icon: String, title: String, hint: String?, trailing: @Composable (() -> Unit)? = null) {
    val c = Waterline.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 15.sp)
                Spacer(Modifier.size(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
            }
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = c.faint)
            }
        }
        trailing?.invoke()
    }
}

/**
 * The ring.
 *
 * Drawn rather than composed from a progress indicator so that the fill can
 * carry the same easing curve as the web app's, and so the "past your goal"
 * amber can cross-fade in place instead of the bar being swapped out.
 */
@Composable
fun ProgressRing(
    progress: Float,
    complete: Boolean,
    modifier: Modifier = Modifier,
    strokeWidth: Int = 10,
    content: @Composable () -> Unit,
) {
    val c = Waterline.colors
    // The eased value is animated, not the raw one: a fast restored from
    // another device would otherwise sweep from zero on every cold start.
    val eased by animateFloatAsState(
        targetValue = Fasting.easeProgress(progress),
        animationSpec = tween(durationMillis = 700),
        label = "ring",
    )
    val ringColor by animateColorAsState(
        targetValue = if (complete) c.win else c.accent,
        animationSpec = tween(durationMillis = 300),
        label = "ringColor",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

            drawArc(
                color = c.surface3,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (eased > 0f) {
                drawArc(
                    color = ringColor,
                    // -90° puts zero at twelve o'clock, as the rotated SVG does.
                    startAngle = -90f,
                    sweepAngle = 360f * eased.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** One of the four numbers under the ring. */
@Composable
fun StatTile(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    val c = Waterline.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(icon, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            /*
             * Auto-sized, not ellipsised.
             *
             * Four tiles across a phone leaves about 80dp each, and the widest
             * value is a duration in Korean — "36시간 18분" against "36h 18m".
             * Truncating it would hide the very number the tile exists to
             * show, so the type shrinks to fit instead and only the longest
             * values ever pay for it.
             */
            BasicText(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(color = c.text),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 18.sp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = c.faint,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** A small rounded label — the goal tag on a history row. */
@Composable
fun Pill(text: String, color: Color = Waterline.colors.muted, background: Color = Waterline.colors.surface2) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** A tappable settings row: title, hint, and whatever sits on the right. */
@Composable
fun SettingRow(
    title: String,
    hint: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Waterline.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = c.faint)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Waterline.colors.border),
    )
}

/** An empty state: one mark, one sentence, centred. */
@Composable
fun EmptyNote(mark: String, text: String) {
    val c = Waterline.colors
    Column(
        Modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(mark, fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = c.faint,
            textAlign = TextAlign.Center,
        )
    }
}

/** A circle with the account's initial — no image loader, no network fetch. */
@Composable
fun InitialAvatar(name: String?, size: Int = 30) {
    val c = Waterline.colors
    val letter = name?.trim()?.firstOrNull()?.uppercase() ?: "·"
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(c.accentSoft)
            .border(1.dp, c.border, RoundedCornerShape(percent = 50)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = c.accent, fontWeight = FontWeight.SemiBold, fontSize = (size / 2.2).sp)
    }
}
