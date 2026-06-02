package com.example.magnetcatcher.parser

import com.example.magnetcatcher.parser.HtmlText.parseInt
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

object DateParser {
    val datePattern: Pattern = Pattern.compile("(20\\d{2}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)")
    val dateOnlyPattern: Pattern = Pattern.compile("(20\\d{2})-(\\d{1,2})-(\\d{1,2})")
    private val dateTimePartsPattern: Pattern = Pattern.compile("(20\\d{2})-(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})")
    private val timePartsPattern: Pattern = Pattern.compile("(\\d{1,2}):(\\d{2})")
    private val relativeDatePattern: Pattern = Pattern.compile("(今天|昨天|前天)\\s*\\d{1,2}:\\d{2}(?::\\d{2})?")
    private val relativeDayPattern: Pattern = Pattern.compile("^(今天|昨天|前天)$")
    private val agoDatePattern: Pattern = Pattern.compile("(刚刚|半小时前|(?:距今\\s*)?(\\d+)\\s*(秒钟?|秒|分钟|分|小时|天)前)")
    val postedAtPattern: Pattern = Pattern.compile("(?:发表于|发布于|发帖时间|发帖|时间)\\s*[:：]?\\s*((?:20\\d{2}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)|(?:(?:今天|昨天|前天)\\s*\\d{1,2}:\\d{2}(?::\\d{2})?))")

    fun normalizePublishedAt(raw: String?): String {
        val value = HtmlText.cleanText(raw)
            .replace('年', '-')
            .replace('月', '-')
            .replace("日", "")
            .replace('/', '-')
            .replace('.', '-')
            .replace(Regex("(\\d{1,2}:\\d{2}):\\d{2}\\b"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()

        val exact = dateTimePartsPattern.matcher(value)
        if (exact.find()) {
            return String.format(
                Locale.ROOT,
                "%04d-%02d-%02d %02d:%02d",
                parseInt(exact.group(1)),
                parseInt(exact.group(2)),
                parseInt(exact.group(3)),
                parseInt(exact.group(4)),
                parseInt(exact.group(5)),
            )
        }
        return value
    }

    fun parseSinceTimeMillis(raw: String?, nowMillis: Long = System.currentTimeMillis()): Long {
        val value = normalizedSinceTime(raw, nowMillis)
        val parsed = parsePublishedAtMillis(value, nowMillis)
        if (parsed == Long.MIN_VALUE) {
            throw IllegalArgumentException("起始时间格式不正确，请用 2026-06-01 00:00 或 今天 00:00")
        }
        return parsed
    }

    fun normalizedSinceTime(raw: String?, nowMillis: Long = System.currentTimeMillis()): String {
        val value = normalizePublishedAt(if (raw.isNullOrBlank()) defaultSinceTime(nowMillis) else raw)
        return if (relativeDayPattern.matcher(value).matches()) "$value 00:00" else value
    }

    fun parsePublishedAtMillis(raw: String?, nowMillis: Long = System.currentTimeMillis()): Long {
        val value = normalizePublishedAt(raw)
        if (value.isEmpty()) return Long.MIN_VALUE

        val relative = relativeDatePattern.matcher(value)
        if (relative.find()) {
            val time = timePartsPattern.matcher(relative.group())
            if (!time.find()) return Long.MIN_VALUE
            val calendar = localCalendar(nowMillis)
            startOfDay(calendar)
            when (relative.group(1)) {
                "昨天" -> calendar.add(Calendar.DAY_OF_MONTH, -1)
                "前天" -> calendar.add(Calendar.DAY_OF_MONTH, -2)
            }
            calendar.set(Calendar.HOUR_OF_DAY, parseInt(time.group(1)))
            calendar.set(Calendar.MINUTE, parseInt(time.group(2)))
            return calendar.timeInMillis
        }

        val ago = agoDatePattern.matcher(value)
        if (ago.find()) {
            val calendar = localCalendar(nowMillis)
            calendar.set(Calendar.MILLISECOND, 0)
            if (ago.group(1) == "半小时前") {
                calendar.add(Calendar.MINUTE, -30)
                return calendar.timeInMillis
            }
            val amount = parseInt(ago.group(2))
            val unit = ago.group(3)
            if (unit == null || ago.group(1) == "刚刚") return calendar.timeInMillis
            when {
                unit.startsWith("秒") -> calendar.add(Calendar.SECOND, -amount)
                unit == "分" || unit == "分钟" -> calendar.add(Calendar.MINUTE, -amount)
                unit == "小时" -> calendar.add(Calendar.HOUR_OF_DAY, -amount)
                unit == "天" -> calendar.add(Calendar.DAY_OF_MONTH, -amount)
            }
            return calendar.timeInMillis
        }

        val exact = dateTimePartsPattern.matcher(value)
        if (exact.find()) {
            return localCalendar(nowMillis).apply {
                clear()
                set(
                    parseInt(exact.group(1)),
                    parseInt(exact.group(2), 1) - 1,
                    parseInt(exact.group(3), 1),
                    parseInt(exact.group(4)),
                    parseInt(exact.group(5)),
                    0,
                )
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val dateOnly = dateOnlyPattern.matcher(value)
        if (dateOnly.find()) {
            return localCalendar(nowMillis).apply {
                clear()
                set(
                    parseInt(dateOnly.group(1)),
                    parseInt(dateOnly.group(2), 1) - 1,
                    parseInt(dateOnly.group(3), 1),
                    0,
                    0,
                    0,
                )
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        return Long.MIN_VALUE
    }

    fun todayString(nowMillis: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }.format(nowMillis)
    }

    fun defaultSinceTime(nowMillis: Long = System.currentTimeMillis()): String = "${todayString(nowMillis)} 00:00"

    fun relativeSinceTime(daysAgo: Int, nowMillis: Long = System.currentTimeMillis()): String {
        val calendar = localCalendar(nowMillis)
        startOfDay(calendar)
        calendar.add(Calendar.DAY_OF_MONTH, -daysAgo.coerceAtLeast(0))
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }.format(calendar.timeInMillis)
    }

    fun publishedDateBadge(publishedAt: String?, nowMillis: Long = System.currentTimeMillis()): String {
        val value = normalizePublishedAt(publishedAt)
        if (value.isEmpty()) return "无时间"
        if (value.startsWith("今天")) return "今天"
        if (value.startsWith("昨天")) return "昨天"
        if (value.startsWith("前天")) return "前天"
        val matcher = dateOnlyPattern.matcher(value)
        if (!matcher.find()) return if (value.length > 6) value.substring(0, 6) else value

        val year = parseInt(matcher.group(1))
        val month = parseInt(matcher.group(2))
        val day = parseInt(matcher.group(3))
        val today = localCalendar(nowMillis).also { startOfDay(it) }
        val posted = localCalendar(nowMillis).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }
        val diffDays = ((posted.timeInMillis - today.timeInMillis) / (24L * 60L * 60L * 1000L)).toInt()
        return when {
            diffDays == 0 -> "今天"
            diffDays == -1 -> "昨天"
            diffDays == -2 -> "前天"
            year == today.get(Calendar.YEAR) -> String.format(Locale.ROOT, "%02d-%02d", month, day)
            else -> String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
        }
    }

    fun extractRelativeOrExactDate(text: String): String {
        val relative = relativeDatePattern.matcher(text)
        if (relative.find()) return normalizePublishedAt(relative.group().trim())
        val ago = agoDatePattern.matcher(text)
        if (ago.find()) return normalizePublishedAt(ago.group().trim())
        val exact = HtmlText.firstMatch(datePattern, text)
        return if (exact.isNotEmpty()) normalizePublishedAt(exact) else ""
    }

    private fun localCalendar(nowMillis: Long): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"), Locale.CHINA).apply {
            timeInMillis = nowMillis
        }
    }

    private fun startOfDay(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }
}
