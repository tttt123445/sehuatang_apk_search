package com.example.magnetcatcher.domain

import com.example.magnetcatcher.model.CrawlerItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFormatterTest {
    @Test
    fun batchesSelectedMagnetsAndDeduplicates() {
        val magnets = (1..55).map { "magnet:?xt=urn:btih:$it" }
        val items = listOf(
            CrawlerItem(threadId = "1", magnets = magnets, enabledMagnets = magnets.toSet()),
            CrawlerItem(threadId = "2", magnets = listOf(magnets.first()), enabledMagnets = setOf(magnets.first())),
            CrawlerItem(threadId = "3", selected = false, magnets = listOf("magnet:?xt=urn:btih:999"), enabledMagnets = setOf("magnet:?xt=urn:btih:999")),
        )

        val (first, signature, offset) = ExportFormatter.nextExportBatch(items, "", 0)
        val (second) = ExportFormatter.nextExportBatch(items, signature, offset)

        assertEquals(1, first.start)
        assertEquals(50, first.end)
        assertEquals(55, first.total)
        assertEquals(51, second.start)
        assertEquals(55, second.end)
        assertEquals(5, second.text.lines().size)
    }
}
