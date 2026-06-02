package com.example.magnetcatcher.parser

import java.net.URLDecoder
import java.util.regex.Pattern

object HtmlText {
    private val tagPattern = Regex("(?is)<[^>]+>")
    private val brPattern = Regex("(?is)<br\\s*/?>")
    private val entityPattern = Regex("&(#x?[0-9A-Fa-f]+|amp|lt|gt|quot|apos|nbsp);")

    fun cleanText(raw: String?): String {
        val withBreaks = (raw ?: "").replace(brPattern, "\n")
        val withoutTags = withBreaks.replace(tagPattern, " ")
        return decodeEntities(withoutTags)
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun decodeEntities(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        return entityPattern.replace(raw) { match ->
            when (val value = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else -> decodeNumericEntity(value) ?: match.value
            }
        }
    }

    fun firstMatch(pattern: Pattern, text: String?): String {
        val matcher = pattern.matcher(text ?: "")
        return if (matcher.find()) (matcher.group(1) ?: "").trim() else ""
    }

    fun parseInt(raw: String?, fallback: Int = 0): Int {
        return raw?.trim()?.toIntOrNull() ?: fallback
    }

    fun normalizeMagnet(raw: String): String {
        val decoded = try {
            URLDecoder.decode(raw.replace("&amp;", "&"), "UTF-8")
        } catch (_: Exception) {
            raw.replace("&amp;", "&")
        }
        return decoded.replace(Regex("[,.;]+$"), "")
    }

    private fun decodeNumericEntity(value: String): String? {
        val code = try {
            if (value.startsWith("#x", ignoreCase = true)) {
                value.substring(2).toInt(16)
            } else if (value.startsWith("#")) {
                value.substring(1).toInt(10)
            } else {
                return null
            }
        } catch (_: Exception) {
            return null
        }
        return runCatching { String(Character.toChars(code)) }.getOrNull()
    }
}
