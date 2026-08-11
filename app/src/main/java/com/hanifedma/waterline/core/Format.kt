package com.hanifedma.waterline.core

import com.hanifedma.waterline.i18n.Lang
import com.hanifedma.waterline.i18n.Strings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.ceil

/**
 * Every number the app prints, in the chosen language's locale.
 *
 * Built per language rather than per call — the calendar asks for weekday
 * names on every recomposition, and DateTimeFormatter is not cheap to build.
 */
class Format(val lang: Lang) {

    private val locale = lang.locale
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (lang == Lang.KO) "a h:mm" else "HH:mm", locale,
    )
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (lang == Lang.KO) "M월 d일 (E)" else "EEE, d MMM", locale,
    )
    private val longDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (lang == Lang.KO) "yyyy년 M월 d일 EEEE" else "EEEE, d MMMM yyyy", locale,
    )
    private val monthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (lang == Lang.KO) "yyyy년 M월" else "MMMM yyyy", locale,
    )

    private fun at(ms: Long): LocalDateTime =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()

    /** "00:00:00" — the big number in the ring. Hours are never wrapped at 24. */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0L)) / 1000
        return "%02d:%02d:%02d".format(total / 3600, (total / 60) % 60, total % 60)
    }

    /**
     * "16h 04m" — the human-readable form used in history and stats.
     *
     * Floored, never rounded: rounding would print "16h 00m" for a fast of
     * 15h 59m 40s and then not award the 16h goal beside it.
     */
    fun duration(ms: Long): String {
        val mins = (ms.coerceAtLeast(0L)) / 60_000
        return hm(mins / 60, mins % 60)
    }

    /** Time still to come, rounded up so it only hits "0m" when it really is up. */
    fun countdown(ms: Long): String {
        val mins = ceil(ms.coerceAtLeast(0L) / 60_000.0).toLong()
        return hm(mins / 60, mins % 60)
    }

    private fun hm(h: Long, m: Long): String = when {
        h > 0 && lang == Lang.KO -> "${h}시간 %02d분".format(m)
        h > 0 -> "${h}h %02dm".format(m)
        lang == Lang.KO -> "${m}분"
        else -> "${m}m"
    }

    /** Whole hours, for the stats tiles: "37h" / "37시간". */
    fun hours(h: Long): String = if (lang == Lang.KO) "${h}시간" else "${h}h"

    fun time(ms: Long): String = timeFmt.format(at(ms))

    fun date(ms: Long): String = dateFmt.format(at(ms))

    fun longDate(date: LocalDate): String = longDateFmt.format(date)

    fun dateTime(ms: Long): String = "${date(ms)} ${time(ms)}"

    fun monthTitle(date: LocalDate): String = monthFmt.format(date)

    /** Just the month, for the calendar's summary line. */
    fun monthName(date: LocalDate): String =
        date.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)

    /** Single-letter weekday names, index 0 = Sunday. */
    fun narrowWeekdays(): List<String> = (0..6).map { i ->
        // 2024-09-01 was a Sunday — a stable anchor for generating names.
        LocalDate.of(2024, 9, 1).plusDays(i.toLong())
            .dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
    }

    /** "6h 30m" for a duration that has to read as a sentence fragment. */
    fun spokenDuration(ms: Long): String = duration(ms)

    /** The stage's own name, translated. */
    fun stageTitle(stage: Stage): String = Strings.t(lang, stage.titleKey)
}
