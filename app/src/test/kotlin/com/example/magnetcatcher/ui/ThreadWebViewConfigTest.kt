package com.example.magnetcatcher.ui

import com.example.magnetcatcher.AppConstants.BASE_URL
import com.example.magnetcatcher.AppConstants.DIRECT_URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadWebViewConfigTest {
    @Test
    fun splitCookieHeaderTrimsCookiePairsAndDropsAttributes() {
        val cookies = splitCookieHeader(" session=abc ; Secure ; theme=dark; HttpOnly ; token=a=b ")

        assertEquals(listOf("session=abc", "theme=dark", "token=a=b"), cookies)
    }

    @Test
    fun splitCookieHeaderReturnsEmptyForBlankHeader() {
        assertEquals(emptyList<String>(), splitCookieHeader(""))
        assertEquals(emptyList<String>(), splitCookieHeader("   "))
    }

    @Test
    fun threadCookieTargetsIncludesThreadBaseAndDirectOriginsOnce() {
        val targets = threadCookieTargets("https://example.test:8443/thread-123.html?from=dialog")

        assertEquals(
            listOf(
                "https://example.test:8443",
                BASE_URL,
                DIRECT_URL,
            ),
            targets,
        )
    }

    @Test
    fun threadCookieTargetsIgnoresInvalidThreadUrlAndDeduplicatesKnownOrigins() {
        assertEquals(listOf(BASE_URL, DIRECT_URL), threadCookieTargets(BASE_URL))
        assertEquals(listOf(BASE_URL, DIRECT_URL), threadCookieTargets("not a url"))
    }

    @Test
    fun threadWebProxyOverrideOnlyEnablesProxyWithPositivePort() {
        val enabled = threadWebProxyOverride(WebPageUiState(url = BASE_URL, useProxy = true, proxyPort = 1080))
        val disabledByFlag = threadWebProxyOverride(WebPageUiState(url = BASE_URL, useProxy = false, proxyPort = 1080))
        val disabledByPort = threadWebProxyOverride(WebPageUiState(url = BASE_URL, useProxy = true, proxyPort = 0))

        assertTrue(enabled.enabled)
        assertEquals("127.0.0.1:1080", enabled.proxyRule)
        assertFalse(disabledByFlag.enabled)
        assertNull(disabledByFlag.proxyRule)
        assertFalse(disabledByPort.enabled)
        assertNull(disabledByPort.proxyRule)
    }
}
