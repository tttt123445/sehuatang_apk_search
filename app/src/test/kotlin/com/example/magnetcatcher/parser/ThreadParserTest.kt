package com.example.magnetcatcher.parser

import com.example.magnetcatcher.AppConstants.BASE_URL
import com.example.magnetcatcher.model.ThreadRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadParserTest {
    @Test
    fun extractsTitleMagnetsAndImages() {
        val html = """
            <html>
              <title>备用标题 - Powered by Discuz!</title>
              <h1 id="thread_subject"> 正式标题 </h1>
              <td id="postmessage_1">
                发表于 2026-06-01 08:30:00
                magnet:?xt=urn:btih:ABCDEF123456&amp;dn=name,
                <img src="/static/image/avatar.png" />
                <img file="forum/202606/01/sample.jpg" src="small.jpg" />
                <img data-original="//img.example.com/pic.jpg" />
              </td>
            </html>
        """.trimIndent()

        val item = ThreadParser.parseThread(
            html,
            ThreadRef(2, "123", "列表标题", "$BASE_URL/thread-123-1-1.html"),
        )

        assertEquals("正式标题", item.title)
        assertEquals("2026-06-01 08:30", item.publishedAt)
        assertEquals(listOf("magnet:?xt=urn:btih:ABCDEF123456&dn=name"), item.magnets)
        assertEquals("$BASE_URL/forum/202606/01/sample.jpg", item.images.first())
        assertTrue(item.images.contains("https://img.example.com/pic.jpg"))
    }

    @Test
    fun fallsBackToPageTitleWhenSubjectIsBad() {
        val html = """<title>好标题 - 手机版</title><h1 id="thread_subject">123</h1>"""
        assertEquals("好标题", ThreadParser.extractThreadTitle(html))
    }
}
