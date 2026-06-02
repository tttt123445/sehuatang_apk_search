package com.example.magnetcatcher.data

import com.example.magnetcatcher.network.HttpCookieStore

class CookieStore(private val settingsStore: SettingsStore) : HttpCookieStore {
    private val cookies = linkedMapOf<String, String>()

    init {
        load()
    }

    @Synchronized
    override fun header(): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    @Synchronized
    fun keys(): Set<String> = cookies.keys.toSet()

    @Synchronized
    override fun put(name: String, value: String) {
        if (name.isNotBlank()) {
            cookies[name] = value
            save()
        }
    }

    @Synchronized
    override fun collect(headers: Map<String, List<String>>?) {
        if (headers == null) return
        for ((key, values) in headers) {
            if (!key.equals("set-cookie", ignoreCase = true)) continue
            for (raw in values) {
                val first = raw.split(";", limit = 2).firstOrNull()?.trim() ?: continue
                val index = first.indexOf('=')
                if (index > 0) {
                    cookies[first.substring(0, index)] = first.substring(index + 1)
                }
            }
        }
        save()
    }

    @Synchronized
    private fun load() {
        cookies.clear()
        val raw = settingsStore.prefs.getString("cookies", "") ?: ""
        if (raw.isEmpty()) return
        for (part in raw.split(";")) {
            val index = part.indexOf('=')
            if (index > 0) {
                cookies[part.substring(0, index).trim()] = part.substring(index + 1).trim()
            }
        }
    }

    @Synchronized
    private fun save() {
        settingsStore.prefs.edit().putString("cookies", header()).apply()
    }
}
