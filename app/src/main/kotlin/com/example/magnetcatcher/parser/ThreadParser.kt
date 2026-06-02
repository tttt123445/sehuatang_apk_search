package com.example.magnetcatcher.parser

import com.example.magnetcatcher.model.CrawlerItem
import com.example.magnetcatcher.model.ThreadRef
import java.util.Locale
import java.util.regex.Pattern

object ThreadParser {
    private val magnetPattern: Pattern = Pattern.compile("magnet:\\?xt=urn:[A-Za-z0-9]+:[A-Za-z0-9]+[^\\s\"'<>]*")
    private val titlePattern: Pattern = Pattern.compile("(?is)<(?:span|h1)[^>]+id=[\"']thread_subject[\"'][^>]*>(.*?)</(?:span|h1)>")
    private val htmlTitlePattern: Pattern = Pattern.compile("(?is)<title[^>]*>(.*?)</title>")
    private val postPattern: Pattern = Pattern.compile("(?is)<td[^>]+id=[\"']postmessage_[^\"']+[\"'][^>]*>(.*?)</td>")
    private val imgTagPattern: Pattern = Pattern.compile("(?is)<img\\b[^>]*>")
    private val imgAttrPattern: Pattern = Pattern.compile("(?is)\\b(file|zoomfile|data-original|data-src|src)\\s*=\\s*([\"'])(.*?)\\2")

    fun parseThread(html: String, ref: ThreadRef): CrawlerItem {
        var title = extractThreadTitle(html)
        if (isBadThreadTitle(title)) title = ref.title
        val publishedAt = extractPublishedAt(html).ifEmpty { ref.publishedAt }
        val magnets = extractMagnets(html)
        return CrawlerItem(
            fid = ref.fid,
            threadId = ref.threadId,
            title = title,
            url = ref.url,
            publishedAt = publishedAt,
            description = extractDescription(html),
            magnets = magnets,
            images = extractImages(html),
            enabledMagnets = magnets.toSet(),
            selected = true,
        )
    }

    fun extractThreadTitle(html: String): String {
        val subject = cleanThreadTitle(HtmlText.firstMatch(titlePattern, html))
        if (!isBadThreadTitle(subject)) return subject
        val pageTitle = stripPageTitleSuffix(cleanThreadTitle(HtmlText.firstMatch(htmlTitlePattern, html)))
        return if (isBadThreadTitle(pageTitle)) "" else pageTitle
    }

    fun cleanThreadTitle(raw: String?): String {
        val withoutImages = (raw ?: "").replace(Regex("(?is)<img\\b[^>]*>"), " ")
        return HtmlText.cleanText(withoutImages)
            .replace('\uFFFC', ' ')
            .replace(Regex("(?i)\\bobj\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun isBadThreadTitle(title: String?): Boolean {
        val value = HtmlText.cleanText(title)
            .replace('\uFFFC', ' ')
            .replace(Regex("(?i)\\bobj\\b"), " ")
            .replace(Regex("\\s+"), "")
            .trim()
        return value.isEmpty() || value.matches(Regex("\\d+"))
    }

    fun extractPublishedAt(html: String): String {
        val text = HtmlText.cleanText(html)
        val labeled = HtmlText.firstMatch(DateParser.postedAtPattern, text)
        if (labeled.isNotEmpty()) return DateParser.normalizePublishedAt(labeled)
        return DateParser.extractRelativeOrExactDate(text)
    }

    fun extractMagnets(html: String): List<String> {
        val magnets = linkedSetOf<String>()
        val decodedText = HtmlText.cleanText(html)
        var matcher = magnetPattern.matcher(decodedText)
        while (matcher.find()) magnets += HtmlText.normalizeMagnet(matcher.group())
        matcher = magnetPattern.matcher(html)
        while (matcher.find()) magnets += HtmlText.normalizeMagnet(HtmlText.decodeEntities(matcher.group()))
        return magnets.toList()
    }

    fun extractDescription(html: String): String {
        var body = HtmlText.firstMatch(postPattern, html)
        if (body.isEmpty()) body = html
        body = body.replace(Regex("(?is)<script.*?</script>"), " ")
        body = body.replace(Regex("(?is)<style.*?</style>"), " ")
        body = body.replace(Regex("(?is)<div[^>]+class=[\"'][^\"']*blockcode[^\"']*[\"'][^>]*>.*?</div>"), " ")
        body = body.replace(Regex("(?is)<img[^>]*>"), " ")
        val text = HtmlText.cleanText(body)
        return if (text.length > 600) text.substring(0, 600) + "..." else text
    }

    fun extractImages(html: String): List<String> {
        val images = linkedSetOf<String>()
        val body = HtmlText.firstMatch(postPattern, html).ifEmpty { html }
        val matcher = imgTagPattern.matcher(body)
        while (matcher.find()) {
            val src = bestImageSource(matcher.group())
            if (src.isNotEmpty()) {
                images += UrlTools.absoluteUrl(src)
                if (images.size >= 6) break
            }
        }
        return images.toList()
    }

    fun bestImageSource(tag: String): String {
        val attrs = linkedMapOf<String, String>()
        val matcher = imgAttrPattern.matcher(tag)
        while (matcher.find()) {
            attrs[(matcher.group(1) ?: "").lowercase(Locale.ROOT)] = HtmlText.decodeEntities(matcher.group(3)).trim()
        }
        val priority = arrayOf("file", "zoomfile", "data-original", "data-src", "src")
        for (key in priority) {
            val value = attrs[key]
            if (!value.isNullOrEmpty() && !isSkippedImage(value)) return value
        }
        return ""
    }

    private fun stripPageTitleSuffix(title: String): String {
        var value = title.trim()
        val markers = mutableListOf(
            " - 98堂",
            " - 原色花堂",
            " - 手机版",
            " - Powered by Discuz!",
            " - Powered by",
        )
        markers += ForumParser.defaultForumOptions.map { " - ${it.name}" }
        for (marker in markers) {
            val index = value.indexOf(marker)
            if (index > 0) value = value.substring(0, index)
        }
        return value.trim()
    }

    private fun isSkippedImage(src: String): Boolean {
        val lower = src.lowercase(Locale.ROOT)
        return lower.startsWith("data:")
            || lower.contains("avatar")
            || lower.contains("smiley")
            || lower.contains("static/image")
    }
}
