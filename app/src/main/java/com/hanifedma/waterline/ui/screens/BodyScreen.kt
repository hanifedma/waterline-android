package com.hanifedma.waterline.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.waterline.core.ActiveFast
import com.hanifedma.waterline.core.STAGES
import com.hanifedma.waterline.core.stageAt
import com.hanifedma.waterline.i18n.Lang
import com.hanifedma.waterline.i18n.Strings
import com.hanifedma.waterline.ui.components.Pill
import com.hanifedma.waterline.ui.components.SectionHead
import com.hanifedma.waterline.ui.components.WCard
import com.hanifedma.waterline.ui.theme.Waterline

/**
 * The metabolic timeline.
 *
 * On the web this is folded away behind a `<details>`; on a phone it earns its
 * own tab, because "what is happening to me right now" is the question people
 * open a fasting app to answer at hour nine.
 */
@Composable
fun BodyScreen(
    lang: Lang,
    active: ActiveFast?,
    now: Long,
    modifier: Modifier = Modifier,
) {
    val c = Waterline.colors
    val activeIndex = active?.let { stageAt(it.elapsed(now) / 3.6e6).index } ?: -1

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WCard {
            SectionHead(
                icon = "🔬",
                title = Strings.t(lang, "stages.heading"),
                hint = Strings.t(lang, "stages.subhead"),
            )
            Spacer(Modifier.height(6.dp))

            STAGES.forEachIndexed { index, stage ->
                val state = when {
                    index == activeIndex -> StageState.ACTIVE
                    index < activeIndex -> StageState.DONE
                    else -> StageState.TODO
                }
                StageRow(lang, index, state)
                if (index != STAGES.lastIndex) Spacer(Modifier.height(2.dp))
            }
        }

        WCard {
            Row(verticalAlignment = Alignment.Top) {
                Text("⚠️", fontSize = 15.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        Strings.t(lang, "settings.disclaimerTitle"),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Strings.t(lang, "settings.disclaimer"),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                }
            }
        }
    }
}

private enum class StageState { DONE, ACTIVE, TODO }

@Composable
private fun StageRow(lang: Lang, index: Int, state: StageState) {
    val c = Waterline.colors
    val stage = STAGES[index]
    val dim = state == StageState.TODO

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (state == StageState.ACTIVE) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .then(
                if (state == StageState.ACTIVE) {
                    Modifier.border(1.dp, c.accent, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            )
            .padding(vertical = 10.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.width(42.dp).padding(top = 2.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Text(
                "${stage.hour}h",
                style = MaterialTheme.typography.labelMedium,
                color = when (state) {
                    StageState.ACTIVE -> c.accent
                    StageState.DONE -> c.muted
                    StageState.TODO -> c.faint
                },
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stage.icon, fontSize = 14.sp)
                Spacer(Modifier.size(7.dp))
                Text(
                    Strings.t(lang, stage.titleKey),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (dim) c.muted else c.text,
                )
                if (state == StageState.ACTIVE) {
                    Spacer(Modifier.size(8.dp))
                    Pill(Strings.t(lang, "label.now"), c.accentContrast, c.accent)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                Strings.t(lang, stage.textKey),
                style = MaterialTheme.typography.bodySmall,
                color = if (dim) c.faint else c.muted,
                textAlign = TextAlign.Start,
            )
        }
    }
}
