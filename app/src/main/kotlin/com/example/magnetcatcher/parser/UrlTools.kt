package com.example.magnetcatcher.parser

import com.example.magnetcatcher.AppConstants.BASE_URL
import com.example.magnetcatcher.AppConstants.DIRECT_URL
import java.util.regex.Pattern

object UrlTools {
    private val tidPattern = Pattern.compile("(?:thread-|tid=)(\\d+)")

    fun absoluteUrl(raw: String?): String {
        var url = (raw ?: "").trim().replace("&amp;", "&")
        if (url.startsWith("//")) return "https:$url"
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        if (!url.startsWith("/")) url = "/$url"
        return BASE_URL + url
    }

    fun externalThreadUrl(raw: String?): String {
        val url = absoluteUrl(raw)
        return if (url.startsWith(BASE_URL)) DIRECT_URL + url.substring(BASE_URL.length) else url
    }

    fun imageUrlCandidates(rawUrl: String?): List<String> {
        val urls = linkedSetOf<String>()
        val url = absoluteUrl(rawUrl)
        urls += url
        if (url.startsWith(BASE_URL)) {
            urls += DIRECT_URL + url.substring(BASE_URL.length)
        } else if (url.startsWith(DIRECT_URL)) {
            urls += BASE_URL + url.substring(DIRECT_URL.length)
        }
        return urls.toList()
    }

    fun extractThreadId(raw: String?): String {
        val matcher = tidPattern.matcher(raw ?: "")
        return if (matcher.find()) matcher.group(1) else ""
    }
}
