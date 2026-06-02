package com.example.magnetcatcher.model

data class ForumOption(
    val fid: Int,
    val name: String,
    val parentFid: Int = 0,
)

data class ThreadRef(
    val fid: Int,
    val threadId: String,
    val title: String,
    val url: String,
    val publishedAt: String = "",
    val sticky: Boolean = false,
)

data class CrawlerItem(
    val fid: Int = 0,
    val threadId: String = "",
    val title: String = "",
    val url: String = "",
    val publishedAt: String = "",
    val description: String = "",
    val thumbnailUrl: String = "",
    val selected: Boolean = true,
    val magnets: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val enabledMagnets: Set<String> = magnets.toSet(),
) {
    fun exportableMagnets(): List<String> = magnets.filter { enabledMagnets.contains(it) }
}

data class ExportBatch(
    val text: String,
    val start: Int,
    val end: Int,
    val total: Int,
)

data class CrawlRequest(
    val fids: List<Int>,
    val pages: Int,
    val sinceRaw: String,
    val useProxy: Boolean,
)

data class CrawlFailureStats(
    val listFailures: Int = 0,
    val threadFailures: Int = 0,
    val noMagnetSkips: Int = 0,
    val timeSkips: Int = 0,
    val samples: List<String> = emptyList(),
)

data class CrawlResult(
    val items: List<CrawlerItem>,
    val sinceLabel: String,
    val failures: CrawlFailureStats,
)
