package com.example.magnetcatcher.parser

import com.example.magnetcatcher.AppConstants.MAX_THREADS_PER_PAGE
import com.example.magnetcatcher.model.ForumOption
import com.example.magnetcatcher.model.ThreadRef
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

object ForumParser {
    private val threadLinkPattern: Pattern = Pattern.compile("(?is)<a[^>]+href=[\"']([^\"']*(?:thread-\\d+|mod=viewthread[^\"']*tid=\\d+)[^\"']*)[\"'][^>]*>(.*?)</a>")
    private val threadRowPattern: Pattern = Pattern.compile("(?is)<tbody[^>]+id=[\"'](?:normalthread|stickthread)_\\d+[\"'][^>]*>.*?</tbody>|<tr[^>]*>.*?</tr>")

    val defaultForumOptions: List<ForumOption> = listOf(
        ForumOption(2, "国产原创"),
        ForumOption(36, "亚洲无码原创"),
        ForumOption(37, "亚洲有码原创"),
        ForumOption(103, "高清中文字幕"),
        ForumOption(107, "经典三级"),
        ForumOption(160, "VR视频区"),
        ForumOption(104, "素人有码系列"),
        ForumOption(38, "欧美无码"),
        ForumOption(151, "4K原版"),
        ForumOption(152, "韩国主播"),
    )

    fun parseList(html: String, fid: Int): List<ThreadRef> {
        val refs = linkedMapOf<String, ThreadRef>()
        val rowMatcher = threadRowPattern.matcher(html)
        while (rowMatcher.find()) {
            addThreadRefFromListSegment(rowMatcher.group(), fid, refs)
            if (refs.size >= MAX_THREADS_PER_PAGE) break
        }
        if (refs.isNotEmpty()) return refs.values.toList()

        val matcher = threadLinkPattern.matcher(html)
        while (matcher.find()) {
            addThreadRef(
                fid = fid,
                rawHref = matcher.group(1) ?: "",
                rawTitle = matcher.group(2) ?: "",
                publishedAt = extractListPublishedAt(contextAround(html, matcher.start(), matcher.end())),
                sticky = false,
                refs = refs,
            )
            if (refs.size >= MAX_THREADS_PER_PAGE) break
        }
        return refs.values.toList()
    }

    fun parseBTForumOptions(body: String): List<ForumOption> {
        val root = JSONObject(extractJSONText(body))
        val variables = root.optJSONObjectCompat("Variables")
            ?: root.optJSONObjectCompat("variables")
            ?: root

        val forumMap = linkedMapOf<Int, ForumOption>()
        collectForumMap(variables.opt("forumlist"), forumMap)

        val options = mutableListOf<ForumOption>()
        val seen = mutableSetOf<Int>()
        collectBTCategoryForums(variables.opt("catlist"), forumMap, options, seen)
        return options
    }

    private fun addThreadRefFromListSegment(segment: String, fid: Int, refs: LinkedHashMap<String, ThreadRef>) {
        val matcher = threadLinkPattern.matcher(segment)
        val before = refs.size
        while (matcher.find()) {
            addThreadRef(
                fid = fid,
                rawHref = matcher.group(1) ?: "",
                rawTitle = matcher.group(2) ?: "",
                publishedAt = extractListPublishedAt(segment),
                sticky = segment.lowercase(Locale.ROOT).contains("stickthread_"),
                refs = refs,
            )
            if (refs.size > before) return
        }
    }

    private fun addThreadRef(
        fid: Int,
        rawHref: String,
        rawTitle: String,
        publishedAt: String,
        sticky: Boolean,
        refs: LinkedHashMap<String, ThreadRef>,
    ) {
        val href = HtmlText.decodeEntities(rawHref)
        val title = ThreadParser.cleanThreadTitle(rawTitle)
        val threadId = UrlTools.extractThreadId(href)
        if (threadId.isEmpty() || ThreadParser.isBadThreadTitle(title)) return
        if (!refs.containsKey(threadId)) {
            refs[threadId] = ThreadRef(fid, threadId, title, UrlTools.absoluteUrl(href), publishedAt, sticky)
        }
    }

    private fun extractListPublishedAt(html: String): String {
        return DateParser.extractRelativeOrExactDate(HtmlText.cleanText(html))
    }

    private fun contextAround(text: String, start: Int, end: Int): String {
        val from = (start - 400).coerceAtLeast(0)
        val to = (end + 1400).coerceAtMost(text.length)
        return text.substring(from, to)
    }

    private fun extractJSONText(body: String): String {
        val trimmed = body.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalArgumentException("接口返回不是 JSON")
        return trimmed.substring(start, end + 1)
    }

    private fun JSONObject.optJSONObjectCompat(key: String): JSONObject? {
        val value = opt(key)
        return value as? JSONObject
    }

    private fun collectForumMap(node: Any?, out: MutableMap<Int, ForumOption>) {
        when (node) {
            null, JSONObject.NULL -> return
            is JSONArray -> for (i in 0 until node.length()) collectForumMap(node.opt(i), out)
            is JSONObject -> {
                val option = forumOptionFromJSON(node)
                if (option != null && !isGroupForum(node) && !out.containsKey(option.fid)) {
                    out[option.fid] = option
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val child = node.opt(keys.next())
                    if (child is JSONObject || child is JSONArray) collectForumMap(child, out)
                }
            }
        }
    }

    private fun collectBTCategoryForums(
        node: Any?,
        forumMap: Map<Int, ForumOption>,
        out: MutableList<ForumOption>,
        seen: MutableSet<Int>,
    ) {
        when (node) {
            null, JSONObject.NULL -> return
            is JSONArray -> for (i in 0 until node.length()) collectBTCategoryForums(node.opt(i), forumMap, out, seen)
            is JSONObject -> {
                val name = optJSONText(node, "name", "title", "catname", "cat_name", "forumname", "forum_name")
                if (isBTSectionName(name)) {
                    addForumRefs(categoryForumRefs(node), forumMap, out, seen)
                    val categoryFid = optJSONInt(node, "fid", "catid", "cat_id", "forumid", "forum_id")
                    if (categoryFid > 0) {
                        forumMap.values.filter { it.parentFid == categoryFid }.forEach { addForumOption(it, out, seen) }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val child = node.opt(keys.next())
                    if (child is JSONObject || child is JSONArray) collectBTCategoryForums(child, forumMap, out, seen)
                }
            }
        }
    }

    private fun categoryForumRefs(objectValue: JSONObject): Any? {
        val keys = arrayOf("forums", "forumids", "forum_ids", "fids", "forum_fids", "children", "child")
        return keys.firstNotNullOfOrNull { key ->
            objectValue.opt(key).takeIf { it != null && it != JSONObject.NULL }
        }
    }

    private fun addForumRefs(refs: Any?, forumMap: Map<Int, ForumOption>, out: MutableList<ForumOption>, seen: MutableSet<Int>) {
        when (refs) {
            null, JSONObject.NULL -> return
            is JSONArray -> for (i in 0 until refs.length()) addForumRefs(refs.opt(i), forumMap, out, seen)
            is JSONObject -> {
                val option = forumOptionFromJSON(refs)
                if (option != null && !isGroupForum(refs)) addForumOption(option, out, seen)
                val keys = refs.keys()
                while (keys.hasNext()) addForumRefs(refs.opt(keys.next()), forumMap, out, seen)
            }
            else -> {
                refs.toString().replace("[", " ").replace("]", " ").replace("\"", " ")
                    .split(Regex("[,\\s]+"))
                    .forEach { part ->
                        val fid = HtmlText.parseInt(part)
                        if (fid > 0) addForumOption(forumMap[fid] ?: ForumOption(fid, "FID $fid"), out, seen)
                    }
            }
        }
    }

    private fun addForumOption(option: ForumOption?, out: MutableList<ForumOption>, seen: MutableSet<Int>) {
        if (option == null || option.fid <= 0 || option.name.isEmpty() || seen.contains(option.fid)) return
        seen += option.fid
        out += option
    }

    private fun forumOptionFromJSON(objectValue: JSONObject): ForumOption? {
        val fid = optJSONInt(objectValue, "fid", "forumid", "forum_id")
        val name = optJSONText(objectValue, "name", "title", "forumname", "forum_name")
        val parentFid = optJSONInt(objectValue, "fup", "parentid", "parent_id")
        return if (fid <= 0 || name.isEmpty()) null else ForumOption(fid, name, parentFid)
    }

    private fun optJSONInt(objectValue: JSONObject, vararg keys: String): Int {
        for (key in keys) {
            val value = objectValue.opt(key)
            if (value == null || value == JSONObject.NULL) continue
            if (value is Number) return value.toInt()
            val parsed = HtmlText.parseInt(value.toString())
            if (parsed > 0) return parsed
        }
        return 0
    }

    private fun optJSONText(objectValue: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = objectValue.opt(key)
            if (value == null || value == JSONObject.NULL) continue
            val text = HtmlText.cleanText(value.toString())
            if (text.isNotEmpty()) return text
        }
        return ""
    }

    private fun isGroupForum(objectValue: JSONObject): Boolean {
        return optJSONText(objectValue, "type").lowercase(Locale.ROOT) == "group"
    }

    private fun isBTSectionName(name: String): Boolean {
        val normalized = name
            .replace('Ｂ', 'B')
            .replace('ｂ', 'b')
            .replace('Ｔ', 'T')
            .replace('ｔ', 't')
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), "")
        return normalized.contains("bt")
    }
}
