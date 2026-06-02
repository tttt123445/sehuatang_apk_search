package com.example.magnetcatcher.data

import android.content.Context
import com.example.magnetcatcher.model.CrawlerItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LibraryStore(context: Context) {
    private val file = File(context.filesDir, "library.json")

    fun save(items: List<CrawlerItem>) {
        runCatching {
            val array = JSONArray()
            for (item in items) {
                array.put(JSONObject().apply {
                    put("fid", item.fid)
                    put("thread_id", item.threadId)
                    put("title", item.title)
                    put("url", item.url)
                    put("published_at", item.publishedAt)
                    put("description", item.description)
                    put("magnets", JSONArray(item.magnets))
                    put("images", JSONArray(item.images))
                })
            }
            file.writeText(array.toString(2), Charsets.UTF_8)
        }
    }

    fun load(): List<CrawlerItem> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            buildList {
                for (i in 0 until array.length()) {
                    val objectValue = array.getJSONObject(i)
                    val magnets = objectValue.optJSONArray("magnets").toStringList()
                    val images = objectValue.optJSONArray("images").toStringList()
                    add(
                        CrawlerItem(
                            fid = objectValue.optInt("fid"),
                            threadId = objectValue.optString("thread_id"),
                            title = objectValue.optString("title"),
                            url = objectValue.optString("url"),
                            publishedAt = objectValue.optString("published_at"),
                            description = objectValue.optString("description"),
                            magnets = magnets,
                            images = images,
                            enabledMagnets = magnets.toSet(),
                            selected = true,
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) add(optString(i))
        }
    }
}
