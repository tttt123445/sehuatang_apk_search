package com.example.magnetcatcher.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class ForumParserTest {
    @Test
    fun parsesThreadListRows() {
        val html = """
            <tbody id="normalthread_123">
              <tr>
                <td><a href="thread-123-1-1.html"> 测试帖子 </a></td>
                <td>今天 10:20</td>
              </tr>
            </tbody>
        """.trimIndent()

        val refs = ForumParser.parseList(html, 2)

        assertEquals(1, refs.size)
        assertEquals("123", refs.first().threadId)
        assertEquals("测试帖子", refs.first().title)
        assertEquals("今天 10:20", refs.first().publishedAt)
    }

    @Test
    fun parsesBtForumOptionsFromMobileJson() {
        val json = """
            {
              "Variables": {
                "forumlist": [
                  {"fid": 2, "name": "国产原创", "fup": 10},
                  {"fid": 99, "name": "茶馆", "fup": 11}
                ],
                "catlist": [
                  {"fid": 10, "name": "BT区", "forums": [2]}
                ]
              }
            }
        """.trimIndent()

        val options = ForumParser.parseBTForumOptions(json)

        assertEquals(listOf(2), options.map { it.fid })
        assertEquals("国产原创", options.first().name)
    }
}
