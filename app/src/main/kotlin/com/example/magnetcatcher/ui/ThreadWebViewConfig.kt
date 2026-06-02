package com.example.magnetcatcher.ui

import com.example.magnetcatcher.AppConstants.BASE_URL
import com.example.magnetcatcher.AppConstants.DIRECT_URL
import java.net.URI

data class ThreadWebProxyOverride(
    val useProxy: Boolean,
    val proxyPort: Int,
) {
    val enabled: Boolean = useProxy && proxyPort > 0
    val proxyRule: String? = if (enabled) "127.0.0.1:$proxyPort" else null
}

fun threadWebProxyOverride(webPage: WebPageUiState): ThreadWebProxyOverride {
    return ThreadWebProxyOverride(webPage.useProxy, webPage.proxyPort)
}

fun threadCookieTargets(url: String): List<String> {
    return buildList {
        addOrigin(url)
        addOrigin(BASE_URL)
        addOrigin(DIRECT_URL)
    }.distinct()
}

fun splitCookieHeader(cookieHeader: String): List<String> {
    if (cookieHeader.isBlank()) return emptyList()
    return cookieHeader
        .split(";")
        .map { it.trim() }
        .filter { it.contains("=") }
}

private fun MutableList<String>.addOrigin(rawUrl: String) {
    val origin = runCatching {
        val uri = URI(rawUrl)
        val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return
        val port = if (uri.port > 0) ":${uri.port}" else ""
        "$scheme://$host$port"
    }.getOrNull()
    if (origin != null) add(origin)
}
