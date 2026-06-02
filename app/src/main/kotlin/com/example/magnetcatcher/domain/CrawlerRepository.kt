package com.example.magnetcatcher.domain

import com.example.magnetcatcher.AppConstants.BASE_URL
import com.example.magnetcatcher.data.LibraryStore
import com.example.magnetcatcher.data.SettingsStore
import com.example.magnetcatcher.model.CrawlFailureStats
import com.example.magnetcatcher.model.CrawlRequest
import com.example.magnetcatcher.model.CrawlResult
import com.example.magnetcatcher.model.CrawlerItem
import com.example.magnetcatcher.model.ForumOption
import com.example.magnetcatcher.model.StatusTone
import com.example.magnetcatcher.model.StatusUpdate
import com.example.magnetcatcher.network.CrawlerHttpClient
import com.example.magnetcatcher.parser.DateParser
import com.example.magnetcatcher.parser.ForumParser
import com.example.magnetcatcher.parser.ThreadParser
import com.example.magnetcatcher.xtunnel.XTunnelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class CrawlerRepository(
    private val settingsStore: SettingsStore,
    private val libraryStore: LibraryStore,
    private val httpClient: CrawlerHttpClient,
    private val xtunnelManager: XTunnelManager,
) {
    fun forumOptions(): List<ForumOption> = settingsStore.loadForumOptions().ifEmpty { ForumParser.defaultForumOptions }

    fun loadLibrary(): List<CrawlerItem> = libraryStore.load()

    fun saveLibrary(items: List<CrawlerItem>) = libraryStore.save(items)

    suspend fun checkAccess(useProxy: Boolean, status: (StatusUpdate) -> Unit): String {
        var activeProxy = useProxy
        if (activeProxy) activeProxy = xtunnelManager.prepareOrFallback("检查访问", status)
        val html = httpClient.fetchWithRetry(BASE_URL, BASE_URL, attempts = 2, useProxy = activeProxy, status = status)
        if (html.length < 100) throw RuntimeException("返回内容太短，请确认当前网络可访问")
        return xtunnelManager.networkModeLabel(activeProxy)
    }

    suspend fun crawl(request: CrawlRequest, status: (StatusUpdate) -> Unit): CrawlResult {
        var activeProxy = request.useProxy
        if (activeProxy) activeProxy = xtunnelManager.prepareOrFallback("抓取", status)
        val options = forumOptions()
        if (request.fids.isEmpty()) throw RuntimeException("先选一个模块")
        val sinceLabel = DateParser.normalizedSinceTime(request.sinceRaw)
        val sinceMillis = DateParser.parseSinceTimeMillis(sinceLabel)
        val found = linkedMapOf<String, CrawlerItem>()
        var listFailures = 0
        var threadFailures = 0
        var noMagnetSkips = 0
        var timeSkips = 0
        val samples = mutableListOf<String>()

        for (fid in request.fids) {
            var stopForum = false
            for (page in 1..request.pages.coerceAtLeast(1)) {
                if (stopForum) break
                val listUrl = buildDatelineListUrl(fid, page)
                status(
                    StatusUpdate(
                        title = "任务进行中",
                        detail = "按发帖时间抓列表 ${forumName(fid, options)} page=$page，起始 $sinceLabel，${xtunnelManager.networkModeLabel(activeProxy)}",
                        meta = "正在读取列表页",
                        tone = StatusTone.Warning,
                        spinning = true,
                    )
                )

                val html = try {
                    httpClient.fetchWithRetry(listUrl, BASE_URL, attempts = 2, useProxy = activeProxy, status = status)
                } catch (error: Exception) {
                    listFailures++
                    addFailureSample(samples, "${forumName(fid, options)} p$page：${xtunnelManager.conciseError(error)}")
                    continue
                }

                val refs = ForumParser.parseList(html, fid)
                val pendingRefs = mutableListOf<ThreadFetchInput>()
                val seenOnPage = mutableSetOf<String>()
                for (ref in refs) {
                    if (found.containsKey(ref.threadId) || !seenOnPage.add(ref.threadId)) continue
                    val listPublishedMillis = DateParser.parsePublishedAtMillis(ref.publishedAt)
                    if (listPublishedMillis != Long.MIN_VALUE && listPublishedMillis < sinceMillis) {
                        if (!ref.sticky) stopForum = true
                        timeSkips++
                        continue
                    }
                    pendingRefs += ThreadFetchInput(ref, listUrl, listPublishedMillis)
                }

                val outcomes = fetchThreadsConcurrently(pendingRefs, sinceMillis, activeProxy, status)
                for (outcome in outcomes) {
                    when (outcome) {
                        is ThreadFetchOutcome.Found -> found[outcome.item.threadId] = outcome.item
                        is ThreadFetchOutcome.Failed -> {
                            threadFailures++
                            addFailureSample(samples, outcome.sample)
                        }
                        ThreadFetchOutcome.NoMagnet -> noMagnetSkips++
                        is ThreadFetchOutcome.TimeSkipped -> {
                            timeSkips++
                            if (outcome.stopForum) stopForum = true
                        }
                    }
                }
            }
        }

        val nextItems = found.values.toList()
        saveLibrary(nextItems)
        return CrawlResult(
            items = nextItems,
            sinceLabel = sinceLabel,
            failures = CrawlFailureStats(listFailures, threadFailures, noMagnetSkips, timeSkips, samples),
        )
    }

    private fun buildDatelineListUrl(fid: Int, page: Int): String {
        return "$BASE_URL/forum.php?mod=forumdisplay&fid=$fid&filter=author&orderby=dateline&page=$page&mobile=2"
    }

    private fun forumName(fid: Int, options: List<ForumOption>): String {
        return options.firstOrNull { it.fid == fid }?.name ?: "fid=$fid"
    }

    private fun addFailureSample(samples: MutableList<String>, sample: String) {
        if (samples.size < 3 && sample.isNotEmpty()) {
            samples += if (sample.length > 42) sample.substring(0, 42) + "..." else sample
        }
    }

    private suspend fun fetchThreadsConcurrently(
        refs: List<ThreadFetchInput>,
        sinceMillis: Long,
        activeProxy: Boolean,
        status: (StatusUpdate) -> Unit,
    ): List<ThreadFetchOutcome> = coroutineScope {
        if (refs.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(THREAD_FETCH_CONCURRENCY)
        refs.map { input ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    fetchThread(input, sinceMillis, activeProxy, status)
                }
            }
        }.map { it.await() }
    }

    private fun fetchThread(
        input: ThreadFetchInput,
        sinceMillis: Long,
        activeProxy: Boolean,
        status: (StatusUpdate) -> Unit,
    ): ThreadFetchOutcome {
        val ref = input.ref
        status(
            StatusUpdate(
                title = "任务进行中",
                detail = "抓帖子：${ref.title}${if (ref.publishedAt.isNotEmpty()) " · ${ref.publishedAt}" else ""}",
                meta = "当前网络：${xtunnelManager.networkModeLabel(activeProxy)}",
                tone = StatusTone.Warning,
                spinning = true,
            )
        )
        return try {
            val threadHtml = httpClient.fetchWithRetry(ref.url, input.listUrl, attempts = 2, useProxy = activeProxy, status = status)
            val item = ThreadParser.parseThread(threadHtml, ref)
            if (item.magnets.isEmpty()) return ThreadFetchOutcome.NoMagnet
            var publishedMillis = DateParser.parsePublishedAtMillis(item.publishedAt)
            if (publishedMillis == Long.MIN_VALUE) publishedMillis = input.listPublishedMillis
            if (publishedMillis != Long.MIN_VALUE && publishedMillis < sinceMillis) {
                return ThreadFetchOutcome.TimeSkipped(stopForum = !ref.sticky)
            }
            if (publishedMillis == Long.MIN_VALUE) return ThreadFetchOutcome.TimeSkipped(stopForum = false)
            ThreadFetchOutcome.Found(item)
        } catch (error: Exception) {
            ThreadFetchOutcome.Failed("${ref.title}：${xtunnelManager.conciseError(error)}")
        }
    }

    private data class ThreadFetchInput(
        val ref: com.example.magnetcatcher.model.ThreadRef,
        val listUrl: String,
        val listPublishedMillis: Long,
    )

    private sealed interface ThreadFetchOutcome {
        data class Found(val item: CrawlerItem) : ThreadFetchOutcome
        data class Failed(val sample: String) : ThreadFetchOutcome
        data object NoMagnet : ThreadFetchOutcome
        data class TimeSkipped(val stopForum: Boolean) : ThreadFetchOutcome
    }

    companion object {
        private const val THREAD_FETCH_CONCURRENCY = 4
    }
}
