package com.example.magnetcatcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.example.magnetcatcher.data.CookieStore
import com.example.magnetcatcher.data.ImageCache
import com.example.magnetcatcher.data.LibraryStore
import com.example.magnetcatcher.data.SettingsStore
import com.example.magnetcatcher.domain.CrawlerRepository
import com.example.magnetcatcher.domain.ExportFormatter
import com.example.magnetcatcher.model.CrawlFailureStats
import com.example.magnetcatcher.model.CrawlRequest
import com.example.magnetcatcher.model.CrawlerItem
import com.example.magnetcatcher.model.StatusTone
import com.example.magnetcatcher.model.StatusUpdate
import com.example.magnetcatcher.network.CrawlerHttpClient
import com.example.magnetcatcher.parser.DateParser
import com.example.magnetcatcher.parser.UrlTools
import com.example.magnetcatcher.xtunnel.XTunnelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application)
    private val cookieStore = CookieStore(settingsStore)
    private val libraryStore = LibraryStore(application)
    private val imageCache = ImageCache(application)
    private val xtunnelManager = XTunnelManager(application, settingsStore)
    private val httpClient = CrawlerHttpClient(cookieStore, xtunnelManager, imageCache)
    private val repository = CrawlerRepository(settingsStore, libraryStore, httpClient, xtunnelManager)

    private var exportBatchOffset = 0
    private var exportBatchSignature = ""
    private val imageLoaderLock = Any()
    private val imageLoaders = mutableMapOf<Boolean, ImageLoader>()
    private val statusLock = Any()
    private var lastStatusUpdateMs = 0L
    private var pendingStatusUpdate: StatusUpdate? = null
    private var statusFlushJob: Job? = null

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _uiState = MutableStateFlow(
        MainUiState(
            forumOptions = repository.forumOptions(),
            useXTunnel = settingsStore.useXTunnel,
            proxyPort = settingsStore.proxyPort.toString(),
            pages = settingsStore.pages.toString(),
            sinceTime = settingsStore.sinceTime,
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    val networkState: StateFlow<NetworkPanelUiState> = _uiState
        .map { NetworkPanelUiState(it.useXTunnel, it.status, it.busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetworkPanelUiState(_uiState.value.useXTunnel, _uiState.value.status, _uiState.value.busy))
    val captureState: StateFlow<CapturePanelUiState> = _uiState
        .map { CapturePanelUiState(it.forumOptions, it.selectedFids, it.pages, it.sinceTime, it.timeRange, it.forumsExpanded, it.busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CapturePanelUiState(_uiState.value.forumOptions, _uiState.value.selectedFids, _uiState.value.pages, _uiState.value.sinceTime, _uiState.value.timeRange, _uiState.value.forumsExpanded, _uiState.value.busy))
    val resultState: StateFlow<ResultPanelUiState> = _uiState
        .map { ResultPanelUiState(it.items, it.busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResultPanelUiState(_uiState.value.items, _uiState.value.busy))
    val developerState: StateFlow<DeveloperPanelUiState> = _uiState
        .map { DeveloperPanelUiState(it.useXTunnel, it.proxyPort, it.pages, it.showDeveloperOptions, it.busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeveloperPanelUiState(_uiState.value.useXTunnel, _uiState.value.proxyPort, _uiState.value.pages, _uiState.value.showDeveloperOptions, _uiState.value.busy))
    val dialogState: StateFlow<DialogUiState> = _uiState
        .map { DialogUiState(it.datePicker, it.preview, it.webPage) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DialogUiState(_uiState.value.datePicker, _uiState.value.preview, _uiState.value.webPage))
    val useXTunnelState: StateFlow<Boolean> = _uiState
        .map { it.useXTunnel }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.useXTunnel)

    init {
        val selected = settingsStore.selectedFids(_uiState.value.forumOptions)
        _uiState.update { it.copy(selectedFids = selected, status = idleStatus(it.useXTunnel)) }
        loadLibrary()
        if (settingsStore.useXTunnel) beginXTunnelWarmup()
    }

    fun onUseXTunnelChange(value: Boolean) {
        settingsStore.useXTunnel = value
        _uiState.update { it.copy(useXTunnel = value, status = idleStatus(value)) }
        if (value) beginXTunnelWarmup()
    }

    fun toggleNetworkMode() {
        onUseXTunnelChange(!_uiState.value.useXTunnel)
    }

    fun onProxyPortChange(value: String) {
        _uiState.update { it.copy(proxyPort = value.filter { ch -> ch.isDigit() }.take(5)) }
    }

    fun onPagesChange(value: String) {
        _uiState.update { it.copy(pages = value.filter { ch -> ch.isDigit() }.take(3)) }
    }

    fun onSinceTimeChange(value: String) {
        _uiState.update { it.copy(sinceTime = value, timeRange = TimeRangeOption.Custom) }
    }

    fun setSincePreset(daysAgo: Int) {
        _uiState.update { it.copy(sinceTime = DateParser.relativeSinceTime(daysAgo)) }
    }

    fun selectTimeRange(option: TimeRangeOption) {
        when (option) {
            TimeRangeOption.Today -> _uiState.update {
                it.copy(timeRange = option, sinceTime = DateParser.relativeSinceTime(0))
            }
            TimeRangeOption.Yesterday -> _uiState.update {
                it.copy(timeRange = option, sinceTime = DateParser.relativeSinceTime(1))
            }
            TimeRangeOption.Last7Days -> _uiState.update {
                it.copy(timeRange = option, sinceTime = DateParser.relativeSinceTime(6))
            }
            TimeRangeOption.Custom -> openDatePicker()
        }
    }

    fun openDatePicker() {
        val initialMillis = pickerInitialMillis(_uiState.value.sinceTime)
        _uiState.update {
            it.copy(
                datePicker = DatePickerUiState(initialSelectedDateMillis = initialMillis),
            )
        }
    }

    fun confirmDatePicker(selectedDateMillis: Long?) {
        if (selectedDateMillis == null) {
            emitToast("请先选择日期")
            return
        }
        _uiState.update {
            it.copy(
                timeRange = TimeRangeOption.Custom,
                sinceTime = formatPickerDate(selectedDateMillis),
                datePicker = null,
            )
        }
    }

    fun dismissDatePicker() {
        _uiState.update { it.copy(datePicker = null) }
    }

    fun onDeveloperOptionsChange(value: Boolean) {
        _uiState.update { it.copy(showDeveloperOptions = value) }
    }

    fun toggleForumsExpanded() {
        _uiState.update { it.copy(forumsExpanded = !it.forumsExpanded) }
    }

    fun toggleForum(fid: Int) {
        _uiState.update { state ->
            val next = state.selectedFids.toMutableSet()
            if (!next.add(fid)) next.remove(fid)
            settingsStore.saveSelectedFids(next)
            state.copy(selectedFids = next)
        }
    }

    fun toggleItemSelected(threadId: String) {
        _uiState.update { state ->
            state.copy(items = state.items.map { item ->
                if (item.threadId == threadId) item.copy(selected = !item.selected) else item
            })
        }
    }

    fun launchXTunnel() {
        val previous = _uiState.value.useXTunnel
        settingsStore.useXTunnel = true
        _uiState.update { it.copy(useXTunnel = true) }
        runBusy("启动内置 XTunnel...") {
            try {
                xtunnelManager.ensureReady(::applyStatus)
            } catch (error: Exception) {
                settingsStore.useXTunnel = previous
                _uiState.update {
                    it.copy(
                        useXTunnel = previous,
                        status = StatusCardUiState(
                            title = "XTunnel 启动失败",
                            detail = "已恢复到${if (previous) "原有网络偏好" else "系统 VPN/直连"}：${xtunnelManager.conciseError(error)}",
                            meta = "可稍后重试，或保持系统 VPN/直连。",
                            tone = StatusTone.Error,
                        )
                    )
                }
                throw error
            }
        }
    }

    fun runCheckAndCookie() {
        saveSettings()
        val useProxy = _uiState.value.useXTunnel
        runBusy("检查中...") {
            val mode = repository.checkAccess(useProxy, ::applyStatus)
            applyStatus(
                StatusUpdate(
                    title = "访问正常",
                    detail = "Cookie 已刷新：${cookieStore.keys()}",
                    meta = "当前网络：$mode",
                    tone = StatusTone.Success,
                )
            )
        }
    }

    fun runCrawl() {
        saveSettings()
        val state = _uiState.value
        val request = CrawlRequest(
            fids = state.selectedFids.toList(),
            pages = state.pages.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            sinceRaw = state.sinceTime,
            useProxy = state.useXTunnel,
        )
        runBusy("开始抓取...") {
            val result = repository.crawl(request, ::applyStatus)
            _uiState.update {
                it.copy(
                    items = result.items,
                    sinceTime = result.sinceLabel,
                    status = StatusCardUiState(
                        title = if (result.failures.listFailures > 0 || result.failures.threadFailures > 0) "抓取完成，有跳过" else "抓取完成",
                        detail = "起始 ${result.sinceLabel}，帖子 ${result.items.size} 个。",
                        meta = crawlFailureSummary(result.failures, result.items),
                        tone = if (result.failures.listFailures > 0 || result.failures.threadFailures > 0) StatusTone.Warning else StatusTone.Success,
                    )
                )
            }
        }
    }

    fun imageLoader(useProxy: Boolean): ImageLoader {
        val activeProxy = useProxy && xtunnelManager.isReadyFast()
        synchronized(imageLoaderLock) {
            imageLoaders[activeProxy]?.let { return it }
            return ImageLoader.Builder(getApplication<Application>())
                .components {
                    add(
                        OkHttpNetworkFetcherFactory(
                            callFactory = {
                                httpClient.imageCallFactory(activeProxy)
                            }
                        )
                    )
                }
                .build()
                .also { imageLoaders[activeProxy] = it }
        }
    }

    fun showPreview(item: CrawlerItem, index: Int = 0) {
        if (item.images.isEmpty()) {
            emitToast("这个帖子没有图片")
            return
        }
        val safeIndex = index.coerceIn(0, item.images.lastIndex)
        val wantsProxy = _uiState.value.useXTunnel
        _uiState.update { it.copy(preview = PreviewUiState(item = item, index = safeIndex, preparingProxy = wantsProxy)) }
        if (!wantsProxy) return
        viewModelScope.launch(Dispatchers.IO) {
            val activeProxy = xtunnelManager.prepareOrFallback("图片预览", ::applyStatus)
            _uiState.update { state ->
                val current = state.preview
                if (current?.item?.threadId == item.threadId && current.index == safeIndex) {
                    state.copy(preview = current.copy(useProxy = activeProxy, preparingProxy = false, error = ""))
                } else {
                    state
                }
            }
        }
    }

    fun retryPreview() {
        _uiState.value.preview?.let { showPreview(it.item, it.index) }
    }

    fun previousPreview() {
        _uiState.value.preview?.let { preview ->
            if (preview.index > 0) showPreview(preview.item, preview.index - 1)
        }
    }

    fun nextPreview() {
        _uiState.value.preview?.let { preview ->
            if (preview.index + 1 < preview.item.images.size) showPreview(preview.item, preview.index + 1)
        }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(preview = null) }
    }

    fun openOriginal(item: CrawlerItem) {
        if (item.url.isEmpty()) {
            emitToast("原帖地址为空")
            return
        }
        val url = UrlTools.externalThreadUrl(item.url)
        val useProxy = _uiState.value.useXTunnel
        if (!useProxy) {
            _uiState.update {
                it.copy(
                    webPage = WebPageUiState(
                        url = url,
                        title = "原帖",
                        proxyPort = xtunnelManager.currentPort(),
                        cookieHeader = cookieStore.header(),
                    )
                )
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val activeProxy = xtunnelManager.prepareOrFallback("打开原帖", ::applyStatus)
            _uiState.update {
                it.copy(
                    webPage = WebPageUiState(
                        url = url,
                        title = "原帖",
                        useProxy = activeProxy,
                        proxyPort = xtunnelManager.currentPort(),
                        cookieHeader = cookieStore.header(),
                    )
                )
            }
        }
    }

    fun dismissWebPage() {
        _uiState.update { it.copy(webPage = null) }
    }

    fun openWebPageExternally() {
        val url = _uiState.value.webPage?.url ?: return
        viewModelScope.launch { _events.emit(UiEvent.OpenUrl(url)) }
    }

    fun copySelected() {
        val (batch, signature, offset) = ExportFormatter.nextExportBatch(_uiState.value.items, exportBatchSignature, exportBatchOffset)
        exportBatchSignature = signature
        exportBatchOffset = offset
        if (batch.text.isEmpty()) {
            emitToast("没有选中的磁力")
            return
        }
        viewModelScope.launch {
            _events.emit(UiEvent.CopyText("115 magnets", batch.text, "已复制 ${batch.start}-${batch.end} / ${batch.total} 条"))
        }
    }

    fun shareSelected() {
        val (batch, signature, offset) = ExportFormatter.nextExportBatch(_uiState.value.items, exportBatchSignature, exportBatchOffset)
        exportBatchSignature = signature
        exportBatchOffset = offset
        if (batch.text.isEmpty()) {
            emitToast("没有选中的磁力")
            return
        }
        viewModelScope.launch {
            _events.emit(UiEvent.ShareText("115-import-${batch.start}-${batch.end}.txt", batch.text))
        }
    }

    override fun onCleared() {
        xtunnelManager.close()
        super.onCleared()
    }

    private fun loadLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = repository.loadLibrary()
            if (loaded.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        items = loaded,
                        status = state.status.copy(
                            detail = "已加载缓存：帖子 ${loaded.size} 个，磁力 ${loaded.sumOf { it.enabledMagnets.size }} 条，带图 ${loaded.count { it.images.isNotEmpty() }} 个。",
                        )
                    )
                }
            }
        }
    }

    private fun beginXTunnelWarmup() {
        if (!settingsStore.useXTunnel || xtunnelManager.isReadyFast() || xtunnelManager.starting || _uiState.value.busy || xtunnelManager.isFailureCooldownActive()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                xtunnelManager.ensureReady(::applyStatus)
            } catch (error: Exception) {
                xtunnelManager.notifyFallbackOnce("XTunnel 预热失败，后续请求会先走系统 VPN/直连：${xtunnelManager.conciseError(error)}", ::applyStatus)
            }
        }
    }

    private fun runBusy(message: String, block: suspend () -> Unit) {
        if (_uiState.value.busy) {
            emitToast("当前任务进行中，请稍候")
            return
        }
        _uiState.update {
            it.copy(
                busy = true,
                status = StatusCardUiState(
                    title = "任务进行中",
                    detail = message,
                    meta = "已锁定启动、检查和抓取按钮，避免重复任务排队。",
                    tone = StatusTone.Warning,
                    spinning = true,
                )
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            var failure: Exception? = null
            try {
                block()
            } catch (error: Exception) {
                failure = error
            } finally {
                val doneFailure = failure
                _uiState.update { state ->
                    val failedStatus = if (doneFailure != null) {
                        StatusCardUiState(
                            title = if (message.contains("XTunnel")) "XTunnel 启动失败" else "操作失败",
                            detail = doneFailure.message ?: doneFailure.javaClass.simpleName,
                            meta = if (message.contains("XTunnel")) "可稍后重试，或保持系统 VPN/直连。" else "请检查网络状态后重试。",
                            tone = StatusTone.Error,
                        )
                    } else {
                        state.status.copy(spinning = false)
                    }
                    state.copy(busy = false, status = failedStatus)
                }
            }
        }
    }

    private fun saveSettings() {
        val state = _uiState.value
        settingsStore.proxyPort = state.proxyPort.toIntOrNull() ?: settingsStore.proxyPort
        settingsStore.pages = state.pages.toIntOrNull() ?: 1
        settingsStore.sinceTime = state.sinceTime
        settingsStore.saveSelectedFids(state.selectedFids)
    }

    private fun applyStatus(update: StatusUpdate) {
        val immediate = !update.spinning || update.tone == StatusTone.Success || update.tone == StatusTone.Error
        val now = System.currentTimeMillis()
        if (immediate) {
            synchronized(statusLock) {
                pendingStatusUpdate = null
                statusFlushJob?.cancel()
                statusFlushJob = null
                lastStatusUpdateMs = now
            }
            commitStatus(update)
            return
        }

        var commitNow: StatusUpdate? = null
        synchronized(statusLock) {
            val elapsed = now - lastStatusUpdateMs
            if (elapsed >= STATUS_THROTTLE_MS) {
                lastStatusUpdateMs = now
                commitNow = update
            } else {
                pendingStatusUpdate = update
                if (statusFlushJob == null) {
                    statusFlushJob = viewModelScope.launch {
                        delay(STATUS_THROTTLE_MS - elapsed)
                        val pending = synchronized(statusLock) {
                            statusFlushJob = null
                            lastStatusUpdateMs = System.currentTimeMillis()
                            pendingStatusUpdate.also { pendingStatusUpdate = null }
                        }
                        pending?.let { commitStatus(it) }
                    }
                }
            }
        }
        commitNow?.let { commitStatus(it) }
    }

    private fun commitStatus(update: StatusUpdate) {
        _uiState.update { it.copy(status = StatusCardUiState.from(update)) }
    }

    private fun idleStatus(useXTunnel: Boolean): StatusCardUiState {
        return when {
            xtunnelManager.starting -> StatusCardUiState("XTunnel 启动中", xtunnelManager.statusDetail, "请稍候，启动完成前不会重复创建任务。", StatusTone.Warning, true)
            xtunnelManager.isReadyFast() && useXTunnel -> StatusCardUiState("XTunnel 已就绪", "本机代理端口 ${xtunnelManager.currentPort()} 已连通：${xtunnelManager.statusDetail}", "抓取、检查和图片预览会优先走内置 XTunnel。", StatusTone.Success)
            useXTunnel -> StatusCardUiState("XTunnel 未启动", "已选择优先使用内置 XTunnel，点击启动或抓取时会自动启动。", "就绪后才会切到本机代理端口 ${xtunnelManager.currentPort()}。", StatusTone.Warning)
            xtunnelManager.isReadyFast() -> StatusCardUiState("网络状态", "当前走系统 VPN/直连。", "内置 XTunnel 已待命，需要时可勾选启用。", StatusTone.Success)
            else -> StatusCardUiState()
        }
    }

    private fun crawlFailureSummary(failures: CrawlFailureStats, items: List<CrawlerItem>): String {
        val parts = mutableListOf(
            "磁力 ${items.sumOf { it.enabledMagnets.size }} 条",
            "带图 ${items.count { it.images.isNotEmpty() }} 个",
            "可导出 ${items.sumOf { item -> if (item.selected) item.enabledMagnets.size else 0 }} 条",
        )
        if (failures.listFailures > 0) parts += "列表失败 ${failures.listFailures}"
        if (failures.threadFailures > 0) parts += "帖子失败 ${failures.threadFailures}"
        if (failures.noMagnetSkips > 0) parts += "无磁力跳过 ${failures.noMagnetSkips}"
        if (failures.timeSkips > 0) parts += "时间过滤 ${failures.timeSkips}"
        if (failures.samples.isNotEmpty()) parts += "例：${failures.samples.joinToString(" / ")}"
        return parts.joinToString(" · ")
    }

    private fun emitToast(message: String) {
        viewModelScope.launch { _events.emit(UiEvent.Toast(message)) }
    }

    private fun pickerInitialMillis(rawSinceTime: String): Long? {
        return try {
            val localMillis = DateParser.parseSinceTimeMillis(rawSinceTime.ifBlank { DateParser.defaultSinceTime() })
            val localCalendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"), Locale.CHINA).apply {
                timeInMillis = localMillis
            }
            Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
                clear()
                set(
                    localCalendar.get(Calendar.YEAR),
                    localCalendar.get(Calendar.MONTH),
                    localCalendar.get(Calendar.DAY_OF_MONTH),
                    0,
                    0,
                    0,
                )
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun formatPickerDate(selectedDateMillis: Long): String {
        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
            timeInMillis = selectedDateMillis
        }
        return String.format(
            Locale.ROOT,
            "%04d-%02d-%02d 00:00",
            utcCalendar.get(Calendar.YEAR),
            utcCalendar.get(Calendar.MONTH) + 1,
            utcCalendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    companion object {
        private const val STATUS_THROTTLE_MS = 250L
    }
}
