package com.example.magnetcatcher.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DateParserTest {
    private val nowMillis = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }.parse("2026-06-02 12:00")!!.time

    @Test
    fun normalizesChineseDateTime() {
        assertEquals("2026-06-01 09:30", DateParser.normalizePublishedAt("2026年6月1日 09:30:55"))
    }

    @Test
    fun parsesRelativeSinceTimeAgainstShanghaiDate() {
        val expected = DateParser.parsePublishedAtMillis("2026-06-01 00:00", nowMillis)
        assertEquals(expected, DateParser.parseSinceTimeMillis("昨天", nowMillis))
    }

    @Test
    fun buildsPresetStartOfDay() {
        assertEquals("2026-05-31 00:00", DateParser.relativeSinceTime(2, nowMillis))
    }
}
