package com.example.magnetcatcher.data

import android.content.Context
import android.content.SharedPreferences
import com.example.magnetcatcher.AppConstants.DEFAULT_PROXY_PORT
import com.example.magnetcatcher.AppConstants.FORUM_OPTIONS_PREF
import com.example.magnetcatcher.model.ForumOption
import com.example.magnetcatcher.parser.DateParser
import com.example.magnetcatcher.parser.ForumParser
import com.example.magnetcatcher.parser.HtmlText
import org.json.JSONArray
import org.json.JSONObject

class SettingsStore(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("magnet-catcher", Context.MODE_PRIVATE)

    var useXTunnel: Boolean
        get() = prefs.getBoolean("use_xtunnel", false)
        set(value) {
            prefs.edit().putBoolean("use_xtunnel", value).apply()
        }

    var proxyPort: Int
        get() {
            val saved = prefs.getInt("proxy_port", DEFAULT_PROXY_PORT)
            return if (saved == 40633) DEFAULT_PROXY_PORT else saved
        }
        set(value) {
            prefs.edit().putInt("proxy_port", if (value > 0) value else DEFAULT_PROXY_PORT).apply()
        }

    var pages: Int
        get() = prefs.getInt("pages", 1)
        set(value) {
            prefs.edit().putInt("pages", value.coerceAtLeast(1)).apply()
        }

    var sinceTime: String
        get() = prefs.getString("since_time", DateParser.defaultSinceTime()) ?: DateParser.defaultSinceTime()
        set(value) {
            prefs.edit().putString("since_time", value.trim()).apply()
        }

    var selectedFidsRaw: String
        get() = prefs.getString("fids", "") ?: ""
        set(value) {
            prefs.edit().putString("fids", value).apply()
        }

    fun parseFids(raw: String): List<Int> {
        return raw.split(",").mapNotNull { part ->
            HtmlText.parseInt(part.trim()).takeIf { it > 0 }
        }
    }

    fun selectedFids(availableOptions: List<ForumOption>): Set<Int> {
        val selected = parseFids(selectedFidsRaw).toMutableSet()
        if (availableOptions.isNotEmpty()) {
            val available = availableOptions.mapTo(mutableSetOf()) { it.fid }
            selected.retainAll(available)
            if (selected.isEmpty()) {
                selected += availableOptions.map { it.fid }
            }
        }
        return selected
    }

    fun saveSelectedFids(fids: Set<Int>) {
        selectedFidsRaw = fids.joinToString(",")
    }

    fun loadForumOptions(): List<ForumOption> {
        val raw = prefs.getString(FORUM_OPTIONS_PREF, "") ?: ""
        if (raw.isBlank()) return ForumParser.defaultForumOptions
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val objectValue = array.getJSONObject(i)
                    val fid = objectValue.optInt("fid")
                    val name = objectValue.optString("name")
                    val parentFid = objectValue.optInt("parent_fid")
                    if (fid > 0 && name.isNotBlank()) add(ForumOption(fid, name, parentFid))
                }
            }.ifEmpty { ForumParser.defaultForumOptions }
        }.getOrDefault(ForumParser.defaultForumOptions)
    }

    fun saveForumOptions(options: List<ForumOption>) {
        val array = JSONArray()
        for (option in options) {
            array.put(JSONObject().apply {
                put("fid", option.fid)
                put("name", option.name)
                put("parent_fid", option.parentFid)
            })
        }
        prefs.edit().putString(FORUM_OPTIONS_PREF, array.toString()).apply()
    }
}
