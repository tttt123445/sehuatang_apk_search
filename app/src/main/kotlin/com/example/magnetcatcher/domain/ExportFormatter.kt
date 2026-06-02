package com.example.magnetcatcher.domain

import com.example.magnetcatcher.AppConstants.EXPORT_BATCH_SIZE
import com.example.magnetcatcher.model.CrawlerItem
import com.example.magnetcatcher.model.ExportBatch

object ExportFormatter {
    fun selectedExportLines(items: List<CrawlerItem>): List<String> {
        return linkedSetOf<String>().apply {
            for (item in items) {
                if (!item.selected) continue
                for (magnet in item.magnets) {
                    if (item.enabledMagnets.contains(magnet)) add(magnet)
                }
            }
        }.toList()
    }

    fun nextExportBatch(items: List<CrawlerItem>, signature: String, offset: Int): Triple<ExportBatch, String, Int> {
        val lines = selectedExportLines(items)
        if (lines.isEmpty()) return Triple(ExportBatch("", 0, 0, 0), "", 0)
        val nextSignature = lines.joinToString("\n")
        var nextOffset = if (nextSignature != signature || offset >= lines.size) 0 else offset
        val start = nextOffset
        val end = minOf(start + EXPORT_BATCH_SIZE, lines.size)
        val text = lines.subList(start, end).joinToString("\n")
        nextOffset = if (end >= lines.size) 0 else end
        return Triple(ExportBatch(text, start + 1, end, lines.size), nextSignature, nextOffset)
    }
}
