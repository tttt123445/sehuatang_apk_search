package com.example.magnetcatcher.network

import com.example.magnetcatcher.AppConstants.BASE_URL
import com.example.magnetcatcher.AppConstants.USER_AGENT
import com.example.magnetcatcher.data.ImageCache
import com.example.magnetcatcher.model.StatusUpdate
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.net.Proxy
import sun.misc.Unsafe

class CrawlerHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var cookieStore: RecordingCookieStore
    private lateinit var client: CrawlerHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        cookieStore = RecordingCookieStore("session=abc; theme=dark")
        client = CrawlerHttpClient(cookieStore, DirectProxyController, uninitializedImageCache())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun fetchSendsCookieRefererAndUserAgentHeaders() {
        server.enqueue(MockResponse.Builder().body("<html>ok</html>").build())

        val body = client.fetch(
            rawUrl = server.url("/thread-123.html").toString(),
            referer = "https://referer.test/forum",
            useProxy = false,
            status = {},
        )

        val request = server.takeRequest()
        assertEquals("<html>ok</html>", body)
        assertEquals("/thread-123.html", request.target)
        assertEquals("session=abc; theme=dark", request.headers["Cookie"])
        assertEquals("https://referer.test/forum", request.headers["Referer"])
        assertEquals(USER_AGENT, request.headers["User-Agent"])
    }

    @Test
    fun fetchUsesBaseUrlAsDefaultRefererAndCollectsResponseCookies() {
        server.enqueue(
            MockResponse.Builder()
                .body("""<script>var safeid = "safe-token"</script>""")
                .addHeader("Set-Cookie", "sid=server; Path=/; HttpOnly")
                .addHeader("Set-Cookie", "pref=wide; Path=/")
                .build()
        )

        client.fetch(
            rawUrl = server.url("/").toString(),
            referer = null,
            useProxy = false,
            status = {},
        )

        val request = server.takeRequest()
        assertEquals(BASE_URL, request.headers["Referer"])
        assertEquals("server", cookieStore.cookies["sid"])
        assertEquals("wide", cookieStore.cookies["pref"])
        assertEquals("safe-token", cookieStore.cookies["_safe"])
    }

    @Test
    fun fetchRejectsHtmlBodiesOverLimit() {
        server.enqueue(
            MockResponse.Builder()
                .body("x".repeat(3 * 1024 * 1024 + 1))
                .build()
        )

        val error = assertThrows(RuntimeException::class.java) {
            client.fetch(
                rawUrl = server.url("/large").toString(),
                referer = null,
                useProxy = false,
                status = {},
            )
        }

        assertTrue(error.message.orEmpty().contains("响应过大"))
    }

    private class RecordingCookieStore(initialHeader: String = "") : HttpCookieStore {
        val cookies = linkedMapOf<String, String>()

        init {
            collect(mapOf("set-cookie" to initialHeader.split("; ").map { "$it; Path=/" }))
        }

        override fun header(): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        override fun collect(headers: Map<String, List<String>>?) {
            if (headers == null) return
            for ((key, values) in headers) {
                if (!key.equals("set-cookie", ignoreCase = true)) continue
                for (raw in values) {
                    val first = raw.split(";", limit = 2).firstOrNull()?.trim() ?: continue
                    val separator = first.indexOf('=')
                    if (separator > 0) cookies[first.substring(0, separator)] = first.substring(separator + 1)
                }
            }
        }

        override fun put(name: String, value: String) {
            cookies[name] = value
        }
    }

    private object DirectProxyController : HttpProxyController {
        override fun requestProxy(useProxy: Boolean): Proxy = Proxy.NO_PROXY
        override fun networkModeLabel(useProxy: Boolean): String = "direct"
        override fun isReadyFast(): Boolean = false
        override fun markProxyUnhealthy(message: String, status: (StatusUpdate) -> Unit) = Unit
        override fun notifyFallbackOnce(message: String, status: (StatusUpdate) -> Unit) = Unit
        override fun conciseError(error: Throwable?): String = error?.message.orEmpty()
    }

    private companion object {
        fun uninitializedImageCache(): ImageCache {
            val unsafeField: Field = Unsafe::class.java.getDeclaredField("theUnsafe").apply {
                isAccessible = true
            }
            val unsafe = unsafeField.get(null) as Unsafe
            return unsafe.allocateInstance(ImageCache::class.java) as ImageCache
        }
    }
}
