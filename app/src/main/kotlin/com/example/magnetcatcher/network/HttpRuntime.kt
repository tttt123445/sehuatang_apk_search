package com.example.magnetcatcher.network

import com.example.magnetcatcher.model.StatusUpdate
import java.net.Proxy

interface HttpCookieStore {
    fun header(): String
    fun collect(headers: Map<String, List<String>>?)
    fun put(name: String, value: String)
}

interface HttpProxyController {
    fun requestProxy(useProxy: Boolean): Proxy
    fun networkModeLabel(useProxy: Boolean): String
    fun isReadyFast(): Boolean
    fun markProxyUnhealthy(message: String, status: (StatusUpdate) -> Unit)
    fun notifyFallbackOnce(message: String, status: (StatusUpdate) -> Unit)
    fun conciseError(error: Throwable?): String
}
