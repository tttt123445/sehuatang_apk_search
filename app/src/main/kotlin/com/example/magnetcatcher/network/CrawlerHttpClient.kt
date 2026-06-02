package com.example.magnetcatcher.network

import android.graphics.Bitmap
import com.example.magnetcatcher.AppConstants.BASE_URL
import com.example.magnetcatcher.AppConstants.CONNECT_TIMEOUT_MS
import com.example.magnetcatcher.AppConstants.IMAGE_PROXY_READ_TIMEOUT_MS
import com.example.magnetcatcher.AppConstants.IMAGE_READ_TIMEOUT_MS
import com.example.magnetcatcher.AppConstants.PROXY_CONNECT_TIMEOUT_MS
import com.example.magnetcatcher.AppConstants.PROXY_READ_TIMEOUT_MS
import com.example.magnetcatcher.AppConstants.READ_TIMEOUT_MS
import com.example.magnetcatcher.AppConstants.USER_AGENT
import com.example.magnetcatcher.data.ImageCache
import com.example.magnetcatcher.model.StatusTone
import com.example.magnetcatcher.model.StatusUpdate
import com.example.magnetcatcher.parser.HtmlText
import com.example.magnetcatcher.parser.UrlTools
import okhttp3.Call
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

class CrawlerHttpClient(
    private val cookieStore: HttpCookieStore,
    private val proxyController: HttpProxyController,
    private val imageCache: ImageCache,
) {
    private data class ClientKey(val proxyActive: Boolean, val image: Boolean, val port: Int)

    private val safeIdPattern = java.util.regex.Pattern.compile("var\\s+safeid\\s*=\\s*['\"]([^'\"]+)['\"]")
    private val clientLock = Any()
    private val clients = linkedMapOf<ClientKey, OkHttpClient>()
    private val unsafeTrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    private var sslSocketFactory: SSLSocketFactory? = null
    private var hostnameVerifier: HostnameVerifier? = null

    fun fetchWithRetry(
        rawUrl: String,
        referer: String?,
        attempts: Int,
        useProxy: Boolean,
        status: (StatusUpdate) -> Unit,
    ): String {
        var last: Exception? = null
        for (attempt in 1..attempts.coerceAtLeast(1)) {
            try {
                return fetch(rawUrl, referer, useProxy, status)
            } catch (error: Exception) {
                last = error
                if (attempt < attempts) {
                    val nextAttempt = attempt + 1
                    status(
                        StatusUpdate(
                            title = "网络重试中",
                            detail = proxyController.conciseError(error),
                            meta = "准备第 $nextAttempt/$attempts 次 · 当前网络：${proxyController.networkModeLabel(useProxy)}",
                            tone = StatusTone.Warning,
                            spinning = true,
                        )
                    )
                    Thread.sleep(1200L * attempt)
                }
            }
        }
        throw last ?: IllegalStateException("网络请求失败")
    }

    fun fetch(rawUrl: String, referer: String?, useProxy: Boolean, status: (StatusUpdate) -> Unit): String {
        val proxyActive = useProxy && proxyController.isReadyFast()
        if (useProxy && !proxyActive) {
            proxyController.notifyFallbackOnce("XTunnel 未就绪，本次请求临时走系统 VPN/直连。", status)
        }
        val request = Request.Builder()
            .url(rawUrl)
            .header("Accept", HTML_ACCEPT)
            .header("Referer", referer ?: BASE_URL)
            .build()
        return try {
            val body = executeTextRequest(request, proxyActive, HTML_BODY_LIMIT_BYTES)
            val safeId = HtmlText.firstMatch(safeIdPattern, body)
            if (safeId.isNotEmpty()) cookieStore.put("_safe", safeId)
            body
        } catch (error: Exception) {
            if (proxyActive) {
                proxyController.markProxyUnhealthy("XTunnel 请求失败，本轮重试切回系统 VPN/直连：${proxyController.conciseError(error)}", status)
            }
            throw error
        }
    }

    fun imageCallFactory(useProxy: Boolean): Call.Factory {
        return Call.Factory { request ->
            val proxyActive = useProxy && proxyController.isReadyFast()
            client(proxyActive = proxyActive, image = true).newCall(request)
        }
    }

    fun fetchBitmap(
        rawUrl: String,
        referer: String?,
        targetWidth: Int,
        targetHeight: Int,
        useProxy: Boolean,
        status: (StatusUpdate) -> Unit,
    ): Bitmap {
        var last: Exception? = null
        for (candidate in UrlTools.imageUrlCandidates(rawUrl)) {
            val key = ImageCache.buildKey(candidate, referer)
            imageCache.get(key, targetWidth, targetHeight)?.let { return it }
            if (useProxy) {
                try {
                    return fetchBitmapOnce(candidate, referer, true, targetWidth, targetHeight, key, status)
                } catch (error: Exception) {
                    last = error
                }
            }
            try {
                return fetchBitmapOnce(candidate, referer, false, targetWidth, targetHeight, key, status)
            } catch (error: Exception) {
                last = error
            }
        }
        throw last ?: RuntimeException("图片加载失败")
    }

    private fun fetchBitmapOnce(
        rawUrl: String,
        referer: String?,
        useProxy: Boolean,
        targetWidth: Int,
        targetHeight: Int,
        cacheKey: String,
        status: (StatusUpdate) -> Unit,
    ): Bitmap {
        val proxyActive = useProxy && proxyController.isReadyFast()
        if (useProxy && !proxyActive) {
            proxyController.notifyFallbackOnce("XTunnel 图片代理不可用，图片请求临时走系统 VPN/直连。", status)
        }
        val request = Request.Builder()
            .url(rawUrl)
            .header("Accept", IMAGE_ACCEPT)
            .header("Referer", referer?.takeIf { it.isNotEmpty() } ?: BASE_URL)
            .build()
        return try {
            client(proxyActive = proxyActive, image = true).newCall(request).execute().use { response ->
                if (response.code !in 200..299) throw RuntimeException("图片 HTTP ${response.code}")
                response.body.byteStream().use { input ->
                    imageCache.putStreamAndDecode(cacheKey, input, targetWidth, targetHeight)
                        ?: throw RuntimeException("图片解码失败")
                }
            }
        } catch (error: Exception) {
            if (proxyActive) {
                proxyController.markProxyUnhealthy("XTunnel 图片请求失败，后续图片临时走系统 VPN/直连：${proxyController.conciseError(error)}", status)
            }
            throw error
        }
    }

    private fun executeTextRequest(request: Request, proxyActive: Boolean, limit: Int): String {
        client(proxyActive = proxyActive, image = false).newCall(request).execute().use { response ->
            val body = readUtf8Limited(response, limit)
            if (response.code !in 200..299) throw RuntimeException("HTTP ${response.code}: $body")
            return body
        }
    }

    private fun readUtf8Limited(response: Response, limit: Int): String {
        val body = response.body
        val out = Buffer()
        val source = body.source()
        var total = 0L
        while (true) {
            val read = source.read(out, IO_BUFFER_SIZE.toLong())
            if (read == -1L) break
            total += read
            if (total > limit) throw RuntimeException("响应过大")
        }
        return out.readString(Charsets.UTF_8)
    }

    private fun client(proxyActive: Boolean, image: Boolean): OkHttpClient {
        val port = if (proxyActive) proxyPort() else 0
        val key = ClientKey(proxyActive, image, port)
        synchronized(clientLock) {
            clients[key]?.let { return it }
            return buildClient(key).also { clients[key] = it }
        }
    }

    private fun buildClient(key: ClientKey): OkHttpClient {
        val connectTimeout = if (key.proxyActive) PROXY_CONNECT_TIMEOUT_MS else CONNECT_TIMEOUT_MS
        val readTimeout = when {
            key.image && key.proxyActive -> IMAGE_PROXY_READ_TIMEOUT_MS
            key.image -> IMAGE_READ_TIMEOUT_MS
            key.proxyActive -> PROXY_READ_TIMEOUT_MS
            else -> READ_TIMEOUT_MS
        }
        return OkHttpClient.Builder()
            .connectTimeout(connectTimeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .apply {
                if (key.proxyActive) {
                    proxy(proxyController.requestProxy(true))
                    sslSocketFactory(unsafeSslSocketFactory(), unsafeTrustManager)
                    hostnameVerifier(unsafeHostnameVerifier())
                }
            }
            .addInterceptor { chain ->
                val next = withDefaultHeaders(chain.request(), key.image)
                chain.proceed(next).also { response ->
                    cookieStore.collect(response.headers.toHeaderMap())
                }
            }
            .build()
    }

    private fun withDefaultHeaders(request: Request, image: Boolean): Request {
        val builder = request.newBuilder()
        if (request.header("User-Agent").isNullOrEmpty()) {
            builder.header("User-Agent", USER_AGENT)
        }
        if (request.header("Accept").isNullOrEmpty()) {
            builder.header("Accept", if (image) IMAGE_ACCEPT else HTML_ACCEPT)
        }
        if (request.header("Referer").isNullOrEmpty()) {
            builder.header("Referer", BASE_URL)
        }
        cookieStore.header().takeIf { it.isNotEmpty() && request.header("Cookie").isNullOrEmpty() }?.let {
            builder.header("Cookie", it)
        }
        return builder.build()
    }

    private fun Headers.toHeaderMap(): Map<String, List<String>> = toMultimap()

    private fun proxyPort(): Int {
        val proxy = proxyController.requestProxy(true)
        val address = proxy.address()
        return address.toString().substringAfterLast(':').toIntOrNull() ?: 0
    }

    private fun unsafeSslSocketFactory(): SSLSocketFactory {
        sslSocketFactory?.let { return it }
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<X509TrustManager>(unsafeTrustManager), SecureRandom())
        }.socketFactory.also { sslSocketFactory = it }
    }

    private fun unsafeHostnameVerifier(): HostnameVerifier {
        return hostnameVerifier ?: HostnameVerifier { _, _ -> true }.also { hostnameVerifier = it }
    }

    companion object {
        private const val HTML_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val IMAGE_ACCEPT = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"
        private const val HTML_BODY_LIMIT_BYTES = 3 * 1024 * 1024
        private const val IO_BUFFER_SIZE = 8192
    }
}
