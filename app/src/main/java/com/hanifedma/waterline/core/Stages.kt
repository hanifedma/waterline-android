package com.hanifedma.waterline.core

/**
 * The metabolic timeline of a water fast — the web app's `stages.js`.
 *
 * `hour` is the elapsed hour at which the stage begins. The wording itself
 * lives in i18n/Strings.kt under `stage.<hour>.title|text|cheer`, so the same
 * timeline reads in Korean without a second copy of the list. Descriptions are
 * a plain-language summary of well-established fasting physiology; they are
 * educational, not medical advice.
 */
data class Stage(val hour: Int, val icon: String) {
    val titleKey get() = "stage.$hour.title"
    val textKey get() = "stage.$hour.text"
    val cheerKey get() = "stage.$hour.cheer"
}

val STAGES = listOf(
    Stage(0, "🍽️"),
    Stage(4, "📉"),
    Stage(8, "🔓"),
    Stage(12, "🔥"),
    Stage(16, "💠"),
    Stage(18, "♻️"),
    Stage(24, "🧠"),
    Stage(36, "⚡"),
    Stage(48, "🛡️"),
    Stage(72, "🌱"),
)

/** The stage you are currently in, plus the one coming next. */
data class StagePosition(val index: Int, val current: Stage, val next: Stage?)

fun stageAt(hours: Double): StagePosition {
    var index = 0
    for (i in STAGES.indices) {
        if (hours >= STAGES[i].hour) index = i
    }
    return StagePosition(index, STAGES[index], STAGES.getOrNull(index + 1))
}

/** How many quotes there are; the rotating line picks one per hour. */
const val QUOTE_COUNT = 12

/** The same quote for a whole hour, and the same one the web app would show. */
fun quoteKeyOfTheHour(now: Long = System.currentTimeMillis()): String =
    "quote.${((now / HOUR_MS) % QUOTE_COUNT).toInt()}"

/**
 * The key for the message in the celebration sheet, tuned to what was actually
 * achieved. Mirrors `completionMessage()` in stages.js.
 */
fun completionKey(hours: Double, goalHours: Int): String = when {
    hours >= goalHours && hours >= 72 -> "done.msg.72"
    hours >= goalHours && hours >= 48 -> "done.msg.48"
    hours >= goalHours && hours >= 24 -> "done.msg.24"
    hours >= goalHours -> "done.msg.goal"
    else -> {
        val pct = Math.round((hours / goalHours) * 100).toInt()
        when {
            pct >= 80 -> "done.msg.80"
            pct >= 50 -> "done.msg.50"
            else -> "done.msg.low"
        }
    }
}

/** The percentage the short-of-goal messages interpolate. */
fun completionPercent(hours: Double, goalHours: Int): Int =
    Math.round((hours / goalHours) * 100).toInt()
